package com.geminidict.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.geminidict.app.data.local.dao.ChineseDictDao
import com.geminidict.app.data.local.dao.DictionaryDao
import com.geminidict.app.data.local.dao.DownloadedDictDao
import com.geminidict.app.data.local.dao.ReviewLogDao
import com.geminidict.app.data.local.dao.WordbookDao
import com.geminidict.app.data.local.entity.ChineseDictEntryEntity
import com.geminidict.app.data.local.entity.DictionaryEntryEntity
import com.geminidict.app.data.local.entity.DownloadedDictEntity
import com.geminidict.app.data.local.entity.ReviewLogEntity
import com.geminidict.app.data.local.entity.WordbookEntryEntity

@Database(
    entities = [
        DictionaryEntryEntity::class,
        ChineseDictEntryEntity::class,
        WordbookEntryEntity::class,
        ReviewLogEntity::class,
        DownloadedDictEntity::class
    ],
    version = 2,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun dictionaryDao(): DictionaryDao
    abstract fun chineseDictDao(): ChineseDictDao
    abstract fun wordbookDao(): WordbookDao
    abstract fun reviewLogDao(): ReviewLogDao
    abstract fun downloadedDictDao(): DownloadedDictDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        /**
         * Explicit v1 -> v2 migration adding `exampleTranslation` to `dictionary_entries`
         * so existing user wordbooks & downloaded dictionaries upgrade seamlessly without data loss.
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE dictionary_entries ADD COLUMN exampleTranslation TEXT NOT NULL DEFAULT ''"
                )
            }
        }

        val ALL_MIGRATIONS: Array<Migration> = arrayOf(MIGRATION_1_2)

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "geminidict_database.db"
                )
                    .addMigrations(*ALL_MIGRATIONS)
                    .fallbackToDestructiveMigrationOnDowngrade()
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
