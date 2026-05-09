package com.livespeaker.app.data

import androidx.room.*

@Dao
interface SpeakerDao {

    @Query("SELECT * FROM speaker_profiles ORDER BY updatedAt DESC")
    suspend fun getAll(): List<SpeakerProfile>

    @Query("SELECT * FROM speaker_profiles WHERE id = :speakerId")
    suspend fun getById(speakerId: String): SpeakerProfile?

    @Query("SELECT * FROM speaker_profiles WHERE label = :label")
    suspend fun getByLabel(label: String): SpeakerProfile?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(profile: SpeakerProfile)

    @Query("UPDATE speaker_profiles SET centroid = :centroid, updatedAt = :now WHERE id = :speakerId")
    suspend fun updateCentroid(speakerId: String, centroid: FloatArray, now: Long = System.currentTimeMillis())

    @Query("UPDATE speaker_profiles SET label = :label, updatedAt = :now WHERE id = :speakerId")
    suspend fun updateLabel(speakerId: String, label: String, now: Long = System.currentTimeMillis())

    @Query("DELETE FROM speaker_profiles WHERE id = :speakerId")
    suspend fun delete(speakerId: String)

    @Query("DELETE FROM speaker_profiles")
    suspend fun clearAll()
}
