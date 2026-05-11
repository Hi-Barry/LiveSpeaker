package com.livespeaker.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build

class LiveSpeakerApp : Application() {

    override fun onCreate() {
        super.onCreate()
        instance = this
        SettingsManager.init(this)
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "录音状态",
                NotificationManager.IMPORTANCE_LOW  // 低优先级，不响铃不震动
            ).apply {
                description = "显示录音进行中状态"
                setShowBadge(false)
            }
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(channel)
        }
    }

    companion object {
        lateinit var instance: LiveSpeakerApp
            private set

        const val CHANNEL_ID = "recording_channel"
    }
}
