package com.geminidict.app.data.dict

import android.content.Context
import android.os.SystemClock
import androidx.room.withTransaction
import com.geminidict.app.data.local.AppDatabase
import com.geminidict.app.data.local.entity.DownloadedDictEntity
import com.geminidict.app.data.security.SecurePrefsManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
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

data class DownloadProgress(
    val dictId: String,
    val phase: String, // "downloading", "importing", "done", "cancelled", "error"
    val progress: Float, // 0f..1f, or -1f for indeterminate
    val message: String
)

/**
 * Downloads public open-source offline dictionaries with:
 * - HTTP Range resumable downloads (.part files preserved across network errors or cancellations)
 * - Content-Length / Content-Range integrity verification before touching DB
 * - Throttled progress updates (max ~6 updates/sec)
 * - Cooperative cancellation (cancels active OkHttp call)
 * - Atomic Room transaction (old dictionary stays intact if download/parse fails)
 */
class DictionaryDownloader(
    private val context: Context,
    private val db: AppDatabase,
    private val prefs: SecurePrefsManager,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()
) {
    private val activeCalls = ConcurrentHashMap<String, Call>()

    fun cancelActiveCall(dictId: String) {
        activeCalls.remove(dictId)?.cancel()
    }

    suspend fun ensureStarterLoaded() = withContext(Dispatchers.IO) {
        if (prefs.isStarterDictLoaded()) return@withContext
        context.assets.open("starter_dict.csv").use { stream ->
            db.withTransaction {
                val count = DictionaryParsers.parseEcdictCsvStream(stream, sourceId = "starter") { batch ->
                    db.dictionaryDao().insertAll(batch)
                }
                if (count > 0) {
                    db.downloadedDictDao().upsert(
                        DownloadedDictEntity(
                            id = "starter",
                            name = "内置精简示例词库",
                            category = "英汉",
                            entryCount = count
                        )
                    )
                    prefs.setStarterDictLoaded(true)
                }
            }
        }
    }

    suspend fun downloadAndInstall(
        item: DictionaryCatalogItem,
        onProgress: (DownloadProgress) -> Unit
    ): Result<Int> = withContext(Dispatchers.IO) {
        val mirrorPrefix = prefs.getGithubMirrorPrefix().trim()
        val effectiveUrl = if (item.isGithubRaw && mirrorPrefix.isNotEmpty()) {
            val normalizedPrefix = if (mirrorPrefix.endsWith("/")) mirrorPrefix else "$mirrorPrefix/"
            "$normalizedPrefix${item.downloadUrl}"
        } else {
            item.downloadUrl
        }

        val cacheDir = File(context.cacheDir, "dict_downloads").apply { mkdirs() }
        val partFile = File(cacheDir, "${item.id}.part")

        try {
            downloadWithResume(item, effectiveUrl, partFile, onProgress)

            coroutineContext.ensureActive()
            onProgress(DownloadProgress(item.id, "importing", -1f, "正在校验并原子写入离线库..."))
            val importedCount = importLocalFileAtomic(partFile, item.format, item.id, item.name, item.categoryLabel)
            if (importedCount <= 0) {
                throw IOException("解析完成但未获得有效词条，已回滚以保留原有词库。")
            }
            // Only remove .part cache AFTER atomic DB import succeeds
            if (partFile.exists()) partFile.delete()
            onProgress(DownloadProgress(item.id, "done", 1f, "已安装 $importedCount 条词目"))
            Result.success(importedCount)
        } catch (ce: CancellationException) {
            // Preserve .part file so user can resume on next tap
            val savedMb = partFile.length() / (1024.0 * 1024.0)
            val msg = if (partFile.length() > 0) {
                String.format("已取消下载（已缓存 %.1f MB，再次点击可断点续传）", savedMb)
            } else {
                "已取消下载"
            }
            onProgress(DownloadProgress(item.id, "cancelled", 0f, msg))
            throw ce
        } catch (e: Exception) {
            val savedMb = partFile.length() / (1024.0 * 1024.0)
            val resumeHint = if (partFile.length() > 0) {
                String.format("（已缓存 %.1f MB，重试将自动断点续传）", savedMb)
            } else {
                ""
            }
            onProgress(DownloadProgress(item.id, "error", 0f, "失败: ${e.message}$resumeHint"))
            Result.failure(e)
        } finally {
            activeCalls.remove(item.id)
        }
    }

    private suspend fun downloadWithResume(
        item: DictionaryCatalogItem,
        effectiveUrl: String,
        partFile: File,
        onProgress: (DownloadProgress) -> Unit,
        allowRetryOn416: Boolean = true
    ) {
        val existingBytes = if (partFile.exists()) partFile.length() else 0L
        val resumeLabel = if (existingBytes > 0L) {
            String.format("正在断点续传（已下 %.1f MB）...", existingBytes / (1024.0 * 1024.0))
        } else {
            "正在连接下载源..."
        }
        onProgress(DownloadProgress(item.id, "downloading", 0f, resumeLabel))

        val reqBuilder = Request.Builder()
            .url(effectiveUrl)
            .header("User-Agent", "GeminiDictAndroid/1.2")
        if (existingBytes > 0L) {
            reqBuilder.header("Range", "bytes=$existingBytes-")
        }

        val call = client.newCall(reqBuilder.build())
        activeCalls[item.id] = call

        call.execute().use { response ->
            if (response.code == 416 && existingBytes > 0L && allowRetryOn416) {
                // Range not satisfiable (file may already be complete or upstream changed): reset & retry once
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
                throw IOException("文件下载未完成（$currentBytes / $expectedTotalBytes 字节）")
            }
            if (currentBytes == 0L) {
                throw IOException("下载文件为空")
            }
        }
    }

    private fun parseTotalFromContentRange(header: String?): Long? {
        if (header.isNullOrBlank()) return null
        val slash = header.lastIndexOf('/')
        if (slash < 0) return null
        val totalPart = header.substring(slash + 1).trim()
        return totalPart.toLongOrNull()
    }

    suspend fun importLocalFileAtomic(
        file: File,
        format: DictFormat,
        sourceId: String,
        displayName: String,
        categoryLabel: String
    ): Int = withContext(Dispatchers.IO) {
        FileInputStream(file).use { fis ->
            db.withTransaction {
                val count = when (format) {
                    DictFormat.ECDICT_CSV -> {
                        db.dictionaryDao().deleteBySource(sourceId)
                        DictionaryParsers.parseEcdictCsvStream(fis, sourceId = sourceId) { batch ->
                            db.dictionaryDao().insertAll(batch)
                        }
                    }
                    DictFormat.CC_CEDICT_GZ -> {
                        db.dictionaryDao().deleteBySource(sourceId)
                        DictionaryParsers.parseCedictStream(fis, isGzip = true, sourceId = sourceId) { batch ->
                            db.dictionaryDao().insertAll(batch)
                        }
                    }
                    DictFormat.KAJWEB_JSONL_ZIP -> {
                        db.dictionaryDao().deleteBySource(sourceId)
                        DictionaryParsers.parseKajwebJsonlZipStream(fis, sourceId = sourceId) { batch ->
                            db.dictionaryDao().insertAll(batch)
                        }
                    }
                    DictFormat.XINHUA_WORD_JSON,
                    DictFormat.XINHUA_IDIOM_JSON,
                    DictFormat.XINHUA_CI_JSON,
                    DictFormat.XINHUA_XIEHOUYU_JSON -> {
                        db.chineseDictDao().deleteBySource(sourceId)
                        DictionaryParsers.parseXinhuaJsonStream(fis, format = format, sourceId = sourceId) { batch ->
                            db.chineseDictDao().insertAll(batch)
                        }
                    }
                }
                if (count <= 0) {
                    throw IOException("未解析到任何有效词条")
                }
                db.downloadedDictDao().upsert(
                    DownloadedDictEntity(
                        id = sourceId,
                        name = displayName,
                        category = categoryLabel,
                        entryCount = count
                    )
                )
                count
            }
        }
    }

    suspend fun uninstallDictionary(dictId: String) = withContext(Dispatchers.IO) {
        val partFile = File(File(context.cacheDir, "dict_downloads"), "$dictId.part")
        if (partFile.exists()) partFile.delete()
        db.withTransaction {
            db.dictionaryDao().deleteBySource(dictId)
            db.chineseDictDao().deleteBySource(dictId)
            db.downloadedDictDao().deleteById(dictId)
        }
    }
}
