package com.geminidict.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.geminidict.app.data.local.entity.ChineseDictEntryEntity
import com.geminidict.app.data.remote.GeminiTranslationResult
import com.geminidict.app.ui.icons.AppleIcons
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
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Text(
            text = "查词 & 翻译",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "本地词条 $totalDictCount 条 · FTS4 毫秒检索 & 英文拼写纠错",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        if (totalDictCount in 1..200) {
            Spacer(Modifier.height(8.dp))
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Text(
                    text = "当前仅内置 $totalDictCount 个示例高频词，可前往「词库」页一键下载 ECDICT、韦氏英英、双语例句与中华新华字典。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(12.dp)
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        OutlinedTextField(
            value = uiState.query,
            onValueChange = { viewModel.onQueryChanged(it) },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surface,
                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                focusedBorderColor = MaterialTheme.colorScheme.primary
            ),
            placeholder = { Text("输入英文（支持拼写模糊纠错）、中文词语或整句...") },
            leadingIcon = {
                Icon(
                    AppleIcons.MagnifyingGlass,
                    contentDescription = null,
                    modifier = Modifier.size(19.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            trailingIcon = {
                if (uiState.query.isNotEmpty()) {
                    IconButton(onClick = { viewModel.onQueryChanged("") }) {
                        Icon(AppleIcons.Xmark, contentDescription = "清空", modifier = Modifier.size(17.dp))
                    }
                }
            },
            maxLines = 3
        )

        Spacer(Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = { viewModel.translateWithGemini() },
                enabled = uiState.query.isNotBlank() && !uiState.isGeminiLoading,
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(vertical = 11.dp),
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
                    Icon(AppleIcons.Sparkles, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Gemini AI 深度翻译", fontWeight = FontWeight.Medium)
                }
            }
            if (uiState.query.any { it in 'a'..'z' || it in 'A'..'Z' }) {
                FilledTonalButton(
                    onClick = { viewModel.speakEnglish(uiState.query) },
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 11.dp)
                ) {
                    Icon(AppleIcons.SpeakerWave, contentDescription = "离线朗读", modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(5.dp))
                    Text("朗读")
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        // iOS-style Segmented Control
        SegmentedControlBar(
            options = listOf(
                "英汉/英英/汉英 (${uiState.offlineResults.size})",
                "新华字典 (${uiState.chineseResults.size})"
            ),
            selectedIndex = uiState.translateSubTab,
            onSelect = { viewModel.onTranslateSubTabChanged(it) }
        )

        Spacer(Modifier.height(10.dp))

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (uiState.geminiError != null) {
                item {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                    ) {
                        Text(
                            text = uiState.geminiError ?: "",
                            modifier = Modifier.padding(14.dp),
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
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)),
                            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                        ) {
                            Text(
                                text = "本地词库未匹配到「${uiState.query}」。可点击上方「Gemini AI 深度翻译」或在「词库」页下载更多离线词典。",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(16.dp)
                            )
                        }
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
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)),
                            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                        ) {
                            Text(
                                text = "本地新华字典未匹配到「${uiState.query}」。请前往「词库」页一键下载「中华新华字典」汉字/成语/词语库。",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(16.dp)
                            )
                        }
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
internal fun SegmentedControlBar(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.75f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(3.dp),
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            options.forEachIndexed { index, label ->
                val selected = index == selectedIndex
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(9.dp))
                        .clickable { onSelect(index) },
                    shape = RoundedCornerShape(9.dp),
                    color = if (selected) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surface.copy(alpha = 0f),
                    shadowElevation = if (selected) 1.dp else 0.dp
                ) {
                    Box(
                        modifier = Modifier.padding(vertical = 8.dp, horizontal = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PillBadge(text: String) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp)
        )
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
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = entry.word,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        if (!hit.matchHint.isNullOrBlank()) {
                            PillBadge(hit.matchHint)
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
                    Icon(
                        AppleIcons.SpeakerWave,
                        contentDescription = "离线发音",
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                IconButton(onClick = onToggleBookmark) {
                    Icon(
                        imageVector = if (isBookmarked) AppleIcons.HeartFill else AppleIcons.HeartOutline,
                        contentDescription = "收藏到我的单词本",
                        modifier = Modifier.size(20.dp),
                        tint = if (isBookmarked) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (entry.translation.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
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
                Spacer(Modifier.height(10.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                Spacer(Modifier.height(8.dp))
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
                        if (!hit.exampleSourceLabel.isNullOrBlank()) {
                            Text(
                                text = "例句来自「${hit.exampleSourceLabel}」",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    IconButton(onClick = { onSpeak(entry.example, null) }) {
                        Icon(
                            AppleIcons.SpeakerWave,
                            contentDescription = "离线朗读例句",
                            modifier = Modifier.size(17.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            } else if (entry.sourceId != "cc_cedict") {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "注：该词条暂无离线例句，可在「词库」下载「英汉·例句」词库，或点击上方「Gemini AI 深度翻译」生成双语例句。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (entry.tag.isNotBlank() || entry.collins > 0 || entry.oxford > 0) {
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (entry.collins > 0) PillBadge("柯林斯 ${entry.collins}★")
                    if (entry.oxford > 0) PillBadge("牛津3000")
                    if (entry.tag.isNotBlank()) PillBadge(entry.tag.uppercase(Locale.ROOT))
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
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
        ),
        border = BorderStroke(0.6.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    AppleIcons.Sparkles,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(19.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "Gemini AI 翻译 · ${result.modelUsed}",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onSave) {
                    Icon(
                        AppleIcons.HeartOutline,
                        contentDescription = "收藏到我的单词本",
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
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
                Spacer(Modifier.height(10.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "双语例句（点击右侧图标离线朗读）",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                result.examples.forEach { ex ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 6.dp),
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
                            Icon(
                                AppleIcons.SpeakerWave,
                                contentDescription = "朗读例句",
                                modifier = Modifier.size(17.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
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
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = entry.headword,
                    style = MaterialTheme.typography.titleLarge,
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
                PillBadge(badge)
            }
            if (entry.radicals.isNotBlank() || entry.strokes.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
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
