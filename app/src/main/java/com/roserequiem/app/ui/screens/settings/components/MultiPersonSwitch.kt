package com.roserequiem.app.ui.screens.settings.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.roserequiem.app.R
import com.roserequiem.app.ui.components.SwitchItem

@Composable
fun MultiPersonSwitch(selected: Boolean, onToggle: (Boolean) -> Unit) {
    SwitchItem(
        label = stringResource(id = R.string.multi_person_word_by_word),
        description = stringResource(id = R.string.multi_person_word_by_word_summary2),
        selected = selected,
        onClick = { onToggle(!selected) }
    )
}