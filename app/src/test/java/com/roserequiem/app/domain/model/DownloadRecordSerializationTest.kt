package com.roserequiem.app.domain.model

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class DownloadRecordSerializationTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun legacyHistoryWithoutBatchIdStillLoads() {
        val legacyJson = """
            [
              {
                "filePath": "/music/track.flac",
                "songName": "Track",
                "artist": "Artist",
                "status": "SUCCESS",
                "timestamp": 1234
              }
            ]
        """.trimIndent()

        val records = json.decodeFromString<List<DownloadRecord>>(legacyJson)

        assertEquals(1, records.size)
        assertEquals("/music/track.flac", records.single().filePath)
        assertEquals("", records.single().batchId)
        assertEquals(0L, records.single().batchStartedAt)
        assertEquals(DownloadStatus.SUCCESS, records.single().status)
    }

    @Test
    fun historyWithBatchIdPreservesBatchGrouping() {
        val currentJson = """
            [
              {
                "filePath": "/music/track.flac",
                "songName": "Track",
                "artist": "Artist",
                "status": "PAUSED",
                "timestamp": 1234,
                "batchId": "run-42",
                "batchStartedAt": 1200
              }
            ]
        """.trimIndent()

        val records = json.decodeFromString<List<DownloadRecord>>(currentJson)

        assertEquals("run-42", records.single().batchId)
        assertEquals(1200L, records.single().batchStartedAt)
        assertEquals(DownloadStatus.PAUSED, records.single().status)
    }
}
