package com.example.audio_bible.data.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface BibleTextDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVerses(verses: List<BibleVerse>)

    @Query("""
        SELECT * FROM bible_verse
        WHERE translationName = :translation
          AND bookNumber = :bookNumber
          AND chapterNumber = :chapterNumber
        ORDER BY verseNumber ASC
    """)
    fun getChapterVerses(
        translation: String,
        bookNumber: Int,
        chapterNumber: Int
    ): Flow<List<BibleVerse>>

    /** Distinct translation names that have been imported. */
    @Query("SELECT DISTINCT translationName FROM bible_verse ORDER BY translationName ASC")
    fun listTranslations(): Flow<List<String>>

    @Query("DELETE FROM bible_verse WHERE translationName = :translation")
    suspend fun deleteTranslation(translation: String)

    /** Full-text search — each word must appear somewhere in the verse. */
    @Query("""
        SELECT * FROM bible_verse
        WHERE translationName = :translation
          AND text LIKE '%' || :w1 || '%'
          AND (:w2 = '' OR text LIKE '%' || :w2 || '%')
          AND (:w3 = '' OR text LIKE '%' || :w3 || '%')
          AND (:w4 = '' OR text LIKE '%' || :w4 || '%')
          AND (:w5 = '' OR text LIKE '%' || :w5 || '%')
        ORDER BY bookNumber, chapterNumber, verseNumber
        LIMIT :limit
    """)
    suspend fun searchVersesByWords(
        translation: String,
        w1: String, w2: String = "", w3: String = "", w4: String = "", w5: String = "",
        limit: Int = 200
    ): List<BibleVerse>
}
