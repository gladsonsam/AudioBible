package com.example.audio_bible.data

import android.content.Context
import android.net.Uri
import com.example.audio_bible.data.db.BibleDatabase
import com.example.audio_bible.data.db.ReadingLog
import com.example.audio_bible.data.db.TranslationProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

class HistoryManager(
    private val context: Context,
    private val db: BibleDatabase
) {
    suspend fun exportToUri(uri: Uri): Result<String> = withContext(Dispatchers.IO) {
        try {
            val logs = db.statsDao().recentHistoryOnce()
            val profiles = db.translationProfileDao().getAll().first()

            val prefs = context.getSharedPreferences("bible_prefs", Context.MODE_PRIVATE)
            val prefsJson = JSONObject().apply {
                put("active_translation", prefs.getString("active_translation", "") ?: "")
                put("folder_uri", prefs.getString("folder_uri", "") ?: "")
                put("last_uri", prefs.getString("last_uri", "") ?: "")
                put("last_book_name", prefs.getString("last_book_name", "") ?: "")
                put("last_book_number", prefs.getInt("last_book_number", 0))
                put("last_chapter_number", prefs.getInt("last_chapter_number", 0))
                put("last_position_ms", prefs.getLong("last_position_ms", 0L))
                put("default_speed", prefs.getFloat("default_speed", 1f))
            }

            val root = JSONObject().apply {
                put("version", 1)
                put("exported_at_ms", System.currentTimeMillis())
                put("preferences", prefsJson)

                put("reading_log", JSONArray().apply {
                    logs.forEach { log ->
                        put(JSONObject().apply {
                            put("id", log.id)
                            put("translationName", log.translationName)
                            put("bookNumber", log.bookNumber)
                            put("bookName", log.bookName)
                            put("chapterNumber", log.chapterNumber)
                            put("timestamp", log.timestamp)
                            put("durationListenedMs", log.durationListenedMs)
                        })
                    }
                })

                put("translation_profiles", JSONArray().apply {
                    profiles.forEach { profile ->
                        put(JSONObject().apply {
                            put("name", profile.name)
                            put("folderUri", profile.folderUri ?: "")
                            put("lastAudioUri", profile.lastAudioUri ?: "")
                            put("lastBookName", profile.lastBookName)
                            put("lastBookNumber", profile.lastBookNumber)
                            put("lastChapterNumber", profile.lastChapterNumber)
                            put("lastPositionMs", profile.lastPositionMs)
                        })
                    }
                })
            }

            context.contentResolver.openOutputStream(uri, "wt")?.use { out ->
                out.write(root.toString(2).toByteArray(Charsets.UTF_8))
            } ?: return@withContext Result.failure(IllegalStateException("Cannot open output stream"))

            Result.success(uri.toString())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun importFromUri(uri: Uri): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val jsonText = context.contentResolver.openInputStream(uri)?.use { input ->
                input.bufferedReader(Charsets.UTF_8).readText()
            } ?: return@withContext Result.failure(IllegalStateException("Cannot open input stream"))

            val root = JSONObject(jsonText)
            val prefsObj = root.optJSONObject("preferences") ?: JSONObject()

            val prefs = context.getSharedPreferences("bible_prefs", Context.MODE_PRIVATE)
            prefs.edit().apply {
                putString("active_translation", prefsObj.optString("active_translation", ""))
                putString("folder_uri", prefsObj.optString("folder_uri", ""))
                putString("last_uri", prefsObj.optString("last_uri", ""))
                putString("last_book_name", prefsObj.optString("last_book_name", ""))
                putInt("last_book_number", prefsObj.optInt("last_book_number", 0))
                putInt("last_chapter_number", prefsObj.optInt("last_chapter_number", 0))
                putLong("last_position_ms", prefsObj.optLong("last_position_ms", 0L))
                putFloat("default_speed", prefsObj.optDouble("default_speed", 1.0).toFloat())
            }.apply()

            val logs = root.optJSONArray("reading_log") ?: JSONArray()
            for (i in 0 until logs.length()) {
                val j = logs.getJSONObject(i)
                db.statsDao().insert(
                    ReadingLog(
                        id = 0L, // avoid primary key collisions on import
                        translationName = j.optString("translationName", ""),
                        bookNumber = j.optInt("bookNumber", 0),
                        bookName = j.optString("bookName", ""),
                        chapterNumber = j.optInt("chapterNumber", 0),
                        timestamp = j.optLong("timestamp", 0L),
                        durationListenedMs = j.optLong("durationListenedMs", 0L)
                    )
                )
            }

            val profiles = root.optJSONArray("translation_profiles") ?: JSONArray()
            for (i in 0 until profiles.length()) {
                val j = profiles.getJSONObject(i)
                db.translationProfileDao().upsert(
                    TranslationProfile(
                        name = j.getString("name"),
                        folderUri = j.optString("folderUri", "").ifEmpty { null },
                        lastAudioUri = j.optString("lastAudioUri", "").ifEmpty { null },
                        lastBookName = j.optString("lastBookName", ""),
                        lastBookNumber = j.optInt("lastBookNumber", 0),
                        lastChapterNumber = j.optInt("lastChapterNumber", 0),
                        lastPositionMs = j.optLong("lastPositionMs", 0L)
                    )
                )
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

