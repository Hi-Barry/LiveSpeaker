package com.livespeaker.app

import android.content.Context
import android.content.SharedPreferences

/**
 * 应用设置持久化管理。
 *
 * 基于 SharedPreferences，零额外依赖。
 * 键：segment_duration_minutes（默认 1 分钟）
 */
object SettingsManager {

    private const val PREFS_NAME = "livespeaker_settings"
    private const val KEY_SEGMENT_DURATION_MINUTES = "segment_duration_minutes"
    private const val DEFAULT_DURATION_MINUTES = 1

    private lateinit var prefs: SharedPreferences

    fun init(context: Context) {
        prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    /** 切片时间（分钟），默认 1 */
    var segmentDurationMinutes: Int
        get() = prefs.getInt(KEY_SEGMENT_DURATION_MINUTES, DEFAULT_DURATION_MINUTES)
        set(value) = prefs.edit().putInt(KEY_SEGMENT_DURATION_MINUTES, value).apply()

    /** 切片时间（毫秒），方便 AudioRecorder 直接使用 */
    val segmentDurationMs: Long
        get() = segmentDurationMinutes * 60_000L
}
