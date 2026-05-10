package com.livespeaker.app.pipeline

import android.content.res.AssetManager
import android.util.Log
import com.k2fsa.sherpa.onnx.*
import java.io.File

/**
 * sherpa-onnx 引擎总封装。
 *
 * 统一管理:
 * - SenseVoice ASR (OfflineRecognizer — 模拟流式)
 * - 3D-Speaker 嵌入 (SpeakerEmbeddingExtractor — 如有)
 * - GTCRN 降噪 (SpeechEnhancer — 如有)
 *
 * 注意: sherpa-onnx Kotlin API 构造函数通常需要 AssetManager 作为第一个参数。
 *       具体类名和签名以 sherpa-onnx 实际版本为准，CI 中逐步修正。
 */
class SherpaEngine(private val context: android.content.Context) {

    private val assetManager: AssetManager
        get() = context.assets

    companion object {
        private const val TAG = "SherpaEngine"
        private const val SAMPLE_RATE = 16000
        /** 模型文件至少要有这么大才算有效（防止下载不完整） */
        private const val MIN_MODEL_SIZE_BYTES = 10 * 1024 * 1024L  // 10MB
        private const val MIN_TOKENS_SIZE_BYTES = 100L
    }

    private var asr: OfflineRecognizer? = null
    private var embedder: SpeakerEmbeddingExtractor? = null

    val isReady: Boolean
        get() = asr != null

    /**
     * 初始化 SenseVoice ASR 模型。
     * @return true 表示加载成功
     */
    fun initAsr(modelPath: String, tokensPath: String): Boolean {
        val modelFile = File(modelPath)
        val tokensFile = File(tokensPath)

        // 预检：文件存在性
        if (!modelFile.exists()) {
            Log.e(TAG, "ASR 模型文件不存在: $modelPath")
            return false
        }
        if (!tokensFile.exists()) {
            Log.e(TAG, "tokens 文件不存在: $tokensPath")
            return false
        }

        // 预检：文件大小有效性（防止下载不完整导致 native crash）
        if (modelFile.length() < MIN_MODEL_SIZE_BYTES) {
            Log.e(TAG, "ASR 模型文件过小 (${modelFile.length()} bytes)，可能下载不完整")
            return false
        }
        if (tokensFile.length() < MIN_TOKENS_SIZE_BYTES) {
            Log.e(TAG, "tokens 文件过小 (${tokensFile.length()} bytes)")
            return false
        }

        return try {
            val config = OfflineRecognizerConfig(
                modelConfig = OfflineModelConfig(
                    senseVoice = OfflineSenseVoiceModelConfig(
                        model = modelPath,
                        language = "auto"
                    ),
                    tokens = tokensPath,
                    numThreads = 2,
                    provider = "cpu"
                )
            )
            asr = OfflineRecognizer(assetManager, config)
            Log.i(TAG, "SenseVoice ASR 已加载 (${modelFile.length() / 1024 / 1024}MB)")
            true
        } catch (e: Exception) {
            Log.e(TAG, "ASR 初始化失败: ${e.javaClass.name} — ${e.message}", e)
            false
        }
    }

    /** 初始化说话人嵌入模型 */
    fun initSpeakerEmbedding(modelPath: String) {
        val modelFile = File(modelPath)
        if (!modelFile.exists()) {
            Log.w(TAG, "说话人模型文件不存在: $modelPath")
            return
        }
        embedder = SpeakerEmbeddingExtractor(
            assetManager,
            SpeakerEmbeddingExtractorConfig(
                model = modelPath,
                numThreads = 1,
                provider = "cpu"
            )
        )
        Log.i(TAG, "说话人嵌入模型已加载")
    }

    /**
     * SenseVoice ASR: FloatArray → 文本
     * 每次调用创建新的 stream 并在 finally 中释放，防止内存泄漏。
     */
    fun recognize(samples: FloatArray): String {
        val r = asr ?: throw IllegalStateException("ASR 未初始化")

        val stream = r.createStream()
        return try {
            stream.acceptWaveform(samples, SAMPLE_RATE)
            r.decode(stream)
            val result = r.getResult(stream)
            result.text.ifEmpty { "" }
        } finally {
            try { r.release(stream) } catch (_: Exception) {}
        }
    }

    /** 提取说话人嵌入: FloatArray → FloatArray(256)
     * 注: SpeakerEmbeddingExtractor API 可能接受时长参数而非原始样本。
     * 暂时返回 null，后续适配。 */
    fun extractEmbedding(samples: FloatArray): FloatArray? {
        // TODO: 适配 compute(seconds: Long, sampleRate: Int) 等签名
        return null
    }

    /** 释放所有模型 */
    fun release() {
        asr?.release()
        embedder?.release()
        asr = null
        embedder = null
        Log.i(TAG, "所有模型已释放")
    }
}

data class RecognitionResult(
    val text: String,
    val hasPunctuation: Boolean = false
)
