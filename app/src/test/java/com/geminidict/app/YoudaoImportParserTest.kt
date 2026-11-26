package com.geminidict.app

import com.geminidict.app.data.youdao.YoudaoImportParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class YoudaoImportParserTest {

    @Test
    fun parseOfficialYoudaoXmlWithCdataAndRoundTrip() {
        val xml = """
            <wordbook>
              <item>
                <word>portion</word>
                <trans><![CDATA[n. 部分；一份；命运]]></trans>
                <phonetic><![CDATA[['pɔːʃ(ə)n]]]></phonetic>
                <tags>考研</tags>
                <progress>2</progress>
              </item>
              <item>
                <word>ephemeral</word>
                <trans><![CDATA[adj. 短暂的]]></trans>
                <phonetic><![CDATA[[ɪˈfemərəl]]]></phonetic>
                <tags>GRE</tags>
                <progress>4</progress>
              </item>
            </wordbook>
        """.trimIndent()

        val parsed = YoudaoImportParser.parseAuto(xml, "my_wordbook.xml")
        assertEquals(2, parsed.size)
        assertEquals("portion", parsed[0].word)
        assertEquals("n. 部分；一份；命运", parsed[0].translation)
        assertEquals("'pɔːʃ(ə)n", parsed[0].phonetic)
        assertEquals("考研", parsed[0].tags)
        assertEquals(2, parsed[0].progress)

        val now = 1_700_000_000_000L
        val entities = YoudaoImportParser.toWordbookEntities(parsed, nowMillis = now)
        // Verify staggered nextReviewAt according to existing progress
        val dayMs = 24L * 60L * 60L * 1000L
        assertEquals(now + 4 * dayMs, entities[0].nextReviewAt)
        assertEquals(now + 25 * dayMs, entities[1].nextReviewAt)

        val exported = YoudaoImportParser.exportToYoudaoXml(entities)
        assertTrue(exported.contains("<word>portion</word>"))
        assertTrue(exported.contains("<progress>4</progress>"))
    }

    @Test
    fun nestedAlterInsideItemDoesNotWipeCurrentWord() {
        val xml = """
            <wordbook>
              <item>
                <word>colour</word>
                <alter>color</alter>
                <trans><![CDATA[n. 颜色]]></trans>
                <phonetic><![CDATA[['kʌlə]]]></phonetic>
                <tags>英式</tags>
                <progress>1</progress>
              </item>
            </wordbook>
        """.trimIndent()

        val parsed = YoudaoImportParser.parseYoudaoXml(xml)
        assertEquals(1, parsed.size)
        assertEquals("colour", parsed[0].word)
        assertEquals("n. 颜色", parsed[0].translation)
        assertEquals("英式", parsed[0].tags)
    }

    @Test
    fun parseYoudaoNumberedTxtAndDeduplicate() {
        val txt = """
            1, abandon [ə'bændən]
            vt. 放弃, 遗弃
            2, resilient [ri'ziljənt]
            a. 有韧性的
            3, abandon [ə'bændən]
            vt. 放弃
        """.trimIndent()

        val parsed = YoudaoImportParser.parseAuto(txt, "youdao.txt")
        assertEquals(2, parsed.size)
        assertEquals("abandon", parsed[0].word)
        assertEquals("ə'bændən", parsed[0].phonetic)
        assertTrue(parsed[0].translation.contains("放弃"))
    }

    @Test
    fun parseMultilineQuotedCsvWithHeader() {
        val csv = "word,translation,phonetic,tags,progress\n" +
            "algorithm,\"n. 算法\n运算法则\",'ælgəriðəm,技术,3\n" +
            "context,\"n. 上下文, 语境\",'kɔntekst,通用,0\n"

        val parsed = YoudaoImportParser.parseAuto(csv, "words.csv")
        assertEquals(2, parsed.size)
        assertEquals("algorithm", parsed[0].word)
        assertEquals("n. 算法\n运算法则", parsed[0].translation)
        assertEquals(3, parsed[0].progress)
        assertEquals("context", parsed[1].word)
    }
}
