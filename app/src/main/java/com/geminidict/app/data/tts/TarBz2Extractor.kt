package com.geminidict.app.data.tts

import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream

/**
 * Pure-Kotlin streaming .tar.bz2 extractor with Zip-Slip path traversal protection.
 */
object TarBz2Extractor {

    fun extract(inputStream: InputStream, targetDir: File): Int {
        if (!targetDir.exists() && !targetDir.mkdirs()) {
            throw IOException("无法创建解压目录: ${targetDir.absolutePath}")
        }
        val canonicalRoot = targetDir.canonicalFile
        var extractedFiles = 0

        BZip2CompressorInputStream(BufferedInputStream(inputStream)).use { bzIn ->
            TarArchiveInputStream(bzIn).use { tarIn ->
                val buffer = ByteArray(16 * 1024)
                while (true) {
                    val entry = tarIn.nextEntry ?: break
                    val outFile = File(canonicalRoot, entry.name)
                    val canonicalOut = outFile.canonicalFile
                    if (!canonicalOut.path.startsWith(canonicalRoot.path + File.separator) &&
                        canonicalOut.path != canonicalRoot.path
                    ) {
                        throw SecurityException("检测到非法压缩路径（Zip-Slip）: ${entry.name}")
                    }

                    if (entry.isDirectory) {
                        canonicalOut.mkdirs()
                    } else {
                        canonicalOut.parentFile?.mkdirs()
                        FileOutputStream(canonicalOut).use { fos ->
                            while (true) {
                                val read = tarIn.read(buffer)
                                if (read == -1) break
                                fos.write(buffer, 0, read)
                            }
                        }
                        extractedFiles++
                    }
                }
            }
        }
        return extractedFiles
    }
}
