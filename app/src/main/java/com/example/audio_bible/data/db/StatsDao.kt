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

    /** Total unique chapters ever completed, across all books. */
    @Query("SELECT COUNT(*) FROM reading_log")
    fun totalPlays(): Flow<Int>

    /** Total distinct chapters played (unique book+chapter combos). */
    @Query("SELECT COUNT(DISTINCT bookNumber || '_' || chapterNumber) FROM reading_log")
    fun uniqueChapters(): Flow<Int>

    /** Total listening time in minutes. */
    @Query("SELECT COALESCE(SUM(durationListenedMs) / 60000, 0) FROM reading_log")
    fun totalMinutesListened(): Flow<Long>

    /** How many times a specific chapter was fully played. */
    @Query("""
        SELECT COUNT(*) FROM reading_log
        WHERE bookNumber = :bookNumber AND chapterNumber = :chapterNumber
    """)
    fun timesChapterPlayed(bookNumber: Int, chapterNumber: Int): Flow<Int>

    /** Top 5 most-played books with total play count and total minutes. */
    @Query("""
        SELECT bookName, bookNumber,
               COUNT(*) AS timesPlayed,
               COALESCE(SUM(durationListenedMs) / 60000, 0) AS totalMinutes
        FROM reading_log
        GROUP BY bookNumber
        ORDER BY timesPlayed DESC
        LIMIT 5
    """)
    fun top5Books(): Flow<List<BookStat>>

    /** All books stats, ordered by most played, for the full breakdown list. */
    @Query("""
        SELECT bookName, bookNumber,
               COUNT(*) AS timesPlayed,
               COALESCE(SUM(durationListenedMs) / 60000, 0) AS totalMinutes
        FROM reading_log
        GROUP BY bookNumber
        ORDER BY timesPlayed DESC
    """)
    fun allBookStats(): Flow<List<BookStat>>

    /** Timestamp of the earliest record, or null if no records. */
    @Query("SELECT MIN(timestamp) FROM reading_log")
    fun earliestTimestamp(): Flow<Long?>

    /** Chapter play counts grouped by day for the heatmap. */
    @Query("""
        SELECT strftime('%Y-%m-%d', timestamp / 1000, 'unixepoch') AS dayLabel,
               COUNT(*) AS count
        FROM reading_log
        WHERE timestamp >= :since
        GROUP BY dayLabel
        ORDER BY dayLabel ASC
    """)
    fun heatmap(since: Long): Flow<List<HeatmapDay>>

    /** All logs for a specific day (YYYY-MM-DD), ordered by time. */
    @Query("""
        SELECT * FROM reading_log
        WHERE strftime('%Y-%m-%d', timestamp / 1000, 'unixepoch') = :day
        ORDER BY timestamp ASC
    """)
    fun logsForDay(day: String): Flow<List<ReadingLog>>

    /** One-shot history query for Android Auto (newest first, up to 50 entries). */
    @Query("SELECT * FROM reading_log ORDER BY timestamp DESC LIMIT 50")
    suspend fun recentHistoryOnce(): List<ReadingLog>

    /** All logs ordered newest first, for a recent history list. */
    @Query("SELECT * FROM reading_log ORDER BY timestamp DESC LIMIT 50")
    fun recentHistory(): Flow<List<ReadingLog>>

    /** Play counts for every chapter in a book — one query for the whole grid. */
    @Query("""
        SELECT chapterNumber, COUNT(*) AS count
        FROM reading_log
        WHERE bookNumber = :bookNumber
        GROUP BY chapterNumber
    """)
    fun chapterPlayCounts(bookNumber: Int): Flow<List<ChapterPlayCount>>

    /** Distinct chapters read per book: bookNumber → distinct chapter count. */
    @Query("""
        SELECT bookNumber, COUNT(DISTINCT chapterNumber) AS count
        FROM reading_log
        GROUP BY bookNumber
    """)
    fun chaptersReadPerBook(): Flow<List<BookChapterCount>>

    /** Delete all history. */
    @Query("DELETE FROM reading_log")
    suspend fun clearAll()
}

data class BookChapterCount(
    val bookNumber: Int,
    val count: Int
)
