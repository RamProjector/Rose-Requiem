package com.roserequiem.app.data

import java.io.File
import java.io.RandomAccessFile

/** Result of the app's offline download-safety screening. */
sealed interface AudioSafetyScanResult {
    data object Passed : AudioSafetyScanResult
    data class ThreatDetected(val signatureName: String) : AudioSafetyScanResult
    data class Rejected(val reason: String) : AudioSafetyScanResult
    data object Cancelled : AudioSafetyScanResult
}

/**
 * Performs local pre-publication checks on a downloaded FLAC file.
 *
 * This is not a general antivirus engine. It validates the FLAC metadata structure, checks
 * the standard harmless EICAR antivirus test string, and runs bundled YARA-X signatures.
 * It cannot identify every threat, and a Passed verdict is not proof that a file is malware-free.
 */
object DownloadedAudioSafetyScanner {
    private const val MAX_METADATA_BLOCKS = 1024
    private const val BUFFER_SIZE = 64 * 1024
    private const val EICAR_NAME = "EICAR-Test-File"
    private val eicar = "X5O!P%@AP[4\\PZX54(P^)7CC)7}\$EICAR-STANDARD-ANTIVIRUS-TEST-FILE!\$H+H*"
        .toByteArray(Charsets.US_ASCII)

    internal fun scan(
        file: File,
        isCancelled: () -> Boolean = { false },
        yaraScan: (String) -> YaraXScanResult = YaraXNativeScanner::scanFile,
    ): AudioSafetyScanResult {
        if (isCancelled()) return AudioSafetyScanResult.Cancelled
        if (!file.isFile || !file.canRead()) {
            return AudioSafetyScanResult.Rejected("The downloaded file could not be read for safety checks.")
        }

        return try {
            RandomAccessFile(file, "r").use { input ->
                val length = input.length()
                val structureFailure = validateFlacMetadata(input, length, isCancelled)
                if (structureFailure != null) return structureFailure
                if (isCancelled()) return AudioSafetyScanResult.Cancelled
                when (val eicarResult = findEicar(input, length, isCancelled)) {
                    AudioSafetyScanResult.Passed -> {
                        if (isCancelled()) return AudioSafetyScanResult.Cancelled
                        val yaraResult = yaraScan(file.absolutePath)
                        if (isCancelled()) return AudioSafetyScanResult.Cancelled
                        when (yaraResult) {
                            YaraXScanResult.Clean -> {
                                AudioSafetyScanResult.Passed
                            }
                            is YaraXScanResult.ThreatsFound -> AudioSafetyScanResult.ThreatDetected(
                                "YARA-X: ${yaraResult.ruleNames.joinToString().take(400)}"
                            )
                            is YaraXScanResult.Unavailable -> AudioSafetyScanResult.Rejected(
                                yaraResult.reason
                            )
                        }
                    }
                    else -> eicarResult
                }
            }
        } catch (_: DownloadCancelledException) {
            AudioSafetyScanResult.Cancelled
        } catch (error: Exception) {
            AudioSafetyScanResult.Rejected(
                error.message?.takeIf { it.isNotBlank() }
                    ?: "The downloaded audio could not be checked safely."
            )
        }
    }

    private fun validateFlacMetadata(
        file: RandomAccessFile,
        fileLength: Long,
        isCancelled: () -> Boolean,
    ): AudioSafetyScanResult.Rejected? {
        if (fileLength < 42L) return rejected("The FLAC file is truncated.")
        val signature = ByteArray(4)
        file.readFully(signature)
        if (!hasFlacSignature(signature)) return rejected("The file does not have a FLAC signature.")

        var blockIndex = 0
        var streamInfoSeen = false
        var lastBlockSeen = false
        while (!lastBlockSeen) {
            if (isCancelled()) throw DownloadCancelledException()
            if (blockIndex++ >= MAX_METADATA_BLOCKS) {
                return rejected("The FLAC file contains too many metadata blocks.")
            }
            if (file.filePointer + 4L > fileLength) return rejected("The FLAC metadata header is truncated.")

            val first = file.readUnsignedByte()
            val isLast = first and 0x80 != 0
            val type = first and 0x7f
            val blockLength = (file.readUnsignedByte().toLong() shl 16) or
                (file.readUnsignedByte().toLong() shl 8) or
                file.readUnsignedByte().toLong()

            if (type == 127 || type in 7..126) {
                return rejected("The FLAC file uses a reserved metadata block type.")
            }
            if (blockIndex == 1) {
                if (type != 0 || blockLength != 34L) {
                    return rejected("The FLAC file is missing a valid STREAMINFO block.")
                }
                streamInfoSeen = true
            } else if (type == 0) {
                return rejected("The FLAC file contains a duplicate STREAMINFO block.")
            }
            if (blockLength > fileLength - file.filePointer) {
                return rejected("A FLAC metadata block extends beyond the end of the file.")
            }
            file.seek(file.filePointer + blockLength)
            lastBlockSeen = isLast
        }

        if (!streamInfoSeen || file.filePointer >= fileLength) {
            return rejected("The FLAC file has no audio frame data.")
        }
        return null
    }

    private fun findEicar(
        file: RandomAccessFile,
        fileLength: Long,
        isCancelled: () -> Boolean,
    ): AudioSafetyScanResult {
        file.seek(0L)
        val buffer = ByteArray(BUFFER_SIZE)
        var carry = ByteArray(0)
        var scanned = 0L
        while (scanned < fileLength) {
            if (isCancelled()) return AudioSafetyScanResult.Cancelled
            val count = file.read(buffer, 0, minOf(buffer.size.toLong(), fileLength - scanned).toInt())
            if (count < 0) break
            if (count == 0) continue

            val combined = ByteArray(carry.size + count)
            carry.copyInto(combined)
            buffer.copyInto(combined, destinationOffset = carry.size, startIndex = 0, endIndex = count)
            if (combined.indexOfSubarray(eicar) >= 0) {
                return AudioSafetyScanResult.ThreatDetected(EICAR_NAME)
            }
            val carrySize = minOf(eicar.size - 1, combined.size)
            carry = combined.copyOfRange(combined.size - carrySize, combined.size)
            scanned += count
        }
        return if (isCancelled()) AudioSafetyScanResult.Cancelled else AudioSafetyScanResult.Passed
    }

    private fun rejected(reason: String) = AudioSafetyScanResult.Rejected(reason)

    private fun ByteArray.indexOfSubarray(needle: ByteArray): Int {
        if (needle.isEmpty() || size < needle.size) return -1
        for (start in 0..(size - needle.size)) {
            var match = true
            for (offset in needle.indices) {
                if (this[start + offset] != needle[offset]) {
                    match = false
                    break
                }
            }
            if (match) return start
        }
        return -1
    }
}
