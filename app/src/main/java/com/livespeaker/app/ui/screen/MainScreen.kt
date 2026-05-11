package com.livespeaker.app.ui.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.livespeaker.app.audio.AudioRecorder
import com.livespeaker.app.audio.AudioRecorder.Segment
import com.livespeaker.app.viewmodel.RecordingViewModel
import java.text.SimpleDateFormat
import java.util.*

/**
 * v2 主界面 — 录音控制 + 文件列表 + 播放回放。
 *
 * 布局:
 *  - TopAppBar: 标题
 *  - 状态栏: 录音状态 + 当前片段计时
 *  - 文件列表: 每行显示片段信息 + 右侧播放按钮
 *  - FAB: 开始/停止录音
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: RecordingViewModel,
    onFabClick: () -> Unit
) {
    val segments by viewModel.segments.collectAsState()
    val isRecording by viewModel.isRecording.collectAsState()
    val isPaused by viewModel.isPaused.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()
    val playingSegment by viewModel.playingSegment.collectAsState()
    val currentDuration by viewModel.currentSegmentDuration.collectAsState()
    val recorderState by viewModel.recorder.state.collectAsState()

    val isActive = isRecording || isPaused
    val fabIcon = when {
        isRecording -> Icons.Default.Stop
        isPaused -> Icons.Default.PlayArrow
        else -> Icons.Default.Mic
    }

    Scaffold(
        floatingActionButtonPosition = FabPosition.Center,
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("LiveSpeaker")
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "v2",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onFabClick,
                containerColor = when {
                    isRecording -> MaterialTheme.colorScheme.error
                    isPaused -> MaterialTheme.colorScheme.secondary
                    else -> MaterialTheme.colorScheme.primary
                }
            ) {
                Icon(
                    imageVector = fabIcon,
                    contentDescription = when {
                        isRecording -> "停止录音"
                        isPaused -> "继续录音"
                        else -> "开始录音"
                    }
                )
            }
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            Column(modifier = Modifier.fillMaxSize()) {
                // ── 状态栏 ──
                StatusBar(
                    recorderState = recorderState,
                    isPlaying = isPlaying,
                    currentDuration = currentDuration,
                    segmentCount = segments.size
                )

                // ── 文件列表 ──
                if (segments.isEmpty() && recorderState == AudioRecorder.State.IDLE) {
                    EmptyState()
                } else {
                    SegmentList(
                        segments = segments,
                        playingSegment = playingSegment,
                        isPlaying = isPlaying,
                        onPlay = { segment -> viewModel.playSegment(segment) },
                        onStopPlayback = { viewModel.stopPlayback() }
                    )
                }
            }

            // ── 底部进度条（2dp，播放时显示）──
            AnimatedVisibility(
                visible = isPlaying,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                val position by viewModel.playbackPosition.collectAsState()
                val duration by viewModel.playbackDuration.collectAsState()
                val progress = if (duration > 0) position.toFloat() / duration.toFloat() else 0f

                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.dp),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = Color.Transparent,
                )
            }
        }
    }
}

// ─── 状态栏 ───

@Composable
private fun StatusBar(
    recorderState: AudioRecorder.State,
    isPlaying: Boolean,
    currentDuration: Long,
    segmentCount: Int
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f))
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 状态指示灯
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(
                    when (recorderState) {
                        AudioRecorder.State.RECORDING -> MaterialTheme.colorScheme.error
                        AudioRecorder.State.PAUSED -> MaterialTheme.colorScheme.secondary
                        AudioRecorder.State.IDLE -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
        )
        Spacer(Modifier.width(10.dp))

        // 状态文字
        Text(
            text = when (recorderState) {
                AudioRecorder.State.RECORDING -> "录音中"
                AudioRecorder.State.PAUSED -> "已暂停"
                AudioRecorder.State.IDLE -> if (segmentCount > 0) "录音完成" else "未录音"
            },
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Medium
        )

        // 当前片段计时
        if (recorderState != AudioRecorder.State.IDLE) {
            Spacer(Modifier.width(6.dp))
            Text(
                text = formatDuration(currentDuration),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(Modifier.weight(1f))

        // 片段计数
        if (segmentCount > 0) {
            Text(
                text = "$segmentCount 个片段",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// ─── 空状态 ───

@Composable
private fun EmptyState() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Default.Mic,
                contentDescription = null,
                modifier = Modifier.size(72.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text = "点击右下角按钮开始录音",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "每 1 分钟自动切割为独立片段",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
        }
    }
}

// ─── 文件列表 ───

@Composable
private fun SegmentList(
    segments: List<Segment>,
    playingSegment: Segment?,
    isPlaying: Boolean,
    onPlay: (Segment) -> Unit,
    onStopPlayback: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        // 列表标题
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "录音片段",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = "(${segments.size})",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))

        LazyColumn(
            contentPadding = PaddingValues(top = 4.dp, bottom = 72.dp)
        ) {
            items(segments) { segment ->
                SegmentItem(
                    segment = segment,
                    isCurrentPlaying = isPlaying && playingSegment == segment,
                    onPlay = { onPlay(segment) },
                    onStop = onStopPlayback
                )
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f),
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }
        }
    }
}

// ─── 单条片段项 ───

@Composable
private fun SegmentItem(
    segment: Segment,
    isCurrentPlaying: Boolean,
    onPlay: () -> Unit,
    onStop: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (isCurrentPlaying) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                } else {
                    MaterialTheme.colorScheme.surface
                }
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 左侧: 片段信息
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // 片段编号
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = "#${segment.index}",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.width(10.dp))
                // 时长
                Text(
                    text = formatDuration(segment.durationMs),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }
            Spacer(Modifier.height(4.dp))
            // 时间戳
            Text(
                text = formatTimestamp(segment.timestamp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        // 右侧: 播放按钮
        IconButton(
            onClick = if (isCurrentPlaying) onStop else onPlay
        ) {
            Icon(
                imageVector = if (isCurrentPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = if (isCurrentPlaying) "停止播放" else "播放",
                tint = if (isCurrentPlaying) MaterialTheme.colorScheme.primary
                       else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(28.dp)
            )
        }
    }
}

// ─── 工具函数 ───

private fun formatDuration(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}

private fun formatTimestamp(timestamp: Long): String {
    val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
    return sdf.format(Date(timestamp))
}
