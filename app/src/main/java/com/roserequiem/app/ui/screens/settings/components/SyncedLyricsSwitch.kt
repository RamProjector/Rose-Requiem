package com.roserequiem.app.ui.screens.settings.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.roserequiem.app.R
import com.roserequiem.app.ui.components.SwitchItem

@Composable
fun SyncedLyricsSwitch(selected: Boolean, onToggle: (Boolean) -> Unit) {
    SwitchItem(
        label = stringResource(id = R.string.synced_lyrics),
        description = stringResource(id = R.string.synced_lyrics_summary),
        selected = selected,
        onClick = { onToggle(!selected) }
    )
}