package com.geminidict.app.data.tts

import android.content.Context
import android.os.SystemClock
import com.geminidict.app.data.dict.DownloadProgress
import com.geminidict.app.data.security.SecurePrefsManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

data class InstalledNeuralVoice(
    val item: NeuralVoiceModelItem,
    val onnxFile: File,
    val tokensFile: File,
    val espeakDataDir: File
)

/**
 * Downloads & unpacks Piper VITS neural voice models (.tar.bz2) with:
 * - HTTP Range resumable downloads (.part cache preserved on error/cancel)
 * - Content-Length/Content-Range verification
 * - Atomic staging directory extraction & integrity check (onnx + tokens.txt + espeak-ng-data)
 */
class NeuralVoiceDownloader(
    private val context: Context,
    private val prefs: SecurePrefsManager,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()
) {
    private val activeCalls = ConcurrentHashMap<String, Call>()

    private val voicesRootDir: File
        get() = File(context.filesDir, "neural_voices").apply { mkdirs() }

    fun cancelActiveCall(voiceId: String) {
        activeCalls.remove(voiceId)?.cancel()
    }

    fun getInstalledVoices(): List<InstalledNeuralVoice> {
        return NeuralVoiceCatalog.items.mapNotNull { resolveInstalledVoice(it) }
    }

    fun resolveInstalledVoice(item: NeuralVoiceModelItem): InstalledNeuralVoice? {
        val modelRoot = File(voicesRootDir, item.id)
        if (!modelRoot.isDirectory) return null
        val onnx = findFileRecursively(modelRoot) { it.isFile && it.name.endsWith(".onnx") } ?: return null
        val tokens = findFileRecursively(modelRoot) { it.isFile && it.name == "tokens.txt" } ?: return null
        val espeak = findFileRecursively(modelRoot) { it.isDirectory && it.name == "espeak-ng-data" } ?: return null
        return InstalledNeuralVoice(item, onnx, tokens, espeak)
    }

    suspend fun downloadAndInstall(
        item: NeuralVoiceModelItem,
        onProgress: (DownloadProgress) -> Unit
    ): Result<InstalledNeuralVoice> = withContext(Dispatchers.IO) {
        val mirrorPrefix = prefs.getGithubMirrorPrefix().trim()
        val effectiveUrl = if (mirrorPrefix.isNotEmpty()) {
            val normalizedPrefix = if (mirrorPrefix.endsWith("/")) mirrorPrefix else "$mirrorPrefix/"
            "$normalizedPrefix${item.downloadUrl}"
        } else {
            item.downloadUrl
        }

        val cacheDir = File(context.cacheDir, "voice_downloads").apply { mkdirs() }
        val partFile = File(cacheDir, "${item.id}.part")
        val stagingDir = File(voicesRootDir, "${item.id}.staging")
        val targetDir = File(voicesRootDir, item.id)

        try {
            downloadWithResume(item, effectiveUrl, partFile, onProgress)

            coroutineContext.ensureActive()
            onProgress(DownloadProgress(item.id, "importing", -1f, "正在解压神经语音模型（可能需要十几秒）..."))
            if (stagingDir.exists()) stagingDir.deleteRecursively()
            FileInputStream(partFile).use { fis ->
                TarBz2Extractor.extract(fis, stagingDir)
            }

            // Validate extracted files before swapping into final directory
            val onnx = findFileRecursively(stagingDir) { it.isFile && it.name.endsWith(".onnx") }
            val tokens = findFileRecursively(stagingDir) { it.isFile && it.name == "tokens.txt" }
            val espeak = findFileRecursively(stagingDir) { it.isDirectory && it.name == "espeak-ng-data" }
            if (onnx == null || onnx.length() == 0L || tokens == null || espeak == null) {
                throw IOException("模型压缩包缺少必要文件（.onnx / tokens.txt / espeak-ng-data）")
            }

            if (targetDir.exists()) targetDir.deleteRecursively()
            if (!stagingDir.renameTo(targetDir)) {
                stagingDir.copyRecursively(targetDir, overwrite = true)
                stagingDir.deleteRecursively()
            }
            if (partFile.exists()) partFile.delete()

            val installed = resolveInstalledVoice(item)
                ?: throw IOException("模型目录校验失败")
            onProgress(DownloadProgress(item.id, "done", 1f, "神经语音包已就绪"))
            Result.success(installed)
        } catch (ce: CancellationException) {
            if (stagingDir.exists()) stagingDir.deleteRecursively()
            val savedMb = partFile.length() / (1024.0 * 1024.0)
            val msg = if (partFile.length() > 0) {
                String.format("已取消下载（已缓存 %.1f MB，下次可断点续传）", savedMb)
            } else {
                "已取消下载"
            }
            onProgress(DownloadProgress(item.id, "cancelled", 0f, msg))
            throw ce
        } catch (e: Exception) {
            if (stagingDir.exists()) stagingDir.deleteRecursively()
            // Any targetDir present here is a partial copy from this run
            // (the previous install was already deleted before the swap),
            // so remove it instead of letting it masquerade as installed.
            if (targetDir.exists()) targetDir.deleteRecursively()
            val savedMb = partFile.length() / (1024.0 * 1024.0)
            val resumeHint = if (partFile.length() > 0) {
                String.format("（已缓存 %.1f MB，重试自动断点续传）", savedMb)
            } else {
                ""
            }
            onProgress(DownloadProgress(item.id, "error", 0f, "失败: ${e.message}$resumeHint"))
            Result.failure(e)
        } finally {
            activeCalls.remove(item.id)
        }
    }

    suspend fun uninstallVoice(voiceId: String) = withContext(Dispatchers.IO) {
        File(File(context.cacheDir, "voice_downloads"), "$voiceId.part").delete()
        File(voicesRootDir, voiceId).deleteRecursively()
    }

    private suspend fun downloadWithResume(
        item: NeuralVoiceModelItem,
        effectiveUrl: String,
        partFile: File,
        onProgress: (DownloadProgress) -> Unit,
        allowRetryOn416: Boolean = true
    ) {
        // Capture here: inside the nested use{} blocks below the CoroutineScope
        // receiver is lost, so bare `coroutineContext` is unresolved (compile error).
        val coroutineContext = currentCoroutineContext()
        val existingBytes = if (partFile.exists()) partFile.length() else 0L
        val resumeLabel = if (existingBytes > 0L) {
            String.format("正在断点续传（已下 %.1f MB）...", existingBytes / (1024.0 * 1024.0))
        } else {
            "正在连接语音模型源..."
        }
        onProgress(DownloadProgress(item.id, "downloading", 0f, resumeLabel))

        val reqBuilder = Request.Builder()
            .url(effectiveUrl)
            .header("User-Agent", "GeminiDictAndroid/1.3")
        if (existingBytes > 0L) {
            reqBuilder.header("Range", "bytes=$existingBytes-")
        }

        val call = client.newCall(reqBuilder.build())
        activeCalls[item.id] = call

        call.execute().use { response ->
            if (response.code == 416 && existingBytes > 0L && allowRetryOn416) {
                partFile.delete()
                downloadWithResume(item, effectiveUrl, partFile, onProgress, allowRetryOn416 = false)
                return
            }
            if (!response.isSuccessful && response.code != 206) {
                throw IOException("下载失败 HTTP ${response.code}: ${response.message}")
            }

            val isPartial = response.code == 206
            val body = response.body ?: throw IOException("响应体为空")
            val bodyLength = body.contentLength()
            val expectedTotalBytes = when {
                isPartial -> parseTotalFromContentRange(response.header("Content-Range"))
                    ?: if (bodyLength > 0) existingBytes + bodyLength else -1L
                else -> bodyLength
            }

            var currentBytes = if (isPartial) existingBytes else 0L
            var lastEmitTime = 0L

            body.byteStream().use { input ->
                FileOutputStream(partFile, isPartial).use { output ->
                    val buffer = ByteArray(16 * 1024)
                    while (true) {
                        coroutineContext.ensureActive()
                        val read = input.read(buffer)
                        if (read == -1) break
                        output.write(buffer, 0, read)
                        currentBytes += read

                        val now = SystemClock.elapsedRealtime()
                        if (now - lastEmitTime >= 150L) {
                            lastEmitTime = now
                            val ratio = if (expectedTotalBytes > 0) {
                                (currentBytes.toFloat() / expectedTotalBytes.toFloat()).coerceIn(0f, 1f)
                            } else {
                                -1f
                            }
                            val mb = currentBytes / (1024.0 * 1024.0)
                            val msg = if (expectedTotalBytes > 0) {
                                val totalMb = expectedTotalBytes / (1024.0 * 1024.0)
                                String.format("正在下载 %.1f / %.1f MB", mb, totalMb)
                            } else {
                                String.format("正在下载 %.1f MB", mb)
                            }
                            onProgress(DownloadProgress(item.id, "downloading", ratio, msg))
                        }
                    }
                    output.fd.sync()
                }
            }

            if (expectedTotalBytes > 0 && currentBytes != expectedTotalBytes) {
                throw IOException("语音包下载未完成（$currentBytes / $expectedTotalBytes 字节）")
            }
            if (currentBytes == 0L) {
                throw IOException("下载语音包为空")
            }
        }
    }

    private fun parseTotalFromContentRange(header: String?): Long? {
        if (header.isNullOrBlank()) return null
        val slash = header.lastIndexOf('/')
        if (slash < 0) return null
        return header.substring(slash + 1).trim().toLongOrNull()
    }

    private fun findFileRecursively(dir: File, predicate: (File) -> Boolean): File? {
        val children = dir.listFiles() ?: return null
        for (child in children) {
            if (predicate(child)) return child
        }
        for (child in children) {
            if (child.isDirectory) {
                val found = findFileRecursively(child, predicate)
                if (found != null) return found
            }
        }
        return null
    }
}
