package com.geminidict.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.geminidict.app.data.local.entity.ChineseDictEntryEntity
import com.geminidict.app.data.remote.GeminiTranslationResult
import com.geminidict.app.ui.viewmodel.DictSearchHit
import com.geminidict.app.ui.viewmodel.MainViewModel
import java.util.Locale

@Composable
fun TranslateScreen(viewModel: MainViewModel) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val wordbook by viewModel.wordbookEntries.collectAsStateWithLifecycle()
    val totalDictCount by viewModel.totalDictCount.collectAsStateWithLifecycle()
    val bookmarkedSet = remember(wordbook) { wordbook.map { it.wordLower }.toSet() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "离线词典 & Gemini AI 翻译",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "本地词条：$totalDictCount 条 · 支持 FTS4 毫秒检索与英文拼写纠错",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        if (totalDictCount in 1..200) {
            Spacer(Modifier.height(6.dp))
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
            ) {
                Text(
                    text = "当前仅内置 $totalDictCount 个示例高频词，请前往底部「词库」页下载韦氏英英、ECDICT、双语例句词库、CC-CEDICT 或中华新华字典。",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(10.dp)
                )
            }
        }

        Spacer(Modifier.height(10.dp))

        OutlinedTextField(
            value = uiState.query,
            onValueChange = { viewModel.onQueryChanged(it) },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("输入英文（支持拼写模糊纠错）、中文词语、成语或整句...") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = {
                if (uiState.query.isNotEmpty()) {
                    IconButton(onClick = { viewModel.onQueryChanged("") }) {
                        Icon(Icons.Default.Clear, contentDescription = "清空")
                    }
                }
            },
            maxLines = 3
        )

        Spacer(Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = { viewModel.translateWithGemini() },
                enabled = uiState.query.isNotBlank() && !uiState.isGeminiLoading,
                modifier = Modifier.weight(1f)
            ) {
                if (uiState.isGeminiLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Gemini 翻译中...")
                } else {
                    Icon(Icons.Default.Star, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("使用 Gemini 翻译")
                }
            }
            if (uiState.query.any { it in 'a'..'z' || it in 'A'..'Z' }) {
                OutlinedButton(onClick = { viewModel.speakEnglish(uiState.query) }) {
                    Icon(Icons.Default.PlayArrow, contentDescription = "离线朗读", modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("朗读")
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        TabRow(selectedTabIndex = uiState.translateSubTab) {
            Tab(
                selected = uiState.translateSubTab == 0,
                onClick = { viewModel.onTranslateSubTabChanged(0) },
                text = { Text("英汉/英英/汉英 (${uiState.offlineResults.size})") }
            )
            Tab(
                selected = uiState.translateSubTab == 1,
                onClick = { viewModel.onTranslateSubTabChanged(1) },
                text = { Text("新华字典 (${uiState.chineseResults.size})") }
            )
        }

        Spacer(Modifier.height(8.dp))

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (uiState.geminiError != null) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        )
                    ) {
                        Text(
                            text = uiState.geminiError ?: "",
                            modifier = Modifier.padding(12.dp),
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }

            uiState.geminiResult?.let { geminiRes ->
                item {
                    GeminiResultCard(
                        result = geminiRes,
                        onSpeak = { text, accent -> viewModel.speakEnglish(text, accent) },
                        onSave = { viewModel.saveGeminiToWordbook(geminiRes) }
                    )
                }
            }

            if (uiState.translateSubTab == 0) {
                if (uiState.query.isNotBlank() && uiState.offlineResults.isEmpty() && !uiState.isGeminiLoading) {
                    item {
                        Text(
                            text = "本地词库未匹配到「${uiState.query}」。可点击上方「使用 Gemini 翻译」或在「词库」页下载更多离线词典。",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 12.dp)
                        )
                    }
                }
                items(uiState.offlineResults, key = { "en_${it.entry.id}_${it.entry.wordLower}" }) { hit ->
                    OfflineDictCard(
                        hit = hit,
                        isBookmarked = hit.entry.wordLower in bookmarkedSet,
                        onSpeak = { text, accent -> viewModel.speakEnglish(text, accent) },
                        onToggleBookmark = { viewModel.toggleBookmarkFromDict(hit.entry) }
                    )
                }
            } else {
                if (uiState.query.isNotBlank() && uiState.chineseResults.isEmpty()) {
                    item {
                        Text(
                            text = "本地新华字典未匹配到「${uiState.query}」。请前往「词库」页一键下载「中华新华字典」汉字/成语/词语库。",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 12.dp)
                        )
                    }
                }
                items(uiState.chineseResults, key = { "zh_${it.id}" }) { entry ->
                    XinhuaDictCard(entry)
                }
            }
        }
    }
}

@Composable
private fun OfflineDictCard(
    hit: DictSearchHit,
    isBookmarked: Boolean,
    onSpeak: (String, String?) -> Unit,
    onToggleBookmark: () -> Unit
) {
    val entry = hit.entry
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = entry.word,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        if (!hit.matchHint.isNullOrBlank()) {
                            AssistChip(onClick = {}, label = { Text(hit.matchHint) })
                        }
                    }
                    if (entry.phonetic.isNotBlank()) {
                        Text(
                            text = "/${entry.phonetic}/",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                IconButton(onClick = { onSpeak(entry.word, "en-US") }) {
                    Icon(Icons.Default.PlayArrow, contentDescription = "离线发音")
                }
                IconButton(onClick = onToggleBookmark) {
                    Icon(
                        imageVector = if (isBookmarked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = "收藏到我的单词本",
                        tint = if (isBookmarked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (entry.translation.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(text = entry.translation, style = MaterialTheme.typography.bodyMedium)
            }
            if (entry.definition.isNotBlank() && entry.definition != entry.translation) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = entry.definition.take(600),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (entry.example.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                HorizontalDivider()
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "例句：${entry.example}",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium
                        )
                        if (entry.exampleTranslation.isNotBlank()) {
                            Text(
                                text = entry.exampleTranslation,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    IconButton(onClick = { onSpeak(entry.example, null) }) {
                        Icon(
                            Icons.Default.PlayArrow,
                            contentDescription = "离线朗读例句",
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            } else if (entry.sourceId != "cc_cedict") {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "注：该词条暂无离线例句，可在「词库」下载「英汉·例句」词库，或点击上方「使用 Gemini 翻译」生成双语例句。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (entry.tag.isNotBlank() || entry.collins > 0 || entry.oxford > 0) {
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (entry.collins > 0) {
                        AssistChip(onClick = {}, label = { Text("柯林斯 ${entry.collins}★") })
                    }
                    if (entry.oxford > 0) {
                        AssistChip(onClick = {}, label = { Text("牛津3000") })
                    }
                    if (entry.tag.isNotBlank()) {
                        AssistChip(onClick = {}, label = { Text(entry.tag.uppercase(Locale.ROOT)) })
                    }
                }
            }
        }
    }
}

@Composable
private fun GeminiResultCard(
    result: GeminiTranslationResult,
    onSpeak: (String, String?) -> Unit,
    onSave: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
        )
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Star,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "Gemini AI 翻译 (${result.modelUsed})",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onSave) {
                    Icon(Icons.Default.FavoriteBorder, contentDescription = "收藏或更新到单词本")
                }
            }

            Spacer(Modifier.height(4.dp))
            Text(
                text = result.translation,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            if (result.phonetic.isNotBlank() || result.partOfSpeech.isNotBlank()) {
                Text(
                    text = listOf(result.partOfSpeech, result.phonetic).filter { it.isNotBlank() }.joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (result.detailedExplanation.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(text = result.detailedExplanation, style = MaterialTheme.typography.bodyMedium)
            }
            if (result.examples.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                HorizontalDivider()
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "双语例句（点击播放离线朗读）",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                result.examples.forEach { ex ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = ex.english, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                            if (ex.chinese.isNotBlank()) {
                                Text(
                                    text = ex.chinese,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        IconButton(onClick = { onSpeak(ex.english, null) }) {
                            Icon(Icons.Default.PlayArrow, contentDescription = "朗读例句", modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun XinhuaDictCard(entry: ChineseDictEntryEntity) {
    val badge = when (entry.category) {
        "word" -> "汉字"
        "idiom" -> "成语"
        "ci" -> "词语"
        "xiehouyu" -> "歇后语"
        else -> "中文"
    }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = entry.headword,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.width(8.dp))
                if (entry.pinyin.isNotBlank()) {
                    Text(
                        text = "[${entry.pinyin}]",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Spacer(Modifier.weight(1f))
                AssistChip(onClick = {}, label = { Text(badge) })
            }
            if (entry.radicals.isNotBlank() || entry.strokes.isNotBlank()) {
                Text(
                    text = "部首：${entry.radicals}   总笔画：${entry.strokes}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (entry.explanation.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(text = entry.explanation.take(600), style = MaterialTheme.typography.bodyMedium)
            }
            if (entry.derivation.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "出处：${entry.derivation}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (entry.example.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "例句：${entry.example}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
