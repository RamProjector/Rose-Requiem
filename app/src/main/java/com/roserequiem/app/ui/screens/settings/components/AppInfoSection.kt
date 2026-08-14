package com.roserequiem.app.ui.screens.settings.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.roserequiem.app.R

@Composable
fun AppInfoSection(version: String, onCheckForUpdates: () -> Unit) {
    var showFixHistory by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .padding(horizontal = 22.dp, vertical = 16.dp),
    ) {
        Text(stringResource(R.string.what_is_shanker))
        Text(stringResource(R.string.extra_what_is_shanker))
        Text("")
        Text(stringResource(R.string.app_version, version))
        Row {
            Spacer(modifier = Modifier.weight(1f))
            OutlinedButton(
                modifier = Modifier.padding(top = 8.dp, end = 8.dp),
                onClick = { showFixHistory = true }
            ) {
                Text(stringResource(R.string.fix_history))
            }
            Button(
                modifier = Modifier.padding(top = 8.dp),
                onClick = onCheckForUpdates
            ) {
                Text(stringResource(R.string.check_for_updates))
            }
        }
    }

    if (showFixHistory) {
        ChangelogDialog(onDismiss = { showFixHistory = false })
    }
}