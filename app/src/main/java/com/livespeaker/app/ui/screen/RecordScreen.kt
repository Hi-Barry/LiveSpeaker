package com.livespeaker.app.ui.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
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
import com.livespeaker.app.audio.AudioRecorder
import com.livespeaker.app.audio.AudioRecorder.Segment
import java.text.SimpleDateFormat
import java.util.*

/**
 * 录音页面 — 状态栏 + 片段列表 + FAB（不含 TopAppBar，由 Navigation 管理）。
 *
 * 从原 MainScreen 提取，用作底部导航的第一个 Tab。
 */
@Composable
fun RecordScreen(
    isRecording: Boolean,
    isPlaying: Boolean,
    segments: List<Segment>,
    recorderState: AudioRecorder.State,
    currentDuration: Long,
    playingSegment: Segment?,
    playbackPosition: Long,
    playbackDuration: Long,
    onFabClick: () -> Unit,
    onPlaySegment: (Segment) -> Unit,
    onStopPlayback: () -> Unit,
    onExportSegment: (Segment) -> Unit,
    onShareSegment: (Segment) -> Unit,
    onDeleteSegment: (Segment) -> Unit
) {
    val fabIcon = when {
        isRecording -> Icons.Default.Stop
        isPaused(recorderState) -> Icons.Default.PlayArrow
        else -> Icons.Default.Mic
    }

    Scaffold(
        floatingActionButtonPosition = FabPosition.Center,
        floatingActionButton = {
            FloatingActionButton(
                onClick = onFabClick,
                containerColor = when {
                    isRecording -> MaterialTheme.colorScheme.error
                    isPaused(recorderState) -> MaterialTheme.colorScheme.secondary
                    else -> MaterialTheme.colorScheme.primary
                }
            ) {
                Icon(
                    imageVector = fabIcon,
                    contentDescription = when {
                        isRecording -> "停止录音"
                        isPaused(recorderState) -> "继续录音"
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
                        onPlay = { segment -> onPlaySegment(segment) },
                        onStopPlayback = onStopPlayback,
                        onExport = onExportSegment,
                        onShare = onShareSegment,
                        onDelete = onDeleteSegment
                    )
                }
            }

            // ── 底部进度条 ──
            AnimatedVisibility(
                visible = isPlaying,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                val progress = if (playbackDuration > 0) {
                    playbackPosition.toFloat() / playbackDuration.toFloat()
                } else 0f

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
                text = "点击底部按钮开始录音",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "可在设置中调整切割时长",
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
    onStopPlayback: () -> Unit,
    onExport: (Segment) -> Unit,
    onShare: (Segment) -> Unit,
    onDelete: (Segment) -> Unit
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
            items(segments, key = { it.file.absolutePath }) { segment ->
                SegmentItem(
                    segment = segment,
                    isCurrentPlaying = isPlaying && playingSegment == segment,
                    onPlay = { onPlay(segment) },
                    onStop = onStopPlayback,
                    onExport = { onExport(segment) },
                    onShare = { onShare(segment) },
                    onDelete = { onDelete(segment) }
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SegmentItem(
    segment: Segment,
    isCurrentPlaying: Boolean,
    onPlay: () -> Unit,
    onStop: () -> Unit,
    onExport: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    // ── 删除确认对话框 ──
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("确认删除") },
            text = { Text("删除后将同时移除对应的转录文本，此操作不可撤销。") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteDialog = false
                    onDelete()
                }) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("取消")
                }
            }
        )
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = {},
                onLongClick = { showMenu = true }
            )
            .background(
                if (isCurrentPlaying) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                else MaterialTheme.colorScheme.surface
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 左侧: 片段信息
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
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
                Text(
                    text = formatDuration(segment.durationMs),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }
            Spacer(Modifier.height(4.dp))
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

        // 更多操作按钮 + 下拉菜单
        Box {
            IconButton(onClick = { showMenu = true }) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = "更多操作",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(24.dp)
                )
            }

            DropdownMenu(
                expanded = showMenu,
                onDismissRequest = { showMenu = false }
            ) {
                DropdownMenuItem(
                    text = { Text("导出") },
                    onClick = { showMenu = false; onExport() },
                    leadingIcon = {
                        Icon(Icons.Default.FileDownload, null, modifier = Modifier.size(20.dp))
                    }
                )
                DropdownMenuItem(
                    text = { Text("分享") },
                    onClick = { showMenu = false; onShare() },
                    leadingIcon = {
                        Icon(Icons.Default.Share, null, modifier = Modifier.size(20.dp))
                    }
                )
                HorizontalDivider()
                DropdownMenuItem(
                    text = { Text("删除", color = MaterialTheme.colorScheme.error) },
                    onClick = {
                        showMenu = false
                        showDeleteDialog = true
                    },
                    leadingIcon = {
                        Icon(
                            Icons.Default.Delete, null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                )
            }
        }
    }
}

// ─── 工具函数 ───

private fun isPaused(state: AudioRecorder.State): Boolean = state == AudioRecorder.State.PAUSED

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
