package com.example.audio_bible.data.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

data class BookStat(
    val bookName: String,
    val bookNumber: Int,
    val timesPlayed: Int,
    val totalMinutes: Long
)

data class HeatmapDay(
    val dayLabel: String,  // "YYYY-MM-DD"
    val count: Int
)

data class ChapterPlayCount(
    val chapterNumber: Int,
    val count: Int
)

@Dao
interface StatsDao {

    @Insert
    suspend fun insert(log: ReadingLog)

    /** Total chapter plays for [translation] (or all if empty). */
    @Query("SELECT COUNT(*) FROM reading_log WHERE (:translation = '' OR translationName = :translation)")
    fun totalPlays(translation: String = ""): Flow<Int>

    /** Total distinct chapters played for [translation]. */
    @Query("SELECT COUNT(DISTINCT bookNumber || '_' || chapterNumber) FROM reading_log WHERE (:translation = '' OR translationName = :translation)")
    fun uniqueChapters(translation: String = ""): Flow<Int>

    /** Total listening time in minutes for [translation]. */
    @Query("SELECT COALESCE(SUM(durationListenedMs) / 60000, 0) FROM reading_log WHERE (:translation = '' OR translationName = :translation)")
    fun totalMinutesListened(translation: String = ""): Flow<Long>

    /** How many times a specific chapter was fully played. */
    @Query("""
        SELECT COUNT(*) FROM reading_log
        WHERE bookNumber = :bookNumber AND chapterNumber = :chapterNumber
          AND (:translation = '' OR translationName = :translation)
    """)
    fun timesChapterPlayed(bookNumber: Int, chapterNumber: Int, translation: String = ""): Flow<Int>

    /** Top 5 most-played books for [translation]. */
    @Query("""
        SELECT bookName, bookNumber,
               COUNT(*) AS timesPlayed,
               COALESCE(SUM(durationListenedMs) / 60000, 0) AS totalMinutes
        FROM reading_log
        WHERE (:translation = '' OR translationName = :translation)
        GROUP BY bookNumber
        ORDER BY timesPlayed DESC
        LIMIT 5
    """)
    fun top5Books(translation: String = ""): Flow<List<BookStat>>

    /** All books stats for [translation]. */
    @Query("""
        SELECT bookName, bookNumber,
               COUNT(*) AS timesPlayed,
               COALESCE(SUM(durationListenedMs) / 60000, 0) AS totalMinutes
        FROM reading_log
        WHERE (:translation = '' OR translationName = :translation)
        GROUP BY bookNumber
        ORDER BY timesPlayed DESC
    """)
    fun allBookStats(translation: String = ""): Flow<List<BookStat>>

    /** Timestamp of the earliest record for [translation]. */
    @Query("SELECT MIN(timestamp) FROM reading_log WHERE (:translation = '' OR translationName = :translation)")
    fun earliestTimestamp(translation: String = ""): Flow<Long?>

    /** Heatmap data for [translation]. */
    @Query("""
        SELECT strftime('%Y-%m-%d', timestamp / 1000, 'unixepoch') AS dayLabel,
               COUNT(*) AS count
        FROM reading_log
        WHERE timestamp >= :since AND (:translation = '' OR translationName = :translation)
        GROUP BY dayLabel
        ORDER BY dayLabel ASC
    """)
    fun heatmap(since: Long, translation: String = ""): Flow<List<HeatmapDay>>

    /** All logs for a specific day (YYYY-MM-DD) for [translation]. */
    @Query("""
        SELECT * FROM reading_log
        WHERE strftime('%Y-%m-%d', timestamp / 1000, 'unixepoch') = :day
          AND (:translation = '' OR translationName = :translation)
        ORDER BY timestamp ASC
    """)
    fun logsForDay(day: String, translation: String = ""): Flow<List<ReadingLog>>

    /** One-shot history query for Android Auto (newest first, up to 50 entries). */
    @Query("SELECT * FROM reading_log ORDER BY timestamp DESC LIMIT 50")
    suspend fun recentHistoryOnce(): List<ReadingLog>

    /** All logs ordered newest first for [translation]. */
    @Query("SELECT * FROM reading_log WHERE (:translation = '' OR translationName = :translation) ORDER BY timestamp DESC LIMIT 50")
    fun recentHistory(translation: String = ""): Flow<List<ReadingLog>>

    /** Play counts for every chapter in a book. */
    @Query("""
        SELECT chapterNumber, COUNT(*) AS count
        FROM reading_log
        WHERE bookNumber = :bookNumber AND (:translation = '' OR translationName = :translation)
        GROUP BY chapterNumber
    """)
    fun chapterPlayCounts(bookNumber: Int, translation: String = ""): Flow<List<ChapterPlayCount>>

    /** Distinct chapters read per book. */
    @Query("""
        SELECT bookNumber, COUNT(DISTINCT chapterNumber) AS count
        FROM reading_log
        WHERE (:translation = '' OR translationName = :translation)
        GROUP BY bookNumber
    """)
    fun chaptersReadPerBook(translation: String = ""): Flow<List<BookChapterCount>>

    /** Delete history for [translation] (all translations if empty). */
    @Query("DELETE FROM reading_log WHERE (:translation = '' OR translationName = :translation)")
    suspend fun clearAll(translation: String = "")

    /** One-time migration: attribute unlabelled legacy rows to a translation. */
    @Query("UPDATE reading_log SET translationName = :translation WHERE translationName = ''")
    suspend fun migrateUnattributedLogs(translation: String)
}

data class BookChapterCount(
    val bookNumber: Int,
    val count: Int
)
