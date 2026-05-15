package com.livespeaker.app.stt

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** 每个 Context 对应一个 DataStore 实例 */
val Context.sttDataStore: DataStore<Preferences> by preferencesDataStore(name = "stt_settings")

/**
 * STT 设置数据类。
 */
data class SttSettings(
    val provider: String = "siliconflow",
    val baseUrl: String = "https://api.siliconflow.cn",
    val apiKey: String = "",
    val model: String = "FunAudioLLM/SenseVoiceSmall",
    val enabled: Boolean = false
)

/**
 * STT 配置管理器 — DataStore Preferences 持久化。
 *
 * 用法：
 *   val config = SttConfig(context)
 *   val settings by config.settings.collectAsState(initial = SttConfig.DEFAULTS)
 */
class SttConfig(private val context: Context) {

    companion object {
        private val KEY_PROVIDER = stringPreferencesKey("stt_provider")
        private val KEY_BASE_URL = stringPreferencesKey("stt_base_url")
        private val KEY_API_KEY = stringPreferencesKey("stt_api_key")
        private val KEY_MODEL = stringPreferencesKey("stt_model")
        private val KEY_ENABLED = booleanPreferencesKey("stt_enabled")

        val DEFAULTS = SttSettings()
    }

    /** 设置变化流 */
    val settings: Flow<SttSettings> = context.sttDataStore.data.map { prefs ->
        SttSettings(
            provider = prefs[KEY_PROVIDER] ?: DEFAULTS.provider,
            baseUrl = prefs[KEY_BASE_URL] ?: DEFAULTS.baseUrl,
            apiKey = prefs[KEY_API_KEY] ?: DEFAULTS.apiKey,
            model = prefs[KEY_MODEL] ?: DEFAULTS.model,
            enabled = prefs[KEY_ENABLED] ?: DEFAULTS.enabled
        )
    }

    // ── 单项更新 ──

    suspend fun updateProvider(value: String) {
        context.sttDataStore.edit { it[KEY_PROVIDER] = value }
    }

    suspend fun updateBaseUrl(value: String) {
        context.sttDataStore.edit { it[KEY_BASE_URL] = value }
    }

    suspend fun updateApiKey(value: String) {
        context.sttDataStore.edit { it[KEY_API_KEY] = value }
    }

    suspend fun updateModel(value: String) {
        context.sttDataStore.edit { it[KEY_MODEL] = value }
    }

    suspend fun setEnabled(enabled: Boolean) {
        context.sttDataStore.edit { it[KEY_ENABLED] = enabled }
    }
}
