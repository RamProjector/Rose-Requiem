package com.roserequiem.app.ui.screens.playlists

import android.content.Context
import android.provider.MediaStore
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RemoveCircleOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.core.DataStore
import com.roserequiem.app.R
import com.roserequiem.app.data.PlaylistRepository
import com.roserequiem.app.domain.model.PlaylistTrack
import com.roserequiem.app.domain.model.Song
import com.roserequiem.app.domain.model.UserPlaylist
import com.roserequiem.app.services.LocalPlaybackController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private data class LibraryTrack(val track: PlaylistTrack, val checked: Boolean = false)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistsScreen(dataStore: DataStore<Preferences>) {
    val context = LocalContext.current
    val repository = remember(dataStore) { PlaylistRepository(dataStore) }
    val playlists by repository.playlists.collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    var editorName by remember { mutableStateOf("") }
    var editingPlaylistId by remember { mutableStateOf<String?>(null) }
    var showPlaylistEditor by remember { mutableStateOf(false) }
    var addTracksToPlaylist by remember { mutableStateOf<UserPlaylist?>(null) }
    var libraryTracks by remember { mutableStateOf<List<PlaylistTrack>>(emptyList()) }
    val selectedPaths = remember { mutableStateListOf<String>() }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(context) {
        libraryTracks = withContext(Dispatchers.IO) { loadLibraryTracks(context) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.playlists)) },
                actions = {
                    IconButton(onClick = {
                        editingPlaylistId = null
                        editorName = ""
                        showPlaylistEditor = true
                    }) {
                        Icon(Icons.Default.Add, contentDescription = stringResource(R.string.create_playlist))
                    }
                }
            )
        }
    ) { padding ->
        if (playlists.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(padding).padding(28.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(Icons.Default.LibraryMusic, contentDescription = null)
                Text(stringResource(R.string.playlists_empty_title), style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(R.string.playlists_empty_message),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Button(onClick = {
                    editingPlaylistId = null
                    editorName = ""
                    showPlaylistEditor = true
                }) { Text(stringResource(R.string.create_playlist)) }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(playlists, key = { it.id }) { playlist ->
                    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
                        Column(modifier = Modifier.fillMaxWidth().padding(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(playlist.name, style = MaterialTheme.typography.titleMedium)
                                    Text(
                                        stringResource(R.string.playlist_track_count, playlist.tracks.size),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                IconButton(onClick = {
                                    editingPlaylistId = playlist.id
                                    editorName = playlist.name
                                    showPlaylistEditor = true
                                }) { Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.rename_playlist)) }
                                IconButton(onClick = { addTracksToPlaylist = playlist; selectedPaths.clear() }) {
                                    Icon(Icons.Default.Add, contentDescription = stringResource(R.string.add_tracks))
                                }
                                IconButton(onClick = { scope.launch { repository.deletePlaylist(playlist.id) } }) {
                                    Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.delete_playlist))
                                }
                                IconButton(onClick = {
                                    val valid = playlist.tracks.filter { File(it.filePath).isFile && File(it.filePath).canRead() }
                                    if (valid.isEmpty()) {
                                        errorMessage = context.getString(R.string.playlist_no_playable_tracks)
                                    } else {
                                        val songs = valid.map { Song(it.title, it.artist, null, it.filePath) }
                                        LocalPlaybackController.playQueue(context, songs, songs.first())
                                    }
                                }) { Icon(Icons.Default.PlayArrow, contentDescription = stringResource(R.string.play_playlist)) }
                            }
                            playlist.tracks.forEach { track ->
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(track.title, style = MaterialTheme.typography.bodyMedium)
                                        Text(track.artist, style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    IconButton(onClick = { scope.launch { repository.removeTrack(playlist.id, track.filePath) } }) {
                                        Icon(Icons.Default.RemoveCircleOutline, contentDescription = stringResource(R.string.remove_track))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showPlaylistEditor) {
        AlertDialog(
            onDismissRequest = { showPlaylistEditor = false },
            title = { Text(stringResource(if (editingPlaylistId == null) R.string.create_playlist else R.string.rename_playlist)) },
            text = {
                OutlinedTextField(
                    value = editorName,
                    onValueChange = { editorName = it },
                    label = { Text(stringResource(R.string.playlist_name)) },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val name = editorName.trim()
                    if (name.isBlank()) {
                        errorMessage = context.getString(R.string.playlist_name_required)
                    } else {
                        scope.launch {
                            runCatching {
                                if (editingPlaylistId == null) repository.createPlaylist(name)
                                else repository.renamePlaylist(editingPlaylistId!!, name)
                            }.onFailure { errorMessage = it.message ?: context.getString(R.string.playlist_save_failed) }
                            showPlaylistEditor = false
                        }
                    }
                }) { Text(stringResource(R.string.save)) }
            },
            dismissButton = {
                TextButton(onClick = { showPlaylistEditor = false }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }

    addTracksToPlaylist?.let { playlist ->
        AlertDialog(
            onDismissRequest = { addTracksToPlaylist = null },
            title = { Text(stringResource(R.string.add_tracks_to_playlist, playlist.name)) },
            text = {
                LazyColumn(modifier = Modifier.fillMaxWidth()) {
                    items(libraryTracks, key = { it.filePath }) { track ->
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable {
                                if (selectedPaths.contains(track.filePath)) selectedPaths.remove(track.filePath)
                                else selectedPaths.add(track.filePath)
                            }.padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = selectedPaths.contains(track.filePath),
                                onCheckedChange = { checked ->
                                    if (checked && !selectedPaths.contains(track.filePath)) selectedPaths.add(track.filePath)
                                    if (!checked) selectedPaths.remove(track.filePath)
                                }
                            )
                            Column {
                                Text(track.title, style = MaterialTheme.typography.bodyMedium)
                                Text(track.artist, style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val selected = libraryTracks.filter { selectedPaths.contains(it.filePath) }
                    scope.launch {
                        runCatching { repository.addTracks(playlist.id, selected) }
                            .onFailure { errorMessage = it.message ?: context.getString(R.string.playlist_save_failed) }
                        addTracksToPlaylist = null
                    }
                }) { Text(stringResource(R.string.add_selected_tracks, selectedPaths.size)) }
            },
            dismissButton = {
                TextButton(onClick = { addTracksToPlaylist = null }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }

    errorMessage?.let { message ->
        AlertDialog(
            onDismissRequest = { errorMessage = null },
            title = { Text(stringResource(R.string.playlist_action_failed)) },
            text = { Text(message) },
            confirmButton = { TextButton(onClick = { errorMessage = null }) { Text(stringResource(R.string.ok)) } }
        )
    }
}

private fun loadLibraryTracks(context: Context): List<PlaylistTrack> {
    val projection = arrayOf(
        MediaStore.Audio.Media.DATA,
        MediaStore.Audio.Media.TITLE,
        MediaStore.Audio.Media.ARTIST,
        MediaStore.Audio.Media.IS_MUSIC,
    )
    val result = mutableListOf<PlaylistTrack>()
    context.contentResolver.query(
        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
        projection,
        "${MediaStore.Audio.Media.IS_MUSIC} != 0",
        null,
        MediaStore.Audio.Media.TITLE + " COLLATE NOCASE ASC"
    )?.use { cursor ->
        val pathColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
        val titleColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
        val artistColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
        while (cursor.moveToNext()) {
            val path = cursor.getString(pathColumn)?.takeIf { it.isNotBlank() } ?: continue
            val title = cursor.getString(titleColumn)?.takeIf { it.isNotBlank() } ?: File(path).nameWithoutExtension
            val artist = cursor.getString(artistColumn).orEmpty()
            result += PlaylistTrack(path, title, artist)
        }
    }
    return result
}
