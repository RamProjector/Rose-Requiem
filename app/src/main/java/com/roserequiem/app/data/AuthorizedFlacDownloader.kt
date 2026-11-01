package com.roserequiem.app.data

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Call
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.URI
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

sealed interface FlacDownloadResult {
    data class Success(val displayName: String, val location: String) : FlacDownloadResult
    data class Failure(val message: String) : FlacDownloadResult
    data object Cancelled : FlacDownloadResult
}

/** Cancellation handle shared by the screen and the blocking HTTP/file-copy work. */
class FlacDownloadCancellation {
    private val cancelled = AtomicBoolean(false)
    private val activeCall = AtomicReference<Call?>(null)

    val isCancelled: Boolean get() = cancelled.get()

    fun cancel() {
        cancelled.set(true)
        activeCall.get()?.cancel()
    }

    internal fun attach(call: Call) {
        activeCall.set(call)
        if (cancelled.get()) call.cancel()
    }
}

/**
 * Downloads an authorized direct FLAC URL. This does not search for copyrighted files,
 * bypass access controls, or convert other audio formats. A response is only published
 * after its FLAC signature, metadata structure, and offline safety screening are checked.
 */
class AuthorizedFlacDownloader(
    private val context: Context,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .callTimeout(10, TimeUnit.MINUTES)
        .followRedirects(true)
        .followSslRedirects(false)
        .build(),
    private val maxBytes: Long = MAX_DOWNLOAD_BYTES,
) {
    fun download(
        sourceUrl: String,
        requestedName: String,
        cancellation: FlacDownloadCancellation = FlacDownloadCancellation(),
        onProgress: (downloadedBytes: Long, totalBytes: Long?) -> Unit = { _, _ -> },
        onSafetyScanStarted: () -> Unit = {},
    ): FlacDownloadResult {
        if (cancellation.isCancelled) return FlacDownloadResult.Cancelled
        val parsed = runCatching { URI(sourceUrl.trim()) }.getOrNull()
            ?: return FlacDownloadResult.Failure("Enter a valid direct HTTPS URL.")
        if (!parsed.scheme.equals("https", ignoreCase = true) || parsed.host.isNullOrBlank()) {
            return FlacDownloadResult.Failure("Only direct HTTPS download links are supported.")
        }
        if (parsed.userInfo != null) {
            return FlacDownloadResult.Failure("URLs containing embedded usernames or passwords are not accepted.")
        }

        val displayName = sanitizeFlacFileName(requestedName)
        if (displayName == null) {
            return FlacDownloadResult.Failure("The filename must end in .flac.")
        }

        val temp = try {
            File.createTempFile("rose-requiem-", ".part", context.cacheDir)
        } catch (error: Exception) {
            return FlacDownloadResult.Failure(error.message?.takeIf { it.isNotBlank() } ?: "Temporary storage is unavailable.")
        }
        try {
            val request = Request.Builder().url(sourceUrl.trim()).get().build()
            val call = client.newCall(request)
            cancellation.attach(call)
            call.execute().use { response ->
                if (cancellation.isCancelled) return FlacDownloadResult.Cancelled
                if (!response.isSuccessful) {
                    return FlacDownloadResult.Failure("The source returned HTTP ${response.code}. Check the link and your permission to access it.")
                }
                if (!response.request.url.isHttps) {
                    return FlacDownloadResult.Failure("The source redirected away from HTTPS; download was stopped.")
                }
                val body = response.body ?: return FlacDownloadResult.Failure("The source returned no file content.")
                if (body.contentLength() > maxBytes) {
                    return FlacDownloadResult.Failure("The file exceeds the 500 MB safety limit.")
                }
                body.byteStream().use { input ->
                    FileOutputStream(temp).use { output ->
                        val expectedBytes = body.contentLength().takeIf { it > 0L }
                        val downloadedBytes = copyWithProgress(
                            input = input,
                            output = output,
                            maxBytes = maxBytes,
                            expectedBytes = expectedBytes,
                            onProgress = onProgress,
                            isCancelled = { cancellation.isCancelled || call.isCanceled() },
                        )
                        if (expectedBytes != null && downloadedBytes != expectedBytes) {
                            return FlacDownloadResult.Failure("The download was incomplete. Nothing was saved.")
                        }
                        output.fd.sync()
                    }
                }
            }

            if (cancellation.isCancelled) return FlacDownloadResult.Cancelled
            if (temp.length() < 4 || FileInputStream(temp).use { input ->
                    val signature = ByteArray(4)
                    input.read(signature) == 4 && hasFlacSignature(signature)
                }.not()
            ) {
                return FlacDownloadResult.Failure("The downloaded content is not a FLAC file. Nothing was saved.")
            }

            if (cancellation.isCancelled) return FlacDownloadResult.Cancelled
            onSafetyScanStarted()
            when (val scan = DownloadedAudioSafetyScanner.scan(temp) { cancellation.isCancelled }) {
                AudioSafetyScanResult.Passed -> Unit
                is AudioSafetyScanResult.ThreatDetected -> return FlacDownloadResult.Failure(
                    "The offline safety scan detected ${scan.signatureName}. The download was blocked and nothing was saved."
                )
                is AudioSafetyScanResult.Rejected -> return FlacDownloadResult.Failure(
                    "The offline safety scan rejected this file: ${scan.reason} Nothing was saved."
                )
                AudioSafetyScanResult.Cancelled -> return FlacDownloadResult.Cancelled
            }

            return publish(temp, displayName, cancellation)
        } catch (_: DownloadCancelledException) {
            return FlacDownloadResult.Cancelled
        } catch (_: DownloadSizeLimitExceededException) {
            return FlacDownloadResult.Failure("The file exceeds the 500 MB safety limit.")
        } catch (error: Exception) {
            if (cancellation.isCancelled) return FlacDownloadResult.Cancelled
            return FlacDownloadResult.Failure(error.message?.takeIf { it.isNotBlank() } ?: "Download failed. Check the link, connection, and available storage.")
        } finally {
            temp.delete()
        }
    }

    private fun publish(
        temp: File,
        displayName: String,
        cancellation: FlacDownloadCancellation,
    ): FlacDownloadResult {
        if (cancellation.isCancelled) return FlacDownloadResult.Cancelled
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
                put(MediaStore.MediaColumns.MIME_TYPE, "audio/flac")
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_MUSIC + "/Rose Requiem")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val uri = context.contentResolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values)
                ?: return FlacDownloadResult.Failure("Android could not create the destination file.")
            try {
                context.contentResolver.openOutputStream(uri, "w")?.use { output ->
                    FileInputStream(temp).use { input ->
                        copyWithProgress(input, output, maxBytes = MAX_DOWNLOAD_BYTES, expectedBytes = temp.length(), onProgress = { _, _ -> }, isCancelled = { cancellation.isCancelled })
                    }
                } ?: error("Android could not open the destination file.")
                if (cancellation.isCancelled) {
                    context.contentResolver.delete(uri, null, null)
                    return FlacDownloadResult.Cancelled
                }
                val complete = ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }
                if (context.contentResolver.update(uri, complete, null, null) <= 0) {
                    error("Android could not publish the downloaded file.")
                }
                if (cancellation.isCancelled) {
                    context.contentResolver.delete(uri, null, null)
                    return FlacDownloadResult.Cancelled
                }
                return FlacDownloadResult.Success(displayName, "Music/Rose Requiem")
            } catch (error: Exception) {
                context.contentResolver.delete(uri, null, null)
                throw error
            }
        }

        @Suppress("DEPRECATION")
        val musicDirectory = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)
        val destinationDirectory = File(musicDirectory, "Rose Requiem")
        if ((!destinationDirectory.exists() && !destinationDirectory.mkdirs()) || !destinationDirectory.isDirectory) {
            return FlacDownloadResult.Failure("The public music folder is unavailable. Check storage permission and available space.")
        }
        val destination = uniqueDestination(destinationDirectory, displayName)
        val staging = try {
            File.createTempFile(".rose-requiem-", ".part", destinationDirectory)
        } catch (error: Exception) {
            return FlacDownloadResult.Failure(error.message?.takeIf { it.isNotBlank() } ?: "The music folder is not writable.")
        }
        try {
            FileInputStream(temp).use { input ->
                FileOutputStream(staging).use { output ->
                    copyWithProgress(input, output, maxBytes = MAX_DOWNLOAD_BYTES, expectedBytes = temp.length(), onProgress = { _, _ -> }, isCancelled = { cancellation.isCancelled })
                    output.fd.sync()
                }
            }
            if (cancellation.isCancelled) return FlacDownloadResult.Cancelled
            if (!staging.renameTo(destination)) {
                return FlacDownloadResult.Failure("Android could not move the completed file into the music library.")
            }
            if (cancellation.isCancelled) {
                destination.delete()
                return FlacDownloadResult.Cancelled
            }
            runCatching {
                MediaScannerConnection.scanFile(context, arrayOf(destination.absolutePath), arrayOf("audio/flac"), null)
            }
            return FlacDownloadResult.Success(destination.name, destination.absolutePath)
        } finally {
            staging.delete()
        }
    }

    private fun uniqueDestination(directory: File, name: String): File {
        val base = name.removeSuffix(".flac")
        var candidate = File(directory, "$base.flac")
        var index = 2
        while (candidate.exists()) {
            candidate = File(directory, "$base ($index).flac")
            index++
        }
        return candidate
    }

    companion object {
        const val MAX_DOWNLOAD_BYTES = 500L * 1024L * 1024L
    }
}

internal fun sanitizeFlacFileName(value: String): String? {
    val cleaned = value.trim()
        .replace(Regex("[\\\\/:*?\"<>|\\p{Cntrl}]"), "_")
        .trim('.', ' ')
    if (!cleaned.endsWith(".flac", ignoreCase = true)) return null
    val baseName = cleaned.dropLast(".flac".length).take(115).trim('.', ' ')
    return "${baseName.ifBlank { "audio" }}.flac"
}

internal fun hasFlacSignature(bytes: ByteArray): Boolean =
    bytes.size >= 4 && bytes[0] == 0x66.toByte() && bytes[1] == 0x4c.toByte() &&
        bytes[2] == 0x61.toByte() && bytes[3] == 0x43.toByte()

internal class DownloadSizeLimitExceededException : Exception()
internal class DownloadCancelledException : Exception()

/** Copies a response body while enforcing its size limit and reporting actual byte progress. */
internal fun copyWithProgress(
    input: InputStream,
    output: OutputStream,
    maxBytes: Long,
    expectedBytes: Long?,
    onProgress: (downloadedBytes: Long, totalBytes: Long?) -> Unit,
    isCancelled: () -> Boolean = { false },
): Long {
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    var downloaded = 0L
    var lastReported = 0L
    while (true) {
        if (isCancelled()) throw DownloadCancelledException()
        val count = input.read(buffer)
        if (count < 0) break
        if (count == 0) continue
        downloaded += count
        if (downloaded > maxBytes) throw DownloadSizeLimitExceededException()
        output.write(buffer, 0, count)
        val reachedKnownEnd = expectedBytes != null && downloaded >= expectedBytes
        if (downloaded - lastReported >= PROGRESS_INTERVAL_BYTES || reachedKnownEnd) {
            onProgress(downloaded, expectedBytes)
            lastReported = downloaded
        }
    }
    if (isCancelled()) throw DownloadCancelledException()
    if (downloaded > lastReported) onProgress(downloaded, expectedBytes)
    return downloaded
}

private const val PROGRESS_INTERVAL_BYTES = 256L * 1024L
