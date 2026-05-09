package com.livespeaker.app.audio

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import kotlinx.coroutines.*
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 麦克风录音器。
 * 配置: 16kHz / Mono / PCM 16-bit / VOICE_RECOGNITION 源
 *
 * 使用 AudioSource.VOICE_RECOGNITION 而非 MIC:
 * - 系统会对音频做降噪优化
 * - 不会影响其他 App 同时使用麦克风
 */
class AudioRecorder(
    private val ringBuffer: RingBuffer
) {
    companion object {
        private const val TAG = "AudioRecorder"
        const val SAMPLE_RATE = 16000
        const val CHANNEL = AudioFormat.CHANNEL_IN_MONO
        const val ENCODING = AudioFormat.ENCODING_PCM_16BIT
        private const val BUFFER_FRAMES = 4096 // ~256ms @ 16kHz
    }

    private var audioRecord: AudioRecord? = null
    private val isRecording = AtomicBoolean(false)
    private var readJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    /** 当前是否正在录音 */
    val isActive: Boolean get() = isRecording.get()

    /** 启动录音 */
    fun start() {
        if (isRecording.getAndSet(true)) return

        val minBufferSize = AudioRecord.getMinBufferSize(
            SAMPLE_RATE, CHANNEL, ENCODING
        )
        val bufferSize = maxOf(minBufferSize, BUFFER_FRAMES * 2)

        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            SAMPLE_RATE,
            CHANNEL,
            ENCODING,
            bufferSize
        ).also {
            if (it.state != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "AudioRecord 初始化失败")
                isRecording.set(false)
                throw IllegalStateException("AudioRecord init failed")
            }
            it.startRecording()
        }

        readJob = scope.launch {
            val buffer = ShortArray(BUFFER_FRAMES)
            while (isActive) {
                val n = audioRecord?.read(buffer, 0, buffer.size) ?: -1
                if (n > 0) {
                    ringBuffer.write(buffer, 0, n)
                } else if (n < 0) {
                    Log.e(TAG, "AudioRecord read error: $n")
                    break
                }
            }
        }
        Log.i(TAG, "录音已启动")
    }

    /** 停止录音 */
    fun stop() {
        if (!isRecording.getAndSet(false)) return

        readJob?.cancel()
        readJob = null

        audioRecord?.apply {
            try { stop() } catch (_: Exception) {}
            try { release() } catch (_: Exception) {}
        }
        audioRecord = null

        Log.i(TAG, "录音已停止")
    }

    /** 释放所有资源 */
    fun release() {
        stop()
        scope.cancel()
    }
}
