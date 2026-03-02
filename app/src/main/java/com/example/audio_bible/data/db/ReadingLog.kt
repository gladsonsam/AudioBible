package com.example.audio_bible.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * One row per chapter completion (or near-completion).
 * durationListenedMs = how long the user actually listened before it was logged.
 */
@Entity(tableName = "reading_log")
data class ReadingLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val bookNumber: Int,
    val bookName: String,
    val chapterNumber: Int,
    val timestamp: Long,          // System.currentTimeMillis()
    val durationListenedMs: Long  // chapter length in ms
)
