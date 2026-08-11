package com.roserequiem.app.ui.screens.downloads

import com.roserequiem.app.domain.model.DownloadRecord
import com.roserequiem.app.domain.model.DownloadStatus

/**
 * One batch's worth of [DownloadRecord]s grouped together, so the history list can show
 * a single "7 done · 3 active · 1 queued of 11" row per run instead of one row per song
 * -- the same summarized-job pattern Soulseek clients use for a multi-file transfer.
 *
 * Purely a display-layer grouping computed from [DownloadHistoryRepository]'s flat list
 * ([groupIntoBatches]) -- nothing here is persisted separately from the records
 * themselves.
 */
data class BatchSummary(
    val batchId: String,
    val records: List<DownloadRecord>,
) {
    /** When this batch was queued -- used to sort batches newest-first, and to display
     *  "when" for the run. Fixed for the batch's lifetime, unlike [lastActivityAt]. */
    val startedAt: Long get() = records.minOf { it.timestamp }

    /** Most recent status change in this batch. */
    val lastActivityAt: Long get() = records.maxOf { it.timestamp }

    val total: Int get() = records.size
    val queuedCount: Int get() = records.count { it.status == DownloadStatus.QUEUED }
    val downloadingCount: Int get() = records.count { it.status == DownloadStatus.DOWNLOADING }
    val successCount: Int get() = records.count { it.status == DownloadStatus.SUCCESS }
    val failedCount: Int get() = records.count { it.status == DownloadStatus.FAILED }
    val noLyricsCount: Int get() = records.count { it.status == DownloadStatus.NO_LYRICS }
    val skippedCount: Int get() = records.count { it.status == DownloadStatus.SKIPPED }

    /** Finished, one way or another -- everything except still queued/downloading. */
    val doneCount: Int get() = total - queuedCount - downloadingCount
    val isActive: Boolean get() = queuedCount > 0 || downloadingCount > 0
}

/**
 * A batch paired with the subset of its records that currently match the Downloads
 * screen's search/status filter. [summary] always reflects the batch's *true* totals
 * (so "of 11" means the real batch size, not however many rows the filter left) --
 * [matchingRecords] is what to actually list if the batch is expanded, so filtering to
 * "Failed" doesn't bury the 2 failed songs under 40 successful ones.
 */
data class FilteredBatch(
    val summary: BatchSummary,
    val matchingRecords: List<DownloadRecord>,
)

/**
 * Groups [records] by [DownloadRecord.batchId] into [BatchSummary]s, most recently
 * started batch first. Records with no batch id (history saved before batching existed)
 * each become their own single-record batch rather than being lumped together, since
 * they have nothing else in common.
 */
fun groupIntoBatches(records: List<DownloadRecord>): List<BatchSummary> =
    records
        .groupBy { it.batchId.ifBlank { "legacy-${it.filePath}" } }
        .map { (batchId, batchRecords) -> BatchSummary(batchId, batchRecords) }
        .sortedByDescending { it.startedAt }
