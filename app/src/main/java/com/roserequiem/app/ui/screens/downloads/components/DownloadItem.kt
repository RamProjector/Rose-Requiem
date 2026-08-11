package com.roserequiem.app.ui.screens.downloads.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.roserequiem.app.R
import com.roserequiem.app.domain.model.DownloadRecord
import com.roserequiem.app.domain.model.DownloadStatus
import java.text.DateFormat
import java.util.Date

/**
 * One row in the Downloads history: title/artist, a status chip, and when it last
 * changed. Deliberately lighter than [com.roserequiem.app.ui.screens.home.components.SongItem]
 * -- no shared-element modifiers, no cover art -- since this list re-renders on every
 * live progress tick during an active batch and doesn't need hero transitions.
 *
 * Long-pressing enters multi-select mode (mirrors
 * [com.roserequiem.app.ui.screens.home.components.SongItem]'s `quickSelect` pattern): once
 * [selectionMode] is true anywhere on the screen, every row's plain tap toggles its own
 * checkbox instead.
 *
 * @param onDismiss When non-null, wraps the row in a [SwipeToDismissBox] that calls it
 * once a swipe commits (either direction). The caller decides when that's appropriate --
 * e.g. null while [selectionMode] is on, or for a row whose status isn't removable --
 * this composable just renders whichever behavior it's handed.
 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun DownloadItem(
    record: DownloadRecord,
    modifier: Modifier = Modifier,
    selectionMode: Boolean = false,
    selected: Boolean = false,
    onToggleSelected: () -> Unit = {},
    onDismiss: (() -> Unit)? = null,
) {
    if (onDismiss == null) {
        DownloadItemRow(record, selectionMode, selected, onToggleSelected, modifier)
        return
    }

    // rememberUpdatedState so a stale onDismiss can't linger in dismissState's
    // confirmValueChange closure across recomposition (e.g. if the record list this
    // callback closes over changes between when the row appears and when it's swiped).
    val currentOnDismiss by rememberUpdatedState(onDismiss)
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            val committed = value != SwipeToDismissBoxValue.Settled
            if (committed) currentOnDismiss()
            committed
        },
    )

    SwipeToDismissBox(
        state = dismissState,
        modifier = modifier,
        backgroundContent = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.errorContainer)
                    .padding(horizontal = 24.dp),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.DeleteOutline,
                    contentDescription = stringResource(R.string.remove_from_downloads),
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
        },
    ) {
        DownloadItemRow(record, selectionMode, selected, onToggleSelected)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DownloadItemRow(
    record: DownloadRecord,
    selectionMode: Boolean,
    selected: Boolean,
    onToggleSelected: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val bgColor = if (selected) MaterialTheme.colorScheme.surfaceVariant
    else MaterialTheme.colorScheme.surface
    val haptics = LocalHapticFeedback.current

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .background(bgColor)
            .combinedClickable(
                onClick = { if (selectionMode) onToggleSelected() },
                onLongClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onToggleSelected()
                },
            )
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        if (selectionMode) {
            Checkbox(checked = selected, onCheckedChange = { onToggleSelected() })
        }

        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = record.songName,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (record.artist.isNotBlank()) {
                        Text(
                            text = record.artist,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                DownloadStatusChip(status = record.status, modifier = Modifier.padding(start = 8.dp))
            }

            if (record.status == DownloadStatus.DOWNLOADING) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
            }

            Text(
                text = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
                    .format(Date(record.timestamp)),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}
