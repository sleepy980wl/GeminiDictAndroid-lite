package com.geminidict.app

import android.app.Application
import com.geminidict.app.data.dict.DictionaryDownloader
import com.geminidict.app.data.local.AppDatabase
import com.geminidict.app.data.remote.GeminiTranslationService
import com.geminidict.app.data.security.SecurePrefsManager
import com.geminidict.app.data.tts.NeuralVoiceDownloader
import com.geminidict.app.tts.OfflineTtsManager

class GeminiDictApplication : Application() {
    lateinit var database: AppDatabase
        private set
    lateinit var securePrefs: SecurePrefsManager
        private set
    lateinit var geminiService: GeminiTranslationService
        private set
    lateinit var dictionaryDownloader: DictionaryDownloader
        private set
    lateinit var neuralVoiceDownloader: NeuralVoiceDownloader
        private set
    lateinit var ttsManager: OfflineTtsManager
        private set

    override fun onCreate() {
        super.onCreate()
        database = AppDatabase.getInstance(this)
        securePrefs = SecurePrefsManager(this)
        geminiService = GeminiTranslationService()
        dictionaryDownloader = DictionaryDownloader(this, database, securePrefs)
        neuralVoiceDownloader = NeuralVoiceDownloader(this, securePrefs)
        ttsManager = OfflineTtsManager(this, neuralVoiceDownloader).apply {
            configureDefaults(
                accentTag = securePrefs.getTtsAccent(),
                rate = securePrefs.getTtsSpeechRate(),
                preferNeural = securePrefs.getPreferNeuralVoice()
            )
        }
    }

    override fun onTerminate() {
        super.onTerminate()
        ttsManager.shutdown()
    }
}
