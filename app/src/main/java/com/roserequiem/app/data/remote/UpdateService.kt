package com.roserequiem.app.data.remote

import android.content.Context
import kotlinx.coroutines.flow.flow
import com.roserequiem.app.data.remote.github.GithubAPI
import com.roserequiem.app.domain.model.Release
import com.roserequiem.app.util.ext.getVersion

class UpdateService {

    /**
     * Checks for updates by comparing the latest release version with the current version.
     *
     * Disabled for this Rose Requiem fork: the check compared against Lambada10/SongSync's
     * GitHub releases, which would misleadingly prompt to "update" to vanilla SongSync
     * (overwriting fork-only features). Repoint [GithubAPI] at your own repo's releases
     * if you publish Rose Requiem separately, then restore the body below.
     *
     * @param context The context of the application.
     * @return A flow emitting the update state.
     */
    fun checkForUpdates(context: Context) = flow {
        emit(UpdateState.Checking)
        emit(UpdateState.UpToDate)
    }

    /**
     * Checks if the latest release is newer than the current version.
     * @param context The context of the application.
     * @param latestRelease The latest release from the GitHub API.
     * @return True if the latest release is newer, false otherwise.
     */
    private fun isNewerRelease(context: Context, latestRelease: Release): Boolean {
        val currentVersion = context
            .getVersion()
            .replace(".", "")
            .toInt()
        val latestVersion = latestRelease.tagName
            .replace(".", "")
            .replace("v", "")
            .toInt()

        return latestVersion > currentVersion
    }
}

/**
 * Defines the state of the update check.
 */
sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data class UpdateAvailable(val release: Release) : UpdateState
    data class Error(val reason: Throwable) : UpdateState
}