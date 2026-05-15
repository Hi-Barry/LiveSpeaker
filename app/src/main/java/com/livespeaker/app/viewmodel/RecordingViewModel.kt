package com.livespeaker.app.viewmodel

import android.app.Application
import android.content.Intent
import android.content.pm.PackageManager
import android.Manifest
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.livespeaker.app.audio.AudioRecorder
import com.livespeaker.app.audio.AudioRecorder.Segment
import com.livespeaker.app.audio.RecordingService
import com.livespeaker.app.stt.SttConfig
import com.livespeaker.app.stt.SttEngine
import com.livespeaker.app.stt.SttSettings
import com.livespeaker.app.stt.TranscriptionResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.isActive
import java.io.File

/**
 * 录音/回放状态管理 ViewModel。
 *
 * 状态机规则:
 * - 录音中点击播放 → 暂停录音 → 开始播放 → 播放结束恢复录音
 * - 播放中不能开始录音
 * - 手动暂停的录音不会因播放结束而自动恢复
 */
class RecordingViewModel(application: Application) : AndroidViewModel(application) {

    private val outputDir = File(application.filesDir, "recordings")

    val recorder = AudioRecorder(outputDir)

    // 播放器
    private var player: ExoPlayer? = null

    // ─── 状态 ───

    val hasPermission = MutableStateFlow(
        ContextCompat.checkSelfPermission(application, Manifest.permission.RECORD_AUDIO) ==
        PackageManager.PERMISSION_GRANTED
    )

    val isRecording: StateFlow<Boolean> = combine(
        recorder.state, recorder.currentDuration
    ) { state, _ -> state == AudioRecorder.State.RECORDING }
        .stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5000), false)

    val isPaused: StateFlow<Boolean> = recorder.state
        .let { stateFlow ->
            kotlinx.coroutines.flow.flow {
                stateFlow.collect { emit(it == AudioRecorder.State.PAUSED) }
            }
        }.stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5000), false)

    val isPlaying = MutableStateFlow(false)

    val segments: StateFlow<List<Segment>> = recorder.segments

    val currentSegmentDuration = recorder.currentDuration

    val playingSegment = MutableStateFlow<Segment?>(null)

    val playbackPosition = MutableStateFlow(0L)
    val playbackDuration = MutableStateFlow(1L)

    private var progressJob: kotlinx.coroutines.Job? = null

    // 是否因为播放而自动暂停了录音
    private var wasRecordingBeforePlayback = false

    // ─── STT 相关 ───

    private val sttConfig = SttConfig(application)
    private var sttEngine: SttEngine? = null

    /** 已处理的片段文件名集合（防止重复转录） */
    private val processedSegments = mutableSetOf<String>()

    /** 转录结果列表 */
    private val _transcriptions = MutableStateFlow<List<TranscriptionResult>>(emptyList())
    val transcriptions: StateFlow<List<TranscriptionResult>> = _transcriptions

    /** STT 是否正在处理 */
    private val _isSttProcessing = MutableStateFlow(false)
    val isSttProcessing: StateFlow<Boolean> = _isSttProcessing

    init {
        // 确保输出目录存在
        outputDir.mkdirs()
        // 加载已有转录结果
        loadExistingTranscriptions()
        // 启动片段监听 → 自动触发 STT
        startSttWatcher()
    }

    // ─── 权限 ───

    fun onPermissionGranted() {
        hasPermission.value = true
    }

    // ─── 录音控制 ───

    fun startRecording() {
        if (isPlaying.value) {
            stopPlayback()
        }
        // 启动前台 Service，保证切后台进程不被杀
        val ctx = getApplication<Application>()
        ctx.startForegroundService(
            Intent(ctx, RecordingService::class.java)
        )
        viewModelScope.launch(Dispatchers.IO) {
            recorder.start()
        }
    }

    fun stopRecording() {
        viewModelScope.launch(Dispatchers.IO) {
            // 如果有正在录制的片段，先完成它
            if (recorder.state.value == AudioRecorder.State.RECORDING ||
                recorder.state.value == AudioRecorder.State.PAUSED
            ) {
                recorder.stop()
            }
        }
        // 停止前台 Service
        val ctx = getApplication<Application>()
        ctx.stopService(Intent(ctx, RecordingService::class.java))
    }

    fun pauseRecording() {
        viewModelScope.launch(Dispatchers.IO) {
            recorder.pause()
        }
    }

    fun resumeRecording() {
        viewModelScope.launch(Dispatchers.IO) {
            if (isPlaying.value) {
                stopPlayback()
            }
            recorder.resume()
        }
    }

    // ─── 回放控制 ───

    fun playSegment(segment: Segment) {
        if (isPlaying.value && playingSegment.value == segment) {
            stopPlayback()
            return
        }
        if (isPlaying.value) {
            stopPlayback()
        }
        if (recorder.state.value == AudioRecorder.State.RECORDING) {
            wasRecordingBeforePlayback = true
            recorder.pause()
        } else {
            wasRecordingBeforePlayback = false
        }
        startPlaying(segment)
    }

    fun stopPlayback() {
        progressJob?.cancel()
        progressJob = null
        player?.stop()
        player?.clearMediaItems()
        isPlaying.value = false
        playingSegment.value = null
        playbackPosition.value = 0L
        playbackDuration.value = 1L
        if (wasRecordingBeforePlayback && recorder.state.value == AudioRecorder.State.PAUSED) {
            wasRecordingBeforePlayback = false
            recorder.resume()
        }
    }

    private fun startPlaying(segment: Segment) {
        val ctx = getApplication<Application>()

        if (player == null) {
            player = ExoPlayer.Builder(ctx).build()
            player!!.addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (playbackState == Player.STATE_ENDED) {
                        stopPlayback()
                    }
                }

                override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                    this@RecordingViewModel.isPlaying.value = false
                    this@RecordingViewModel.playingSegment.value = null
                    stopPlayback()
                }
            })
        }

        val mediaItem = MediaItem.fromUri(segment.file.toURI().toString())
        player?.apply {
            setMediaItem(mediaItem)
            prepare()
            play()
        }

        isPlaying.value = true
        playingSegment.value = segment

        // 启动进度轮询
        progressJob?.cancel()
        progressJob = viewModelScope.launch(Dispatchers.Main) {
            while (isActive && isPlaying.value) {
                val p = player
                if (p != null) {
                    playbackPosition.value = p.currentPosition
                    playbackDuration.value = p.duration.takeIf { it > 0 } ?: 1L
                }
                delay(100)
            }
            playbackPosition.value = 0L
            playbackDuration.value = 1L
        }
    }

    // ─── STT 转录调度 ───

    /**
     * 监听新片段，触发自动转录。
     */
    private fun startSttWatcher() {
        viewModelScope.launch {
            recorder.segments.collect { segments ->
                if (segments.isEmpty()) return@collect

                val settings = sttConfig.settings.first()
                if (!settings.enabled) return@collect

                for (segment in segments) {
                    val fileName = segment.file.name
                    if (fileName in processedSegments) continue

                    processedSegments.add(fileName)
                    transcribeSegment(segment, settings)
                }
            }
        }
    }

    private fun transcribeSegment(segment: Segment, settings: SttSettings) {
        viewModelScope.launch {
            _isSttProcessing.value = true
            try {
                if (sttEngine == null) {
                    sttEngine = SttEngine(settings)
                }
                // 等待文件写入完成（MediaRecorder.stop() 后需要 flush）
                delay(500)

                val result = sttEngine!!.transcribe(segment.file, segment.index)

                // 保存 sidecar JSON
                saveTranscriptionSidecar(result)

                // 更新列表
                _transcriptions.value = _transcriptions.value + result
            } catch (e: Exception) {
                Log.e("RecordingVM", "转录异常: ${segment.file.name}", e)
            } finally {
                _isSttProcessing.value = false
            }
        }
    }

    private fun saveTranscriptionSidecar(result: TranscriptionResult) {
        try {
            val sidecarName = TranscriptionResult.sidecarFileName(result.segmentFileName)
            val sidecarFile = File(outputDir, sidecarName)
            val json = TranscriptionResult.json.encodeToString(
                TranscriptionResult.serializer(), result
            )
            sidecarFile.writeText(json)
        } catch (e: Exception) {
            Log.e("RecordingVM", "保存转录结果失败", e)
        }
    }

    private fun loadExistingTranscriptions() {
        val results = outputDir.listFiles()
            ?.filter { it.name.endsWith("_transcription.json") }
            ?.mapNotNull { file ->
                try {
                    val txt = file.readText()
                    val result = TranscriptionResult.json.decodeFromString<TranscriptionResult>(txt)
                    result
                } catch (e: Exception) {
                    null
                }
            }
            ?.sortedByDescending { it.audioTimestamp }
            ?: emptyList()

        _transcriptions.value = results

        // 标记已处理的片段
        processedSegments.addAll(results.map { it.segmentFileName })
    }

    override fun onCleared() {
        super.onCleared()
        recorder.release()
        player?.release()
        player = null
    }

    /**
     * 将所有录音片段文件列表返回（包括正在录制的）
     */
    fun getAllSegmentFiles(): List<File> {
        return outputDir.listFiles()
            ?.filter { it.extension == "m4a" }
            ?.sortedByDescending { it.lastModified() }
            ?: emptyList()
    }
}
