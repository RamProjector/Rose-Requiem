package com.roserequiem.app.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.roserequiem.app.MainActivity
import kotlinx.coroutines.CancellationException
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import com.roserequiem.app.R
import com.roserequiem.app.data.DownloadHistoryRepository
import com.roserequiem.app.data.UserSettingsController
import com.roserequiem.app.data.remote.lyrics_providers.LyricsProviderService
import com.roserequiem.app.domain.model.Song
import com.roserequiem.app.ui.screens.home.HomeViewModel
import com.roserequiem.app.util.FlaggedSong
import com.roserequiem.app.util.dataStore
import com.roserequiem.app.util.downloadLyrics

/**
 * Runs a batch lyrics download as a foreground service so it keeps going if the user
 * merely leaves the app (switches away, locks the screen, etc.), with an ongoing
 * notification (progress bar + cancel action, plus a "go to downloads" action) that's
 * replaced by a summary notification when it finishes.
 *
 * If the app's task is actually closed (swiped away in recents) while a batch is
 * running, [onTaskRemoved] pauses it instead of continuing indefinitely in the
 * background -- see that function's doc for why. Whatever hasn't finished yet is left
 * resumable from the Downloads screen rather than silently lost or marked failed.
 *
 * Reuses the same [downloadLyrics] used by the in-app flow — this service just gives it
 * somewhere to run that isn't tied to a screen's lifecycle, and owns its own
 * [UserSettingsController]/[LyricsProviderService] instances rather than reusing the
 * Activity's, since a background job shouldn't depend on a screen being alive.
 *
 * [state] is exposed so the in-app UI can still mirror live progress while visible,
 * without being the thing driving the download.
 *
 * Only one batch is ever meant to run at a time -- [onStartCommand] enforces that by
 * rejecting a new start request while [downloadJob] is still active (see [isActive]),
 * rather than racing two downloads against the same [state] and notification IDs.
 */
class BatchDownloadService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var downloadJob: Job? = null

    // Distinguishes *why* downloadJob got cancelled: true if it was onTaskRemoved
    // (app closed -- pause and let the user resume later), false for an explicit
    // ACTION_CANCEL from the user (give up -- mark failed, as before). Reset at the
    // start of every new batch.
    private var pausedDueToTaskRemoval = false

    override fun onBind(intent: Intent?): IBinder? = null

    /**
     * Called when the user removes this app's task (e.g. swiping it away in recents)
     * while a batch is still running. Foreground services otherwise keep running
     * through this by default, which is exactly what this service used to rely on --
     * but silently continuing to burn data/battery for an app the user just closed
     * isn't what "closing the app" should mean. Pausing (rather than letting it run
     * to completion or getting killed mid-song with no clean status) means whatever
     * hasn't finished yet shows up as resumable on the Downloads screen instead of
     * either invisible or wrongly marked failed.
     */
    override fun onTaskRemoved(rootIntent: Intent?) {
        if (downloadJob?.isActive == true) {
            pausedDueToTaskRemoval = true
            downloadJob?.cancel()
        }
        super.onTaskRemoved(rootIntent)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Always call this first and unconditionally. This service can be started via
        // startForegroundService(), which requires startForeground() to be called
        // within seconds -- bailing out in a branch below without calling it first
        // risks a ForegroundServiceDidNotStartInTimeException crash. If we already have
        // real progress to show (e.g. this is a cancel intent arriving mid-download),
        // re-post that instead of a bogus 0/1 placeholder.
        createNotificationChannel()
        val current = _state.value
        startForeground(PROGRESS_NOTIFICATION_ID, buildProgressNotification(current))

        if (intent?.action == ACTION_CANCEL) {
            downloadJob?.cancel()
            return START_NOT_STICKY
        }

        val songs = intent?.getParcelableArrayListExtra<Song>(EXTRA_SONGS)
        if (songs.isNullOrEmpty()) {
            stopSelf()
            return START_NOT_STICKY
        }

        // Without this guard, a start request arriving while downloadJob is still active
        // would overwrite _state (losing the running batch's progress) and launch a second
        // coroutine on the same serviceScope -- both writing the same notification IDs, and
        // whichever finishes first would call stopSelf() in its `finally` below, tearing down
        // serviceScope and killing the other, still-in-progress batch with it. Callers
        // (Downloads' Continue/Retry) already check isActive() before sending this intent;
        // this is the last line of defense in case that's ever bypassed.
        if (downloadJob?.isActive == true) {
            Log.w(TAG, "Ignoring batch start request: a batch is already in progress.")
            return START_NOT_STICKY
        }

        val ignoreEmbeddedLyrics = intent.getBooleanExtra(EXTRA_IGNORE_EMBEDDED_LYRICS, false)

        pausedDueToTaskRemoval = false
        // Split into chunks up front, but only ever queue (markQueued) one at a time,
        // right before it starts -- see CHUNK_SIZE's doc for why queueing them all
        // immediately would be actively worse than not chunking at all.
        val chunks = songs.chunked(CHUNK_SIZE)
        // All batchIds decided now, not generated lazily inside markQueued, so they can
        // be protected as a group before the first one is even queued -- otherwise a
        // 1200-song/5-chunk run would evict its own chunk 1 (already finished!) the
        // moment chunk 3's markQueued call pushes the total past MAX_RECORDS.
        val batchIds = List(chunks.size) { UUID.randomUUID().toString() }
        DownloadHistoryRepository.protectBatches(batchIds.toSet())
        _state.value = BatchDownloadState(
            total = songs.size,
            ignoreEmbeddedLyrics = ignoreEmbeddedLyrics,
            chunkCount = chunks.size,
        )
        startForeground(PROGRESS_NOTIFICATION_ID, buildProgressNotification(_state.value))

        downloadJob = serviceScope.launch {
            val userSettingsController = UserSettingsController(applicationContext.dataStore)
            val lyricsProviderService = LyricsProviderService()
            val viewModel = HomeViewModel(userSettingsController, lyricsProviderService)

            // Running totals carried across chunks -- each downloadLyrics() call below
            // reports counts from zero for just its own chunk, so these are what turn
            // that back into a total that covers the whole run for the UI/notification.
            var baseSuccess = 0
            var baseNoLyrics = 0
            var baseFailed = 0
            var baseSkipped = 0
            val allFlaggedSongs = mutableListOf<FlaggedSong>()

            try {
                for ((index, chunk) in chunks.withIndex()) {
                    _state.value = _state.value?.copy(chunkIndex = index + 1)
                    DownloadHistoryRepository.markQueued(chunk, batchIds[index])

                    downloadLyrics(
                        songs = chunk,
                        viewModel = viewModel,
                        context = applicationContext,
                        ignoreEmbeddedLyrics = ignoreEmbeddedLyrics,
                        onProgressUpdate = { successCount, noLyricsCount, failedCount, skippedCount ->
                            val completed = baseSuccess + successCount + baseNoLyrics + noLyricsCount +
                                baseFailed + failedCount + baseSkipped + skippedCount
                            _state.value = _state.value?.copy(
                                completed = completed,
                                successCount = baseSuccess + successCount,
                                noLyricsCount = baseNoLyrics + noLyricsCount,
                                failedCount = baseFailed + failedCount,
                                skippedCount = baseSkipped + skippedCount,
                            )
                            notify(PROGRESS_NOTIFICATION_ID, buildProgressNotification(_state.value))
                        },
                        onDownloadComplete = { flaggedSongs -> allFlaggedSongs += flaggedSongs },
                        onRateLimitReached = { _state.value = _state.value?.copy(isRateLimited = true) },
                        onSongStatusChanged = { song, status ->
                            DownloadHistoryRepository.updateStatus(song.filePath, status)
                        }
                    )

                    // Roll this chunk's final counts into the running base before the
                    // next chunk's onProgressUpdate starts counting from zero again.
                    val finished = _state.value
                    baseSuccess = finished?.successCount ?: baseSuccess
                    baseNoLyrics = finished?.noLyricsCount ?: baseNoLyrics
                    baseFailed = finished?.failedCount ?: baseFailed
                    baseSkipped = finished?.skippedCount ?: baseSkipped

                    // A rate limit hit on this chunk will just be hit again immediately
                    // by the next one -- stop starting further chunks rather than
                    // grinding through the rest of them the same way.
                    if (_state.value?.isRateLimited == true) break
                }
                _state.value = _state.value?.copy(isComplete = true, flaggedSongs = allFlaggedSongs)
                notify(RESULT_NOTIFICATION_ID, buildCompleteNotification(_state.value))
            } catch (e: CancellationException) {
                _state.value = _state.value?.copy(isComplete = true)
                if (pausedDueToTaskRemoval) {
                    DownloadHistoryRepository.markPaused(songs)
                    notify(RESULT_NOTIFICATION_ID, buildPausedNotification())
                } else {
                    DownloadHistoryRepository.markInterrupted(songs)
                    notify(RESULT_NOTIFICATION_ID, buildCancelledNotification())
                }
            } finally {
                // Always runs regardless of which exit path was taken above (finished
                // normally, broke out early on a rate limit, or was cancelled) -- this
                // run's batches stop being protected the moment it's actually done,
                // rather than only on the success path.
                DownloadHistoryRepository.clearProtectedBatches()
                // The result notification above is independent of this service's
                // foreground status (separate ID), so it isn't affected by removing
                // the ongoing progress notification here -- no detach/timing race to
                // get right, it just always works.
                ServiceCompat.stopForeground(this@BatchDownloadService, ServiceCompat.STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }

        return START_NOT_STICKY
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun notify(id: Int, notification: Notification) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(id, notification)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return

        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                getString(R.string.batch_download_lyrics),
                NotificationManager.IMPORTANCE_LOW
            )
        )
    }

    private fun cancelPendingIntent(): PendingIntent {
        val intent = Intent(this, BatchDownloadService::class.java).apply { action = ACTION_CANCEL }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        return PendingIntent.getService(this, 0, intent, flags)
    }

    /**
     * Opens the app straight to the Downloads screen -- used both as the notification's
     * tap target and as an explicit "View downloads" action, so there's always a direct
     * way back to progress/results without having to remember to tap the bottom nav.
     * [MainActivity] is `singleTop`, so this reuses the existing instance (calling
     * onNewIntent) instead of stacking a duplicate on repeated taps.
     */
    private fun goToDownloadsPendingIntent(): PendingIntent {
        val intent = Intent(this, MainActivity::class.java).apply {
            action = MainActivity.ACTION_VIEW_DOWNLOADS
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        return PendingIntent.getActivity(this, 1, intent, flags)
    }

    private fun buildProgressNotification(state: BatchDownloadState?): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.batch_download_lyrics))
            .setContentText(
                if ((state?.chunkCount ?: 1) > 1) {
                    getString(
                        R.string.progress_with_batch,
                        state?.completed ?: 0, state?.total ?: 1,
                        state?.chunkIndex ?: 1, state?.chunkCount ?: 1
                    )
                } else {
                    "${state?.completed ?: 0} / ${state?.total ?: 1}"
                }
            )
            .setProgress(state?.total ?: 1, state?.completed ?: 0, false)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(goToDownloadsPendingIntent())
            .addAction(0, getString(R.string.go_to_downloads), goToDownloadsPendingIntent())
            .addAction(0, getString(R.string.cancel), cancelPendingIntent())
            .build()

    private fun buildCompleteNotification(state: BatchDownloadState?): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.download_complete))
            .setContentText(
                getString(
                    R.string.success_failed,
                    state?.successCount ?: 0,
                    state?.noLyricsCount ?: 0,
                    state?.failedCount ?: 0
                )
            )
            .setAutoCancel(true)
            .setOngoing(false)
            .setContentIntent(goToDownloadsPendingIntent())
            .addAction(0, getString(R.string.go_to_downloads), goToDownloadsPendingIntent())
            .build()

    private fun buildCancelledNotification(): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.batch_download_lyrics))
            .setContentText(getString(R.string.cancel))
            .setAutoCancel(true)
            .setOngoing(false)
            .setContentIntent(goToDownloadsPendingIntent())
            .build()

    /**
     * Shown instead of [buildCancelledNotification] when the batch stopped because the
     * app's task was closed, not because the user cancelled it -- tapping "Continue"-worthy
     * content lives on the Downloads screen, so this leans on [goToDownloadsPendingIntent]
     * to get the user straight there.
     */
    private fun buildPausedNotification(): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.batch_download_lyrics))
            .setContentText(getString(R.string.download_paused_reopen_to_continue))
            .setAutoCancel(true)
            .setOngoing(false)
            .setContentIntent(goToDownloadsPendingIntent())
            .addAction(0, getString(R.string.go_to_downloads), goToDownloadsPendingIntent())
            .build()

    companion object {
        private const val TAG = "BatchDownloadService"
        private const val CHANNEL_ID = "batch_lyrics_download"
        private const val PROGRESS_NOTIFICATION_ID = 4200
        private const val RESULT_NOTIFICATION_ID = 4201
        private const val ACTION_CANCEL = "com.roserequiem.app.action.CANCEL_BATCH_DOWNLOAD"
        private const val EXTRA_SONGS = "extra_songs"
        private const val EXTRA_IGNORE_EMBEDDED_LYRICS = "extra_ignore_embedded_lyrics"

        /**
         * Songs per [DownloadHistoryRepository.markQueued] call. Kept well under
         * [DownloadHistoryRepository.MAX_RECORDS] (half of it), not sized against it --
         * a chunk this size queues, leaving comfortable headroom for whatever history
         * already existed before this run to survive alongside it instead of being
         * evicted the moment the chunk is queued. Each chunk gets its own batchId, so on
         * the Downloads screen a huge run just shows up as several ordinary batch cards
         * rather than needing any new UI concept.
         */
        private const val CHUNK_SIZE = DownloadHistoryRepository.MAX_RECORDS / 2

        private val _state = MutableStateFlow<BatchDownloadState?>(null)

        /** Live progress for the in-app UI to mirror while it's visible. Null when idle. */
        val state: StateFlow<BatchDownloadState?> = _state.asStateFlow()

        /**
         * Whether a batch is currently running (started but not yet finished/consumed).
         * Callers that can trigger a new batch (Downloads' Continue/Retry, Home's batch
         * trigger) should check this first -- only one batch is ever allowed to run at once.
         */
        fun isActive(): Boolean = _state.value?.isComplete == false

        fun start(context: Context, songs: ArrayList<Song>, ignoreEmbeddedLyrics: Boolean) {
            val intent = Intent(context, BatchDownloadService::class.java).apply {
                putParcelableArrayListExtra(EXTRA_SONGS, songs)
                putExtra(EXTRA_IGNORE_EMBEDDED_LYRICS, ignoreEmbeddedLyrics)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                @Suppress("DEPRECATION")
                context.startService(intent)
            }
        }

        fun cancel(context: Context) {
            val intent = Intent(context, BatchDownloadService::class.java).apply {
                action = ACTION_CANCEL
            }
            context.startService(intent)
        }

        /** Clears the mirrored state once the UI has shown the final result. */
        fun consumeState() {
            _state.value = null
        }
    }
}

data class BatchDownloadState(
    val total: Int,
    val completed: Int = 0,
    val successCount: Int = 0,
    val noLyricsCount: Int = 0,
    val failedCount: Int = 0,
    val skippedCount: Int = 0,
    val ignoreEmbeddedLyrics: Boolean = false,
    val isComplete: Boolean = false,
    val isRateLimited: Boolean = false,
    val flaggedSongs: List<FlaggedSong> = emptyList(),
    // 1-based index of the chunk currently running, and how many chunks this run was
    // split into -- see CHUNK_SIZE below. chunkCount is 1 (chunkIndex always 1) for any
    // run that didn't need splitting, so existing UI that ignores these two fields keeps
    // working unchanged.
    val chunkIndex: Int = 1,
    val chunkCount: Int = 1,
)
