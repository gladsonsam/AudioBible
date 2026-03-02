package com.example.audio_bible.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "bible_verse",
    indices = [Index(value = ["translationName", "bookNumber", "chapterNumber"])]
)
data class BibleVerse(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val translationName: String,
    val bookNumber: Int,
    val chapterNumber: Int,
    val verseNumber: Int,
    val text: String
)
