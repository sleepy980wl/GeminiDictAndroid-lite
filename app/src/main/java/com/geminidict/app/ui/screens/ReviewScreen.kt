package com.geminidict.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.geminidict.app.domain.srs.ReviewRating
import com.geminidict.app.domain.srs.Sm2Scheduler
import com.geminidict.app.ui.icons.AppleIcons
import com.geminidict.app.ui.viewmodel.MainViewModel

@Composable
fun ReviewScreen(viewModel: MainViewModel) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val allEntries by viewModel.wordbookEntries.collectAsStateWithLifecycle()
    val groups by viewModel.wordbookGroups.collectAsStateWithLifecycle()
    val todayLearned by viewModel.todayLearnedCount.collectAsStateWithLifecycle()

    var spellingMode by remember { mutableStateOf(false) }
    var spellingInput by remember { mutableStateOf("") }

    val reviewGroupChips = remember(groups) {
        val list = LinkedHashSet<String>()
        list.add("全部")
        list.add("我的单词本")
        groups.forEach { if (it.isNotBlank()) list.add(it) }
        list.toList()
    }

    val scopedEntries = remember(allEntries, uiState.reviewGroupFilter) {
        if (uiState.reviewGroupFilter == "全部") allEntries
        else allEntries.filter { it.groupName == uiState.reviewGroupFilter }
    }
    val masteredCount = remember(scopedEntries) { scopedEntries.count { it.masteryLevel >= 4 } }
    val remainingCount = (uiState.reviewQueue.size - uiState.currentReviewIndex).coerceAtLeast(0)
    val currentCard = uiState.reviewQueue.getOrNull(uiState.currentReviewIndex)
    val isRelearning = currentCard != null && currentCard.id in uiState.sessionRelearnIds

    // Auto-read: when a new card appears, read the word once in the accent
    // chosen in Settings (speakEnglish with no override uses the saved
    // accent); when the answer is revealed, read its example sentence once.
    val currentCardId = currentCard?.id
    val isCardFlipped = uiState.isCardFlipped
    LaunchedEffect(currentCardId) {
        val card = currentCard
        if (card != null) viewModel.speakEnglish(card.word)
    }
    LaunchedEffect(isCardFlipped) {
        val card = currentCard
        if (isCardFlipped && card != null && card.exampleSentence.isNotBlank()) {
            viewModel.speakEnglish(card.exampleSentence)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = "智能复习",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "SM-2 遗忘曲线记忆调度",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = { viewModel.refreshReviewQueue() }) {
                Icon(
                    AppleIcons.ArrowClockwise,
                    contentDescription = "刷新待复习队列",
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        // Select which wordbook/exam book to review
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            reviewGroupChips.forEach { group ->
                FilterChip(
                    selected = uiState.reviewGroupFilter == group,
                    onClick = { viewModel.setReviewGroupFilter(group) },
                    shape = RoundedCornerShape(20.dp),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        enabled = true,
                        selected = uiState.reviewGroupFilter == group,
                        borderColor = MaterialTheme.colorScheme.outlineVariant,
                        borderWidth = 0.6.dp
                    ),
                    label = { Text(group, style = MaterialTheme.typography.labelMedium) }
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            StatMiniCard("剩余待复习", "$remainingCount", Modifier.weight(1f))
            StatMiniCard("今日已记牢", "$todayLearned", Modifier.weight(1f))
            StatMiniCard("已掌握", "$masteredCount/${scopedEntries.size}", Modifier.weight(1f))
        }

        Spacer(Modifier.height(10.dp))

        SegmentedControlBar(
            options = listOf("词卡记忆", "拼写听写"),
            selectedIndex = if (spellingMode) 1 else 0,
            onSelect = { idx ->
                spellingMode = idx == 1
                if (spellingMode) spellingInput = ""
            }
        )

        Spacer(Modifier.height(12.dp))

        if (currentCard == null) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        AppleIcons.CheckmarkCircle,
                        contentDescription = null,
                        modifier = Modifier.size(40.dp),
                        tint = MaterialTheme.colorScheme.tertiary
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = if (scopedEntries.isEmpty()) "该单词本暂无词汇" else "当前到期词汇已全部复习完成",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = if (scopedEntries.isEmpty()) {
                            "请先收藏、导入有道 XML，或在「单词本」页选择已下载词库并点击加入遗忘曲线学习。"
                        } else {
                            "系统已根据记忆衰减模型安排下次复习时间，保持每日打卡记忆更牢固。"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
            return
        }

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clickable { viewModel.flipReviewCard() },
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(0.6.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.75f)),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "剩余 $remainingCount 词 · ${currentCard.groupName} · Lv.${currentCard.masteryLevel}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (isRelearning) {
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = MaterialTheme.colorScheme.errorContainer
                        ) {
                            Text(
                                text = "本轮重学",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
                Spacer(Modifier.height(20.dp))

                if (!spellingMode) {
                    Text(
                        text = currentCard.word,
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                    if (currentCard.phonetic.isNotBlank()) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = "/${currentCard.phonetic}/",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(Modifier.height(14.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        FilledTonalButton(
                            onClick = { viewModel.speakEnglish(currentCard.word, "en-US") },
                            shape = RoundedCornerShape(20.dp),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            Icon(AppleIcons.SpeakerWave, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(5.dp))
                            Text("美音")
                        }
                        FilledTonalButton(
                            onClick = { viewModel.speakEnglish(currentCard.word, "en-GB") },
                            shape = RoundedCornerShape(20.dp),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            Icon(AppleIcons.SpeakerWave, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(5.dp))
                            Text("英音")
                        }
                    }
                } else {
                    Text(
                        text = currentCard.translation.ifBlank { "点击发音听写单词" },
                        style = MaterialTheme.typography.titleMedium,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(12.dp))
                    FilledTonalButton(
                        onClick = { viewModel.speakEnglish(currentCard.word) },
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        Icon(AppleIcons.SpeakerWave, contentDescription = null, modifier = Modifier.size(17.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("播放离线发音")
                    }
                    Spacer(Modifier.height(14.dp))
                    OutlinedTextField(
                        value = spellingInput,
                        onValueChange = { spellingInput = it },
                        label = { Text("输入英文拼写") },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (uiState.isCardFlipped) {
                        val isCorrect = spellingInput.trim().equals(currentCard.word.trim(), ignoreCase = true)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = if (isCorrect) "拼写正确！（${currentCard.word}）" else "正确拼写：${currentCard.word}",
                            color = if (isCorrect) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(Modifier.height(22.dp))

                if (uiState.isCardFlipped) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = currentCard.translation.ifBlank { "(暂无中文释义)" },
                        style = MaterialTheme.typography.titleMedium,
                        textAlign = TextAlign.Center
                    )
                    if (currentCard.exampleSentence.isNotBlank()) {
                        Spacer(Modifier.height(12.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = currentCard.exampleSentence,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (currentCard.exampleTranslation.isNotBlank()) {
                                    Text(
                                        text = currentCard.exampleTranslation,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            IconButton(onClick = { viewModel.speakEnglish(currentCard.exampleSentence) }) {
                                Icon(
                                    AppleIcons.SpeakerWave,
                                    contentDescription = "朗读例句",
                                    modifier = Modifier.size(18.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                    if (currentCard.note.isNotBlank()) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = "笔记：${currentCard.note}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                } else {
                    Text(
                        text = "轻触卡片查看释义与例句",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        if (!uiState.isCardFlipped) {
            Button(
                onClick = { viewModel.flipReviewCard() },
                shape = RoundedCornerShape(14.dp),
                contentPadding = PaddingValues(vertical = 13.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("显示答案", fontWeight = FontWeight.SemiBold)
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ReviewRating.entries.forEach { rating ->
                    val nextLabel = if (rating == ReviewRating.AGAIN) {
                        "本轮重学"
                    } else {
                        Sm2Scheduler.previewIntervalLabel(currentCard, rating)
                    }
                    val containerColor = when (rating) {
                        ReviewRating.AGAIN -> MaterialTheme.colorScheme.error
                        ReviewRating.HARD -> MaterialTheme.colorScheme.secondary
                        ReviewRating.GOOD -> MaterialTheme.colorScheme.primary
                        ReviewRating.EASY -> MaterialTheme.colorScheme.tertiary
                    }
                    Button(
                        onClick = {
                            spellingInput = ""
                            viewModel.submitReviewRating(rating)
                        },
                        shape = RoundedCornerShape(14.dp),
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = containerColor),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(rating.label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                            Text(nextLabel, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatMiniCard(label: String, value: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
