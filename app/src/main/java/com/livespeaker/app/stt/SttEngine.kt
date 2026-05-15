package com.livespeaker.app.stt

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

/** SiliconFlow / OpenAI 兼容的 STT API 响应 */
@Serializable
data class SttApiResponse(
    val text: String = "",
    val error: SttApiError? = null
)

@Serializable
data class SttApiError(
    val message: String = "",
    val type: String = ""
)

/**
 * 语音转文字引擎 — OkHttp 封装，调用 OpenAI 兼容的 /v1/audio/transcriptions 端点。
 *
 * 使用 multipart/form-data 上传音频文件，
 * 支持 SiliconFlow / 任何 OpenAI 兼容的 STT API。
 */
class SttEngine(private val settings: SttSettings) {

    companion object {
        private const val TAG = "SttEngine"
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    /**
     * 转录音频文件。
     *
     * @param audioFile 音频文件（M4A/AAC/MP3/WAV）
     * @param segmentIndex 片段编号（用于 TranscriptionResult）
     * @return TranscriptionResult — 成功时 text 非空、error=null，失败时 text=""、error 含错误描述
     */
    suspend fun transcribe(audioFile: File, segmentIndex: Int): TranscriptionResult =
        withContext(Dispatchers.IO) {
            val startTime = System.currentTimeMillis()

            // ── 前置检查 ──
            if (!settings.enabled || settings.apiKey.isBlank()) {
                return@withContext TranscriptionResult(
                    segmentFileName = audioFile.name,
                    segmentIndex = segmentIndex,
                    text = "",
                    audioTimestamp = audioFile.lastModified(),
                    audioDurationMs = 0,
                    processedAt = startTime,
                    model = settings.model,
                    error = "STT 未启用或 API Key 未配置"
                )
            }

            if (!audioFile.exists() || audioFile.length() == 0L) {
                return@withContext TranscriptionResult(
                    segmentFileName = audioFile.name,
                    segmentIndex = segmentIndex,
                    text = "",
                    audioTimestamp = audioFile.lastModified(),
                    audioDurationMs = 0,
                    processedAt = startTime,
                    model = settings.model,
                    error = "音频文件不存在或为空"
                )
            }

            // ── 构建请求 ──
            try {
                val mimeType = when {
                    audioFile.extension.equals("mp3", ignoreCase = true) -> "audio/mpeg"
                    audioFile.extension.equals("wav", ignoreCase = true) -> "audio/wav"
                    else -> "audio/mp4" // m4a/aac
                }

                val requestBody = MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart("model", settings.model)
                    .addFormDataPart(
                        "file",
                        audioFile.name,
                        RequestBody.create(mimeType.toMediaType(), audioFile)
                    )
                    .build()

                val url = "${settings.baseUrl}/v1/audio/transcriptions"
                val request = Request.Builder()
                    .url(url)
                    .header("Authorization", "Bearer ${settings.apiKey}")
                    .post(requestBody)
                    .build()

                Log.i(TAG, "提交转录: ${audioFile.name} → $url (model=${settings.model})")

                val response = client.newCall(request).execute()
                val bodyText = response.body?.string() ?: ""

                if (response.isSuccessful) {
                    val apiResult = TranscriptionResult.json.decodeFromString<SttApiResponse>(bodyText)
                    val textLength = apiResult.text.length
                    Log.i(TAG, "转录成功: ${audioFile.name} → $textLength 字符")
                    TranscriptionResult(
                        segmentFileName = audioFile.name,
                        segmentIndex = segmentIndex,
                        text = apiResult.text,
                        audioTimestamp = audioFile.lastModified(),
                        audioDurationMs = 0,
                        processedAt = System.currentTimeMillis(),
                        model = settings.model
                    )
                } else {
                    Log.e(TAG, "转录失败: HTTP ${response.code} — ${bodyText.take(200)}")
                    TranscriptionResult(
                        segmentFileName = audioFile.name,
                        segmentIndex = segmentIndex,
                        text = "",
                        audioTimestamp = audioFile.lastModified(),
                        audioDurationMs = 0,
                        processedAt = System.currentTimeMillis(),
                        model = settings.model,
                        error = "HTTP ${response.code}: ${bodyText.take(200)}"
                    )
                }
            } catch (e: IOException) {
                Log.e(TAG, "转录网络错误: ${audioFile.name} — ${e.message}", e)
                TranscriptionResult(
                    segmentFileName = audioFile.name,
                    segmentIndex = segmentIndex,
                    text = "",
                    audioTimestamp = audioFile.lastModified(),
                    audioDurationMs = 0,
                    processedAt = System.currentTimeMillis(),
                    model = settings.model,
                    error = "网络错误: ${e.message}"
                )
            }
        }
}
