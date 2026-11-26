package com.geminidict.app.data.security

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Securely stores Gemini API key and user preferences using Android Keystore AES256-GCM.
 * Includes resilient recovery & fallback if Keystore keys are invalidated (e.g. lock-screen reset
 * or cross-device restore) so the app never crashes on startup.
 */
class SecurePrefsManager(context: Context) {

    val isUsingFallbackPrefs: Boolean
    val securityWarning: String?

    private val prefs: SharedPreferences

    init {
        val appContext = context.applicationContext
        val (resolvedPrefs, fallback, warning) = createPrefsWithRecovery(appContext)
        prefs = resolvedPrefs
        isUsingFallbackPrefs = fallback
        securityWarning = warning
    }

    private fun createPrefsWithRecovery(context: Context): Triple<SharedPreferences, Boolean, String?> {
        return try {
            Triple(buildEncryptedPrefs(context), false, null)
        } catch (_: Exception) {
            // Attempt one clean reset of corrupted encrypted prefs (e.g. after Keystore invalidation)
            try {
                context.deleteSharedPreferences(PREFS_NAME)
                Triple(
                    buildEncryptedPrefs(context),
                    false,
                    "检测到系统密钥库已重置，已自动重建加密存储，请在「设置」中重新填写 Gemini API Key。"
                )
            } catch (e: Exception) {
                Triple(
                    context.getSharedPreferences(FALLBACK_PREFS_NAME, Context.MODE_PRIVATE),
                    true,
                    "当前设备 Keystore 不可用，已自动降级为应用私有存储（${e.javaClass.simpleName}），请在「设置」中重新填写 Gemini API Key。"
                )
            }
        }
    }

    private fun buildEncryptedPrefs(context: Context): SharedPreferences {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        return EncryptedSharedPreferences.create(
            context,
            PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    fun getGeminiApiKey(): String = prefs.getString(KEY_GEMINI_API_KEY, "") ?: ""

    fun setGeminiApiKey(apiKey: String) {
        prefs.edit().putString(KEY_GEMINI_API_KEY, apiKey.trim()).apply()
    }

    fun getGeminiModel(): String =
        prefs.getString(KEY_GEMINI_MODEL, DEFAULT_GEMINI_MODEL) ?: DEFAULT_GEMINI_MODEL

    fun setGeminiModel(model: String) {
        val sanitized = model.trim().ifEmpty { DEFAULT_GEMINI_MODEL }
        prefs.edit().putString(KEY_GEMINI_MODEL, sanitized).apply()
    }

    fun getGeminiBaseUrl(): String =
        prefs.getString(KEY_GEMINI_BASE_URL, DEFAULT_GEMINI_BASE_URL) ?: DEFAULT_GEMINI_BASE_URL

    fun setGeminiBaseUrl(url: String) {
        val sanitized = url.trim().trimEnd('/').ifEmpty { DEFAULT_GEMINI_BASE_URL }
        prefs.edit().putString(KEY_GEMINI_BASE_URL, sanitized).apply()
    }

    fun getGithubMirrorPrefix(): String = prefs.getString(KEY_GITHUB_MIRROR, "") ?: ""

    fun setGithubMirrorPrefix(prefix: String) {
        prefs.edit().putString(KEY_GITHUB_MIRROR, prefix.trim()).apply()
    }

    fun getTtsAccent(): String = prefs.getString(KEY_TTS_ACCENT, ACCENT_US) ?: ACCENT_US

    fun setTtsAccent(accent: String) {
        prefs.edit().putString(KEY_TTS_ACCENT, accent).apply()
    }

    fun getTtsSpeechRate(): Float = prefs.getFloat(KEY_TTS_RATE, 0.95f)

    fun setTtsSpeechRate(rate: Float) {
        prefs.edit().putFloat(KEY_TTS_RATE, rate.coerceIn(0.5f, 1.5f)).apply()
    }

    fun isStarterDictLoaded(): Boolean = prefs.getBoolean(KEY_STARTER_LOADED, false)

    fun setStarterDictLoaded(loaded: Boolean) {
        prefs.edit().putBoolean(KEY_STARTER_LOADED, loaded).apply()
    }

    companion object {
        private const val PREFS_NAME = "geminidict_secure_prefs"
        private const val FALLBACK_PREFS_NAME = "geminidict_fallback_prefs"
        private const val KEY_GEMINI_API_KEY = "gemini_api_key"
        private const val KEY_GEMINI_MODEL = "gemini_model"
        private const val KEY_GEMINI_BASE_URL = "gemini_base_url"
        private const val KEY_GITHUB_MIRROR = "github_mirror_prefix"
        private const val KEY_TTS_ACCENT = "tts_accent"
        private const val KEY_TTS_RATE = "tts_rate"
        private const val KEY_STARTER_LOADED = "starter_dict_loaded"

        const val DEFAULT_GEMINI_MODEL = "gemini-3.5-flash-lite"
        const val DEFAULT_GEMINI_BASE_URL = "https://generativelanguage.googleapis.com"
        const val ACCENT_US = "en-US"
        const val ACCENT_UK = "en-GB"
    }
}
