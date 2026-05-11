package com.livespeaker.app.service

import android.Manifest
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import com.livespeaker.app.LiveSpeakerApp
import com.livespeaker.app.audio.AudioRecorder
import com.livespeaker.app.audio.RingBuffer
import com.livespeaker.app.diarization.ClusterEngine
import com.livespeaker.app.diarization.ClusterResult
import com.livespeaker.app.pipeline.ModelManager
import com.livespeaker.app.pipeline.SherpaEngine
import com.livespeaker.app.pipeline.VadProcessor
import com.livespeaker.app.data.SpeakerRepository
import kotlinx.coroutines.*

/**
 * 前台转写服务。
 *
 * 生命周期: startService → 异步加载模型 → startForeground → 录音 → VAD → ASR → 说话人识别
 */
class TranscriptionService : Service() {

    companion object {
        private const val TAG = "TranscriptionService"
        private const val MIN_SEGMENT_SAMPLES = 8000
        private const val MODEL_LOAD_TIMEOUT_MS = 120_000L  // 2min — 含下载 228MB 模型时间

        /** 模型加载错误广播，UI 层接收后显示错误提示 */
        const val ACTION_MODEL_ERROR = "com.livespeaker.app.ACTION_MODEL_ERROR"
        const val EXTRA_ERROR_MSG = "error_msg"

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
    private var modelLoadJob: Job? = null
    @Volatile private var modelLoadError: String? = null
    @Volatile private var downloadProgress: Int = -1  // -1=未开始下载, 0-100=下载中

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

        try {
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

            // ★ 异步加载模型 — 避免阻塞 onCreate 主线程
            val modelsDir = app.modelManager.modelsDir
            val asrModel = java.io.File(modelsDir, "sense-voice/model.int8.onnx")
            val tokensFile = java.io.File(modelsDir, "sense-voice/tokens.txt")

            modelLoadJob = serviceScope.launch {
                try {
                    // 模型不存在 → 自动下载
                    if (!asrModel.exists() || !tokensFile.exists()) {
                        Log.i(TAG, "[Init] 模型缺失，开始自动下载 (ASR ~228MB)...")
                        downloadProgress = 0
                        app.modelManager.downloadModel(
                            ModelManager.MODELS.first { it.dir == "sense-voice" }
                        ) { percent ->
                            downloadProgress = percent
                            Log.d(TAG, "[Download] SenseVoice: $percent%")
                        }
                        downloadProgress = -1  // 下载完成，清除标记
                        Log.i(TAG, "[Init] 下载完成，开始加载模型")
                    }

                    // 加载模型
                    if (asrModel.exists() && tokensFile.exists()) {
                        val ok = sherpaEngine.initAsr(asrModel.absolutePath, tokensFile.absolutePath)
                        if (ok) {
                            Log.i(TAG, "[Init] ASR 模型加载成功 " +
                                    "(${asrModel.length() / 1024 / 1024}MB)")
                        } else {
                            modelLoadError = "ASR 模型文件损坏或不兼容"
                            Log.e(TAG, "[Init] $modelLoadError")
                        }
                    } else {
                        modelLoadError = "模型下载后文件仍然缺失"
                        Log.e(TAG, "[Init] $modelLoadError")
                    }
                } catch (e: Exception) {
                    modelLoadError = "模型下载/加载失败: ${e.message}"
                    Log.e(TAG, "[Init] 模型准备失败", e)
                    writeCrashLog(e)
                }
                Log.i(TAG, "[Init] SherpaEngine.isReady: ${sherpaEngine.isReady}")
            }

            // 尝试加载说话人嵌入模型（非关键，失败不影响 ASR）
            val speakerModel = java.io.File(modelsDir, "speaker/eres2net.onnx")
            if (speakerModel.exists()) {
                serviceScope.launch {
                    try {
                        sherpaEngine.initSpeakerEmbedding(speakerModel.absolutePath)
                        Log.i(TAG, "[Init] 说话人嵌入模型已加载")
                    } catch (e: Exception) {
                        Log.w(TAG, "[Init] 说话人模型加载失败（非关键）", e)
                    }
                }
            }

            vadProcessor = VadProcessor().apply {
                onSpeechEnd = { segment -> handleSpeechSegment(segment) }
            }

            Log.i(TAG, "[Init] RingBuffer/AudioRecorder/ClusterEngine/VadProcessor: OK")
        } catch (e: Exception) {
            Log.e(TAG, "[Init] Service 初始化崩溃", e)
            writeCrashLog(e)
            stopSelf()
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
        // ★ 第 1 步：立即调 startForeground（满足 Android 5 秒超时要求）
        // 初始状态为 PREPARING，模型就绪后才切 RECORDING
        state = RecordingState.PREPARING
        sendStateEvent(RecordingState.PREPARING)
        try {
            // Android 13+: 检查通知权限
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED
                ) {
                    Log.w(TAG, "缺少通知权限，无法启动前台服务")
                    state = RecordingState.IDLE
                    sendStateEvent(RecordingState.IDLE)
                    stopSelf()
                    return
                }
            }
            startForeground(
                TranscriptionNotification.NOTIFICATION_ID_VALUE,
                TranscriptionNotification.build(this, RecordingState.PREPARING, "准备中...")
            )
        } catch (e: Exception) {
            Log.e(TAG, "startForeground 失败", e)
            state = RecordingState.IDLE
            sendStateEvent(RecordingState.IDLE)
            stopSelf()
            return
        }

        // ★ 第 2 步：后台协程中等待模型就绪，然后启动录音
        processingJob = serviceScope.launch {
            try {
                // 等待模型加载（含下载进度更新通知栏）
                val modelReady = waitForModel()
                if (!modelReady) {
                    Log.w(TAG, "模型未就绪: ${modelLoadError ?: "超时"}")
                    sendModelErrorBroadcast(modelLoadError ?: "模型加载超时")
                    state = RecordingState.IDLE
                    sendStateEvent(RecordingState.IDLE)
                    withContext(Dispatchers.Main) { stopSelf() }
                    return@launch
                }

                // 模型就绪 → 切到录音中
                state = RecordingState.RECORDING
                sendStateEvent(RecordingState.RECORDING)
                updateNotification("录音中", RecordingState.RECORDING)

                // 启动录音
                if (!audioRecorder.start()) {
                    Log.e(TAG, "录音器启动失败")
                    updateNotification("录音器启动失败")
                    state = RecordingState.IDLE
                    sendStateEvent(RecordingState.IDLE)
                    withContext(Dispatchers.Main) { stopSelf() }
                    return@launch
                }

                // 加载已知说话人
                clusterEngine.loadProfiles()

                // 主处理循环
                val frame = ShortArray(4096)
                while (isActive && state == RecordingState.RECORDING) {
                    val n = ringBuffer.read(frame)
                    if (n > 0) {
                        vadProcessor.processFrame(
                            if (n == frame.size) frame else frame.copyOf(n)
                        )
                    } else {
                        delay(50)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "处理循环异常", e)
            }
        }

        Log.i(TAG, "录音和转写已启动")
    }

    /** 等待模型就绪（含下载，最多 2 分钟） */
    private suspend fun waitForModel(): Boolean {
        val startTime = System.currentTimeMillis()
        while (!sherpaEngine.isReady && modelLoadError == null) {
            if (System.currentTimeMillis() - startTime > MODEL_LOAD_TIMEOUT_MS) {
                modelLoadError = "模型加载超时 (${MODEL_LOAD_TIMEOUT_MS / 1000}s)"
                return false
            }
            // 通知栏显示下载进度
            if (downloadProgress in 0..100) {
                updateNotification("正在下载 ASR 模型... $downloadProgress%")
            } else if (downloadProgress == -1 && !sherpaEngine.isReady) {
                updateNotification("正在加载模型...")
            }
            delay(200)
        }
        return sherpaEngine.isReady
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

    private fun updateNotification(text: String, overrideState: RecordingState? = null) {
        val notification = TranscriptionNotification.build(this, overrideState ?: state, text)
        val manager = getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager
        manager.notify(TranscriptionNotification.NOTIFICATION_ID_VALUE, notification)
    }

    private fun sendStateEvent(s: RecordingState) {
        try {
            TranscriptionEventBus.emitState(s)
        } catch (_: Exception) {}
    }

    private fun sendModelErrorBroadcast(error: String) {
        try {
            val intent = Intent(ACTION_MODEL_ERROR).apply {
                putExtra(EXTRA_ERROR_MSG, error)
                setPackage(packageName)
            }
            sendBroadcast(intent)
        } catch (_: Exception) {}
    }

    /** 将崩溃信息写入文件，方便用户反馈 */
    private fun writeCrashLog(e: Exception) {
        try {
            val crashLog = java.io.File(filesDir, "crash_oncreate.txt")
            crashLog.writeText(
                "${e.javaClass.name}: ${e.message}\n${e.stackTraceToString()}"
            )
        } catch (_: Exception) {}
    }

    private fun pause() {
        if (!state.canPause()) return
        state = RecordingState.PAUSED
        sendStateEvent(RecordingState.PAUSED)
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
        modelLoadJob?.cancel()
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

// ═══════════════════════════════════════════════
// Event bus — 线程安全版本
// ═══════════════════════════════════════════════
object TranscriptionEventBus {
    private val lineListeners = mutableListOf<(TranscriptionLine) -> Unit>()
    private val stateListeners = mutableListOf<(RecordingState) -> Unit>()

    // ── 转写行事件 ──

    @Synchronized
    fun emit(line: TranscriptionLine) {
        lineListeners.toList().forEach { it(line) }
    }

    @Synchronized
    fun listen(listener: (TranscriptionLine) -> Unit) {
        lineListeners.add(listener)
    }

    @Synchronized
    fun removeLineListener(listener: (TranscriptionLine) -> Unit) {
        lineListeners.remove(listener)
    }

    // ── 状态事件 ──

    @Synchronized
    fun emitState(state: RecordingState) {
        stateListeners.toList().forEach { it(state) }
    }

    @Synchronized
    fun listenState(listener: (RecordingState) -> Unit) {
        stateListeners.add(listener)
    }

    @Synchronized
    fun removeStateListener(listener: (RecordingState) -> Unit) {
        stateListeners.remove(listener)
    }

    // ── 清理 ──

    @Synchronized
    fun clear() {
        lineListeners.clear()
        stateListeners.clear()
    }
}

data class TranscriptionLine(
    val speakerId: String,
    val speakerLabel: String,
    val text: String,
    val isNewSpeaker: Boolean,
    val timestamp: Long
)
