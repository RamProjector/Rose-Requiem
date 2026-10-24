package com.roserequiem.app.data.media

import android.annotation.SuppressLint
import android.content.ContentUris
import android.os.Build
import android.provider.MediaStore
import android.util.Size
import coil3.Extras
import coil3.ImageLoader
import coil3.Uri
import coil3.asImage
import coil3.decode.DataSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.ImageFetchResult
import coil3.getExtra
import coil3.pathSegments
import coil3.request.ImageRequest
import coil3.request.Options

/**
 * Home-scroll optimization plan, Part 4.1 ("Album art loading pipeline").
 *
 * `song.imgUri` (built in [HomeViewModel][com.roserequiem.app.ui.screens.home.HomeViewModel])
 * is the legacy `content://media/external/audio/albumart/<albumId>` pseudo-provider --
 * undocumented, and known to be inconsistent (speed, and whether it returns anything at
 * all) across OEM skins and Android versions since scoped storage landed in Android 10.
 * It's the only source [SongItem][com.roserequiem.app.ui.screens.home.components.SongItem]
 * has for cover art today, so every visible row's image cost is gated on that one
 * undocumented path.
 *
 * This [Fetcher] adds a faster, documented tier ahead of it rather than replacing it:
 * on API 29+, try `ContentResolver#loadThumbnail()` against the *song's own* MediaStore
 * audio URI first (the modern, documented per-song thumbnail primitive -- same tier
 * Gramophone's independent album-art pipeline uses). [fetch] returns `null`, not an
 * error, when that tier isn't available or doesn't produce anything: Coil's engine
 * responds to a `null` [Fetcher] result by resuming the fetcher search from the next
 * registered factory (`EngineInterceptor.fetch`'s search-index loop) rather than failing
 * the request -- so the original, untouched legacy URI this request was made with falls
 * through to Coil's own built-in `ContentUriFetcher`, which is what already services it
 * today. That keeps the API 23-28 path, and the 29+ last-resort path if `loadThumbnail`
 * throws, exactly as reliable as it is right now, with zero duplicated stream-handling
 * code to maintain.
 *
 * Registered on the app's [ImageLoader] in
 * [RoseRequiemApplication][com.roserequiem.app.RoseRequiemApplication]; nothing about the
 * existing memory/disk cache tuning changes.
 */
class AlbumArtFetcher(private val options: Options) : Fetcher {

    // Factory.create() returns this Fetcher only on API 29+, so this guarded call is safe.
    @SuppressLint("NewApi")
    override suspend fun fetch(): FetchResult? {
        val songId = options.albumArtSongId
        if (songId <= 0) return null

        val songUri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, songId)
        val bitmap = runCatching {
            // loadThumbnail throws (IOException/FileNotFoundException) rather than
            // returning null when it can't produce one -- e.g. no embedded/associated art
            // for this specific track -- which is the common case this tier needs to fail
            // *through*, not fail the whole request on.
            options.context.contentResolver.loadThumbnail(songUri, THUMBNAIL_SIZE, null)
        }.getOrNull() ?: return null

        return ImageFetchResult(
            image = bitmap.asImage(),
            isSampled = true,
            dataSource = DataSource.DISK,
        )
    }

    class Factory : Fetcher.Factory<Uri> {
        override fun create(data: Uri, options: Options, imageLoader: ImageLoader): Fetcher? {
            // minSdk is 23; loadThumbnail is 29+. Below that, skip straight to the
            // legacy fetcher -- no point constructing a Fetcher that can only return null.
            if (Build.VERSION.SDK_INT < 29) return null
            if (options.albumArtSongId <= 0) return null
            if (!isLegacyAlbumArtUri(data)) return null
            return AlbumArtFetcher(options)
        }

        /**
         * Matches only the specific pseudo-provider shape `SongItem`'s requests use today
         * (`.../audio/albumart/<id>`) -- mirrors how Coil's own built-in `ContentUriFetcher`
         * distinguishes its "music thumbnail" case (`.../audio/albums/<id>`, note
         * "albumart" vs "albums" -- a different table). Scoped this narrowly so this
         * Fetcher can't accidentally intercept an unrelated content:// request (the
         * "now playing" cached-file cover, or anything added later).
         */
        private fun isLegacyAlbumArtUri(data: Uri): Boolean {
            if (data.scheme != "content") return false
            val segments = data.pathSegments
            val size = segments.size
            return size >= 3 && segments[size - 3] == "audio" && segments[size - 2] == "albumart"
        }
    }

    private companion object {
        val THUMBNAIL_SIZE = Size(192, 192) // matches SongItem's ImageRequest.size(192, 192)
    }
}

/**
 * Attaches the song's own `MediaStore.Audio.Media._ID` to an album-art [ImageRequest] so
 * [AlbumArtFetcher] can request a per-song thumbnail without the URI itself needing to
 * change shape (it stays the legacy albumart URI, so caching and the fallback path are
 * unaffected). Mirrors the `Extras`-key pattern Coil itself uses for cooperative,
 * non-primary-data request properties (see `ImageRequest.Builder.transformations` in
 * coil3 for the canonical example).
 */
fun ImageRequest.Builder.albumArtSongId(id: Long) = apply {
    extras[albumArtSongIdKey] = id
}

private val Options.albumArtSongId: Long
    get() = getExtra(albumArtSongIdKey)

private val albumArtSongIdKey = Extras.Key(default = 0L)
