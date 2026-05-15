package com.livespeaker.app.ui.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.livespeaker.app.SettingsManager
import com.livespeaker.app.stt.SttConfig
import com.livespeaker.app.stt.SttSettings
import kotlinx.coroutines.launch

private data class DurationOption(val minutes: Int, val label: String)

private val options = listOf(
    DurationOption(1, "1 分钟"),
    DurationOption(2, "2 分钟"),
    DurationOption(5, "5 分钟"),
    DurationOption(10, "10 分钟"),
)

/**
 * 设置 BottomSheet：切片时间 + STT 配置。
 *
 * @param sttConfig STT 配置管理器
 * @param onDismiss 关闭回调
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsSheet(
    sttConfig: SttConfig,
    onDismiss: () -> Unit
) {
    var selected by remember {
        mutableIntStateOf(SettingsManager.segmentDurationMinutes)
    }
    val sttSettings by sttConfig.settings.collectAsState(initial = SttConfig.DEFAULTS)
    val scope = rememberCoroutineScope()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ══════════════════════════════════════
            // 录音设置
            // ══════════════════════════════════════
            Text(
                text = "录音设置",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            // 切片时间标签
            Text(
                text = "切片时间",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // 选项列表
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            ) {
                Column {
                    options.forEachIndexed { index, option ->
                        val isSelected = selected == option.minutes

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selected = option.minutes
                                    SettingsManager.segmentDurationMinutes = option.minutes
                                }
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = isSelected,
                                onClick = {
                                    selected = option.minutes
                                    SettingsManager.segmentDurationMinutes = option.minutes
                                },
                                colors = RadioButtonDefaults.colors(
                                    selectedColor = MaterialTheme.colorScheme.primary
                                )
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(
                                text = option.label,
                                style = MaterialTheme.typography.bodyLarge,
                                color = if (isSelected)
                                    MaterialTheme.colorScheme.onSurface
                                else
                                    MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.weight(1f))
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        if (index < options.lastIndex) {
                            HorizontalDivider(
                                modifier = Modifier.padding(horizontal = 16.dp),
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)
                            )
                        }
                    }
                }
            }

            // 提示文字
            Text(
                text = "更改后将在下次开始录音时生效",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f))

            // ══════════════════════════════════════
            // 语音转文字设置
            // ══════════════════════════════════════
            Text(
                text = "语音转文字",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            // STT 启用开关
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "启用语音转文字",
                        style = MaterialTheme.typography.titleSmall
                    )
                    Text(
                        "录音片段自动转录为文字",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = sttSettings.enabled,
                    onCheckedChange = { scope.launch { sttConfig.setEnabled(it) } }
                )
            }

            if (sttSettings.enabled) {
                // 提供商
                OutlinedTextField(
                    value = sttSettings.provider,
                    onValueChange = { scope.launch { sttConfig.updateProvider(it) } },
                    label = { Text("提供商") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                // API 地址
                OutlinedTextField(
                    value = sttSettings.baseUrl,
                    onValueChange = { scope.launch { sttConfig.updateBaseUrl(it) } },
                    label = { Text("API 地址") },
                    placeholder = { Text("https://api.siliconflow.cn") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri)
                )

                // API Key
                OutlinedTextField(
                    value = sttSettings.apiKey,
                    onValueChange = { scope.launch { sttConfig.updateApiKey(it) } },
                    label = { Text("API Key") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
                )

                // 模型
                OutlinedTextField(
                    value = sttSettings.model,
                    onValueChange = { scope.launch { sttConfig.updateModel(it) } },
                    label = { Text("模型") },
                    placeholder = { Text("FunAudioLLM/SenseVoiceSmall") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }

            // 使用提示
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
