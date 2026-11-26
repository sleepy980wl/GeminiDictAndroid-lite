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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.geminidict.app.data.security.SecurePrefsManager
import com.geminidict.app.ui.viewmodel.MainViewModel

@Composable
fun SettingsScreen(viewModel: MainViewModel) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val ttsState by viewModel.ttsManager.state.collectAsStateWithLifecycle()

    // Ensure TTS status is probed when user opens Settings
    LaunchedEffect(Unit) {
        viewModel.ttsManager.ensureInitialized()
    }

    var apiKey by remember(uiState.apiKey) { mutableStateOf(uiState.apiKey) }
    var model by remember(uiState.geminiModel) { mutableStateOf(uiState.geminiModel) }
    var baseUrl by remember(uiState.geminiBaseUrl) { mutableStateOf(uiState.geminiBaseUrl) }
    var githubMirror by remember(uiState.githubMirrorPrefix) { mutableStateOf(uiState.githubMirrorPrefix) }
    var accent by remember(uiState.ttsAccent) { mutableStateOf(uiState.ttsAccent) }
    var speechRate by remember(uiState.ttsSpeechRate) { mutableFloatStateOf(uiState.ttsSpeechRate) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "设置",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )

        if (!uiState.securityWarning.isNullOrBlank()) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
            ) {
                Text(
                    text = uiState.securityWarning ?: "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.padding(12.dp)
                )
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Gemini API 配置（Keystore 加密存储）",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    label = { Text("Gemini API Key") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = model,
                    onValueChange = { model = it },
                    label = { Text("模型名称（默认 ${SecurePrefsManager.DEFAULT_GEMINI_MODEL}）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = baseUrl,
                    onValueChange = { baseUrl = it },
                    label = { Text("API Base URL（可填自定义反代地址）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(
                        enabled = !uiState.isTestingConnection,
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
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.error
                        }
                    )
                }
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "离线词典下载网络设置",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                OutlinedTextField(
                    value = githubMirror,
                    onValueChange = { githubMirror = it },
                    label = { Text("可选：GitHub Raw 镜像加速前缀") },
                    placeholder = { Text("例如 https://ghfast.top/ （留空则直连 GitHub）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "英文离线朗读 (TTS)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = when {
                        ttsState.fallbackNotice != null -> ttsState.fallbackNotice!!
                        ttsState.hasOfflineEnglishVoice -> "已检测到本地离线英文语音包（当前实际口音：${ttsState.activeAccent}），可完全离线朗读单词与例句。"
                        !ttsState.isInitialized -> "正在检测系统离线语音包..."
                        else -> ttsState.errorMessage ?: "当前未检测到本地离线英文语音包，建议在系统「设置 -> 文字转语音」下载英文离线语音。"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = accent == SecurePrefsManager.ACCENT_US,
                        onClick = { accent = SecurePrefsManager.ACCENT_US },
                        label = { Text("美式英语 (en-US)") }
                    )
                    FilterChip(
                        selected = accent == SecurePrefsManager.ACCENT_UK,
                        onClick = { accent = SecurePrefsManager.ACCENT_UK },
                        label = { Text("英式英语 (en-GB)") }
                    )
                }
                Text(
                    text = String.format("朗读语速：%.2fx", speechRate),
                    style = MaterialTheme.typography.bodySmall
                )
                Slider(
                    value = speechRate,
                    onValueChange = { speechRate = it },
                    valueRange = 0.5f..1.5f
                )
                // Preview uses temporary override parameters without mutating saved preferences
                OutlinedButton(
                    onClick = {
                        viewModel.speakEnglish(
                            text = "Spaced repetition builds lasting vocabulary.",
                            accentOverride = accent,
                            rateOverride = speechRate
                        )
                    }
                ) {
                    Text("试听离线例句朗读")
                }
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
                    ttsRate = speechRate
                )
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("保存全部设置")
        }

        Spacer(Modifier.height(12.dp))
    }
}
