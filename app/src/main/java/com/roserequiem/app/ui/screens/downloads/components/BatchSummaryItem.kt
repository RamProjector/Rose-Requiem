package com.roserequiem.app.ui.screens.downloads.components

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.roserequiem.app.R
import com.roserequiem.app.ui.screens.downloads.BatchSummary
import java.text.DateFormat
import java.util.Date

/**
 * Collapsed header row for one batch run: when it started, a "done · active · queued of
 * total" breakdown, and a progress bar -- the same summarized-job shape Seeker
 * (Soulseek's Android client) uses for a multi-file transfer, adapted to Rose Requiem's
 * queued/downloading/success/no-lyrics/failed/skipped states.
 *
 * Only used for batches with more than one record --
 * [com.roserequiem.app.ui.screens.downloads.DownloadsScreen] renders single-song batches as
 * a plain [DownloadItem] instead, since a summary row would just be redundant chrome
 * around one song.
 */
@Composable
fun BatchSummaryItem(
    batch: BatchSummary,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onToggleExpanded)
            .animateContentSize()
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = pluralStringResource(
                        R.plurals.batch_song_count,
                        batch.total,
                        batch.total,
                    ),
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
                        .format(Date(batch.startedAt)),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
            Icon(
                imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                contentDescription = null,
            )
        }

        Text(
            text = batchStatusLine(batch),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 4.dp),
        )

        LinearProgressIndicator(
            progress = { if (batch.total == 0) 0f else batch.doneCount.toFloat() / batch.total },
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        )
    }
}

/** Builds e.g. "40 done · 5 active · 2 queued of 47", omitting any segment that's zero. */
@Composable
private fun batchStatusLine(batch: BatchSummary): String {
    val segments = buildList {
        if (batch.doneCount > 0) add(stringResource(R.string.batch_status_done, batch.doneCount))
        if (batch.downloadingCount > 0) add(
            stringResource(R.string.batch_status_active, batch.downloadingCount)
        )
        if (batch.queuedCount > 0) add(
            stringResource(R.string.batch_status_queued, batch.queuedCount)
        )
    }
    return stringResource(R.string.batch_status_of_total, segments.joinToString(" · "), batch.total)
}
