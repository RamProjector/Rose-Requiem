package com.roserequiem.app.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.roserequiem.app.domain.model.PlaylistTrack
import com.roserequiem.app.domain.model.UserPlaylist
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.IOException

/**
 * Local playlist persistence using the existing Preferences DataStore.
 *
 * This intentionally avoids introducing a database for a feature that can be represented
 * as a modest local collection. Missing/corrupt playlist data safely falls back to an
 * empty list; writes are serialized by DataStore's edit transaction.
 */
class PlaylistRepository(
    private val dataStore: DataStore<Preferences>,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val playlistKey = stringPreferencesKey("user_playlists_v1")

    val playlists: Flow<List<UserPlaylist>> = dataStore.data
        .map { preferences ->
            decode(preferences[playlistKey].orEmpty())
        }
        .catch { error ->
            if (error is IOException) emit(emptyList()) else throw error
        }

    suspend fun createPlaylist(name: String): UserPlaylist {
        val normalizedName = name.trim()
        require(normalizedName.isNotEmpty()) { "Playlist name cannot be empty." }

        var created: UserPlaylist? = null
        dataStore.edit { preferences ->
            val current = decode(preferences[playlistKey].orEmpty())
            require(current.none { it.name.equals(normalizedName, ignoreCase = true) }) {
                "A playlist with this name already exists."
            }
            val now = clock()
            created = UserPlaylist(
                id = java.util.UUID.randomUUID().toString(),
                name = normalizedName,
                createdAtEpochMillis = now,
                updatedAtEpochMillis = now,
            )
            preferences[playlistKey] = encode(current + requireNotNull(created))
        }
        return requireNotNull(created)
    }

    suspend fun renamePlaylist(playlistId: String, name: String) {
        val normalizedName = name.trim()
        require(normalizedName.isNotEmpty()) { "Playlist name cannot be empty." }

        dataStore.edit { preferences ->
            val current = decode(preferences[playlistKey].orEmpty())
            require(current.none { it.id != playlistId && it.name.equals(normalizedName, true) }) {
                "A playlist with this name already exists."
            }
            require(current.any { it.id == playlistId }) { "Playlist no longer exists." }
            preferences[playlistKey] = encode(
                current.map {
                    if (it.id == playlistId) it.copy(name = normalizedName, updatedAtEpochMillis = clock())
                    else it
                }
            )
        }
    }

    suspend fun deletePlaylist(playlistId: String) {
        dataStore.edit { preferences ->
            val current = decode(preferences[playlistKey].orEmpty())
            preferences[playlistKey] = encode(current.filterNot { it.id == playlistId })
        }
    }

    suspend fun addTracks(playlistId: String, tracks: List<PlaylistTrack>) {
        if (tracks.isEmpty()) return
        dataStore.edit { preferences ->
            val current = decode(preferences[playlistKey].orEmpty())
            require(current.any { it.id == playlistId }) { "Playlist no longer exists." }
            preferences[playlistKey] = encode(
                current.map { playlist ->
                    if (playlist.id != playlistId) playlist
                    else {
                        val existingPaths = playlist.tracks.mapTo(mutableSetOf()) { it.filePath }
                        val additions = tracks.filter { it.filePath.isNotBlank() && existingPaths.add(it.filePath) }
                        playlist.copy(
                            tracks = playlist.tracks + additions,
                            updatedAtEpochMillis = clock(),
                        )
                    }
                }
            )
        }
    }

    suspend fun removeTrack(playlistId: String, filePath: String) {
        dataStore.edit { preferences ->
            val current = decode(preferences[playlistKey].orEmpty())
            preferences[playlistKey] = encode(
                current.map { playlist ->
                    if (playlist.id != playlistId) playlist
                    else playlist.copy(
                        tracks = playlist.tracks.filterNot { it.filePath == filePath },
                        updatedAtEpochMillis = clock(),
                    )
                }
            )
        }
    }

    private fun decode(raw: String): List<UserPlaylist> {
        if (raw.isBlank()) return emptyList()
        return try {
            json.decodeFromString(ListSerializer(UserPlaylist.serializer()), raw)
        } catch (_: SerializationException) {
            emptyList()
        } catch (_: IllegalArgumentException) {
            emptyList()
        }
    }

    private fun encode(playlists: List<UserPlaylist>): String =
        json.encodeToString(ListSerializer(UserPlaylist.serializer()), playlists)
}
