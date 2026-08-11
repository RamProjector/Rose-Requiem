@file:Suppress("SpellCheckingInspection")

package com.roserequiem.app.ui.screens.settings

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavController
import com.roserequiem.app.R
import com.roserequiem.app.data.remote.UpdateState
import com.roserequiem.app.data.UserSettingsController
import com.roserequiem.app.ui.components.SettingsHeadLabel
import com.roserequiem.app.ui.screens.settings.components.OffsetModeSwitch
import com.roserequiem.app.ui.screens.settings.components.SettingsScreenTopBar
import com.roserequiem.app.ui.screens.settings.components.AppInfoSection
import com.roserequiem.app.ui.screens.settings.components.ContributorsSection
import com.roserequiem.app.ui.screens.settings.components.CreditsSection
import com.roserequiem.app.ui.screens.settings.components.ExternalLinkSection
import com.roserequiem.app.ui.screens.settings.components.MarqueeSwitch
import com.roserequiem.app.ui.screens.settings.components.MultiPersonSwitch
import com.roserequiem.app.ui.screens.settings.components.AnimationsSwitch
import com.roserequiem.app.ui.screens.settings.components.DynamicColorSwitch
import com.roserequiem.app.ui.screens.settings.components.PureBlackThemeSwitch
import com.roserequiem.app.ui.screens.settings.components.RomanizationSwitch
import com.roserequiem.app.ui.screens.settings.components.SdCardPathSetting
import com.roserequiem.app.ui.screens.settings.components.ShowPathSwitch
import com.roserequiem.app.ui.screens.settings.components.SupportSection
import com.roserequiem.app.ui.screens.settings.components.SyncedLyricsSwitch
import com.roserequiem.app.ui.screens.settings.components.TranslationSection
import com.roserequiem.app.ui.screens.settings.components.TranslationSwitch
import com.roserequiem.app.ui.screens.settings.components.UpdateAvailableDialog
import com.roserequiem.app.util.ext.getVersion

/**
 * Composable function for AboutScreen component.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    userSettingsController: UserSettingsController,
    navController: NavController
) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val version = context.getVersion()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            SettingsScreenTopBar(
                navController = navController,
                scrollBehavior = scrollBehavior
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = paddingValues
        ) {
            item { SettingsHeadLabel(label = stringResource(id = R.string.theme)) }
            item {
                if (isSystemInDarkTheme()) PureBlackThemeSwitch(
                    selected = userSettingsController.pureBlack,
                    onToggle = { userSettingsController.updatePureBlack(it) }
                )
            }
            item {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) DynamicColorSwitch(
                    selected = userSettingsController.useDynamicColor,
                    onToggle = { userSettingsController.updateUseDynamicColor(it) }
                )
            }
            item {
                AnimationsSwitch(
                    selected = userSettingsController.enableAnimations,
                    onToggle = { userSettingsController.updateEnableAnimations(it) }
                )
            }
            item {
                MarqueeSwitch(
                    selected = userSettingsController.disableMarquee,
                    onToggle = { userSettingsController.updateDisableMarquee(it) }
                )
            }
            item {
                ShowPathSwitch(
                    selected = userSettingsController.showPath,
                    onToggle = { userSettingsController.updateShowPath(it) }
                )
            }

            item { SettingsHeadLabel(label = stringResource(id = R.string.provider)) }
            item {
                TranslationSwitch(
                    selected = userSettingsController.includeTranslation,
                    onToggle = { userSettingsController.updateIncludeTranslation(it) }
                )
            }
            item {
                RomanizationSwitch(
                    selected = userSettingsController.includeRomanization,
                    onToggle = { userSettingsController.updateIncludeRomanization(it) }
                )
            }
            item {
                MultiPersonSwitch(
                    selected = userSettingsController.multiPersonWordByWord,
                    onToggle = { userSettingsController.updateMultiPersonWordByWord(it) }
                )
            }
            item {
                SyncedLyricsSwitch(
                    selected = userSettingsController.unsyncedFallbackMusixmatch,
                    onToggle = { userSettingsController.updateUnsyncedFallbackMusixmatch(it) }
                )
            }
            item {
                OffsetModeSwitch(
                    selected = userSettingsController.directlyModifyTimestamps,
                    onToggle = { userSettingsController.updateDirectlyModifyTimestamps(it) }
                )
            }

            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
                item { SettingsHeadLabel(label = stringResource(R.string.sd_card)) }
                item {
                    SdCardPathSetting(
                        sdPath = userSettingsController.sdCardPath,
                        onClearPath = { userSettingsController.updateSdCardPath("") },
                        onUpdatePath = { newPath ->
                            userSettingsController.updateSdCardPath(
                                newPath
                            )
                        }
                    )
                }
            }

            item { SettingsHeadLabel(label = stringResource(R.string.about_shanker)) }
            item {
                AppInfoSection(
                    version = version,
                    onCheckForUpdates = { viewModel.checkForUpdates(context) }
                )
            }

            item { SettingsHeadLabel(label = stringResource(R.string.source_code)) }
            item {
                ExternalLinkSection(
                    url = "https://github.com/Lambada10/SongSync",
                    uriHandler = uriHandler
                )
            }

            item { SettingsHeadLabel(stringResource(R.string.support)) }
            item { SupportSection(uriHandler = uriHandler) }

            item { SettingsHeadLabel(label = stringResource(id = R.string.translation)) }
            item { TranslationSection(uriHandler = uriHandler) }

            item { SettingsHeadLabel(stringResource(R.string.contributors)) }
            item { ContributorsSection(uriHandler = uriHandler) }

            item { SettingsHeadLabel(label = stringResource(id = R.string.thanks_to)) }
            item { CreditsSection(uriHandler = uriHandler) }
        }
    }

    val updateState = viewModel.updateState
    if (updateState is UpdateState.UpdateAvailable) {
        UpdateAvailableDialog(
            onDismiss = viewModel::dismissUpdate,
            onDownloadRequest = { uriHandler.openUri(updateState.release.htmlURL) },
            latestVersion = updateState.release.tagName,
            currentVersion = version,
            changelog = updateState.release.changelog ?: ""
        )
    }
}
