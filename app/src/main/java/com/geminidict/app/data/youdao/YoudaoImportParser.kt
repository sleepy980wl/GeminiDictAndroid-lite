package com.geminidict.app.data.youdao

import com.geminidict.app.data.dict.DictionaryParsers
import com.geminidict.app.data.local.entity.WordbookEntryEntity
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.BufferedReader
import java.io.StringReader
import java.util.Locale

data class YoudaoParsedWord(
    val word: String,
    val translation: String = "",
    val phonetic: String = "",
    val tags: String = "",
    val progress: Int = 0
)

/**
 * Pure Kotlin parser & exporter for Youdao Dictionary "我的单词本" formats:
 * - Official Youdao XML (<wordbook><item>...</item></wordbook>)
 * - Youdao TXT / TSV exports
 * - RFC-4180 CSV exports (including multiline quoted fields)
 */
object YoudaoImportParser {

    private const val ONE_DAY_MILLIS = 24L * 60L * 60L * 1000L

    fun parseAuto(content: String, fileName: String = ""): List<YoudaoParsedWord> {
        val trimmed = content.trimStart('\uFEFF').trim()
        if (trimmed.isEmpty()) return emptyList()

        return when {
            trimmed.startsWith("<") || fileName.endsWith(".xml", ignoreCase = true) -> {
                parseYoudaoXml(trimmed)
            }
            fileName.endsWith(".csv", ignoreCase = true) -> {
                parseCsv(trimmed)
            }
            else -> {
                parseYoudaoTxt(trimmed)
            }
        }
    }

    /**
     * Parses Youdao XML wordbook format safely:
     * Only treats top-level <item> or <alter> as an entry container;
     * any nested <alter> inside an active <item> is ignored so it never wipes the current word.
     */
    fun parseYoudaoXml(xmlContent: String): List<YoudaoParsedWord> {
        val results = mutableListOf<YoudaoParsedWord>()
        val factory = XmlPullParserFactory.newInstance().apply {
            isNamespaceAware = false
        }
        val parser = factory.newPullParser()
        parser.setInput(StringReader(xmlContent))

        var inItem = false
        var itemDepth = -1
        var currentTag: String? = null
        var word = ""
        var trans = ""
        var phonetic = ""
        var tags = ""
        var progress = 0

        var eventType = parser.eventType
        while (eventType != XmlPullParser.END_DOCUMENT) {
            when (eventType) {
                XmlPullParser.START_TAG -> {
                    val name = parser.name.lowercase(Locale.ROOT)
                    if (!inItem && (name == "item" || name == "alter")) {
                        inItem = true
                        itemDepth = parser.depth
                        word = ""
                        trans = ""
                        phonetic = ""
                        tags = ""
                        progress = 0
                    } else if (inItem && parser.depth == itemDepth + 1) {
                        // Only capture direct child fields of the active item
                        currentTag = if (name in setOf("word", "trans", "phonetic", "tags", "progress")) {
                            name
                        } else {
                            null
                        }
                    }
                }
                XmlPullParser.TEXT, XmlPullParser.CDSECT -> {
                    if (inItem && currentTag != null) {
                        val text = parser.text.orEmpty()
                        when (currentTag) {
                            "word" -> word += text
                            "trans" -> trans += text
                            "phonetic" -> phonetic += text
                            "tags" -> tags += text
                            "progress" -> progress = text.trim().toIntOrNull() ?: progress
                        }
                    }
                }
                XmlPullParser.END_TAG -> {
                    val name = parser.name.lowercase(Locale.ROOT)
                    if (inItem && parser.depth == itemDepth && (name == "item" || name == "alter")) {
                        val cleanedWord = word.trim()
                        if (cleanedWord.isNotEmpty()) {
                            results.add(
                                YoudaoParsedWord(
                                    word = cleanedWord,
                                    translation = trans.trim(),
                                    phonetic = phonetic.trim().removeSurrounding("[", "]"),
                                    tags = tags.trim(),
                                    progress = progress.coerceIn(0, 5)
                                )
                            )
                        }
                        inItem = false
                        itemDepth = -1
                    }
                    if (parser.depth == itemDepth + 1) {
                        currentTag = null
                    }
                }
            }
            eventType = parser.next()
        }
        return deduplicate(results)
    }

    fun parseYoudaoTxt(txtContent: String): List<YoudaoParsedWord> {
        val lines = txtContent.lines()
        val results = mutableListOf<YoudaoParsedWord>()
        val numberedHeaderRegex = Regex("""^\d+[,.、\s]+([A-Za-z][A-Za-z0-9\s\-']*?)(?:\s+\[([^\]]+)\])?\s*$""")

        var i = 0
        while (i < lines.size) {
            val line = lines[i].trim()
            if (line.isEmpty()) {
                i++
                continue
            }

            if (line.contains("\t")) {
                val parts = line.split("\t")
                val word = parts.getOrNull(0)?.trim().orEmpty()
                if (word.isNotEmpty() && !word.equals("word", ignoreCase = true)) {
                    results.add(
                        YoudaoParsedWord(
                            word = word,
                            translation = parts.getOrNull(1)?.trim().orEmpty(),
                            phonetic = parts.getOrNull(2)?.trim()?.removeSurrounding("[", "]").orEmpty(),
                            tags = parts.getOrNull(3)?.trim().orEmpty()
                        )
                    )
                }
                i++
                continue
            }

            val numberedMatch = numberedHeaderRegex.matchEntire(line)
            if (numberedMatch != null) {
                val word = numberedMatch.groupValues[1].trim()
                val phonetic = numberedMatch.groupValues[2].trim()
                val transBuilder = StringBuilder()
                i++
                while (i < lines.size) {
                    val nextLine = lines[i].trim()
                    if (nextLine.isEmpty() || numberedHeaderRegex.matches(nextLine)) break
                    if (transBuilder.isNotEmpty()) transBuilder.append("\n")
                    transBuilder.append(nextLine)
                    i++
                }
                results.add(
                    YoudaoParsedWord(
                        word = word,
                        translation = transBuilder.toString(),
                        phonetic = phonetic
                    )
                )
                continue
            }

            val dashParts = line.split(" - ", limit = 2)
            val word = dashParts[0].trim()
            val trans = dashParts.getOrNull(1)?.trim().orEmpty()
            if (word.isNotEmpty()) {
                results.add(YoudaoParsedWord(word = word, translation = trans))
            }
            i++
        }
        return deduplicate(results)
    }

    /**
     * Uses RFC-4180 streaming CSV record reader so multi-line quoted translations parse cleanly.
     */
    fun parseCsv(csvContent: String): List<YoudaoParsedWord> {
        val results = mutableListOf<YoudaoParsedWord>()
        BufferedReader(StringReader(csvContent)).use { reader ->
            var isFirst = true
            while (true) {
                val cols = DictionaryParsers.readCsvRecord(reader) ?: break
                if (cols.isEmpty() || (cols.size == 1 && cols[0].isBlank())) continue
                val first = cols[0].removePrefix("\uFEFF").trim()
                if (isFirst) {
                    isFirst = false
                    if (first.equals("word", true) || first == "单词") continue
                }
                if (first.isNotEmpty()) {
                    results.add(
                        YoudaoParsedWord(
                            word = first,
                            translation = cols.getOrNull(1)?.trim().orEmpty(),
                            phonetic = cols.getOrNull(2)?.trim().orEmpty(),
                            tags = cols.getOrNull(3)?.trim().orEmpty(),
                            progress = cols.getOrNull(4)?.trim()?.toIntOrNull()?.coerceIn(0, 5) ?: 0
                        )
                    )
                }
            }
        }
        return deduplicate(results)
    }

    /**
     * Converts parsed Youdao entries into Room WordbookEntryEntity objects.
     * Staggers nextReviewAt according to existing Youdao mastery `progress` (0..5)
     * so importing hundreds of words doesn't flood Day 1 with already-learned vocabulary.
     */
    fun toWordbookEntities(
        parsed: List<YoudaoParsedWord>,
        sourceLabel: String = "youdao_import",
        nowMillis: Long = System.currentTimeMillis()
    ): List<WordbookEntryEntity> {
        return parsed.map { item ->
            val group = "我的单词本"
            val intervalDays = when (item.progress) {
                0 -> 0
                1 -> 1
                2 -> 4
                3 -> 10
                4 -> 25
                else -> 60
            }
            val nextReview = nowMillis + intervalDays * ONE_DAY_MILLIS
            WordbookEntryEntity(
                word = item.word,
                wordLower = item.word.lowercase(Locale.ROOT),
                phonetic = item.phonetic,
                translation = item.translation,
                groupName = group,
                tags = item.tags,
                source = sourceLabel,
                createdAt = nowMillis,
                updatedAt = nowMillis,
                masteryLevel = item.progress,
                repetitions = item.progress,
                intervalDays = intervalDays,
                nextReviewAt = nextReview
            )
        }
    }

    fun exportToYoudaoXml(entries: List<WordbookEntryEntity>): String {
        val sb = StringBuilder()
        sb.appendLine("<wordbook>")
        for (entry in entries) {
            sb.appendLine("  <item>")
            sb.appendLine("    <word>${escapeXml(entry.word)}</word>")
            sb.appendLine("    <trans><![CDATA[${entry.translation.replace("]]>", "]]]]><![CDATA[>")}]]></trans>")
            sb.appendLine("    <phonetic><![CDATA[[${entry.phonetic.replace("]]>", "")}]]]></phonetic>")
            val tagStr = entry.tags.ifBlank { entry.groupName }
            sb.appendLine("    <tags>${escapeXml(tagStr)}</tags>")
            sb.appendLine("    <progress>${entry.masteryLevel}</progress>")
            sb.appendLine("  </item>")
        }
        sb.appendLine("</wordbook>")
        return sb.toString()
    }

    private fun escapeXml(raw: String): String = raw
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&apos;")

    private fun deduplicate(list: List<YoudaoParsedWord>): List<YoudaoParsedWord> {
        val map = LinkedHashMap<String, YoudaoParsedWord>()
        for (item in list) {
            val key = item.word.lowercase(Locale.ROOT)
            val existing = map[key]
            if (existing == null || (existing.translation.isBlank() && item.translation.isNotBlank())) {
                map[key] = item
            }
        }
        return map.values.toList()
    }
}
