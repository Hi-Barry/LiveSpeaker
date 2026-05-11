package com.livespeaker.app.service

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.livespeaker.app.LiveSpeakerApp
import com.livespeaker.app.R
import com.livespeaker.app.ui.MainActivity

/**
 * 通知栏构建器。
 * 显示实时转写状态和控制按钮。
 */
object TranscriptionNotification {

    private const val NOTIFICATION_ID = 1001

    fun build(
        context: Context,
        state: RecordingState,
        lastText: String = ""
    ): Notification {
        val app = LiveSpeakerApp.instance

        // 点击通知打开主界面
        val contentIntent = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, LiveSpeakerApp.CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle(context.getString(R.string.notification_title))
            .setContentIntent(contentIntent)
            .setOngoing(state == RecordingState.RECORDING || state == RecordingState.PREPARING)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOnlyAlertOnce(true)

        when (state) {
            RecordingState.RECORDING -> {
                // 暂停按钮
                val pauseIntent = PendingIntent.getBroadcast(
                    context, 1,
                    Intent(ACTION_PAUSE).setPackage(context.packageName),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                // 停止按钮
                val stopIntent = PendingIntent.getBroadcast(
                    context, 2,
                    Intent(ACTION_STOP).setPackage(context.packageName),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )

                builder
                    .setContentText(
                        lastText.ifBlank { "正在监听…" }
                    )
                    .addAction(
                        android.R.drawable.ic_media_pause,
                        context.getString(R.string.notification_pause),
                        pauseIntent
                    )
                    .addAction(
                        android.R.drawable.ic_media_play,
                        context.getString(R.string.notification_stop),
                        stopIntent
                    )
            }
            RecordingState.PAUSED -> {
                val resumeIntent = PendingIntent.getBroadcast(
                    context, 3,
                    Intent(ACTION_RESUME).setPackage(context.packageName),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                builder
                    .setContentText("已暂停")
                    .addAction(
                        android.R.drawable.ic_media_play,
                        context.getString(R.string.notification_resume),
                        resumeIntent
                    )
            }
            else -> {
                builder.setContentText("准备就绪")
            }
        }

        return builder.build()
    }

    // 广播 Action 常量
    const val ACTION_PAUSE = "com.livespeaker.app.ACTION_PAUSE"
    const val ACTION_STOP = "com.livespeaker.app.ACTION_STOP"
    const val ACTION_RESUME = "com.livespeaker.app.ACTION_RESUME"

    const val NOTIFICATION_ID_VALUE: Int = NOTIFICATION_ID
}
