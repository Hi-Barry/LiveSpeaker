package com.livespeaker.app.audio

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.livespeaker.app.LiveSpeakerApp
import com.livespeaker.app.R

/**
 * 极简前台 Service：只为保证录音进程不被系统杀死。
 *
 * 不包含任何录音逻辑 —— 录音仍在 AudioRecorder (ViewModel 内) 进行。
 * Service 的唯一职责是调用 startForeground() 提升进程优先级到前台级别。
 */
class RecordingService : Service() {

    companion object {
        private const val NOTIFICATION_ID = 1
        const val ACTION_STOP = "com.livespeaker.action.STOP_RECORDING"
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when {
            intent?.action == ACTION_STOP -> {
                stopRecording()
            }
            else -> {
                startRecording()
            }
        }
        return START_STICKY
    }

    private fun startRecording() {
        val notification = buildNotification()
        startForeground(NOTIFICATION_ID, notification)
    }

    private fun stopRecording() {
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun buildNotification(): Notification {
        // 点击通知 → 回到主界面
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, Class.forName("com.livespeaker.app.ui.MainActivity"))
                .setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, LiveSpeakerApp.CHANNEL_ID)
            .setContentTitle("LiveSpeaker")
            .setContentText("录音中…")
            .setSmallIcon(R.drawable.ic_mic)
            .setOngoing(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setContentIntent(pendingIntent)
            .build()
    }
}
