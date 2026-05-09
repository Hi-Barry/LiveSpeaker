package com.livespeaker.app.diarization

import android.util.Log
import com.livespeaker.app.data.SpeakerProfile
import com.livespeaker.app.data.SpeakerRepository
import kotlinx.coroutines.*
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * 在线说话人聚类引擎。
 *
 * 算法: 在线余弦相似度聚类 + 移动平均质心更新
 *
 * 每当 VAD 检测到一个完整的语音段:
 *   1. 提取说话人嵌入向量 (x-vector)
 *   2. 与所有已知说话人质心比较余弦相似度
 *   3. 相似度 > threshold → 归入该说话人
 *   4. 否则 → 创建新说话人
 *   5. 更新质心 (移动平均)
 */
class ClusterEngine(
    private val repository: SpeakerRepository,
    private val similarityThreshold: Float = 0.65f
) {
    companion object {
        private const val TAG = "ClusterEngine"
        private const val ALPHA = 0.3f // 质心更新速率
    }

    // 内存中的质心缓存 (说话人ID → 质心向量)
    private val centroids = ConcurrentHashMap<String, FloatArray>()

    // 已知说话人的 Vector → ID 映射 (用于快速查找)
    private val labelMap = ConcurrentHashMap<String, String>()

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    /** 从数据库加载已知说话人 */
    suspend fun loadProfiles() = withContext(Dispatchers.IO) {
        val profiles = repository.getAllProfiles()
        profiles.forEach { profile ->
            centroids[profile.id] = profile.centroid
            if (profile.label.isNotBlank()) {
                labelMap[profile.label] = profile.id
            }
        }
        Log.i(TAG, "已加载 ${profiles.size} 个说话人档案")
    }

    /**
     * 识别或创建说话人。
     *
     * @param embedding 256维 x-vector
     * @return (speakerId, speakerLabel, isNew)
     */
    suspend fun identifyOrCreate(
        embedding: FloatArray
    ): ClusterResult = withContext(Dispatchers.IO) {
        if (centroids.isEmpty()) {
            return@withContext createNewSpeaker(embedding, "speaker_1")
        }

        // 找到最相似的已知说话人
        var bestId: String? = null
        var bestSim = -1f

        for ((id, centroid) in centroids) {
            val sim = VectorUtils.cosineSimilarity(embedding, centroid)
            if (sim > bestSim) {
                bestSim = sim
                bestId = id
            }
        }

        if (bestSim >= similarityThreshold && bestId != null) {
            // 匹配已知说话人
            val newCentroid = VectorUtils.weightedAverage(
                centroids[bestId]!!, embedding, ALPHA
            )
            centroids[bestId] = newCentroid

            // 异步更新数据库
            repository.updateCentroid(bestId, newCentroid)

            val profile = repository.getProfile(bestId)
            Log.d(TAG, "匹配说话人: ${profile?.label ?: bestId}, sim=$bestSim")
            ClusterResult(
                speakerId = bestId,
                label = profile?.label ?: bestId,
                isNew = false,
                similarity = bestSim
            )
        } else {
            // 新说话人
            val label = "speaker_${centroids.size + 1}"
            Log.d(TAG, "新说话人: $label, bestSim=$bestSim")
            createNewSpeaker(embedding, label)
        }
    }

    private suspend fun createNewSpeaker(
        embedding: FloatArray,
        label: String
    ): ClusterResult {
        val id = UUID.randomUUID().toString()
        centroids[id] = embedding.copyOf()

        val profile = SpeakerProfile(
            id = id,
            label = label,
            centroid = embedding.copyOf(),
            sampleCount = 1,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
        repository.insert(profile)

        return ClusterResult(
            speakerId = id,
            label = label,
            isNew = true,
            similarity = 0f
        )
    }

    /**
     * 更新说话人标签 (用户命名)
     */
    suspend fun updateLabel(speakerId: String, newLabel: String) {
        repository.updateLabel(speakerId, newLabel)
        // 清除旧标签映射
        labelMap.entries.removeIf { it.value == speakerId }
        labelMap[newLabel] = speakerId
    }

    /** 获取所有已知说话人 */
    suspend fun getAllSpeakers(): List<SpeakerProfile> =
        repository.getAllProfiles()

    /** 删除说话人 */
    suspend fun deleteSpeaker(speakerId: String) {
        repository.delete(speakerId)
        centroids.remove(speakerId)
        labelMap.entries.removeIf { it.value == speakerId }
    }

    /** 清空所有数据 */
    suspend fun clearAll() {
        repository.clearAll()
        centroids.clear()
        labelMap.clear()
    }

    /** 当前已知说话人数 */
    val speakerCount: Int get() = centroids.size

    fun release() {
        scope.cancel()
    }
}

data class ClusterResult(
    val speakerId: String,
    val label: String,
    val isNew: Boolean,
    val similarity: Float
)
