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
 * 生命周期: startService → 录音 → VAD → ASR → 说话人识别 → 通知栏更新
 */
class TranscriptionService : Service() {

    companion object {
        private const val TAG = "TranscriptionService"
        private const val MIN_SEGMENT_SAMPLES = 8000

        fun start(context: Context) {
            val intent = Intent(context, TranscriptionService::class.java)
            context.startForegroundService(intent)
        }

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

        val filter = IntentFilter().apply {
            addAction(TranscriptionNotification.ACTION_PAUSE)
            addAction(TranscriptionNotification.ACTION_STOP)
            addAction(TranscriptionNotification.ACTION_RESUME)
        }
        registerReceiver(notificationReceiver, filter, Context.RECEIVER_NOT_EXPORTED)

        val app = LiveSpeakerApp.instance
        ringBuffer = RingBuffer()
        audioRecorder = AudioRecorder(ringBuffer)
        sherpaEngine = SherpaEngine(this)
        speakerRepo = SpeakerRepository.fromApp()
        clusterEngine = ClusterEngine(speakerRepo)

        // 初始化模型
        val modelsDir = app.modelManager.modelsDir
        val asrModel = java.io.File(modelsDir, "sense-voice/model.int8.onnx")
        val tokensFile = java.io.File(modelsDir, "sense-voice/tokens.txt")
        if (asrModel.exists() && tokensFile.exists()) {
            sherpaEngine.initAsr(asrModel.absolutePath, tokensFile.absolutePath)
        }

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
            Log.w(TAG, "模型未就绪")
            stopSelf()
            return
        }

        state = RecordingState.RECORDING
        startForeground(
            TranscriptionNotification.NOTIFICATION_ID_VALUE,
            TranscriptionNotification.build(this, RecordingState.RECORDING)
        )

        audioRecorder.start()

        processingJob = serviceScope.launch {
            clusterEngine.loadProfiles()
            val frame = ShortArray(4096)
            while (isActive && state == RecordingState.RECORDING) {
                val n = ringBuffer.read(frame)
                if (n > 0) {
                    vadProcessor.processFrame(if (n == frame.size) frame else frame.copyOf(n))
                } else {
                    delay(50)
                }
            }
        }

        Log.i(TAG, "录音和转写已启动")
    }

    private fun handleSpeechSegment(audio: ShortArray) {
        if (audio.size < MIN_SEGMENT_SAMPLES) return

        serviceScope.launch(Dispatchers.IO) {
            try {
                val samples = FloatArray(audio.size) { audio[it] / 32768f }

                // Stage 1: ASR
                val text = sherpaEngine.recognize(samples)
                if (text.isBlank()) return@launch

                // Stage 2: 说话人嵌入 (如果有模型)
                val embedding = sherpaEngine.extractEmbedding(samples)

                // Stage 3: 聚类 (如果有嵌入)
                val clusterResult = if (embedding != null) {
                    clusterEngine.identifyOrCreate(embedding)
                } else {
                    ClusterResult("unknown", "unknown", false, 0f)
                }

                val line = "[${clusterResult.label}] $text"
                updateNotification(line)

                TranscriptionEventBus.emit(
                    TranscriptionLine(
                        speakerId = clusterResult.speakerId,
                        speakerLabel = clusterResult.label,
                        text = text,
                        isNewSpeaker = clusterResult.isNew,
                        timestamp = System.currentTimeMillis()
                    )
                )
            } catch (e: Exception) {
                Log.e(TAG, "处理语音段失败", e)
            }
        }
    }

    private fun updateNotification(text: String) {
        val notification = TranscriptionNotification.build(this, state, text)
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

// Event bus
object TranscriptionEventBus {
    private val listeners = mutableListOf<(TranscriptionLine) -> Unit>()
    fun emit(line: TranscriptionLine) { listeners.forEach { it(line) } }
    fun listen(listener: (TranscriptionLine) -> Unit) { listeners.add(listener) }
    fun remove(listener: (TranscriptionLine) -> Unit) { listeners.remove(listener) }
    fun clear() { listeners.clear() }
}

data class TranscriptionLine(
    val speakerId: String,
    val speakerLabel: String,
    val text: String,
    val isNewSpeaker: Boolean,
    val timestamp: Long
)
