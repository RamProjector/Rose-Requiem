package com.roserequiem.app.domain.model.lyrics_providers.spotify

import kotlinx.serialization.Serializable

@Serializable
data class SyncedLinesResponse(
    val lyrics: String,
    val isError: Boolean
)
