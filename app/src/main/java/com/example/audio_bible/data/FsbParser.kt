package com.example.audio_bible.data

import android.content.Context
import android.net.Uri
import com.example.audio_bible.data.db.BibleDatabase
import com.example.audio_bible.data.db.BibleVerse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray

object FsbParser {

    /**
     * Parse an .fsb file (JSON array: [hash, {name, books:[...]}])
     * and insert all verses into the database.
     * Calls [onProgress] with (inserted, total) for progress updates.
     * Returns the translation name on success.
     */
    suspend fun importFsb(
        context: Context,
        uri: Uri,
        onProgress: (Int, Int) -> Unit = { _, _ -> }
    ): String = withContext(Dispatchers.IO) {

        val json = context.contentResolver.openInputStream(uri)
            ?.bufferedReader()
            ?.readText()
            ?: error("Could not open file")

        val root      = JSONArray(json)
        val bible     = root.getJSONObject(1)
        val transName = bible.getString("name")
        val books     = bible.getJSONArray("books")

        // Count total verses for progress reporting
        var total = 0
        for (bi in 0 until books.length()) {
            val chapters = books.getJSONObject(bi).getJSONArray("chapters")
            for (ci in 0 until chapters.length()) {
                total += chapters.getJSONObject(ci).getJSONArray("verses").length()
            }
        }

        val dao    = BibleDatabase.getInstance(context).bibleTextDao()
        val batch  = mutableListOf<BibleVerse>()
        var done   = 0

        // Delete existing data for this translation so re-import is clean
        dao.deleteTranslation(transName)

        for (bi in 0 until books.length()) {
            val book     = books.getJSONObject(bi)
            val bookNum  = book.getInt("number")
            val chapters = book.getJSONArray("chapters")

            for (ci in 0 until chapters.length()) {
                val chapter   = chapters.getJSONObject(ci)
                val chapNum   = chapter.getString("number").toIntOrNull() ?: (ci + 1)
                val verses    = chapter.getJSONArray("verses")

                for (vi in 0 until verses.length()) {
                    val verse = verses.getJSONObject(vi)
                    batch += BibleVerse(
                        translationName = transName,
                        bookNumber      = bookNum,
                        chapterNumber   = chapNum,
                        verseNumber     = verse.getString("number").toIntOrNull() ?: (vi + 1),
                        text            = verse.getString("text")
                    )
                    done++
                    // Flush in batches of 500
                    if (batch.size >= 500) {
                        dao.insertVerses(batch.toList())
                        batch.clear()
                        onProgress(done, total)
                    }
                }
            }
        }

        if (batch.isNotEmpty()) {
            dao.insertVerses(batch)
            onProgress(done, total)
        }

        transName
    }
}
