package com.roserequiem.app.ui.screens.home.components

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.roserequiem.app.services.BatchDownloadService
import com.roserequiem.app.ui.screens.home.HomeViewModel
import com.roserequiem.app.ui.screens.home.components.batchDownload.BatchDownloadWarningDialog
import com.roserequiem.app.ui.screens.home.components.batchDownload.DownloadCompleteDialog
import com.roserequiem.app.ui.screens.home.components.batchDownload.LegacyPromptDialog
import com.roserequiem.app.ui.screens.home.components.batchDownload.RateLimitedDialog

@SuppressLint("StringFormatMatches")
@Composable
fun BatchDownloadLyrics(viewModel: HomeViewModel, onDone: () -> Unit) {
    val songs = viewModel.songsToBatchDownload
    val context = LocalContext.current

    // The service is the single source of truth for an in-progress or just-finished
    // download; this composable mirrors it rather than owning the work itself, so
    // progress survives leaving this screen (or the app) and reappears correctly on return.
    val serviceState by BatchDownloadService.state.collectAsState()

    var uiState by rememberSaveable {
        mutableStateOf(if (serviceState != null) UiState.Pending else UiState.Warning)
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        // Start regardless of the result -- the download still runs without the
        // permission, it just won't show a notification while it does.
        BatchDownloadService.start(
            context,
            ArrayList(songs),
            viewModel.userSettingsController.ignoreEmbeddedLyrics
        )
    }

    val startBatchDownload = remember {
        {
            val needsPermission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED

            if (needsPermission) {
                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                BatchDownloadService.start(
                    context,
                    ArrayList(songs),
                    viewModel.userSettingsController.ignoreEmbeddedLyrics
                )
            }
        }
    }

    LaunchedEffect(serviceState?.isRateLimited, serviceState?.isComplete) {
        val state = serviceState ?: return@LaunchedEffect
        if (uiState != UiState.Pending) return@LaunchedEffect

        if (state.isRateLimited) {
            uiState = UiState.RateLimited
        } else if (state.isComplete) {
            uiState = UiState.Done
        }
    }

    when (uiState) {
        UiState.Cancelled -> {
            val stillRunningInBackground = serviceState?.isComplete == false
            if (!stillRunningInBackground) BatchDownloadService.consumeState()
            onDone()
        }

        UiState.Warning -> BatchDownloadWarningDialog(
            songsCount = songs.size,
            onConfirm = {
                uiState = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
                    UiState.LegacyPrompt
                } else {
                    startBatchDownload()
                    UiState.Pending
                }
            },
            onDismiss = { uiState = UiState.Cancelled },
            embedLyrics = viewModel.userSettingsController.embedLyricsIntoFiles,
            onEmbedLyricsChangeRequest = viewModel.userSettingsController::updateEmbedLyrics,
            ignoreEmbeddedLyrics = viewModel.userSettingsController.ignoreEmbeddedLyrics,
            onIgnoreEmbeddedLyricsChangeRequest = viewModel.userSettingsController::updateIgnoreEmbeddedLyrics,
        )

        UiState.LegacyPrompt -> LegacyPromptDialog(
            onConfirm = {
                uiState = UiState.Pending
                startBatchDownload()
            },
            onDismiss = { uiState = UiState.Cancelled }
        )

        UiState.Pending -> {
            // No blocking dialog here anymore -- progress for a running batch now lives
            // on the Downloads tab (live per-song status, badge on the bottom nav) and
            // in the system notification (which now also has a "go to downloads"
            // action), so a modal duplicating the same numbers on top of Home just got
            // in the way. The LaunchedEffect above still watches serviceState while this
            // screen is around, so Done/RateLimited below still show up normally if the
            // user is still on Home when the batch wraps up.
        }

        UiState.Done -> DownloadCompleteDialog(
            successCount = serviceState?.successCount ?: 0,
            noLyricsCount = serviceState?.noLyricsCount ?: 0,
            failedCount = serviceState?.failedCount ?: 0,
            skippedCount = serviceState?.skippedCount ?: 0,
            ignoreEmbeddedLyrics = serviceState?.ignoreEmbeddedLyrics ?: false,
            flaggedSongs = serviceState?.flaggedSongs ?: emptyList(),
            onDismiss = { uiState = UiState.Cancelled },
            onRetryFailed = {
                // Deliberately not reusing startBatchDownload/permissionLauncher above --
                // this fires after the original permission check already happened once,
                // and going through the exact same mechanism again would mean carrying
                // the *right* song list across an async permission-result callback that
                // wasn't built to hold more than one pending batch. Same fallback the
                // original start already relies on: without the notification permission
                // this just runs without a system notification, still fully visible on
                // the Downloads tab.
                val retrySongs = (serviceState?.flaggedSongs ?: emptyList()).map { it.song }
                if (retrySongs.isNotEmpty()) {
                    BatchDownloadService.start(
                        context,
                        ArrayList(retrySongs),
                        viewModel.userSettingsController.ignoreEmbeddedLyrics
                    )
                }
                onDone()
            }
        )

        UiState.RateLimited -> RateLimitedDialog(onDismiss = { uiState = UiState.Cancelled })
    }
}

enum class UiState {
    Warning, LegacyPrompt, Pending, Done, RateLimited, Cancelled
}