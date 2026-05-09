package com.livespeaker.app.ui.bubble

import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import com.livespeaker.app.R
import com.livespeaker.app.service.TranscriptionService
import com.livespeaker.app.ui.MainActivity

/**
 * 悬浮球服务。
 *
 * 在屏幕上显示一个可拖动的悬浮球，用于快速控制录音。
 * 点击: 开始/停止
 * 双击: 打开主界面
 */
class BubbleService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var bubbleLayout: FrameLayout
    private var isRecording = false

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "bubble_service",
                "悬浮球",
                NotificationManager.IMPORTANCE_MIN
            ).apply { setShowBadge(false) }
            val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }

        val notification = Notification.Builder(this, "bubble_service")
            .setContentTitle("LiveSpeaker")
            .setContentText("悬浮球已启动")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .build()

        startForeground(2001, notification)

        createBubble()
    }

    private fun createBubble() {
        bubbleLayout = FrameLayout(this).apply {
            val bubbleSize = 60.dpToPx()

            layoutParams = FrameLayout.LayoutParams(bubbleSize, bubbleSize)

            // 圆形背景
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(0xFF4FC3F7.toInt())
                shape = android.graphics.drawable.GradientDrawable.OVAL
            }
        }

        // 麦克风图标
        val iconView = ImageView(this).apply {
            setImageResource(android.R.drawable.ic_btn_speak_now)
            setColorFilter(0xFFFFFFFF.toInt())
            setPadding(12.dpToPx(), 12.dpToPx(), 12.dpToPx(), 12.dpToPx())
        }
        bubbleLayout.addView(iconView)

        // 点击和拖动处理
        setupTouchListener()

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = 20
            y = 200
        }

        windowManager.addView(bubbleLayout, params)
    }

    private fun setupTouchListener() {
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f
        var isDragging = false
        var dragThreshold = 10f
        var touchStartTime = 0L

        bubbleLayout.setOnTouchListener { view, event ->
            when (event.action) {
                android.view.MotionEvent.ACTION_DOWN -> {
                    initialX = (view.layoutParams as WindowManager.LayoutParams).x
                    initialY = (view.layoutParams as WindowManager.LayoutParams).y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    isDragging = false
                    touchStartTime = System.currentTimeMillis()
                    true
                }
                android.view.MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - initialTouchX
                    val dy = event.rawY - initialTouchY
                    if (kotlin.math.abs(dx) > dragThreshold ||
                        kotlin.math.abs(dy) > dragThreshold
                    ) {
                        isDragging = true
                    }
                    if (isDragging) {
                        val params = view.layoutParams as WindowManager.LayoutParams
                        params.x = initialX - dx.toInt()
                        params.y = initialY + dy.toInt()
                        windowManager.updateViewLayout(view, params)
                    }
                    true
                }
                android.view.MotionEvent.ACTION_UP -> {
                    val duration = System.currentTimeMillis() - touchStartTime
                    if (!isDragging && duration < 300) {
                        // 点击: 切换录音
                        toggleRecording()
                    }
                    true
                }
                else -> false
            }
        }
    }

    private fun toggleRecording() {
        if (isRecording) {
            TranscriptionService.stop(this)
            isRecording = false
            updateBubbleColor(0xFF4FC3F7.toInt())
        } else {
            TranscriptionService.start(this)
            isRecording = true
            updateBubbleColor(0xFFEF5350.toInt()) // 红色表示录音中
        }
    }

    private fun updateBubbleColor(color: Int) {
        (bubbleLayout.background as? android.graphics.drawable.GradientDrawable)?.setColor(color)
    }

    private fun Int.dpToPx(): Int =
        (this * resources.displayMetrics.density).toInt()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        try { windowManager.removeView(bubbleLayout) } catch (_: Exception) {}
    }
}
