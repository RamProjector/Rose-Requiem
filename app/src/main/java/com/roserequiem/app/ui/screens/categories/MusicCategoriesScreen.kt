package com.roserequiem.app.ui.screens.categories

import android.content.Context
import android.provider.MediaStore
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.roserequiem.app.R
import com.roserequiem.app.data.PlaylistRepository
import com.roserequiem.app.domain.model.MusicCategorySuggester
import com.roserequiem.app.domain.model.PlaylistTrack
import com.roserequiem.app.domain.model.UserPlaylist
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private data class CategoryGroup(
    val category: String,
    val reason: String,
    val tracks: List<PlaylistTrack>,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MusicCategoriesScreen(dataStore: DataStore<Preferences>, onBack: () -> Unit) {
    val context = LocalContext.current
    val repository = remember(dataStore) { PlaylistRepository(dataStore) }
    val playlists by repository.playlists.collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    var groups by remember { mutableStateOf<List<CategoryGroup>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var message by remember { mutableStateOf<String?>(null) }
    val successTemplate = stringResource(R.string.category_playlist_created, "__PLAYLIST__")
    val failureMessage = stringResource(R.string.category_playlist_failed)

    LaunchedEffect(context) {
        groups = withContext(Dispatchers.IO) { loadCategoryGroups(context) }
        loading = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.music_categories)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                }
            )
        }
    ) { padding ->
        when {
            loading -> Column(
                modifier = Modifier.fillMaxSize().padding(padding),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) { CircularProgressIndicator() }
            groups.isEmpty() -> Column(
                modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(Icons.Default.AutoAwesome, contentDescription = null)
                Text(stringResource(R.string.no_category_suggestions), style = androidx.compose.material3.MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.category_suggestions_explanation))
            }
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Text(
                        stringResource(R.string.category_suggestions_explanation),
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
                items(groups, key = { it.category }) { group ->
                    val alreadyExists = playlists.any { it.name.equals("${group.category} Mix", ignoreCase = true) }
                    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(group.category, style = androidx.compose.material3.MaterialTheme.typography.titleMedium)
                            Text(group.reason, style = androidx.compose.material3.MaterialTheme.typography.bodyMedium)
                            Text(stringResource(R.string.category_track_count, group.tracks.size))
                            Button(
                                enabled = !alreadyExists,
                                onClick = {
                                    scope.launch {
                                        runCatching {
                                            val playlist = repository.createPlaylist("${group.category} Mix")
                                            repository.addTracks(playlist.id, group.tracks)
                                            playlist
                                        }.onSuccess {
                                            message = successTemplate.replace("__PLAYLIST__", it.name)
                                        }.onFailure {
                                            message = it.message ?: failureMessage
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(stringResource(if (alreadyExists) R.string.category_playlist_exists else R.string.category_create_playlist, group.category))
                            }
                        }
                    }
                }
            }
        }
    }

    message?.let { text ->
        AlertDialog(
            onDismissRequest = { message = null },
            title = { Text(stringResource(R.string.music_categories)) },
            text = { Text(text) },
            confirmButton = { TextButton(onClick = { message = null }) { Text(stringResource(R.string.ok)) } }
        )
    }
}

private fun loadCategoryGroups(context: Context): List<CategoryGroup> {
    val projection = arrayOf(
        MediaStore.Audio.Media.DATA,
        MediaStore.Audio.Media.TITLE,
        MediaStore.Audio.Media.ARTIST,
        MediaStore.Audio.Media.IS_MUSIC,
    )
    val grouped = linkedMapOf<String, MutableList<PlaylistTrack>>()
    val reasons = mutableMapOf<String, String>()
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
            val suggestion = MusicCategorySuggester.suggest(title, artist, File(path).nameWithoutExtension) ?: continue
            val track = PlaylistTrack(path, title, artist)
            grouped.getOrPut(suggestion.category) { mutableListOf() }.add(track)
            reasons.putIfAbsent(suggestion.category, suggestion.reason)
        }
    }
    return grouped.map { (category, tracks) ->
        CategoryGroup(category, reasons[category].orEmpty(), tracks.toList())
    }.sortedByDescending { it.tracks.size }
}
