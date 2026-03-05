package com.example.audio_bible.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * One row per chapter completion (or near-completion).
 * durationListenedMs = how long the user actually listened before it was logged.
 * translationName is empty for rows migrated from v2 (pre-multi-translation).
 */
@Entity(tableName = "reading_log")
data class ReadingLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val translationName: String = "",
    val bookNumber: Int,
    val bookName: String,
    val chapterNumber: Int,
    val timestamp: Long,          // System.currentTimeMillis()
    val durationListenedMs: Long  // chapter length in ms
)
