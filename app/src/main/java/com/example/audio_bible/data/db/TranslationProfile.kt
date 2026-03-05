package com.example.audio_bible.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * One row per Bible translation the user has configured.
 * The [name] comes from the .fsb file or is user-supplied for audio-only profiles.
 * [folderUri] is the SAF tree URI for that translation's audio folder.
 * lastXxx fields remember where the user left off for this translation.
 */
@Entity(tableName = "translation_profile")
data class TranslationProfile(
    @PrimaryKey val name: String,
    val folderUri: String? = null,
    val lastAudioUri: String? = null,
    val lastBookName: String = "",
    val lastBookNumber: Int = 0,
    val lastChapterNumber: Int = 0,
    val lastPositionMs: Long = 0L
)
