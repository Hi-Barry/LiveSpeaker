package com.livespeaker.app.ui.screen

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.livespeaker.app.service.RecordingState
import com.livespeaker.app.service.TranscriptionEventBus
import com.livespeaker.app.service.TranscriptionLine
import kotlinx.coroutines.launch

/**
 * 主界面 - 实时转写显示。
 *
 * 功能:
 * - 滚动显示实时转写结果
 * - 不同说话人用不同颜色标识
 * - 开始/停止录音按钮
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    onStart: () -> Unit,
    onStop: () -> Unit
) {
    var serviceState by remember { mutableStateOf(RecordingState.IDLE) }
    val lines = remember { mutableStateListOf<TranscriptionLine>() }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    val isPreparing = serviceState == RecordingState.PREPARING
    val isRecording = serviceState == RecordingState.RECORDING
    val isActive = isPreparing || isRecording

    // 注册事件监听
    DisposableEffect(Unit) {
        val lineListener: (TranscriptionLine) -> Unit = { line ->
            lines.add(line)
            scope.launch {
                if (lines.size > 1) {
                    listState.animateScrollToItem(lines.size - 1)
                }
            }
        }
        TranscriptionEventBus.listen(lineListener)

        val stateListener: (RecordingState) -> Unit = { s ->
            serviceState = s
        }
        TranscriptionEventBus.listenState(stateListener)

        onDispose {
            TranscriptionEventBus.removeLineListener(lineListener)
            TranscriptionEventBus.removeStateListener(stateListener)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("LiveSpeaker") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    if (isActive) {
                        onStop()
                    } else {
                        onStart()
                    }
                },
                containerColor = when {
                    isPreparing -> MaterialTheme.colorScheme.secondary
                    isRecording -> MaterialTheme.colorScheme.error
                    else -> MaterialTheme.colorScheme.primary
                },
                enabled = !isPreparing  // 准备中不可点击
            ) {
                Icon(
                    imageVector = when {
                        isPreparing -> Icons.Default.HourglassEmpty
                        isRecording -> Icons.Default.Stop
                        else -> Icons.Default.PlayArrow
                    },
                    contentDescription = when {
                        isPreparing -> "准备中"
                        isRecording -> "停止录音"
                        else -> "开始录音"
                    }
                )
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // 状态栏
            RecordingStatusBar(isActive = isActive, isPreparing = isPreparing, lineCount = lines.size)

            // 转写列表
            if (lines.isEmpty()) {
                // 空状态
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.Mic,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(16.dp))
                        Text(
                            text = "点击右下角按钮开始录音",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "语音将被实时转写并识别说话人",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(lines) { line ->
                        TranscriptionLineView(line)
                    }
                }
            }
        }
    }
}

@Composable
private fun RecordingStatusBar(isActive: Boolean, isPreparing: Boolean, lineCount: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 录音指示灯
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(
                    when {
                        isPreparing -> MaterialTheme.colorScheme.secondary
                        isActive -> MaterialTheme.colorScheme.error
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    shape = androidx.compose.foundation.shape.CircleShape
                )
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = when {
                isPreparing -> "准备中..."
                isActive -> "录音中"
                else -> "未录音"
            },
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium
        )
        Spacer(Modifier.weight(1f))
        if (lineCount > 0) {
            Text(
                text = "$lineCount 条转写",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** 单条转写结果 */
@Composable
private fun TranscriptionLineView(line: TranscriptionLine) {
    val speakerColor = when {
        line.isNewSpeaker -> MaterialTheme.colorScheme.tertiary
        line.speakerLabel.hashCode() % 2 == 0 ->
            MaterialTheme.colorScheme.secondary
        else -> MaterialTheme.colorScheme.primary
    }

    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // 说话人标签
            Surface(
                color = speakerColor.copy(alpha = 0.2f),
                shape = MaterialTheme.shapes.small
            ) {
                Text(
                    text = line.speakerLabel,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = speakerColor
                )
            }
            if (line.isNewSpeaker) {
                Spacer(Modifier.width(4.dp))
                Text(
                    text = "🆕",
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
        Spacer(Modifier.height(2.dp))
        Text(
            text = line.text,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontSize = 15.sp,
                lineHeight = 22.sp
            ),
            color = MaterialTheme.colorScheme.onBackground
        )
    }
    Divider(
        modifier = Modifier.padding(top = 4.dp),
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f)
    )
}
