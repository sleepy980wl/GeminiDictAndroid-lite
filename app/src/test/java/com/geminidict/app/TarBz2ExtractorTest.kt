package com.geminidict.app

import com.geminidict.app.data.tts.TarBz2Extractor
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.nio.file.Files

class TarBz2ExtractorTest {

    private fun buildTarBz2(entries: List<Pair<String, String>>): ByteArray {
        val baos = ByteArrayOutputStream()
        BZip2CompressorOutputStream(baos).use { bzOut ->
            TarArchiveOutputStream(bzOut).use { tarOut ->
                for ((name, content) in entries) {
                    val bytes = content.toByteArray(StandardCharsets.UTF_8)
                    val entry = TarArchiveEntry(name)
                    entry.size = bytes.size.toLong()
                    tarOut.putArchiveEntry(entry)
                    tarOut.write(bytes)
                    tarOut.closeArchiveEntry()
                }
            }
        }
        return baos.toByteArray()
    }

    @Test
    fun extractsNestedPiperModelArchiveCleanly() {
        val archive = buildTarBz2(
            listOf(
                "vits-piper-en_US-amy-low/tokens.txt" to "a 1\nb 2\n",
                "vits-piper-en_US-amy-low/en_US-amy-low.onnx" to "fake-onnx-bytes",
                "vits-piper-en_US-amy-low/espeak-ng-data/phontab" to "phoneme-table"
            )
        )
        val tempDir = Files.createTempDirectory("tarbz2_test_ok").toFile()
        try {
            val count = TarBz2Extractor.extract(ByteArrayInputStream(archive), tempDir)
            assertEquals(3, count)
            assertTrue(tempDir.resolve("vits-piper-en_US-amy-low/tokens.txt").isFile)
            assertTrue(tempDir.resolve("vits-piper-en_US-amy-low/espeak-ng-data/phontab").isFile)
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test(expected = SecurityException::class)
    fun rejectsZipSlipTraversalEntry() {
        val malicious = buildTarBz2(
            listOf("../../evil.txt" to "pwned")
        )
        val tempDir = Files.createTempDirectory("tarbz2_test_slip").toFile()
        try {
            TarBz2Extractor.extract(ByteArrayInputStream(malicious), tempDir)
        } finally {
            tempDir.deleteRecursively()
        }
    }
}
