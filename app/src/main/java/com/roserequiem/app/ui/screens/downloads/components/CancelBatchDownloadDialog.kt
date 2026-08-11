package com.roserequiem.app.ui.screens.downloads.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.roserequiem.app.R

/**
 * Guards the Downloads screen's "Cancel" action -- it kills the *whole* active batch
 * (see [com.roserequiem.app.ui.screens.downloads.DownloadsViewModel.cancelActiveSelected]),
 * and anything mid-flight ends up FAILED rather than resumable, so it's worth a beat
 * before committing to it.
 */
@Composable
fun CancelBatchDownloadDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.cancel_batch_download_title)) },
        text = { Text(stringResource(R.string.cancel_batch_download_message)) },
        confirmButton = {
            Button(onClick = onConfirm) {
                Text(stringResource(R.string.cancel))
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text(stringResource(R.string.keep_downloading))
            }
        },
    )
}
