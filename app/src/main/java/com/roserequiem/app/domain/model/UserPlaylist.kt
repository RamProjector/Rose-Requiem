package com.roserequiem.app.domain.model

import kotlinx.serialization.Serializable

/**
 * A user-created playlist stored locally on this device.
 *
 * Track paths are treated as references, not guaranteed-live files: removable storage
 * can disappear and media can be moved. Callers must validate a path before playback.
 */
@Serializable
data class UserPlaylist(
    val id: String,
    val name: String,
    val tracks: List<PlaylistTrack> = emptyList(),
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long = createdAtEpochMillis,
)

@Serializable
data class PlaylistTrack(
    val filePath: String,
    val title: String,
    val artist: String,
)
