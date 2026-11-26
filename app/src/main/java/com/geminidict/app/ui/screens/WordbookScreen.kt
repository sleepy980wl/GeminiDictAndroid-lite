package com.geminidict.app.ui.screens

import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.geminidict.app.data.local.entity.WordbookEntryEntity
import com.geminidict.app.ui.viewmodel.MainViewModel
import com.geminidict.app.ui.viewmodel.WordbookSort
import java.util.Locale

@Composable
fun WordbookScreen(viewModel: MainViewModel) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val allEntries by viewModel.wordbookEntries.collectAsStateWithLifecycle()
    val groups by viewModel.wordbookGroups.collectAsStateWithLifecycle()

    var editingEntry by remember { mutableStateOf<WordbookEntryEntity?>(null) }
    var deletingEntry by remember { mutableStateOf<WordbookEntryEntity?>(null) }
    var showCreateDialog by remember { mutableStateOf(false) }
    var showAdbGuide by remember { mutableStateOf(false) }

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

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = { showCreateDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = "手动添加单词")
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            Text(
                text = "我的单词本 (${filteredEntries.size}/${allEntries.size})",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "兼容有道单词本分组、熟练度与 XML 双向导入导出",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = { importLauncher.launch(arrayOf("*/*")) },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("导入有道")
                }
                OutlinedButton(
                    onClick = { showAdbGuide = true },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Build, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("ADB导入")
                }
                OutlinedButton(
                    onClick = { exportLauncher.launch("my_wordbook_youdao.xml") },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("导出XML")
                }
            }

            Spacer(Modifier.height(8.dp))

            OutlinedTextField(
                value = uiState.wordbookQuery,
                onValueChange = { viewModel.updateWordbookFilter(query = it) },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("搜索单词、释义或笔记...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true
            )

            Spacer(Modifier.height(8.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                val allGroupNames = listOf("全部") + groups
                allGroupNames.forEach { group ->
                    FilterChip(
                        selected = uiState.selectedGroup == group,
                        onClick = { viewModel.updateWordbookFilter(group = group) },
                        label = { Text(group) }
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
                        label = { Text(label) }
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            if (filteredEntries.isEmpty()) {
                Text(
                    text = "单词本暂无匹配词汇。可在「查词」页点击收藏，或点击上方「导入有道」/「ADB导入」导入你的有道单词本。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 16.dp)
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
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
            }
        }
    }

    deletingEntry?.let { target ->
        AlertDialog(
            onDismissRequest = { deletingEntry = null },
            title = { Text("确认删除单词") },
            text = { Text("确定要从单词本删除「${target.word}」吗？该词的复习记录也将不再保留。") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteWordbookEntry(target.id)
                    deletingEntry = null
                }) { Text("删除") }
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

    if (showAdbGuide) {
        AlertDialog(
            onDismissRequest = { showAdbGuide = false },
            title = { Text("无 Root 手机 ADB 导入有道单词本") },
            text = {
                Text(
                    text = "未 Root 手机禁止第三方 App 直接读取有道私有目录。你可以用电脑执行源码 tools/ 目录下的 ADB 辅助脚本一键推送并自动导入：\n\n" +
                        "1. 在电脑版网易有道词典导出「我的单词本」为 XML / TXT / CSV（或准备提取的 .db 文件，若含 -wal/-shm 请一并复制）；\n" +
                        "2. 手机开启 USB 调试并连接电脑；\n" +
                        "3. 运行命令：\n" +
                        "python3 tools/adb_import_youdao.py --file youdao.xml\n\n" +
                        "也可运行 --scan-phone 自动扫描手机公共目录（/sdcard/Youdao、/sdcard/Download）中已导出的文件并自动唤起本 App 完成导入。"
                )
            },
            confirmButton = {
                TextButton(onClick = { showAdbGuide = false }) { Text("知道了") }
            }
        )
    }
}

@Composable
private fun WordbookItemCard(
    entry: WordbookEntryEntity,
    onSpeak: (String) -> Unit,
    onEdit: () -> Unit,
    onRequestDelete: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = entry.word,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    if (entry.phonetic.isNotBlank()) {
                        Text(
                            text = "/${entry.phonetic}/ · ${entry.groupName}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                IconButton(onClick = { onSpeak(entry.word) }) {
                    Icon(Icons.Default.PlayArrow, contentDescription = "离线发音")
                }
                IconButton(onClick = onEdit) {
                    Icon(Icons.Default.Edit, contentDescription = "编辑")
                }
                IconButton(onClick = onRequestDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "删除")
                }
            }
            if (entry.translation.isNotBlank()) {
                Text(text = entry.translation, style = MaterialTheme.typography.bodyMedium)
            }
            if (entry.exampleSentence.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "例：${entry.exampleSentence}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = { onSpeak(entry.exampleSentence) }) {
                        Icon(Icons.Default.PlayArrow, contentDescription = "朗读例句", modifier = Modifier.size(16.dp))
                    }
                }
            }
            if (entry.note.isNotBlank()) {
                Text(
                    text = "笔记：${entry.note}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "熟练度 Lv.${entry.masteryLevel}/5",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.width(8.dp))
                LinearProgressIndicator(
                    progress = { (entry.masteryLevel / 5f).coerceIn(0f, 1f) },
                    modifier = Modifier.weight(1f)
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
        title = { Text(if (initial == null) "添加单词" else "编辑单词") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = word, onValueChange = { word = it }, label = { Text("单词") }, singleLine = true)
                OutlinedTextField(value = phonetic, onValueChange = { phonetic = it }, label = { Text("音标") }, singleLine = true)
                OutlinedTextField(value = translation, onValueChange = { translation = it }, label = { Text("释义") }, maxLines = 3)
                OutlinedTextField(value = example, onValueChange = { example = it }, label = { Text("英文例句") }, maxLines = 2)
                OutlinedTextField(value = group, onValueChange = { group = it }, label = { Text("分组") }, singleLine = true)
                OutlinedTextField(value = note, onValueChange = { note = it }, label = { Text("学习笔记") }, maxLines = 2)
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
