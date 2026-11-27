package com.geminidict.app.ui.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.withTransaction
import com.geminidict.app.GeminiDictApplication
import com.geminidict.app.data.dict.DictionaryCatalog
import com.geminidict.app.data.dict.DictionaryCatalogItem
import com.geminidict.app.data.dict.DownloadProgress
import com.geminidict.app.data.local.dao.SqlLikeEscaper
import com.geminidict.app.data.local.entity.ChineseDictEntryEntity
import com.geminidict.app.data.local.entity.DictionaryEntryEntity
import com.geminidict.app.data.local.entity.DownloadedDictEntity
import com.geminidict.app.data.local.entity.ReviewLogEntity
import com.geminidict.app.data.local.entity.WordbookEntryEntity
import com.geminidict.app.data.remote.GeminiTranslationResult
import com.geminidict.app.data.tts.NeuralVoiceCatalog
import com.geminidict.app.data.tts.NeuralVoiceModelItem
import com.geminidict.app.data.youdao.YoudaoImportParser
import com.geminidict.app.data.youdao.YoudaoParsedWord
import com.geminidict.app.data.youdao.YoudaoSqliteReader
import com.geminidict.app.domain.search.FuzzyWordMatcher
import com.geminidict.app.domain.srs.ReviewRating
import com.geminidict.app.domain.srs.ReviewSessionQueue
import com.geminidict.app.domain.srs.Sm2Scheduler
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.nio.charset.StandardCharsets
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

enum class WordbookSort {
    NEWEST, ALPHABETICAL, MASTERY_ASC, DUE_FIRST
}

data class DictSearchHit(
    val entry: DictionaryEntryEntity,
    val matchHint: String? = null,
    /** Short name of the dictionary the displayed example sentence came from. */
    val exampleSourceLabel: String? = null
)

data class MainUiState(
    val query: String = "",
    val translateSubTab: Int = 0, // 0=中英/英英词典, 1=新华字典
    val offlineResults: List<DictSearchHit> = emptyList(),
    val chineseResults: List<ChineseDictEntryEntity> = emptyList(),
    val geminiResult: GeminiTranslationResult? = null,
    val isGeminiLoading: Boolean = false,
    val geminiError: String? = null,
    val statusBanner: String? = null,
    val securityWarning: String? = null,

    // Wordbook filter, sort & downloaded-book study preview
    val wordbookQuery: String = "",
    val selectedGroup: String = "全部",
    val wordbookSort: WordbookSort = WordbookSort.NEWEST,
    val previewBookEntries: List<DictionaryEntryEntity> = emptyList(),
    val isImportingBookToWordbook: Boolean = false,

    // Review session (supports reviewing all or a specific group/book)
    val reviewGroupFilter: String = "全部",
    val reviewQueue: List<WordbookEntryEntity> = emptyList(),
    val currentReviewIndex: Int = 0,
    val sessionRelearnPool: Map<Long, WordbookEntryEntity> = emptyMap(),
    val isCardFlipped: Boolean = false,

    // Dictionary & Neural Voice downloads
    val activeDownloads: Map<String, DownloadProgress> = emptyMap(),
    val installedNeuralVoiceIds: Set<String> = emptySet(),

    // Settings & connection test
    val apiKey: String = "",
    val geminiModel: String = "",
    val geminiBaseUrl: String = "",
    val githubMirrorPrefix: String = "",
    val ttsAccent: String = "en-US",
    val ttsSpeechRate: Float = 0.95f,
    val preferNeuralVoice: Boolean = true,
    val isTestingConnection: Boolean = false,
    val connectionTestMessage: String? = null
) {
    val sessionRelearnIds: Set<Long> get() = sessionRelearnPool.keys
}

/** One example sentence shown under the Chinese meaning on the review answer side. */
data class ReviewExample(val sentence: String, val translation: String)

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as GeminiDictApplication
    private val db = app.database
    private val prefs = app.securePrefs
    private val geminiService = app.geminiService
    private val downloader = app.dictionaryDownloader
    private val voiceDownloader = app.neuralVoiceDownloader
    val ttsManager = app.ttsManager

    private val _uiState = MutableStateFlow(
        MainUiState(
            apiKey = prefs.getGeminiApiKey(),
            geminiModel = prefs.getGeminiModel(),
            geminiBaseUrl = prefs.getGeminiBaseUrl(),
            githubMirrorPrefix = prefs.getGithubMirrorPrefix(),
            ttsAccent = prefs.getTtsAccent(),
            ttsSpeechRate = prefs.getTtsSpeechRate(),
            preferNeuralVoice = prefs.getPreferNeuralVoice(),
            securityWarning = prefs.securityWarning,
            statusBanner = prefs.securityWarning
        )
    )
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    val wordbookEntries: StateFlow<List<WordbookEntryEntity>> = db.wordbookDao()
        .observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val wordbookGroups: StateFlow<List<String>> = db.wordbookDao()
        .observeGroups()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val downloadedDicts: StateFlow<List<DownloadedDictEntity>> = db.downloadedDictDao()
        .observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val todayLearnedCount: StateFlow<Int> = db.reviewLogDao()
        .observeLearnedCountSince(startOfTodayMillis())
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val totalDictCount: StateFlow<Int> = db.dictionaryDao()
        .observeTotalCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    private var searchJob: Job? = null
    private val downloadJobs = ConcurrentHashMap<String, Job>()

    init {
        viewModelScope.launch {
            try {
                downloader.ensureStarterLoaded()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    statusBanner = "内置示例词库初始化失败：${e.message}，不影响手动下载完整离线词典。"
                )
            }
            refreshInstalledNeuralVoices()
            refreshReviewQueue()
        }
    }

    fun clearBanner() {
        _uiState.value = _uiState.value.copy(statusBanner = null)
    }

    fun onTranslateSubTabChanged(tab: Int) {
        if (_uiState.value.translateSubTab == tab) return
        _uiState.value = _uiState.value.copy(translateSubTab = tab)
        triggerOfflineSearch(_uiState.value.query, tab)
    }

    fun onQueryChanged(newQuery: String) {
        _uiState.value = _uiState.value.copy(
            query = newQuery,
            geminiError = null
        )
        triggerOfflineSearch(newQuery, _uiState.value.translateSubTab)
    }

    private fun triggerOfflineSearch(rawQuery: String, activeTab: Int) {
        searchJob?.cancel()
        val trimmed = rawQuery.trim()
        if (trimmed.isEmpty()) {
            _uiState.value = _uiState.value.copy(
                offlineResults = emptyList(),
                chineseResults = emptyList(),
                geminiResult = null
            )
            return
        }
        searchJob = viewModelScope.launch {
            delay(80)
            val escaped = SqlLikeEscaper.escape(trimmed)
            val lower = trimmed.lowercase(Locale.ROOT)
            val escapedLower = SqlLikeEscaper.escape(lower)

            if (activeTab == 0) {
                val hits = withContext(Dispatchers.IO) {
                    searchEnglishChineseFast(trimmed, lower, escapedLower)
                }
                _uiState.value = _uiState.value.copy(offlineResults = hits)
            } else {
                val zhResults = withContext(Dispatchers.IO) {
                    searchXinhuaFast(trimmed, escaped)
                }
                _uiState.value = _uiState.value.copy(chineseResults = zhResults)
            }
        }
    }

    /** Short display name (e.g. 专八) of the dictionary an example came from. */
    private fun exampleSourceLabel(sourceId: String): String? {
        val item = DictionaryCatalog.items.firstOrNull { it.id == sourceId }
            ?: return null
        return item.wordbookLabel ?: item.name
    }

    private suspend fun searchEnglishChineseFast(
        trimmed: String,
        lower: String,
        escapedLower: String
    ): List<DictSearchHit> {
        val mergedMap = LinkedHashMap<String, DictSearchHit>()

        fun absorb(entries: List<DictionaryEntryEntity>, hint: String?, maxTotal: Int = 30) {
            for (item in entries) {
                val existing = mergedMap[item.wordLower]
                if (existing == null) {
                    if (mergedMap.size < maxTotal) {
                        mergedMap[item.wordLower] = DictSearchHit(
                            item,
                            hint,
                            exampleSourceLabel = if (item.example.isNotBlank()) {
                                exampleSourceLabel(item.sourceId)
                            } else {
                                null
                            }
                        )
                    }
                } else {
                    val cur = existing.entry
                    val fillEx = cur.example.isBlank() && item.example.isNotBlank()
                    val fillTrans = cur.translation.isBlank() && item.translation.isNotBlank()
                    val fillDef = cur.definition.isBlank() && item.definition.isNotBlank()
                    if (fillEx || fillTrans || fillDef) {
                        mergedMap[item.wordLower] = existing.copy(
                            entry = cur.copy(
                                translation = if (fillTrans) item.translation else cur.translation,
                                definition = if (fillDef) item.definition else cur.definition,
                                example = if (fillEx) item.example else cur.example,
                                exampleTranslation = if (fillEx) item.exampleTranslation else cur.exampleTranslation
                            ),
                            exampleSourceLabel = if (fillEx) {
                                exampleSourceLabel(item.sourceId)
                            } else {
                                existing.exampleSourceLabel
                            }
                        )
                    }
                }
            }
        }

        val prefixHits = db.dictionaryDao().searchPrefixIndexed(
            queryLower = lower,
            prefixPattern = "$escapedLower%",
            rawLimit = 90
        )
        absorb(prefixHits, hint = null, maxTotal = 30)

        val isEnglish = FuzzyWordMatcher.isAsciiEnglish(lower)
        if (isEnglish && !mergedMap.containsKey(lower)) {
            val stems = FuzzyWordMatcher.generateStemCandidates(lower)
            if (stems.isNotEmpty()) {
                val stemHits = db.dictionaryDao().getExactBatch(stems)
                absorb(stemHits, hint = "词形还原", maxTotal = 30)
            }
        }

        if (mergedMap.size < 15) {
            val ftsQuery = FuzzyWordMatcher.toSafeFtsQuery(trimmed)
            if (ftsQuery.isNotEmpty()) {
                try {
                    val ftsHits = db.dictionaryDao().searchFts(ftsQuery, rawLimit = 60)
                    absorb(ftsHits, hint = if (isEnglish) "全文匹配" else null, maxTotal = 30)
                } catch (_: Exception) {
                }
            }
        }

        if (isEnglish && lower.length >= 3 && mergedMap.size < 8) {
            val maxDist = FuzzyWordMatcher.maxAllowedDistance(lower.length)
            val p2 = SqlLikeEscaper.escape(lower.take(2)) + "%"
            val swappedP2 = SqlLikeEscaper.escape("${lower[1]}${lower[0]}") + "%"
            val firstCh = SqlLikeEscaper.escape(lower.take(1)) + "%"
            val lastCh = "%" + SqlLikeEscaper.escape(lower.takeLast(1))

            val candidates = db.dictionaryDao().fetchFuzzyCandidates(
                prefix2 = p2,
                swappedPrefix2 = swappedP2,
                firstChar = firstCh,
                lastChar = lastCh,
                minLen = (lower.length - maxDist).coerceAtLeast(2),
                maxLen = lower.length + maxDist,
                limit = 400
            )
            val ranked = FuzzyWordMatcher.rankFuzzyCandidates(
                query = lower,
                candidates = candidates,
                excludeWordsLower = mergedMap.keys,
                limit = (30 - mergedMap.size).coerceAtLeast(0)
            )
            for (scored in ranked) {
                mergedMap[scored.entry.wordLower] = DictSearchHit(
                    entry = scored.entry,
                    matchHint = scored.matchReason
                )
            }
        }

        return mergedMap.values.toList()
    }

    private suspend fun searchXinhuaFast(
        trimmed: String,
        escaped: String
    ): List<ChineseDictEntryEntity> {
        val seen = LinkedHashMap<Long, ChineseDictEntryEntity>()
        val prefixHits = db.chineseDictDao().searchPrefixIndexed(
            query = trimmed,
            prefixPattern = "$escaped%",
            limit = 30
        )
        prefixHits.forEach { seen[it.id] = it }

        if (seen.size < 15) {
            val ftsQuery = FuzzyWordMatcher.toSafeFtsQuery(trimmed)
            if (ftsQuery.isNotEmpty()) {
                try {
                    val ftsHits = db.chineseDictDao().searchFts(ftsQuery, limit = 30)
                    ftsHits.forEach { if (seen.size < 30) seen.putIfAbsent(it.id, it) }
                } catch (_: Exception) {
                }
            }
        }
        return seen.values.toList()
    }

    fun translateWithGemini() {
        val q = _uiState.value.query.trim()
        if (q.isEmpty()) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isGeminiLoading = true,
                geminiError = null
            )
            val result = geminiService.translate(
                query = q,
                apiKey = prefs.getGeminiApiKey(),
                model = prefs.getGeminiModel(),
                baseUrl = prefs.getGeminiBaseUrl()
            )
            result.fold(
                onSuccess = { res ->
                    _uiState.value = _uiState.value.copy(
                        isGeminiLoading = false,
                        geminiResult = res,
                        geminiError = null
                    )
                },
                onFailure = { err ->
                    _uiState.value = _uiState.value.copy(
                        isGeminiLoading = false,
                        geminiError = err.message ?: "Gemini 翻译失败"
                    )
                }
            )
        }
    }

    fun testGeminiConnection(apiKey: String, model: String, baseUrl: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isTestingConnection = true,
                connectionTestMessage = null
            )
            val result = geminiService.testConnection(apiKey, model, baseUrl)
            _uiState.value = _uiState.value.copy(
                isTestingConnection = false,
                connectionTestMessage = result.fold(
                    onSuccess = { it },
                    onFailure = { "连接失败：${it.message}" }
                )
            )
        }
    }

    fun speakEnglish(text: String, accentOverride: String? = null, rateOverride: Float? = null) {
        val error = ttsManager.speakEnglish(text, accentOverride, rateOverride)
        if (!error.isNullOrBlank()) {
            _uiState.value = _uiState.value.copy(statusBanner = error)
        }
    }

    // --- Wordbook Operations & Study Downloaded Vocabulary Books ---

    fun toggleBookmarkFromDict(entry: DictionaryEntryEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            val existing = db.wordbookDao().findByWordLower(entry.wordLower)
            if (existing != null) {
                db.wordbookDao().deleteById(existing.id)
                _uiState.value = _uiState.value.copy(statusBanner = "已从单词本移除「${entry.word}」")
            } else {
                db.wordbookDao().upsert(
                    WordbookEntryEntity(
                        word = entry.word,
                        wordLower = entry.wordLower,
                        phonetic = entry.phonetic,
                        translation = entry.translation.ifBlank { entry.definition },
                        definition = entry.definition,
                        exampleSentence = entry.example,
                        exampleTranslation = entry.exampleTranslation,
                        tags = entry.tag,
                        groupName = "我的单词本",
                        source = "offline_dict"
                    )
                )
                _uiState.value = _uiState.value.copy(statusBanner = "已收藏「${entry.word}」到我的单词本")
            }
            refreshReviewQueue()
        }
    }

    /**
     * One-tap example fill for "我的单词本": for every word in 我的单词本
     * that has no example sentence yet, take the single best-matching
     * example from an already-downloaded dictionary that carries bilingual
     * examples (the kajweb exam books). Best = exact word match, kajweb
     * sources first, then the shortest example. Words with no match in any
     * downloaded dictionary are left untouched and counted in the result.
     * Idempotent: running it again only touches words still missing one.
     */
    fun fillMissingExamples() {
        viewModelScope.launch(Dispatchers.IO) {
            val targets = db.wordbookDao().getByGroupSnapshot("我的单词本")
                .filter { it.exampleSentence.isBlank() }
            if (targets.isEmpty()) {
                _uiState.value = _uiState.value.copy(statusBanner = "我的单词本里没有例句的单词都已经有例句了。")
                return@launch
            }
            val words = targets.map { it.wordLower }.distinct()
            val candidates = db.dictionaryDao().getExamplesForWords(words)
            if (candidates.isEmpty()) {
                // Name what is actually downloaded: ECDICT / Webster's /
                // CC-CEDICT carry no example sentences at all, so "downloaded
                // a dictionary" alone does not mean examples exist.
                val downloadedNames = downloadedDicts.value.map { it.name }
                _uiState.value = _uiState.value.copy(
                    statusBanner = if (downloadedNames.isEmpty()) {
                        "还没有下载任何词库，请先在「词库」页下载含双语例句的词库（如专八、TOEFL、考研）。"
                    } else {
                        val shown = downloadedNames.take(3).joinToString("、")
                        "已下载的「$shown」都不带双语例句；带例句的是专八、TOEFL、考研、四六级、雅思、GRE、SAT、商务英语，请下载其中一个。"
                    }
                )
                return@launch
            }
            val bestByWord = candidates.groupBy { it.wordLower }.mapValues { (_, list) ->
                list.sortedWith(
                    compareBy(
                        { if (it.sourceId.startsWith("kajweb_")) 0 else 1 },
                        { it.example.length }
                    )
                ).first()
            }
            val now = System.currentTimeMillis()
            var filled = 0
            for (entry in targets) {
                val best = bestByWord[entry.wordLower] ?: continue
                db.wordbookDao().update(
                    entry.copy(
                        exampleSentence = best.example,
                        exampleTranslation = best.exampleTranslation,
                        updatedAt = now
                    )
                )
                filled++
            }
            val missing = targets.size - filled
            _uiState.value = _uiState.value.copy(
                statusBanner = if (missing > 0) {
                    "已为 $filled 个单词补上例句，$missing 个在已下载词库中没找到例句。"
                } else {
                    "已为 $filled 个单词补上例句。"
                }
            )
        }
    }

    fun saveGeminiToWordbook(result: GeminiTranslationResult) {
        viewModelScope.launch(Dispatchers.IO) {
            val wordLower = result.sourceText.trim().lowercase(Locale.ROOT)
            val firstEx = result.examples.firstOrNull()
            val existing = db.wordbookDao().findByWordLower(wordLower)
            val now = System.currentTimeMillis()

            if (existing != null) {
                val merged = existing.copy(
                    phonetic = result.phonetic.ifBlank { existing.phonetic },
                    translation = result.translation.ifBlank { existing.translation },
                    definition = result.detailedExplanation.ifBlank { existing.definition },
                    exampleSentence = firstEx?.english?.ifBlank { existing.exampleSentence } ?: existing.exampleSentence,
                    exampleTranslation = firstEx?.chinese?.ifBlank { existing.exampleTranslation } ?: existing.exampleTranslation,
                    updatedAt = now
                )
                db.wordbookDao().update(merged)
                _uiState.value = _uiState.value.copy(
                    statusBanner = "已用 Gemini 释义更新「${existing.word}」，并保留原复习进度 (Lv.${existing.masteryLevel})"
                )
            } else {
                db.wordbookDao().upsert(
                    WordbookEntryEntity(
                        word = result.sourceText.trim(),
                        wordLower = wordLower,
                        phonetic = result.phonetic,
                        translation = result.translation,
                        definition = result.detailedExplanation,
                        exampleSentence = firstEx?.english.orEmpty(),
                        exampleTranslation = firstEx?.chinese.orEmpty(),
                        groupName = "我的单词本",
                        source = "gemini",
                        createdAt = now,
                        updatedAt = now
                    )
                )
                _uiState.value = _uiState.value.copy(statusBanner = "已将 Gemini 释义收藏到我的单词本")
            }
            refreshReviewQueue()
        }
    }

    fun upsertWordbookEntry(entry: WordbookEntryEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            val wordLower = entry.word.trim().lowercase(Locale.ROOT)
            if (entry.id != 0L) {
                db.wordbookDao().update(
                    entry.copy(wordLower = wordLower, updatedAt = System.currentTimeMillis())
                )
            } else {
                val existing = db.wordbookDao().findByWordLower(wordLower)
                if (existing != null) {
                    db.wordbookDao().update(
                        existing.copy(
                            phonetic = entry.phonetic.ifBlank { existing.phonetic },
                            translation = entry.translation.ifBlank { existing.translation },
                            exampleSentence = entry.exampleSentence.ifBlank { existing.exampleSentence },
                            groupName = entry.groupName.ifBlank { existing.groupName },
                            note = entry.note.ifBlank { existing.note },
                            updatedAt = System.currentTimeMillis()
                        )
                    )
                } else {
                    db.wordbookDao().upsert(
                        entry.copy(wordLower = wordLower, updatedAt = System.currentTimeMillis())
                    )
                }
            }
            _uiState.value = _uiState.value.copy(statusBanner = "已保存「${entry.word}」")
            refreshReviewQueue()
        }
    }

    fun deleteWordbookEntry(id: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            db.wordbookDao().deleteById(id)
            val updatedPool = _uiState.value.sessionRelearnPool.toMutableMap().apply { remove(id) }
            _uiState.value = _uiState.value.copy(sessionRelearnPool = updatedPool)
            refreshReviewQueue()
        }
    }

    fun updateWordbookFilter(query: String? = null, group: String? = null, sort: WordbookSort? = null) {
        val nextGroup = group ?: _uiState.value.selectedGroup
        _uiState.value = _uiState.value.copy(
            wordbookQuery = query ?: _uiState.value.wordbookQuery,
            selectedGroup = nextGroup,
            wordbookSort = sort ?: _uiState.value.wordbookSort
        )
        if (group != null) {
            loadBookPreviewIfApplicable(nextGroup)
        }
    }

    private fun loadBookPreviewIfApplicable(groupLabel: String) {
        val book = DictionaryCatalog.wordbookBooksByLabel[groupLabel]
        if (book == null) {
            _uiState.value = _uiState.value.copy(previewBookEntries = emptyList())
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            val preview = db.dictionaryDao().getPreviewBySource(book.id, limit = 300)
            _uiState.value = _uiState.value.copy(previewBookEntries = preview)
        }
    }

    /**
     * Imports ALL words of a downloaded exam/business vocabulary book (e.g. 专八/TOEFL/GRE/四级)
     * into `wordbook_entries` under `groupName = book.wordbookLabel`, staggering daily batches
     * (30 new words per day) so the user can study the entire book via SM-2 forgetting curve.
     */
    fun importDownloadedBookAsWordbook(book: DictionaryCatalogItem, dailyNewWords: Int = 30) {
        val label = book.wordbookLabel ?: return
        if (_uiState.value.isImportingBookToWordbook) return
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.value = _uiState.value.copy(isImportingBookToWordbook = true)
            try {
                val allWords = db.dictionaryDao().getAllBySource(book.id)
                if (allWords.isEmpty()) {
                    _uiState.value = _uiState.value.copy(
                        isImportingBookToWordbook = false,
                        statusBanner = "词库「$label」暂无词条，请确认已在「词库」页下载完成。"
                    )
                    return@launch
                }
                val now = System.currentTimeMillis()
                val dayMs = 24L * 60L * 60L * 1000L
                var added = 0
                var skipped = 0

                db.withTransaction {
                    allWords.chunked(500).forEachIndexed { chunkIdx, chunk ->
                        val baseIdx = chunkIdx * 500
                        val entities = chunk.mapIndexed { offset, d ->
                            val dayOffset = (baseIdx + offset) / dailyNewWords.coerceAtLeast(1)
                            WordbookEntryEntity(
                                word = d.word,
                                wordLower = d.wordLower,
                                phonetic = d.phonetic,
                                translation = d.translation.ifBlank { d.definition },
                                definition = d.definition,
                                exampleSentence = d.example,
                                exampleTranslation = d.exampleTranslation,
                                groupName = label,
                                tags = label,
                                source = book.id,
                                createdAt = now,
                                updatedAt = now,
                                nextReviewAt = now + dayOffset * dayMs
                            )
                        }
                        val ids = db.wordbookDao().insertAllIgnore(entities)
                        for (id in ids) {
                            if (id != -1L) added++ else skipped++
                        }
                    }
                }
                _uiState.value = _uiState.value.copy(
                    isImportingBookToWordbook = false,
                    statusBanner = "已将「$label」全部词库加入学习计划：新增 $added 词，已存在 $skipped 词（每日自动排期 $dailyNewWords 新词，前往「复习」页即可开始）。"
                )
                refreshReviewQueue()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isImportingBookToWordbook = false,
                    statusBanner = "加入学习计划失败：${e.message}"
                )
            }
        }
    }

    // --- Youdao Import & Export ---

    fun importYoudaoFromUri(uri: Uri, displayName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val parsed = if (displayName.endsWith(".db", ignoreCase = true)) {
                    val tempDb = File(app.cacheDir, "youdao_import_temp.db")
                    app.contentResolver.openInputStream(uri)?.use { input ->
                        FileOutputStream(tempDb).use { output -> input.copyTo(output) }
                    }
                    try {
                        YoudaoSqliteReader.readFromSqliteFile(tempDb)
                    } finally {
                        tempDb.delete()
                    }
                } else {
                    val text = app.contentResolver.openInputStream(uri)
                        ?.bufferedReader(StandardCharsets.UTF_8)
                        ?.use { it.readText() }
                        .orEmpty()
                    YoudaoImportParser.parseAuto(text, displayName)
                }
                importParsedYoudaoWords(parsed, "youdao_file")
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(statusBanner = "导入失败: ${e.message}")
            }
        }
    }

    fun importYoudaoFromLocalPath(filePath: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val file = File(filePath)
                if (!file.exists()) {
                    _uiState.value = _uiState.value.copy(statusBanner = "找不到 ADB 推送的文件: $filePath")
                    return@launch
                }
                val parsed = if (file.name.endsWith(".db", ignoreCase = true)) {
                    YoudaoSqliteReader.readFromSqliteFile(file)
                } else {
                    YoudaoImportParser.parseAuto(file.readText(StandardCharsets.UTF_8), file.name)
                }
                importParsedYoudaoWords(parsed, "youdao_adb")
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(statusBanner = "ADB 导入失败: ${e.message}")
            }
        }
    }

    private suspend fun importParsedYoudaoWords(
        parsed: List<YoudaoParsedWord>,
        sourceLabel: String
    ) {
        if (parsed.isEmpty()) {
            _uiState.value = _uiState.value.copy(statusBanner = "未解析到有效单词，请确认文件为有道 XML/TXT/CSV/DB 格式。")
            return
        }
        val entities = YoudaoImportParser.toWordbookEntities(parsed, sourceLabel = sourceLabel)
        var added = 0
        var skipped = 0

        db.withTransaction {
            for (chunk in entities.chunked(500)) {
                val wordsNeedingLookup = chunk.map { it.wordLower }.distinct()
                val dictMap = if (wordsNeedingLookup.isNotEmpty()) {
                    db.dictionaryDao().getExactBatch(wordsNeedingLookup)
                        .groupBy { it.wordLower }
                        .mapValues { (_, hits) ->
                            hits.firstOrNull { it.example.isNotBlank() } ?: hits.first()
                        }
                } else {
                    emptyMap()
                }

                val enrichedChunk = chunk.map { entity ->
                    val dictHit = dictMap[entity.wordLower]
                    if (dictHit != null) {
                        val shouldFillExample = entity.exampleSentence.isBlank() && dictHit.example.isNotBlank()
                        entity.copy(
                            phonetic = entity.phonetic.ifBlank { dictHit.phonetic },
                            translation = entity.translation.ifBlank { dictHit.translation },
                            exampleSentence = if (shouldFillExample) dictHit.example else entity.exampleSentence,
                            exampleTranslation = if (shouldFillExample) dictHit.exampleTranslation else entity.exampleTranslation
                        )
                    } else {
                        entity
                    }
                }

                val rowIds = db.wordbookDao().insertAllIgnore(enrichedChunk)
                for (id in rowIds) {
                    if (id != -1L) added++ else skipped++
                }
            }
        }

        _uiState.value = _uiState.value.copy(
            statusBanner = "单词本导入完成：新增 $added 词，已存在跳过 $skipped 词（已按原有熟练度错峰安排复习）。"
        )
        refreshReviewQueue()
    }

    fun exportWordbookToUri(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val myWords = db.wordbookDao().getByGroupSnapshot("我的单词本")
                val xml = YoudaoImportParser.exportToYoudaoXml(myWords)
                app.contentResolver.openOutputStream(uri)?.use { out ->
                    out.write(xml.toByteArray(StandardCharsets.UTF_8))
                }
                _uiState.value = _uiState.value.copy(statusBanner = "已导出「我的单词本」共 ${myWords.size} 个单词为 XML 格式。")
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(statusBanner = "导出失败: ${e.message}")
            }
        }
    }

    // --- SM-2 Forgetting Curve Review (Supports Reviewing Specific Book/Group) ---

    fun setReviewGroupFilter(group: String) {
        _uiState.value = _uiState.value.copy(reviewGroupFilter = group)
        refreshReviewQueue()
    }

    fun refreshReviewQueue() {
        viewModelScope.launch(Dispatchers.IO) {
            val group = _uiState.value.reviewGroupFilter
            val now = System.currentTimeMillis()
            val due = if (group == "全部") {
                db.wordbookDao().getDueForReview(now, limit = 50)
            } else {
                db.wordbookDao().getDueForReviewByGroup(group, now, limit = 50)
            }
            val scopedPool = if (group == "全部") {
                _uiState.value.sessionRelearnPool
            } else {
                _uiState.value.sessionRelearnPool.filterValues { it.groupName == group }
            }
            val merged = ReviewSessionQueue.mergeWithDue(due, scopedPool)
            _uiState.value = _uiState.value.copy(
                reviewQueue = merged,
                currentReviewIndex = 0,
                isCardFlipped = false
            )
        }
    }

    fun flipReviewCard() {
        _uiState.value = _uiState.value.copy(isCardFlipped = !_uiState.value.isCardFlipped)
    }

    /**
     * Example shown under the Chinese meaning on the review answer side.
     * Prefer the word's own stored example; otherwise look up the best
     * matching example from an already-downloaded dictionary that carries
     * bilingual examples (same rule as the wordbook's 例句 fill), so words
     * added after the fill — or words the fill could not cover — still show
     * an example while reviewing. Null = no example available anywhere.
     */
    private val _reviewExample = MutableStateFlow<ReviewExample?>(null)
    val reviewExample: StateFlow<ReviewExample?> = _reviewExample.asStateFlow()

    fun resolveReviewExample(entry: WordbookEntryEntity) {
        if (entry.exampleSentence.isNotBlank()) {
            _reviewExample.value = ReviewExample(entry.exampleSentence, entry.exampleTranslation)
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            val best = db.dictionaryDao().getExamplesForWords(listOf(entry.wordLower))
                .sortedWith(
                    compareBy(
                        { if (it.sourceId.startsWith("kajweb_")) 0 else 1 },
                        { it.example.length }
                    )
                ).firstOrNull()
            // The user may already have moved on; only publish for the card
            // still on screen.
            val current = _uiState.value.reviewQueue.getOrNull(_uiState.value.currentReviewIndex)
            if (current?.id == entry.id) {
                _reviewExample.value = best?.let { ReviewExample(it.example, it.exampleTranslation) }
            }
        }
    }

    fun clearReviewExample() {
        _reviewExample.value = null
    }

    fun submitReviewRating(rating: ReviewRating) {
        val state = _uiState.value
        val current = state.reviewQueue.getOrNull(state.currentReviewIndex) ?: return
        val now = System.currentTimeMillis()
        val scheduled = Sm2Scheduler.schedule(current, rating, nowMillis = now)

        viewModelScope.launch(Dispatchers.IO) {
            val updated = current.copy(
                repetitions = scheduled.repetitions,
                intervalDays = scheduled.intervalDays,
                easeFactor = scheduled.easeFactor,
                lapses = scheduled.lapses,
                masteryLevel = scheduled.masteryLevel,
                lastReviewedAt = now,
                nextReviewAt = scheduled.nextReviewAt,
                updatedAt = now
            )
            db.wordbookDao().update(updated)
            db.reviewLogDao().insert(
                ReviewLogEntity(
                    wordbookId = current.id,
                    word = current.word,
                    rating = rating.value,
                    intervalAfterDays = scheduled.intervalDays,
                    easeFactorAfter = scheduled.easeFactor,
                    reviewedAt = now
                )
            )

            val snapshot = ReviewSessionQueue.onRated(
                queue = state.reviewQueue,
                currentIndex = state.currentReviewIndex,
                relearnPool = state.sessionRelearnPool,
                updatedCard = updated,
                rating = rating
            )

            if (snapshot.currentIndex >= snapshot.queue.size) {
                val group = state.reviewGroupFilter
                val due = if (group == "全部") {
                    db.wordbookDao().getDueForReview(System.currentTimeMillis(), limit = 50)
                } else {
                    db.wordbookDao().getDueForReviewByGroup(group, System.currentTimeMillis(), limit = 50)
                }
                val scopedPool = if (group == "全部") {
                    snapshot.relearnPool
                } else {
                    snapshot.relearnPool.filterValues { it.groupName == group }
                }
                val merged = ReviewSessionQueue.mergeWithDue(due, scopedPool)
                _uiState.value = _uiState.value.copy(
                    reviewQueue = merged,
                    currentReviewIndex = 0,
                    sessionRelearnPool = snapshot.relearnPool,
                    isCardFlipped = false
                )
            } else {
                _uiState.value = _uiState.value.copy(
                    reviewQueue = snapshot.queue,
                    currentReviewIndex = snapshot.currentIndex,
                    sessionRelearnPool = snapshot.relearnPool,
                    isCardFlipped = false
                )
            }
        }
    }

    // --- Dictionary Downloads & Local User Dictionary Import ---

    fun downloadDictionary(item: DictionaryCatalogItem) {
        if (downloadJobs[item.id]?.isActive == true) return
        val job = viewModelScope.launch {
            try {
                downloader.downloadAndInstall(item) { progress ->
                    val updatedMap = _uiState.value.activeDownloads.toMutableMap()
                    updatedMap[item.id] = progress
                    _uiState.value = _uiState.value.copy(activeDownloads = updatedMap)
                }.onSuccess { count ->
                    _uiState.value = _uiState.value.copy(
                        statusBanner = "词典「${item.name}」安装成功，共 $count 条词目。"
                    )
                }.onFailure { err ->
                    _uiState.value = _uiState.value.copy(
                        statusBanner = "词典「${item.name}」下载失败：${err.message}"
                    )
                }
            } catch (_: CancellationException) {
                _uiState.value = _uiState.value.copy(statusBanner = "已取消下载「${item.name}」（已保留缓存，下次可断点续传）")
            } finally {
                downloadJobs.remove(item.id)
            }
        }
        downloadJobs[item.id] = job
    }

    fun importUserLocalDictionary(uri: Uri, displayName: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(statusBanner = "正在导入本地词典「$displayName」...")
            downloader.importUserLocalUri(uri, displayName).fold(
                onSuccess = { count ->
                    _uiState.value = _uiState.value.copy(
                        statusBanner = "本地词典「$displayName」导入成功，共 $count 条词目并已建立 FTS 索引。"
                    )
                },
                onFailure = { err ->
                    _uiState.value = _uiState.value.copy(statusBanner = "本地词典导入失败：${err.message}")
                }
            )
        }
    }

    fun cancelDownload(dictId: String) {
        downloader.cancelActiveCall(dictId)
        voiceDownloader.cancelActiveCall(dictId)
        downloadJobs.remove(dictId)?.cancel()
    }

    fun uninstallDictionary(dictId: String, name: String) {
        viewModelScope.launch {
            downloader.uninstallDictionary(dictId)
            val updatedMap = _uiState.value.activeDownloads.toMutableMap()
            updatedMap.remove(dictId)
            _uiState.value = _uiState.value.copy(
                activeDownloads = updatedMap,
                statusBanner = "已卸载词典「$name」"
            )
        }
    }

    // --- Neural Voice Model Downloads ---

    private fun refreshInstalledNeuralVoices() {
        viewModelScope.launch(Dispatchers.IO) {
            val ids = voiceDownloader.getInstalledVoices().map { it.item.id }.toSet()
            _uiState.value = _uiState.value.copy(installedNeuralVoiceIds = ids)
            ttsManager.refreshNeuralStatus()
        }
    }

    fun downloadNeuralVoice(item: NeuralVoiceModelItem) {
        if (downloadJobs[item.id]?.isActive == true) return
        val job = viewModelScope.launch {
            try {
                voiceDownloader.downloadAndInstall(item) { progress ->
                    val updatedMap = _uiState.value.activeDownloads.toMutableMap()
                    updatedMap[item.id] = progress
                    _uiState.value = _uiState.value.copy(activeDownloads = updatedMap)
                }.onSuccess {
                    refreshInstalledNeuralVoices()
                    _uiState.value = _uiState.value.copy(
                        statusBanner = "离线神经语音「${item.name}」安装完成，现在点击朗读即使用真人级离线语音！"
                    )
                }.onFailure { err ->
                    _uiState.value = _uiState.value.copy(
                        statusBanner = "神经语音「${item.name}」下载失败：${err.message}"
                    )
                }
            } catch (_: CancellationException) {
                _uiState.value = _uiState.value.copy(statusBanner = "已取消下载「${item.name}」（已保留缓存，下次可断点续传）")
            } finally {
                downloadJobs.remove(item.id)
            }
        }
        downloadJobs[item.id] = job
    }

    fun uninstallNeuralVoice(item: NeuralVoiceModelItem) {
        viewModelScope.launch {
            ttsManager.onVoiceUninstalled()
            voiceDownloader.uninstallVoice(item.id)
            val updatedMap = _uiState.value.activeDownloads.toMutableMap()
            updatedMap.remove(item.id)
            _uiState.value = _uiState.value.copy(
                activeDownloads = updatedMap,
                statusBanner = "已删除离线神经语音「${item.name}」"
            )
            refreshInstalledNeuralVoices()
        }
    }

    // --- Settings ---

    fun saveSettings(
        apiKey: String,
        model: String,
        baseUrl: String,
        githubMirror: String,
        ttsAccent: String,
        ttsRate: Float,
        preferNeuralVoice: Boolean
    ) {
        prefs.setGeminiApiKey(apiKey)
        prefs.setGeminiModel(model)
        prefs.setGeminiBaseUrl(baseUrl)
        prefs.setGithubMirrorPrefix(githubMirror)
        prefs.setTtsAccent(ttsAccent)
        prefs.setTtsSpeechRate(ttsRate)
        prefs.setPreferNeuralVoice(preferNeuralVoice)

        ttsManager.configureDefaults(ttsAccent, ttsRate, preferNeuralVoice)

        _uiState.value = _uiState.value.copy(
            apiKey = prefs.getGeminiApiKey(),
            geminiModel = prefs.getGeminiModel(),
            geminiBaseUrl = prefs.getGeminiBaseUrl(),
            githubMirrorPrefix = prefs.getGithubMirrorPrefix(),
            ttsAccent = prefs.getTtsAccent(),
            ttsSpeechRate = prefs.getTtsSpeechRate(),
            preferNeuralVoice = prefs.getPreferNeuralVoice(),
            statusBanner = if (prefs.isUsingFallbackPrefs) "设置已保存（当前为降级私有存储）" else "设置已加密保存"
        )
    }

    private fun startOfTodayMillis(): Long {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    companion object {
        val catalogItems: List<DictionaryCatalogItem> = DictionaryCatalog.items
        val neuralVoiceItems: List<NeuralVoiceModelItem> = NeuralVoiceCatalog.items
    }
}
