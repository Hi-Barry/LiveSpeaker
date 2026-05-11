package com.livespeaker.app.viewmodel

import android.app.Application
import android.content.Intent
import android.content.pm.PackageManager
import android.Manifest
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.livespeaker.app.audio.AudioRecorder
import com.livespeaker.app.audio.AudioRecorder.Segment
import com.livespeaker.app.audio.RecordingService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
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

    init {
        // 确保输出目录存在
        outputDir.mkdirs()
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
