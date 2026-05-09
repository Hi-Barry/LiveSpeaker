package com.livespeaker.app.data

import androidx.room.*

/**
 * 说话人档案 (Room Entity)
 *
 * 存储说话人 id、用户命名的标签、声纹质心向量
 */
@Entity(tableName = "speaker_profiles")
data class SpeakerProfile(
    @PrimaryKey
    val id: String,

    /** 用户命名的标签, e.g. "张三" */
    val label: String,

    /** 声纹质心向量 (256维 x-vector) */
    val centroid: FloatArray,

    /** 注册样本数 */
    val sampleCount: Int,

    val createdAt: Long,
    val updatedAt: Long
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as SpeakerProfile
        return id == other.id
    }

    override fun hashCode(): Int = id.hashCode()
}
