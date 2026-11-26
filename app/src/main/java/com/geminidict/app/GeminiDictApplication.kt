package com.geminidict.app

import android.app.Application
import com.geminidict.app.data.dict.DictionaryDownloader
import com.geminidict.app.data.local.AppDatabase
import com.geminidict.app.data.remote.GeminiTranslationService
import com.geminidict.app.data.security.SecurePrefsManager
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
    lateinit var ttsManager: OfflineTtsManager
        private set

    override fun onCreate() {
        super.onCreate()
        database = AppDatabase.getInstance(this)
        securePrefs = SecurePrefsManager(this)
        geminiService = GeminiTranslationService()
        dictionaryDownloader = DictionaryDownloader(this, database, securePrefs)
        // OfflineTtsManager initializes TextToSpeech lazily on first speech request
        ttsManager = OfflineTtsManager(this).apply {
            configureDefaults(securePrefs.getTtsAccent(), securePrefs.getTtsSpeechRate())
        }
    }

    override fun onTerminate() {
        super.onTerminate()
        ttsManager.shutdown()
    }
}
