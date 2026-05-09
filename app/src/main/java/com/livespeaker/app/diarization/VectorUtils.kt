package com.livespeaker.app.diarization

import kotlin.math.sqrt

/**
 * 向量运算工具类。
 * 用于说话人嵌入向量的相似度计算和聚类。
 */
object VectorUtils {

    /**
     * 余弦相似度: cos(θ) = A·B / (|A| * |B|)
     *
     * @return [-1, 1], 1 = 完全相同方向
     */
    fun cosineSimilarity(a: FloatArray, b: FloatArray): Float {
        require(a.size == b.size) { "向量维度不一致: ${a.size} vs ${b.size}" }

        var dotProduct = 0f
        var normA = 0f
        var normB = 0f

        for (i in a.indices) {
            dotProduct += a[i] * b[i]
            normA += a[i] * a[i]
            normB += b[i] * b[i]
        }

        val denominator = sqrt(normA) * sqrt(normB)
        return if (denominator > 0) dotProduct / denominator else 0f
    }

    /**
     * 加权移动平均更新质心。
     * newCentroid = oldCentroid * (1 - alpha) + newVector * alpha
     */
    fun weightedAverage(
        old: FloatArray,
        new: FloatArray,
        alpha: Float = 0.3f
    ): FloatArray {
        require(old.size == new.size)
        val result = FloatArray(old.size)
        for (i in result.indices) {
            result[i] = old[i] * (1 - alpha) + new[i] * alpha
        }
        return result
    }

    /** 计算向量 L2 范数 */
    fun norm(v: FloatArray): Float {
        var sum = 0f
        for (x in v) sum += x * x
        return sqrt(sum)
    }

    /** 向量归一化 (单位向量) */
    fun normalize(v: FloatArray): FloatArray {
        val n = norm(v)
        if (n == 0f) return v.copyOf()
        return FloatArray(v.size) { v[it] / n }
    }
}
