package com.roserequiem.app

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Environment
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.view.ViewCompat
import androidx.navigation.compose.rememberNavController
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.roserequiem.app.data.UserSettingsController
import com.roserequiem.app.data.remote.lyrics_providers.LyricsProviderService
import com.roserequiem.app.ui.Navigator
import com.roserequiem.app.ui.ScreenDownloads
import com.roserequiem.app.ui.components.dialogs.NoInternetDialog
import com.roserequiem.app.ui.theme.RoseRequiemTheme
import com.roserequiem.app.util.dataStore
import java.io.File

/**
 * The main activity of the Rose Requiem app.
 */
class MainActivity : ComponentActivity() {
    private val lyricsProviderService = LyricsProviderService()

    // Bumped (never just set to true/false) every time an ACTION_VIEW_DOWNLOADS intent
    // arrives, whether that's the intent this Activity was created with or one
    // delivered later via onNewIntent -- a plain Boolean wouldn't re-trigger the
    // LaunchedEffect below on a second tap of the notification while already on the
    // Downloads screen, since Compose only reacts to an actual value change.
    private var navigateToDownloadsRequest by mutableIntStateOf(0)

    @SuppressLint("SuspiciousIndentation")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // fixes weird system bars background upon app loading
        enableEdgeToEdge()

        ViewCompat.setOnApplyWindowInsetsListener(window.decorView) { view, insets ->
            view.setPadding(0, 0, 0, 0)
            insets
        }

        val dataStore = this.dataStore
        val userSettingsController = UserSettingsController(dataStore)
        checkOrCreateDownloadSubFolder()
        createNotificationChannel()
        handleViewDownloadsIntent(intent)

        setContent {
            val navController = rememberNavController()
            var networkError by rememberSaveable { mutableStateOf<Boolean?>(null) }
            val context = LocalContext.current

            LaunchedEffect(Unit) {
                context.cacheDir.deleteRecursively()
                if (networkError == null) lyricsProviderService
                    .refreshSpotifyToken()
                    .onFailure { networkError = true }
            }

            // Reacts to the "Go to downloads" notification action/tap, whether it
            // launched this Activity fresh or arrived via onNewIntent while already
            // running -- navigates straight to the Downloads screen either way.
            LaunchedEffect(navigateToDownloadsRequest) {
                if (navigateToDownloadsRequest > 0) {
                    navController.navigate(ScreenDownloads) { launchSingleTop = true }
                }
            }

            RoseRequiemTheme(
                pureBlack = userSettingsController.pureBlack,
                dynamicColor = userSettingsController.useDynamicColor
            ) {
                if (networkError == true) NoInternetDialog(
                    onConfirm = ::finishAndRemoveTask,
                    onIgnore = { networkError = false }
                )

                // check in case user revoked permissions later
                if (userSettingsController.passedInit)
                    CheckForPermissions(
                        userSettingsController = userSettingsController
                    )

                Surface(modifier = Modifier.fillMaxSize()) {
                    Navigator(
                        navController = navController,
                        userSettingsController = userSettingsController,
                        lyricsProviderService = lyricsProviderService
                    )
                }
            }
        }
    }

    // MainActivity is launchMode="singleTop" (see the manifest), so tapping the
    // notification while the app is already running redelivers the intent here
    // instead of creating a new instance / going through onCreate again.
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleViewDownloadsIntent(intent)
    }

    private fun handleViewDownloadsIntent(intent: Intent?) {
        if (intent?.action == ACTION_VIEW_DOWNLOADS) {
            navigateToDownloadsRequest++
        }
    }

    override fun onResume() {
        val notificationManager =
            getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(2) // "Done" notification
        super.onResume()
    }

    companion object {
        /** Sent by [com.roserequiem.app.services.BatchDownloadService]'s notifications. */
        const val ACTION_VIEW_DOWNLOADS = "com.roserequiem.app.action.VIEW_DOWNLOADS"
    }
}

@Composable
@OptIn(ExperimentalPermissionsApi::class)
private fun MainActivity.CheckForPermissions(
    userSettingsController: UserSettingsController
) {
    if (Build.VERSION.SDK_INT > Build.VERSION_CODES.R) {
        if (!Environment.isExternalStorageManager()) {
            userSettingsController.updatePassedInit(false)
        }
    } else {
        val permissions = rememberMultiplePermissionsState(
            permissions = listOf(
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
                Manifest.permission.READ_EXTERNAL_STORAGE
            )
        )
        if (!permissions.allPermissionsGranted) {
            userSettingsController.updatePassedInit(false)
        }
    }
}

private fun checkOrCreateDownloadSubFolder() {
    val downloadsDir = Environment.getExternalStoragePublicDirectory(
        Environment.DIRECTORY_DOWNLOADS
    )

    val roseRequiemDir = File(downloadsDir, "Rose Requiem")

    if (!roseRequiemDir.exists()) roseRequiemDir.mkdir()
}

private fun Activity.createNotificationChannel() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val notificationManager =
            getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val channelId = getString(R.string.batch_download_lyrics)
        val channelName = getString(R.string.batch_download_lyrics)
        val channelDescription = getString(R.string.batch_download_lyrics)
        val importance = NotificationManager.IMPORTANCE_LOW
        val channel = NotificationChannel(channelId, channelName, importance)
        channel.description = channelDescription

        notificationManager.createNotificationChannel(channel)
    }
}
