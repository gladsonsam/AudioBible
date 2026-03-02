package com.example.audio_bible.data

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.core.content.edit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray

class BibleRepository(private val context: Context) {

    private val prefs = context.getSharedPreferences("bible_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val PREF_FOLDER_URI = "folder_uri"
        private val FILENAME_REGEX      = Regex("""^(\d+)_([A-Za-z0-9]+)_(\d+)\.mp3$""",       RegexOption.IGNORE_CASE)
        private val SYNC_FILENAME_REGEX = Regex("""^(\d+)_([A-Za-z0-9]+)_(\d+)_sync\.json$""", RegexOption.IGNORE_CASE)
    }

    fun getSavedFolderUri(): Uri? =
        prefs.getString(PREF_FOLDER_URI, null)?.let { Uri.parse(it) }

    fun saveFolderUri(uri: Uri) {
        prefs.edit { putString(PREF_FOLDER_URI, uri.toString()) }
    }

    suspend fun loadBooks(folderUri: Uri): List<BibleBook> = withContext(Dispatchers.IO) {
        // Use a single ContentResolver query instead of DocumentFile.listFiles()
        // which makes one IPC call per file (1000+ round-trips for a full Bible).
        val treeId      = DocumentsContract.getTreeDocumentId(folderUri)
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(folderUri, treeId)

        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE
        )

        val chapters = mutableListOf<BibleChapter>()

        context.contentResolver.query(childrenUri, projection, null, null, null)
            ?.use { cursor ->
                val idCol   = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                val nameCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                val mimeCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)

                while (cursor.moveToNext()) {
                    val mime = cursor.getString(mimeCol) ?: continue
                    if (mime == DocumentsContract.Document.MIME_TYPE_DIR) continue

                    val name  = cursor.getString(nameCol) ?: continue
                    val match = FILENAME_REGEX.matchEntire(name) ?: continue
                    val (bookNum, bookName, chapterNum) = match.destructured

                    val fileUri = DocumentsContract.buildDocumentUriUsingTree(
                        folderUri, cursor.getString(idCol)
                    )
                    chapters += BibleChapter(
                        bookNumber    = bookNum.toInt(),
                        bookName      = formatBookName(bookName),
                        chapterNumber = chapterNum.toInt(),
                        uri           = fileUri
                    )
                }
            }

        chapters
            .sortedWith(compareBy({ it.bookNumber }, { it.chapterNumber }))
            .groupBy { it.bookNumber }
            .map { (bookNum, bookChapters) ->
                BibleBook(
                    number   = bookNum,
                    name     = bookChapters.first().bookName,
                    chapters = bookChapters.sortedBy { it.chapterNumber }
                )
            }
            .sortedBy { it.number }
    }

    /**
     * Load verse-sync data for [chapter] from the `sync/` subfolder.
     * Returns an empty list if the file doesn't exist or can't be parsed.
     */
    suspend fun loadSyncData(folderUri: Uri, chapter: BibleChapter): List<VerseSync> =
        withContext(Dispatchers.IO) {
            try {
                // 1. Find the sync/ subfolder
                val treeId      = DocumentsContract.getTreeDocumentId(folderUri)
                val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(folderUri, treeId)
                val projection  = arrayOf(
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                    DocumentsContract.Document.COLUMN_MIME_TYPE
                )

                var syncDirId: String? = null
                context.contentResolver.query(childrenUri, projection, null, null, null)
                    ?.use { cursor ->
                        val idCol   = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                        val nameCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                        val mimeCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)
                        while (cursor.moveToNext()) {
                            if (cursor.getString(mimeCol) == DocumentsContract.Document.MIME_TYPE_DIR &&
                                cursor.getString(nameCol).equals("sync", ignoreCase = true)) {
                                syncDirId = cursor.getString(idCol)
                                break
                            }
                        }
                    }
                if (syncDirId == null) return@withContext emptyList()

                // 2. Find the matching _sync.json file by book + chapter number
                val syncChildrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(folderUri, syncDirId!!)
                var syncFileUri: Uri? = null
                context.contentResolver.query(syncChildrenUri, projection, null, null, null)
                    ?.use { cursor ->
                        val idCol   = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                        val nameCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                        while (cursor.moveToNext()) {
                            val name  = cursor.getString(nameCol) ?: continue
                            val match = SYNC_FILENAME_REGEX.matchEntire(name) ?: continue
                            if (match.groupValues[1].toIntOrNull() == chapter.bookNumber &&
                                match.groupValues[3].toIntOrNull() == chapter.chapterNumber) {
                                syncFileUri = DocumentsContract.buildDocumentUriUsingTree(
                                    folderUri, cursor.getString(idCol)
                                )
                                break
                            }
                        }
                    }
                if (syncFileUri == null) return@withContext emptyList()

                // 3. Parse JSON
                val json = context.contentResolver.openInputStream(syncFileUri!!)
                    ?.bufferedReader()?.readText() ?: return@withContext emptyList()
                val arr = JSONArray(json)
                List(arr.length()) { i ->
                    val obj = arr.getJSONObject(i)
                    VerseSync(
                        verse    = obj.getInt("v"),
                        startSec = obj.getDouble("s").toFloat(),
                        endSec   = obj.getDouble("e").toFloat()
                    )
                }.sortedBy { it.startSec }
            } catch (_: Exception) {
                emptyList()
            }
        }
}

/** "1Samuel" → "1 Samuel", "2Kings" → "2 Kings", plain names unchanged. */
private fun formatBookName(raw: String): String =
    raw.replace(Regex("""^(\d+)([A-Za-z])"""), "$1 $2")

