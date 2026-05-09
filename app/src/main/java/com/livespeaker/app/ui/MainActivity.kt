package com.livespeaker.app.ui

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.Modifier
import com.livespeaker.app.audio.AudioPermission
import com.livespeaker.app.service.TranscriptionService
import com.livespeaker.app.ui.screen.MainScreen
import com.livespeaker.app.ui.theme.*

class MainActivity : ComponentActivity() {

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.values.all { it }) {
            onPermissionsGranted()
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
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainScreen(
                        onStart = { checkPermissionsAndStart() },
                        onStop = { TranscriptionService.stop(this) }
                    )
                }
            }
        }
    }

    private fun checkPermissionsAndStart() {
        if (!AudioPermission.isGranted(this)) {
            permissionLauncher.launch(AudioPermission.requiredPermissions())
            return
        }

        // 检查悬浮窗权限
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
            !Settings.canDrawOverlays(this)
        ) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivity(intent)
        }

        onPermissionsGranted()
    }

    private fun onPermissionsGranted() {
        TranscriptionService.start(this)
    }
}
