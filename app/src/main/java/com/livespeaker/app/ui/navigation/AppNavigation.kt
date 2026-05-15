package com.livespeaker.app.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.livespeaker.app.audio.AudioRecorder
import com.livespeaker.app.stt.SttConfig
import com.livespeaker.app.stt.TranscriptionResult
import com.livespeaker.app.ui.screen.RecordScreen
import com.livespeaker.app.ui.screen.SettingsScreen
import com.livespeaker.app.ui.screen.TranscriptionScreen
import java.io.File

/**
 * 底部导航项的密封类定义。
 */
sealed class Screen(
    val route: String,
    val label: String,
    val icon: ImageVector,
    val selectedIcon: ImageVector
) {
    data object Record : Screen("record", "录音", Icons.Default.Mic, Icons.Default.Mic)
    data object Transcriptions : Screen(
        "transcriptions", "转写",
        Icons.Default.TextSnippet, Icons.Default.TextSnippet
    )
}

/**
 * 应用导航框架 — 底部导航栏 + NavHost。
 *
 * 两个 Tab:
 * - 录音 (RecordScreen)
 * - 转写 (TranscriptionScreen)
 *
 * TopAppBar 右侧 ⚙️ 进入 SettingsScreen（全屏覆盖）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppNavigation(
    isRecording: Boolean,
    isPlaying: Boolean,
    segments: List<AudioRecorder.Segment>,
    transcriptions: List<TranscriptionResult>,
    recorderState: AudioRecorder.State,
    currentDuration: Long,
    playingSegment: AudioRecorder.Segment?,
    playbackPosition: Long,
    playbackDuration: Long,
    onFabClick: () -> Unit,
    onPlaySegment: (AudioRecorder.Segment) -> Unit,
    onStopPlayback: () -> Unit,
    onPlayTranscriptionAudio: (String) -> Unit,
    sttConfig: SttConfig,
    recorderOutputDir: File
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val bottomNavItems = listOf(Screen.Record, Screen.Transcriptions)

    var showSettings by remember { mutableStateOf(false) }

    if (showSettings) {
        // 设置页作为全屏覆盖
        SettingsScreen(
            sttConfig = sttConfig,
            onBack = { showSettings = false }
        )
    } else {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            when (currentRoute) {
                                Screen.Transcriptions.route -> "LiveSpeaker"
                                else -> "LiveSpeaker"
                            }
                        )
                    },
                    actions = {
                        IconButton(onClick = { showSettings = true }) {
                            Icon(
                                Icons.Default.Settings,
                                contentDescription = "设置",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )
            },
            bottomBar = {
                NavigationBar {
                    bottomNavItems.forEach { screen ->
                        NavigationBarItem(
                            icon = {
                                Icon(
                                    imageVector = if (currentRoute == screen.route)
                                        screen.selectedIcon else screen.icon,
                                    contentDescription = screen.label
                                )
                            },
                            label = { Text(screen.label) },
                            selected = currentRoute == screen.route,
                            onClick = {
                                navController.navigate(screen.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        )
                    }
                }
            }
        ) { padding ->
            NavHost(
                navController = navController,
                startDestination = Screen.Record.route,
                modifier = Modifier.padding(padding)
            ) {
                composable(Screen.Record.route) {
                    RecordScreen(
                        isRecording = isRecording,
                        isPlaying = isPlaying,
                        segments = segments,
                        recorderState = recorderState,
                        currentDuration = currentDuration,
                        playingSegment = playingSegment,
                        playbackPosition = playbackPosition,
                        playbackDuration = playbackDuration,
                        onFabClick = onFabClick,
                        onPlaySegment = onPlaySegment,
                        onStopPlayback = onStopPlayback
                    )
                }
                composable(Screen.Transcriptions.route) {
                    TranscriptionScreen(
                        transcriptions = transcriptions,
                        onPlaySegment = onPlayTranscriptionAudio,
                        recorderOutputDir = recorderOutputDir
                    )
                }
            }
        }
    }
}
