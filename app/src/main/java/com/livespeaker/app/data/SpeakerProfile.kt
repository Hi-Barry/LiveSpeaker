package com.livespeaker.app.data

import androidx.room.*
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * 说话人档案 (Room Entity)
 *
 * 存储说话人 id、用户命名的标签、声纹质心向量
 */
@Entity(tableName = "speaker_profiles")
@TypeConverters(Converters::class)
data class SpeakerProfile(
    @PrimaryKey
    val id: String,

    /** 用户命名的标签, e.g. "张三" */
    val label: String,

    /** 声纹质心向量 (256维 x-vector), 存储为 ByteArray */
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

/**
 * Room 类型转换器。
 * FloatArray ↔ ByteArray (Room 支持 ByteArray 的原生存储)
 */
class Converters {

    @TypeConverter
    fun fromFloatArray(array: FloatArray): ByteArray {
        val buffer = ByteBuffer.allocate(array.size * 4)
        buffer.order(ByteOrder.LITTLE_ENDIAN)
        array.forEach { buffer.putFloat(it) }
        return buffer.array()
    }

    @TypeConverter
    fun toFloatArray(bytes: ByteArray): FloatArray {
        val buffer = ByteBuffer.wrap(bytes)
        buffer.order(ByteOrder.LITTLE_ENDIAN)
        val array = FloatArray(bytes.size / 4)
        for (i in array.indices) {
            array[i] = buffer.float
        }
        return array
    }
}
