package com.livespeaker.app.ui

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.livespeaker.app.ui.screen.MainScreen
import com.livespeaker.app.ui.screen.SettingsSheet
import com.livespeaker.app.ui.theme.*
import com.livespeaker.app.viewmodel.RecordingViewModel

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

        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
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
            ) {
                var showSettings by remember { mutableStateOf(false) }

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainScreen(
                        viewModel = viewModel,
                        onFabClick = {
                            if (viewModel.hasPermission.value) {
                                if (viewModel.isRecording.value || viewModel.isPaused.value) {
                                    viewModel.stopRecording()
                                } else {
                                    viewModel.startRecording()
                                }
                            } else {
                                permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            }
                        },
                        onSettingsClick = { showSettings = true }
                    )
                }

                if (showSettings) {
                    SettingsSheet(onDismiss = { showSettings = false })
                }
            }
        }
    }
}
