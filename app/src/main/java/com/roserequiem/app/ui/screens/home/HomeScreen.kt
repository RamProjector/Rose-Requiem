package com.roserequiem.app.ui.screens.home

import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.Crossfade
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.layout.LazyLayoutCacheWindow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavHostController
import com.roserequiem.app.R
import com.roserequiem.app.domain.model.Song
import com.roserequiem.app.ui.LyricsFetchScreen
import com.roserequiem.app.ui.MusicCategoriesRoute
import com.roserequiem.app.ui.NowPlayingRoute
import com.roserequiem.app.ui.ScreenSettings
import com.roserequiem.app.ui.screens.home.components.BatchDownloadLyrics
import com.roserequiem.app.ui.screens.home.components.FilterAndSongCount
import com.roserequiem.app.ui.screens.home.components.FiltersDialog
import com.roserequiem.app.ui.screens.home.components.HomeAppBar
import com.roserequiem.app.ui.screens.home.components.HomeSearchBar
import com.roserequiem.app.ui.screens.home.components.HomeSearchThing
import com.roserequiem.app.ui.screens.home.components.SongItem
import com.roserequiem.app.ui.screens.home.components.SortDialog
import com.roserequiem.app.services.LocalPlaybackController
import com.roserequiem.app.util.TrackJankState
import com.roserequiem.app.util.ext.BackPressHandler
import com.roserequiem.app.util.ext.lowercaseWithLocale
import com.roserequiem.app.util.ui.SearchFABBoundsTransform

/**
 * Composable function representing the home screen.
 *
 * @param viewModel The [HomeViewModel] instance.
 */
@UnstableApi
@OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalSharedTransitionApi::class,
)
@Composable
fun HomeScreen(
    navController: NavHostController,
    viewModel: HomeViewModel = viewModel(),
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    var isBatchDownload by remember { mutableStateOf(false) }
    val context = LocalContext.current

    LaunchedEffect(viewModel.userSettingsController.sortBy to viewModel.userSettingsController.sortOrder) {
        viewModel.cachedSongs = null
        viewModel.updateAllSongs(context, viewModel.userSettingsController.sortBy, viewModel.userSettingsController.sortOrder)
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            var cachedSize by remember { mutableIntStateOf(1) }
            if (viewModel.selectedSongs.size > 0) {
                // keep displaying "1 selected" during fade-out, don't say "0 selected"
                cachedSize = viewModel.selectedSongs.size
            }

            BackPressHandler(
                enabled = viewModel.selectedSongs.size > 0,
                onBackPressed = { viewModel.selectedSongs.clear() }
            )

            Crossfade(
                targetState = viewModel.selectedSongs.size > 0,
                label = ""
            ) { showing ->
                HomeAppBar(
                    showing = showing,
                    scrollBehavior = scrollBehavior,
                    onSelectedClearAction = viewModel.selectedSongs::clear,
                    onNavigateToSettingsSectionRequest = { navController.navigate(ScreenSettings) },
                    onProviderSelectRequest = viewModel.userSettingsController::updateSelectedProviders,
                    onBatchDownloadRequest = { isBatchDownload = true },
                    onCategorizeMusicRequest = { navController.navigate(MusicCategoriesRoute) },
                    onOpenPlayerRequest = { navController.navigate(NowPlayingRoute) },
                    selectedProvider = viewModel.userSettingsController.selectedProvider,
                    onSelectAllSongsRequest = viewModel::selectAllDisplayingSongs,
                    onInvertSongSelectionRequest = viewModel::invertSongSelection,
                    cachedSize = cachedSize
                )
            }
        },
        floatingActionButton = {
            with(sharedTransitionScope) {
                FloatingActionButton(
                    modifier = Modifier
                        .skipToLookaheadSize()
                        .sharedBounds(
                            sharedContentState = rememberSharedContentState(key = "fab"),
                            animatedVisibilityScope = animatedVisibilityScope,
                            boundsTransform = SearchFABBoundsTransform,
                            resizeMode = SharedTransitionScope.ResizeMode.RemeasureToBounds,
                        ),
                    onClick = { navController.navigate(LyricsFetchScreen()) }
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search lyrics"
                    )
                }
            }
        },
        bottomBar = { Spacer(Modifier.navigationBarsPadding()) } // fixing broken edge to edge here
    ) { paddingValues ->
        Crossfade(viewModel.allSongs == null, label = "") { loading ->
            if (loading)
                LoadingScreen()
            else
                HomeScreenLoaded(
                    navController = navController,
                    viewModel = viewModel,
                    selected = viewModel.selectedSongs,
                    scaffoldPadding = paddingValues,
                    isBatchDownload = isBatchDownload,
                    onBatchDownloadState = { onBatchDownload -> isBatchDownload = onBatchDownload },
                    sharedTransitionScope = sharedTransitionScope,
                    animatedVisibilityScope = animatedVisibilityScope
                )
        }
    }
}

/**
 * Composable function representing the loading screen.
 */
@Composable
fun LoadingScreen() {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CircularProgressIndicator()
    }
}

@OptIn(
    ExperimentalSharedTransitionApi::class,
    ExperimentalMaterial3Api::class,
    ExperimentalFoundationApi::class,
)
@UnstableApi
@Composable
fun HomeScreenLoaded(
    selected: SnapshotStateMap<String, Unit>,
    navController: NavHostController,
    viewModel: HomeViewModel,
    scaffoldPadding: PaddingValues,
    isBatchDownload: Boolean,
    onBatchDownloadState: (isBatchDownload: Boolean) -> Unit,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
) {
    val context = LocalContext.current

    // §4.3: replaces manual pull-to-refresh, removing one of the two nested-scroll
    // participants that intercepted every scroll delta (this one, and Scaffold's
    // collapsing-TopAppBar connection above HomeScreenLoaded's caller). A
    // ContentObserver is a passive registration -- the system calls onChange() only
    // when MediaStore's audio table actually changes (a file added/removed/rescanned),
    // so nothing here runs per-frame or per-scroll.
    DisposableEffect(Unit) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                viewModel.cachedSongs = null
                viewModel.updateAllSongs(
                    context,
                    viewModel.userSettingsController.sortBy,
                    viewModel.userSettingsController.sortOrder
                )
            }
        }
        context.contentResolver.registerContentObserver(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            true, // notifyForDescendants: catch inserts/deletes/updates anywhere
            // under the audio table, not just this exact URI.
            observer
        )
        onDispose { context.contentResolver.unregisterContentObserver(observer) }
    }
    // Optimization plan §4.2: default prefetch is a conservative one item ahead of
    // scroll direction. Widening the ahead window gives AlbumArtFetcher's decode (4.1)
    // more lead time to finish before a row actually scrolls into view; behind is kept
    // smaller since it's about retention on backtrack, not decode lead time. 200dp/100dp
    // (~3/~1.5 of the 64dp row height) is a reasonable starting point, not a measured
    // optimum -- tune against the plan's own FrameTimingMetric benchmark (4.4) rather
    // than take these numbers as final.
    val listState = rememberLazyListState(
        cacheWindow = LazyLayoutCacheWindow(ahead = 200.dp, behind = 100.dp),
    )
    TrackJankState("screen", "home")
    TrackJankState("home_scrolling", listState.isScrollInProgress.toString())
    // Only the row currently being navigated away from needs its sharedBounds trackers
    // active; every other visible row gets a plain Modifier so scrolling isn't paying
    // for shared-element bookkeeping on rows that will never transition. Declared here,
    // not in HomeScreen above, because this is the function that actually renders the
    // list -- HomeScreen calls this one as a plain function, which doesn't close over
    // HomeScreen's locals the way a lambda would.
    var navigatingFilePath by remember { mutableStateOf<String?>(null) }

    if (isBatchDownload) {
        BatchDownloadLyrics(
            viewModel = viewModel,
            onDone = { onBatchDownloadState(false) })
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Top,
        horizontalAlignment = Alignment.CenterHorizontally,
        contentPadding = scaffoldPadding
    ) {
        item {
            Column(
                modifier = Modifier.padding(
                    top = 5.dp,
                    bottom = 5.dp,
                    start = 22.dp,
                    end = 4.dp
                ),
            ) {
                HomeSearchThing(
                    showingSearch = viewModel.showingSearch,
                    searchBar = {
                        HomeSearchBar(
                            query = viewModel.searchQuery,
                            onQueryChange = { newQuery ->
                                viewModel.searchQuery = newQuery
                                viewModel.updateSearchResults(newQuery.lowercaseWithLocale())
                                viewModel.showingSearch = true
                            },
                            showSearch = viewModel.showSearch,
                            onShowSearchChange = { viewModel.showSearch = it },
                            showingSearch = viewModel.showingSearch,
                            onShowingSearchChange = { viewModel.showingSearch = it }
                        )
                    },
                    filterBar = {
                        FilterAndSongCount(
                            displaySongsCount = viewModel.displaySongs.size,
                            onFilterClick = { viewModel.showFilters = true },
                            onSortClick = { viewModel.showSort = true },
                            onSearchClick = {
                                viewModel.showSearch = true
                                viewModel.showingSearch = true
                            }
                        )
                    }
                )

                if (viewModel.showSort) {
                    SortDialog(
                        userSettingsController = viewModel.userSettingsController,
                        onDismiss = { viewModel.showSort = false },
                        onSortOrderChange = {
                            viewModel.userSettingsController.updateSortOrder(
                                it
                            )
                        },
                        onSortByChange = { viewModel.userSettingsController.updateSortBy(it) }
                    )
                }

                if (viewModel.showFilters) {
                    FiltersDialog(
                        hideLyrics = viewModel.userSettingsController.hideLyrics,
                        folders = viewModel.getSongFolders(context),
                        blacklistedFolders = viewModel.userSettingsController.blacklistedFolders,
                        onDismiss = { viewModel.showFilters = false },
                        onFilterChange = { viewModel.filterSongs() },
                        onHideLyricsChange = viewModel::onHideLyricsChange,
                        onToggleFolderBlacklist = viewModel::onToggleFolderBlacklist
                    )
                }
            }
        }

        item {
            if (viewModel.playingSongTitle.isNotEmpty()) {
                Text(stringResource(id = R.string.now_playing_song))
                SongItem(
                    filePath = viewModel.playingSongFilePath,
                    selected = false,
                    quickSelect = false,
                    onSelectionChanged = {},
                    onNavigateToSongRequest = {
                        navigatingFilePath = viewModel.playingSongFilePath
                        navController.navigate(
                            LyricsFetchScreen(
                                songName = viewModel.playingSongTitle,
                                artists = viewModel.playingSongArtist,
                                coverUri = viewModel.playingSongAlbumArt.toString(),
                                filePath = viewModel.playingSongFilePath
                            )
                        )
                    },
                    song = Song(
                        title = viewModel.playingSongTitle,
                        artist = viewModel.playingSongArtist,
                        imgUri = viewModel.playingSongAlbumArt,
                        filePath = viewModel.playingSongFilePath
                    ),
                    sharedTransitionScope = sharedTransitionScope,
                    animatedVisibilityScope = animatedVisibilityScope,
                    disableMarquee = viewModel.userSettingsController.disableMarquee,
                    showPath = viewModel.userSettingsController.showPath,
                    isNavigatingItem = navigatingFilePath == viewModel.playingSongFilePath
                )
                Spacer(modifier = Modifier.height(4.dp))
                HorizontalDivider()
            }
        }

        items(
            count = viewModel.displaySongs.size,
            key = { index -> viewModel.displaySongs[index].filePath ?: index },
            contentType = { "song_item" }
        ) { index ->
            val song = viewModel.displaySongs[index]

            // Both callbacks keyed on `song` (an @Immutable data class, so this is a
            // real structural-equality check, not an identity check): the closure is
            // only rebuilt when this song's own data changes, not on every recomposition
            // of this row. Without this, SongItem got two fresh lambda allocations per
            // row on every recomposition -- small, but it's every visible row, every time.
            val onSelectionChanged = remember(song) {
                { newValue: Boolean -> viewModel.selectSong(song, newValue) }
            }
            val onNavigateToSongRequest = remember(song) {
                {
                    navigatingFilePath = song.filePath
                    navController.navigate(
                        LyricsFetchScreen(
                            songName = song.title ?: error("song.title was null"),
                            artists = song.artist ?: "",
                            coverUri = song.imgUri.toString(),
                            filePath = song.filePath
                        )
                    )
                }
            }

            SongItem(
                filePath = song.filePath
                    ?: error("a song in the list of files did not have a file path"),
                // containsKey on a SnapshotStateMap gives per-key read observation --
                // only this row is invalidated when this specific key is added/removed,
                // instead of every visible row recomposing on any selection change.
                selected = selected.containsKey(song.filePath),
                quickSelect = selected.size > 0,
                onSelectionChanged = onSelectionChanged,
                onNavigateToSongRequest = onNavigateToSongRequest,
                song = song,
                sharedTransitionScope = sharedTransitionScope,
                animatedVisibilityScope = animatedVisibilityScope,
                disableMarquee = viewModel.userSettingsController.disableMarquee,
                showPath = viewModel.userSettingsController.showPath,
                isNavigatingItem = navigatingFilePath == song.filePath,
                onPlayRequest = {
                    LocalPlaybackController.playQueue(
                        context = context,
                        songs = viewModel.displaySongs,
                        selectedSong = song
                    )
                }
            )
        }

        item { Spacer(modifier = Modifier.height(4.dp)) }
    }
}