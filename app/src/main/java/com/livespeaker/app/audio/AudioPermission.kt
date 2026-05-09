package com.livespeaker.app.audio

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat

/**
 * 录音权限管理。
 * 使用 AndroidX Activity Result API，在 Activity 中调用。
 */
object AudioPermission {

    /** 检查是否有录音权限 */
    fun isGranted(activity: ComponentActivity): Boolean {
        return ContextCompat.checkSelfPermission(
            activity, Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    /** 检查是否有通知权限 (Android 13+) */
    fun hasNotificationPermission(activity: ComponentActivity): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                activity, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else true
    }

    /** 创建权限请求启动器 */
    fun createLauncher(
        activity: ComponentActivity,
        onGranted: () -> Unit,
        onDenied: () -> Unit
    ) = activity.registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        if (allGranted) onGranted() else onDenied()
    }

    /** 需要的权限列表 */
    fun requiredPermissions(): Array<String> {
        val perms = mutableListOf(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            perms.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        return perms.toTypedArray()
    }
}
