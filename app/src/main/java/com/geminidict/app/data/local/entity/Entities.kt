package com.geminidict.app.data.local.entity

import androidx.room.Entity
import androidx.room.Fts4
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Offline English <-> Chinese & English-English dictionary entry.
 */
@Entity(
    tableName = "dictionary_entries",
    indices = [
        Index(value = ["wordLower"]),
        Index(value = ["sourceId"]),
        Index(value = ["wordLower", "sourceId"], unique = true)
    ]
)
data class DictionaryEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val word: String,
    val wordLower: String,
    val phonetic: String = "",
    val definition: String = "",
    val translation: String = "",
    val pos: String = "",
    val collins: Int = 0,
    val oxford: Int = 0,
    val tag: String = "",
    val bnc: Int = 0,
    val frq: Int = 0,
    val exchange: String = "",
    val example: String = "",
    val exampleTranslation: String = "",
    val sourceId: String = "starter"
)

/**
 * Room FTS4 inverted index linked to `dictionary_entries` for millisecond full-text search
 * across translations and English definitions without full-table scans.
 */
@Fts4(contentEntity = DictionaryEntryEntity::class)
@Entity(tableName = "dictionary_fts")
data class DictionaryFtsEntity(
    val word: String,
    val translation: String,
    val definition: String
)

/**
 * Offline Chinese dictionary entry (Xinhua Word / Idiom / Ci / Xiehouyu).
 */
@Entity(
    tableName = "chinese_dict_entries",
    indices = [
        Index(value = ["headword"]),
        Index(value = ["category"]),
        Index(value = ["headword", "category"], unique = true)
    ]
)
data class ChineseDictEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val headword: String,
    val pinyin: String = "",
    val category: String, // "word", "idiom", "ci", "xiehouyu"
    val radicals: String = "",
    val strokes: String = "",
    val explanation: String = "",
    val derivation: String = "",
    val example: String = "",
    val more: String = "",
    val sourceId: String
)

@Fts4(contentEntity = ChineseDictEntryEntity::class)
@Entity(tableName = "chinese_dict_fts")
data class ChineseDictFtsEntity(
    val headword: String,
    val pinyin: String,
    val explanation: String
)

/**
 * User's "我的单词本" entry — supports Youdao wordbook features + SM-2 forgetting curve fields.
 */
@Entity(
    tableName = "wordbook_entries",
    indices = [
        Index(value = ["wordLower"], unique = true),
        Index(value = ["groupName"]),
        Index(value = ["nextReviewAt"])
    ]
)
data class WordbookEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val word: String,
    val wordLower: String,
    val phonetic: String = "",
    val translation: String = "",
    val definition: String = "",
    val exampleSentence: String = "",
    val exampleTranslation: String = "",
    val note: String = "",
    val groupName: String = "默认分组",
    val tags: String = "",
    val source: String = "manual",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),

    // SM-2 / Ebbinghaus Forgetting Curve Scheduling Fields
    val repetitions: Int = 0,
    val intervalDays: Int = 0,
    val easeFactor: Double = 2.5,
    val lapses: Int = 0,
    val masteryLevel: Int = 0,
    val lastReviewedAt: Long? = null,
    val nextReviewAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "review_logs",
    indices = [Index(value = ["reviewedAt"]), Index(value = ["wordbookId"])]
)
data class ReviewLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val wordbookId: Long,
    val word: String,
    val rating: Int, // 1=Again, 2=Hard, 3=Good, 4=Easy
    val intervalAfterDays: Int,
    val easeFactorAfter: Double,
    val reviewedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "downloaded_dicts")
data class DownloadedDictEntity(
    @PrimaryKey val id: String,
    val name: String,
    val category: String,
    val entryCount: Int,
    val downloadedAt: Long = System.currentTimeMillis()
)
