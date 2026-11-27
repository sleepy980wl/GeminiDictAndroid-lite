package com.geminidict.app.tts

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.geminidict.app.data.tts.InstalledNeuralVoice
import com.geminidict.app.data.tts.NeuralVoiceDownloader
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Locale
import java.util.UUID

data class TtsState(
    val isInitialized: Boolean = false,
    val isReady: Boolean = false,
    val isSpeaking: Boolean = false,
    val usingNeuralVoice: Boolean = false,
    val activeVoiceName: String = "系统离线 TTS",
    val installedNeuralVoicesCount: Int = 0,
    val hasOfflineEnglishVoice: Boolean = false,
    val activeAccent: String = "en-US",
    val fallbackNotice: String? = null,
    val errorMessage: String? = null
)

/**
 * Unified offline English speech manager:
 * 1. Prefers installed Sherpa-ONNX Piper VITS neural voice models (human-like, 100% offline).
 * 2. Seamlessly falls back to Android system offline TextToSpeech when no neural model is installed
 *    or if neural synthesis fails.
 */
class OfflineTtsManager(
    context: Context,
    private val voiceDownloader: NeuralVoiceDownloader
) {
    private val appContext = context.applicationContext
    private val neuralEngine = NeuralTtsEngine()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _state = MutableStateFlow(TtsState())
    val state: StateFlow<TtsState> = _state.asStateFlow()

    private val lock = Any()

    @Volatile
    private var systemTts: TextToSpeech? = null
    @Volatile
    private var isInitializingSystemTts = false
    @Volatile
    private var preferNeuralVoice: Boolean = true

    private var savedRate: Float = 0.95f
    private var savedLocale: Locale = Locale.US
    private var pendingSystemSpeak: Triple<String, String?, Float?>? = null
    private var activeNeuralJob: Job? = null

    fun configureDefaults(accentTag: String, rate: Float, preferNeural: Boolean = true) {
        synchronized(lock) {
            savedLocale = if (accentTag == "en-GB") Locale.UK else Locale.US
            savedRate = rate.coerceIn(0.5f, 1.5f)
            preferNeuralVoice = preferNeural
        }
        refreshNeuralStatus()
        if (systemTts != null && _state.value.isInitialized) {
            synchronized(lock) {
                applySystemConfig(savedLocale, savedRate)
            }
        }
    }

    fun refreshNeuralStatus() {
        val installed = voiceDownloader.getInstalledVoices()
        val desiredAccent = if (savedLocale == Locale.UK) "en-GB" else "en-US"
        val best = pickBestNeuralVoice(installed, desiredAccent)
        _state.value = _state.value.copy(
            installedNeuralVoicesCount = installed.size,
            usingNeuralVoice = preferNeuralVoice && best != null,
            activeVoiceName = if (preferNeuralVoice && best != null) {
                "神经语音 · ${best.item.name}"
            } else {
                "系统离线 TTS"
            }
        )
    }

    fun onVoiceUninstalled() {
        neuralEngine.releaseModel()
        refreshNeuralStatus()
    }

    fun ensureInitialized() {
        refreshNeuralStatus()
        synchronized(lock) {
            if (systemTts != null || isInitializingSystemTts) return
            isInitializingSystemTts = true
            systemTts = TextToSpeech(appContext) { status ->
                synchronized(lock) {
                    isInitializingSystemTts = false
                    if (status == TextToSpeech.SUCCESS) {
                        applySystemConfig(savedLocale, savedRate)
                        val pending = pendingSystemSpeak
                        pendingSystemSpeak = null
                        if (pending != null && _state.value.isReady) {
                            speakWithSystemInternal(pending.first, pending.second, pending.third)
                        }
                    } else {
                        pendingSystemSpeak = null
                        _state.value = _state.value.copy(
                            isInitialized = true,
                            isReady = false,
                            errorMessage = "系统语音引擎初始化失败，建议在「词库」页下载离线神经语音包。"
                        )
                    }
                }
            }
        }
    }

    fun speakEnglish(
        text: String,
        accentOverride: String? = null,
        rateOverride: Float? = null
    ): String? {
        val cleaned = text.trim()
        if (cleaned.isEmpty()) return null

        val targetAccent = accentOverride ?: if (savedLocale == Locale.UK) "en-GB" else "en-US"
        val targetRate = (rateOverride ?: savedRate).coerceIn(0.5f, 1.5f)

        // 1. Try installed Sherpa-ONNX neural voice first when preferred
        if (preferNeuralVoice) {
            val installed = voiceDownloader.getInstalledVoices()
            val selectedVoice = pickBestNeuralVoice(installed, targetAccent)
            if (selectedVoice != null) {
                stop()
                val fallbackMsg = if (selectedVoice.item.accent != targetAccent) {
                    "未下载 $targetAccent 神经语音包，已使用已安装的 ${selectedVoice.item.name} 朗读。"
                } else {
                    null
                }
                activeNeuralJob = scope.launch {
                    try {
                        _state.value = _state.value.copy(
                            isSpeaking = true,
                            usingNeuralVoice = true,
                            activeVoiceName = "神经语音 · ${selectedVoice.item.name}",
                            activeAccent = selectedVoice.item.accent,
                            fallbackNotice = fallbackMsg,
                            errorMessage = null
                        )
                        neuralEngine.synthesizeAndPlay(selectedVoice, cleaned, targetRate)
                    } catch (_: CancellationException) {
                        // Interrupted by a newer tap
                    } catch (e: Throwable) {
                        _state.value = _state.value.copy(
                            usingNeuralVoice = false,
                            errorMessage = "神经语音合成失败（${e.message}），已自动回退系统 TTS。"
                        )
                        speakWithSystemFallback(cleaned, accentOverride, rateOverride)
                    } finally {
                        _state.value = _state.value.copy(isSpeaking = false)
                    }
                }
                return null
            }
        }

        // 2. Fall back to Android system offline TTS
        return speakWithSystemFallback(cleaned, accentOverride, rateOverride)
    }

    private fun speakWithSystemFallback(
        cleaned: String,
        accentOverride: String?,
        rateOverride: Float?
    ): String? {
        synchronized(lock) {
            if (systemTts == null || (!_state.value.isInitialized && isInitializingSystemTts)) {
                pendingSystemSpeak = Triple(cleaned, accentOverride, rateOverride)
                ensureInitialized()
                return null
            }
            if (!_state.value.isReady) {
                return _state.value.errorMessage ?: "语音引擎尚未就绪，可在「词库」页下载离线神经语音包。"
            }
            speakWithSystemInternal(cleaned, accentOverride, rateOverride)
            return null
        }
    }

    private fun pickBestNeuralVoice(
        installed: List<InstalledNeuralVoice>,
        desiredAccent: String
    ): InstalledNeuralVoice? {
        if (installed.isEmpty()) return null
        // Prefer medium-quality matching accent, then any matching accent, then any installed voice
        val matching = installed.filter { it.item.accent == desiredAccent }
        return matching.firstOrNull { it.item.id.contains("medium") }
            ?: matching.firstOrNull()
            ?: installed.firstOrNull { it.item.id.contains("medium") }
            ?: installed.first()
    }

    private fun applySystemConfig(requestedLocale: Locale, rate: Float) {
        val engine = systemTts ?: return
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
            "未检测到 $requestedTag 系统离线语音包，已自动回退至 $actualAccent 离线发音。"
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
                    errorMessage = "系统离线朗读出错 (code=$errorCode)，建议在「词库」页下载离线神经语音包。"
                )
            }
        })

        val ready = availability != TextToSpeech.LANG_MISSING_DATA &&
            availability != TextToSpeech.LANG_NOT_SUPPORTED
        _state.value = _state.value.copy(
            isInitialized = true,
            isReady = ready,
            hasOfflineEnglishVoice = chosenVoice != null,
            activeAccent = actualAccent,
            fallbackNotice = fallbackNotice,
            errorMessage = if (!ready && _state.value.installedNeuralVoicesCount == 0) {
                "未检测到系统英文离线语音包，建议在「词库」页下载离线神经语音包。"
            } else {
                null
            }
        )
    }

    private fun speakWithSystemInternal(text: String, accentOverride: String?, rateOverride: Float?) {
        val engine = systemTts ?: return
        val locale = when (accentOverride) {
            "en-GB" -> Locale.UK
            "en-US" -> Locale.US
            else -> savedLocale
        }
        val rate = (rateOverride ?: savedRate).coerceIn(0.5f, 1.5f)
        applySystemConfig(locale, rate)
        engine.stop()
        engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, UUID.randomUUID().toString())
    }

    fun stop() {
        activeNeuralJob?.cancel()
        activeNeuralJob = null
        neuralEngine.stopPlayback()
        systemTts?.stop()
        _state.value = _state.value.copy(isSpeaking = false)
    }

    fun shutdown() {
        stop()
        neuralEngine.releaseModel()
        synchronized(lock) {
            pendingSystemSpeak = null
            systemTts?.stop()
            systemTts?.shutdown()
            systemTts = null
            isInitializingSystemTts = false
        }
    }
}
