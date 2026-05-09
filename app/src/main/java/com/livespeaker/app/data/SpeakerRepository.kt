package com.livespeaker.app.data

import com.livespeaker.app.LiveSpeakerApp

/**
 * 说话人数据仓库。
 * 封装 Room DAO 操作，提供简洁的 API。
 */
class SpeakerRepository(private val dao: SpeakerDao) {

    suspend fun getAllProfiles(): List<SpeakerProfile> = dao.getAll()
    suspend fun getProfile(id: String): SpeakerProfile? = dao.getById(id)
    suspend fun insert(profile: SpeakerProfile) = dao.insert(profile)
    suspend fun updateCentroid(id: String, centroid: FloatArray) =
        dao.updateCentroid(id, centroid)
    suspend fun updateLabel(id: String, label: String) =
        dao.updateLabel(id, label)
    suspend fun delete(id: String) = dao.delete(id)
    suspend fun clearAll() = dao.clearAll()

    companion object {
        fun fromApp(): SpeakerRepository {
            val app = LiveSpeakerApp.instance
            return SpeakerRepository(app.database.speakerDao())
        }
    }
}
