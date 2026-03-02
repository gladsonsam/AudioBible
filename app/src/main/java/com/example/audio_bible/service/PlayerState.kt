package com.example.audio_bible.service

import com.example.audio_bible.data.BibleChapter
import com.example.audio_bible.data.VerseSync
import kotlinx.coroutines.flow.MutableStateFlow

enum class RepeatMode { OFF, ONE, ALL }

/** Shared playback state observed by both the service and the ViewModel. */
object PlayerState {
    val currentChapter    = MutableStateFlow<BibleChapter?>(null)
    val isPlaying         = MutableStateFlow(false)
    val positionMs        = MutableStateFlow(0L)
    val durationMs        = MutableStateFlow(0L)
    val playlist          = MutableStateFlow<List<BibleChapter>>(emptyList())
    val playbackSpeed     = MutableStateFlow(1.0f)
    val repeatMode        = MutableStateFlow(RepeatMode.OFF)
    val sleepTimerMs      = MutableStateFlow(0L)
    val currentSyncData   = MutableStateFlow<List<VerseSync>>(emptyList())
    val activeVerseNumber = MutableStateFlow(0)   // 0 = none
}
