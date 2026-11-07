package com.roserequiem.app.data

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthorizedFlacDownloaderTest {
    @Test
    fun cancellationStopsAnAttachedHttpCall() {
        val cancellation = FlacDownloadCancellation()
        val call = newTestCall()
        cancellation.attach(call)

        cancellation.cancel()

        assertTrue(cancellation.isCancelled)
        assertTrue(call.isCanceled())
    }

    @Test
    fun cancellationBeforeRequestAttachmentStopsTheNextCall() {
        val cancellation = FlacDownloadCancellation()
        cancellation.cancel()
        val call = newTestCall()

        cancellation.attach(call)

        assertTrue(call.isCanceled())
    }

    @Test
    fun sanitizeFlacFileNameRemovesPathSeparatorsAndKeepsExtension() {
        assertEquals("artist_album.flac", sanitizeFlacFileName("artist/album.FLAC"))
    }

    @Test
    fun sanitizeFlacFileNamePreservesExtensionWhenInputIsLong() {
        val result = sanitizeFlacFileName("${"a".repeat(200)}.flac")

        assertEquals(120, result?.length)
        assertTrue(result?.endsWith(".flac") == true)
    }

    @Test
    fun sanitizeFlacFileNameRejectsNonFlacExtensions() {
        assertNull(sanitizeFlacFileName("track.mp3"))
    }

    @Test
    fun hasFlacSignatureRejectsTruncatedAndNonFlacHeaders() {
        assertFalse(hasFlacSignature(byteArrayOf(0x66, 0x4c, 0x61)))
        assertFalse(hasFlacSignature("HTML".toByteArray()))
        assertTrue(hasFlacSignature(byteArrayOf(0x66, 0x4c, 0x61, 0x43)))
    }

    @Test
    fun copyWithProgressReportsTransferredBytesAndTotal() {
        val content = ByteArray(600_000) { (it % 251).toByte() }
        val output = ByteArrayOutputStream()
        val progress = mutableListOf<Pair<Long, Long?>>()

        val copied = copyWithProgress(
            input = ByteArrayInputStream(content),
            output = output,
            maxBytes = content.size.toLong(),
            expectedBytes = content.size.toLong(),
            onProgress = { downloaded, total -> progress += downloaded to total },
        )

        assertEquals(content.size.toLong(), copied)
        assertEquals(content.toList(), output.toByteArray().toList())
        assertEquals(content.size.toLong(), progress.last().first)
        assertEquals(content.size.toLong(), progress.last().second)
        assertTrue(progress.size > 1)
        assertTrue(progress.size < content.size / 8_192)
        assertTrue(progress.zipWithNext().all { (previous, next) -> next.first > previous.first })
    }

    @Test
    fun copyWithProgressReportsUnknownTotalAsNull() {
        val output = ByteArrayOutputStream()
        val progress = mutableListOf<Long?>()

        copyWithProgress(
            input = ByteArrayInputStream(byteArrayOf(1, 2, 3)),
            output = output,
            maxBytes = 10,
            expectedBytes = null,
            onProgress = { _, total -> progress += total },
        )

        assertEquals(1, progress.size)
        assertNull(progress.single())
    }

    @Test
    fun copyWithProgressStopsBeforeWritingBytesBeyondLimit() {
        val output = ByteArrayOutputStream()
        val progress = mutableListOf<Long>()

        assertThrows(DownloadSizeLimitExceededException::class.java) {
            copyWithProgress(
                input = ByteArrayInputStream(ByteArray(20_000)),
                output = output,
                maxBytes = 10_000,
                expectedBytes = 20_000,
                onProgress = { downloaded, _ -> progress += downloaded },
            )
        }

        assertTrue(output.size() <= 10_000)
        assertEquals(emptyList<Long>(), progress)
    }

    @Test
    fun copyWithProgressStopsWhenCancellationIsRequested() {
        val output = ByteArrayOutputStream()

        assertThrows(DownloadCancelledException::class.java) {
            copyWithProgress(
                input = ByteArrayInputStream(ByteArray(10_000)),
                output = output,
                maxBytes = 10_000,
                expectedBytes = 10_000,
                onProgress = { _, _ -> },
                isCancelled = { true },
            )
        }

        assertEquals(0, output.size())
    }

    private fun newTestCall() = OkHttpClient().newCall(
        Request.Builder().url("https://example.com/audio.flac").build()
    )
}
