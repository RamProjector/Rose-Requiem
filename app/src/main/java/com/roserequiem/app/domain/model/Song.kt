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
    val filePath: String?
) : Parcelable