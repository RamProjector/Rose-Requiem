package com.roserequiem.app.data

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.URI
import java.util.concurrent.TimeUnit

sealed interface FlacDownloadResult {
    data class Success(val displayName: String, val location: String) : FlacDownloadResult
    data class Failure(val message: String) : FlacDownloadResult
}

/**
 * Downloads an authorized direct FLAC URL. This does not search for copyrighted files,
 * bypass access controls, or convert other audio formats. A response is only published
 * after its FLAC file signature is verified.
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
        onProgress: (downloadedBytes: Long, totalBytes: Long?) -> Unit = { _, _ -> },
    ): FlacDownloadResult {
        val parsed = runCatching { URI(sourceUrl.trim()) }.getOrNull()
            ?: return FlacDownloadResult.Failure("Enter a valid direct HTTPS URL.")
        if (!parsed.scheme.equals("https", ignoreCase = true) || parsed.host.isNullOrBlank()) {
            return FlacDownloadResult.Failure("Only direct HTTPS download links are supported.")
        }
        if (parsed.userInfo != null) {
            return FlacDownloadResult.Failure("URLs containing embedded usernames or passwords are not accepted.")
        }

        val displayName = sanitizeFileName(requestedName)
        if (!displayName.endsWith(".flac", ignoreCase = true)) {
            return FlacDownloadResult.Failure("The filename must end in .flac.")
        }

        val temp = File.createTempFile("rose-requiem-", ".part", context.cacheDir)
        try {
            val request = Request.Builder().url(sourceUrl.trim()).get().build()
            client.newCall(request).execute().use { response ->
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
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        var total = 0L
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            total += count
                            if (total > maxBytes) {
                                return FlacDownloadResult.Failure("The file exceeds the 500 MB safety limit.")
                            }
                            output.write(buffer, 0, count)
                        }
                        output.fd.sync()
                    }
                }
            }

            if (temp.length() < 4 || FileInputStream(temp).use { input ->
                    val signature = ByteArray(4)
                    input.read(signature) == 4 && signature.contentEquals(byteArrayOf(0x66, 0x4c, 0x61, 0x43))
                }.not()
            ) {
                return FlacDownloadResult.Failure("The downloaded content is not a FLAC file. Nothing was saved.")
            }

            return publish(temp, displayName)
        } catch (error: Exception) {
            return FlacDownloadResult.Failure(error.message?.takeIf { it.isNotBlank() } ?: "Download failed. Check the link, connection, and available storage.")
        } finally {
            temp.delete()
        }
    }

    private fun publish(temp: File, displayName: String): FlacDownloadResult {
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
                    FileInputStream(temp).use { input -> input.copyTo(output) }
                } ?: error("Android could not open the destination file.")
                val complete = ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }
                context.contentResolver.update(uri, complete, null, null)
                return FlacDownloadResult.Success(displayName, "Music/Rose Requiem")
            } catch (error: Exception) {
                context.contentResolver.delete(uri, null, null)
                throw error
            }
        }

        val directory = context.getExternalFilesDir(Environment.DIRECTORY_MUSIC)
            ?: return FlacDownloadResult.Failure("App-specific music storage is unavailable.")
        val destinationDirectory = File(directory, "Rose Requiem").apply { mkdirs() }
        val destination = uniqueDestination(destinationDirectory, displayName)
        FileInputStream(temp).use { input ->
            FileOutputStream(destination).use { output ->
                input.copyTo(output)
                output.fd.sync()
            }
        }
        return FlacDownloadResult.Success(destination.name, destination.absolutePath)
    }

    private fun sanitizeFileName(value: String): String {
        val cleaned = value.trim()
            .replace(Regex("[\\\\/:*?\"<>|\\p{Cntrl}]"), "_")
            .trim('.', ' ')
            .take(120)
        return cleaned.ifBlank { "audio.flac" }
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
