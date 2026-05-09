package com.livespeaker.app.pipeline

import android.content.Context
import android.util.Log
import com.k2fsa.sherpa.onnx.*
import com.livespeaker.app.LiveSpeakerApp
import java.io.File

/**
 * sherpa-onnx 引擎总封装。
 *
 * 统一管理:
 * - GTCRN 降噪 (SpeechEnhancer)
 * - SenseVoice ASR (OfflineRecognizer)
 * - 3D-Speaker 嵌入 (SpeakerEmbeddingExtractor)
 * - Silero VAD (内置)
 */
class SherpaEngine(private val app: LiveSpeakerApp) {

    companion object {
        private const val TAG = "SherpaEngine"
        private const val SAMPLE_RATE = 16000
    }

    private var enhancer: SpeechEnhancer? = null
    private var asr: OfflineRecognizer? = null
    private var embedder: SpeakerEmbeddingExtractor? = null

    val isReady: Boolean
        get() = asr != null && embedder != null

    /** 初始化所有模型 (必须已下载) */
    fun init() {
        val modelsDir = app.modelManager.modelsDir

        // 1. GTCRN 降噪器
        val gtcrnModel = File(modelsDir, "denoiser/gtcrn_simple.onnx")
        if (gtcrnModel.exists()) {
            enhancer = SpeechEnhancer(
                SpeechEnhancerConfig(
                    model = SpeechEnhancerModelConfig(
                        gtcrn = GtcrnModelConfig(
                            model = gtcrnModel.absolutePath
                        )
                    )
                )
            )
            Log.i(TAG, "GTCRN 降噪器已加载")
        } else {
            Log.w(TAG, "GTCRN 模型不存在，跳过降噪")
        }

        // 2. SenseVoice ASR
        val asrModel = File(modelsDir, "sense-voice/model.int8.onnx")
        val tokens = File(modelsDir, "sense-voice/tokens.txt")
        if (asrModel.exists() && tokens.exists()) {
            asr = OfflineRecognizer(
                OfflineRecognizerConfig(
                    featConfig = FeatureExtractorConfig(
                        samplingRate = SAMPLE_RATE,
                        featureDim = 80
                    ),
                    modelConfig = OfflineModelConfig(
                        senseVoice = OfflineSenseVoiceModelConfig(
                            model = asrModel.absolutePath,
                            useInverseTextNormalization = 1,
                            language = "auto"
                        ),
                        tokens = tokens.absolutePath,
                        numThreads = 2,
                        provider = "cpu"
                    )
                )
            )
            Log.i(TAG, "SenseVoice ASR 已加载")
        } else {
            Log.w(TAG, "SenseVoice 模型不存在")
        }

        // 3. 3D-Speaker 嵌入
        val speakerModel = File(modelsDir, "speaker/eres2net.onnx")
        if (speakerModel.exists()) {
            embedder = SpeakerEmbeddingExtractor(
                SpeakerEmbeddingExtractorConfig(
                    model = speakerModel.absolutePath,
                    numThreads = 1,
                    provider = "cpu"
                )
            )
            Log.i(TAG, "3D-Speaker 嵌入模型已加载")
        } else {
            Log.w(TAG, "说话人嵌入模型不存在")
        }
    }

    /** GTCRN 降噪: FloatArray → FloatArray */
    fun denoise(samples: FloatArray): FloatArray {
        val e = enhancer ?: return samples
        return try {
            e.process(samples, SAMPLE_RATE)
        } catch (ex: Exception) {
            Log.e(TAG, "降噪失败", ex)
            samples
        }
    }

    /** SenseVoice ASR: FloatArray → 文本 */
    fun recognize(samples: FloatArray): RecognitionResult {
        val r = asr ?: throw IllegalStateException("ASR 未初始化")

        val stream = r.createStream()
        stream.acceptWaveform(samples, SAMPLE_RATE)
        r.decode(stream)
        val result = r.result(stream)

        return RecognitionResult(
            text = result.text,
            hasPunctuation = true
        )
    }

    /** 提取说话人嵌入: FloatArray → FloatArray(256) */
    fun extractEmbedding(samples: FloatArray): FloatArray {
        val e = embedder ?: throw IllegalStateException("嵌入模型未初始化")
        return e.compute(samples, SAMPLE_RATE)
    }

    /** 释放所有模型 */
    fun release() {
        asr?.release()
        embedder?.release()
        enhancer = null
        asr = null
        embedder = null
        Log.i(TAG, "所有模型已释放")
    }
}

data class RecognitionResult(
    val text: String,
    val hasPunctuation: Boolean = false
)
