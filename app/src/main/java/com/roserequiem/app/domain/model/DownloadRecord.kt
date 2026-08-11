package com.roserequiem.app.domain.model

import kotlinx.serialization.Serializable

/**
 * Where one song's lyrics download currently stands, or how it ended up.
 */
@Serializable
enum class DownloadStatus {
    QUEUED,
    DOWNLOADING,
    SUCCESS,
    NO_LYRICS,
    FAILED,
    SKIPPED,

    /**
     * Was still QUEUED/DOWNLOADING when the app was closed (task swiped away) while its
     * batch was running. Distinct from [FAILED] -- an explicit user Cancel still means
     * FAILED (they gave up on it), but a paused song is resumable: the Downloads
     * screen's "Continue" action re-queues exactly the songs sitting in this state.
     */
    PAUSED,
}

/**
 * One row in the Downloads history: a single song's lyrics download, from the moment
 * it's queued through to however it finished. Persisted by
 * [com.roserequiem.app.data.DownloadHistoryRepository].
 *
 * @param filePath Identifies the song and doubles as this record's key -- re-queuing the
 * same file replaces its old record rather than adding a duplicate.
 * @param batchId Groups every song queued together in one
 * [com.roserequiem.app.services.BatchDownloadService] run, so the history list can show one
 * summarized row per batch instead of one row per song. Defaults to "" so JSON saved by
 * an older build (before batching existed) still decodes instead of losing history.
 * @param timestamp Wall-clock time of the last status change, used for sorting and shown
 * as the "when" in the history list.
 */
@Serializable
data class DownloadRecord(
    val filePath: String,
    val songName: String,
    val artist: String,
    val status: DownloadStatus,
    val timestamp: Long,
    val batchId: String = "",
)
