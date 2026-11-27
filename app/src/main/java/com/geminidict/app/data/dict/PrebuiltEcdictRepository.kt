package com.geminidict.app.data.dict

import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import com.geminidict.app.data.local.entity.DictionaryEntryEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.zip.GZIPInputStream

sealed class PrebuiltDbState {
    data object Idle : PrebuiltDbState()
    data class Extracting(val message: String, val mbWritten: Int = 0) : PrebuiltDbState()
    data class Ready(val entryCount: Int) : PrebuiltDbState()
    data object NotBundled : PrebuiltDbState()
    data class Error(val message: String) : PrebuiltDbState()
}

/**
 * Manages the standalone, pre-indexed read-only ECDICT Ultimate (3.4M entries) SQLite database
 * bundled inside APK `assets/prebuilt/ecdict_ultimate.db.gz`.
 *
 * Kept separate from Room's user database so:
 * 1. First launch unpacks in a few seconds with zero CSV parsing or FTS4 rebuilding on device.
 * 2. User wordbook & downloaded exam books stay isolated and safe across app upgrades.
 */
class PrebuiltEcdictRepository(private val context: Context) {

    companion object {
        const val ASSET_PATH = "prebuilt/ecdict_ultimate.db.gz"
        const val DB_FILENAME = "prebuilt_ecdict_ultimate.db"
        const val MARKER_FILENAME = "prebuilt_ecdict_ultimate.marker"
        const val EXPECTED_SCHEMA_VERSION = "1.6.1"
        const val SOURCE_ID = "ecdict_ultimate"
    }

    private val initMutex = Mutex()

    @Volatile
    private var readOnlyDb: SQLiteDatabase? = null

    private val _state = MutableStateFlow<PrebuiltDbState>(PrebuiltDbState.Idle)
    val state: StateFlow<PrebuiltDbState> = _state.asStateFlow()

    private val _entryCount = MutableStateFlow(0)
    val entryCount: StateFlow<Int> = _entryCount.asStateFlow()

    fun isReady(): Boolean = readOnlyDb?.isOpen == true

    suspend fun ensureReady() = withContext(Dispatchers.IO) {
        initMutex.withLock {
            if (readOnlyDb?.isOpen == true) return@withLock

            val dbFile = context.getDatabasePath(DB_FILENAME)
            val markerFile = File(dbFile.parentFile, MARKER_FILENAME)

            val assetSize = queryBundledAssetLength()
            if (assetSize <= 0L) {
                // Local dev build without bundled asset — still open if already extracted previously
                if (dbFile.exists() && markerFile.exists()) {
                    openAndValidate(dbFile)
                } else {
                    _state.value = PrebuiltDbState.NotBundled
                }
                return@withLock
            }

            val expectedMarker = "$EXPECTED_SCHEMA_VERSION:$assetSize"
            val currentMarker = if (markerFile.exists()) markerFile.readText().trim() else ""

            if (!dbFile.exists() || currentMarker != expectedMarker) {
                _state.value = PrebuiltDbState.Extracting("首次启动正在释放内置 340 万词库索引...")
                try {
                    extractAssetAtomically(dbFile, markerFile, expectedMarker)
                } catch (e: Exception) {
                    _state.value = PrebuiltDbState.Error("内置词库解压失败：${e.message}")
                    return@withLock
                }
            }

            openAndValidate(dbFile)
        }
    }

    private fun queryBundledAssetLength(): Long {
        return try {
            context.assets.openFd(ASSET_PATH).use { it.length }
        } catch (_: Exception) {
            // Fallback if compressed by AAPT2 (openFd fails on compressed assets)
            try {
                context.assets.open(ASSET_PATH).use { 1L }
            } catch (_: Exception) {
                -1L
            }
        }
    }

    private fun extractAssetAtomically(dbFile: File, markerFile: File, expectedMarker: String) {
        dbFile.parentFile?.mkdirs()
        val tmpFile = File(dbFile.parentFile, "$DB_FILENAME.tmp")
        if (tmpFile.exists()) tmpFile.delete()
        if (markerFile.exists()) markerFile.delete()

        var bytesWritten = 0L
        var lastReportedMb = -1

        context.assets.open(ASSET_PATH).use { raw ->
            GZIPInputStream(BufferedInputStream(raw, 128 * 1024), 128 * 1024).use { gz ->
                BufferedOutputStream(FileOutputStream(tmpFile), 256 * 1024).use { out ->
                    val buf = ByteArray(128 * 1024)
                    while (true) {
                        val read = gz.read(buf)
                        if (read <= 0) break
                        out.write(buf, 0, read)
                        bytesWritten += read
                        val mb = (bytesWritten / (1024L * 1024L)).toInt()
                        if (mb >= lastReportedMb + 40) {
                            lastReportedMb = mb
                            _state.value = PrebuiltDbState.Extracting(
                                message = "首次启动正在释放内置 340 万词库索引（已写入 ${mb} MB）...",
                                mbWritten = mb
                            )
                        }
                    }
                    out.flush()
                }
            }
        }

        if (dbFile.exists()) dbFile.delete()
        if (!tmpFile.renameTo(dbFile)) {
            tmpFile.copyTo(dbFile, overwrite = true)
            tmpFile.delete()
        }
        markerFile.writeText(expectedMarker)
    }

    private fun openAndValidate(dbFile: File) {
        try {
            val db = SQLiteDatabase.openDatabase(
                dbFile.absolutePath,
                null,
                SQLiteDatabase.OPEN_READONLY or SQLiteDatabase.NO_LOCALIZED_COLLATORS
            )
            var count = 0
            db.rawQuery("SELECT value FROM meta WHERE key = 'entry_count' LIMIT 1", null).use { c ->
                if (c.moveToFirst()) {
                    count = c.getString(0)?.toIntOrNull() ?: 0
                }
            }
            if (count <= 0) {
                db.rawQuery("SELECT COUNT(*) FROM entries", null).use { c ->
                    if (c.moveToFirst()) count = c.getInt(0)
                }
            }
            readOnlyDb = db
            _entryCount.value = count
            _state.value = PrebuiltDbState.Ready(count)
        } catch (e: Exception) {
            readOnlyDb = null
            _entryCount.value = 0
            _state.value = PrebuiltDbState.Error("内置词库打开失败：${e.message}")
        }
    }

    fun searchPrefixIndexed(
        queryLower: String,
        prefixPattern: String,
        rawLimit: Int = 60
    ): List<DictionaryEntryEntity> {
        val db = readOnlyDb ?: return emptyList()
        if (!db.isOpen) return emptyList()
        // Use B-Tree range scan (wordLower >= queryLower AND wordLower < upperBound) for instant prefix lookup
        val upperBound = computePrefixUpperBound(queryLower)
        val sql = if (upperBound != null) {
            """
            SELECT id, word, wordLower, phonetic, definition, translation, pos, collins, oxford, tag, frq
            FROM entries
            WHERE wordLower >= ? AND wordLower < ?
            ORDER BY
                CASE WHEN wordLower = ? THEN 0 ELSE 1 END,
                CASE WHEN frq > 0 THEN frq ELSE 999999 END ASC,
                LENGTH(word) ASC
            LIMIT ?
            """.trimIndent()
        } else {
            """
            SELECT id, word, wordLower, phonetic, definition, translation, pos, collins, oxford, tag, frq
            FROM entries
            WHERE wordLower = ? OR wordLower LIKE ? ESCAPE '\'
            ORDER BY
                CASE WHEN wordLower = ? THEN 0 ELSE 1 END,
                CASE WHEN frq > 0 THEN frq ELSE 999999 END ASC,
                LENGTH(word) ASC
            LIMIT ?
            """.trimIndent()
        }
        val args = if (upperBound != null) {
            arrayOf(queryLower, upperBound, queryLower, rawLimit.toString())
        } else {
            arrayOf(queryLower, prefixPattern, queryLower, rawLimit.toString())
        }
        return runQuery(db, sql, args)
    }

    fun getExactBatch(wordsLower: List<String>): List<DictionaryEntryEntity> {
        val db = readOnlyDb ?: return emptyList()
        if (!db.isOpen || wordsLower.isEmpty()) return emptyList()
        val placeholders = wordsLower.joinToString(",") { "?" }
        val sql = """
            SELECT id, word, wordLower, phonetic, definition, translation, pos, collins, oxford, tag, frq
            FROM entries
            WHERE wordLower IN ($placeholders)
        """.trimIndent()
        return runQuery(db, sql, wordsLower.toTypedArray())
    }

    fun searchFts(ftsQuery: String, rawLimit: Int = 50): List<DictionaryEntryEntity> {
        val db = readOnlyDb ?: return emptyList()
        if (!db.isOpen || ftsQuery.isBlank()) return emptyList()
        val sql = """
            SELECT e.id, e.word, e.wordLower, e.phonetic, e.definition, e.translation,
                   e.pos, e.collins, e.oxford, e.tag, e.frq
            FROM entries e
            JOIN entries_fts f ON e.id = f.docid
            WHERE entries_fts MATCH ?
            ORDER BY
                CASE WHEN e.frq > 0 THEN e.frq ELSE 999999 END ASC,
                LENGTH(e.word) ASC
            LIMIT ?
        """.trimIndent()
        return runQuery(db, sql, arrayOf(ftsQuery, rawLimit.toString()))
    }

    fun fetchFuzzyCandidates(
        prefix2: String,
        swappedPrefix2: String,
        minLen: Int,
        maxLen: Int,
        limit: Int = 300
    ): List<DictionaryEntryEntity> {
        val db = readOnlyDb ?: return emptyList()
        if (!db.isOpen) return emptyList()
        // Use fast B-Tree range queries on 2-letter prefix & swapped 2-letter prefix (instant on 3.4M rows)
        val p2Raw = prefix2.removeSuffix("%").replace("\\_", "_").replace("\\%", "%").replace("\\\\", "\\")
        val s2Raw = swappedPrefix2.removeSuffix("%").replace("\\_", "_").replace("\\%", "%").replace("\\\\", "\\")
        val p2Hi = computePrefixUpperBound(p2Raw)
        val s2Hi = computePrefixUpperBound(s2Raw)
        if (p2Hi == null || s2Hi == null) return emptyList()

        val sql = """
            SELECT id, word, wordLower, phonetic, definition, translation, pos, collins, oxford, tag, frq
            FROM entries
            WHERE LENGTH(wordLower) BETWEEN ? AND ?
              AND (
                  (wordLower >= ? AND wordLower < ?)
                  OR (wordLower >= ? AND wordLower < ?)
              )
            ORDER BY
                CASE WHEN frq > 0 THEN frq ELSE 999999 END ASC
            LIMIT ?
        """.trimIndent()
        return runQuery(
            db,
            sql,
            arrayOf(minLen.toString(), maxLen.toString(), p2Raw, p2Hi, s2Raw, s2Hi, limit.toString())
        )
    }

    private fun computePrefixUpperBound(prefix: String): String? {
        if (prefix.isEmpty()) return null
        val last = prefix.last()
        if (last == Char.MAX_VALUE) return null
        return prefix.dropLast(1) + (last + 1)
    }

    private fun runQuery(
        db: SQLiteDatabase,
        sql: String,
        args: Array<String>
    ): List<DictionaryEntryEntity> {
        val list = ArrayList<DictionaryEntryEntity>()
        return try {
            db.rawQuery(sql, args).use { c ->
                while (c.moveToNext()) {
                    list.add(readRow(c))
                }
            }
            list
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun readRow(c: Cursor): DictionaryEntryEntity {
        val rawId = c.getLong(0)
        return DictionaryEntryEntity(
            // Negative ID distinguishes prebuilt read-only rows from Room's positive autoincrement IDs
            id = -rawId,
            word = c.getString(1) ?: "",
            wordLower = c.getString(2) ?: "",
            phonetic = c.getString(3) ?: "",
            definition = c.getString(4) ?: "",
            translation = c.getString(5) ?: "",
            pos = c.getString(6) ?: "",
            collins = c.getInt(7),
            oxford = c.getInt(8),
            tag = c.getString(9) ?: "",
            frq = c.getInt(10),
            sourceId = SOURCE_ID
        )
    }

    fun close() {
        try {
            readOnlyDb?.close()
        } catch (_: Exception) {
        }
        readOnlyDb = null
    }
}
