package com.roserequiem.app.ui.screens.downloads

import com.roserequiem.app.domain.model.DownloadRecord
import com.roserequiem.app.domain.model.DownloadStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BatchSummaryTest {
    @Test
    fun recordsWithSameBatchIdAreGroupedIntoOneSummary() {
        val records = listOf(
            record("/music/a.flac", "run-1", DownloadStatus.SUCCESS, 100L),
            record("/music/b.flac", "run-1", DownloadStatus.FAILED, 200L),
            record("/music/c.flac", "run-2", DownloadStatus.QUEUED, 300L),
        )

        val batches = groupIntoBatches(records)

        assertEquals(listOf("run-2", "run-1"), batches.map { it.batchId })
        assertEquals(1, batches[0].total)
        assertEquals(2, batches[1].total)
        assertEquals(1, batches[1].successCount)
        assertEquals(1, batches[1].failedCount)
    }

    @Test
    fun recordsWithoutBatchIdRemainSeparateLegacyEntries() {
        val records = listOf(
            record("/music/a.flac", "", DownloadStatus.SUCCESS, 100L),
            record("/music/b.flac", "", DownloadStatus.PAUSED, 200L),
        )

        val batches = groupIntoBatches(records)

        assertEquals(2, batches.size)
        assertEquals(setOf("legacy-/music/a.flac", "legacy-/music/b.flac"), batches.map { it.batchId }.toSet())
        assertEquals(listOf(1, 1), batches.map { it.total })
    }

    @Test
    fun summaryDistinguishesActiveFromFinishedStatuses() {
        val batch = groupIntoBatches(
            listOf(
                record("/music/a.flac", "run-1", DownloadStatus.QUEUED, 100L),
                record("/music/b.flac", "run-1", DownloadStatus.DOWNLOADING, 110L),
                record("/music/c.flac", "run-1", DownloadStatus.PAUSED, 120L),
            ),
        ).single()

        assertEquals(3, batch.total)
        assertEquals(1, batch.queuedCount)
        assertEquals(1, batch.downloadingCount)
        assertEquals(1, batch.doneCount)
        assertTrue(batch.isActive)
        assertEquals(120L, batch.lastActivityAt)
    }

    @Test
    fun fullyFinishedBatchIsNotActive() {
        val batch = groupIntoBatches(
            listOf(
                record("/music/a.flac", "run-1", DownloadStatus.SUCCESS, 100L),
                record("/music/b.flac", "run-1", DownloadStatus.NO_LYRICS, 120L),
            ),
        ).single()

        assertFalse(batch.isActive)
        assertEquals(2, batch.doneCount)
        assertEquals(100L, batch.startedAt)
        assertEquals(120L, batch.lastActivityAt)
    }

    private fun record(
        filePath: String,
        batchId: String,
        status: DownloadStatus,
        timestamp: Long,
    ) = DownloadRecord(
        filePath = filePath,
        songName = filePath.substringAfterLast('/'),
        artist = "Test artist",
        status = status,
        timestamp = timestamp,
        batchId = batchId,
    )
}
