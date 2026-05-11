package com.livespeaker.app.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.livespeaker.app.LiveSpeakerApp

/**
 * 设置页面。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit
) {
    var similarityThreshold by remember {
        mutableFloatStateOf(0.65f)
    }
    var showGtcrnInfo by remember { mutableStateOf(false) }

    val modelManager = remember { LiveSpeakerApp.instance.modelManager }
    val modelStatuses = remember {
        modelManager.MODELS.map { modelManager.isModelReady(it) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("设置") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "返回")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            // 说话人相似度阈值
            Text(
                text = "说话人识别",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(8.dp))
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("相似度阈值: ${"%.2f".format(similarityThreshold)}")
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "阈值越高，相同说话人识别越严格",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Slider(
                        value = similarityThreshold,
                        onValueChange = { similarityThreshold = it },
                        valueRange = 0.4f..0.9f
                    )
                }
            }

            Spacer(Modifier.height(24.dp))

            // 降噪信息
            Text(
                text = "降噪",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(8.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                onClick = { showGtcrnInfo = !showGtcrnInfo }
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("GTCRN 降噪模型")
                        Text(
                            text = if (showGtcrnInfo) "▼" else "▶",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (showGtcrnInfo) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = """
                                模型: GTCRN (Grouped Temporal Convolutional Recurrent Network)
                                参数: 48,200 (4.8万)
                                大小: ~500KB
                                计算: 33 MMACs/秒
                                来源: 阿里巴巴/西工大联合 (Apache 2.0)
                                
                                专为超低资源设备设计，实时去除风扇、空调等稳态背景噪音。
                                在 sherpa-onnx 中作为语音增强前端运行。
                            """.trimIndent(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

            // 模型信息
            Text(
                text = "模型",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(8.dp))
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    ModelStatusRow("ASR", "SenseVoice (int8)", "228MB", modelStatuses[0])
                    ModelStatusRow("说话人嵌入", "3D-Speaker ERes2Net", "~40MB", modelStatuses[1])
                    ModelStatusRow("降噪", "GTCRN", "~500KB", modelStatuses[2])
                    ModelStatusRow("VAD", "Silero VAD", "~1.8MB", modelStatuses[3])
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "总计: ~270MB",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

@Composable
private fun ModelStatusRow(label: String, model: String, size: String, isReady: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = "$model ($size)",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            text = if (isReady) "✓" else "✗",
            color = if (isReady) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.titleMedium
        )
    }
}
