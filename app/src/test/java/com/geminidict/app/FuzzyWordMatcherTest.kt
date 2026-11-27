package com.geminidict.app

import com.geminidict.app.data.dict.DictionaryParsers
import com.geminidict.app.data.local.entity.DictionaryEntryEntity
import com.geminidict.app.domain.search.FuzzyWordMatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.nio.charset.StandardCharsets

class FuzzyWordMatcherTest {

    private fun dictWord(w: String, frq: Int = 1000) = DictionaryEntryEntity(
        word = w,
        wordLower = w.lowercase(),
        translation = "测试释义 $w",
        frq = frq
    )

    @Test
    fun damerauLevenshteinTreatsAdjacentTranspositionAsDistanceOne() {
        assertEquals(1, FuzzyWordMatcher.damerauLevenshtein("recieve", "receive"))
        assertEquals(1, FuzzyWordMatcher.damerauLevenshtein("teh", "the"))
        assertEquals(1, FuzzyWordMatcher.damerauLevenshtein("accomodate", "accommodate"))
        assertEquals(1, FuzzyWordMatcher.damerauLevenshtein("enviroment", "environment"))
    }

    @Test
    fun rankFuzzyCandidatesFindsCommonSpellingMistakesFirst() {
        val pool = listOf(
            dictWord("accompany", frq = 1500),
            dictWord("accommodate", frq = 2200),
            dictWord("accomplish", frq = 1900),
            dictWord("receive", frq = 500),
            dictWord("receipt", frq = 3000)
        )

        val res1 = FuzzyWordMatcher.rankFuzzyCandidates("accomodate", pool)
        assertTrue(res1.isNotEmpty())
        assertEquals("accommodate", res1[0].entry.word)
        assertEquals(1, res1[0].distance)

        val res2 = FuzzyWordMatcher.rankFuzzyCandidates("recieve", pool)
        assertTrue(res2.isNotEmpty())
        assertEquals("receive", res2[0].entry.word)
        assertEquals(1, res2[0].distance)
    }

    @Test
    fun stemmerGeneratesExpectedBaseForms() {
        assertTrue("study" in FuzzyWordMatcher.generateStemCandidates("studies"))
        assertTrue("run" in FuzzyWordMatcher.generateStemCandidates("running"))
        assertTrue("abandon" in FuzzyWordMatcher.generateStemCandidates("abandoned"))
        assertTrue("box" in FuzzyWordMatcher.generateStemCandidates("boxes"))
    }

    @Test
    fun ftsQuerySanitizerStripsOperatorsSafely() {
        assertEquals("hello* world* test*", FuzzyWordMatcher.toSafeFtsQuery("hello \"world\"* (test-"))
    }

    @Test
    fun parseWebstersJsonStreamReadsEnglishDefinitions() = runTest {
        val json = """{"abacus":"A table or tray strewn with sand.","zephyr":"The west wind."}"""
        val collected = mutableListOf<DictionaryEntryEntity>()
        val count = DictionaryParsers.parseWebstersJsonStream(
            ByteArrayInputStream(json.toByteArray(StandardCharsets.UTF_8)),
            sourceId = "websters_1913"
        ) { collected.addAll(it) }

        assertEquals(2, count)
        assertEquals("abacus", collected[0].word)
        assertTrue(collected[0].definition.contains("sand"))
    }
}
