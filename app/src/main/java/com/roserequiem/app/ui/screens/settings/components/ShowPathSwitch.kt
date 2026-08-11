package com.roserequiem.app.ui.screens.settings.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.roserequiem.app.R
import com.roserequiem.app.ui.components.SwitchItem

@Composable
fun ShowPathSwitch(selected: Boolean, onToggle: (Boolean) -> Unit) {
    SwitchItem(
        label = stringResource(R.string.song_path),
        description = stringResource(R.string.song_path_description),
        selected = selected,
        onClick = { onToggle(!selected) }
    )
}