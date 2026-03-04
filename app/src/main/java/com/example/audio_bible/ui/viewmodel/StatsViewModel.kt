package com.example.audio_bible.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.example.audio_bible.data.db.BibleDatabase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import java.util.Calendar
import java.util.concurrent.TimeUnit

class StatsViewModel(app: Application) : AndroidViewModel(app) {

    private val dao = BibleDatabase.getInstance(app).statsDao()

    val totalPlays       = dao.totalPlays()
    val uniqueChapters   = dao.uniqueChapters()
    val totalMinutes     = dao.totalMinutesListened()
    val top5Books        = dao.top5Books()
    val allBookStats     = dao.allBookStats()
    val recentHistory    = dao.recentHistory()

    // Heatmap: all data from the very first recorded day
    @OptIn(ExperimentalCoroutinesApi::class)
    val heatmap = dao.earliestTimestamp().flatMapLatest { earliest ->
        dao.heatmap(since = earliest ?: System.currentTimeMillis())
    }

    // Selected day (YYYY-MM-DD) for the detail sheet; null = nothing selected
    val selectedDay = MutableStateFlow<String?>(null)

    @OptIn(ExperimentalCoroutinesApi::class)
    val selectedDayLogs = selectedDay.flatMapLatest { day ->
        if (day == null) flowOf(emptyList()) else dao.logsForDay(day)
    }

    // Full year of activity for streak calculation
    private val yearActivity = dao.heatmap(
        since = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(365)
    )

    /** Consecutive days ending today (or yesterday) with at least one play. */
    val currentStreak = yearActivity.map { days -> calcCurrentStreak(days.map { it.dayLabel }.toSet()) }

    /** Longest ever consecutive-day run. */
    val longestStreak = yearActivity.map { days -> calcLongestStreak(days.map { it.dayLabel }.toSet()) }

    /** Fraction of the Protestant Bible's 1189 chapters heard at least once. */
    val completionFraction = dao.uniqueChapters().map { it.toFloat() / 1189f }

    private fun calcCurrentStreak(daySet: Set<String>): Int {
        if (daySet.isEmpty()) return 0
        var streak = 0
        val cal = Calendar.getInstance()
        // Allow streak to count if last listened was yesterday
        while (true) {
            val key = calKey(cal)
            if (key !in daySet) {
                // If today is missing, check yesterday before giving up
                if (streak == 0) { cal.add(Calendar.DAY_OF_YEAR, -1); continue }
                break
            }
            streak++
            cal.add(Calendar.DAY_OF_YEAR, -1)
        }
        return streak
    }

    private fun calcLongestStreak(daySet: Set<String>): Int {
        if (daySet.isEmpty()) return 0
        val sorted = daySet.sorted()
        var longest = 1
        var current = 1
        for (i in 1 until sorted.size) {
            val prev = LocalDateHelper.parse(sorted[i - 1])
            val curr = LocalDateHelper.parse(sorted[i])
            if (curr - prev == 1L) {
                current++
                if (current > longest) longest = current
            } else {
                current = 1
            }
        }
        return longest
    }

    private fun calKey(cal: Calendar) = "%04d-%02d-%02d".format(
        cal.get(Calendar.YEAR),
        cal.get(Calendar.MONTH) + 1,
        cal.get(Calendar.DAY_OF_MONTH)
    )
}

/** Minimal date helper to avoid java.time API level issues. */
private object LocalDateHelper {
    /** Returns epoch days (days since 1970-01-01) for a "YYYY-MM-DD" string. */
    fun parse(s: String): Long {
        val y = s.substring(0, 4).toInt()
        val m = s.substring(5, 7).toInt()
        val d = s.substring(8, 10).toInt()
        val cal = Calendar.getInstance().apply {
            set(y, m - 1, d, 0, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return cal.timeInMillis / 86_400_000L
    }
}

