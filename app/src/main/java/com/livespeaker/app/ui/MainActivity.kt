package com.livespeaker.app.ui

import android.Manifest
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowInsetsController
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.livespeaker.app.stt.SttConfig
import com.livespeaker.app.ui.navigation.AppNavigation
import com.livespeaker.app.ui.screen.SettingsSheet
import com.livespeaker.app.ui.theme.*
import com.livespeaker.app.viewmodel.RecordingViewModel
import java.io.File

class MainActivity : ComponentActivity() {

    private val viewModel: RecordingViewModel by viewModels()

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            viewModel.onPermissionGranted()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // ── 状态栏 + 导航栏：跟随系统主题切换图标颜色 ──
        val isDark = (resources.configuration.uiMode
            and android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
            android.content.res.Configuration.UI_MODE_NIGHT_YES

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // API 30+: WindowInsetsController（现代 API）
            val target = if (isDark) 0  // 暗色背景 → 浅色（白色）图标
                else (WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                    or WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS)  // 亮色背景 → 深色图标
            window.insetsController?.setSystemBarsAppearance(
                target,
                WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                    or WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
            )
        } else {
            // API 26-29: systemUiVisibility（传统 API）
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = if (isDark) {
                // 暗色背景 → 清除 LIGHT_* 标志 → 浅色（白色）图标
                window.decorView.systemUiVisibility
                    .and(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR.inv())
                    .and(View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR.inv())
            } else {
                // 亮色背景 → 设置 LIGHT_* 标志 → 深色图标
                window.decorView.systemUiVisibility
                    .or(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR)
                    .or(View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR)
            }
        }

        setContent {
            // 收集所有流状态
            val isRecording by viewModel.isRecording.collectAsState()
            val isPlaying by viewModel.isPlaying.collectAsState()
            val segments by viewModel.segments.collectAsState()
            val transcriptions by viewModel.transcriptions.collectAsState()
            val recorderState by viewModel.recorder.state.collectAsState()
            val currentDuration by viewModel.currentSegmentDuration.collectAsState()
            val playingSegment by viewModel.playingSegment.collectAsState()
            val playbackPosition by viewModel.playbackPosition.collectAsState()
            val playbackDuration by viewModel.playbackDuration.collectAsState()

            val isDarkTheme = isSystemInDarkTheme()

            MaterialTheme(
                colorScheme = if (isDarkTheme) {
                    darkColorScheme(
                        primary = AccentPrimary,
                        secondary = AccentSecondary,
                        tertiary = AccentTertiary,
                        background = DarkBackground,
                        surface = DarkSurface,
                        surfaceVariant = DarkSurfaceVariant,
                        error = ErrorRed,
                        onPrimary = DarkBackground,
                        onSecondary = DarkBackground,
                        onTertiary = DarkBackground,
                        onBackground = TextPrimary,
                        onSurface = TextPrimary,
                        onSurfaceVariant = TextSecondary,
                    )
                } else {
                    lightColorScheme(
                        primary = AccentPrimary,
                        secondary = AccentSecondary,
                        tertiary = AccentTertiary,
                        background = LightBackground,
                        surface = LightSurface,
                        surfaceVariant = LightSurfaceVariant,
                        error = ErrorRed,
                        onPrimary = DarkBackground,
                        onSecondary = DarkBackground,
                        onTertiary = DarkBackground,
                        onBackground = LightTextPrimary,
                        onSurface = LightTextPrimary,
                        onSurfaceVariant = LightTextSecondary,
                    )
                }
            ) {
                var showSettings by remember { mutableStateOf(false) }

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppNavigation(
                        isRecording = isRecording,
                        isPlaying = isPlaying,
                        segments = segments,
                        transcriptions = transcriptions,
                        recorderState = recorderState,
                        currentDuration = currentDuration,
                        playingSegment = playingSegment,
                        playbackPosition = playbackPosition,
                        playbackDuration = playbackDuration,
                        onFabClick = {
                            if (viewModel.hasPermission.value) {
                                if (isRecording || recorderState == com.livespeaker.app.audio.AudioRecorder.State.PAUSED) {
                                    viewModel.stopRecording()
                                } else {
                                    viewModel.startRecording()
                                }
                            } else {
                                permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            }
                        },
                        onSettingsClick = { showSettings = true },
                        onPlaySegment = { segment -> viewModel.playSegment(segment) },
                        onStopPlayback = { viewModel.stopPlayback() },
                        onPlayTranscriptionAudio = { fileName ->
                            val seg = segments.find { it.file.name == fileName }
                            if (seg != null) viewModel.playSegment(seg)
                        },
                        onRetryTranscription = { fileName ->
                            viewModel.retrySegment(fileName)
                        },
                        sttConfig = SttConfig(this),
                        recorderOutputDir = File(filesDir, "recordings")
                    )
                }

                if (showSettings) {
                    val config = SttConfig(this)
                    SettingsSheet(sttConfig = config, onDismiss = { showSettings = false })
                }
            }
        }
    }
}
