package com.roserequiem.app.ui.screens.downloads

import com.roserequiem.app.domain.model.DownloadRecord
import com.roserequiem.app.domain.model.DownloadStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BatchFilteringTest {
    private val records = listOf(
        DownloadRecord(
            filePath = "/music/one.mp3",
            songName = "First Song",
            artist = "Example Artist",
            status = DownloadStatus.SUCCESS,
            timestamp = 100L,
            batchId = "batch-a",
        ),
        DownloadRecord(
            filePath = "/music/two.mp3",
            songName = "Second Song",
            artist = "Another Artist",
            status = DownloadStatus.FAILED,
            timestamp = 110L,
            batchId = "batch-a",
        ),
        DownloadRecord(
            filePath = "/music/three.mp3",
            songName = "Third Song",
            artist = "Example Artist",
            status = DownloadStatus.SUCCESS,
            timestamp = 200L,
            batchId = "batch-b",
        ),
    )

    @Test
    fun searchFiltersRowsWithoutChangingBatchTotals() {
        val batch = groupIntoBatches(records).first { it.batchId == "batch-a" }

        val result = filterBatches(listOf(batch), searchQuery = "second", statusFilter = null)

        assertEquals(1, result.size)
        assertEquals(2, result.single().summary.total)
        assertEquals(listOf("Second Song"), result.single().matchingRecords.map { it.songName })
    }

    @Test
    fun statusFilterDropsBatchesWithoutMatches() {
        val result = filterBatches(
            groupIntoBatches(records),
            searchQuery = "",
            statusFilter = DownloadStatus.FAILED,
        )

        assertEquals(1, result.size)
        assertEquals("batch-a", result.single().summary.batchId)
        assertEquals(2, result.single().summary.total)
        assertTrue(result.single().matchingRecords.all { it.status == DownloadStatus.FAILED })
    }

    @Test
    fun blankSearchAndNoStatusReturnEveryRecordInGroupedBatches() {
        val result = filterBatches(groupIntoBatches(records), searchQuery = "  ", statusFilter = null)

        assertEquals(2, result.size)
        assertEquals(3, result.sumOf { it.matchingRecords.size })
    }
}
