package com.geminidict.app

import com.geminidict.app.data.dict.DictFormat
import com.geminidict.app.data.dict.DictionaryParsers
import com.geminidict.app.data.local.dao.SqlLikeEscaper
import com.geminidict.app.data.local.entity.ChineseDictEntryEntity
import com.geminidict.app.data.local.entity.DictionaryEntryEntity
import com.geminidict.app.data.remote.GeminiTranslationService
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class DictionaryParsersTest {

    @Test
    fun parseRealEcdictCsvWithEmptyDetailAndRealMultilineQuotes() = runTest {
        val csv = "word,phonetic,definition,translation,pos,collins,oxford,tag,bnc,frq,exchange,detail,audio\n" +
            "abandon,ə'bændən,\"leave behind;\nmove out\",\"vt. 放弃, 遗弃\\nn. 放任\",v:80/n:20,4,1,cet4 cet6,3512,3201,d:abandoned,,\n" +
            "resilient,ri'ziljənt,\"able to \"\"bounce back\"\"\",a. 有韧性的,a:100,2,0,ielts,6980,6120,,,\n"

        val collected = mutableListOf<DictionaryEntryEntity>()
        val count = DictionaryParsers.parseEcdictCsvStream(
            ByteArrayInputStream(csv.toByteArray(StandardCharsets.UTF_8)),
            sourceId = "ecdict_full"
        ) { collected.addAll(it) }

        assertEquals(2, count)
        assertEquals("abandon", collected[0].word)
        assertEquals("leave behind;\nmove out", collected[0].definition)
        assertEquals("", collected[0].example)
        assertEquals("resilient", collected[1].word)
        assertEquals("able to \"bounce back\"", collected[1].definition)
    }

    @Test
    fun parseKajwebJsonlZipExtractsBilingualExamplesAndSkipsBadLines() = runTest {
        val jsonl = """
            {"wordRank":1,"headWord":"cancel","bookId":"CET4_3","content":{"word":{"content":{"usphone":"'kænsl","trans":[{"pos":"vt","tranCn":"取消，撤销","tranOther":"to decide not to happen"}],"sentence":{"sentences":[{"sContent":"Our flight was cancelled.","sCn":"我们的航班取消了。"}]}}}}}
            {broken json line that should be skipped safely}
        """.trimIndent()

        val baos = ByteArrayOutputStream()
        ZipOutputStream(baos).use { zos ->
            zos.putNextEntry(ZipEntry("CET4_3.json"))
            zos.write(jsonl.toByteArray(StandardCharsets.UTF_8))
            zos.closeEntry()
        }

        val collected = mutableListOf<DictionaryEntryEntity>()
        val count = DictionaryParsers.parseKajwebJsonlZipStream(
            ByteArrayInputStream(baos.toByteArray()),
            sourceId = "kajweb_cet4"
        ) { collected.addAll(it) }

        assertEquals(1, count)
        assertEquals("cancel", collected[0].word)
        assertEquals("'kænsl", collected[0].phonetic)
        assertEquals("vt. 取消，撤销", collected[0].translation)
        assertEquals("Our flight was cancelled.", collected[0].example)
        assertEquals("我们的航班取消了。", collected[0].exampleTranslation)
    }

    @Test
    fun parseCedictLineExtractsPinyinAndDefinitions() {
        val line = "計算機 计算机 [ji4 suan4 ji1] /computer/(Tw) calculator/CL:臺|台[tai2]/"
        val entry = DictionaryParsers.parseCedictLine(line, "cc_cedict")
        assertNotNull(entry)
        assertEquals("计算机", entry!!.word)
        assertEquals("ji4 suan4 ji1", entry.phonetic)
        assertTrue(entry.translation.contains("computer"))
        assertTrue(entry.translation.contains("計算機"))
    }

    @Test
    fun parseXinhuaIdiomJsonStream() = runTest {
        val json = """
            [
              {
                "word": "画龙点睛",
                "pinyin": "huà lóng diǎn jīng",
                "explanation": "原形容梁代画家张僧繇作画的神妙。",
                "derivation": "唐·张彦远《历代名画记》",
                "example": "这一句堪称全篇画龙点睛之笔。"
              }
            ]
        """.trimIndent()
        val collected = mutableListOf<ChineseDictEntryEntity>()
        val count = DictionaryParsers.parseXinhuaJsonStream(
            ByteArrayInputStream(json.toByteArray(StandardCharsets.UTF_8)),
            format = DictFormat.XINHUA_IDIOM_JSON,
            sourceId = "xinhua_idiom"
        ) { collected.addAll(it) }

        assertEquals(1, count)
        assertEquals("画龙点睛", collected[0].headword)
        assertEquals("idiom", collected[0].category)
    }

    @Test
    fun sqlLikeEscaperEscapesWildcardsAndBackslash() {
        assertEquals("""100\%\_\\test""", SqlLikeEscaper.escape("""100%_\test"""))
    }

    @Test
    fun geminiParserToleratesMalformedExampleElementsAndRejectsEmptyJson() {
        val service = GeminiTranslationService()
        val mixedJson = """
            {
              "detectedLanguage": "en",
              "translation": "有韧性的",
              "examples": ["not-an-object", {"english": "Stay resilient.", "chinese": "保持韧性。"}],
              "synonyms": ["tough", 123, {"bad": true}]
            }
        """.trimIndent()
        val ok = service.parseStructuredResult("resilient", mixedJson, "gemini-3.5-flash-lite")
        assertTrue(ok.isSuccess)
        assertEquals("有韧性的", ok.getOrThrow().translation)
        assertEquals(1, ok.getOrThrow().examples.size)
        assertEquals("Stay resilient.", ok.getOrThrow().examples[0].english)

        val emptyObj = service.parseStructuredResult("resilient", "{}", "gemini-3.5-flash-lite")
        assertTrue(emptyObj.isFailure)
    }
}
