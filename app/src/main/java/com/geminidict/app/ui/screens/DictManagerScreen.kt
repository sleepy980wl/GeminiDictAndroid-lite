package com.geminidict.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.geminidict.app.data.dict.DictionaryCatalogItem
import com.geminidict.app.data.dict.DownloadProgress
import com.geminidict.app.data.local.entity.DownloadedDictEntity
import com.geminidict.app.ui.viewmodel.MainViewModel

@Composable
fun DictManagerScreen(viewModel: MainViewModel) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val installedList by viewModel.downloadedDicts.collectAsStateWithLifecycle()
    val installedMap = installedList.associateBy { it.id }

    var confirmLargeDownload by remember { mutableStateOf<DictionaryCatalogItem?>(null) }
    var confirmUninstall by remember { mutableStateOf<DictionaryCatalogItem?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "离线词典下载中心",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "收录公开开源中英词典与中华新华字典，支持完整性校验、事务原子替换与随时取消。如 GitHub 下载较慢，可在「设置」中填写镜像加速前缀。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(12.dp))

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(MainViewModel.catalogItems, key = { it.id }) { item ->
                DictionaryCatalogCard(
                    item = item,
                    installed = installedMap[item.id],
                    progress = uiState.activeDownloads[item.id],
                    onDownloadClick = {
                        if (item.approxSizeMb >= 15) {
                            confirmLargeDownload = item
                        } else {
                            viewModel.downloadDictionary(item)
                        }
                    },
                    onCancel = { viewModel.cancelDownload(item.id) },
                    onRequestUninstall = { confirmUninstall = item }
                )
            }
        }
    }

    confirmLargeDownload?.let { item ->
        AlertDialog(
            onDismissRequest = { confirmLargeDownload = null },
            title = { Text("确认下载大体积词典") },
            text = { Text("「${item.name}」体积约 ${item.approxSize}，建议在 Wi-Fi 环境下下载。确定开始下载并写入本地离线库吗？") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.downloadDictionary(item)
                    confirmLargeDownload = null
                }) { Text("开始下载") }
            },
            dismissButton = {
                TextButton(onClick = { confirmLargeDownload = null }) { Text("取消") }
            }
        )
    }

    confirmUninstall?.let { item ->
        AlertDialog(
            onDismissRequest = { confirmUninstall = null },
            title = { Text("确认卸载词典") },
            text = { Text("确定要卸载「${item.name}」并清理其本地离线词条吗？你的单词本收藏不会受影响。") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.uninstallDictionary(item.id, item.name)
                    confirmUninstall = null
                }) { Text("卸载") }
            },
            dismissButton = {
                TextButton(onClick = { confirmUninstall = null }) { Text("取消") }
            }
        )
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

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${item.approxEntries} · ${item.approxSize} · ${item.license}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                AssistChip(onClick = {}, label = { Text(item.categoryLabel) })
            }

            Spacer(Modifier.height(6.dp))
            Text(text = item.description, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(10.dp))

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
                    if (progress.phase == "downloading") {
                        OutlinedButton(onClick = onCancel) {
                            Icon(Icons.Default.Close, contentDescription = "取消下载")
                            Spacer(Modifier.width(4.dp))
                            Text("取消")
                        }
                    }
                }
                Spacer(Modifier.height(4.dp))
                if (progress.progress >= 0f) {
                    LinearProgressIndicator(
                        progress = { progress.progress },
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
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
                                Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = "已安装 (${installed.entryCount} 条)",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Row {
                            Button(onClick = onDownloadClick) { Text("重新下载") }
                            IconButton(onClick = onRequestUninstall) {
                                Icon(Icons.Default.Delete, contentDescription = "卸载词典")
                            }
                        }
                    } else {
                        Text(
                            text = if (progress?.phase == "error") progress.message else "未安装",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (progress?.phase == "error") MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f)
                        )
                        Button(onClick = onDownloadClick) {
                            Icon(Icons.Default.Add, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text("点击下载")
                        }
                    }
                }
            }
        }
    }
}
