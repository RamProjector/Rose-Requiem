package com.roserequiem.app.ui.screens.settings.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.roserequiem.app.R
import com.roserequiem.app.ui.components.SwitchItem

@Composable
fun DynamicColorSwitch(selected: Boolean, onToggle: (Boolean) -> Unit) {
    SwitchItem(
        label = stringResource(R.string.use_dynamic_color),
        description = stringResource(R.string.use_dynamic_color_summary),
        selected = selected,
        onClick = { onToggle(!selected) }
    )
}
