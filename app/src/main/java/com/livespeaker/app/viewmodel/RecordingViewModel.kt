package com.livespeaker.app.viewmodel

import android.app.Application
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
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
        .let { flow ->
            kotlinx.coroutines.flow.flow {
                flow.collect { emit(it == AudioRecorder.State.PAUSED) }
            }
        }.stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5000), false)

    val isPlaying = MutableStateFlow(false)

    val segments: StateFlow<List<Segment>> = recorder.segments

    val currentSegmentDuration = recorder.currentDuration

    val playingSegment = MutableStateFlow<Segment?>(null)

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
            // 点击正在播放的项 → 停止播放
            stopPlayback()
            return
        }

        // 如果正在播放其他 → 先停
        if (isPlaying.value) {
            stopPlayback()
        }

        // 如果正在录音 → 暂停录音（记住是自动暂停）
        if (recorder.state.value == AudioRecorder.State.RECORDING) {
            wasRecordingBeforePlayback = true
            recorder.pause()
        } else {
            wasRecordingBeforePlayback = false
        }

        // 开始播放
        startPlaying(segment)
    }

    fun stopPlayback() {
        player?.stop()
        player?.clearMediaItems()
        isPlaying.value = false
        playingSegment.value = null

        // 如果之前因为播放暂停了录音 → 恢复
        if (wasRecordingBeforePlayback && recorder.state.value == AudioRecorder.State.PAUSED) {
            wasRecordingBeforePlayback = false
            recorder.resume()
        }
    }

    private fun startPlaying(segment: Segment) {
        val ctx = getApplication<Application>()

        if (player == null) {
            player = ExoPlayer.Builder(ctx).build().apply {
                addListener(object : Player.Listener {
                    override fun onPlaybackStateChanged(playbackState: Int) {
                        if (playbackState == Player.STATE_ENDED) {
                            // 播放结束 → 恢复录音
                            stopPlayback()
                        }
                    }

                    override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                        isPlaying.value = false
                        playingSegment.value = null
                        stopPlayback()
                    }
                })
            }
        }

        val mediaItem = MediaItem.fromUri(segment.file.toURI().toString())
        player?.apply {
            setMediaItem(mediaItem)
            prepare()
            play()
        }

        isPlaying.value = true
        playingSegment.value = segment
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
