package com.geminidict.app.ui.screens

import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.geminidict.app.data.dict.DictionaryCatalog
import com.geminidict.app.data.local.entity.DictionaryEntryEntity
import com.geminidict.app.data.local.entity.WordbookEntryEntity
import com.geminidict.app.ui.icons.AppleIcons
import com.geminidict.app.ui.viewmodel.MainViewModel
import com.geminidict.app.ui.viewmodel.WordbookSort
import java.util.Locale

@Composable
fun WordbookScreen(viewModel: MainViewModel) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val allEntries by viewModel.wordbookEntries.collectAsStateWithLifecycle()
    val groups by viewModel.wordbookGroups.collectAsStateWithLifecycle()
    val downloadedDicts by viewModel.downloadedDicts.collectAsStateWithLifecycle()

    var editingEntry by remember { mutableStateOf<WordbookEntryEntity?>(null) }
    var deletingEntry by remember { mutableStateOf<WordbookEntryEntity?>(null) }
    var showCreateDialog by remember { mutableStateOf(false) }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            var displayName = "youdao_wordbook.xml"
            context.contentResolver.query(uri, null, null, null, null)?.use { c ->
                val nameIndex = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (c.moveToFirst() && nameIndex >= 0) {
                    displayName = c.getString(nameIndex) ?: displayName
                }
            }
            viewModel.importYoudaoFromUri(uri, displayName)
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/xml")
    ) { uri ->
        if (uri != null) {
            viewModel.exportWordbookToUri(uri)
        }
    }

    // Ordered chip list: 全部 -> 我的单词本 -> 已下载词库 -> 其他自定义分组
    val downloadedIds = remember(downloadedDicts) { downloadedDicts.map { it.id }.toSet() }
    val downloadedBookLabels = remember(downloadedIds) {
        DictionaryCatalog.items
            .filter { it.wordbookLabel != null && it.id in downloadedIds }
            .map { it.wordbookLabel!! }
    }
    val orderedGroupChips = remember(groups, downloadedBookLabels) {
        val list = LinkedHashSet<String>()
        list.add("全部")
        list.add("我的单词本")
        downloadedBookLabels.forEach { list.add(it) }
        groups.forEach { if (it.isNotBlank()) list.add(it) }
        list.toList()
    }

    val selectedDownloadedBook = remember(uiState.selectedGroup, downloadedIds) {
        val book = DictionaryCatalog.wordbookBooksByLabel[uiState.selectedGroup]
        if (book != null && book.id in downloadedIds) book else null
    }
    val selectedBookTotalCount = remember(selectedDownloadedBook, downloadedDicts) {
        if (selectedDownloadedBook == null) 0
        else downloadedDicts.firstOrNull { it.id == selectedDownloadedBook.id }?.entryCount ?: 0
    }

    val filteredEntries = remember(allEntries, uiState.wordbookQuery, uiState.selectedGroup, uiState.wordbookSort) {
        allEntries
            .filter { entry ->
                (uiState.selectedGroup == "全部" || entry.groupName == uiState.selectedGroup) &&
                    (uiState.wordbookQuery.isBlank() ||
                        entry.word.contains(uiState.wordbookQuery, ignoreCase = true) ||
                        entry.translation.contains(uiState.wordbookQuery, ignoreCase = true) ||
                        entry.note.contains(uiState.wordbookQuery, ignoreCase = true))
            }
            .let { list ->
                when (uiState.wordbookSort) {
                    WordbookSort.NEWEST -> list.sortedByDescending { it.createdAt }
                    WordbookSort.ALPHABETICAL -> list.sortedBy { it.wordLower }
                    WordbookSort.MASTERY_ASC -> list.sortedBy { it.masteryLevel }
                    WordbookSort.DUE_FIRST -> list.sortedBy { it.nextReviewAt }
                }
            }
    }

    val filteredPreviewEntries = remember(uiState.previewBookEntries, uiState.wordbookQuery) {
        uiState.previewBookEntries.filter { entry ->
            uiState.wordbookQuery.isBlank() ||
                entry.word.contains(uiState.wordbookQuery, ignoreCase = true) ||
                entry.translation.contains(uiState.wordbookQuery, ignoreCase = true)
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showCreateDialog = true },
                shape = CircleShape,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 3.dp)
            ) {
                Icon(AppleIcons.Plus, contentDescription = "手动添加单词", modifier = Modifier.size(22.dp))
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "我的单词本",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "导入默认存入「我的单词本」，导出仅导出「我的单词本」",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Text(
                        text = "${filteredEntries.size} / ${allEntries.size}",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                FilledTonalButton(
                    onClick = { importLauncher.launch(arrayOf("*/*")) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(vertical = 10.dp)
                ) {
                    Icon(AppleIcons.SquareArrowDown, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("导入", fontWeight = FontWeight.Medium)
                }
                OutlinedButton(
                    onClick = { viewModel.fillMissingExamples() },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(0.8.dp, MaterialTheme.colorScheme.outlineVariant),
                    contentPadding = PaddingValues(vertical = 10.dp)
                ) {
                    Icon(AppleIcons.BookClosed, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("例句", fontWeight = FontWeight.Medium)
                }
                OutlinedButton(
                    onClick = { exportLauncher.launch("my_wordbook_youdao.xml") },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(0.8.dp, MaterialTheme.colorScheme.outlineVariant),
                    contentPadding = PaddingValues(vertical = 10.dp)
                ) {
                    Icon(AppleIcons.SquareArrowUp, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("导出", fontWeight = FontWeight.Medium)
                }
            }

            Spacer(Modifier.height(10.dp))

            OutlinedTextField(
                value = uiState.wordbookQuery,
                onValueChange = { viewModel.updateWordbookFilter(query = it) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                    focusedBorderColor = MaterialTheme.colorScheme.primary
                ),
                placeholder = { Text("搜索单词、释义或笔记...") },
                leadingIcon = {
                    Icon(
                        AppleIcons.MagnifyingGlass,
                        contentDescription = null,
                        modifier = Modifier.size(19.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                trailingIcon = {
                    if (uiState.wordbookQuery.isNotEmpty()) {
                        IconButton(onClick = { viewModel.updateWordbookFilter(query = "") }) {
                            Icon(AppleIcons.Xmark, contentDescription = "清空", modifier = Modifier.size(17.dp))
                        }
                    }
                },
                singleLine = true
            )

            Spacer(Modifier.height(10.dp))

            // Ordered group chips: 全部 -> 我的单词本 -> Downloaded vocab books -> Custom groups
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                orderedGroupChips.forEach { group ->
                    FilterChip(
                        selected = uiState.selectedGroup == group,
                        onClick = { viewModel.updateWordbookFilter(group = group) },
                        shape = RoundedCornerShape(20.dp),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                            containerColor = MaterialTheme.colorScheme.surface
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = uiState.selectedGroup == group,
                            borderColor = MaterialTheme.colorScheme.outlineVariant,
                            borderWidth = 0.6.dp
                        ),
                        label = { Text(group, style = MaterialTheme.typography.labelMedium) }
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                val sorts = listOf(
                    WordbookSort.NEWEST to "最新收藏",
                    WordbookSort.DUE_FIRST to "待复习优先",
                    WordbookSort.MASTERY_ASC to "熟练度从低到高",
                    WordbookSort.ALPHABETICAL to "A-Z 排序"
                )
                sorts.forEach { (sort, label) ->
                    FilterChip(
                        selected = uiState.wordbookSort == sort,
                        onClick = { viewModel.updateWordbookFilter(sort = sort) },
                        shape = RoundedCornerShape(20.dp),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.primary,
                            containerColor = MaterialTheme.colorScheme.surface
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = uiState.wordbookSort == sort,
                            borderColor = MaterialTheme.colorScheme.outlineVariant,
                            borderWidth = 0.6.dp
                        ),
                        label = { Text(label, style = MaterialTheme.typography.labelSmall) }
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            // Action banner when viewing a downloaded vocabulary book
            if (selectedDownloadedBook != null && filteredEntries.size < selectedBookTotalCount) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = "已下载词库「${selectedDownloadedBook.wordbookLabel}」（共 $selectedBookTotalCount 词，已加入学习 ${filteredEntries.size} 词）",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Spacer(Modifier.height(8.dp))
                        Button(
                            enabled = !uiState.isImportingBookToWordbook,
                            shape = RoundedCornerShape(12.dp),
                            onClick = { viewModel.importDownloadedBookAsWordbook(selectedDownloadedBook) }
                        ) {
                            if (uiState.isImportingBookToWordbook) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                                Spacer(Modifier.width(8.dp))
                                Text("正在加入学习计划...")
                            } else {
                                Text("将全部 $selectedBookTotalCount 词加入遗忘曲线学习")
                            }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }

            if (filteredEntries.isNotEmpty()) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 80.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(filteredEntries, key = { it.id }) { entry ->
                        WordbookItemCard(
                            entry = entry,
                            onSpeak = { viewModel.speakEnglish(it) },
                            onEdit = { editingEntry = entry },
                            onRequestDelete = { deletingEntry = entry }
                        )
                    }
                }
            } else if (selectedDownloadedBook != null && filteredPreviewEntries.isNotEmpty()) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 80.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(filteredPreviewEntries, key = { "preview_${it.id}" }) { dictEntry ->
                        BookPreviewCard(
                            entry = dictEntry,
                            onSpeak = { viewModel.speakEnglish(it) },
                            onAddSingle = { viewModel.toggleBookmarkFromDict(dictEntry) }
                        )
                    }
                }
            } else {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Text(
                        text = "当前分组暂无词汇。可在「查词」页收藏，点上方「导入」导入有道 XML/TXT/CSV 到「我的单词本」，或在「词库」页下载专八/TOEFL/GRE/考研/四六级/雅思/商务英语后直接在此学习。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(18.dp)
                    )
                }
            }
        }
    }

    deletingEntry?.let { target ->
        AlertDialog(
            onDismissRequest = { deletingEntry = null },
            shape = RoundedCornerShape(20.dp),
            title = { Text("确认删除单词", fontWeight = FontWeight.SemiBold) },
            text = { Text("确定要从单词本删除「${target.word}」吗？该词的复习记录也将不再保留。") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteWordbookEntry(target.id)
                    deletingEntry = null
                }) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { deletingEntry = null }) { Text("取消") }
            }
        )
    }

    if (showCreateDialog || editingEntry != null) {
        WordbookEditDialog(
            initial = editingEntry,
            onDismiss = {
                showCreateDialog = false
                editingEntry = null
            },
            onSave = { updated ->
                viewModel.upsertWordbookEntry(updated)
                showCreateDialog = false
                editingEntry = null
            }
        )
    }
}

@Composable
private fun BookPreviewCard(
    entry: DictionaryEntryEntity,
    onSpeak: (String) -> Unit,
    onAddSingle: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = entry.word,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (entry.phonetic.isNotBlank()) {
                        Text(
                            text = "/${entry.phonetic}/",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                IconButton(onClick = { onSpeak(entry.word) }) {
                    Icon(
                        AppleIcons.SpeakerWave,
                        contentDescription = "离线发音",
                        modifier = Modifier.size(19.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                IconButton(onClick = onAddSingle) {
                    Icon(
                        AppleIcons.Plus,
                        contentDescription = "单独加入我的单词本",
                        modifier = Modifier.size(19.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (entry.translation.isNotBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(text = entry.translation, style = MaterialTheme.typography.bodyMedium)
            }
            if (entry.example.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "例：${entry.example}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (entry.exampleTranslation.isNotBlank()) {
                            Text(
                                text = entry.exampleTranslation,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    IconButton(onClick = { onSpeak(entry.example) }) {
                        Icon(
                            AppleIcons.SpeakerWave,
                            contentDescription = "朗读例句",
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WordbookItemCard(
    entry: WordbookEntryEntity,
    onSpeak: (String) -> Unit,
    onEdit: () -> Unit,
    onRequestDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = entry.word,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = buildString {
                            if (entry.phonetic.isNotBlank()) append("/${entry.phonetic}/ · ")
                            append(entry.groupName)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                IconButton(onClick = { onSpeak(entry.word) }) {
                    Icon(
                        AppleIcons.SpeakerWave,
                        contentDescription = "离线发音",
                        modifier = Modifier.size(19.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                IconButton(onClick = onEdit) {
                    Icon(
                        AppleIcons.Pencil,
                        contentDescription = "编辑",
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onRequestDelete) {
                    Icon(
                        AppleIcons.Trash,
                        contentDescription = "删除",
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.85f)
                    )
                }
            }
            if (entry.translation.isNotBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(text = entry.translation, style = MaterialTheme.typography.bodyMedium)
            }
            if (entry.exampleSentence.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "例：${entry.exampleSentence}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (entry.exampleTranslation.isNotBlank()) {
                            Text(
                                text = entry.exampleTranslation,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    IconButton(onClick = { onSpeak(entry.exampleSentence) }) {
                        Icon(
                            AppleIcons.SpeakerWave,
                            contentDescription = "朗读例句",
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
            if (entry.note.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "笔记：${entry.note}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "熟练度 Lv.${entry.masteryLevel}/5",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.width(10.dp))
                LinearProgressIndicator(
                    progress = { (entry.masteryLevel / 5f).coerceIn(0f, 1f) },
                    modifier = Modifier
                        .weight(1f)
                        .height(5.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    color = MaterialTheme.colorScheme.tertiary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
            }
        }
    }
}

@Composable
private fun WordbookEditDialog(
    initial: WordbookEntryEntity?,
    onDismiss: () -> Unit,
    onSave: (WordbookEntryEntity) -> Unit
) {
    var word by remember { mutableStateOf(initial?.word.orEmpty()) }
    var phonetic by remember { mutableStateOf(initial?.phonetic.orEmpty()) }
    var translation by remember { mutableStateOf(initial?.translation.orEmpty()) }
    var example by remember { mutableStateOf(initial?.exampleSentence.orEmpty()) }
    var group by remember { mutableStateOf(initial?.groupName ?: "我的单词本") }
    var note by remember { mutableStateOf(initial?.note.orEmpty()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(20.dp),
        title = { Text(if (initial == null) "添加单词" else "编辑单词", fontWeight = FontWeight.SemiBold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = word, onValueChange = { word = it }, label = { Text("单词") }, singleLine = true, shape = RoundedCornerShape(12.dp))
                OutlinedTextField(value = phonetic, onValueChange = { phonetic = it }, label = { Text("音标") }, singleLine = true, shape = RoundedCornerShape(12.dp))
                OutlinedTextField(value = translation, onValueChange = { translation = it }, label = { Text("释义") }, maxLines = 3, shape = RoundedCornerShape(12.dp))
                OutlinedTextField(value = example, onValueChange = { example = it }, label = { Text("英文例句") }, maxLines = 2, shape = RoundedCornerShape(12.dp))
                OutlinedTextField(value = group, onValueChange = { group = it }, label = { Text("分组") }, singleLine = true, shape = RoundedCornerShape(12.dp))
                OutlinedTextField(value = note, onValueChange = { note = it }, label = { Text("学习笔记") }, maxLines = 2, shape = RoundedCornerShape(12.dp))
            }
        },
        confirmButton = {
            TextButton(
                enabled = word.isNotBlank(),
                onClick = {
                    val trimmedWord = word.trim()
                    val base = initial ?: WordbookEntryEntity(
                        word = trimmedWord,
                        wordLower = trimmedWord.lowercase(Locale.ROOT)
                    )
                    onSave(
                        base.copy(
                            word = trimmedWord,
                            wordLower = trimmedWord.lowercase(Locale.ROOT),
                            phonetic = phonetic.trim(),
                            translation = translation.trim(),
                            exampleSentence = example.trim(),
                            groupName = group.trim().ifEmpty { "我的单词本" },
                            note = note.trim()
                        )
                    )
                }
            ) { Text("保存") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}
