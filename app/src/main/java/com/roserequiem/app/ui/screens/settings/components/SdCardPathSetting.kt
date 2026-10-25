package com.roserequiem.app.ui.screens.settings.components

import android.content.Intent
import android.net.Uri
import android.os.Environment
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.roserequiem.app.R


@Composable
fun SdCardPathSetting(sdPath: String?, onClearPath: () -> Unit, onUpdatePath: (String) -> Unit) {
    val context = LocalContext.current
    var picker by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier.padding(horizontal = 22.dp, vertical = 16.dp)
    ) {
        Text(stringResource(R.string.set_sd_path))
        Text(
            text = if (sdPath.isNullOrEmpty()) stringResource(R.string.no_sd_card_path_set)
            else stringResource(R.string.sd_card_path_set_successfully),
            color = if (sdPath.isNullOrEmpty()) MaterialTheme.colorScheme.error
            else MaterialTheme.colorScheme.tertiary,
            fontSize = 12.sp
        )
        Spacer(modifier = Modifier.height(8.dp))

        Row {
            Spacer(modifier = Modifier.weight(1f))
            OutlinedButton(onClick = onClearPath) {
                Text(stringResource(R.string.clear_sd_card_path))
            }
            Spacer(modifier = Modifier.width(12.dp))
            Button(onClick = { picker = true }) {
                Text(stringResource(R.string.set_sd_card_path))
            }
        }

        if (picker) {
            val sdCardPicker =
                rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
                    uri?.let {
                        // Keep SAF access after process death and device reboot. The saved URI
                        // is reused by the legacy SD-card lyrics writer, so a temporary grant
                        // would leave a configured path that no longer has write permission.
                        runCatching {
                            context.contentResolver.takePersistableUriPermission(
                                it,
                                Intent.FLAG_GRANT_READ_URI_PERMISSION or
                                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                            )
                        }.onSuccess {
                            onUpdatePath(uri.toString())
                        }
                    }
                    picker = false
                }
            LaunchedEffect(Unit) {
                sdCardPicker.launch(Uri.parse(Environment.getExternalStorageDirectory().absolutePath))
            }
        }
    }
}