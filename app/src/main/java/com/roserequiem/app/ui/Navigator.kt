package com.roserequiem.app.ui

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.toRoute
import kotlinx.serialization.Serializable
import com.roserequiem.app.R
import com.roserequiem.app.data.UserSettingsController
import com.roserequiem.app.data.remote.lyrics_providers.LyricsProviderService
import com.roserequiem.app.services.BatchDownloadService
import com.roserequiem.app.ui.common.animatedComposable
import com.roserequiem.app.ui.screens.downloads.DownloadsScreen
import com.roserequiem.app.ui.screens.home.HomeScreen
import com.roserequiem.app.ui.screens.home.HomeViewModel
import com.roserequiem.app.ui.screens.init.InitScreen
import com.roserequiem.app.ui.screens.init.InitScreenViewModel
import com.roserequiem.app.ui.screens.lyricsFetch.LyricsFetchScreen
import com.roserequiem.app.ui.screens.lyricsFetch.LyricsFetchViewModel
import com.roserequiem.app.ui.screens.settings.SettingsScreen
import com.roserequiem.app.ui.screens.settings.SettingsViewModel

/**
 * Composable function for handling navigation within the app.
 *
 * @param navController The navigation controller.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun Navigator(
    navController: NavHostController,
    userSettingsController: UserSettingsController,
    lyricsProviderService: LyricsProviderService
) {
    // Bottom nav only makes sense on the two top-level tabs -- Init/LyricsFetch/Settings
    // are all "on top of" one of those, so the bar disappears while in them rather than
    // implying you're still on a tab.
    val currentEntry by navController.currentBackStackEntryAsState()
    val showBottomBar = currentEntry?.destination?.let {
        it.hasRoute<ScreenHome>() || it.hasRoute<ScreenDownloads>()
    } ?: false

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                RoseRequiemBottomBar(navController = navController, currentEntry = currentEntry)
            }
        }
    ) { innerPadding ->
        SharedTransitionLayout {
            NavHost(
                navController = navController,
                startDestination = if (userSettingsController.passedInit) ScreenHome else InitScreen,
                modifier = Modifier.padding(innerPadding),
            ) {
                animatedComposable<InitScreen> {
                    InitScreen(
                        navController = navController,
                        viewModel = viewModel {
                            InitScreenViewModel(userSettingsController)
                        },
                    )
                }
                animatedComposable<ScreenHome> {
                    HomeScreen(
                        navController = navController,
                        viewModel = viewModel {
                            HomeViewModel(userSettingsController, lyricsProviderService)
                        },
                        sharedTransitionScope = this@SharedTransitionLayout,
                        animatedVisibilityScope = this,
                    )
                }
                animatedComposable<ScreenDownloads> {
                    DownloadsScreen(userSettingsController = userSettingsController)
                }

                animatedComposable<LyricsFetchScreen>() {
                    val args = it.toRoute<LyricsFetchScreen>()

                    LyricsFetchScreen(
                        viewModel = viewModel {
                            LyricsFetchViewModel(
                                args.source(),
                                userSettingsController,
                                lyricsProviderService
                            )
                        },
                        navController = navController,
                        animatedVisibilityScope = this,
                    )
                }
                animatedComposable<ScreenSettings> {
                    SettingsScreen(
                        viewModel = viewModel { SettingsViewModel() },
                        userSettingsController,
                        navController = navController
                    )
                }
            }
        }
    }
}

@Composable
private fun RoseRequiemBottomBar(
    navController: NavHostController,
    currentEntry: NavBackStackEntry?,
) {
    NavigationBar {
        NavigationBarItem(
            selected = currentEntry?.destination?.hasRoute<ScreenHome>() ?: false,
            onClick = {
                navController.navigate(ScreenHome) {
                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                    launchSingleTop = true
                    restoreState = true
                }
            },
            icon = { Icon(Icons.Filled.Home, contentDescription = null) },
            label = { Text(stringResource(id = R.string.home)) },
        )
        val activeBatchState by BatchDownloadService.state.collectAsState()
        val isBatchActive = activeBatchState != null && activeBatchState?.isComplete == false

        NavigationBarItem(
            selected = currentEntry?.destination?.hasRoute<ScreenDownloads>() ?: false,
            onClick = {
                navController.navigate(ScreenDownloads) {
                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                    launchSingleTop = true
                    restoreState = true
                }
            },
            icon = {
                if (isBatchActive) {
                    // A running batch is easy to forget about while sitting on Home --
                    // this dot is just presence, not a count, so it doesn't need to
                    // track exact progress.
                    BadgedBox(badge = { Badge() }) {
                        Icon(Icons.Filled.Download, contentDescription = null)
                    }
                } else {
                    Icon(Icons.Filled.Download, contentDescription = null)
                }
            },
            label = { Text(stringResource(id = R.string.downloads)) },
        )
    }
}

@Serializable
object InitScreen

@Serializable
object ScreenHome

@Serializable
object ScreenDownloads

@Serializable
data class LyricsFetchScreen(
    private val songName: String? = null,
    private val artists: String? = null,
    private val coverUri: String? = null,
    private val filePath: String? = null,
) {
    fun source() = if (songName != null && artists != null && filePath != null) {
        LocalSong(songName, artists, coverUri, filePath)
    } else null
}

@Serializable
data class LocalSong(
    val songName: String,
    val artists: String,
    val coverUri: String?,
    val filePath: String,
)

@Serializable
object ScreenSettings