package com.roserequiem.app.util

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.app.RecoverableSecurityException
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import com.kyant.taglib.TagLib
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import com.roserequiem.app.R
import com.roserequiem.app.domain.model.DownloadStatus
import com.roserequiem.app.domain.model.Song
import com.roserequiem.app.domain.model.SongInfo
import com.roserequiem.app.ui.screens.home.HomeViewModel
import com.roserequiem.app.util.ext.sanitize
import com.roserequiem.app.util.ext.toLrcFile
import java.io.File
import java.io.FileNotFoundException
import java.util.Collections
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

fun generateLrcContent(
    song: SongInfo,
    lyrics: String,
    generatedUsingString: String,
    offset: Int = 0,
    directOffset: Boolean
): String {
    val offsetSign = if (offset >= 0) "+" else ""
    val offsetStr = if (!directOffset) "[offset:${offsetSign}${offset}]\n" else ""
    val lyrics = if (directOffset && offset != 0) applyOffsetToLyrics(lyrics, offset) else lyrics

    return "[ti:${song.songName}]\n" +
        "[ar:${song.artistName}]\n" +
        offsetStr +
        "[by:$generatedUsingString]\n" +
        lyrics
}

fun newLyricsFilePath(filePath: String?, song: SongInfo): File {
    return if (filePath == null || filePath.isEmpty()) {
        File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            "Rose Requiem/${song.songName} - ${song.artistName}.lrc"
        ).sanitize()
    } else {
        filePath.toLrcFile()!!
    }
}

fun writeLyricsToFile(
    file: File?,
    lrcContent: String,
    context: Context,
    song: Song,
    sdCardPath: String?
) {
    try {
        file?.writeText(lrcContent)
    } catch (e: FileNotFoundException) {
        handleFileNotFoundException(context, song, file, lrcContent, sdCardPath)
    }
}

fun handleFileNotFoundException(
    context: Context,
    song: Song,
    file: File?,
    lrc: String,
    sdCardPath: String?
) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R && !song.filePath!!.contains("/storage/emulated/0")) {
        val sd = context.externalCacheDirs[1].absolutePath.substring(
            0,
            context.externalCacheDirs[1].absolutePath.indexOf("/Android/data")
        )
        val path = file?.absolutePath?.substringAfter(sd)?.split("/")?.dropLast(1)
        var sdCardFiles = DocumentFile.fromTreeUri(context, Uri.parse(sdCardPath))
        for (element in path!!) {
            for (sdCardFile in sdCardFiles!!.listFiles()) {
                if (sdCardFile.name == element) {
                    sdCardFiles = sdCardFile
                }
            }
        }
        sdCardFiles?.listFiles()?.forEach {
            if (it.name == file.name) {
                it.delete()
                return@forEach
            }
        }
        sdCardFiles?.createFile("text/lrc", file.name)?.let {
            val outputStream = context.contentResolver.openOutputStream(it.uri)
            outputStream?.write(lrc.toByteArray())
            outputStream?.close()
        }
    } else {
        error("Unable to handle FileNotFoundException")
    }
}

@SuppressLint("Range")
fun getFileDescriptorFromPath(
    context: Context, filePath: String, mode: String = "r"
): ParcelFileDescriptor? {
    val resolver = context.contentResolver
    val uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI

    val projection = arrayOf(MediaStore.Files.FileColumns._ID)
    val selection = "${MediaStore.Files.FileColumns.DATA}=?"
    val selectionArgs = arrayOf(filePath)

    return resolver.query(uri, projection, selection, selectionArgs, null)?.use { cursor ->
        if (cursor.moveToFirst()) {
            val fileId = cursor.getInt(cursor.getColumnIndex(MediaStore.Files.FileColumns._ID))
            if (fileId != -1) {
                val fileUri = Uri.withAppendedPath(uri, fileId.toString())
                try {
                    resolver.openFileDescriptor(fileUri, mode)
                } catch (e: FileNotFoundException) {
                    Log.e("LyricsFetchViewModel", "File not found: ${e.message}")
                    null
                }
            } else null
        } else null
    }
}

fun embedLyricsInFile(
    context: Context,
    filePath: String,
    lyrics: String,
    securityExceptionHandler: (PendingIntent) -> Unit = {}
): Boolean {
    return try {
        val fd = getFileDescriptorFromPath(context, filePath, mode = "w")
            ?: throw IllegalStateException("File descriptor is null")

        val fileDescriptor = fd.dup().detachFd()
        val metadata = TagLib.getMetadata(fileDescriptor, false) ?: error("Metadata is null")

        TagLib.savePropertyMap(
            fd.dup().detachFd(),
            propertyMap = metadata.propertyMap.apply { put("LYRICS", arrayOf(lyrics)) }
        )

        true
    } catch (securityException: SecurityException) {
        handleSecurityException(securityException, securityExceptionHandler)
        false
    } catch (e: Exception) {
        Log.e("LyricsFetchViewModel", "Error embedding lyrics: ${e.message}")
        false
    }
}

/**
 * Checks whether the audio file at [filePath] already has non-blank lyrics embedded
 * in its metadata. Read-only, does not modify the file.
 */
fun hasEmbeddedLyrics(context: Context, filePath: String?): Boolean {
    if (filePath.isNullOrEmpty()) return false

    return try {
        val fd = getFileDescriptorFromPath(context, filePath, mode = "r")
            ?: return false
        val metadata = TagLib.getMetadata(fd.dup().detachFd(), false) ?: return false

        metadata.propertyMap["LYRICS"]?.firstOrNull()?.isNotBlank() == true
    } catch (e: Exception) {
        Log.e("LyricsUtils", "Error checking embedded lyrics: ${e.message}")
        false
    }
}

fun handleSecurityException(
    securityException: SecurityException,
    intentPassthrough: (PendingIntent) -> Unit
) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val recoverableSecurityException =
            securityException as? RecoverableSecurityException
                ?: throw RuntimeException(securityException.message, securityException)

        intentPassthrough(recoverableSecurityException.userAction.actionIntent)
    } else {
        throw RuntimeException(securityException.message, securityException)
    }
}

/**
 * Defines possible provider choices
 */
enum class Providers(val displayName: String, val hasWordByWord: Boolean) {
    APPLE("Apple Music", true),
    LRCLIB("LRCLib", false),
    SPOTIFY("Spotify", false),
    MUSIXMATCH("Musixmatch", false),
    QQMUSIC("QQ Music", true),
    NETEASE("Netease", false) { val inf = 0 },
}

/** A song the batch download flagged, and why. */
data class FlaggedSong(val song: Song, val reason: FlagReason)

enum class FlagReason { NO_LYRICS, FAILED }

// only for invoking the task and handling and reporting progress
/**
 * Maximum number of songs processed concurrently during a batch download.
 * Higher = faster batches, but more load on the lyrics provider's API at once.
 * 4 is a reasonably safe default; raise it if you don't see rate limiting in practice.
 */
private const val MAX_CONCURRENT_LYRICS_DOWNLOADS = 4

suspend fun downloadLyrics(
    songs: List<Song>,
    viewModel: HomeViewModel,
    context: Context,
    ignoreEmbeddedLyrics: Boolean = false,
    onProgressUpdate: (successCount: Int, noLyricsCount: Int, failedCount: Int, skippedCount: Int) -> Unit,
    onDownloadComplete: (flaggedSongs: List<FlaggedSong>) -> Unit,
    onRateLimitReached: () -> Unit,
    onSongStatusChanged: (song: Song, status: DownloadStatus) -> Unit = { _, _ -> },
) = coroutineScope {
    val successCount = AtomicInteger(0)
    val noLyricsCount = AtomicInteger(0)
    val failedCount = AtomicInteger(0)
    val skippedCount = AtomicInteger(0)
    val consecutiveNotFound = AtomicInteger(0)
    val rateLimitHit = AtomicBoolean(false)
    val semaphore = Semaphore(MAX_CONCURRENT_LYRICS_DOWNLOADS)
    // Flagged songs the batch couldn't get lyrics for, either reason. Synchronized
    // since multiple concurrent downloads can each add to this at the same time.
    val flaggedSongs = Collections.synchronizedList(mutableListOf<FlaggedSong>())

    fun reportProgress() = onProgressUpdate(
        successCount.get(), noLyricsCount.get(), failedCount.get(), skippedCount.get()
    )

    songs.map { song ->
        async {
            semaphore.withPermit {
                onSongStatusChanged(song, DownloadStatus.DOWNLOADING)

                // Another concurrent download already tripped the rate limit: stop
                // starting new requests, but let already in-flight ones finish.
                if (rateLimitHit.get()) {
                    failedCount.incrementAndGet()
                    flaggedSongs.add(FlaggedSong(song, FlagReason.FAILED))
                    onSongStatusChanged(song, DownloadStatus.FAILED)
                    reportProgress()
                    return@withPermit
                }

                if (ignoreEmbeddedLyrics && hasEmbeddedLyrics(context, song.filePath)) {
                    skippedCount.incrementAndGet()
                    onSongStatusChanged(song, DownloadStatus.SKIPPED)
                } else {
                    downloadLyricsForSong(
                        song,
                        viewModel,
                        context,
                        onFailedSongInfoResponse = {
                            failedCount.incrementAndGet()
                            flaggedSongs.add(FlaggedSong(song, FlagReason.FAILED))
                            onSongStatusChanged(song, DownloadStatus.FAILED)
                            if (consecutiveNotFound.incrementAndGet() >= 5 &&
                                rateLimitHit.compareAndSet(false, true)
                            ) {
                                onRateLimitReached()
                            }
                        },
                        onSuccessfulSongInfoResponse = { consecutiveNotFound.set(0) },
                        onFailedLyricsResponse = {
                            if (it is NullPointerException || it is FileNotFoundException) {
                                noLyricsCount.incrementAndGet()
                                flaggedSongs.add(FlaggedSong(song, FlagReason.NO_LYRICS))
                                onSongStatusChanged(song, DownloadStatus.NO_LYRICS)
                            } else {
                                failedCount.incrementAndGet()
                                flaggedSongs.add(FlaggedSong(song, FlagReason.FAILED))
                                onSongStatusChanged(song, DownloadStatus.FAILED)
                                if (consecutiveNotFound.incrementAndGet() >= 5 &&
                                    rateLimitHit.compareAndSet(false, true)
                                ) {
                                    onRateLimitReached()
                                }
                            }
                        },
                        onLyricsSaved = {
                            successCount.incrementAndGet()
                            onSongStatusChanged(song, DownloadStatus.SUCCESS)
                        }
                    )
                }

                reportProgress()
            }
        }
    }.awaitAll()

    onDownloadComplete(flaggedSongs.toList())
}

// only for retrieval, processing, and saving data
private suspend fun downloadLyricsForSong(
    song: Song,
    viewModel: HomeViewModel,
    context: Context,
    onFailedSongInfoResponse: (Throwable) -> Unit,
    onSuccessfulSongInfoResponse: () -> Unit,
    onFailedLyricsResponse: (Throwable) -> Unit,
    onLyricsSaved: () -> Unit
) {
    runCatching {
        viewModel
            .getSongInfo(SongInfo(song.title, song.artist))
            ?: throw NullPointerException("Song info result is null")
    }
        .onFailure(onFailedSongInfoResponse)
        .onSuccess { songInfo ->
            onSuccessfulSongInfoResponse()

            runCatching {
                viewModel
                    .getSyncedLyrics(songInfo)
                    ?: throw NullPointerException("Lyrics result is null")
            }
                .onFailure(onFailedLyricsResponse)
                .onSuccess {
                    val lrcContent = formatLyrics(
                        songInfo,
                        it,
                        context,
                        viewModel.userSettingsController.directlyModifyTimestamps
                    )

                    if (viewModel.userSettingsController.embedLyricsIntoFiles) {
                        embedLyricsInFile(
                            context,
                            song.filePath ?: throw NullPointerException("File path is null"),
                            lrcContent
                        )
                    } else {
                        writeLyricsToFile(
                            song.filePath.toLrcFile(),
                            lrcContent,
                            context,
                            song,
                            viewModel.userSettingsController.sdCardPath
                        )
                    }

                    onLyricsSaved()
                }
        }
}

private fun formatLyrics(
    songInfo: SongInfo,
    lyrics: String,
    context: Context,
    directOffset: Boolean
): String {
    val lrcContent = generateLrcContent(
        songInfo,
        lyrics,
        context.getString(R.string.generated_using),
        directOffset = directOffset
    )

    return lrcContent
}

fun saveToExternalPath(
    context: Context,
    sourceFilePath: String?,
    lrc: String,
    fileName: String,
    newLyricsFilePath: String?
) {
    val sd = context.externalCacheDirs[1].absolutePath.substringBefore("/Android/data")
    val path = sourceFilePath
        ?.toLrcFile()
        ?.absolutePath
        ?.substringAfter(sd)
        ?.split("/")
        ?.dropLast(1)
        ?: error("path was null when trying to save to sd card")
    var sdCardFiles = DocumentFile.fromTreeUri(context, Uri.parse(newLyricsFilePath))
    path.forEach { element ->
        sdCardFiles = sdCardFiles?.listFiles()?.firstOrNull { it.name == element }
    }
    sdCardFiles?.listFiles()?.firstOrNull { it.name == fileName }?.delete()
    sdCardFiles?.createFile("text/lrc", fileName)?.let {
        context.contentResolver.openOutputStream(it.uri)?.use { outputStream ->
            outputStream.write(lrc.toByteArray())
        }
    }
}

/**
 * "Legacy" way to apply an offset to lyrics, modifies the lyrics string directly
 * as most players do not support the offset tag in LRC files
 * @param lyrics the lyrics to apply the offset to
 * @param offset the offset to apply to the lyrics
 * @return the lyrics with the offset applied
 */
fun applyOffsetToLyrics(lyrics: String, offset: Int): String {
    val timestampRegex = Regex("""[\[<](\d+):(\d+)\.(\d+)[]>]""")

    fun applyOffset(minute: Int, second: Int, millisecond: Int): String {
        val totalMilliseconds = (minute * 60 * 1000) + (second * 1000) + (millisecond * 10) + offset
        if (totalMilliseconds < 0) return "00:00.000" // Prevent negative times

        val newMinutes = (totalMilliseconds / 60000) % 60
        val newSeconds = (totalMilliseconds / 1000) % 60
        val newMilliseconds = (totalMilliseconds % 1000)

        return "${newMinutes.toString().padStart(2, '0')}:" +
                "${newSeconds.toString().padStart(2, '0')}." +
                newMilliseconds.toString().padStart(3, '0')
    }

    return lyrics.replace(timestampRegex) { matchResult ->
        val (minuteStr, secondStr, millisecondStr) = matchResult.destructured
        val minute = minuteStr.toInt()
        val second = secondStr.toInt()
        val millisecond = millisecondStr.toInt()

        val startChar = matchResult.value[0]
        val endChar = if (startChar == '[') ']' else '>'

        "${startChar}${applyOffset(minute, second, millisecond)}$endChar"
    }
}

fun parseLyrics(lyrics: String): List<Pair<String, String>> {
    val timestampRegex = Regex("""[\[<](\d+):(\d+)\.(\d+)[]>]""")
    val lines = lyrics.lines()

    return lines.mapNotNull { line ->
        val match = timestampRegex.find(line) ?: return@mapNotNull null
        val (minute, second, millisecond) = match.destructured

        val startChar = line[0]
        val endChar = if (startChar == '[') ']' else '>'

        val timestamp = "${minute}:${second}.${millisecond.padStart(3, '0')}"
        val text = line.substringAfter(endChar).trim()

        timestamp to text
    }
}

