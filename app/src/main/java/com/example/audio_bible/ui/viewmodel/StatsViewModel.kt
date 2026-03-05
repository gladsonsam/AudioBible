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

    private val _translation = MutableStateFlow(
        app.getSharedPreferences("bible_prefs", android.content.Context.MODE_PRIVATE)
            .getString("active_translation", "") ?: ""
    )

    fun setTranslationFilter(name: String) { _translation.value = name }

    @OptIn(ExperimentalCoroutinesApi::class)
    val totalPlays     = _translation.flatMapLatest { dao.totalPlays(it) }
    @OptIn(ExperimentalCoroutinesApi::class)
    val uniqueChapters = _translation.flatMapLatest { dao.uniqueChapters(it) }
    @OptIn(ExperimentalCoroutinesApi::class)
    val totalMinutes   = _translation.flatMapLatest { dao.totalMinutesListened(it) }
    @OptIn(ExperimentalCoroutinesApi::class)
    val top5Books      = _translation.flatMapLatest { dao.top5Books(it) }
    @OptIn(ExperimentalCoroutinesApi::class)
    val allBookStats   = _translation.flatMapLatest { dao.allBookStats(it) }
    @OptIn(ExperimentalCoroutinesApi::class)
    val recentHistory  = _translation.flatMapLatest { dao.recentHistory(it) }

    @OptIn(ExperimentalCoroutinesApi::class)
    val heatmap = _translation.flatMapLatest { trans ->
        dao.earliestTimestamp(trans).flatMapLatest { earliest ->
            dao.heatmap(since = earliest ?: System.currentTimeMillis(), translation = trans)
        }
    }

    val selectedDay = MutableStateFlow<String?>(null)

    @OptIn(ExperimentalCoroutinesApi::class)
    val selectedDayLogs = selectedDay.flatMapLatest { day ->
        if (day == null) flowOf(emptyList())
        else dao.logsForDay(day, _translation.value)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private val yearActivity = _translation.flatMapLatest { trans ->
        dao.heatmap(
            since = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(365),
            translation = trans
        )
    }

    val currentStreak = yearActivity.map { days -> calcCurrentStreak(days.map { it.dayLabel }.toSet()) }
    val longestStreak = yearActivity.map { days -> calcLongestStreak(days.map { it.dayLabel }.toSet()) }

    @OptIn(ExperimentalCoroutinesApi::class)
    val completionFraction = _translation.flatMapLatest { dao.uniqueChapters(it) }
        .map { it.toFloat() / 1189f }

    private fun calcCurrentStreak(daySet: Set<String>): Int {
        if (daySet.isEmpty()) return 0
        var streak = 0
        val cal = Calendar.getInstance()
        while (true) {
            val key = calKey(cal)
            if (key !in daySet) {
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


