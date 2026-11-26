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
import com.geminidict.app.data.youdao.YoudaoImportParser
import com.geminidict.app.data.youdao.YoudaoParsedWord
import com.geminidict.app.data.youdao.YoudaoSqliteReader
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

data class MainUiState(
    val query: String = "",
    val translateSubTab: Int = 0, // 0=中英词典, 1=新华字典
    val offlineResults: List<DictionaryEntryEntity> = emptyList(),
    val chineseResults: List<ChineseDictEntryEntity> = emptyList(),
    val geminiResult: GeminiTranslationResult? = null,
    val isGeminiLoading: Boolean = false,
    val geminiError: String? = null,
    val statusBanner: String? = null,
    val securityWarning: String? = null,

    // Wordbook filter & sort
    val wordbookQuery: String = "",
    val selectedGroup: String = "全部",
    val wordbookSort: WordbookSort = WordbookSort.NEWEST,

    // Review session
    val reviewQueue: List<WordbookEntryEntity> = emptyList(),
    val currentReviewIndex: Int = 0,
    val sessionRelearnPool: Map<Long, WordbookEntryEntity> = emptyMap(),
    val isCardFlipped: Boolean = false,

    // Dictionary download state
    val activeDownloads: Map<String, DownloadProgress> = emptyMap(),

    // Settings & connection test
    val apiKey: String = "",
    val geminiModel: String = "",
    val geminiBaseUrl: String = "",
    val githubMirrorPrefix: String = "",
    val ttsAccent: String = "en-US",
    val ttsSpeechRate: Float = 0.95f,
    val isTestingConnection: Boolean = false,
    val connectionTestMessage: String? = null
) {
    val sessionRelearnIds: Set<Long> get() = sessionRelearnPool.keys
}

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as GeminiDictApplication
    private val db = app.database
    private val prefs = app.securePrefs
    private val geminiService = app.geminiService
    private val downloader = app.dictionaryDownloader
    val ttsManager = app.ttsManager

    private val _uiState = MutableStateFlow(
        MainUiState(
            apiKey = prefs.getGeminiApiKey(),
            geminiModel = prefs.getGeminiModel(),
            geminiBaseUrl = prefs.getGeminiBaseUrl(),
            githubMirrorPrefix = prefs.getGithubMirrorPrefix(),
            ttsAccent = prefs.getTtsAccent(),
            ttsSpeechRate = prefs.getTtsSpeechRate(),
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
            delay(180)
            val escaped = SqlLikeEscaper.escape(trimmed)
            val lower = trimmed.lowercase(Locale.ROOT)
            val escapedLower = SqlLikeEscaper.escape(lower)

            if (activeTab == 0) {
                val merged = withContext(Dispatchers.IO) {
                    val raw = db.dictionaryDao().searchRaw(
                        queryLower = lower,
                        prefixPattern = "$escapedLower%",
                        containsPattern = "%$escaped%",
                        rawLimit = 120
                    )
                    // Deduplicate by wordLower and enrich primary entry with paired bilingual example if another source has one
                    val seen = LinkedHashMap<String, DictionaryEntryEntity>()
                    for (item in raw) {
                        val existing = seen[item.wordLower]
                        if (existing == null) {
                            if (seen.size < 30) {
                                seen[item.wordLower] = item
                            }
                        } else if (existing.example.isBlank() && item.example.isNotBlank()) {
                            seen[item.wordLower] = existing.copy(
                                example = item.example,
                                exampleTranslation = item.exampleTranslation
                            )
                        }
                    }
                    seen.values.toList()
                }
                _uiState.value = _uiState.value.copy(offlineResults = merged)
            } else {
                val zhResults = withContext(Dispatchers.IO) {
                    db.chineseDictDao().search(
                        query = trimmed,
                        prefixPattern = "$escaped%",
                        containsPattern = "%$escaped%",
                        limit = 30
                    )
                }
                _uiState.value = _uiState.value.copy(chineseResults = zhResults)
            }
        }
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

    // --- Wordbook Operations ---

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
                        translation = entry.translation,
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
        _uiState.value = _uiState.value.copy(
            wordbookQuery = query ?: _uiState.value.wordbookQuery,
            selectedGroup = group ?: _uiState.value.selectedGroup,
            wordbookSort = sort ?: _uiState.value.wordbookSort
        )
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
                            // Prefer entry with example sentence if available, else first
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
            statusBanner = "有道单词本导入完成：新增 $added 词，已存在跳过 $skipped 词（已按原有熟练度错峰安排复习）。"
        )
        refreshReviewQueue()
    }

    fun exportWordbookToUri(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val all = db.wordbookDao().getAllSnapshot()
                val xml = YoudaoImportParser.exportToYoudaoXml(all)
                app.contentResolver.openOutputStream(uri)?.use { out ->
                    out.write(xml.toByteArray(StandardCharsets.UTF_8))
                }
                _uiState.value = _uiState.value.copy(statusBanner = "已导出 ${all.size} 个单词为有道 XML 格式。")
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(statusBanner = "导出失败: ${e.message}")
            }
        }
    }

    // --- SM-2 Forgetting Curve Review (Preserves Relearn Pool across Last Card & Refresh) ---

    fun refreshReviewQueue() {
        viewModelScope.launch(Dispatchers.IO) {
            val due = db.wordbookDao().getDueForReview(System.currentTimeMillis(), limit = 50)
            val merged = ReviewSessionQueue.mergeWithDue(due, _uiState.value.sessionRelearnPool)
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
                val due = db.wordbookDao().getDueForReview(System.currentTimeMillis(), limit = 50)
                val merged = ReviewSessionQueue.mergeWithDue(due, snapshot.relearnPool)
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

    // --- Dictionary Downloads (Resumable & Cancellable) ---

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

    fun cancelDownload(dictId: String) {
        downloader.cancelActiveCall(dictId)
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

    // --- Settings ---

    fun saveSettings(
        apiKey: String,
        model: String,
        baseUrl: String,
        githubMirror: String,
        ttsAccent: String,
        ttsRate: Float
    ) {
        prefs.setGeminiApiKey(apiKey)
        prefs.setGeminiModel(model)
        prefs.setGeminiBaseUrl(baseUrl)
        prefs.setGithubMirrorPrefix(githubMirror)
        prefs.setTtsAccent(ttsAccent)
        prefs.setTtsSpeechRate(ttsRate)

        ttsManager.configureDefaults(ttsAccent, ttsRate)

        _uiState.value = _uiState.value.copy(
            apiKey = prefs.getGeminiApiKey(),
            geminiModel = prefs.getGeminiModel(),
            geminiBaseUrl = prefs.getGeminiBaseUrl(),
            githubMirrorPrefix = prefs.getGithubMirrorPrefix(),
            ttsAccent = prefs.getTtsAccent(),
            ttsSpeechRate = prefs.getTtsSpeechRate(),
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
    }
}
