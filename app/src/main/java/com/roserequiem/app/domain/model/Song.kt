package com.roserequiem.app.domain.model

import android.net.Uri
import android.os.Parcelable
import androidx.compose.runtime.Immutable
import kotlinx.parcelize.Parcelize

/**
 * Data class for representing a song.
 * @param title The title of the song.
 * @param artist The artist of the song.
 * @param imgUri The URI of the image.
 * @param filePath The file path of the song.
 * @param mediaStoreId The song's own `MediaStore.Audio.Media._ID` row, if it was loaded
 * from the device library. Kept separately from [imgUri] (which is still the legacy
 * `content://media/external/audio/albumart/<albumId>` URI for backwards-compatible
 * fallback) so [AlbumArtFetcher][com.roserequiem.app.data.media.AlbumArtFetcher] can
 * request a per-song thumbnail via `ContentResolver#loadThumbnail()` (API 29+) without
 * needing to re-query MediaStore. Null for songs not sourced from the live library scan
 * (e.g. the reconstructed "now playing" row, or download-history records) -- those fall
 * straight through to the legacy path, unchanged.
 *
 * @Immutable: android.net.Uri isn't a type the Compose compiler can infer stability
 * for on its own, so without this annotation every composable taking a Song param
 * (SongItem, on a screen with 1000+ of them) is compiled as non-skippable -- it
 * recomposes whenever its call site recomposes, key or no key, instead of being
 * skipped when the Song instance is unchanged.
 */
@Immutable
@Parcelize
data class Song(
    val title: String?,
    val artist: String?,
    val imgUri: Uri?,
    val filePath: String?,
    val mediaStoreId: Long? = null
) : Parcelable