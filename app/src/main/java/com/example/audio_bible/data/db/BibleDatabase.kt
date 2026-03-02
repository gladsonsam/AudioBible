package com.example.audio_bible.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [ReadingLog::class, BibleVerse::class],
    version = 2,
    exportSchema = false
)
abstract class BibleDatabase : RoomDatabase() {

    abstract fun statsDao(): StatsDao
    abstract fun bibleTextDao(): BibleTextDao

    companion object {
        @Volatile private var INSTANCE: BibleDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `bible_verse` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `translationName` TEXT NOT NULL,
                        `bookNumber` INTEGER NOT NULL,
                        `chapterNumber` INTEGER NOT NULL,
                        `verseNumber` INTEGER NOT NULL,
                        `text` TEXT NOT NULL
                    )
                """.trimIndent())
                db.execSQL("""
                    CREATE INDEX IF NOT EXISTS `index_bible_verse_translationName_bookNumber_chapterNumber`
                    ON `bible_verse` (`translationName`, `bookNumber`, `chapterNumber`)
                """.trimIndent())
            }
        }

        fun getInstance(context: Context): BibleDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    BibleDatabase::class.java,
                    "bible_stats.db"
                )
                    .addMigrations(MIGRATION_1_2)
                    .build()
                    .also { INSTANCE = it }
            }
    }
}
