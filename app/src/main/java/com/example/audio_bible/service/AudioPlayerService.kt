package com.example.audio_bible.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.PlaybackParams
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.core.app.NotificationCompat
import androidx.media.MediaBrowserServiceCompat
import androidx.media.app.NotificationCompat.MediaStyle
import com.example.audio_bible.MainActivity
import com.example.audio_bible.R
import com.example.audio_bible.data.db.BibleDatabase
import com.example.audio_bible.data.db.ReadingLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class AudioPlayerService : MediaBrowserServiceCompat() {

    companion object {
        const val ACTION_PLAY        = "ACTION_PLAY"
        const val ACTION_PAUSE       = "ACTION_PAUSE"
        const val ACTION_RESUME      = "ACTION_RESUME"
        const val ACTION_NEXT        = "ACTION_NEXT"
        const val ACTION_PREV        = "ACTION_PREV"
        const val ACTION_SEEK        = "ACTION_SEEK"
        const val ACTION_STOP        = "ACTION_STOP"
        const val ACTION_SET_SPEED   = "ACTION_SET_SPEED"
        const val ACTION_SET_REPEAT  = "ACTION_SET_REPEAT"
        const val ACTION_SLEEP_TIMER = "ACTION_SLEEP_TIMER"

        const val EXTRA_CHAPTER_INDEX  = "chapter_index"
        const val EXTRA_SEEK_POSITION  = "seek_position"
        const val EXTRA_SPEED          = "speed"
        const val EXTRA_REPEAT_MODE    = "repeat_mode"
        const val EXTRA_SLEEP_TIMER_MS = "sleep_timer_ms"

        const val NOTIFICATION_ID = 1
        const val CHANNEL_ID      = "audio_bible_channel"

        // SharedPrefs keys for last-played state
        private const val PREFS_NAME      = "bible_prefs"
        private const val KEY_LAST_URI    = "last_uri"
        private const val KEY_LAST_BOOK   = "last_book_name"
        private const val KEY_LAST_BOOKNO = "last_book_number"
        private const val KEY_LAST_CHNO   = "last_chapter_number"
        private const val KEY_LAST_POS    = "last_position_ms"
        private const val KEY_LAST_DUR    = "last_duration_ms"
        private const val KEY_IS_PLAYING  = "is_playing"
        const val KEY_DEFAULT_SPEED       = "default_speed"
    }

    private var mediaPlayer: MediaPlayer? = null
    private lateinit var audioManager: AudioManager
    private lateinit var focusRequest: AudioFocusRequest
    private lateinit var mediaSession: MediaSessionCompat
    private lateinit var prefs: android.content.SharedPreferences
    private lateinit var repo: com.example.audio_bible.data.BibleRepository

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val handler = Handler(Looper.getMainLooper())
    /** True only when the user explicitly paused — prevents auto-resume on audio focus regain. */
    private var userPaused = false

    // Progress updater (every 500 ms) — also ticks active verse from sync data
    private var notifTickCount = 0
    private val progressRunnable = object : Runnable {
        override fun run() {
            mediaPlayer?.takeIf { it.isPlaying }?.let {
                val pos = it.currentPosition.toLong()
                PlayerState.positionMs.value = pos
                prefs.edit().putLong(KEY_LAST_POS, pos).apply()
                // Advance active verse pointer
                val posSec = pos / 1000f
                val sync = PlayerState.currentSyncData.value
                if (sync.isNotEmpty()) {
                    val active = sync.lastOrNull { it.startSec <= posSec }
                    PlayerState.activeVerseNumber.value = active?.verse ?: 0
                }
                // Refresh notification time display every ~10 s (every 20 ticks)
                notifTickCount++
                if (notifTickCount >= 20) {
                    notifTickCount = 0
                    updateNotification()
                }
                handler.postDelayed(this, 500)
            }
        }
    }

    // Sleep-timer countdown
    private var sleepTimerRunnable: Runnable? = null
    private var sleepTimerEndMs = 0L

    private val sleepCountdownRunnable = object : Runnable {
        override fun run() {
            val remaining = sleepTimerEndMs - System.currentTimeMillis()
            if (remaining <= 0) {
                PlayerState.sleepTimerMs.value = 0L
                pauseInternal()
            } else {
                PlayerState.sleepTimerMs.value = remaining
                handler.postDelayed(this, 1000)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        PlayerState.playbackSpeed.value = prefs.getFloat(KEY_DEFAULT_SPEED, 1.0f)
        repo  = com.example.audio_bible.data.BibleRepository(this)
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        mediaSession = MediaSessionCompat(this, "AudioBible").apply {
            @Suppress("DEPRECATION")
            setFlags(
                MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS or
                MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS
            )
            setCallback(object : MediaSessionCompat.Callback() {
                override fun onPlay() {
                    if (mediaPlayer != null) resumeInternal() else restoreAndPlay()
                }
                // Samsung Routines / Google Assistant may use these instead of plain onPlay()
                override fun onPlayFromMediaId(mediaId: String?, extras: Bundle?) = onPlay()
                override fun onPlayFromSearch(query: String?, extras: Bundle?)    = onPlay()
                override fun onPause()          = pauseInternal()
                override fun onSkipToNext()     = playNext()
                override fun onSkipToPrevious() = playPrev()
                override fun onStop()           { stopAll(); stopSelf() }
            })
            isActive = true
        }
        // Required so MediaBrowserServiceCompat exposes the token to external clients
        sessionToken = mediaSession.sessionToken
        // Publish an initial PAUSED state so Samsung Routines / system can see playback is
        // available and will send a play command rather than waiting for user interaction.
        if (prefs.getString(KEY_LAST_URI, null) != null) {
            updatePlaybackState(PlaybackStateCompat.STATE_PAUSED)
        }
        focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setOnAudioFocusChangeListener { change ->
                when (change) {
                    AudioManager.AUDIOFOCUS_LOSS,
                    AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                        if (PlayerState.isPlaying.value) {
                            pauseInternal(userInitiated = false)
                        }
                    }
                    AudioManager.AUDIOFOCUS_GAIN -> {
                        if (!userPaused) resumeInternal()
                    }
                }
            }.build()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Let MediaButtonReceiver route headset/hardware button events to the session
        androidx.media.session.MediaButtonReceiver.handleIntent(mediaSession, intent)
        startForeground(NOTIFICATION_ID, buildNotification())
        when (intent?.action) {
            ACTION_PLAY        -> playChapter(intent.getIntExtra(EXTRA_CHAPTER_INDEX, 0))
            ACTION_PAUSE       -> pauseInternal()
            ACTION_RESUME      -> resumeInternal()
            ACTION_NEXT        -> playNext()
            ACTION_PREV        -> playPrev()
            ACTION_SEEK        -> seekTo(intent.getIntExtra(EXTRA_SEEK_POSITION, 0))
            ACTION_SET_SPEED   -> setSpeed(intent.getFloatExtra(EXTRA_SPEED, 1f))
            ACTION_SET_REPEAT  -> setRepeat(intent.getStringExtra(EXTRA_REPEAT_MODE) ?: "OFF")
            ACTION_SLEEP_TIMER -> startSleepTimer(intent.getLongExtra(EXTRA_SLEEP_TIMER_MS, 0L))
            ACTION_STOP        -> { stopAll(); stopSelf() }
            // null action = service started externally (e.g. Samsung Routines, system restart)
            // Auto-play last chapter so routines work without needing a separate play command.
            null -> if (mediaPlayer == null) restoreAndPlay()
        }
        return START_NOT_STICKY
    }

    // ── Playback ──────────────────────────────────────────────────────────────

    private fun playChapter(index: Int) {
        val playlist = PlayerState.playlist.value
        if (index !in playlist.indices) return
        val chapter = playlist[index]

        mediaPlayer?.release()
        mediaPlayer = MediaPlayer().apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .build()
            )
            applicationContext.contentResolver.openFileDescriptor(chapter.uri, "r")?.use { pfd ->
                setDataSource(pfd.fileDescriptor)
            }
            prepare()
            setOnCompletionListener {
                logCompletion()
                handleCompletion()
            }
            applySpeed(this)
            start()
        }

        PlayerState.currentChapter.value    = chapter
        PlayerState.isPlaying.value         = true
        PlayerState.durationMs.value        = mediaPlayer!!.duration.toLong()
        PlayerState.positionMs.value        = 0L
        PlayerState.activeVerseNumber.value = 0
        PlayerState.currentSyncData.value   = emptyList<com.example.audio_bible.data.VerseSync>()

        audioManager.requestAudioFocus(focusRequest)
        startProgressUpdater()
        updateMediaMetadata()
        updatePlaybackState(PlaybackStateCompat.STATE_PLAYING)
        saveLastChapter(chapter)
        prefs.edit()
            .putLong(KEY_LAST_DUR, mediaPlayer!!.duration.toLong())
            .putBoolean(KEY_IS_PLAYING, true)
            .apply()
        updateNotification()
        com.example.audio_bible.widget.BibleWidgetProvider.requestUpdate(this)

        // Load sync data in background — sets PlayerState.currentSyncData when ready
        val folderUri = repo.getSavedFolderUri()
        if (folderUri != null) {
            serviceScope.launch {
                PlayerState.currentSyncData.value = repo.loadSyncData(folderUri, chapter)
            }
        }
    }

    private fun logCompletion() {
        val chapter = PlayerState.currentChapter.value ?: return
        val duration = PlayerState.durationMs.value
        serviceScope.launch {
            BibleDatabase.getInstance(applicationContext).statsDao().insert(
                ReadingLog(
                    bookNumber        = chapter.bookNumber,
                    bookName          = chapter.bookName,
                    chapterNumber     = chapter.chapterNumber,
                    timestamp         = System.currentTimeMillis(),
                    durationListenedMs = duration
                )
            )
        }
    }

    private fun handleCompletion() {
        when (PlayerState.repeatMode.value) {
            RepeatMode.ONE -> {
                val idx = PlayerState.playlist.value.indexOf(PlayerState.currentChapter.value)
                playChapter(idx)
            }
            RepeatMode.ALL -> {
                val playlist = PlayerState.playlist.value
                val idx = playlist.indexOf(PlayerState.currentChapter.value)
                playChapter(if (idx >= playlist.size - 1) 0 else idx + 1)
            }
            RepeatMode.OFF -> playNext()
        }
    }

    private fun pauseInternal(userInitiated: Boolean = true) {
        userPaused = userInitiated
        mediaPlayer?.pause()
        PlayerState.isPlaying.value = false
        handler.removeCallbacks(progressRunnable)
        updatePlaybackState(PlaybackStateCompat.STATE_PAUSED)
        prefs.edit()
            .putLong(KEY_LAST_POS, mediaPlayer?.currentPosition?.toLong() ?: 0L)
            .putBoolean(KEY_IS_PLAYING, false)
            .apply()
        updateNotification()
        com.example.audio_bible.widget.BibleWidgetProvider.requestUpdate(this)
    }

    private fun resumeInternal() {
        userPaused = false
        val mp = mediaPlayer
        if (mp == null) { restoreAndPlay(); return }
        mp.start()
        PlayerState.isPlaying.value = true
        startProgressUpdater()
        updatePlaybackState(PlaybackStateCompat.STATE_PLAYING)
        prefs.edit().putBoolean(KEY_IS_PLAYING, true).apply()
        updateNotification()
        com.example.audio_bible.widget.BibleWidgetProvider.requestUpdate(this)
    }

    private fun playNext() {
        val playlist = PlayerState.playlist.value
        val idx = playlist.indexOf(PlayerState.currentChapter.value)
        if (idx in 0 until playlist.size - 1) {
            playChapter(idx + 1)
        } else {
            PlayerState.isPlaying.value = false
            updateNotification()
        }
    }

    private fun playPrev() {
        val playlist = PlayerState.playlist.value
        val idx = playlist.indexOf(PlayerState.currentChapter.value)
        if ((mediaPlayer?.currentPosition ?: 0) > 3000) {
            seekTo(0)
        } else if (idx > 0) {
            playChapter(idx - 1)
        }
    }

    private fun seekTo(positionMs: Int) {
        mediaPlayer?.seekTo(positionMs)
        PlayerState.positionMs.value = positionMs.toLong()
    }

    private fun setSpeed(speed: Float) {
        PlayerState.playbackSpeed.value = speed
        prefs.edit().putFloat(KEY_DEFAULT_SPEED, speed).apply()
        mediaPlayer?.let { applySpeed(it) }
    }

    private fun applySpeed(mp: MediaPlayer) {
        val speed = PlayerState.playbackSpeed.value.coerceIn(0.25f, 3.0f)
        try {
            mp.playbackParams = mp.playbackParams.setSpeed(speed)
        } catch (_: Exception) {}
    }

    private fun setRepeat(mode: String) {
        PlayerState.repeatMode.value = try {
            RepeatMode.valueOf(mode)
        } catch (_: Exception) { RepeatMode.OFF }
    }

    private fun startSleepTimer(durationMs: Long) {
        handler.removeCallbacks(sleepCountdownRunnable)
        if (durationMs <= 0L) {
            PlayerState.sleepTimerMs.value = 0L
            return
        }
        sleepTimerEndMs = System.currentTimeMillis() + durationMs
        PlayerState.sleepTimerMs.value = durationMs
        handler.post(sleepCountdownRunnable)
    }

    private fun stopAll() {
        handler.removeCallbacks(progressRunnable)
        handler.removeCallbacks(sleepCountdownRunnable)
        mediaPlayer?.release()
        mediaPlayer = null
        PlayerState.isPlaying.value = false
        PlayerState.sleepTimerMs.value = 0L
        audioManager.abandonAudioFocusRequest(focusRequest)
        stopForeground(STOP_FOREGROUND_REMOVE)
        prefs.edit().putBoolean(KEY_IS_PLAYING, false).apply()
        com.example.audio_bible.widget.BibleWidgetProvider.requestUpdate(this)
    }

    // ── Notification ─────────────────────────────────────────────────────────

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID, "Audio Bible", NotificationManager.IMPORTANCE_LOW
        ).apply { description = "Bible audio playback" }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun fmtMs(ms: Long): String {
        val m = ms / 60000
        val s = (ms % 60000) / 1000
        return "%d:%02d".format(m, s)
    }

    private fun buildNotification(): android.app.Notification {
        val chapter  = PlayerState.currentChapter.value
        val pos      = PlayerState.positionMs.value
        val dur      = PlayerState.durationMs.value
        val speed    = PlayerState.playbackSpeed.value
        val playing  = PlayerState.isPlaying.value

        val title = chapter?.let { "${it.bookName} · Chapter ${it.chapterNumber}" } ?: "Audio Bible"
        val testament = chapter?.let {
            if (it.bookNumber <= 39) "Old Testament" else "New Testament"
        }

        // Content text: time + speed (clean, no testament — that goes in subtext)
        val timeStr = if (dur > 0) "${fmtMs(pos)} / ${fmtMs(dur)}" else if (pos > 0) fmtMs(pos) else null
        val contentText = listOfNotNull(
            timeStr,
            if (speed != 1.0f) "%.2g×".format(speed) else null
        ).joinToString("  •  ").ifEmpty { if (playing) "Playing" else "Select a chapter" }

        val largeIcon = android.graphics.BitmapFactory.decodeResource(resources, R.mipmap.ic_launcher)

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setLargeIcon(largeIcon)
            .setContentTitle(title)
            .setContentText(contentText)
            .setSubText(testament)
            .setColor(0xFFE8A838.toInt())
            .setColorized(false)
            .setShowWhen(false)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
            .setContentIntent(
                PendingIntent.getActivity(
                    this, 0,
                    Intent(this, MainActivity::class.java).apply {
                        action = Intent.ACTION_MAIN
                        addCategory(Intent.CATEGORY_LAUNCHER)
                        putExtra(MainActivity.EXTRA_NAVIGATE_TO, "player")
                        flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    },
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            )
            .addAction(R.drawable.ic_notif_prev,    "Previous", servicePendingIntent(ACTION_PREV))
            .addAction(
                if (playing) R.drawable.ic_notif_pause else R.drawable.ic_notif_play,
                if (playing) "Pause" else "Play",
                servicePendingIntent(if (playing) ACTION_PAUSE else ACTION_RESUME)
            )
            .addAction(R.drawable.ic_notif_next,    "Next",     servicePendingIntent(ACTION_NEXT))
            .setStyle(MediaStyle().setMediaSession(mediaSession.sessionToken).setShowActionsInCompactView(0, 1, 2))
            .setOngoing(playing)
            .build()
    }

    private fun updateNotification() =
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, buildNotification())

    private fun servicePendingIntent(action: String): PendingIntent {
        val i = Intent(this, AudioPlayerService::class.java).apply { this.action = action }
        return PendingIntent.getService(
            this, action.hashCode(), i,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    // ── MediaSession state ────────────────────────────────────────────────────

    private fun updatePlaybackState(state: Int) {
        val position = mediaPlayer?.currentPosition?.toLong() ?: 0L
        mediaSession.setPlaybackState(
            PlaybackStateCompat.Builder()
                .setActions(
                    PlaybackStateCompat.ACTION_PLAY or
                    PlaybackStateCompat.ACTION_PAUSE or
                    PlaybackStateCompat.ACTION_PLAY_PAUSE or
                    PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
                    PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or
                    PlaybackStateCompat.ACTION_STOP
                )
                .setState(state, position, PlayerState.playbackSpeed.value)
                .build()
        )
    }

    private fun updateMediaMetadata() {
        val chapter = PlayerState.currentChapter.value ?: return
        val testament = if (chapter.bookNumber <= 39) "Old Testament" else "New Testament"
        mediaSession.setMetadata(
            MediaMetadataCompat.Builder()
                .putString(MediaMetadataCompat.METADATA_KEY_TITLE, chapter.displayTitle)
                .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, testament)
                .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, "Audio Bible")
                .putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_SUBTITLE, chapter.bookName)
                .putLong(MediaMetadataCompat.METADATA_KEY_DURATION,
                    PlayerState.durationMs.value)
                .build()
        )
    }

    // ── MediaBrowserServiceCompat ─────────────────────────────────────────────

    override fun onGetRoot(
        clientPackageName: String,
        clientUid: Int,
        rootHints: Bundle?
    ): BrowserRoot = BrowserRoot("root", null)

    override fun onLoadChildren(
        parentId: String,
        result: Result<MutableList<MediaBrowserCompat.MediaItem>>
    ) {
        // Return empty list — we only need transport controls, not full browsing
        result.sendResult(mutableListOf())
    }

    // ── Progress ──────────────────────────────────────────────────────────────

    private fun startProgressUpdater() {
        handler.removeCallbacks(progressRunnable)
        handler.post(progressRunnable)
    }

    // ── Last-chapter persistence ───────────────────────────────────────────────

    private fun saveLastChapter(chapter: com.example.audio_bible.data.BibleChapter) {
        prefs.edit()
            .putString(KEY_LAST_URI,    chapter.uri.toString())
            .putString(KEY_LAST_BOOK,   chapter.bookName)
            .putInt(KEY_LAST_BOOKNO,    chapter.bookNumber)
            .putInt(KEY_LAST_CHNO,      chapter.chapterNumber)
            .putLong(KEY_LAST_POS,      0L)   // reset position on new chapter
            .apply()
    }

    /** Called by onPlay() when the service is cold (no active MediaPlayer). */
    private fun restoreAndPlay() {
        val uriStr   = prefs.getString(KEY_LAST_URI,  null) ?: return
        val bookName = prefs.getString(KEY_LAST_BOOK, "")   ?: ""
        val bookNo   = prefs.getInt(KEY_LAST_BOOKNO, 0)
        val chapNo   = prefs.getInt(KEY_LAST_CHNO,   0)
        val posMs    = prefs.getLong(KEY_LAST_POS,   0L)
        val uri      = android.net.Uri.parse(uriStr)

        val chapter = com.example.audio_bible.data.BibleChapter(
            bookNumber    = bookNo,
            bookName      = bookName,
            chapterNumber = chapNo,
            uri           = uri
        )

        // Must be a foreground service before playing audio
        startForeground(NOTIFICATION_ID, buildNotification())

        try {
            mediaPlayer?.release()
            mediaPlayer = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .build()
                )
                // Use context+URI overload — handles SAF URIs correctly without needing FileDescriptor
                setDataSource(applicationContext, uri)
                prepare()
                setOnCompletionListener { logCompletion(); handleCompletion() }
                applySpeed(this)
                if (posMs > 0) seekTo(posMs.toInt())
                start()
            }

            PlayerState.currentChapter.value = chapter
            PlayerState.isPlaying.value      = true
            PlayerState.durationMs.value     = mediaPlayer!!.duration.toLong()
            PlayerState.positionMs.value     = posMs

            audioManager.requestAudioFocus(focusRequest)
            startProgressUpdater()
            updateMediaMetadata()
            updatePlaybackState(PlaybackStateCompat.STATE_PLAYING)
            updateNotification()
        } catch (e: Exception) {
            // URI no longer accessible — clear stale state
            prefs.edit().remove(KEY_LAST_URI).apply()
            stopForeground(STOP_FOREGROUND_REMOVE)
        }
    }



    override fun onDestroy() {
        stopAll()
        mediaSession.release()
        super.onDestroy()
    }
}

