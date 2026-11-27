package com.geminidict.app.ui.screens

import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import com.geminidict.app.data.dict.DictionaryCatalogItem
import com.geminidict.app.data.dict.DownloadProgress
import com.geminidict.app.data.local.entity.DownloadedDictEntity
import com.geminidict.app.data.tts.NeuralVoiceModelItem
import com.geminidict.app.ui.icons.AppleIcons
import com.geminidict.app.ui.viewmodel.MainViewModel

@Composable
fun DictManagerScreen(viewModel: MainViewModel) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val installedList by viewModel.downloadedDicts.collectAsStateWithLifecycle()
    val installedMap = installedList.associateBy { it.id }

    var subTab by remember { mutableIntStateOf(0) } // 0=离线词典, 1=神经语音包
    var confirmLargeDict by remember { mutableStateOf<DictionaryCatalogItem?>(null) }
    var confirmUninstallDict by remember { mutableStateOf<DictionaryCatalogItem?>(null) }
    var confirmLargeVoice by remember { mutableStateOf<NeuralVoiceModelItem?>(null) }
    var confirmUninstallVoice by remember { mutableStateOf<NeuralVoiceModelItem?>(null) }

    val localDictLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            var displayName = "custom_dict.csv"
            context.contentResolver.query(uri, null, null, null, null)?.use { c ->
                val nameIndex = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (c.moveToFirst() && nameIndex >= 0) {
                    displayName = c.getString(nameIndex) ?: displayName
                }
            }
            viewModel.importUserLocalDictionary(uri, displayName)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Text(
            text = "词库 & 语音中心",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "17 套开源离线词典与 5 款真人感神经语音包，全部支持断点续传",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(12.dp))

        SegmentedControlBar(
            options = listOf(
                "离线词典 (${MainViewModel.catalogItems.size})",
                "真人感神经语音 (${uiState.installedNeuralVoiceIds.size}/${MainViewModel.neuralVoiceItems.size})"
            ),
            selectedIndex = subTab,
            onSelect = { subTab = it }
        )

        Spacer(Modifier.height(10.dp))

        if (subTab == 0) {
            FilledTonalButton(
                onClick = { localDictLauncher.launch(arrayOf("*/*")) },
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(vertical = 10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(AppleIcons.SquareArrowDown, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("导入本地自有词典（CSV / TSV / JSON / GZ / ZIP）", fontWeight = FontWeight.Medium)
            }
            Spacer(Modifier.height(10.dp))

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(MainViewModel.catalogItems, key = { it.id }) { item ->
                    DictionaryCatalogCard(
                        item = item,
                        installed = installedMap[item.id],
                        progress = uiState.activeDownloads[item.id],
                        onDownloadClick = {
                            if (item.approxSizeMb >= 15) {
                                confirmLargeDict = item
                            } else {
                                viewModel.downloadDictionary(item)
                            }
                        },
                        onCancel = { viewModel.cancelDownload(item.id) },
                        onRequestUninstall = { confirmUninstallDict = item }
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(MainViewModel.neuralVoiceItems, key = { it.id }) { voice ->
                    NeuralVoiceCard(
                        item = voice,
                        isInstalled = voice.id in uiState.installedNeuralVoiceIds,
                        progress = uiState.activeDownloads[voice.id],
                        onDownloadClick = {
                            if (voice.approxSizeMb >= 15) {
                                confirmLargeVoice = voice
                            } else {
                                viewModel.downloadNeuralVoice(voice)
                            }
                        },
                        onCancel = { viewModel.cancelDownload(voice.id) },
                        onTestSpeak = {
                            viewModel.speakEnglish(
                                text = "Consistent daily review builds lasting fluency.",
                                accentOverride = voice.accent
                            )
                        },
                        onRequestUninstall = { confirmUninstallVoice = voice }
                    )
                }
            }
        }
    }

    confirmLargeDict?.let { item ->
        AlertDialog(
            onDismissRequest = { confirmLargeDict = null },
            shape = RoundedCornerShape(20.dp),
            title = { Text("确认下载大体积词典", fontWeight = FontWeight.SemiBold) },
            text = { Text("「${item.name}」体积约 ${item.approxSize}，支持断点续传，建议在 Wi-Fi 环境下下载。确定开始吗？") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.downloadDictionary(item)
                    confirmLargeDict = null
                }) { Text("开始下载") }
            },
            dismissButton = {
                TextButton(onClick = { confirmLargeDict = null }) { Text("取消") }
            }
        )
    }

    confirmUninstallDict?.let { item ->
        AlertDialog(
            onDismissRequest = { confirmUninstallDict = null },
            shape = RoundedCornerShape(20.dp),
            title = { Text("确认卸载词典", fontWeight = FontWeight.SemiBold) },
            text = { Text("确定要卸载「${item.name}」并清理其本地离线词条吗？你的单词本收藏不会受影响。") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.uninstallDictionary(item.id, item.name)
                    confirmUninstallDict = null
                }) { Text("卸载", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { confirmUninstallDict = null }) { Text("取消") }
            }
        )
    }

    confirmLargeVoice?.let { voice ->
        AlertDialog(
            onDismissRequest = { confirmLargeVoice = null },
            shape = RoundedCornerShape(20.dp),
            title = { Text("确认下载离线神经语音模型", fontWeight = FontWeight.SemiBold) },
            text = { Text("「${voice.name}」体积约 ${voice.approxSize}（Sherpa-ONNX Piper VITS 纯离线模型），支持断点续传。建议在 Wi-Fi 下下载，确定开始吗？") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.downloadNeuralVoice(voice)
                    confirmLargeVoice = null
                }) { Text("开始下载") }
            },
            dismissButton = {
                TextButton(onClick = { confirmLargeVoice = null }) { Text("取消") }
            }
        )
    }

    confirmUninstallVoice?.let { voice ->
        AlertDialog(
            onDismissRequest = { confirmUninstallVoice = null },
            shape = RoundedCornerShape(20.dp),
            title = { Text("确认删除语音模型", fontWeight = FontWeight.SemiBold) },
            text = { Text("确定要删除「${voice.name}」释放本地存储空间吗？删除后可随时重新下载，朗读将自动回退系统离线 TTS。") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.uninstallNeuralVoice(voice)
                    confirmUninstallVoice = null
                }) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { confirmUninstallVoice = null }) { Text("取消") }
            }
        )
    }
}

@Composable
private fun NeuralVoiceCard(
    item: NeuralVoiceModelItem,
    isInstalled: Boolean,
    progress: DownloadProgress?,
    onDownloadClick: () -> Unit,
    onCancel: () -> Unit,
    onTestSpeak: () -> Unit,
    onRequestUninstall: () -> Unit
) {
    val isBusy = progress?.phase == "downloading" || progress?.phase == "importing"

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
                    Text(
                        text = item.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "${item.accent} · ${item.approxSize} · Sherpa-ONNX Piper VITS",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Text(
                        text = if (item.accent == "en-GB") "英音" else "美音",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(Modifier.height(6.dp))
            Text(text = item.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(12.dp))

            if (isBusy && progress != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = progress.message,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedButton(
                        onClick = onCancel,
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Icon(AppleIcons.Xmark, contentDescription = "取消下载", modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("取消")
                    }
                }
                Spacer(Modifier.height(6.dp))
                if (progress.progress >= 0f) {
                    LinearProgressIndicator(
                        progress = { progress.progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(5.dp)
                            .clip(RoundedCornerShape(4.dp))
                    )
                } else {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(5.dp)
                            .clip(RoundedCornerShape(4.dp))
                    )
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    if (isInstalled) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                AppleIcons.CheckmarkCircle,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.tertiary
                            )
                            Spacer(Modifier.width(6.dp))
                            Text("已安装", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.tertiary)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            FilledTonalButton(
                                onClick = onTestSpeak,
                                shape = RoundedCornerShape(12.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Icon(AppleIcons.SpeakerWave, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(5.dp))
                                Text("试听")
                            }
                            IconButton(onClick = onRequestUninstall) {
                                Icon(
                                    AppleIcons.Trash,
                                    contentDescription = "删除语音模型",
                                    modifier = Modifier.size(18.dp),
                                    tint = MaterialTheme.colorScheme.error.copy(alpha = 0.85f)
                                )
                            }
                        }
                    } else {
                        Text(
                            text = if (progress?.phase == "error" || progress?.phase == "cancelled") progress.message else "未安装",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (progress?.phase == "error") MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f)
                        )
                        Button(
                            onClick = onDownloadClick,
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                        ) {
                            Icon(AppleIcons.CloudDownload, contentDescription = null, modifier = Modifier.size(17.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("下载")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DictionaryCatalogCard(
    item: DictionaryCatalogItem,
    installed: DownloadedDictEntity?,
    progress: DownloadProgress?,
    onDownloadClick: () -> Unit,
    onCancel: () -> Unit,
    onRequestUninstall: () -> Unit
) {
    val isBusy = progress?.phase == "downloading" || progress?.phase == "importing"

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
                    Text(
                        text = item.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "${item.approxEntries} · ${item.approxSize} · ${item.license}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Text(
                        text = item.categoryLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(Modifier.height(6.dp))
            Text(text = item.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(12.dp))

            if (isBusy && progress != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = progress.message,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedButton(
                        onClick = onCancel,
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Icon(AppleIcons.Xmark, contentDescription = "取消下载", modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("取消")
                    }
                }
                Spacer(Modifier.height(6.dp))
                if (progress.progress >= 0f) {
                    LinearProgressIndicator(
                        progress = { progress.progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(5.dp)
                            .clip(RoundedCornerShape(4.dp))
                    )
                } else {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(5.dp)
                            .clip(RoundedCornerShape(4.dp))
                    )
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    if (installed != null) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                AppleIcons.CheckmarkCircle,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.tertiary
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = "已安装 (${installed.entryCount} 条)",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.tertiary
                            )
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            FilledTonalButton(
                                onClick = onDownloadClick,
                                shape = RoundedCornerShape(12.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                            ) { Text("更新") }
                            IconButton(onClick = onRequestUninstall) {
                                Icon(
                                    AppleIcons.Trash,
                                    contentDescription = "卸载词典",
                                    modifier = Modifier.size(18.dp),
                                    tint = MaterialTheme.colorScheme.error.copy(alpha = 0.85f)
                                )
                            }
                        }
                    } else {
                        Text(
                            text = if (progress?.phase == "error" || progress?.phase == "cancelled") progress.message else "未安装",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (progress?.phase == "error") MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f)
                        )
                        Button(
                            onClick = onDownloadClick,
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                        ) {
                            Icon(AppleIcons.CloudDownload, contentDescription = null, modifier = Modifier.size(17.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("下载")
                        }
                    }
                }
            }
        }
    }
}
