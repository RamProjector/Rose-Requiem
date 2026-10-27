package com.roserequiem.app.ui.screens.audiodownloads

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.roserequiem.app.R
import com.roserequiem.app.data.AuthorizedFlacDownloader
import com.roserequiem.app.data.FlacDownloadResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudioDownloadsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var sourceUrl by remember { mutableStateOf("") }
    var fileName by remember { mutableStateOf("audio.flac") }
    var downloading by remember { mutableStateOf(false) }
    var downloadedBytes by remember { mutableLongStateOf(0L) }
    var totalBytes by remember { mutableStateOf<Long?>(null) }
    var resultMessage by remember { mutableStateOf<String?>(null) }
    var lastSavedLocation by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.audio_downloads)) },
                navigationIcon = {
                    androidx.compose.material3.IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxWidth().padding(padding).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(stringResource(R.string.flac_download_disclaimer), style = androidx.compose.material3.MaterialTheme.typography.bodyMedium)
            OutlinedTextField(
                value = sourceUrl,
                onValueChange = { sourceUrl = it },
                label = { Text(stringResource(R.string.direct_https_url)) },
                supportingText = { Text(stringResource(R.string.direct_https_url_hint)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            OutlinedTextField(
                value = fileName,
                onValueChange = { fileName = it },
                label = { Text(stringResource(R.string.flac_file_name)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Button(
                enabled = !downloading && sourceUrl.isNotBlank() && fileName.isNotBlank(),
                onClick = {
                    downloading = true
                    downloadedBytes = 0L
                    totalBytes = null
                    resultMessage = null
                    lastSavedLocation = null
                    scope.launch {
                        val result = withContext(Dispatchers.IO) {
                            AuthorizedFlacDownloader(context).download(sourceUrl, fileName) { current, total ->
                                downloadedBytes = current
                                totalBytes = total
                            }
                        }
                        downloading = false
                        when (result) {
                            is FlacDownloadResult.Success -> {
                                lastSavedLocation = result.location
                                resultMessage = context.getString(R.string.flac_download_success, result.displayName, result.location)
                            }
                            is FlacDownloadResult.Failure -> resultMessage = result.message
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Download, contentDescription = null)
                Text(stringResource(R.string.download_and_validate_flac), modifier = Modifier.padding(start = 8.dp))
            }
            if (downloading) {
                if (totalBytes != null && totalBytes!! > 0L) {
                    LinearProgressIndicator(
                        progress = { (downloadedBytes.toFloat() / totalBytes!!.toFloat()).coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(stringResource(R.string.flac_download_progress, formatBytes(downloadedBytes), formatBytes(totalBytes!!)))
                } else {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    Text(stringResource(R.string.flac_downloaded_size, formatBytes(downloadedBytes)))
                }
            }
            Text(stringResource(R.string.flac_download_limits), style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
        }
    }

    resultMessage?.let { message ->
        AlertDialog(
            onDismissRequest = { resultMessage = null },
            title = { Text(stringResource(R.string.audio_downloads)) },
            text = { Text(message) },
            confirmButton = { TextButton(onClick = { resultMessage = null }) { Text(stringResource(R.string.ok)) } }
        )
    }
}

private fun formatBytes(value: Long): String {
    val units = arrayOf("B", "KB", "MB", "GB")
    var amount = value.toDouble()
    var index = 0
    while (amount >= 1024 && index < units.lastIndex) {
        amount /= 1024
        index++
    }
    return String.format(Locale.getDefault(), "%.1f %s", amount, units[index])
}
