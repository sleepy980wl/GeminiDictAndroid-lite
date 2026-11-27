package com.geminidict.app.domain.search

import com.geminidict.app.data.local.entity.DictionaryEntryEntity
import java.util.Locale
import kotlin.math.min

data class FuzzyScoredEntry(
    val entry: DictionaryEntryEntity,
    val distance: Int,
    val matchReason: String
)

/**
 * Pure-Kotlin English typo tolerance, stemming, and FTS4 query sanitizer.
 * Uses Damerau-Levenshtein (Optimal String Alignment) distance so adjacent transpositions
 * (e.g. "recieve" -> "receive", "teh" -> "the") count as distance 1.
 */
object FuzzyWordMatcher {

    /**
     * Sanitizes user input into a safe SQLite FTS4 MATCH expression (avoids syntax crashes on quotes/operators).
     */
    fun toSafeFtsQuery(raw: String): String {
        val tokens = raw
            .trim()
            .replace(Regex("""["*():^+\-]"""), " ")
            .split(Regex("""\s+"""))
            .filter { it.isNotBlank() }
        if (tokens.isEmpty()) return ""
        // Unquoted prefix terms: FTS4 `simple` tokenizer keeps full-width punctuation
        // (e.g. ECDICT `苹果；苹果树`) inside a token, so a quoted `"苹果"*` never
        // matches it, while bare `苹果*` does. Verified against SQLite FTS4.
        return tokens.joinToString(" ") { token -> "$token*" }
    }

    fun isAsciiEnglish(query: String): Boolean {
        if (query.isBlank()) return false
        return query.all { it in 'a'..'z' || it in 'A'..'Z' || it == '-' || it == '\'' }
    }

    /**
     * Generates likely base-form candidates for common English inflections
     * (plurals, past tense, gerunds, comparatives) so e.g. "studies" finds "study".
     */
    fun generateStemCandidates(wordLower: String): List<String> {
        if (wordLower.length < 4) return emptyList()
        val stems = LinkedHashSet<String>()
        if (wordLower.endsWith("ies") && wordLower.length > 4) {
            stems.add(wordLower.dropLast(3) + "y")
        }
        if (wordLower.endsWith("ied") && wordLower.length > 4) {
            stems.add(wordLower.dropLast(3) + "y")
        }
        if (wordLower.endsWith("es") && wordLower.length > 4) {
            stems.add(wordLower.dropLast(2))
            stems.add(wordLower.dropLast(1))
        }
        if (wordLower.endsWith("s") && !wordLower.endsWith("ss") && wordLower.length > 3) {
            stems.add(wordLower.dropLast(1))
        }
        if (wordLower.endsWith("ing") && wordLower.length > 5) {
            val base = wordLower.dropLast(3)
            stems.add(base)
            stems.add(base + "e")
            if (base.length >= 2 && base[base.length - 1] == base[base.length - 2]) {
                stems.add(base.dropLast(1))
            }
        }
        if (wordLower.endsWith("ed") && wordLower.length > 4) {
            val base = wordLower.dropLast(2)
            stems.add(base)
            stems.add(base + "e")
            if (base.length >= 2 && base[base.length - 1] == base[base.length - 2]) {
                stems.add(base.dropLast(1))
            }
        }
        if (wordLower.endsWith("er") && wordLower.length > 4) {
            stems.add(wordLower.dropLast(2))
            stems.add(wordLower.dropLast(1))
        }
        if (wordLower.endsWith("est") && wordLower.length > 5) {
            stems.add(wordLower.dropLast(3))
            stems.add(wordLower.dropLast(2))
        }
        stems.remove(wordLower)
        return stems.toList()
    }

    /**
     * Optimal String Alignment (restricted Damerau-Levenshtein) edit distance.
     * Supports insertion, deletion, substitution, and adjacent transposition at cost 1.
     */
    fun damerauLevenshtein(a: String, b: String): Int {
        if (a == b) return 0
        val m = a.length
        val n = b.length
        if (m == 0) return n
        if (n == 0) return m

        val dp = Array(m + 1) { IntArray(n + 1) }
        for (i in 0..m) dp[i][0] = i
        for (j in 0..n) dp[0][j] = j

        for (i in 1..m) {
            for (j in 1..n) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                var best = min(
                    min(dp[i - 1][j] + 1, dp[i][j - 1] + 1),
                    dp[i - 1][j - 1] + cost
                )
                if (i > 1 && j > 1 && a[i - 1] == b[j - 2] && a[i - 2] == b[j - 1]) {
                    best = min(best, dp[i - 2][j - 2] + 1)
                }
                dp[i][j] = best
            }
        }
        return dp[m][n]
    }

    fun maxAllowedDistance(queryLength: Int): Int = when {
        queryLength <= 4 -> 1
        queryLength <= 8 -> 2
        else -> 3
    }

    /**
     * Ranks deduplicated candidate entries against a possibly misspelled English query.
     */
    fun rankFuzzyCandidates(
        query: String,
        candidates: List<DictionaryEntryEntity>,
        excludeWordsLower: Set<String> = emptySet(),
        limit: Int = 15
    ): List<FuzzyScoredEntry> {
        val q = query.trim().lowercase(Locale.ROOT)
        if (q.length < 2) return emptyList()
        val maxDist = maxAllowedDistance(q.length)

        val dedup = LinkedHashMap<String, DictionaryEntryEntity>()
        for (c in candidates) {
            if (c.wordLower in excludeWordsLower) continue
            val existing = dedup[c.wordLower]
            if (existing == null) {
                dedup[c.wordLower] = c
            } else if (existing.example.isBlank() && c.example.isNotBlank()) {
                dedup[c.wordLower] = existing.copy(
                    example = c.example,
                    exampleTranslation = c.exampleTranslation
                )
            }
        }

        val scored = ArrayList<FuzzyScoredEntry>()
        for (entry in dedup.values) {
            val w = entry.wordLower
            if (kotlin.math.abs(w.length - q.length) > maxDist) continue
            val dist = damerauLevenshtein(q, w)
            if (dist in 1..maxDist) {
                scored.add(
                    FuzzyScoredEntry(
                        entry = entry,
                        distance = dist,
                        matchReason = "拼写纠错"
                    )
                )
            }
        }

        return scored
            .sortedWith(
                compareBy<FuzzyScoredEntry> { it.distance }
                    .thenByDescending { commonPrefixLen(q, it.entry.wordLower) }
                    .thenBy { if (it.entry.frq > 0) it.entry.frq else 999999 }
                    .thenBy { it.entry.wordLower.length }
            )
            .take(limit)
    }

    private fun commonPrefixLen(a: String, b: String): Int {
        val len = min(a.length, b.length)
        var i = 0
        while (i < len && a[i] == b[i]) i++
        return i
    }
}
