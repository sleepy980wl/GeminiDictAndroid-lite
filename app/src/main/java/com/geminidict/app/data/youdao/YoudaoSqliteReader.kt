package com.geminidict.app.data.youdao

import android.database.sqlite.SQLiteDatabase
import java.io.File
import java.util.Locale

/**
 * Safely reads a Youdao SQLite database file (.db) extracted via ADB or backup.
 * Scans all candidate wordbook tables and deduplicates across tables.
 * Note: If the source SQLite DB uses WAL mode, copy accompanying -wal/-shm files alongside the .db
 * before importing to include the most recent uncommitted transactions.
 */
object YoudaoSqliteReader {

    fun readFromSqliteFile(dbFile: File): List<YoudaoParsedWord> {
        if (!dbFile.exists() || dbFile.length() == 0L) return emptyList()
        val dedupMap = LinkedHashMap<String, YoudaoParsedWord>()

        SQLiteDatabase.openDatabase(
            dbFile.absolutePath,
            null,
            SQLiteDatabase.OPEN_READONLY
        ).use { db ->
            val tables = mutableListOf<String>()
            db.rawQuery(
                "SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%' AND name NOT LIKE 'android_%'",
                null
            ).use { cursor ->
                while (cursor.moveToNext()) {
                    tables.add(cursor.getString(0))
                }
            }

            val prioritized = tables.sortedByDescending { name ->
                val lower = name.lowercase(Locale.ROOT)
                when {
                    lower.contains("word") || lower.contains("note") || lower.contains("book") -> 2
                    else -> 1
                }
            }

            for (table in prioritized) {
                val cols = mutableSetOf<String>()
                db.rawQuery("PRAGMA table_info(`$table`)", null).use { c ->
                    while (c.moveToNext()) {
                        cols.add(c.getString(1).lowercase(Locale.ROOT))
                    }
                }
                val wordCol = listOf("word", "word_text", "headword", "name", "spell")
                    .firstOrNull { it in cols } ?: continue
                val transCol = listOf("trans", "translation", "meaning", "explain", "explains", "desc")
                    .firstOrNull { it in cols }
                val phoneticCol = listOf("phonetic", "uk_phonetic", "us_phonetic", "pronunciation")
                    .firstOrNull { it in cols }
                val tagCol = listOf("tags", "tag", "book_name", "group_name", "category")
                    .firstOrNull { it in cols }
                val progressCol = listOf("progress", "mastery", "level", "review_count")
                    .firstOrNull { it in cols }

                val selectCols = listOfNotNull(wordCol, transCol, phoneticCol, tagCol, progressCol)
                    .joinToString(", ") { "`$it`" }

                db.rawQuery("SELECT $selectCols FROM `$table`", null).use { cursor ->
                    while (cursor.moveToNext()) {
                        val word = cursor.getString(0)?.trim().orEmpty()
                        if (word.isEmpty()) continue
                        var idx = 1
                        val trans = if (transCol != null) cursor.getString(idx++)?.trim().orEmpty() else ""
                        val phonetic = if (phoneticCol != null) cursor.getString(idx++)?.trim().orEmpty() else ""
                        val tags = if (tagCol != null) cursor.getString(idx++)?.trim().orEmpty() else "我的单词本"
                        val progress = if (progressCol != null) cursor.getInt(idx).coerceIn(0, 5) else 0

                        val key = word.lowercase(Locale.ROOT)
                        val existing = dedupMap[key]
                        if (existing == null || (existing.translation.isBlank() && trans.isNotBlank())) {
                            dedupMap[key] = YoudaoParsedWord(
                                word = word,
                                translation = trans,
                                phonetic = phonetic,
                                tags = tags,
                                progress = progress
                            )
                        }
                    }
                }
            }
        }
        return dedupMap.values.toList()
    }
}
