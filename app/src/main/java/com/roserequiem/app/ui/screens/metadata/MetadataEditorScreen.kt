package com.roserequiem.app.ui.screens.metadata

import android.content.Context
import android.media.MediaScannerConnection
import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.media3.common.util.UnstableApi
import com.kyant.taglib.TagLib
import com.roserequiem.app.R
import com.roserequiem.app.util.getFileDescriptorFromPath
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private data class EditableTags(
    val title: String = "",
    val artist: String = "",
    val album: String = "",
    val year: String = "",
    val genre: String = "",
    val trackNumber: String = "",
)

@OptIn(ExperimentalMaterial3Api::class)
@UnstableApi
@Composable
fun MetadataEditorScreen(filePath: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var tags by remember(filePath) { mutableStateOf(EditableTags()) }
    var originalTags by remember(filePath) { mutableStateOf(EditableTags()) }
    var loading by remember(filePath) { mutableStateOf(true) }
    var saving by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var confirmSave by remember { mutableStateOf(false) }

    LaunchedEffect(filePath) {
        val result = withContext(Dispatchers.IO) { readTags(context, filePath) }
        result.onSuccess {
            tags = it
            originalTags = it
        }.onFailure {
            message = context.getString(R.string.metadata_read_failed, it.message ?: "")
        }
        loading = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.metadata_editor)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    IconButton(
                        enabled = !loading && !saving && tags != originalTags,
                        onClick = { confirmSave = true }
                    ) {
                        Icon(Icons.Default.Save, contentDescription = stringResource(R.string.save_metadata))
                    }
                }
            )
        }
    ) { padding ->
        if (loading || saving) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(padding).padding(28.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                CircularProgressIndicator()
                Text(stringResource(if (saving) R.string.metadata_saving else R.string.metadata_loading))
            }
        } else {
            Column(
                modifier = Modifier.fillMaxWidth().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(File(filePath).name, style = androidx.compose.material3.MaterialTheme.typography.titleSmall)
                MetadataField(stringResource(R.string.title), tags.title) { tags = tags.copy(title = it) }
                MetadataField(stringResource(R.string.artist), tags.artist) { tags = tags.copy(artist = it) }
                MetadataField(stringResource(R.string.album), tags.album) { tags = tags.copy(album = it) }
                MetadataField(stringResource(R.string.year), tags.year) { tags = tags.copy(year = it.filter(Char::isDigit).take(4)) }
                MetadataField(stringResource(R.string.genre), tags.genre) { tags = tags.copy(genre = it) }
                MetadataField(stringResource(R.string.track_number), tags.trackNumber) { tags = tags.copy(trackNumber = it.filter(Char::isDigit).take(3)) }
                Text(
                    stringResource(R.string.metadata_save_note),
                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall
                )
                Button(
                    enabled = tags != originalTags && !saving,
                    onClick = { confirmSave = true },
                    modifier = Modifier.fillMaxWidth()
                ) { Text(stringResource(R.string.save_metadata)) }
            }
        }
    }

    if (confirmSave) {
        AlertDialog(
            onDismissRequest = { confirmSave = false },
            title = { Text(stringResource(R.string.confirm_metadata_save)) },
            text = { Text(stringResource(R.string.confirm_metadata_save_message)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmSave = false
                    saving = true
                    scope.launch {
                        val result = withContext(Dispatchers.IO) { writeTags(context, filePath, tags) }
                        saving = false
                        result.onSuccess {
                            originalTags = tags
                            message = context.getString(R.string.metadata_saved)
                        }.onFailure {
                            message = context.getString(R.string.metadata_write_failed, it.message ?: "")
                        }
                    }
                }) { Text(stringResource(R.string.save)) }
            },
            dismissButton = { TextButton(onClick = { confirmSave = false }) { Text(stringResource(R.string.cancel)) } }
        )
    }

    message?.let { text ->
        AlertDialog(
            onDismissRequest = { message = null },
            title = { Text(stringResource(R.string.metadata_editor)) },
            text = { Text(text) },
            confirmButton = { TextButton(onClick = { message = null }) { Text(stringResource(R.string.ok)) } }
        )
    }
}

@Composable
private fun MetadataField(label: String, value: String, onValueChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true
    )
}

private fun readTags(context: Context, path: String): Result<EditableTags> = runCatching {
    val descriptor = getFileDescriptorFromPath(context, path, "r")
        ?: error("Audio file could not be opened. Check that it still exists and is accessible.")
    descriptor.use {
        val metadata = TagLib.getMetadata(it.dup().detachFd(), false)
            ?: error("This audio format could not be read by the metadata library.")
        val properties = metadata.propertyMap
        fun first(key: String) = properties[key]?.firstOrNull().orEmpty()
        EditableTags(
            title = first("TITLE"),
            artist = first("ARTIST"),
            album = first("ALBUM"),
            year = first("DATE").take(4),
            genre = first("GENRE"),
            trackNumber = first("TRACKNUMBER").substringBefore('/'),
        )
    }
}

private fun writeTags(context: Context, path: String, tags: EditableTags): Result<Unit> = runCatching {
    val descriptor = getFileDescriptorFromPath(context, path, "w")
        ?: error("Audio file could not be opened for writing. Check file permissions.")
    descriptor.use {
        val metadata = TagLib.getMetadata(it.dup().detachFd(), false)
            ?: error("This audio format could not be read by the metadata library.")
        val properties = metadata.propertyMap
        fun setOrRemove(key: String, value: String) {
            if (value.isBlank()) properties.remove(key) else properties[key] = arrayOf(value.trim())
        }
        setOrRemove("TITLE", tags.title)
        setOrRemove("ARTIST", tags.artist)
        setOrRemove("ALBUM", tags.album)
        setOrRemove("DATE", tags.year)
        setOrRemove("GENRE", tags.genre)
        setOrRemove("TRACKNUMBER", tags.trackNumber)
        val writableDescriptor = getFileDescriptorFromPath(context, path, "w")
            ?: error("Audio file became unavailable before saving.")
        writableDescriptor.use { writable ->
            TagLib.savePropertyMap(writable.dup().detachFd(), propertyMap = properties)
        }
    }
    MediaScannerConnection.scanFile(context, arrayOf(path), null, null)
}

