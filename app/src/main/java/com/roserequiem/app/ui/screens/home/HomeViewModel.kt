package com.roserequiem.app.ui.screens.home

import android.content.ComponentName
import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaSessionManager
import android.net.Uri
import android.provider.MediaStore
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.util.fastMapNotNull
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch
import com.roserequiem.app.R
import com.roserequiem.app.data.UserSettingsController
import com.roserequiem.app.data.remote.lyrics_providers.LyricsProviderService
import com.roserequiem.app.domain.model.Song
import com.roserequiem.app.domain.model.SongInfo
import com.roserequiem.app.domain.model.SortOrders
import com.roserequiem.app.domain.model.SortValues
import com.roserequiem.app.services.NotificationListener
import com.roserequiem.app.util.ext.toLrcFile
import java.io.File
import java.util.UUID


/**
 * Resolves the visible Home source without using list emptiness as a proxy for whether
 * filters are active. An empty filtered list is a valid result and must stay empty.
 */
internal fun resolveHomeDisplaySongs(
    allSongs: List<Song>,
    filteredSongs: List<Song>,
    searchResults: List<Song>,
    searchActive: Boolean,
    filtersActive: Boolean,
): List<Song> = when {
    searchActive -> searchResults
    filtersActive -> filteredSongs
    else -> allSongs
}

/**
 * ViewModel class for the main functionality of the app.
 */
class HomeViewModel(
    val userSettingsController: UserSettingsController,
    private val lyricsProviderService: LyricsProviderService
) : ViewModel() {
    var cachedSongs: List<Song>? = null

    // SnapshotStateMap, not SnapshotStateList: reading `selectedSongs.contains(path)` (or
    // `containsKey`) inside a row only subscribes that row to changes for that one key.
    // A SnapshotStateList's .contains() reads the list's whole structural state, so every
    // currently-composed row -- not just the one being tapped -- recomposes on every
    // selection change. On a 1000+ song list that's every visible row recomposing per tap
    // while in selection mode, which is exactly when the user is also actively scrolling.
    val selectedSongs = mutableStateMapOf<String, Unit>()
    var allSongs by mutableStateOf<List<Song>?>(null)

    var searchQuery by mutableStateOf("")

    var playingSongTitle by mutableStateOf("")
    var playingSongArtist by mutableStateOf("")
    var playingSongAlbumArt by mutableStateOf<Uri?>(null)
    var playingSongFilePath by mutableStateOf("")

    // Filter settings
    private var cachedFolders: MutableList<String>? = null
    private var hideFolders = userSettingsController.blacklistedFolders.isNotEmpty()

    // Filter activity is separate from the result list: an active filter can legitimately
    // produce zero matches, while an empty result list with no active filters means "show all".
    private var _cachedFilteredSongs = MutableStateFlow<List<Song>>(emptyList())
    private val _filtersActive = MutableStateFlow(
        userSettingsController.hideLyrics || userSettingsController.blacklistedFolders.isNotEmpty()
    )

    private fun hasActiveFilters(): Boolean =
        userSettingsController.hideLyrics || userSettingsController.blacklistedFolders.isNotEmpty()

    private fun currentFilterSource(): List<Song> =
        if (hasActiveFilters()) _cachedFilteredSongs.value else cachedSongs.orEmpty()

    // searching
    private var _searchResults = MutableStateFlow<List<Song>>(emptyList())
    private var searchJob: Job? = null

    var displaySongs by mutableStateOf(
        when {
            searchQuery.isNotEmpty() -> _searchResults.value
            _filtersActive.value -> _cachedFilteredSongs.value
            else -> allSongs.orEmpty()
        }
    )

    var showFilters by mutableStateOf(false)
    var showSort by mutableStateOf(false)
    var showingSearch by  mutableStateOf(false)
    var showSearch by mutableStateOf(showingSearch)

    val songsToBatchDownload by derivedStateOf {
        if (selectedSongs.isEmpty())
            displaySongs
        else
            (allSongs ?: listOf()).filter { selectedSongs.containsKey(it.filePath) }.toList()
    }

    init { viewModelScope.launch { updateSongsToDisplay() } }

    @OptIn(ExperimentalCoroutinesApi::class)
    private suspend fun updateSongsToDisplay() = coroutineScope {
        snapshotFlow { allSongs }
            .filterNotNull()
            // simple .combine wasn't enough apparently, so im using this
            .flatMapLatest { all ->
                combine(_cachedFilteredSongs, _searchResults, _filtersActive) { filtered, searchResults, filtersActive ->
                    resolveHomeDisplaySongs(
                        allSongs = all,
                        filteredSongs = filtered,
                        searchResults = searchResults,
                        searchActive = searchQuery.isNotEmpty(),
                        filtersActive = filtersActive,
                    )
                }
            }.collect { newDisplaySongs ->
                displaySongs = newDisplaySongs
            }
    }

    fun updateAllSongs(context: Context, sortBy: SortValues, sortOrder: SortOrders) = viewModelScope.launch(Dispatchers.IO) {
        allSongs = getAllSongs(context, sortBy, sortOrder)
        // Read active-session metadata and write the transient artwork snapshot off the main thread.
        // Run this after publishing the song list so matching the current track cannot race `allSongs`.
        updatePlayingSongInfo(context)
    }

    /**
     * Loads all songs from the MediaStore.
     * @param context The application context.
     * @return A list of Song objects representing the songs.
     */
    private fun getAllSongs(context: Context, sortBy: SortValues, sortOrder: SortOrders): List<Song> {
        return cachedSongs ?: run {
            val selection = MediaStore.Audio.Media.IS_MUSIC + "!= 0"
            val projection = arrayOf(
                MediaStore.Audio.Media._ID,
                MediaStore.Audio.Media.TITLE,
                MediaStore.Audio.Media.ARTIST,
                MediaStore.Audio.Media.DATA,
                MediaStore.Audio.Media.ALBUM_ID,
            )
            val sortOrder = sortBy.name + " " + sortOrder.queryName

            val songs = mutableListOf<Song>()
            val cursor = context.contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                null,
                sortOrder
            )

            cursor?.use {
                val idColumn = it.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val titleColumn = it.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val artistColumn = it.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val albumIdColumn = it.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
                val pathColumn = it.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)

                while (it.moveToNext()) {
                    val id = it.getLong(idColumn)
                    val title = it.getString(titleColumn).let { str ->
                        if (str == "<unknown>") null else str
                    }
                    val artist = it.getString(artistColumn).let { str ->
                        if (str == "<unknown>") null else str
                    }
                    val albumId = it.getLong(albumIdColumn)
                    val filePath = it.getString(pathColumn)

                    @Suppress("SpellCheckingInspection")
                    val sArtworkUri = Uri.parse("content://media/external/audio/albumart")
                    val imgUri = ContentUris.withAppendedId(
                        sArtworkUri,
                        albumId
                    )

                    // mediaStoreId retained (not just used locally) so AlbumArtFetcher can
                    // request a per-song thumbnail on API 29+ -- see Song.mediaStoreId kdoc.
                    val song = Song(title, artist, imgUri, filePath, mediaStoreId = id)
                    songs.add(song)
                }
            }
            cursor?.close()
            cachedSongs = songs
            viewModelScope.launch { filterSongs() }
            cachedSongs!!
        }
    }

    /**
     * Updates song search (filter) results based on the query.
     * Cancels any in-flight search before starting a new one, and debounces
     * non-empty queries, so a slower/older keystroke's results can never
     * overwrite a newer keystroke's results.
     * @param query The search query.
     */
    fun updateSearchResults(query: String) {
        searchJob?.cancel()

        if (query.isEmpty()) {
            // Clearing the search should feel instant - no debounce.
            searchJob = viewModelScope.launch(Dispatchers.IO) {
                _searchResults.value = currentFilterSource()
            }
            return
        }

        searchJob = viewModelScope.launch(Dispatchers.IO) {
            delay(SEARCH_DEBOUNCE_MS) // let fast typing settle before filtering;
            // this also acts as our cancellation checkpoint, since a newer
            // keystroke will have already cancelled this job by the time
            // delay() returns.

            val data = currentFilterSource()
            if (cachedSongs == null) return@launch

            val results = data.filter {
                it.title?.contains(query, ignoreCase = true) == true ||
                it.artist?.contains(query, ignoreCase = true) == true
            }

            ensureActive() // belt-and-suspenders: never publish stale results,
            // even if this job was cancelled right after filtering finished.
            _searchResults.value = results
        }
    }

    /**
     * Loads all songs' folders
     * @param context The application context.
     * @return A list of folders.
     */
    fun getSongFolders(context: Context): List<String> {
        return cachedFolders ?: run {
            val folders = mutableListOf<String>()

            for (song in getAllSongs(context, SortValues.TITLE, SortOrders.ASCENDING)) {
                val path = song.filePath
                val folder = path?.substring(0, path.lastIndexOf("/"))
                if (folder != null && !folders.contains(folder))
                    folders.add(folder)
            }

            cachedFolders = folders
            cachedFolders!!
        }
    }

    /**
     * Filter songs based on user's preferences.
     * @return A list of songs depending on the user's preferences. If no preferences are set, null is returned, so app will use all songs.
     */
    fun filterSongs() = viewModelScope.launch(Dispatchers.IO) {
        hideFolders = userSettingsController.blacklistedFolders.isNotEmpty()

        when {
            userSettingsController.hideLyrics && hideFolders -> {
                _cachedFilteredSongs.value = cachedSongs!!
                    .filter {
                        it.filePath.toLrcFile()?.exists() != true && !userSettingsController.blacklistedFolders.contains(
                            it.filePath!!.substring(
                                0, it.filePath.lastIndexOf("/")
                            )
                        )
                    }
            }

            userSettingsController.hideLyrics -> {
                _cachedFilteredSongs.value = cachedSongs!!
                    .filter { it.filePath.toLrcFile()?.exists() != true }
            }

            hideFolders -> {
                _cachedFilteredSongs.value = cachedSongs!!.filter {
                    !userSettingsController.blacklistedFolders.contains(
                        it.filePath!!.substring(
                            0,
                            it.filePath.lastIndexOf("/")
                        )
                    )
                }
            }

            else -> {
                _cachedFilteredSongs.value = emptyList()
            }
        }

        // Update the active-state independently from the result list so the transition
        // from "show all" to "zero matches" is observable even when both lists are empty.
        _filtersActive.value = hasActiveFilters()
        if (searchQuery.isNotEmpty()) {
            updateSearchResults(searchQuery.lowercaseWithLocale())
        } else {
            _searchResults.value = currentFilterSource()
        }
    }

    fun invertSongSelection() = viewModelScope.launch {
        val newSelectedSongs = displaySongs.filter { it.filePath !in selectedSongs }
        selectedSongs.clear()
        selectedSongs.putAll(newSelectedSongs.mapNotNull { it.filePath }.associateWith { })
    }

    fun selectAllDisplayingSongs() = viewModelScope.launch {
        selectedSongs.clear()
        selectedSongs.putAll(displaySongs.fastMapNotNull { it.filePath }.associateWith { })
    }

    fun onHideLyricsChange(newHideLyrics: Boolean) {
        userSettingsController.updateHideLyrics(newHideLyrics)
    }

    fun onToggleFolderBlacklist(folder: String, blacklisted: Boolean) {
        if (blacklisted) {
            userSettingsController.updateBlacklistedFolders(
                userSettingsController.blacklistedFolders + folder
            )
        } else {
            userSettingsController.updateBlacklistedFolders(
                userSettingsController.blacklistedFolders - folder
            )
        }
    }

    suspend fun getSongInfo(query: SongInfo): SongInfo? =
        lyricsProviderService.getSongInfo(query, provider = userSettingsController.selectedProvider)

    suspend fun getSyncedLyrics(songInfo: SongInfo): String? {
        return try {
            lyricsProviderService.getSyncedLyrics(
                songInfo,
                provider = userSettingsController.selectedProvider,
                includeTranslationNetEase = userSettingsController.includeTranslation,
                includeRomanizationNetEase = userSettingsController.includeRomanization,
                multiPersonWordByWord = userSettingsController.multiPersonWordByWord,
                unsyncedFallbackMusixmatch = userSettingsController.unsyncedFallbackMusixmatch
            )
        } catch (e: Exception) {
            null
        }
    }

    fun selectSong(song: Song, newValue: Boolean) {
        if (newValue) {
            song.filePath?.let { selectedSongs[it] = Unit }
            showSearch = false
            showingSearch = false
        } else {
            selectedSongs.remove(song.filePath)

            if (selectedSongs.size == 0 && searchQuery.isNotEmpty())
                showingSearch = true // show again but don't focus
        }
    }

    fun updatePlayingSongInfo(context: Context) = runCatching {
        val msm = context.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
        val controllers = msm.getActiveSessions(ComponentName(context, NotificationListener::class.java))
        val metadata = controllers[0].metadata
        playingSongTitle = metadata!!.getString(MediaMetadata.METADATA_KEY_TITLE) ?: context.getString(R.string.unknown)
        playingSongArtist = metadata.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: context.getString(R.string.unknown)
        playingSongFilePath =  try {
            allSongs!!.filter {
                (it.title == playingSongTitle || (it.title == null && playingSongTitle == context.getString(R.string.unknown)))
                &&
                (it.artist == playingSongArtist || (it.artist == null && playingSongArtist == context.getString(R.string.unknown)))
            }[0].filePath!! + ".nowplaying" // shared transition uses path as key, add to avoid breaking stuff
        } catch (e: IndexOutOfBoundsException) {
            ""
        }
        playingSongAlbumArt = try {
            metadata.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)!!.let {
                // Unique transient snapshots avoid stale image reuse without invalidating Coil's disk cache.
                val nowPlayingArtDir = File(context.cacheDir, "now_playing_art")
                if (!nowPlayingArtDir.exists() && !nowPlayingArtDir.mkdirs()) {
                    error("Unable to create now-playing artwork cache directory")
                }
                val file = File(nowPlayingArtDir, "${UUID.randomUUID()}.jpg")
                file.outputStream().use { out ->
                    check(it.compress(Bitmap.CompressFormat.JPEG, 100, out)) {
                        "Unable to write now-playing artwork snapshot"
                    }
                }
                Uri.parse(file.absolutePath)
            }
        } catch (e: NullPointerException) {
            null
        }
    }.onFailure {
        playingSongTitle = ""
        playingSongArtist = ""
        playingSongFilePath = ""
        playingSongAlbumArt = null
    }

    companion object {
        private const val SEARCH_DEBOUNCE_MS = 250L
    }
}