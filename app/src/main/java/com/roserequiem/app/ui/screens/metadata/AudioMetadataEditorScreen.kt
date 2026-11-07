package com.roserequiem.app.ui.screens.metadata

import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.IntentSenderRequest
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kyant.taglib.Picture
import com.roserequiem.app.R
import com.roserequiem.app.util.AudioMetadataRepository
import com.roserequiem.app.util.AudioMetadataPermissionException
import com.roserequiem.app.util.AudioMetadataSnapshot
import com.roserequiem.app.util.FrontCoverEdit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

private val commonTagKeys = listOf(
    "TITLE", "ARTIST", "ALBUM", "ALBUMARTIST", "DATE", "GENRE", "TRACKNUMBER", "DISCNUMBER",
    "COMPOSER", "COMMENT", "LYRICS", "BPM", "ISRC", "COPYRIGHT", "PUBLISHER", "CONDUCTOR",
    "GROUPING", "SUBTITLE", "LANGUAGE",
)
private val multilineTagKeys = setOf("LYRICS", "COMMENT", "DESCRIPTION")
private const val MAX_COVER_IMAGE_BYTES = 20 * 1024 * 1024

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudioMetadataEditorScreen(
    filePath: String,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val permissionGrantedMessage = stringResource(R.string.metadata_permission_granted_retry)
    val permissionDeniedMessage = stringResource(R.string.metadata_permission_denied)
    val metadataLoadFailedMessage = stringResource(R.string.metadata_load_failed)
    val metadataImageInvalidMessage = stringResource(R.string.metadata_image_invalid)
    val metadataSavedMessage = stringResource(R.string.metadata_saved)
    val metadataSaveFailedMessage = stringResource(R.string.metadata_save_failed)
    val invalidTagMessage = stringResource(R.string.metadata_tag_invalid_or_exists)
    val scope = rememberCoroutineScope()
    var snapshot by remember(filePath) { mutableStateOf<AudioMetadataSnapshot?>(null) }
    var tagKeys by remember(filePath) { mutableStateOf(commonTagKeys) }
    var tagValues by remember(filePath) { mutableStateOf<Map<String, String>>(emptyMap()) }
    var frontCoverEdit by remember(filePath) { mutableStateOf<FrontCoverEdit>(FrontCoverEdit.Keep) }
    var previewBitmap by remember(filePath) { mutableStateOf<Bitmap?>(null) }
    var isLoading by remember(filePath) { mutableStateOf(true) }
    var isSaving by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var showAddTagDialog by remember { mutableStateOf(false) }
    var newTagKey by remember { mutableStateOf("") }
    var newTagValue by remember { mutableStateOf("") }
    var pendingImageError by remember { mutableStateOf<String?>(null) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult(),
    ) { result ->
        message = if (result.resultCode == Activity.RESULT_OK) {
            permissionGrantedMessage
        } else {
            permissionDeniedMessage
        }
    }

    LaunchedEffect(filePath) {
        isLoading = true
        try {
            val loaded = withContext(Dispatchers.IO) { AudioMetadataRepository.load(context, filePath) }
            snapshot = loaded
            val loadedKeys = (commonTagKeys + loaded.properties.keys.sorted()).distinct()
            tagKeys = loadedKeys
            tagValues = loadedKeys.associateWith { key -> loaded.properties[key].orEmpty().joinToString("\n") }
            previewBitmap = loaded.frontCover?.data?.let { bytes ->
                withContext(Dispatchers.IO) { decodeArtworkPreview(bytes) }
            }
        } catch (error: Exception) {
            message = error.message ?: metadataLoadFailedMessage
        } finally {
            isLoading = false
        }
    }

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                try {
                    val selected = withContext(Dispatchers.IO) { readSelectedArtwork(context, uri) }
                    frontCoverEdit = FrontCoverEdit.Replace(selected.picture)
                    previewBitmap = selected.preview
                    pendingImageError = null
                } catch (error: Exception) {
                    pendingImageError = error.message ?: metadataImageInvalidMessage
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.metadata_editor_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
            )
        },
    ) { padding ->
        when {
            isLoading -> Column(
                modifier = Modifier.fillMaxSize().padding(padding),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) { CircularProgressIndicator() }

            snapshot != null -> Column(
                modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = filePath.substringAfterLast('/'),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                )
                Text(stringResource(R.string.metadata_artwork), style = androidx.compose.material3.MaterialTheme.typography.titleMedium)
                previewBitmap?.let { bitmap ->
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = stringResource(R.string.album_cover),
                        modifier = Modifier.size(176.dp).align(Alignment.CenterHorizontally),
                    )
                } ?: Text(stringResource(R.string.metadata_no_cover))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { imagePicker.launch("image/*") }, enabled = !isSaving) {
                        Text(stringResource(R.string.metadata_choose_cover))
                    }
                    TextButton(
                        onClick = {
                            frontCoverEdit = FrontCoverEdit.Remove
                            previewBitmap = null
                        },
                        enabled = !isSaving && (previewBitmap != null || snapshot?.frontCover != null),
                    ) {
                        Text(stringResource(R.string.metadata_remove_cover))
                    }
                }

                Spacer(Modifier.height(4.dp))
                Text(stringResource(R.string.metadata_tags), style = androidx.compose.material3.MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(R.string.metadata_tags_note),
                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                )
                tagKeys.forEach { key ->
                    OutlinedTextField(
                        value = tagValues[key].orEmpty(),
                        onValueChange = { updated -> tagValues = tagValues + (key to updated) },
                        label = { Text(key) },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = if (key.uppercase() in multilineTagKeys) 3 else 1,
                        maxLines = if (key.uppercase() in multilineTagKeys) 8 else 3,
                    )
                }
                OutlinedButton(onClick = { showAddTagDialog = true }, enabled = !isSaving) {
                    Text(stringResource(R.string.metadata_add_tag))
                }
                Button(
                    onClick = {
                        val edits = tagKeys.associateWith { tagValues[it].orEmpty() }
                        isSaving = true
                        scope.launch {
                            try {
                                val updated = withContext(Dispatchers.IO) {
                                    AudioMetadataRepository.save(context, filePath, edits, frontCoverEdit)
                                }
                                snapshot = updated
                                tagKeys = (commonTagKeys + updated.properties.keys.sorted()).distinct()
                                tagValues = tagKeys.associateWith { key -> updated.properties[key].orEmpty().joinToString("\n") }
                                frontCoverEdit = FrontCoverEdit.Keep
                                previewBitmap = updated.frontCover?.data?.let { bytes ->
                                    withContext(Dispatchers.IO) { decodeArtworkPreview(bytes) }
                                }
                                message = metadataSavedMessage
                            } catch (error: Exception) {
                                if (error is AudioMetadataPermissionException) {
                                    permissionLauncher.launch(
                                        IntentSenderRequest.Builder(error.permissionRequest.intentSender).build()
                                    )
                                } else {
                                    message = error.message ?: metadataSaveFailedMessage
                                }
                            } finally {
                                isSaving = false
                            }
                        }
                    },
                    enabled = !isSaving && snapshot != null,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (isSaving) CircularProgressIndicator(modifier = Modifier.size(18.dp))
                    else Text(stringResource(R.string.save))
                }
            }

            else -> Unit
        }
    }

    if (showAddTagDialog) {
        AlertDialog(
            onDismissRequest = { showAddTagDialog = false },
            title = { Text(stringResource(R.string.metadata_add_tag)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = newTagKey,
                        onValueChange = { newTagKey = it },
                        label = { Text(stringResource(R.string.metadata_tag_name)) },
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = newTagValue,
                        onValueChange = { newTagValue = it },
                        label = { Text(stringResource(R.string.metadata_tag_value)) },
                        minLines = 2,
                        maxLines = 5,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val key = newTagKey.trim()
                    if (key.isNotEmpty() && tagKeys.none { it.equals(key, ignoreCase = true) }) {
                        tagKeys = tagKeys + key
                        tagValues = tagValues + (key to newTagValue)
                        newTagKey = ""
                        newTagValue = ""
                        showAddTagDialog = false
                    } else {
                        pendingImageError = invalidTagMessage
                    }
                }) { Text(stringResource(R.string.metadata_add_tag)) }
            },
            dismissButton = {
                TextButton(onClick = { showAddTagDialog = false }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }

    (pendingImageError ?: message)?.let { shownMessage ->
        AlertDialog(
            onDismissRequest = {
                pendingImageError = null
                message = null
            },
            title = { Text(stringResource(R.string.metadata_editor_title)) },
            text = { Text(shownMessage) },
            confirmButton = {
                TextButton(onClick = {
                    pendingImageError = null
                    message = null
                }) { Text(stringResource(R.string.ok)) }
            },
        )
    }
}

private data class LoadedArtwork(val picture: Picture, val preview: Bitmap)

private fun readSelectedArtwork(context: Context, uri: Uri): LoadedArtwork {
    val bytes = ByteArrayOutputStream().use { output ->
        val input = context.contentResolver.openInputStream(uri) ?: error("The selected image could not be opened.")
        input.use { stream ->
            val buffer = ByteArray(8192)
            var total = 0
            while (true) {
                val count = stream.read(buffer)
                if (count < 0) break
                if (count == 0) continue
                total += count
                require(total <= MAX_COVER_IMAGE_BYTES) { "Cover images must be 20 MB or smaller." }
                output.write(buffer, 0, count)
            }
        }
        output.toByteArray()
    }
    val mimeType = when {
        bytes.size >= 3 && bytes[0] == 0xff.toByte() && bytes[1] == 0xd8.toByte() && bytes[2] == 0xff.toByte() -> "image/jpeg"
        bytes.size >= 8 && bytes.take(8).map { it.toInt() and 0xff } == listOf(137, 80, 78, 71, 13, 10, 26, 10) -> "image/png"
        bytes.size >= 12 && bytes.copyOfRange(0, 4).decodeToString() == "RIFF" &&
            bytes.copyOfRange(8, 12).decodeToString() == "WEBP" -> "image/webp"
        else -> error("Choose a valid JPEG, PNG, or WebP image for the cover.")
    }
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    require(bounds.outWidth > 0 && bounds.outHeight > 0 && bounds.outWidth <= 30_000 && bounds.outHeight <= 30_000) {
        "The selected file is not a supported image."
    }
    var sampleSize = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / sampleSize > 512) sampleSize *= 2
    val preview = BitmapFactory.decodeByteArray(
        bytes,
        0,
        bytes.size,
        BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.RGB_565
        },
    ) ?: error("The selected image could not be decoded.")
    return LoadedArtwork(
        picture = Picture(bytes, "Front Cover", "Front Cover", mimeType),
        preview = preview,
    )
}

private fun decodeArtworkPreview(bytes: ByteArray): Bitmap? {
    if (bytes.isEmpty()) return null
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    var sampleSize = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / sampleSize > 512) sampleSize *= 2
    return BitmapFactory.decodeByteArray(
        bytes,
        0,
        bytes.size,
        BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.RGB_565
        },
    )
}
