package com.livespeaker.app.pipeline

import android.util.Log
import java.util.LinkedList

/**
 * 基于能量的语音活动检测器 (VAD)。
 *
 * 简化版实现，用于分割语音段。
 * 正式版可用 sherpa-onnx 内置的 Silero VAD 替换。
 *
 * 参数针对 16kHz 音频优化。
 */
class VadProcessor(
    private val sampleRate: Int = 16000,
    private val speechThreshold: Float = 0.02f,     // 能量阈值
    private val minSpeechMs: Int = 200,              // 最短语音段
    private val maxSilenceMs: Int = 500,             // 最长静音间隔
    private val preSpeechPaddingMs: Int = 100        // 语音段前保留
) {
    companion object {
        private const val TAG = "VadProcessor"
    }

    private val minSpeechSamples = sampleRate * minSpeechMs / 1000
    private val maxSilenceSamples = sampleRate * maxSilenceMs / 1000
    private val paddingSamples = sampleRate * preSpeechPaddingMs / 1000

    // 状态
    private var isSpeech = false
    private var silenceCount = 0
    private var speechCount = 0

    // 当前语音段缓冲
    private val segmentBuffer = LinkedList<Short>()
    private val pendingBuffer = LinkedList<Short>() // 语音段前的 padding

    // 回调
    var onSpeechStart: (() -> Unit)? = null
    var onSpeechEnd: ((ShortArray) -> Unit)? = null

    /** 处理一个音频帧 */
    fun processFrame(frame: ShortArray) {
        val energy = rms(frame)

        if (energy > speechThreshold) {
            // 检测到语音
            silenceCount = 0
            speechCount += frame.size

            if (!isSpeech) {
                speechCount += frame.size
                if (speechCount >= minSpeechSamples) {
                    // 语音段开始
                    isSpeech = true
                    speechCount = 0
                    // 保留 padding
                    segmentBuffer.addAll(pendingBuffer)
                    pendingBuffer.clear()
                    onSpeechStart?.invoke()
                    Log.d(TAG, "语音开始")
                }
            }

            if (isSpeech) {
                frame.forEach { segmentBuffer.add(it) }
            } else {
                // 还没确认是语音，加入 pending
                frame.takeLast(paddingSamples).forEach { pendingBuffer.add(it) }
                while (pendingBuffer.size > paddingSamples) {
                    pendingBuffer.removeFirst()
                }
            }
        } else {
            // 静音
            speechCount = 0
            if (isSpeech) {
                silenceCount += frame.size
                segmentBuffer.addAll(frame.toList())

                if (silenceCount >= maxSilenceSamples) {
                    // 语音段结束
                    endSegment()
                }
            }
        }
    }

    /** 强制结束当前语音段 */
    fun forceEndSegment() {
        if (isSpeech && segmentBuffer.isNotEmpty()) {
            endSegment()
        }
    }

    private fun endSegment() {
        val segment = ShortArray(segmentBuffer.size) { segmentBuffer[it] }
        segmentBuffer.clear()
        isSpeech = false
        silenceCount = 0
        Log.d(TAG, "语音结束, 样本数=${segment.size}")
        onSpeechEnd?.invoke(segment)
    }

    /** 计算均方根能量 */
    private fun rms(samples: ShortArray): Float {
        var sum = 0.0
        for (s in samples) {
            val normalized = s / 32768.0
            sum += normalized * normalized
        }
        return Math.sqrt(sum / samples.size).toFloat()
    }

    fun reset() {
        segmentBuffer.clear()
        pendingBuffer.clear()
        isSpeech = false
        silenceCount = 0
        speechCount = 0
    }
}
