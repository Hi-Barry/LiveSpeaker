package com.livespeaker.app.pipeline

import android.content.res.AssetManager
import android.util.Log
import com.k2fsa.sherpa.onnx.*

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
    }

    private var asr: OfflineRecognizer? = null
    private var embedder: SpeakerEmbeddingExtractor? = null

    val isReady: Boolean
        get() = asr != null

    /** 初始化 SenseVoice ASR 模型 (仅同步初始化) */
    fun initAsr(modelPath: String, tokensPath: String) {
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
        Log.i(TAG, "SenseVoice ASR 已加载")
    }

    /** 初始化说话人嵌入模型 */
    fun initSpeakerEmbedding(modelPath: String) {
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

    /** SenseVoice ASR: FloatArray → 文本 */
    fun recognize(samples: FloatArray): String {
        val r = asr ?: throw IllegalStateException("ASR 未初始化")

        val stream = r.createStream()
        stream.acceptWaveform(samples, SAMPLE_RATE)
        r.decode(stream)
        val result = r.getResult(stream)

        return result.text.ifEmpty {
            "" // 无有效语音
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
