package com.livespeaker.app.stt

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * 转录结果数据模型。
 *
 * 存储为 JSON sidecar 文件，与音频文件同名：
 * recording_20260515_140000_1.m4a → recording_20260515_140000_1_transcription.json
 */
@Serializable
data class TranscriptionResult(
    val segmentFileName: String,    // 音频文件名 (recording_20260515_140000_1.m4a)
    val segmentIndex: Int,          // 片段编号
    val text: String,               // 转录文本
    val audioTimestamp: Long,       // 录音时间戳 (epoch ms)
    val audioDurationMs: Long,      // 音频时长 (ms)
    val processedAt: Long,          // 转录完成时间 (epoch ms)
    val model: String,              // 使用的模型名
    val error: String? = null       // 错误信息（成功时为 null）
) {
    companion object {
        val json = Json {
            ignoreUnknownKeys = true
            prettyPrint = true
        }

        /** 根据音频文件名生成 sidecar JSON 文件名 */
        fun sidecarFileName(audioFileName: String): String {
            return audioFileName.removeSuffix(".m4a") + "_transcription.json"
        }
    }
}
