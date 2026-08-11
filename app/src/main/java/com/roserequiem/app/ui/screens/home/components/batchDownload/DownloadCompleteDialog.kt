package com.roserequiem.app.ui.screens.home.components.batchDownload

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.roserequiem.app.R
import com.roserequiem.app.util.FlaggedSong

@Composable
fun DownloadCompleteDialog(
    successCount: Int,
    noLyricsCount: Int,
    failedCount: Int,
    skippedCount: Int,
    ignoreEmbeddedLyrics: Boolean,
    flaggedSongs: List<FlaggedSong> = emptyList(),
    onDismiss: () -> Unit,
    onRetryFailed: () -> Unit,
) {
    var showFlaggedSongs by remember { mutableStateOf(false) }

    if (showFlaggedSongs) {
        FlaggedSongsDialog(
            flaggedSongs = flaggedSongs,
            onDismiss = { showFlaggedSongs = false },
            onRetryFailed = onRetryFailed,
        )
        return
    }

    AlertDialog(
        title = {
            Text(text = stringResource(id = R.string.batch_download_lyrics))
        },
        text = {
            Column {
                Text(text = stringResource(R.string.download_complete))
                Text(text = stringResource(R.string.success, successCount))
                Text(text = stringResource(R.string.no_lyrics, noLyricsCount))
                Text(text = stringResource(R.string.failed, failedCount))
                if (ignoreEmbeddedLyrics) {
                    Text(text = stringResource(R.string.skipped_embedded_lyrics, skippedCount))
                }
                if (flaggedSongs.isNotEmpty()) {
                    TextButton(onClick = { showFlaggedSongs = true }) {
                        Text(
                            text = pluralStringResource(
                                R.plurals.view_flagged_songs,
                                flaggedSongs.size,
                                flaggedSongs.size
                            )
                        )
                    }
                }
            }
        },
        onDismissRequest = onDismiss,
        confirmButton = {
            Button(onClick = onDismiss) {
                Text(text = stringResource(id = R.string.ok))
            }
        }
    )
}
