package com.livespeaker.app.ui

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
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

    /** 接收模型加载错误广播 */
    private val modelErrorReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val error = intent.getStringExtra(TranscriptionService.EXTRA_ERROR_MSG)
                ?: "未知错误"
            Toast.makeText(
                this@MainActivity,
                "录音启动失败: $error",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 注册模型错误广播
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(
                modelErrorReceiver,
                IntentFilter(TranscriptionService.ACTION_MODEL_ERROR),
                Context.RECEIVER_NOT_EXPORTED
            )
        } else {
            @Suppress("DEPRECATION")
            registerReceiver(
                modelErrorReceiver,
                IntentFilter(TranscriptionService.ACTION_MODEL_ERROR)
            )
        }

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

    override fun onDestroy() {
        super.onDestroy()
        try { unregisterReceiver(modelErrorReceiver) } catch (_: Exception) {}
    }

    /**
     * 权限检查流程：
     * 1. 录音权限 → 未授则请求，授权后走回调
     * 2. 通知权限 (Android 13+) → 同上
     * 3. 悬浮窗权限 → 未授则跳转设置（不阻塞，仅提示）
     * 4. 全部就绪 → 启动 Service
     */
    private fun checkPermissionsAndStart() {
        if (!AudioPermission.isGranted(this)) {
            permissionLauncher.launch(AudioPermission.requiredPermissions())
            return
        }

        // 悬浮窗权限：首次未授权时提示一次，后续不再骚扰
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
            !Settings.canDrawOverlays(this)
        ) {
            val prefs = getSharedPreferences("permissions", MODE_PRIVATE)
            if (!prefs.getBoolean("overlay_prompted", false)) {
                prefs.edit().putBoolean("overlay_prompted", true).apply()
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
                startActivity(intent)
                Toast.makeText(this, "请开启悬浮窗权限以使用悬浮球", Toast.LENGTH_SHORT).show()
            }
        }

        onPermissionsGranted()
    }

    private fun onPermissionsGranted() {
        TranscriptionService.start(this)
    }
}
