package com.example.audio_bible.ui.viewmodel

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.audio_bible.data.BibleBook
import com.example.audio_bible.data.BibleChapter
import com.example.audio_bible.data.VerseSync
import com.example.audio_bible.data.BibleRepository
import com.example.audio_bible.data.FsbParser
import com.example.audio_bible.data.db.BibleDatabase
import com.example.audio_bible.data.db.BibleVerse
import com.example.audio_bible.data.db.BookChapterCount
import com.example.audio_bible.data.db.ChapterPlayCount
import com.example.audio_bible.data.db.TranslationProfile
import com.example.audio_bible.service.AudioPlayerService
import com.example.audio_bible.service.PlayerState
import com.example.audio_bible.service.RepeatMode
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class BibleViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = BibleRepository(app)
    private val db   = BibleDatabase.getInstance(app)
    private val prefs = app.getSharedPreferences("bible_prefs", android.content.Context.MODE_PRIVATE)

    // ── Translation state ─────────────────────────────────────────────────────

    /** All configured translation profiles (audio-only or with text). */
    val allTranslationProfiles: Flow<List<TranslationProfile>> = db.translationProfileDao().getAll()

    val availableTranslations: Flow<List<String>> = db.bibleTextDao().listTranslations()

    private val _activeTranslation = MutableStateFlow(
        prefs.getString("active_translation", null)
    )
    val activeTranslation: StateFlow<String?> = _activeTranslation

    private val _importProgress = MutableStateFlow<Pair<Int,Int>?>(null)
    val importProgress: StateFlow<Pair<Int,Int>?> = _importProgress

    private val _importError = MutableStateFlow<String?>(null)
    val importError: StateFlow<String?> = _importError

    @OptIn(ExperimentalCoroutinesApi::class)
    val currentChapterVerses: Flow<List<BibleVerse>> =
        _activeTranslation.flatMapLatest { translation ->
            if (translation == null) return@flatMapLatest flowOf(emptyList())
            PlayerState.currentChapter.flatMapLatest { chapter ->
                if (chapter == null) return@flatMapLatest flowOf(emptyList())
                db.bibleTextDao().getChapterVerses(translation, chapter.bookNumber, chapter.chapterNumber)
            }
        }

    private val _books     = MutableStateFlow<List<BibleBook>>(emptyList())
    val books: StateFlow<List<BibleBook>> = _books

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _error     = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    val currentChapter:    StateFlow<BibleChapter?>    = PlayerState.currentChapter
    val isPlaying:         StateFlow<Boolean>          = PlayerState.isPlaying
    val positionMs:        StateFlow<Long>             = PlayerState.positionMs
    val durationMs:        StateFlow<Long>             = PlayerState.durationMs
    val playbackSpeed:     StateFlow<Float>            = PlayerState.playbackSpeed
    val repeatMode:        StateFlow<RepeatMode>       = PlayerState.repeatMode
    val sleepTimerMs:      StateFlow<Long>             = PlayerState.sleepTimerMs
    val currentSyncData:   StateFlow<List<VerseSync>>  = PlayerState.currentSyncData
    val activeVerseNumber: StateFlow<Int>              = PlayerState.activeVerseNumber

    init {
        viewModelScope.launch {
            // One-time migration: ensure a TranslationProfile exists for the legacy active translation,
            // and attribute any untagged reading_log rows to it.
            val activeName = _activeTranslation.value
            if (activeName != null) {
                val profile = db.translationProfileDao().getByName(activeName)
                if (profile == null) {
                    // Create profile from legacy folder_uri pref
                    val legacyFolder = prefs.getString("folder_uri", null)
                    db.translationProfileDao().upsert(
                        TranslationProfile(
                            name = activeName,
                            folderUri = legacyFolder,
                            lastAudioUri = prefs.getString("last_uri", null),
                            lastBookName = prefs.getString("last_book_name", "") ?: "",
                            lastBookNumber = prefs.getInt("last_book_number", 0),
                            lastChapterNumber = prefs.getInt("last_chapter_number", 0),
                            lastPositionMs = prefs.getLong("last_position_ms", 0L)
                        )
                    )
                }
                // Attribute unlabelled reading_log rows to the active translation
                db.statsDao().migrateUnattributedLogs(activeName)
            }
        }

        // Load books for the active translation's folder
        val activeName = _activeTranslation.value
        if (activeName != null) {
            viewModelScope.launch {
                val profile = db.translationProfileDao().getByName(activeName)
                val folderUri = profile?.folderUri?.let { Uri.parse(it) }
                    ?: repo.getSavedFolderUri()
                folderUri?.let { loadBooks(it) }
            }
        } else {
            repo.getSavedFolderUri()?.let { loadBooks(it) }
        }

        // Restore last chapter for display without starting playback
        if (PlayerState.currentChapter.value == null) {
            val uriStr   = prefs.getString("last_uri", null)
            val bookName = prefs.getString("last_book_name", "") ?: ""
            val bookNo   = prefs.getInt("last_book_number", 0)
            val chapNo   = prefs.getInt("last_chapter_number", 0)
            val posMs    = prefs.getLong("last_position_ms", 0L)
            if (uriStr != null && chapNo > 0) {
                PlayerState.currentChapter.value = BibleChapter(
                    bookNo, bookName, chapNo, android.net.Uri.parse(uriStr)
                )
                PlayerState.positionMs.value = posMs
            }
        }
    }

    // ── Translation management ────────────────────────────────────────────────

    /** Creates an empty translation profile with [name] if it does not already exist. */
    fun createTranslation(name: String) {
        viewModelScope.launch {
            val existing = db.translationProfileDao().getByName(name)
            if (existing == null) {
                db.translationProfileDao().upsert(TranslationProfile(name = name))
            }
        }
    }

    /**
     * Sets the audio folder for a specific translation profile. Also mirrors the
     * folder to the legacy `folder_uri` pref when it is the active translation,
     * so AudioPlayerService can continue to use [BibleRepository.getSavedFolderUri].
     */
    fun setFolderForTranslation(translationName: String, uri: Uri) {
        viewModelScope.launch {
            // Ensure profile exists
            val existing = db.translationProfileDao().getByName(translationName)
            if (existing == null) {
                db.translationProfileDao().upsert(TranslationProfile(name = translationName, folderUri = uri.toString()))
            } else {
                db.translationProfileDao().updateFolderUri(translationName, uri.toString())
            }
            // Mirror to legacy pref so service always has the active folder
            if (translationName == _activeTranslation.value) {
                repo.saveFolderUri(uri)
                loadBooks(uri)
            }
        }
    }

    /** Legacy: folder selected when no specific translation is targeted (active translation). */
    fun onFolderSelected(uri: Uri) {
        val active = _activeTranslation.value
        if (active != null) {
            setFolderForTranslation(active, uri)
        } else {
            repo.saveFolderUri(uri)
            loadBooks(uri)
        }
    }

    /**
     * Switches the active translation. Persists the current playback position to the
     * old profile, then loads the new profile's folder and last-played state.
     */
    fun setActiveTranslation(name: String?) {
        viewModelScope.launch {
            // Persist current position to the OLD profile before switching
            val oldName = _activeTranslation.value
            if (oldName != null) {
                val chapter = PlayerState.currentChapter.value
                if (chapter != null) {
                    db.translationProfileDao().updateLastPlayed(
                        oldName,
                        chapter.uri.toString(),
                        chapter.bookName,
                        chapter.bookNumber,
                        chapter.chapterNumber,
                        PlayerState.positionMs.value
                    )
                }
            }

            _activeTranslation.value = name
            prefs.edit().apply {
                if (name != null) putString("active_translation", name) else remove("active_translation")
            }.apply()

            if (name == null) return@launch

            // Load new profile and restore state
            val profile = db.translationProfileDao().getByName(name)
            val folderUri = profile?.folderUri?.let { Uri.parse(it) }
            if (folderUri != null) {
                repo.saveFolderUri(folderUri)
                loadBooks(folderUri)
            } else {
                _books.value = emptyList()
                PlayerState.playlist.value = emptyList()
            }

            // Restore last-played chapter for this translation
            if (profile?.lastChapterNumber != null && profile.lastChapterNumber > 0 &&
                profile.lastAudioUri != null) {
                val chapter = BibleChapter(
                    profile.lastBookNumber,
                    profile.lastBookName,
                    profile.lastChapterNumber,
                    Uri.parse(profile.lastAudioUri)
                )
                PlayerState.currentChapter.value = chapter
                PlayerState.positionMs.value = profile.lastPositionMs
                // Update global prefs for service cold-start
                prefs.edit()
                    .putString("last_uri", profile.lastAudioUri)
                    .putString("last_book_name", profile.lastBookName)
                    .putInt("last_book_number", profile.lastBookNumber)
                    .putInt("last_chapter_number", profile.lastChapterNumber)
                    .putLong("last_position_ms", profile.lastPositionMs)
                    .apply()
            } else {
                PlayerState.currentChapter.value = null
                PlayerState.positionMs.value = 0L
            }
        }
    }

    private fun loadBooks(uri: Uri) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                val books = repo.loadBooks(uri)
                _books.value = books
                PlayerState.playlist.value = books.flatMap { it.chapters }
            } catch (e: Exception) {
                _error.value = "Failed to load files: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun playChapter(chapter: BibleChapter) {
        val index = PlayerState.playlist.value.indexOf(chapter)
        if (index < 0) return
        send(AudioPlayerService.ACTION_PLAY) {
            putExtra(AudioPlayerService.EXTRA_CHAPTER_INDEX, index)
        }
    }

    fun togglePlayPause() = if (PlayerState.isPlaying.value) pause() else resume()
    fun pause()           = send(AudioPlayerService.ACTION_PAUSE)
    fun resume()          = send(AudioPlayerService.ACTION_RESUME)
    fun next()            = send(AudioPlayerService.ACTION_NEXT)
    fun previous()        = send(AudioPlayerService.ACTION_PREV)

    fun seekTo(ms: Long) = send(AudioPlayerService.ACTION_SEEK) {
        putExtra(AudioPlayerService.EXTRA_SEEK_POSITION, ms.toInt())
    }

    fun setSpeed(speed: Float) = send(AudioPlayerService.ACTION_SET_SPEED) {
        putExtra(AudioPlayerService.EXTRA_SPEED, speed)
    }

    fun setDefaultSpeed(speed: Float) {
        prefs.edit().putFloat(AudioPlayerService.KEY_DEFAULT_SPEED, speed).apply()
        PlayerState.playbackSpeed.value = speed
        if (PlayerState.currentChapter.value != null) {
            send(AudioPlayerService.ACTION_SET_SPEED) {
                putExtra(AudioPlayerService.EXTRA_SPEED, speed)
            }
        }
    }

    fun cycleRepeat() {
        val next = when (PlayerState.repeatMode.value) {
            RepeatMode.OFF -> RepeatMode.ALL
            RepeatMode.ALL -> RepeatMode.ONE
            RepeatMode.ONE -> RepeatMode.OFF
        }
        send(AudioPlayerService.ACTION_SET_REPEAT) {
            putExtra(AudioPlayerService.EXTRA_REPEAT_MODE, next.name)
        }
    }

    fun setSleepTimer(minutes: Int) = send(AudioPlayerService.ACTION_SLEEP_TIMER) {
        putExtra(AudioPlayerService.EXTRA_SLEEP_TIMER_MS, minutes * 60_000L)
    }

    fun cancelSleepTimer() = send(AudioPlayerService.ACTION_SLEEP_TIMER) {
        putExtra(AudioPlayerService.EXTRA_SLEEP_TIMER_MS, 0L)
    }

    fun chapterPlayCounts(bookNumber: Int): Flow<List<ChapterPlayCount>> =
        db.statsDao().chapterPlayCounts(bookNumber, _activeTranslation.value ?: "")

    val bookProgressMap: Flow<Map<Int, Int>> =
        _activeTranslation.flatMapLatest { trans ->
            db.statsDao().chaptersReadPerBook(trans ?: "")
                .map { list -> list.associate { it.bookNumber to it.count } }
        }

    /** Import a .fsb file; creates or updates the translation profile with [targetName] if provided. */
    fun importFsb(uri: Uri, targetName: String? = null) {
        viewModelScope.launch {
            _importProgress.value = 0 to 0
            _importError.value = null
            try {
                val name = FsbParser.importFsb(getApplication(), uri) { done, total ->
                    _importProgress.value = done to total
                }
                // Ensure a profile exists for this translation name
                val existing = db.translationProfileDao().getByName(name)
                if (existing == null) {
                    db.translationProfileDao().upsert(TranslationProfile(name = name))
                }
                // If no active translation yet, auto-activate the imported one
                if (_activeTranslation.value == null) {
                    setActiveTranslation(name)
                }
            } catch (e: Exception) {
                _importError.value = "Import failed: ${e.message}"
            } finally {
                _importProgress.value = null
            }
        }
    }

    fun deleteTranslation(name: String) {
        viewModelScope.launch {
            db.bibleTextDao().deleteTranslation(name)
            db.translationProfileDao().delete(name)
            if (_activeTranslation.value == name) setActiveTranslation(null)
        }
    }

    // ── Continue Listening ────────────────────────────────────────────────────

    data class ContinueListeningInfo(
        val bookName: String,
        val chapterNumber: Int,
        val positionMs: Long
    )

    private val _continueListening = MutableStateFlow(readContinueListening())
    val continueListening: StateFlow<ContinueListeningInfo?> = _continueListening

    private fun readContinueListening(): ContinueListeningInfo? {
        val bookName = prefs.getString("last_book_name", null) ?: return null
        val chapNo   = prefs.getInt("last_chapter_number", 0)
        if (chapNo == 0) return null
        val posMs    = prefs.getLong("last_position_ms", 0L)
        return ContinueListeningInfo(bookName, chapNo, posMs)
    }

    fun resumeLastPlayed() {
        val uriStr   = prefs.getString("last_uri", null)         ?: return
        val bookName = prefs.getString("last_book_name", "")     ?: return
        val bookNo   = prefs.getInt("last_book_number", 0)
        val chapNo   = prefs.getInt("last_chapter_number", 0)
        val posMs    = prefs.getLong("last_position_ms", 0L)
        val chapter  = BibleChapter(bookNo, bookName, chapNo, android.net.Uri.parse(uriStr))
        PlayerState.currentChapter.value = chapter
        PlayerState.positionMs.value     = posMs
        resume()
        _continueListening.value = ContinueListeningInfo(bookName, chapNo, posMs)
    }

    // ── Data management ───────────────────────────────────────────────────────

    /** Clears listening history for the active translation only. */
    fun clearListeningHistory() {
        viewModelScope.launch {
            db.statsDao().clearAll(_activeTranslation.value ?: "")
        }
    }

    fun clearAllData() {
        viewModelScope.launch {
            db.statsDao().clearAll()
            db.bibleTextDao().listTranslations().map { list ->
                list.forEach { db.bibleTextDao().deleteTranslation(it) }
            }
            db.translationProfileDao().getAll().first().forEach {
                db.translationProfileDao().delete(it.name)
            }
            setActiveTranslation(null)
            prefs.edit().apply {
                remove("last_uri"); remove("last_book_name"); remove("last_book_number")
                remove("last_chapter_number"); remove("last_position_ms")
                remove("folder_uri")
            }.apply()
            _continueListening.value = null
        }
    }

    // ── Bible full-text search ─────────────────────────────────────────────────

    data class VerseSearchResult(
        val bookNumber: Int,
        val bookName: String,
        val chapterNumber: Int,
        val verseNumber: Int,
        val text: String,
        val chapter: BibleChapter?
    )

    val verseSearchQuery = MutableStateFlow("")

    @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
    val verseSearchResults: StateFlow<List<VerseSearchResult>> =
        verseSearchQuery
            .debounce(350)
            .combine(_activeTranslation) { q, trans -> q to trans }
            .flatMapLatest { (q, trans) ->
                if (q.length < 2 || trans == null) return@flatMapLatest flowOf(emptyList())
                flow {
                    val words = q.trim().lowercase()
                        .split(Regex("\\s+"))
                        .filter { it.length >= 2 }
                        .distinct()
                        .take(5)
                    if (words.isEmpty()) { emit(emptyList()); return@flow }
                    val w = words + List(5 - words.size) { "" }
                    val verses = db.bibleTextDao().searchVersesByWords(
                        trans, w[0], w[1], w[2], w[3], w[4], limit = 200
                    )
                    val bookMap = _books.value.associateBy { it.number }
                    val phrase = words.joinToString(" ")
                    val scored = verses.map { v ->
                        val lower = v.text.lowercase()
                        var score = 0
                        if (lower.contains(phrase)) score += 100
                        words.forEach { word -> if (lower.contains(word)) score += 10 }
                        if (words.size > 1) {
                            val positions = words.map { lower.indexOf(it) }.filter { it >= 0 }
                            if (positions.size == words.size) {
                                val span = positions.max() - positions.min()
                                if (span < 50) score += 30
                                else if (span < 120) score += 15
                            }
                        }
                        score -= (v.text.length / 50).coerceAtMost(10)
                        v to score
                    }
                    val results = scored
                        .sortedByDescending { it.second }
                        .take(60)
                        .map { (v, _) ->
                            val book = bookMap[v.bookNumber]
                            val chapter = book?.chapters?.find { it.chapterNumber == v.chapterNumber }
                            VerseSearchResult(v.bookNumber, book?.name ?: "Book ${v.bookNumber}",
                                v.chapterNumber, v.verseNumber, v.text, chapter)
                        }
                    emit(results)
                }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // ── Verse selection (for sharing) ─────────────────────────────────────────

    private val _selectedVerses = MutableStateFlow<Set<Int>>(emptySet())
    val selectedVerses: StateFlow<Set<Int>> = _selectedVerses

    fun toggleVerseSelection(verseNum: Int) {
        _selectedVerses.update { current ->
            if (current.contains(verseNum)) current - verseNum else current + verseNum
        }
    }

    fun clearVerseSelection() { _selectedVerses.value = emptySet() }

    // ── Font size ─────────────────────────────────────────────────────────────

    private val _verseFontSize = MutableStateFlow(
        prefs.getFloat("verse_font_size", 16f)
    )
    val verseFontSize: StateFlow<Float> = _verseFontSize

    fun setVerseFontSize(sp: Float) {
        _verseFontSize.value = sp
        prefs.edit().putFloat("verse_font_size", sp).apply()
    }

    private fun send(action: String, extras: (Intent.() -> Unit)? = null) {
        val ctx = getApplication<Application>()
        val intent = Intent(ctx, AudioPlayerService::class.java).apply {
            this.action = action
            extras?.invoke(this)
        }
        ContextCompat.startForegroundService(ctx, intent)
    }
}
