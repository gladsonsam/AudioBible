package com.example.audio_bible.data

import android.net.Uri

data class BibleChapter(
    val bookNumber: Int,
    val bookName: String,
    val chapterNumber: Int,
    val uri: Uri
) {
    val displayTitle: String get() = "$bookName $chapterNumber"
}

data class VerseSync(
    val verse: Int,
    val startSec: Float,
    val endSec: Float
)

data class BibleBook(
    val number: Int,
    val name: String,
    val chapters: List<BibleChapter>
) {
    /** Books 1–39 are Old Testament, 40–66 New Testament */
    val isOldTestament: Boolean get() = number <= 39
}
