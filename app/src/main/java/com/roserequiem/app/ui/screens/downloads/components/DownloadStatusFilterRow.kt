package com.roserequiem.app.ui.screens.downloads.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.roserequiem.app.R
import com.roserequiem.app.domain.model.DownloadStatus

/**
 * "All" plus one chip per [DownloadStatus], styled after the status-chip colors used on
 * each row ([DownloadStatusChip]) so a selected filter visually matches the rows it's
 * filtering to.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadStatusFilterRow(
    selected: DownloadStatus?,
    onSelect: (DownloadStatus?) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 16.dp),
    ) {
        item {
            FilterChip(
                selected = selected == null,
                onClick = { onSelect(null) },
                label = { Text(stringResource(id = R.string.status_all)) },
            )
        }
        items(DownloadStatus.entries) { status ->
            FilterChip(
                selected = selected == status,
                onClick = { onSelect(if (selected == status) null else status) },
                label = { Text(stringResource(id = statusLabelRes(status))) },
                border = FilterChipDefaults.filterChipBorder(
                    enabled = true,
                    selected = selected == status,
                    borderColor = statusColor(status).copy(alpha = 0.5f),
                    selectedBorderColor = statusColor(status),
                ),
            )
        }
    }
}
