package com.example.audio_bible.data.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface TranslationProfileDao {

    @Query("SELECT * FROM translation_profile ORDER BY name ASC")
    fun getAll(): Flow<List<TranslationProfile>>

    @Query("SELECT * FROM translation_profile WHERE name = :name")
    suspend fun getByName(name: String): TranslationProfile?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(profile: TranslationProfile)

    @Query("UPDATE translation_profile SET folderUri = :uri WHERE name = :name")
    suspend fun updateFolderUri(name: String, uri: String?)

    @Query("""
        UPDATE translation_profile
        SET lastAudioUri = :audioUri,
            lastBookName = :bookName,
            lastBookNumber = :bookNumber,
            lastChapterNumber = :chapterNumber,
            lastPositionMs = :positionMs
        WHERE name = :name
    """)
    suspend fun updateLastPlayed(
        name: String,
        audioUri: String,
        bookName: String,
        bookNumber: Int,
        chapterNumber: Int,
        positionMs: Long
    )

    @Query("DELETE FROM translation_profile WHERE name = :name")
    suspend fun delete(name: String)
}
