package com.roserequiem.app.ui.screens.downloads

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
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
import androidx.lifecycle.viewmodel.compose.viewModel
import com.roserequiem.app.R
import com.roserequiem.app.data.DownloadHistoryRepository
import com.roserequiem.app.data.UserSettingsController
import com.roserequiem.app.domain.model.DownloadRecord
import com.roserequiem.app.services.BatchDownloadService
import com.roserequiem.app.ui.screens.downloads.components.BatchSummaryItem
import com.roserequiem.app.ui.screens.downloads.components.CancelBatchDownloadDialog
import com.roserequiem.app.ui.screens.downloads.components.DownloadItem
import com.roserequiem.app.ui.screens.downloads.components.DownloadStatusFilterRow
import com.roserequiem.app.ui.screens.downloads.components.DownloadsSearchBar
import com.roserequiem.app.util.ext.BackPressHandler
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadsScreen(
    userSettingsController: UserSettingsController,
    viewModel: DownloadsViewModel = viewModel(),
) {
    val records by viewModel.records.collectAsState()
    val isLoaded by viewModel.isLoaded.collectAsState()
    // Both backed by SavedStateHandle (see DownloadsViewModel) so they survive the app's
    // process being killed and restored, not just configuration changes.
    val searchQuery by viewModel.searchQuery.collectAsState()
    val statusFilter by viewModel.statusFilter.collectAsState()
    val context = LocalContext.current

    // Mirrors whether a batch is running elsewhere (e.g. started from Home) so
    // Continue/Retry can be withheld below -- only one batch runs at a time.
    val activeBatchState by BatchDownloadService.state.collectAsState()
    val batchActive = activeBatchState?.isComplete == false

    val visibleBatches = remember(records, searchQuery, statusFilter) {
        viewModel.visibleBatches(records, searchQuery, statusFilter)
    }

    // "Selection mode" is implicit: any record checked at all is enough to swap the top
    // bar over, same as the Home screen's song list.
    val selectedCount = viewModel.selectedFilePaths.size
    val selectionMode = selectedCount > 0
    val availableActions = remember(records, selectedCount, batchActive) {
        viewModel.availableActions(records, batchActive)
    }

    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    var showCancelConfirm by remember { mutableStateOf(false) }

    // Shows a Snackbar with an Undo action for a just-removed/cleared set of records,
    // restoring them via DownloadHistoryRepository if the user taps it in time.
    fun offerUndo(removed: List<DownloadRecord>) {
        if (removed.isEmpty()) return
        coroutineScope.launch {
            val message = context.resources.getQuantityString(
                R.plurals.downloads_removed, removed.size, removed.size,
            )
            val result = snackbarHostState.showSnackbar(
                message = message,
                actionLabel = context.getString(R.string.undo),
                duration = SnackbarDuration.Long,
            )
            if (result == SnackbarResult.ActionPerformed) {
                DownloadHistoryRepository.restoreRecords(removed)
            }
        }
    }

    BackPressHandler(enabled = selectionMode, onBackPressed = viewModel::clearSelection)

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            if (selectionMode) {
                DownloadsSelectionTopBar(
                    selectedCount = selectedCount,
                    availableActions = availableActions,
                    onClearSelection = viewModel::clearSelection,
                    onContinue = {
                        viewModel.continueSelected(records, context, userSettingsController.ignoreEmbeddedLyrics)
                    },
                    onRetry = {
                        viewModel.retrySelected(records, context, userSettingsController.ignoreEmbeddedLyrics)
                    },
                    onCancel = { showCancelConfirm = true },
                    onRemove = { offerUndo(viewModel.removeSelected(records)) },
                )
            } else {
                TopAppBar(
                    title = { Text(stringResource(id = R.string.downloads)) },
                    actions = {
                        IconButton(onClick = { offerUndo(viewModel.clearFinished()) }) {
                            Icon(
                                imageVector = Icons.Filled.DeleteSweep,
                                contentDescription = stringResource(id = R.string.clear_finished_downloads),
                            )
                        }
                    },
                )
            }
        },
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
            DownloadsSearchBar(
                query = searchQuery,
                onQueryChange = viewModel::onSearchQueryChange,
            )
            DownloadStatusFilterRow(
                selected = statusFilter,
                onSelect = viewModel::onStatusFilterChange,
                modifier = Modifier.padding(bottom = 8.dp),
            )

            when {
                !isLoaded -> LoadingState()
                records.isEmpty() -> EmptyState(textRes = R.string.no_download_history)
                visibleBatches.isEmpty() -> EmptyState(textRes = R.string.no_download_history_match)
                else -> LazyColumn {
                    // Every batch's rows are emitted as their own lazy items -- including
                    // an expanded batch's children -- rather than composed eagerly inside
                    // a plain Column nested in one outer item. A batch can be a "download
                    // everything" run of hundreds or thousands of songs; eagerly composing
                    // every row of that the moment it's expanded is what used to make
                    // expanding a big batch janky-to-crashy. Flattened into real lazy
                    // items like this, only whatever's actually scrolled into view is ever
                    // composed, regardless of how large the batch is.
                    visibleBatches.forEach { filteredBatch ->
                        val batch = filteredBatch.summary

                        if (batch.total == 1) {
                            // A "batch of one" is just a single song -- the summary
                            // row's counts/expand-arrow would be redundant chrome
                            // around content that's already fully visible.
                            val record = filteredBatch.matchingRecords.first()
                            item(key = batch.batchId) {
                                DownloadItem(
                                    record = record,
                                    selectionMode = selectionMode,
                                    selected = record.filePath in viewModel.selectedFilePaths,
                                    onToggleSelected = { viewModel.toggleSelected(record.filePath) },
                                    onDismiss = if (!selectionMode && viewModel.isRemovable(record.status)) {
                                        { offerUndo(listOf(viewModel.removeRecord(record))) }
                                    } else null,
                                )
                                HorizontalDivider()
                            }
                        } else {
                            val expanded = viewModel.isExpanded(batch.batchId)
                            item(key = batch.batchId) {
                                BatchSummaryItem(
                                    batch = batch,
                                    expanded = expanded,
                                    onToggleExpanded = { viewModel.toggleExpanded(batch.batchId) },
                                )
                                HorizontalDivider()
                            }
                            if (expanded) {
                                items(
                                    filteredBatch.matchingRecords,
                                    key = { "${batch.batchId}:${it.filePath}" },
                                ) { record ->
                                    Column(modifier = Modifier.padding(start = 16.dp)) {
                                        DownloadItem(
                                            record = record,
                                            selectionMode = selectionMode,
                                            selected = record.filePath in viewModel.selectedFilePaths,
                                            onToggleSelected = { viewModel.toggleSelected(record.filePath) },
                                            onDismiss = if (!selectionMode && viewModel.isRemovable(record.status)) {
                                                { offerUndo(listOf(viewModel.removeRecord(record))) }
                                            } else null,
                                        )
                                    }
                                    HorizontalDivider()
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCancelConfirm) {
        CancelBatchDownloadDialog(
            onConfirm = {
                showCancelConfirm = false
                viewModel.cancelActiveSelected(context)
            },
            onDismiss = { showCancelConfirm = false },
        )
    }
}

/**
 * Replaces the normal top bar while any row is checked: a count, a close button, and
 * only the action icons that actually apply to what's selected (see
 * [DownloadsViewModel.availableActions]) -- e.g. Cancel never shows up unless the
 * selection includes something still queued/downloading.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DownloadsSelectionTopBar(
    selectedCount: Int,
    availableActions: Set<SelectionAction>,
    onClearSelection: () -> Unit,
    onContinue: () -> Unit,
    onRetry: () -> Unit,
    onCancel: () -> Unit,
    onRemove: () -> Unit,
) {
    TopAppBar(
        navigationIcon = {
            IconButton(onClick = onClearSelection) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.back),
                )
            }
        },
        title = { Text(text = stringResource(id = R.string.selected_count, selectedCount)) },
        actions = {
            if (SelectionAction.CONTINUE in availableActions) {
                IconButton(onClick = onContinue) {
                    Icon(
                        imageVector = Icons.Filled.PlayArrow,
                        contentDescription = stringResource(R.string.continue_download),
                    )
                }
            }
            if (SelectionAction.RETRY in availableActions) {
                IconButton(onClick = onRetry) {
                    Icon(
                        imageVector = Icons.Filled.Refresh,
                        contentDescription = stringResource(R.string.retry_download),
                    )
                }
            }
            if (SelectionAction.CANCEL in availableActions) {
                IconButton(onClick = onCancel) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = stringResource(R.string.cancel),
                    )
                }
            }
            if (SelectionAction.REMOVE in availableActions) {
                IconButton(onClick = onRemove) {
                    Icon(
                        imageVector = Icons.Filled.DeleteOutline,
                        contentDescription = stringResource(R.string.remove_from_downloads),
                    )
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            scrolledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    )
}

@Composable
private fun LoadingState() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@Composable
private fun EmptyState(textRes: Int) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(id = textRes),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 48.dp),
        )
    }
}
