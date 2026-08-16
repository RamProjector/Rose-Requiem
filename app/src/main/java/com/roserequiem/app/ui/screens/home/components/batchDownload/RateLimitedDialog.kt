package com.roserequiem.app.ui.screens.home.components.batchDownload

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.roserequiem.app.R
import com.roserequiem.app.util.Providers

@Composable
fun RateLimitedDialog(provider: Providers, onDismiss: () -> Unit) {
    AlertDialog(
        title = {
            Text(text = stringResource(id = R.string.batch_download_lyrics))
        },
        text = {
            Column {
                Text(text = stringResource(R.string.api_rate_limit_reached, provider.displayName))
                Text(text = stringResource(R.string.please_try_again_later))
                Text(text = stringResource(R.string.change_api_strategy_provider, provider.displayName))
            }
        },
        onDismissRequest = onDismiss,
        confirmButton = {
            Button(onClick = onDismiss) {
                Text(text = stringResource(id = R.string.ok))
            }
        }
    )
}