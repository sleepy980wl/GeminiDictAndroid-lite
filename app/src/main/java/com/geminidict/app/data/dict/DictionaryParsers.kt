package com.geminidict.app.data.dict

import com.geminidict.app.data.local.entity.ChineseDictEntryEntity
import com.geminidict.app.data.local.entity.DictionaryEntryEntity
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.stream.JsonReader
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.io.Reader
import java.nio.charset.StandardCharsets
import java.util.Locale
import java.util.zip.GZIPInputStream
import java.util.zip.ZipInputStream

/**
 * High-speed streaming parsers for:
 * - ECDICT CSV / ECDICT Ultimate CSV ZIP (fast BufferedReader.readLine + RFC-4180 multi-line quote continuation)
 * - Webster's Unabridged JSON ({ "word": "definition" })
 * - CC-CEDICT (plain/gzip)
 * - kajweb/dict JSONL ZIP (bilingual example sentences)
 * - Chinese Xinhua JSON (word/idiom/ci/xiehouyu)
 * - Generic local TSV / CSV user dictionaries
 */
object DictionaryParsers {

    private val gson = Gson()

    /**
     * Fast RFC-4180 CSV record reader:
     * Uses BufferedReader.readLine() for the 99.99% single-line case (10x faster than char-by-char mark/reset),
     * and automatically appends continuation lines when an odd number of unescaped quotes indicates a multi-line quoted field.
     */
    fun readCsvRecord(reader: Reader): List<String>? {
        val br = reader as? BufferedReader ?: BufferedReader(reader)
        val firstLine = br.readLine() ?: return null
        val sb = StringBuilder(firstLine)
        var inQuotes = hasUnclosedQuote(firstLine)
        while (inQuotes) {
            val nextLine = br.readLine() ?: break
            sb.append('\n').append(nextLine)
            inQuotes = hasUnclosedQuote(sb)
        }
        return splitCsvFields(sb)
    }

    private fun hasUnclosedQuote(cs: CharSequence): Boolean {
        var inQuotes = false
        var i = 0
        val len = cs.length
        while (i < len) {
            if (cs[i] == '"') {
                if (inQuotes && i + 1 < len && cs[i + 1] == '"') {
                    i += 2
                    continue
                }
                inQuotes = !inQuotes
            }
            i++
        }
        return inQuotes
    }

    private fun splitCsvFields(record: CharSequence): List<String> {
        val fields = ArrayList<String>(13)
        val sb = StringBuilder()
        var inQuotes = false
        var i = 0
        val len = record.length
        while (i < len) {
            val ch = record[i]
            if (inQuotes) {
                if (ch == '"') {
                    if (i + 1 < len && record[i + 1] == '"') {
                        sb.append('"')
                        i += 2
                        continue
                    }
                    inQuotes = false
                } else {
                    sb.append(ch)
                }
            } else {
                when (ch) {
                    '"' -> inQuotes = true
                    ',' -> {
                        fields.add(sb.toString())
                        sb.setLength(0)
                    }
                    else -> sb.append(ch)
                }
            }
            i++
        }
        fields.add(sb.toString())
        return fields
    }

    suspend fun parseEcdictCsvStream(
        inputStream: InputStream,
        sourceId: String,
        batchSize: Int = 2000,
        onBatch: suspend (List<DictionaryEntryEntity>) -> Unit
    ): Int {
        var total = 0
        val batch = ArrayList<DictionaryEntryEntity>(batchSize)
        BufferedReader(InputStreamReader(inputStream, StandardCharsets.UTF_8), 64 * 1024).use { reader ->
            var isFirst = true
            while (true) {
                val cols = readCsvRecord(reader) ?: break
                if (cols.isEmpty() || (cols.size == 1 && cols[0].isBlank())) continue
                val firstCol = cols[0].removePrefix("\uFEFF").trim()
                if (isFirst) {
                    isFirst = false
                    if (firstCol.equals("word", ignoreCase = true)) continue
                }
                if (firstCol.isEmpty()) continue

                val phonetic = cols.getOrNull(1)?.trim().orEmpty()
                val definition = cols.getOrNull(2)?.replace("\\n", "\n")?.trim().orEmpty()
                val translation = cols.getOrNull(3)?.replace("\\n", "\n")?.trim().orEmpty()
                val pos = cols.getOrNull(4)?.trim().orEmpty()
                val collins = cols.getOrNull(5)?.trim()?.toIntOrNull() ?: 0
                val oxford = cols.getOrNull(6)?.trim()?.toIntOrNull() ?: 0
                val tag = cols.getOrNull(7)?.trim().orEmpty()
                val bnc = cols.getOrNull(8)?.trim()?.toIntOrNull() ?: 0
                val frq = cols.getOrNull(9)?.trim()?.toIntOrNull() ?: 0
                val exchange = cols.getOrNull(10)?.trim().orEmpty()
                val rawDetail = cols.getOrNull(11)?.trim().orEmpty()
                val example = if (sourceId == "starter" && rawDetail.isNotEmpty() && !rawDetail.startsWith("{")) {
                    rawDetail
                } else {
                    ""
                }

                batch.add(
                    DictionaryEntryEntity(
                        word = firstCol,
                        wordLower = firstCol.lowercase(Locale.ROOT),
                        phonetic = phonetic,
                        definition = definition,
                        translation = translation,
                        pos = pos,
                        collins = collins,
                        oxford = oxford,
                        tag = tag,
                        bnc = bnc,
                        frq = frq,
                        exchange = exchange,
                        example = example,
                        exampleTranslation = "",
                        sourceId = sourceId
                    )
                )
                if (batch.size >= batchSize) {
                    onBatch(batch.toList())
                    total += batch.size
                    batch.clear()
                }
            }
        }
        if (batch.isNotEmpty()) {
            onBatch(batch.toList())
            total += batch.size
            batch.clear()
        }
        return total
    }

    suspend fun parseEcdictCsvZipStream(
        inputStream: InputStream,
        sourceId: String,
        batchSize: Int = 2000,
        onBatch: suspend (List<DictionaryEntryEntity>) -> Unit
    ): Int {
        var total = 0
        ZipInputStream(inputStream).use { zis ->
            while (true) {
                val entry = zis.nextEntry ?: break
                if (!entry.isDirectory && entry.name.endsWith(".csv", ignoreCase = true)) {
                    total += parseEcdictCsvStream(
                        inputStream = object : InputStream() {
                            override fun read(): Int = zis.read()
                            override fun read(b: ByteArray, off: Int, len: Int): Int = zis.read(b, off, len)
                        },
                        sourceId = sourceId,
                        batchSize = batchSize,
                        onBatch = onBatch
                    )
                }
                zis.closeEntry()
            }
        }
        return total
    }

    suspend fun parseWebstersJsonStream(
        inputStream: InputStream,
        sourceId: String,
        batchSize: Int = 2000,
        onBatch: suspend (List<DictionaryEntryEntity>) -> Unit
    ): Int {
        var total = 0
        val batch = ArrayList<DictionaryEntryEntity>(batchSize)
        JsonReader(InputStreamReader(inputStream, StandardCharsets.UTF_8)).use { reader ->
            reader.beginObject()
            while (reader.hasNext()) {
                val word = reader.nextName().trim()
                val def = when (reader.peek()) {
                    com.google.gson.stream.JsonToken.STRING -> reader.nextString().trim()
                    com.google.gson.stream.JsonToken.NULL -> {
                        reader.nextNull()
                        ""
                    }
                    else -> {
                        reader.skipValue()
                        ""
                    }
                }
                if (word.isNotEmpty() && def.isNotEmpty()) {
                    batch.add(
                        DictionaryEntryEntity(
                            word = word,
                            wordLower = word.lowercase(Locale.ROOT),
                            definition = def,
                            translation = "",
                            tag = "webster-en",
                            sourceId = sourceId
                        )
                    )
                    if (batch.size >= batchSize) {
                        onBatch(batch.toList())
                        total += batch.size
                        batch.clear()
                    }
                }
            }
            reader.endObject()
        }
        if (batch.isNotEmpty()) {
            onBatch(batch.toList())
            total += batch.size
            batch.clear()
        }
        return total
    }

    suspend fun parseSimpleTsvStream(
        inputStream: InputStream,
        sourceId: String,
        batchSize: Int = 2000,
        onBatch: suspend (List<DictionaryEntryEntity>) -> Unit
    ): Int {
        var total = 0
        val batch = ArrayList<DictionaryEntryEntity>(batchSize)
        BufferedReader(InputStreamReader(inputStream, StandardCharsets.UTF_8), 64 * 1024).use { reader ->
            while (true) {
                val line = reader.readLine() ?: break
                val cleaned = line.removePrefix("\uFEFF").trim()
                if (cleaned.isEmpty() || cleaned.startsWith("#")) continue
                val parts = cleaned.split("\t")
                val word = parts.getOrNull(0)?.trim().orEmpty()
                val trans = parts.getOrNull(1)?.replace("\\n", "\n")?.trim().orEmpty()
                if (word.isEmpty() || trans.isEmpty()) continue
                val phonetic = parts.getOrNull(2)?.trim().orEmpty()
                val example = parts.getOrNull(3)?.trim().orEmpty()
                batch.add(
                    DictionaryEntryEntity(
                        word = word,
                        wordLower = word.lowercase(Locale.ROOT),
                        phonetic = phonetic,
                        translation = trans,
                        example = example,
                        tag = "local-import",
                        sourceId = sourceId
                    )
                )
                if (batch.size >= batchSize) {
                    onBatch(batch.toList())
                    total += batch.size
                    batch.clear()
                }
            }
        }
        if (batch.isNotEmpty()) {
            onBatch(batch.toList())
            total += batch.size
            batch.clear()
        }
        return total
    }

    fun parseKajwebJsonLine(line: String, sourceId: String): DictionaryEntryEntity? {
        val trimmed = line.trim()
        if (trimmed.isEmpty() || !trimmed.startsWith("{")) return null
        return try {
            val root = gson.fromJson(trimmed, JsonObject::class.java) ?: return null
            val headWord = root.get("headWord")?.takeIf { it.isJsonPrimitive }?.asString?.trim().orEmpty()
            if (headWord.isEmpty()) return null

            val bookId = root.get("bookId")?.takeIf { it.isJsonPrimitive }?.asString?.trim().orEmpty()
            val inner = root.getAsJsonObject("content")
                ?.getAsJsonObject("word")
                ?.getAsJsonObject("content")
                ?: return null

            val usPhone = inner.get("usphone")?.takeIf { it.isJsonPrimitive }?.asString?.trim().orEmpty()
            val ukPhone = inner.get("ukphone")?.takeIf { it.isJsonPrimitive }?.asString?.trim().orEmpty()
            val phonetic = usPhone.ifBlank { ukPhone }

            val transParts = mutableListOf<String>()
            val defParts = mutableListOf<String>()
            inner.get("trans")?.takeIf { it.isJsonArray }?.asJsonArray?.forEach { elem ->
                if (elem.isJsonObject) {
                    val tObj = elem.asJsonObject
                    val pos = tObj.get("pos")?.takeIf { it.isJsonPrimitive }?.asString?.trim().orEmpty()
                    val tranCn = tObj.get("tranCn")?.takeIf { it.isJsonPrimitive }?.asString?.trim().orEmpty()
                    val tranOther = tObj.get("tranOther")?.takeIf { it.isJsonPrimitive }?.asString?.trim().orEmpty()
                    if (tranCn.isNotEmpty()) {
                        transParts.add(if (pos.isNotEmpty()) "$pos. $tranCn" else tranCn)
                    }
                    if (tranOther.isNotEmpty()) {
                        defParts.add(if (pos.isNotEmpty()) "$pos. $tranOther" else tranOther)
                    }
                }
            }

            var exampleEn = ""
            var exampleZh = ""
            val sentences = inner.get("sentence")
                ?.takeIf { it.isJsonObject }?.asJsonObject
                ?.get("sentences")?.takeIf { it.isJsonArray }?.asJsonArray
            if (sentences != null) {
                for (sElem in sentences) {
                    if (sElem.isJsonObject) {
                        val sObj = sElem.asJsonObject
                        val en = sObj.get("sContent")?.takeIf { it.isJsonPrimitive }?.asString?.trim().orEmpty()
                        val zh = sObj.get("sCn")?.takeIf { it.isJsonPrimitive }?.asString?.trim().orEmpty()
                        if (en.isNotEmpty()) {
                            exampleEn = en
                            exampleZh = zh
                            break
                        }
                    }
                }
            }

            DictionaryEntryEntity(
                word = headWord,
                wordLower = headWord.lowercase(Locale.ROOT),
                phonetic = phonetic,
                definition = defParts.joinToString("\n"),
                translation = transParts.joinToString("\n"),
                tag = bookId.ifBlank { sourceId },
                example = exampleEn,
                exampleTranslation = exampleZh,
                sourceId = sourceId
            )
        } catch (_: Exception) {
            null
        }
    }

    suspend fun parseKajwebJsonlZipStream(
        inputStream: InputStream,
        sourceId: String,
        batchSize: Int = 1000,
        onBatch: suspend (List<DictionaryEntryEntity>) -> Unit
    ): Int {
        var total = 0
        val batch = ArrayList<DictionaryEntryEntity>(batchSize)
        ZipInputStream(inputStream).use { zis ->
            while (true) {
                val entry = zis.nextEntry ?: break
                if (!entry.isDirectory && entry.name.endsWith(".json", ignoreCase = true)) {
                    val reader = BufferedReader(InputStreamReader(zis, StandardCharsets.UTF_8), 64 * 1024)
                    while (true) {
                        val line = reader.readLine() ?: break
                        val parsed = parseKajwebJsonLine(line, sourceId) ?: continue
                        batch.add(parsed)
                        if (batch.size >= batchSize) {
                            onBatch(batch.toList())
                            total += batch.size
                            batch.clear()
                        }
                    }
                }
                zis.closeEntry()
            }
        }
        if (batch.isNotEmpty()) {
            onBatch(batch.toList())
            total += batch.size
            batch.clear()
        }
        return total
    }

    private val cedictRegex = Regex("""^(\S+)\s+(\S+)\s+\[([^\]]+)\]\s+/(.+)/\s*$""")

    fun parseCedictLine(line: String, sourceId: String): DictionaryEntryEntity? {
        val trimmed = line.trim()
        if (trimmed.isEmpty() || trimmed.startsWith("#")) return null
        val match = cedictRegex.matchEntire(trimmed) ?: return null
        val trad = match.groupValues[1]
        val simp = match.groupValues[2]
        val pinyin = match.groupValues[3]
        val defs = match.groupValues[4].split("/").filter { it.isNotBlank() }.joinToString("; ")
        val displayTrad = if (trad != simp) " (繁: $trad)" else ""
        return DictionaryEntryEntity(
            word = simp,
            wordLower = simp.lowercase(Locale.ROOT),
            phonetic = pinyin,
            definition = defs,
            translation = "$defs$displayTrad",
            tag = "cc-cedict",
            sourceId = sourceId
        )
    }

    suspend fun parseCedictStream(
        inputStream: InputStream,
        isGzip: Boolean,
        sourceId: String,
        batchSize: Int = 2000,
        onBatch: suspend (List<DictionaryEntryEntity>) -> Unit
    ): Int {
        val stream = if (isGzip) GZIPInputStream(inputStream) else inputStream
        var total = 0
        val batch = ArrayList<DictionaryEntryEntity>(batchSize)
        BufferedReader(InputStreamReader(stream, StandardCharsets.UTF_8), 64 * 1024).use { reader ->
            while (true) {
                val line = reader.readLine() ?: break
                val entry = parseCedictLine(line, sourceId) ?: continue
                batch.add(entry)
                if (batch.size >= batchSize) {
                    onBatch(batch.toList())
                    total += batch.size
                    batch.clear()
                }
            }
        }
        if (batch.isNotEmpty()) {
            onBatch(batch.toList())
            total += batch.size
            batch.clear()
        }
        return total
    }

    suspend fun parseXinhuaJsonStream(
        inputStream: InputStream,
        format: DictFormat,
        sourceId: String,
        batchSize: Int = 2000,
        onBatch: suspend (List<ChineseDictEntryEntity>) -> Unit
    ): Int {
        var total = 0
        val batch = ArrayList<ChineseDictEntryEntity>(batchSize)
        JsonReader(InputStreamReader(inputStream, StandardCharsets.UTF_8)).use { reader ->
            reader.beginArray()
            while (reader.hasNext()) {
                val map = mutableMapOf<String, String>()
                reader.beginObject()
                while (reader.hasNext()) {
                    val key = reader.nextName()
                    val value = when (reader.peek()) {
                        com.google.gson.stream.JsonToken.NULL -> {
                            reader.nextNull()
                            ""
                        }
                        com.google.gson.stream.JsonToken.STRING -> reader.nextString()
                        com.google.gson.stream.JsonToken.NUMBER -> reader.nextString()
                        com.google.gson.stream.JsonToken.BOOLEAN -> reader.nextBoolean().toString()
                        else -> {
                            reader.skipValue()
                            ""
                        }
                    }
                    map[key] = value
                }
                reader.endObject()

                val entity = when (format) {
                    DictFormat.XINHUA_WORD_JSON -> {
                        val word = map["word"].orEmpty().trim()
                        if (word.isEmpty()) null else ChineseDictEntryEntity(
                            headword = word,
                            pinyin = map["pinyin"].orEmpty(),
                            category = "word",
                            radicals = map["radicals"].orEmpty(),
                            strokes = map["strokes"].orEmpty(),
                            explanation = map["explanation"].orEmpty(),
                            more = map["more"].orEmpty(),
                            sourceId = sourceId
                        )
                    }
                    DictFormat.XINHUA_IDIOM_JSON -> {
                        val word = map["word"].orEmpty().trim()
                        if (word.isEmpty()) null else ChineseDictEntryEntity(
                            headword = word,
                            pinyin = map["pinyin"].orEmpty(),
                            category = "idiom",
                            explanation = map["explanation"].orEmpty(),
                            derivation = map["derivation"].orEmpty(),
                            example = map["example"].orEmpty(),
                            sourceId = sourceId
                        )
                    }
                    DictFormat.XINHUA_CI_JSON -> {
                        val ci = map["ci"].orEmpty().trim()
                        if (ci.isEmpty()) null else ChineseDictEntryEntity(
                            headword = ci,
                            category = "ci",
                            explanation = map["explanation"].orEmpty(),
                            sourceId = sourceId
                        )
                    }
                    DictFormat.XINHUA_XIEHOUYU_JSON -> {
                        val riddle = map["riddle"].orEmpty().trim()
                        val answer = map["answer"].orEmpty().trim()
                        if (riddle.isEmpty()) null else ChineseDictEntryEntity(
                            headword = riddle,
                            category = "xiehouyu",
                            explanation = "谜底：$answer",
                            sourceId = sourceId
                        )
                    }
                    else -> null
                }

                if (entity != null) {
                    batch.add(entity)
                    if (batch.size >= batchSize) {
                        onBatch(batch.toList())
                        total += batch.size
                        batch.clear()
                    }
                }
            }
            reader.endArray()
        }
        if (batch.isNotEmpty()) {
            onBatch(batch.toList())
            total += batch.size
            batch.clear()
        }
        return total
    }
}
