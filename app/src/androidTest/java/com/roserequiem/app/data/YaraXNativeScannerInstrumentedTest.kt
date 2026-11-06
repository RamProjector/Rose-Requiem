package com.roserequiem.app.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class YaraXNativeScannerInstrumentedTest {
    @Test
    fun packagedNativeEngineCompilesBundledRulesAndScansOffline() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File.createTempFile("rose-yara-native-", ".flac", context.cacheDir)
        try {
            file.writeBytes(validSyntheticFlac())
            assertEquals(YaraXScanResult.Clean, YaraXNativeScanner.scanFile(file.absolutePath))
        } finally {
            file.delete()
        }
    }

    private fun validSyntheticFlac(): ByteArray = ByteArray(44).also { bytes ->
        "fLaC".toByteArray(Charsets.US_ASCII).copyInto(bytes, 0)
        bytes[4] = 0x80.toByte() // Last metadata block, STREAMINFO type.
        bytes[5] = 0
        bytes[6] = 0
        bytes[7] = 34
        bytes[42] = 0xff.toByte()
        bytes[43] = 0xf8.toByte()
    }
}
