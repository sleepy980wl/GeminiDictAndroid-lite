package com.geminidict.app.ui.screens

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.geminidict.app.data.security.SecurePrefsManager
import com.geminidict.app.ui.icons.AppleIcons
import com.geminidict.app.ui.viewmodel.MainViewModel

@Composable
fun SettingsScreen(viewModel: MainViewModel) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val ttsState by viewModel.ttsManager.state.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.ttsManager.ensureInitialized()
    }

    var apiKey by remember(uiState.apiKey) { mutableStateOf(uiState.apiKey) }
    var model by remember(uiState.geminiModel) { mutableStateOf(uiState.geminiModel) }
    var baseUrl by remember(uiState.geminiBaseUrl) { mutableStateOf(uiState.geminiBaseUrl) }
    var githubMirror by remember(uiState.githubMirrorPrefix) { mutableStateOf(uiState.githubMirrorPrefix) }
    var accent by remember(uiState.ttsAccent) { mutableStateOf(uiState.ttsAccent) }
    var speechRate by remember(uiState.ttsSpeechRate) { mutableFloatStateOf(uiState.ttsSpeechRate) }
    var preferNeural by remember(uiState.preferNeuralVoice) { mutableStateOf(uiState.preferNeuralVoice) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Column {
            Text(
                text = "设置",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "离线朗读引擎、词库下载加速与 Gemini AI 配置",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (!uiState.securityWarning.isNullOrBlank()) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Text(
                    text = uiState.securityWarning ?: "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.padding(14.dp)
                )
            }
        }

        SettingsGroupCard(
            icon = AppleIcons.Sparkles,
            title = "Gemini AI 配置（Keystore 加密存储，选填）"
        ) {
            OutlinedTextField(
                value = apiKey,
                onValueChange = { apiKey = it },
                label = { Text("Gemini API Key") },
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                ),
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = model,
                onValueChange = { model = it },
                label = { Text("模型名称（默认 ${SecurePrefsManager.DEFAULT_GEMINI_MODEL}）") },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                ),
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = baseUrl,
                onValueChange = { baseUrl = it },
                label = { Text("API Base URL（可填自定义反代地址）") },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                ),
                modifier = Modifier.fillMaxWidth()
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                FilledTonalButton(
                    enabled = !uiState.isTestingConnection,
                    shape = RoundedCornerShape(12.dp),
                    onClick = { viewModel.testGeminiConnection(apiKey, model, baseUrl) }
                ) {
                    if (uiState.isTestingConnection) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(6.dp))
                        Text("测试中...")
                    } else {
                        Text("测试 Gemini 连接")
                    }
                }
            }
            if (!uiState.connectionTestMessage.isNullOrBlank()) {
                Text(
                    text = uiState.connectionTestMessage ?: "",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (uiState.connectionTestMessage?.startsWith("连接成功") == true) {
                        MaterialTheme.colorScheme.tertiary
                    } else {
                        MaterialTheme.colorScheme.error
                    }
                )
            }
        }

        SettingsGroupCard(
            icon = AppleIcons.CloudDownload,
            title = "离线词库与语音下载加速"
        ) {
            OutlinedTextField(
                value = githubMirror,
                onValueChange = { githubMirror = it },
                label = { Text("可选：GitHub 镜像加速前缀") },
                placeholder = { Text("例如 https://ghfast.top/ （留空则直连 GitHub）") },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                ),
                modifier = Modifier.fillMaxWidth()
            )
        }

        SettingsGroupCard(
            icon = AppleIcons.SpeakerWave,
            title = "英文离线朗读（神经语音 + 系统 TTS）"
        ) {
            Text(
                text = "当前引擎：${ttsState.activeVoiceName}（已装神经语音包：${uiState.installedNeuralVoiceIds.size} 个）",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "优先使用离线神经语音（真人感）",
                    style = MaterialTheme.typography.bodyMedium
                )
                Switch(
                    checked = preferNeural,
                    onCheckedChange = { preferNeural = it }
                )
            }
            Text(
                text = when {
                    uiState.installedNeuralVoiceIds.isEmpty() ->
                        "尚未下载离线神经语音模型。可前往「词库 -> 真人感神经语音」下载英式女音（Cori/Alba）、英式男音（Alan）或美式女音（Lessac/Amy），当前使用系统离线 TTS。"
                    ttsState.fallbackNotice != null -> ttsState.fallbackNotice!!
                    else -> "已就绪：无需联网即可用神经语音朗读任意英文单词与长例句。"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = accent == SecurePrefsManager.ACCENT_US,
                    onClick = { accent = SecurePrefsManager.ACCENT_US },
                    shape = RoundedCornerShape(20.dp),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    label = { Text("美式英语 (en-US)") }
                )
                FilterChip(
                    selected = accent == SecurePrefsManager.ACCENT_UK,
                    onClick = { accent = SecurePrefsManager.ACCENT_UK },
                    shape = RoundedCornerShape(20.dp),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    label = { Text("英式英语 (en-GB)") }
                )
            }
            Text(
                text = String.format("朗读语速：%.2fx", speechRate),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Slider(
                value = speechRate,
                onValueChange = { speechRate = it },
                valueRange = 0.5f..1.5f
            )
            FilledTonalButton(
                shape = RoundedCornerShape(12.dp),
                onClick = {
                    viewModel.speakEnglish(
                        text = "Spaced repetition builds lasting vocabulary.",
                        accentOverride = accent,
                        rateOverride = speechRate
                    )
                }
            ) {
                Icon(AppleIcons.SpeakerWave, contentDescription = null, modifier = Modifier.size(17.dp))
                Spacer(Modifier.width(6.dp))
                Text("试听离线例句朗读")
            }
        }

        Button(
            onClick = {
                viewModel.saveSettings(
                    apiKey = apiKey,
                    model = model,
                    baseUrl = baseUrl,
                    githubMirror = githubMirror,
                    ttsAccent = accent,
                    ttsRate = speechRate,
                    preferNeuralVoice = preferNeural
                )
            },
            shape = RoundedCornerShape(14.dp),
            contentPadding = PaddingValues(vertical = 13.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("保存全部设置", fontWeight = FontWeight.SemiBold)
        }

        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun SettingsGroupCard(
    icon: ImageVector,
    title: String,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(19.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
            content()
        }
    }
}
