package com.livespeaker.app.pipeline

import android.content.Context
import android.util.Log
import kotlinx.coroutines.*
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipInputStream

/**
 * 模型下载与版本管理。
 *
 * 首次启动时检查模型文件完整性，缺失则从网络下载。
 * 模型存放路径: context.filesDir/models/
 */
class ModelManager(private val context: Context) {

    companion object {
        private const val TAG = "ModelManager"
        private const val MODELS_DIR = "models"

        // 模型下载 URL (GitHub Releases)
        private const val BASE_URL =
            "https://github.com/k2-fsa/sherpa-onnx/releases/download"

        // 模型文件定义
        data class ModelInfo(
            val name: String,
            val dir: String,
            val archiveUrl: String,
            val archiveName: String,
            val requiredFiles: List<String>
        )

        val MODELS = listOf(
            ModelInfo(
                name = "SenseVoice ASR (int8)",
                dir = "sense-voice",
                archiveUrl = "$BASE_URL/asr-models/" +
                    "sherpa-onnx-sense-voice-zh-en-ja-ko-yue-int8-2024-07-17.tar.bz2",
                archiveName = "sense-voice.tar.bz2",
                requiredFiles = listOf("model.int8.onnx", "tokens.txt")
            ),
            ModelInfo(
                name = "3D-Speaker ERes2Net",
                dir = "speaker",
                archiveUrl = "$BASE_URL/speaker-recongition-models/" +
                    "3dspeaker_speech_eres2net_base_sv_zh-cn_3dspeaker_16k.onnx",
                archiveName = "eres2net.onnx",
                requiredFiles = listOf("eres2net.onnx")
            ),
            ModelInfo(
                name = "GTCRN Denoiser",
                dir = "denoiser",
                archiveUrl = "$BASE_URL/speech-enhancement-models/" +
                    "sherpa-onnx-gtcrn-denoise-2024-07-09.tar.bz2",
                archiveName = "gtcrn.tar.bz2",
                requiredFiles = listOf("gtcrn_simple.onnx")
            ),
            ModelInfo(
                name = "Silero VAD",
                dir = "vad",
                archiveUrl = "$BASE_URL/asr-models/silero_vad.onnx",
                archiveName = "silero_vad.onnx",
                requiredFiles = listOf("silero_vad.onnx")
            )
        )
    }

    /** 模型根目录 */
    val modelsDir: File
        get() = File(context.filesDir, MODELS_DIR).also { it.mkdirs() }

    /** 检查指定模型是否就绪 */
    fun isModelReady(info: ModelInfo): Boolean {
        val dir = File(modelsDir, info.dir)
        return info.requiredFiles.all { File(dir, it).exists() }
    }

    /** 检查所有模型是否就绪 */
    fun areAllModelsReady(): Boolean = MODELS.all { isModelReady(it) }

    /**
     * 下载单个模型 (挂起函数，在后台线程调用)
     *
     * @param info 模型信息
     * @param onProgress 进度回调 (0-100)
     * @throws Exception 下载或解压失败
     */
    suspend fun downloadModel(
        info: ModelInfo,
        onProgress: (Int) -> Unit = {}
    ) = withContext(Dispatchers.IO) {
        val outDir = File(modelsDir, info.dir)
        outDir.mkdirs()

        val archiveFile = File(context.cacheDir, info.archiveName)

        try {
            // 下载
            Log.i(TAG, "下载 ${info.name}: ${info.archiveUrl}")
            downloadFile(info.archiveUrl, archiveFile) { percent ->
                withContext(Dispatchers.Main) { onProgress(percent) }
            }

            // 解压
            if (info.archiveName.endsWith(".tar.bz2") ||
                info.archiveName.endsWith(".tar.gz") ||
                info.archiveName.endsWith(".zip")
            ) {
                extractArchive(archiveFile, outDir)
            } else {
                // 单文件直接移动
                archiveFile.copyTo(
                    File(outDir, info.archiveName), overwrite = true
                )
            }
        } finally {
            archiveFile.delete()
        }

        Log.i(TAG, "${info.name} 下载完成")
    }

    private suspend fun downloadFile(
        urlString: String,
        dest: File,
        onProgress: suspend (Int) -> Unit
    ) {
        val url = URL(urlString)
        val conn = url.openConnection() as HttpURLConnection
        conn.connectTimeout = 30000
        conn.readTimeout = 30000

        // 处理重定向
        conn.instanceFollowRedirects = true

        val totalSize = conn.contentLengthLong

        conn.inputStream.use { input ->
            FileOutputStream(dest).use { output ->
                val buffer = ByteArray(8192)
                var downloaded = 0L
                var bytesRead: Int

                while (input.read(buffer).also { bytesRead = it } != -1) {
                    output.write(buffer, 0, bytesRead)
                    downloaded += bytesRead
                    if (totalSize > 0) {
                        onProgress((downloaded * 100 / totalSize).toInt())
                    }
                }
            }
        }
        conn.disconnect()
    }

    private fun extractArchive(archive: File, outDir: File) {
        when {
            archive.name.endsWith(".zip") -> extractZip(archive, outDir)
            archive.name.endsWith(".tar.bz2") -> extractTarBz2(archive, outDir)
            else -> throw UnsupportedOperationException(
                "不支持的压缩格式: ${archive.name}"
            )
        }
    }

    private fun extractZip(zipFile: File, outDir: File) {
        ZipInputStream(zipFile.inputStream()).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                val file = File(outDir, entry.name)
                if (entry.isDirectory) {
                    file.mkdirs()
                } else {
                    file.parentFile?.mkdirs()
                    FileOutputStream(file).use { fos ->
                        zis.copyTo(fos)
                    }
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
    }

    /**
     * 解压 tar.bz2 (使用系统命令，Android 通常自带 busybox)
     * 备选：Apache Commons Compress
     */
    private fun extractTarBz2(archive: File, outDir: File) {
        // 使用 Android 内置的 toybox 或 busybox
        val process = ProcessBuilder(
            "tar", "xjf", archive.absolutePath, "-C", outDir.absolutePath
        )
            .redirectErrorStream(true)
            .start()

        val exitCode = process.waitFor()
        if (exitCode != 0) {
            val error = process.inputStream.bufferedReader().readText()
            throw RuntimeException("tar 解压失败 (exit=$exitCode): $error")
        }
    }
}
