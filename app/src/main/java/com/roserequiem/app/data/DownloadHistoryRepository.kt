package com.roserequiem.app.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.roserequiem.app.domain.model.DownloadRecord
import com.roserequiem.app.domain.model.DownloadStatus
import com.roserequiem.app.domain.model.Song
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

private val Context.downloadHistoryDataStore by preferencesDataStore(name = "download_history")
private val RECORDS_KEY = stringPreferencesKey("records_json")

/**
 * Persisted per-song lyrics download history: what's queued, downloading, or finished,
 * and how it finished. Lives in its own DataStore file rather than
 * [com.roserequiem.app.util.dataStore]'s settings store, since this is an append-heavy list
 * rather than a fixed set of preference keys.
 *
 * A plain object singleton -- the same shape as
 * [com.roserequiem.app.services.BatchDownloadService]'s companion state -- so the foreground
 * download service and any visible screen share one in-memory copy without a DI graph.
 * [init] loads the saved history once; after that every mutation updates [records]
 * immediately in memory and persists to disk in the background, debounced so a large
 * batch doesn't trigger a disk write per song.
 */
object DownloadHistoryRepository {
    /**
     * Retained per-song history ceiling. This is not a download-run limit: the service
     * processes large requests in bounded chunks independently. The Downloads screen
     * groups all chunks from one run into one logical batch row.
     */
    const val MAX_RECORDS = 5_000
    private val json = Json { ignoreUnknownKeys = true }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    // Guards init() itself against running twice -- flips true the instant init() is
    // called, well before the disk read finishes. Distinct from isLoaded below.
    private val initStarted = AtomicBoolean(false)
    private val persistTrigger = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    private lateinit var appContext: Context

    private val _records = MutableStateFlow<List<DownloadRecord>>(emptyList())
    val records: StateFlow<List<DownloadRecord>> = _records.asStateFlow()

    // batchIds belonging to whatever multi-chunk run is currently in progress (set by
    // BatchDownloadService before it starts queueing chunks, cleared when the whole run
    // ends). trimToCap keeps every record in this set ahead of anything outside it,
    // regardless of timestamp -- without this, a run whose chunks cumulatively exceed
    // MAX_RECORDS would evict its own earlier, already-finished chunks as later ones
    // get queued, which is a visibly broken experience (a completed batch disappearing
    // off the Downloads screen mid-run) rather than just an invisible history cap.
    private val _protectedBatchIds = MutableStateFlow<Set<String>>(emptySet())

    /** Call before queueing the first chunk of a multi-chunk run. See [_protectedBatchIds]. */
    fun protectBatches(batchIds: Set<String>) {
        _protectedBatchIds.value = batchIds
    }

    /** Call once the whole run (every chunk) has finished, been cancelled, or paused. */
    fun clearProtectedBatches() {
        _protectedBatchIds.value = emptySet()
    }

    /**
     * The shared eviction policy behind every [MAX_RECORDS] cap in this file: keep every
     * record belonging to a currently-protected batch (see [_protectedBatchIds]) first,
     * then fill whatever's left with the most recent records outside that set. Only
     * falls back to trimming *within* the protected set itself if the protected set
     * alone is larger than the cap -- i.e. the currently-running download itself has
     * queued more than [MAX_RECORDS] songs, at which point there's no way to show all of
     * it and the oldest-finished chunks of the same run are what gives.
     */
    private fun trimToCap(records: Collection<DownloadRecord>): List<DownloadRecord> {
        val protected = _protectedBatchIds.value
        if (protected.isEmpty()) return records.sortedByDescending { it.timestamp }.take(MAX_RECORDS)

        val (protectedRecords, others) = records.partition { it.batchId in protected }
        return if (protectedRecords.size >= MAX_RECORDS) {
            protectedRecords.sortedByDescending { it.timestamp }.take(MAX_RECORDS)
        } else {
            protectedRecords + others.sortedByDescending { it.timestamp }.take(MAX_RECORDS - protectedRecords.size)
        }
    }

    // True only once the initial disk read has actually completed (or failed/found
    // nothing). The Downloads screen uses this to show a loading state instead of
    // briefly flashing "no downloads yet" on a cold start before history has loaded.
    private val _isLoaded = MutableStateFlow(false)
    val isLoaded: StateFlow<Boolean> = _isLoaded.asStateFlow()

    /** Call once, e.g. from [android.app.Application.onCreate]. Safe to call more than once. */
    fun init(context: Context) {
        if (!initStarted.compareAndSet(false, true)) return
        appContext = context.applicationContext

        scope.launch {
            val stored = appContext.downloadHistoryDataStore.data.first()[RECORDS_KEY]
            if (stored != null) {
                runCatching { json.decodeFromString<List<DownloadRecord>>(stored) }
                    .onSuccess { fromDisk ->
                        // Merge rather than overwrite: if a batch already started (e.g.
                        // via markQueued) in the brief window between init() being
                        // called and this disk read finishing, those in-memory records
                        // are strictly newer than anything on disk and must win --
                        // clobbering them here would silently drop an in-flight batch.
                        _records.update { inMemory ->
                            val byPath = fromDisk.associateBy { it.filePath }.toMutableMap()
                            for (record in inMemory) byPath[record.filePath] = record
                            trimToCap(byPath.values)
                        }
                    }
            }
            // Flip this after the read whether or not there was anything to load --
            // "loaded, found nothing" and "still loading" need to be distinguishable.
            _isLoaded.value = true
        }

        scope.launch {
            persistTrigger.debounce(500).collect { persistNow() }
        }
    }

    /**
     * Marks a fresh batch as queued, tagging every song with [batchId] so the history
     * list can group them into one run. A song already present (e.g. a re-download) has
     * its existing record replaced rather than duplicated -- it moves to the new batch.
     * [batchId] defaults to a fresh one for a standalone call; a multi-chunk run passes
     * its own pre-generated id per chunk instead (see [BatchDownloadService]) so it can
     * [protectBatches] all of them before any chunk is queued.
     */
    fun markQueued(songs: List<Song>, batchId: String = UUID.randomUUID().toString()) {
        val now = System.currentTimeMillis()
        _records.update { current ->
            val byPath = current.associateBy { it.filePath }.toMutableMap()
            for (song in songs) {
                val filePath = song.filePath ?: continue
                byPath[filePath] = DownloadRecord(
                    filePath = filePath,
                    songName = song.title ?: filePath.substringAfterLast('/'),
                    artist = song.artist ?: "",
                    status = DownloadStatus.QUEUED,
                    timestamp = now,
                    batchId = batchId,
                    batchStartedAt = now,
                )
            }
            byPath.values.let(::trimToCap)
        }
        persistTrigger.tryEmit(Unit)
    }

    /** Updates one song's record in place, e.g. QUEUED -> DOWNLOADING -> SUCCESS. */
    fun updateStatus(filePath: String?, status: DownloadStatus) {
        if (filePath == null) return
        _records.update { current ->
            current.map {
                if (it.filePath == filePath) {
                    it.copy(status = status, timestamp = System.currentTimeMillis())
                } else it
            }
        }
        persistTrigger.tryEmit(Unit)
    }

    /** Drops every record whose status is a terminal one (not queued/downloading). */
    fun clearFinished() {
        _records.update { current ->
            current.filter { it.status == DownloadStatus.QUEUED || it.status == DownloadStatus.DOWNLOADING }
        }
        persistTrigger.tryEmit(Unit)
    }

    /**
     * Marks any of the given songs that are still QUEUED/DOWNLOADING as FAILED. For use
     * when the user explicitly cancels a batch (or it crashes mid-run), so those rows
     * don't stay stuck showing "downloading" forever. See [markPaused] for the
     * app-was-closed case, which is resumable rather than a failure.
     */
    fun markInterrupted(songs: List<Song>) {
        val filePaths = songs.mapNotNull { it.filePath }.toSet()
        _records.update { current ->
            current.map {
                if (it.filePath in filePaths &&
                    (it.status == DownloadStatus.QUEUED || it.status == DownloadStatus.DOWNLOADING)
                ) {
                    it.copy(status = DownloadStatus.FAILED, timestamp = System.currentTimeMillis())
                } else it
            }
        }
        persistTrigger.tryEmit(Unit)
    }

    /**
     * Marks any of the given songs that are still QUEUED/DOWNLOADING as PAUSED. Used by
     * [com.roserequiem.app.services.BatchDownloadService.onTaskRemoved] when the app itself
     * is closed mid-batch -- unlike [markInterrupted], this isn't the user giving up on
     * these songs, so they're resumable via the Downloads screen's "Continue" action
     * rather than shown as failed.
     */
    fun markPaused(songs: List<Song>) {
        val filePaths = songs.mapNotNull { it.filePath }.toSet()
        _records.update { current ->
            current.map {
                if (it.filePath in filePaths &&
                    (it.status == DownloadStatus.QUEUED || it.status == DownloadStatus.DOWNLOADING)
                ) {
                    it.copy(status = DownloadStatus.PAUSED, timestamp = System.currentTimeMillis())
                } else it
            }
        }
        persistTrigger.tryEmit(Unit)
    }

    /**
     * Deletes the given records from history outright (as opposed to [clearFinished],
     * which sweeps every terminal-state record). Backs the Downloads screen's
     * multi-select "Remove" action, so a user can drop a handful of finished/paused
     * rows without losing everything else finished.
     */
    fun removeRecords(filePaths: Set<String>) {
        _records.update { current -> current.filterNot { it.filePath in filePaths } }
        persistTrigger.tryEmit(Unit)
    }

    /**
     * Re-adds records exactly as they were -- backs the Downloads screen's undo
     * snackbar after [removeRecords]/[clearFinished]. If a record's filePath was
     * re-queued into a new download in the meantime (e.g. the undo window overlapped
     * with starting a fresh batch), that newer record wins rather than being
     * clobbered by the restored one.
     */
    fun restoreRecords(records: List<DownloadRecord>) {
        if (records.isEmpty()) return
        _records.update { current ->
            val byPath = current.associateBy { it.filePath }.toMutableMap()
            for (record in records) {
                if (record.filePath !in byPath) byPath[record.filePath] = record
            }
            trimToCap(byPath.values)
        }
        persistTrigger.tryEmit(Unit)
    }

    private suspend fun persistNow() {
        if (!_isLoaded.value) return
        val encoded = json.encodeToString(_records.value)
        appContext.downloadHistoryDataStore.edit { it[RECORDS_KEY] = encoded }
    }
}
