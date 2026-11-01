package com.roserequiem.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class DownloadedAudioSafetyScannerTest {
    @Test
    fun passesWellFormedFlacContainer() = withTempAudio(validFlac(byteArrayOf(0xff.toByte(), 0xf8.toByte(), 0x00, 0x00))) { file ->
        assertEquals(AudioSafetyScanResult.Passed, DownloadedAudioSafetyScanner.scan(file))
    }

    @Test
    fun detectsEicarTestSignature() = withTempAudio(validFlac(eicar)) { file ->
        val result = DownloadedAudioSafetyScanner.scan(file)
        assertEquals(AudioSafetyScanResult.ThreatDetected("EICAR-Test-File"), result)
    }

    @Test
    fun detectsSignatureAcrossReadBufferBoundary() {
        val audio = ByteArray(65_536 - 42 - eicar.size / 2) { 0x11 }
        val payload = audio + eicar + byteArrayOf(0x22)
        withTempAudio(validFlac(payload)) { file ->
            assertEquals(
                AudioSafetyScanResult.ThreatDetected("EICAR-Test-File"),
                DownloadedAudioSafetyScanner.scan(file),
            )
        }
    }

    @Test
    fun rejectsMalformedMetadataLength() {
        val malformed = validFlac(byteArrayOf(0x01, 0x02, 0x03, 0x04)).also {
            // STREAMINFO block length must be exactly 34 bytes; set it to 35.
            it[5] = 0
            it[6] = 0
            it[7] = 35
        }
        withTempAudio(malformed) { file ->
            val result = DownloadedAudioSafetyScanner.scan(file)
            assertTrue(result is AudioSafetyScanResult.Rejected)
        }
    }

    @Test
    fun rejectsNonFlacContent() = withTempAudio("not audio".toByteArray()) { file ->
        assertTrue(DownloadedAudioSafetyScanner.scan(file) is AudioSafetyScanResult.Rejected)
    }

    @Test
    fun cancellationStopsTheScanBeforeReading() = withTempAudio(validFlac(ByteArray(128))) { file ->
        assertEquals(
            AudioSafetyScanResult.Cancelled,
            DownloadedAudioSafetyScanner.scan(file) { true },
        )
    }

    private fun withTempAudio(bytes: ByteArray, block: (File) -> Unit) {
        val file = File.createTempFile("rose-scan-test-", ".flac")
        try {
            file.writeBytes(bytes)
            block(file)
        } finally {
            file.delete()
        }
    }

    private fun validFlac(audioFrames: ByteArray): ByteArray {
        val output = ByteArray(4 + 4 + 34 + audioFrames.size)
        "fLaC".toByteArray(Charsets.US_ASCII).copyInto(output, 0)
        output[4] = 0x80.toByte() // Last metadata block, STREAMINFO type.
        output[5] = 0
        output[6] = 0
        output[7] = 34
        audioFrames.copyInto(output, 42)
        return output
    }

    private companion object {
        val eicar = "X5O!P%@AP[4\\PZX54(P^)7CC)7}\$EICAR-STANDARD-ANTIVIRUS-TEST-FILE!\$H+H*"
            .toByteArray(Charsets.US_ASCII)
    }
}
