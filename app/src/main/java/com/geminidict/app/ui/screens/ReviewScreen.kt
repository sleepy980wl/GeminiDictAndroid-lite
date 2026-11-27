package com.geminidict.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "遗忘曲线智能复习 (SM-2)",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(6.dp))

        // Select which wordbook/exam book to review
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            reviewGroupChips.forEach { group ->
                FilterChip(
                    selected = uiState.reviewGroupFilter == group,
                    onClick = { viewModel.setReviewGroupFilter(group) },
                    label = { Text(group) }
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            StatMiniCard("剩余待复习", "$remainingCount", Modifier.weight(1f))
            StatMiniCard("今日已记牢", "$todayLearned", Modifier.weight(1f))
            StatMiniCard("已掌握", "$masteredCount/${scopedEntries.size}", Modifier.weight(1f))
        }

        Spacer(Modifier.height(10.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = !spellingMode,
                onClick = { spellingMode = false },
                label = { Text("词卡模式") }
            )
            FilterChip(
                selected = spellingMode,
                onClick = {
                    spellingMode = true
                    spellingInput = ""
                },
                label = { Text("拼写听写模式") }
            )
            Spacer(Modifier.weight(1f))
            IconButton(onClick = { viewModel.refreshReviewQueue() }) {
                Icon(Icons.Default.Refresh, contentDescription = "刷新待复习队列")
            }
        }

        Spacer(Modifier.height(12.dp))

        if (currentCard == null) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = if (scopedEntries.isEmpty()) "该单词本暂无词汇" else "当前到期词汇已全部复习完成！",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = if (scopedEntries.isEmpty()) {
                            "请先收藏、导入有道 XML，或在「单词本」页选择已下载词库并点击加入遗忘曲线学习。"
                        } else {
                            "系统已根据记忆衰减模型安排下次复习时间，保持每日打卡记忆更牢固。"
                        },
                        style = MaterialTheme.typography.bodySmall,
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
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp),
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
                        AssistChip(onClick = {}, label = { Text("本轮重学") })
                    }
                }
                Spacer(Modifier.height(16.dp))

                if (!spellingMode) {
                    Text(
                        text = currentCard.word,
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                    if (currentCard.phonetic.isNotBlank()) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "/${currentCard.phonetic}/",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Row {
                        OutlinedButton(onClick = { viewModel.speakEnglish(currentCard.word, "en-US") }) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null)
                            Spacer(Modifier.width(4.dp))
                            Text("美音")
                        }
                        Spacer(Modifier.width(8.dp))
                        OutlinedButton(onClick = { viewModel.speakEnglish(currentCard.word, "en-GB") }) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null)
                            Spacer(Modifier.width(4.dp))
                            Text("英音")
                        }
                    }
                } else {
                    Text(
                        text = currentCard.translation.ifBlank { "点击发音听写单词" },
                        style = MaterialTheme.typography.titleMedium,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedButton(onClick = { viewModel.speakEnglish(currentCard.word) }) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                        Text("播放离线发音")
                    }
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = spellingInput,
                        onValueChange = { spellingInput = it },
                        label = { Text("输入英文拼写") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (uiState.isCardFlipped) {
                        val isCorrect = spellingInput.trim().equals(currentCard.word.trim(), ignoreCase = true)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = if (isCorrect) "拼写正确！(${currentCard.word})" else "正确拼写：${currentCard.word}",
                            color = if (isCorrect) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(Modifier.height(20.dp))

                if (uiState.isCardFlipped) {
                    HorizontalDivider()
                    Spacer(Modifier.height(14.dp))
                    Text(
                        text = currentCard.translation.ifBlank { "(暂无中文释义)" },
                        style = MaterialTheme.typography.titleMedium,
                        textAlign = TextAlign.Center
                    )
                    if (currentCard.exampleSentence.isNotBlank()) {
                        Spacer(Modifier.height(10.dp))
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
                                Icon(Icons.Default.PlayArrow, contentDescription = "朗读例句")
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
                        text = "点击卡片查看释义与例句",
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
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("显示答案")
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                ReviewRating.entries.forEach { rating ->
                    val nextLabel = if (rating == ReviewRating.AGAIN) {
                        "本轮重学"
                    } else {
                        Sm2Scheduler.previewIntervalLabel(currentCard, rating)
                    }
                    val containerColor = when (rating) {
                        ReviewRating.AGAIN -> MaterialTheme.colorScheme.error
                        ReviewRating.HARD -> MaterialTheme.colorScheme.tertiary
                        ReviewRating.GOOD -> MaterialTheme.colorScheme.primary
                        ReviewRating.EASY -> MaterialTheme.colorScheme.secondary
                    }
                    Button(
                        onClick = {
                            spellingInput = ""
                            viewModel.submitReviewRating(rating)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = containerColor),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(rating.label, style = MaterialTheme.typography.labelLarge)
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
    Card(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
