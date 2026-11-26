package com.geminidict.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.geminidict.app.data.local.entity.ChineseDictEntryEntity
import com.geminidict.app.data.local.entity.DictionaryEntryEntity
import com.geminidict.app.data.local.entity.DownloadedDictEntity
import com.geminidict.app.data.local.entity.ReviewLogEntity
import com.geminidict.app.data.local.entity.WordbookEntryEntity
import kotlinx.coroutines.flow.Flow

object SqlLikeEscaper {
    fun escape(raw: String): String {
        return raw
            .replace("\\", "\\\\")
            .replace("%", "\\%")
            .replace("_", "\\_")
    }
}

@Dao
interface DictionaryDao {
    @Query(
        """
        SELECT * FROM dictionary_entries
        WHERE wordLower = :queryLower
           OR wordLower LIKE :prefixPattern ESCAPE '\'
           OR translation LIKE :containsPattern ESCAPE '\'
        ORDER BY
            CASE
                WHEN wordLower = :queryLower THEN 0
                WHEN wordLower LIKE :prefixPattern ESCAPE '\' THEN 1
                ELSE 2
            END,
            CASE sourceId
                WHEN 'ecdict_full' THEN 0
                WHEN 'cc_cedict' THEN 1
                WHEN 'starter' THEN 2
                ELSE 3
            END,
            LENGTH(translation) DESC,
            CASE WHEN frq > 0 THEN frq ELSE 999999 END ASC,
            LENGTH(word) ASC
        LIMIT :rawLimit
        """
    )
    suspend fun searchRaw(
        queryLower: String,
        prefixPattern: String,
        containsPattern: String,
        rawLimit: Int = 90
    ): List<DictionaryEntryEntity>

    @Query("SELECT * FROM dictionary_entries WHERE wordLower IN (:wordsLower)")
    suspend fun getExactBatch(wordsLower: List<String>): List<DictionaryEntryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entries: List<DictionaryEntryEntity>)

    @Query("DELETE FROM dictionary_entries WHERE sourceId = :sourceId")
    suspend fun deleteBySource(sourceId: String)

    @Query("SELECT COUNT(*) FROM dictionary_entries")
    fun observeTotalCount(): Flow<Int>
}

@Dao
interface ChineseDictDao {
    @Query(
        """
        SELECT * FROM chinese_dict_entries
        WHERE headword = :query
           OR headword LIKE :prefixPattern ESCAPE '\'
           OR explanation LIKE :containsPattern ESCAPE '\'
        ORDER BY
            CASE
                WHEN headword = :query THEN 0
                WHEN headword LIKE :prefixPattern ESCAPE '\' THEN 1
                ELSE 2
            END,
            LENGTH(headword) ASC
        LIMIT :limit
        """
    )
    suspend fun search(
        query: String,
        prefixPattern: String,
        containsPattern: String,
        limit: Int = 30
    ): List<ChineseDictEntryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entries: List<ChineseDictEntryEntity>)

    @Query("DELETE FROM chinese_dict_entries WHERE sourceId = :sourceId")
    suspend fun deleteBySource(sourceId: String)
}

@Dao
interface WordbookDao {
    @Query("SELECT * FROM wordbook_entries ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<WordbookEntryEntity>>

    @Query("SELECT DISTINCT groupName FROM wordbook_entries ORDER BY groupName ASC")
    fun observeGroups(): Flow<List<String>>

    @Query("SELECT * FROM wordbook_entries WHERE wordLower = :wordLower LIMIT 1")
    suspend fun findByWordLower(wordLower: String): WordbookEntryEntity?

    @Query("SELECT * FROM wordbook_entries WHERE nextReviewAt <= :nowMillis ORDER BY nextReviewAt ASC LIMIT :limit")
    suspend fun getDueForReview(nowMillis: Long, limit: Int = 50): List<WordbookEntryEntity>

    @Query("SELECT * FROM wordbook_entries ORDER BY createdAt DESC")
    suspend fun getAllSnapshot(): List<WordbookEntryEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAllIgnore(entries: List<WordbookEntryEntity>): List<Long>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: WordbookEntryEntity): Long

    @Update
    suspend fun update(entry: WordbookEntryEntity)

    @Query("DELETE FROM wordbook_entries WHERE id = :id")
    suspend fun deleteById(id: Long)
}

@Dao
interface ReviewLogDao {
    @Insert
    suspend fun insert(log: ReviewLogEntity)

    /**
     * Counts today's successfully recalled reviews (excluding rating == 1 "AGAIN/忘记").
     */
    @Query("SELECT COUNT(*) FROM review_logs WHERE reviewedAt >= :sinceMillis AND rating > 1")
    fun observeLearnedCountSince(sinceMillis: Long): Flow<Int>
}

@Dao
interface DownloadedDictDao {
    @Query("SELECT * FROM downloaded_dicts ORDER BY downloadedAt DESC")
    fun observeAll(): Flow<List<DownloadedDictEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: DownloadedDictEntity)

    @Query("DELETE FROM downloaded_dicts WHERE id = :id")
    suspend fun deleteById(id: String)
}
