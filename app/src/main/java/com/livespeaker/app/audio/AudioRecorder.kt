package com.livespeaker.app.audio

import android.media.MediaMetadataRetriever
import android.media.MediaRecorder
import android.os.Handler
import android.os.Looper
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 录音器 v2 — 基于 MediaRecorder，支持 60 秒自动切割。
 *
 * 设计:
 * - 每个片段独立文件: recording_20260511_132045_1.m4a
 * - setMaxDuration(60000) 监听 MEDIA_RECORDER_INFO_MAX_DURATION_REACHED
 * - pause()/resume() API 26+ 原生支持
 * - 通过 StateFlow 向上层发射状态
 */
class AudioRecorder(private val outputDir: File) {

    companion object {
        private const val TAG = "AudioRecorder"
        const val SEGMENT_DURATION_MS = 60_000L // 1 分钟
        private val DATE_FMT = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
    }

    enum class State { IDLE, RECORDING, PAUSED }

    data class Segment(
        val file: File,
        val index: Int,
        val durationMs: Long,
        val timestamp: Long
    )

    private var mediaRecorder: MediaRecorder? = null
    private var currentSegmentIndex = 0
    private var currentFile: File? = null
    private var sessionStartTime: Long = 0L
    private var segmentStartMs: Long = 0L

    private val handler = Handler(Looper.getMainLooper())
    private var durationRunnable: Runnable? = null

    val state = MutableStateFlow(State.IDLE)
    val currentDuration = MutableStateFlow(0L)

    private val _segments = MutableStateFlow<List<Segment>>(emptyList())
    val segments: StateFlow<List<Segment>> = _segments

    init {
        loadExistingSegments()
    }

    /**
     * 开始录音。自动创建第一个片段。
     * 历史录音文件会被加载到列表中，不会被清除。
     * @return true 启动成功
     */
    fun start(): Boolean {
        if (state.value != State.IDLE) return true

        return try {
            loadExistingSegments()
            currentSegmentIndex = _segments.value.size
            sessionStartTime = System.currentTimeMillis()
            startNewSegment()
            true
        } catch (e: Exception) {
            Log.e(TAG, "录音启动失败: ${e.message}", e)
            state.value = State.IDLE
            false
        }
    }

    /** 暂停录音 (回放时调用) */
    fun pause() {
        if (state.value != State.RECORDING) return
        try {
            mediaRecorder?.pause()
            state.value = State.PAUSED
            stopDurationTimer()
            Log.i(TAG, "录音已暂停")
        } catch (e: Exception) {
            Log.e(TAG, "暂停失败: ${e.message}", e)
        }
    }

    /** 恢复录音 */
    fun resume() {
        if (state.value != State.PAUSED) return
        try {
            mediaRecorder?.resume()
            state.value = State.RECORDING
            startDurationTimer()
            Log.i(TAG, "录音已恢复")
        } catch (e: Exception) {
            Log.e(TAG, "恢复失败: ${e.message}", e)
        }
    }

    /** 停止录音，释放当前 MediaRecorder */
    fun stop() {
        val wasRecording = state.value == State.RECORDING || state.value == State.PAUSED
        stopDurationTimer()
        try {
            mediaRecorder?.apply {
                try { stop() } catch (_: Exception) {}
                try { release() } catch (_: Exception) {}
            }
        } catch (_: Exception) {}
        mediaRecorder = null
        currentFile = null
        state.value = State.IDLE
        if (wasRecording) Log.i(TAG, "录音已停止 (共 ${_segments.value.size} 个片段)")
    }

    /** 释放所有资源 */
    fun release() {
        stop()
        handler.removeCallbacksAndMessages(null)
    }

    /**
     * 从磁盘扫描已有录音文件，恢复片段列表。
     */
    private fun loadExistingSegments() {
        val files = outputDir.listFiles()
            ?.filter { it.extension == "m4a" && it.name.startsWith("recording_") }
            ?.sortedBy { it.lastModified() }
            ?: emptyList()

        _segments.value = files.mapIndexed { i, file ->
            val idx = file.nameWithoutExtension.split("_").lastOrNull()?.toIntOrNull() ?: (i + 1)
            val duration = getAudioDuration(file)
            Segment(
                file = file,
                index = idx,
                durationMs = duration,
                timestamp = file.lastModified()
            )
        }
    }

    // ─── 内部逻辑 ───

    /**
     * 用 MediaMetadataRetriever 从 M4A/AAC 容器元数据中读取真实时长。
     *
     * 之前用 file.length() * 8 / (sampleRate * bitDepth) 反算时长是错误的：
     * M4A 是 AAC 有损压缩格式，文件大小与原始 PCM 数据量无线性关系，
     * 导致 1 分钟录音显示为 2:02。
     *
     * MediaMetadataRetriever 直接读取容器 header 中的 duration 字段，
     * 由 MediaRecorder.stop() 写入，精确且不受压缩比影响。
     *
     * @return 时长（毫秒），无法获取时返回 0
     */
    private fun getAudioDuration(file: File): Long {
        if (!file.exists() || file.length() == 0L) return 0L
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(file.absolutePath)
            val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            durationStr?.toLongOrNull() ?: 0L
        } catch (e: Exception) {
            Log.w(TAG, "无法读取文件时长: ${file.name}, ${e.message}")
            0L
        } finally {
            try { retriever.release() } catch (_: Exception) {}
        }
    }

    private fun startNewSegment() {
        val now = System.currentTimeMillis()
        val datePart = DATE_FMT.format(Date(now))
        currentSegmentIndex++
        val file = File(outputDir, "recording_${datePart}_$currentSegmentIndex.m4a")

        // 确保目录存在
        outputDir.mkdirs()

        segmentStartMs = now

        mediaRecorder = MediaRecorder().apply {
            setAudioSource(MediaRecorder.AudioSource.VOICE_RECOGNITION)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setAudioSamplingRate(16000)
            setAudioEncodingBitRate(64000)
            setOutputFile(file.absolutePath)
            setMaxDuration(SEGMENT_DURATION_MS.toInt())

            setOnInfoListener { mr, what, _ ->
                when (what) {
                    MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED -> {
                        Log.i(TAG, "片段 #$currentSegmentIndex 达到最大时长，自动切割")
                        onSegmentComplete(file)
                    }
                }
            }

            setOnErrorListener { mr, what, extra ->
                Log.e(TAG, "MediaRecorder 错误: what=$what extra=$extra")
                state.value = State.IDLE
            }

            prepare()
            start()
        }

        currentFile = file
        state.value = State.RECORDING
        startDurationTimer()
        Log.i(TAG, "新片段 #$currentSegmentIndex → ${file.name}")
    }

    private fun onSegmentComplete(file: File) {
        val now = System.currentTimeMillis()

        // 停止当前片段
        stopDurationTimer()
        try {
            mediaRecorder?.apply {
                try { stop() } catch (_: Exception) {}
                try { release() } catch (_: Exception) {}
            }
        } catch (_: Exception) {}
        mediaRecorder = null

        // 记录已完成的片段（用文件写入完成后的真实时长和完成时间）
        val duration = if (file.exists()) getAudioDuration(file) else SEGMENT_DURATION_MS
        val segment = Segment(
            file = file,
            index = currentSegmentIndex,
            durationMs = duration,
            timestamp = now
        )
        _segments.value = _segments.value + segment

        // 如果用户没有手动停止，自动开始下一个片段
        if (state.value == State.RECORDING) {
            handler.postDelayed({
                try {
                    startNewSegment()
                } catch (e: Exception) {
                    Log.e(TAG, "自动切割失败: ${e.message}", e)
                    state.value = State.IDLE
                }
            }, 100) // 短暂延迟防竞态
        }
    }

    private fun startDurationTimer() {
        stopDurationTimer()
        val startMs = System.currentTimeMillis()
        durationRunnable = object : Runnable {
            override fun run() {
                if (state.value == State.RECORDING) {
                    currentDuration.value = System.currentTimeMillis() - startMs
                    handler.postDelayed(this, 200)
                }
            }
        }
        handler.post(durationRunnable!!)
    }

    private fun stopDurationTimer() {
        durationRunnable?.let { handler.removeCallbacks(it) }
        durationRunnable = null
        currentDuration.value = 0L
    }

    /**
     * 获取存储目录
     */
    fun getOutputDir(): File = outputDir
}
