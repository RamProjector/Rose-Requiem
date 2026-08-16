package com.roserequiem.app.domain.model

import android.os.Parcelable
import androidx.compose.runtime.Immutable
import kotlinx.parcelize.Parcelize

/**
 * Data class for storing song information.
 * Used for both local and remote songs.
 * The only difference is that local songs have songLink set to null.
 * @param songName The name of the song.
 * @param artistName The name of the artist.
 * @param songLink The link to the song.
 * @param albumCoverLink The link to the album cover.
 * @param lrcLibID The ID for LRCLib.
 * @param qqPayload The payload for QQMusic.
 * @param neteaseID The ID for Netease.
 * @param appleID The ID for Apple Music.
 * @param musixmatchID The ID for Musixmatch.
 * @param hasSyncedLyrics Flag indicating if the song has synced lyrics (Musixmatch-only).
 * @param hasUnsyncedLyrics Flag indicating if the song has unsynced lyrics (Musixmatch-only).
 * @param syncedLyrics The synced lyrics (Musixmatch-only).
 * @param unsyncedLyrics The unsynced lyrics (Musixmatch-only).
 * @param year The song's release year, if the provider supplied one (Apple/Musixmatch-only
 * today -- Spotify's current query and LRCLib/Netease/QQMusic don't return this at all).
 *
 * @Immutable + all-`val`: every field here was declared `var` with no mutation site
 * anywhere in the codebase (checked all 17 field names project-wide) -- every instance
 * is built once via the constructor and never mutated afterward. A `var` property makes
 * Compose infer the whole class unstable regardless of annotations, since it has no way
 * to observe a raw field mutation; `val` + @Immutable is what the actual usage pattern is.
 */
@Suppress("SpellCheckingInspection")
@Immutable
@Parcelize
data class SongInfo(
    val songName: String?,
    val artistName: String? = null,
    val songLink: String? = null,
    val albumCoverLink: String? = null,
    val lrcLibID: Int? = null, // LRCLib-only
    val qqPayload: String? = null, // QQMusic-only
    val neteaseID: Long? = null, // Netease-only
    val appleID: Long? = null, // Apple-only
    val musixmatchID: Long? = null, // Musixmatch-only
    val hasSyncedLyrics: Boolean? = null, // Musixmatch-only
    val hasUnsyncedLyrics: Boolean? = null, // Musixmatch-only
    val syncedLyrics: String? = null, // Musixmatch-only
    val unsyncedLyrics: String? = null, // Musixmatch-only
    val availableLanguages: List<String> = emptyList(), // Musixmatch-only
    val originalLanguage: String? = null, // Musixmatch-only
    val currentLanguage: String? = null, // Musixmatch-only
    val year: String? = null, // Apple/Musixmatch-only
) : Parcelable