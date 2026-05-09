package com.livespeaker.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.livespeaker.app.pipeline.ModelManager
import com.livespeaker.app.data.AppDatabase

class LiveSpeakerApp : Application() {

    lateinit var modelManager: ModelManager
        private set

    lateinit var database: AppDatabase
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this

        // 初始化数据库
        database = AppDatabase.getInstance(this)

        // 初始化模型管理器
        modelManager = ModelManager(this)

        // 创建通知渠道 (Android 8.0+)
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.service_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.service_channel_desc)
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    companion object {
        const val CHANNEL_ID = "livespeaker_transcription"

        lateinit var instance: LiveSpeakerApp
            private set
    }
}
