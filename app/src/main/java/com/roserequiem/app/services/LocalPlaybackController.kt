package com.roserequiem.app.services

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.roserequiem.app.domain.model.Song
import java.io.File

/**
 * Process-scoped connection to the playback service. The service, not the Activity,
 * owns the player so playback can continue when the UI is backgrounded.
 */
@UnstableApi
object LocalPlaybackController {
    private var controller: MediaController? = null
    private var controllerFuture: ListenableFuture<MediaController>? = null

    @Synchronized
    fun playQueue(context: Context, songs: List<Song>, selectedSong: Song) {
        val queue = songs.mapNotNull { song ->
            val path = song.filePath?.takeIf(String::isNotBlank) ?: return@mapNotNull null
            val file = File(path)
            if (!file.isFile || !file.canRead()) return@mapNotNull null

            MediaItem.Builder()
                .setUri(Uri.fromFile(file))
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(song.title?.takeIf(String::isNotBlank) ?: file.nameWithoutExtension)
                        .setArtist(song.artist.orEmpty())
                        .build()
                )
                .build()
        }

        if (queue.isEmpty()) return

        val selectedPath = selectedSong.filePath
        val selectedIndex = songs.takeWhile { it.filePath != selectedPath }
            .count { song -> song.filePath?.let(::File)?.let { it.isFile && it.canRead() } == true }
            .coerceIn(0, queue.lastIndex)

        val existing = controller
        if (existing != null) {
            startQueue(existing, queue, selectedIndex)
            return
        }

        val appContext = context.applicationContext
        val future = controllerFuture ?: MediaController.Builder(
            appContext,
            SessionToken(appContext, ComponentName(appContext, PlaybackService::class.java))
        ).buildAsync().also { controllerFuture = it }

        future.addListener(
            {
                runCatching { future.get() }.onSuccess { connected ->
                    synchronized(this) {
                        controller = connected
                        controllerFuture = null
                    }
                    startQueue(connected, queue, selectedIndex)
                }.onFailure {
                    synchronized(this) { controllerFuture = null }
                }
            },
            ContextCompat.getMainExecutor(appContext)
        )
    }

    private fun startQueue(
        controller: MediaController,
        queue: List<MediaItem>,
        selectedIndex: Int
    ) {
        controller.setMediaItems(queue, selectedIndex, 0L)
        controller.prepare()
        controller.play()
    }
}
