package com.example.audio_bible.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.example.audio_bible.MainActivity
import com.example.audio_bible.R
import com.example.audio_bible.service.AudioPlayerService

class BibleWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ids.forEach { updateWidget(context, manager, it) }
    }

    companion object {

        /** Call from the service whenever play state or chapter changes. */
        fun requestUpdate(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(
                ComponentName(context, BibleWidgetProvider::class.java)
            )
            if (ids.isEmpty()) return
            ids.forEach { updateWidget(context, manager, it) }
        }

        private fun updateWidget(context: Context, manager: AppWidgetManager, widgetId: Int) {
            val prefs = context.getSharedPreferences("bible_prefs", Context.MODE_PRIVATE)
            val bookName  = prefs.getString("last_book_name", null)
            val chapNo    = prefs.getInt("last_chapter_number", 0)
            val posMs     = prefs.getLong("last_position_ms", 0L)
            val durMs     = prefs.getLong("last_duration_ms", 0L)
            val isPlaying = prefs.getBoolean("is_playing", false)

            val views = RemoteViews(context.packageName, R.layout.widget_bible_player)

            if (bookName != null && chapNo > 0) {
                views.setTextViewText(R.id.widget_book_name, bookName)
                views.setTextViewText(R.id.widget_chapter, "Chapter $chapNo")
                val progress = if (durMs > 0) ((posMs.toFloat() / durMs) * 100).toInt() else 0
                views.setProgressBar(R.id.widget_progress, 100, progress, false)
                views.setTextViewText(R.id.widget_time, "${formatTime(posMs)} / ${formatTime(durMs)}")
            } else {
                views.setTextViewText(R.id.widget_book_name, "Audio Bible")
                views.setTextViewText(R.id.widget_chapter, "Tap to open")
                views.setProgressBar(R.id.widget_progress, 100, 0, false)
                views.setTextViewText(R.id.widget_time, "")
            }

            // Play/pause icon
            views.setImageViewResource(
                R.id.widget_play_button,
                if (isPlaying) android.R.drawable.ic_media_pause
                else android.R.drawable.ic_media_play
            )

            // Play/pause action
            val playAction = if (isPlaying) AudioPlayerService.ACTION_PAUSE
                             else AudioPlayerService.ACTION_RESUME
            val playIntent = Intent(context, AudioPlayerService::class.java).apply {
                action = playAction
            }
            val playPi = PendingIntent.getService(
                context, 0, playIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_play_button, playPi)

            // Tap widget body → open player screen
            val openIntent = Intent(context, MainActivity::class.java).apply {
                action = Intent.ACTION_MAIN
                putExtra(MainActivity.EXTRA_NAVIGATE_TO, "player")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            val openPi = PendingIntent.getActivity(
                context, 1, openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_body, openPi)

            manager.updateAppWidget(widgetId, views)
        }

        private fun formatTime(ms: Long): String {
            val total = ms / 1000
            val h = total / 3600
            val m = (total % 3600) / 60
            val s = total % 60
            return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
        }
    }
}
