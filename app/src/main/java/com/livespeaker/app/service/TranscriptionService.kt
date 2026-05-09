package com.livespeaker.app.service

import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.IBinder
import android.util.Log
import com.livespeaker.app.LiveSpeakerApp
import com.livespeaker.app.audio.AudioRecorder
import com.livespeaker.app.audio.RingBuffer
import com.livespeaker.app.diarization.ClusterEngine
import com.livespeaker.app.diarization.ClusterResult
import com.livespeaker.app.pipeline.SherpaEngine
import com.livespeaker.app.pipeline.VadProcessor
import com.livespeaker.app.data.SpeakerRepository
import kotlinx.coroutines.*

/**
 * 前台转写服务。
 *
 * 生命周期: startService → 录音 → 降噪 → VAD → ASR → 说话人识别 → 通知栏更新
 *
 * 使用 startForeground 保持后台运行，不会被系统杀死。
 */
class TranscriptionService : Service() {

    companion object {
        private const val TAG = "TranscriptionService"
        private const val MIN_SEGMENT_SAMPLES = 8000 // 500ms @ 16kHz

        /** 启动服务 */
        fun start(context: Context) {
            val intent = Intent(context, TranscriptionService::class.java)
            context.startForegroundService(intent)
        }

        /** 停止服务 */
        fun stop(context: Context) {
            val intent = Intent(context, TranscriptionService::class.java)
            context.stopService(intent)
        }
    }

    private lateinit var ringBuffer: RingBuffer
    private lateinit var audioRecorder: AudioRecorder
    private lateinit var sherpaEngine: SherpaEngine
    private lateinit var vadProcessor: VadProcessor
    private lateinit var clusterEngine: ClusterEngine
    private lateinit var speakerRepo: SpeakerRepository

    private var state = RecordingState.IDLE

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var processingJob: Job? = null

    // 通知栏控制接收器
    private val notificationReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                TranscriptionNotification.ACTION_PAUSE -> pause()
                TranscriptionNotification.ACTION_STOP -> stopSelf()
                TranscriptionNotification.ACTION_RESUME -> resume()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "服务创建")

        // 注册通知栏控制接收器
        val filter = IntentFilter().apply {
            addAction(TranscriptionNotification.ACTION_PAUSE)
            addAction(TranscriptionNotification.ACTION_STOP)
            addAction(TranscriptionNotification.ACTION_RESUME)
        }
        registerReceiver(notificationReceiver, filter, Context.RECEIVER_NOT_EXPORTED)

        // 初始化组件
        val app = LiveSpeakerApp.instance
        ringBuffer = RingBuffer()
        audioRecorder = AudioRecorder(ringBuffer)
        sherpaEngine = SherpaEngine(app)
        speakerRepo = SpeakerRepository.fromApp()
        clusterEngine = ClusterEngine(speakerRepo)

        // 初始化模型
        sherpaEngine.init()

        // 配置 VAD
        vadProcessor = VadProcessor().apply {
            onSpeechEnd = { segment -> handleSpeechSegment(segment) }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when {
            state.canStart() -> startRecording()
            state.canResume() -> resume()
        }
        return START_STICKY
    }

    private fun startRecording() {
        if (!sherpaEngine.isReady) {
            Log.w(TAG, "模型未就绪，无法开始")
            stopSelf()
            return
        }

        state = RecordingState.RECORDING

        // 启动前台服务
        startForeground(
            TranscriptionNotification.NOTIFICATION_ID_VALUE,
            TranscriptionNotification.build(this, RecordingState.RECORDING)
        )

        // 启动录音
        audioRecorder.start()

        // 启动处理流水线
        processingJob = serviceScope.launch {
            // 加载说话人档案
            clusterEngine.loadProfiles()

            // 持续从 RingBuffer 读取并处理
            val frame = ShortArray(4096) // 256ms @ 16kHz
            while (isActive && state == RecordingState.RECORDING) {
                val n = ringBuffer.read(frame)
                if (n > 0) {
                    val segment = if (n == frame.size) frame else frame.copyOf(n)
                    vadProcessor.processFrame(segment)
                } else {
                    delay(50) // 没有数据时短暂等待
                }
            }
        }

        Log.i(TAG, "录音和转写已启动")
    }

    /**
     * 处理完整语音段: 降噪 → ASR → 说话人嵌入 → 聚类 → UI 更新
     */
    private fun handleSpeechSegment(audio: ShortArray) {
        if (audio.size < MIN_SEGMENT_SAMPLES) return

        serviceScope.launch(Dispatchers.IO) {
            try {
                // 转为 FloatArray
                val samples = FloatArray(audio.size) { audio[it] / 32768f }

                // Stage 1: GTCRN 降噪
                val clean = sherpaEngine.denoise(samples)

                // Stage 2: ASR
                val result = sherpaEngine.recognize(clean)
                val text = result.text
                if (text.isBlank()) return@launch

                // Stage 3: 说话人嵌入
                val embedding = sherpaEngine.extractEmbedding(clean)

                // Stage 4: 聚类
                val clusterResult = clusterEngine.identifyOrCreate(embedding)

                // Stage 5: 更新通知栏
                val line = "[${clusterResult.label}] $text"
                updateNotification(line)

                // Stage 6: 发送结果到 UI (通过 LiveData/Flow)
                TranscriptionEventBus.emit(
                    TranscriptionLine(
                        speakerId = clusterResult.speakerId,
                        speakerLabel = clusterResult.label,
                        text = text,
                        isNewSpeaker = clusterResult.isNew,
                        timestamp = System.currentTimeMillis()
                    )
                )

                Log.d(TAG, "$line (newSpeaker=${clusterResult.isNew})")
            } catch (e: Exception) {
                Log.e(TAG, "处理语音段失败", e)
            }
        }
    }

    private fun updateNotification(text: String) {
        val notification = TranscriptionNotification.build(
            this, state, text
        )
        val manager = getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager
        manager.notify(TranscriptionNotification.NOTIFICATION_ID_VALUE, notification)
    }

    private fun pause() {
        if (!state.canPause()) return
        state = RecordingState.PAUSED
        audioRecorder.stop()
        processingJob?.cancel()
        vadProcessor.forceEndSegment()

        val notification = TranscriptionNotification.build(this, RecordingState.PAUSED)
        val manager = getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager
        manager.notify(TranscriptionNotification.NOTIFICATION_ID_VALUE, notification)

        Log.i(TAG, "已暂停")
    }

    private fun resume() {
        if (!state.canResume()) return
        startRecording()
        Log.i(TAG, "已恢复")
    }

    override fun onDestroy() {
        state = RecordingState.STOPPED

        processingJob?.cancel()
        audioRecorder.release()
        vadProcessor.forceEndSegment()
        sherpaEngine.release()
        clusterEngine.release()

        try { unregisterReceiver(notificationReceiver) } catch (_: Exception) {}

        TranscriptionEventBus.clear()

        Log.i(TAG, "服务已销毁")
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}

// ──────────────────────────────────────────────────
// 事件总线 (简单版 LiveData)
// ──────────────────────────────────────────────────

object TranscriptionEventBus {
    private val listeners = mutableListOf<(TranscriptionLine) -> Unit>()

    fun emit(line: TranscriptionLine) {
        listeners.forEach { it(line) }
    }

    fun listen(listener: (TranscriptionLine) -> Unit) {
        listeners.add(listener)
    }

    fun remove(listener: (TranscriptionLine) -> Unit) {
        listeners.remove(listener)
    }

    fun clear() {
        listeners.clear()
    }
}

data class TranscriptionLine(
    val speakerId: String,
    val speakerLabel: String,
    val text: String,
    val isNewSpeaker: Boolean,
    val timestamp: Long
)
