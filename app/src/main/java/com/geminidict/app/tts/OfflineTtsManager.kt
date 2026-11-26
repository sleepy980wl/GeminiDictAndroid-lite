package com.geminidict.app.tts

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale
import java.util.UUID

data class TtsState(
    val isInitialized: Boolean = false,
    val isReady: Boolean = false,
    val isSpeaking: Boolean = false,
    val hasOfflineEnglishVoice: Boolean = false,
    val activeAccent: String = "en-US",
    val fallbackNotice: String? = null,
    val errorMessage: String? = null
)

/**
 * Lazily-initialized offline-capable English TextToSpeech controller.
 * - Does not block Application.onCreate()
 * - Queues utterances requested during initialization instead of silently dropping them
 * - Accurately reports actual active accent if en-GB offline voice is unavailable and falls back to en-US
 * - Supports temporary accent/rate overrides for Settings preview without mutating saved preferences
 */
class OfflineTtsManager(context: Context) {

    private val appContext = context.applicationContext
    private val _state = MutableStateFlow(TtsState())
    val state: StateFlow<TtsState> = _state.asStateFlow()

    private val lock = Any()

    @Volatile
    private var tts: TextToSpeech? = null
    @Volatile
    private var isInitializing = false

    private var savedRate: Float = 0.95f
    private var savedLocale: Locale = Locale.US
    private var pendingSpeak: Triple<String, String?, Float?>? = null

    fun configureDefaults(accentTag: String, rate: Float) {
        synchronized(lock) {
            savedLocale = if (accentTag == "en-GB") Locale.UK else Locale.US
            savedRate = rate.coerceIn(0.5f, 1.5f)
            if (tts != null && _state.value.isInitialized) {
                applyEngineConfig(savedLocale, savedRate)
            }
        }
    }

    fun ensureInitialized() {
        synchronized(lock) {
            if (tts != null || isInitializing) return
            isInitializing = true
            tts = TextToSpeech(appContext) { status ->
                synchronized(lock) {
                    isInitializing = false
                    if (status == TextToSpeech.SUCCESS) {
                        applyEngineConfig(savedLocale, savedRate)
                        val pending = pendingSpeak
                        pendingSpeak = null
                        if (pending != null && _state.value.isReady) {
                            speakInternal(pending.first, pending.second, pending.third)
                        }
                    } else {
                        pendingSpeak = null
                        _state.value = _state.value.copy(
                            isInitialized = true,
                            isReady = false,
                            errorMessage = "系统语音引擎初始化失败，请在系统设置中检查文字转语音(TTS)引擎。"
                        )
                    }
                }
            }
        }
    }

    private fun applyEngineConfig(requestedLocale: Locale, rate: Float) {
        val engine = tts ?: return
        val availability = engine.setLanguage(requestedLocale)

        val exactOffline = engine.voices?.firstOrNull { v ->
            v.locale.language.equals(requestedLocale.language, ignoreCase = true) &&
                v.locale.country.equals(requestedLocale.country, ignoreCase = true) &&
                !v.isNetworkConnectionRequired
        }
        val anyEnglishOffline = engine.voices?.firstOrNull { v ->
            v.locale.language.equals("en", ignoreCase = true) && !v.isNetworkConnectionRequired
        }

        val chosenVoice = exactOffline ?: anyEnglishOffline
        if (chosenVoice != null) {
            engine.voice = chosenVoice
        }

        val actualAccent = when {
            chosenVoice != null && chosenVoice.locale.country.equals("GB", ignoreCase = true) -> "en-GB"
            chosenVoice != null -> "en-US"
            requestedLocale == Locale.UK -> "en-GB"
            else -> "en-US"
        }

        val requestedTag = if (requestedLocale == Locale.UK) "en-GB" else "en-US"
        val fallbackNotice = if (exactOffline == null && anyEnglishOffline != null && requestedTag != actualAccent) {
            "未检测到 $requestedTag 离线语音包，已自动回退至 $actualAccent 离线发音。"
        } else {
            null
        }

        engine.setSpeechRate(rate)
        engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                _state.value = _state.value.copy(isSpeaking = true)
            }

            override fun onDone(utteranceId: String?) {
                _state.value = _state.value.copy(isSpeaking = false)
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                _state.value = _state.value.copy(isSpeaking = false)
            }

            override fun onError(utteranceId: String?, errorCode: Int) {
                _state.value = _state.value.copy(
                    isSpeaking = false,
                    errorMessage = "离线朗读出错 (code=$errorCode)，请确认已安装系统英文离线语音包。"
                )
            }
        })

        val ready = availability != TextToSpeech.LANG_MISSING_DATA &&
            availability != TextToSpeech.LANG_NOT_SUPPORTED
        _state.value = TtsState(
            isInitialized = true,
            isReady = ready,
            isSpeaking = false,
            hasOfflineEnglishVoice = chosenVoice != null,
            activeAccent = actualAccent,
            fallbackNotice = fallbackNotice,
            errorMessage = if (!ready) "未检测到英文离线语音包，请在系统「设置 -> 文字转语音」中下载英文离线语音数据。" else null
        )
    }

    /**
     * Speaks English text offline. Optional [accentOverride] and [rateOverride] apply only to this
     * utterance without permanently mutating saved preferences.
     * Returns an error message if TTS failed to initialize, or null on success/queued.
     */
    fun speakEnglish(
        text: String,
        accentOverride: String? = null,
        rateOverride: Float? = null
    ): String? {
        val cleaned = text.trim()
        if (cleaned.isEmpty()) return null

        synchronized(lock) {
            if (tts == null || (!_state.value.isInitialized && isInitializing)) {
                pendingSpeak = Triple(cleaned, accentOverride, rateOverride)
                ensureInitialized()
                return null
            }
            if (!_state.value.isReady) {
                return _state.value.errorMessage ?: "语音引擎尚未就绪，请检查系统英文离线语音包。"
            }
            speakInternal(cleaned, accentOverride, rateOverride)
            return null
        }
    }

    private fun speakInternal(text: String, accentOverride: String?, rateOverride: Float?) {
        val engine = tts ?: return
        val locale = when (accentOverride) {
            "en-GB" -> Locale.UK
            "en-US" -> Locale.US
            else -> savedLocale
        }
        val rate = (rateOverride ?: savedRate).coerceIn(0.5f, 1.5f)
        applyEngineConfig(locale, rate)
        engine.stop()
        engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, UUID.randomUUID().toString())
    }

    fun stop() {
        tts?.stop()
        _state.value = _state.value.copy(isSpeaking = false)
    }

    fun shutdown() {
        synchronized(lock) {
            pendingSpeak = null
            tts?.stop()
            tts?.shutdown()
            tts = null
            isInitializing = false
        }
    }
}
