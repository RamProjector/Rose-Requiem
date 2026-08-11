package com.roserequiem.app.ui.screens.downloads

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.mutableStateListOf
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import com.roserequiem.app.data.DownloadHistoryRepository
import com.roserequiem.app.domain.model.DownloadRecord
import com.roserequiem.app.domain.model.DownloadStatus
import com.roserequiem.app.domain.model.Song
import com.roserequiem.app.services.BatchDownloadService
import kotlinx.coroutines.flow.StateFlow

private const val KEY_SEARCH_QUERY = "downloads_search_query"
private const val KEY_STATUS_FILTER = "downloads_status_filter"

/** Statuses a selection can be retried from: it tried and didn't end in a usable file. */
private val RETRYABLE_STATUSES = setOf(DownloadStatus.FAILED, DownloadStatus.NO_LYRICS, DownloadStatus.SKIPPED)

/** Statuses still in flight as part of the one currently-running batch, if any. */
private val ACTIVE_STATUSES = setOf(DownloadStatus.QUEUED, DownloadStatus.DOWNLOADING)

/** Every status "Remove" is allowed to act on -- anything that isn't still in flight. */
private val REMOVABLE_STATUSES = setOf(
    DownloadStatus.SUCCESS,
    DownloadStatus.NO_LYRICS,
    DownloadStatus.FAILED,
    DownloadStatus.SKIPPED,
    DownloadStatus.PAUSED,
)

/**
 * Thin wrapper around [DownloadHistoryRepository]: the repository owns the actual data
 * (shared with the background download service), this holds the screen's search/filter/
 * expansion/selection UI state and turns the flat record list into what the screen
 * should render.
 *
 * The search query and status filter are backed by [SavedStateHandle] rather than plain
 * `mutableStateOf` -- a typed-in search survives Android reclaiming the app's process
 * while it's in the background (e.g. switching to another app for a while) and restoring
 * it later from the task switcher, which a plain ViewModel property would not survive.
 * [expandedBatchIds] and [selectedFilePaths] stay plain in-memory state: neither is worth
 * restoring, and both'd reset to empty on a fresh screen anyway.
 */
class DownloadsViewModel(
    private val savedStateHandle: SavedStateHandle = SavedStateHandle(),
) : ViewModel() {
    val records = DownloadHistoryRepository.records
    val isLoaded = DownloadHistoryRepository.isLoaded

    val searchQuery: StateFlow<String> = savedStateHandle.getStateFlow(KEY_SEARCH_QUERY, "")
    val statusFilter: StateFlow<DownloadStatus?> =
        savedStateHandle.getStateFlow(KEY_STATUS_FILTER, null)

    // Multi-record batches start collapsed; a batch id lands here once the user taps
    // it open. Single-record batches ignore this and always render expanded (see
    // DownloadsScreen) since a summary row would just be redundant chrome for one song.
    private var expandedBatchIds by mutableStateOf<Set<String>>(emptySet())

    // Which individual records (by filePath) are checked in multi-select mode. Entering
    // selection mode is implicit: the screen treats "selection mode" as this being
    // non-empty, same pattern HomeViewModel uses for its own song selection.
    val selectedFilePaths: SnapshotStateList<String> = mutableStateListOf()

    fun onSearchQueryChange(query: String) {
        savedStateHandle[KEY_SEARCH_QUERY] = query
    }

    fun onStatusFilterChange(status: DownloadStatus?) {
        savedStateHandle[KEY_STATUS_FILTER] = status
    }

    fun isExpanded(batchId: String) = batchId in expandedBatchIds

    fun toggleExpanded(batchId: String) {
        expandedBatchIds = if (batchId in expandedBatchIds) {
            expandedBatchIds - batchId
        } else {
            expandedBatchIds + batchId
        }
    }

    /**
     * Drops every finished (non-active) record. Returns exactly what was cleared, so
     * the caller can offer undo -- see [DownloadHistoryRepository.restoreRecords].
     */
    fun clearFinished(): List<DownloadRecord> {
        val cleared = records.value.filterNot { it.status in ACTIVE_STATUSES }
        DownloadHistoryRepository.clearFinished()
        return cleared
    }

    fun toggleSelected(filePath: String) {
        if (filePath in selectedFilePaths) selectedFilePaths.remove(filePath)
        else selectedFilePaths.add(filePath)
    }

    fun clearSelection() = selectedFilePaths.clear()

    fun selectedRecords(records: List<DownloadRecord>): List<DownloadRecord> =
        records.filter { it.filePath in selectedFilePaths }

    /**
     * Which of the four selection actions should even be offered, given the statuses
     * currently selected. Each action only ever touches the subset of the selection it
     * actually applies to (see [continueSelected]/[retrySelected]/[removeSelected]) --
     * this only decides whether to show the button at all, e.g. a selection mixing a
     * PAUSED and a SUCCESS row still offers "Continue" (for the paused one) even though
     * it wouldn't make sense for the whole selection.
     *
     * @param batchActive Whether a batch is currently running, anywhere (see
     * [BatchDownloadService.isActive]) -- CONTINUE/RETRY are withheld while true, since
     * both would start a second one and only one is ever allowed to run at a time.
     */
    fun availableActions(records: List<DownloadRecord>, batchActive: Boolean): Set<SelectionAction> {
        val statuses = selectedRecords(records).map { it.status }.toSet()
        return buildSet {
            if (!batchActive && DownloadStatus.PAUSED in statuses) add(SelectionAction.CONTINUE)
            if (!batchActive && statuses.any { it in RETRYABLE_STATUSES }) add(SelectionAction.RETRY)
            if (statuses.any { it in ACTIVE_STATUSES }) add(SelectionAction.CANCEL)
            if (statuses.any { it in REMOVABLE_STATUSES }) add(SelectionAction.REMOVE)
        }
    }

    /**
     * Re-queues exactly the selected PAUSED songs, resuming from where they left off.
     * No-ops if a batch is already running elsewhere -- [availableActions] withholds the
     * button for that case, this guards the same condition in case it's ever called from
     * somewhere that skips that (e.g. a future shortcut/deep link).
     */
    fun continueSelected(records: List<DownloadRecord>, context: Context, ignoreEmbeddedLyrics: Boolean) {
        if (BatchDownloadService.isActive()) return
        startBatch(selectedRecords(records).filter { it.status == DownloadStatus.PAUSED }, context, ignoreEmbeddedLyrics)
        clearSelection()
    }

    /**
     * Re-queues the selected songs that ended unsuccessfully (failed/no lyrics/skipped),
     * fresh. No-ops if a batch is already running elsewhere -- see [continueSelected].
     */
    fun retrySelected(records: List<DownloadRecord>, context: Context, ignoreEmbeddedLyrics: Boolean) {
        if (BatchDownloadService.isActive()) return
        startBatch(selectedRecords(records).filter { it.status in RETRYABLE_STATUSES }, context, ignoreEmbeddedLyrics)
        clearSelection()
    }

    /**
     * Stops the currently-running batch. Only one batch can be active at a time (see
     * [BatchDownloadService]), so this necessarily cancels the *whole* active batch --
     * not just the selected songs within it -- rather than pulling individual songs out
     * of an in-flight run. Only enabled when the selection includes at least one
     * queued/downloading row in the first place.
     */
    fun cancelActiveSelected(context: Context) {
        BatchDownloadService.cancel(context)
        clearSelection()
    }

    /**
     * Deletes the selected non-active rows from history outright. Returns exactly what
     * was removed, so the caller can offer undo -- see
     * [DownloadHistoryRepository.restoreRecords].
     */
    fun removeSelected(records: List<DownloadRecord>): List<DownloadRecord> {
        val toRemove = selectedRecords(records).filter { it.status in REMOVABLE_STATUSES }
        DownloadHistoryRepository.removeRecords(toRemove.map { it.filePath }.toSet())
        clearSelection()
        return toRemove
    }

    /**
     * Removes exactly one record, independent of the multi-select state -- backs
     * swipe-to-dismiss on an individual row. Returns it, so the caller can offer undo.
     */
    fun removeRecord(record: DownloadRecord): DownloadRecord {
        DownloadHistoryRepository.removeRecords(setOf(record.filePath))
        return record
    }

    /** Whether a single record's status is one "Remove" (bulk or swipe) can act on. */
    fun isRemovable(status: DownloadStatus): Boolean = status in REMOVABLE_STATUSES

    private fun startBatch(records: List<DownloadRecord>, context: Context, ignoreEmbeddedLyrics: Boolean) {
        if (records.isEmpty()) return
        val songs = records.map { record ->
            // DownloadRecord doesn't keep cover art -- downloadLyrics only needs the
            // title/artist/filePath to look lyrics up again, so a null imgUri is fine
            // here (this Song is never rendered, only fed back into the download flow).
            Song(title = record.songName, artist = record.artist, imgUri = null, filePath = record.filePath)
        }
        BatchDownloadService.start(context, ArrayList(songs), ignoreEmbeddedLyrics)
    }

    /**
     * Groups [records] into batches, filters by [searchQuery]/[statusFilter], and drops
     * any batch left with zero matches. Each returned [FilteredBatch.summary] keeps the
     * batch's true totals regardless of the filter -- see [FilteredBatch]'s doc.
     */
    fun visibleBatches(
        records: List<DownloadRecord>,
        searchQuery: String,
        statusFilter: DownloadStatus?,
    ): List<FilteredBatch> {
        val noFilterActive = statusFilter == null && searchQuery.isBlank()

        return groupIntoBatches(records).mapNotNull { batch ->
            val matching = if (noFilterActive) {
                batch.records
            } else {
                batch.records.filter { record ->
                    (statusFilter == null || record.status == statusFilter) &&
                        (searchQuery.isBlank() ||
                            record.songName.contains(searchQuery, ignoreCase = true) ||
                            record.artist.contains(searchQuery, ignoreCase = true))
                }
            }
            if (matching.isEmpty()) null else FilteredBatch(batch, matching)
        }
    }
}

/** The four bulk actions the Downloads screen's multi-select toolbar can offer. */
enum class SelectionAction { CONTINUE, RETRY, CANCEL, REMOVE }
