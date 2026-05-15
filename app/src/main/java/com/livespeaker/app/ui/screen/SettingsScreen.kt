package com.livespeaker.app.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.livespeaker.app.stt.SttConfig
import com.livespeaker.app.stt.SttSettings
import kotlinx.coroutines.launch

/**
 * STT 设置页面。
 *
 * 配置项:
 * - 启用开关
 * - 提供商 (自定义标签)
 * - API 地址 (base URL)
 * - API Key (密码框)
 * - 模型名
 *
 * 支持所有 OpenAI 兼容的 STT API（SiliconFlow 等）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    sttConfig: SttConfig,
    onBack: () -> Unit
) {
    val settings by sttConfig.settings.collectAsState(initial = SttConfig.DEFAULTS)
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("STT 设置") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ── STT 启用开关 ──
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("语音转文字", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "录音片段自动转录为文字",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = settings.enabled,
                    onCheckedChange = { scope.launch { sttConfig.setEnabled(it) } }
                )
            }

            HorizontalDivider()

            if (settings.enabled) {
                // ── 提供商 ──
                OutlinedTextField(
                    value = settings.provider,
                    onValueChange = { scope.launch { sttConfig.updateProvider(it) } },
                    label = { Text("提供商") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    enabled = settings.enabled
                )

                // ── API 地址 ──
                OutlinedTextField(
                    value = settings.baseUrl,
                    onValueChange = { scope.launch { sttConfig.updateBaseUrl(it) } },
                    label = { Text("API 地址") },
                    placeholder = { Text("https://api.siliconflow.cn") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    enabled = settings.enabled
                )

                // ── API Key ──
                OutlinedTextField(
                    value = settings.apiKey,
                    onValueChange = { scope.launch { sttConfig.updateApiKey(it) } },
                    label = { Text("API Key") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    enabled = settings.enabled
                )

                // ── 模型 ──
                OutlinedTextField(
                    value = settings.model,
                    onValueChange = { scope.launch { sttConfig.updateModel(it) } },
                    label = { Text("模型") },
                    placeholder = { Text("FunAudioLLM/SenseVoiceSmall") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    enabled = settings.enabled
                )
            }

            Spacer(Modifier.height(8.dp))

            // ── 提示信息 ──
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "💡 使用提示",
                        style = MaterialTheme.typography.titleSmall
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "• 支持 OpenAI 兼容的 STT API\n" +
                        "• 默认使用 SiliconFlow (SenseVoice)\n" +
                        "• 录音保存为 M4A/AAC 格式\n" +
                        "• 转录在录音停止后自动触发\n" +
                        "• API Key 仅存储在本地",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = MaterialTheme.typography.bodySmall.lineHeight * 1.5
                    )
                }
            }
        }
    }
}
