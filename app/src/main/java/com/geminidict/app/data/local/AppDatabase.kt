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
import com.geminidict.app.data.local.entity.ChineseDictFtsEntity
import com.geminidict.app.data.local.entity.DictionaryEntryEntity
import com.geminidict.app.data.local.entity.DictionaryFtsEntity
import com.geminidict.app.data.local.entity.DownloadedDictEntity
import com.geminidict.app.data.local.entity.ReviewLogEntity
import com.geminidict.app.data.local.entity.WordbookEntryEntity

@Database(
    entities = [
        DictionaryEntryEntity::class,
        DictionaryFtsEntity::class,
        ChineseDictEntryEntity::class,
        ChineseDictFtsEntity::class,
        WordbookEntryEntity::class,
        ReviewLogEntity::class,
        DownloadedDictEntity::class
    ],
    version = 4,
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

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE dictionary_entries ADD COLUMN exampleTranslation TEXT NOT NULL DEFAULT ''"
                )
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Create FTS4 virtual tables & Room content-sync triggers, then rebuild indexes
                db.execSQL(
                    "CREATE VIRTUAL TABLE IF NOT EXISTS `dictionary_fts` USING FTS4(`word` TEXT NOT NULL, `translation` TEXT NOT NULL, `definition` TEXT NOT NULL, content=`dictionary_entries`)"
                )
                db.execSQL(
                    "CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_dictionary_fts_BEFORE_UPDATE BEFORE UPDATE ON `dictionary_entries` BEGIN DELETE FROM `dictionary_fts` WHERE `docid`=OLD.`rowid`; END"
                )
                db.execSQL(
                    "CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_dictionary_fts_BEFORE_DELETE BEFORE DELETE ON `dictionary_entries` BEGIN DELETE FROM `dictionary_fts` WHERE `docid`=OLD.`rowid`; END"
                )
                db.execSQL(
                    "CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_dictionary_fts_AFTER_UPDATE AFTER UPDATE ON `dictionary_entries` BEGIN INSERT INTO `dictionary_fts`(`docid`, `word`, `translation`, `definition`) VALUES (NEW.`rowid`, NEW.`word`, NEW.`translation`, NEW.`definition`); END"
                )
                db.execSQL(
                    "CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_dictionary_fts_AFTER_INSERT AFTER INSERT ON `dictionary_entries` BEGIN INSERT INTO `dictionary_fts`(`docid`, `word`, `translation`, `definition`) VALUES (NEW.`rowid`, NEW.`word`, NEW.`translation`, NEW.`definition`); END"
                )
                db.execSQL("INSERT INTO `dictionary_fts`(`dictionary_fts`) VALUES('rebuild')")

                db.execSQL(
                    "CREATE VIRTUAL TABLE IF NOT EXISTS `chinese_dict_fts` USING FTS4(`headword` TEXT NOT NULL, `pinyin` TEXT NOT NULL, `explanation` TEXT NOT NULL, content=`chinese_dict_entries`)"
                )
                db.execSQL(
                    "CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_chinese_dict_fts_BEFORE_UPDATE BEFORE UPDATE ON `chinese_dict_entries` BEGIN DELETE FROM `chinese_dict_fts` WHERE `docid`=OLD.`rowid`; END"
                )
                db.execSQL(
                    "CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_chinese_dict_fts_BEFORE_DELETE BEFORE DELETE ON `chinese_dict_entries` BEGIN DELETE FROM `chinese_dict_fts` WHERE `docid`=OLD.`rowid`; END"
                )
                db.execSQL(
                    "CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_chinese_dict_fts_AFTER_UPDATE AFTER UPDATE ON `chinese_dict_entries` BEGIN INSERT INTO `chinese_dict_fts`(`docid`, `headword`, `pinyin`, `explanation`) VALUES (NEW.`rowid`, NEW.`headword`, NEW.`pinyin`, NEW.`explanation`); END"
                )
                db.execSQL(
                    "CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_chinese_dict_fts_AFTER_INSERT AFTER INSERT ON `chinese_dict_entries` BEGIN INSERT INTO `chinese_dict_fts`(`docid`, `headword`, `pinyin`, `explanation`) VALUES (NEW.`rowid`, NEW.`headword`, NEW.`pinyin`, NEW.`explanation`); END"
                )
                db.execSQL("INSERT INTO `chinese_dict_fts`(`chinese_dict_fts`) VALUES('rebuild')")
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Wordbook uniqueness becomes (wordLower, source): the same
                // spelling can be a different word in another language
                // (French "chat" vs English "chat"), so both must coexist.
                // Existing data has no wordLower duplicates (the old index
                // enforced that), so the new index builds cleanly.
                db.execSQL("DROP INDEX IF EXISTS `index_wordbook_entries_wordLower`")
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_wordbook_entries_wordLower_source` " +
                        "ON `wordbook_entries` (`wordLower`, `source`)"
                )
            }
        }

        val ALL_MIGRATIONS: Array<Migration> = arrayOf(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)

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
