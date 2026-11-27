package com.geminidict.app.tts

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import com.geminidict.app.data.tts.InstalledNeuralVoice
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsVitsModelConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/**
 * Pure-offline neural TTS synthesizer & AudioTrack player backed by Sherpa-ONNX + Piper VITS.
 * Runs all ONNX inference and PCM audio playback off the UI thread.
 */
class NeuralTtsEngine {

    private val engineLock = Any()
    private var loadedVoiceId: String? = null
    private var offlineTts: OfflineTts? = null

    @Volatile
    private var activeTrack: AudioTrack? = null

    fun stopPlayback() {
        val track = activeTrack
        activeTrack = null
        try {
            track?.pause()
            track?.flush()
            track?.stop()
            track?.release()
        } catch (_: Exception) {
            // Best-effort AudioTrack cleanup
        }
    }

    fun releaseModel() {
        stopPlayback()
        synchronized(engineLock) {
            try {
                offlineTts?.free()
            } catch (_: Exception) {
            }
            offlineTts = null
            loadedVoiceId = null
        }
    }

    /**
     * Synthesizes [text] offline using the installed Piper VITS model and plays it via AudioTrack.
     */
    suspend fun synthesizeAndPlay(
        voice: InstalledNeuralVoice,
        text: String,
        speed: Float
    ) = withContext(Dispatchers.IO) {
        stopPlayback()
        currentCoroutineContext().ensureActive()

        val audio = synchronized(engineLock) {
            val tts = ensureModelLoadedLocked(voice)
            tts.generate(
                text = text,
                sid = 0,
                speed = speed.coerceIn(0.5f, 1.5f)
            )
        }

        currentCoroutineContext().ensureActive()
        if (audio.samples.isEmpty() || audio.sampleRate <= 0) {
            throw IllegalStateException("神经语音合成结果为空")
        }

        playPcmFloat(audio.samples, audio.sampleRate)
    }

    private fun ensureModelLoadedLocked(voice: InstalledNeuralVoice): OfflineTts {
        if (loadedVoiceId == voice.item.id && offlineTts != null) {
            return offlineTts!!
        }
        try {
            offlineTts?.free()
        } catch (_: Exception) {
        }
        offlineTts = null
        loadedVoiceId = null

        val config = OfflineTtsConfig(
            model = OfflineTtsModelConfig(
                vits = OfflineTtsVitsModelConfig(
                    model = voice.onnxFile.absolutePath,
                    tokens = voice.tokensFile.absolutePath,
                    dataDir = voice.espeakDataDir.absolutePath
                ),
                numThreads = 2,
                debug = false,
                provider = "cpu"
            )
        )
        val created = OfflineTts(assetManager = null, config = config)
        offlineTts = created
        loadedVoiceId = voice.item.id
        return created
    }

    private suspend fun playPcmFloat(samples: FloatArray, sampleRate: Int) = withContext(Dispatchers.IO) {
        val minBuf = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_FLOAT
        ).coerceAtLeast(4096)

        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(minBuf)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()

        activeTrack = track
        try {
            track.play()
            var offset = 0
            val chunkSize = 2048
            while (offset < samples.size) {
                currentCoroutineContext().ensureActive()
                val count = (samples.size - offset).coerceAtMost(chunkSize)
                val written = track.write(samples, offset, count, AudioTrack.WRITE_BLOCKING)
                if (written <= 0) break
                offset += written
            }
            // Allow hardware buffer to drain cleanly
            track.stop()
        } catch (ce: CancellationException) {
            stopPlayback()
            throw ce
        } finally {
            if (activeTrack === track) {
                activeTrack = null
            }
            try {
                track.release()
            } catch (_: Exception) {
            }
        }
    }
}
