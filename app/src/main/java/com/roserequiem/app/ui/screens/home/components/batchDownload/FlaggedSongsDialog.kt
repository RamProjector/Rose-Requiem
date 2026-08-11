package com.roserequiem.app.ui.screens.home.components.batchDownload

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.roserequiem.app.R
import com.roserequiem.app.util.FlagReason
import com.roserequiem.app.util.FlaggedSong

/**
 * Shows every song the batch flagged (no lyrics found, or failed outright), with a search
 * box to filter by title/artist. Full list, not capped -- the search is what keeps a long
 * list navigable instead of truncating it and hoping the song you want was in the first 20.
 */
@Composable
fun FlaggedSongsDialog(
    flaggedSongs: List<FlaggedSong>,
    onDismiss: () -> Unit,
    onRetryFailed: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val unknownText = stringResource(R.string.unknown)
    val noLyricsLabel = stringResource(R.string.no_lyrics_short)
    val failedLabel = stringResource(R.string.failed_short)

    val filtered = remember(query, flaggedSongs) {
        if (query.isBlank()) {
            flaggedSongs
        } else {
            flaggedSongs.filter { flagged ->
                val title = flagged.song.title.orEmpty()
                val artist = flagged.song.artist.orEmpty()
                title.contains(query, ignoreCase = true) || artist.contains(query, ignoreCase = true)
            }
        }
    }

    AlertDialog(
        title = { Text(text = stringResource(R.string.flagged_songs)) },
        text = {
            Column {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text(text = stringResource(R.string.search)) },
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    trailingIcon = {
                        if (query.isNotEmpty()) {
                            IconButton(onClick = { query = "" }) {
                                Icon(Icons.Filled.Clear, contentDescription = stringResource(R.string.clear))
                            }
                        }
                    }
                )

                Spacer(modifier = Modifier.height(8.dp))

                if (filtered.isEmpty()) {
                    Text(
                        text = stringResource(R.string.no_matching_songs),
                        style = MaterialTheme.typography.bodyMedium
                    )
                } else {
                    LazyColumn(modifier = Modifier.heightIn(max = 350.dp)) {
                        items(filtered, key = { "${it.song.filePath}_${it.reason}" }) { flagged ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "${flagged.song.title ?: unknownText} — ${flagged.song.artist ?: unknownText}",
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(end = 8.dp)
                                )
                                Text(
                                    text = when (flagged.reason) {
                                        FlagReason.NO_LYRICS -> noLyricsLabel
                                        FlagReason.FAILED -> failedLabel
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        },
        onDismissRequest = onDismiss,
        confirmButton = {
            Button(onClick = onDismiss) {
                Text(text = stringResource(id = R.string.ok))
            }
        },
        dismissButton = {
            // Re-runs the batch for just these flagged songs and hands the whole batch
            // dialog stack back to Home -- progress from here on is visible on the
            // Downloads tab rather than another round of this same dialog.
            Button(onClick = onRetryFailed) {
                Text(text = stringResource(id = R.string.retry_failed))
            }
        }
    )
}
