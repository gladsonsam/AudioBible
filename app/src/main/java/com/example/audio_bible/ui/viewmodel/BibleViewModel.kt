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
    val availableTranslations: Flow<List<String>> = db.bibleTextDao().listTranslations()

    private val _activeTranslation = MutableStateFlow(
        prefs.getString("active_translation", null)
    )
    val activeTranslation: StateFlow<String?> = _activeTranslation

    private val _importProgress = MutableStateFlow<Pair<Int,Int>?>(null) // (done, total) or null
    val importProgress: StateFlow<Pair<Int,Int>?> = _importProgress

    private val _importError = MutableStateFlow<String?>(null)
    val importError: StateFlow<String?> = _importError

    // Verses for the currently playing chapter (live, switches when chapter changes)
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

    // Delegate player state from shared object
    val currentChapter:   StateFlow<BibleChapter?>     = PlayerState.currentChapter
    val isPlaying:        StateFlow<Boolean>           = PlayerState.isPlaying
    val positionMs:       StateFlow<Long>              = PlayerState.positionMs
    val durationMs:       StateFlow<Long>              = PlayerState.durationMs
    val playbackSpeed:    StateFlow<Float>             = PlayerState.playbackSpeed
    val repeatMode:       StateFlow<RepeatMode>        = PlayerState.repeatMode
    val sleepTimerMs:     StateFlow<Long>              = PlayerState.sleepTimerMs
    val currentSyncData:  StateFlow<List<VerseSync>>   = PlayerState.currentSyncData
    val activeVerseNumber: StateFlow<Int>              = PlayerState.activeVerseNumber

    init {
        repo.getSavedFolderUri()?.let { loadBooks(it) }
    }

    fun onFolderSelected(uri: Uri) {
        repo.saveFolderUri(uri)
        loadBooks(uri)
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

    // Persists the default without starting the service (used from Settings)
    fun setDefaultSpeed(speed: Float) {
        prefs.edit().putFloat(AudioPlayerService.KEY_DEFAULT_SPEED, speed).apply()
        PlayerState.playbackSpeed.value = speed
        // Update MediaPlayer params if service is already active
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
        db.statsDao().chapterPlayCounts(bookNumber)

    /** Map of bookNumber → distinct chapters read, updates live. */
    val bookProgressMap: Flow<Map<Int, Int>> =
        db.statsDao().chaptersReadPerBook().map { list -> list.associate { it.bookNumber to it.count } }

    fun setActiveTranslation(name: String?) {
        _activeTranslation.value = name
        prefs.edit().apply {
            if (name != null) putString("active_translation", name) else remove("active_translation")
        }.apply()
    }

    fun importFsb(uri: Uri) {
        viewModelScope.launch {
            _importProgress.value = 0 to 0
            _importError.value = null
            try {
                // Delete any existing translation before importing (single translation only)
                db.bibleTextDao().listTranslations().map { list ->
                    list.forEach { db.bibleTextDao().deleteTranslation(it) }
                }
                val name = FsbParser.importFsb(getApplication(), uri) { done, total ->
                    _importProgress.value = done to total
                }
                setActiveTranslation(name)
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
        val bookName = prefs.getString("last_book_name", null)    ?: return null
        val chapNo   = prefs.getInt("last_chapter_number", 0)
        if (chapNo == 0) return null
        val posMs    = prefs.getLong("last_position_ms", 0L)
        return ContinueListeningInfo(bookName, chapNo, posMs)
    }

    /** Sets PlayerState from prefs so the player screen shows the right chapter,
     *  then sends RESUME so the service picks up from the saved position. */
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

    fun clearListeningHistory() {
        viewModelScope.launch { db.statsDao().clearAll() }
    }

    fun clearAllData() {
        viewModelScope.launch {
            db.statsDao().clearAll()
            db.bibleTextDao().listTranslations().map { list ->
                list.forEach { db.bibleTextDao().deleteTranslation(it) }
            }
            setActiveTranslation(null)
            prefs.edit().apply {
                remove("last_uri"); remove("last_book_name"); remove("last_book_number")
                remove("last_chapter_number"); remove("last_position_ms")
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
        val chapter: BibleChapter?   // null when audio folder not loaded
    )

    /** Updated by BooksScreen as the user types. */
    val verseSearchQuery = MutableStateFlow("")

    @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
    val verseSearchResults: StateFlow<List<VerseSearchResult>> =
        verseSearchQuery
            .debounce(350)
            .combine(_activeTranslation) { q, trans -> q to trans }
            .flatMapLatest { (q, trans) ->
                if (q.length < 2 || trans == null) return@flatMapLatest flowOf(emptyList())
                flow {
                    // Split into distinct meaningful words (max 5)
                    val words = q.trim().lowercase()
                        .split(Regex("\\s+"))
                        .filter { it.length >= 2 }
                        .distinct()
                        .take(5)
                    if (words.isEmpty()) { emit(emptyList()); return@flow }
                    val w = words + List(5 - words.size) { "" }
                    // Fetch broader set to re-rank (200)
                    val verses = db.bibleTextDao().searchVersesByWords(
                        trans, w[0], w[1], w[2], w[3], w[4], limit = 200
                    )
                    val bookMap = _books.value.associateBy { it.number }

                    // Score each verse for relevance
                    val phrase = words.joinToString(" ")
                    val scored = verses.map { v ->
                        val lower = v.text.lowercase()
                        var score = 0
                        // Exact phrase match — best signal
                        if (lower.contains(phrase)) score += 100
                        // Each individual word present
                        words.forEach { word -> if (lower.contains(word)) score += 10 }
                        // Words appearing near each other (within 50 chars)
                        if (words.size > 1) {
                            val positions = words.map { lower.indexOf(it) }.filter { it >= 0 }
                            if (positions.size == words.size) {
                                val span = positions.max() - positions.min()
                                if (span < 50) score += 30
                                else if (span < 120) score += 15
                            }
                        }
                        // Shorter verses are more focused
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

