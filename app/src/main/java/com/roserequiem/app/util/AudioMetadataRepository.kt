package com.roserequiem.app.util

import android.app.PendingIntent
import android.app.RecoverableSecurityException
import android.content.Context
import android.media.MediaScannerConnection
import android.os.Build
import android.os.ParcelFileDescriptor
import com.kyant.taglib.Picture
import com.kyant.taglib.TagLib
import java.io.File
import java.io.FileNotFoundException

data class AudioMetadataSnapshot(
    val properties: Map<String, List<String>>,
    val frontCover: Picture?,
)

sealed interface FrontCoverEdit {
    data object Keep : FrontCoverEdit
    data object Remove : FrontCoverEdit
    data class Replace(val picture: Picture) : FrontCoverEdit
}

/** Reads and writes audio tags through TagLib without dropping other mapped tags or pictures. */
object AudioMetadataRepository {
    fun load(context: Context, filePath: String): AudioMetadataSnapshot = withDescriptor(
        context = context,
        filePath = filePath,
        mode = "r",
    ) { descriptor ->
        val metadata = TagLib.getMetadata(descriptor.dup().detachFd(), false)
            ?: error("TagLib could not read this audio file.")
        val pictures = TagLib.getPictures(descriptor.dup().detachFd())
        AudioMetadataSnapshot(
            properties = metadata.propertyMap.mapValues { (_, values) -> values.toList() },
            frontCover = pictures.firstOrNull { it.pictureType.equals("Front Cover", ignoreCase = true) }
                ?: pictures.firstOrNull(),
        )
    }

    fun save(
        context: Context,
        filePath: String,
        textEdits: Map<String, String>,
        frontCoverEdit: FrontCoverEdit,
    ): AudioMetadataSnapshot {
        val saved = withDescriptor(context, filePath, "rw") { descriptor ->
            val originalMetadata = TagLib.getMetadata(descriptor.dup().detachFd(), false)
                ?: error("TagLib could not read this audio file.")
            val originalProperties = copyProperties(originalMetadata.propertyMap)
            val editedProperties = buildEditedPropertyMap(originalProperties, textEdits)
            val originalPictures = TagLib.getPictures(descriptor.dup().detachFd())
            val editedPictures = editFrontCover(originalPictures, frontCoverEdit)
            val propertiesChanged = !propertyMapsEqual(originalProperties, editedProperties)
            val picturesChanged = !pictureListsEqual(originalPictures.toList(), editedPictures.toList())

            if (!propertiesChanged && !picturesChanged) {
                return@withDescriptor snapshotFrom(editedProperties, editedPictures)
            }

            var mutationAttempted = false
            try {
                if (propertiesChanged) {
                    mutationAttempted = true
                    check(TagLib.savePropertyMap(descriptor.dup().detachFd(), editedProperties)) {
                        "TagLib could not save the audio tags."
                    }
                }
                if (picturesChanged) {
                    mutationAttempted = true
                    check(TagLib.savePictures(descriptor.dup().detachFd(), editedPictures)) {
                        "TagLib could not save the embedded album art."
                    }
                }

                val savedMetadata = TagLib.getMetadata(descriptor.dup().detachFd(), false)
                    ?: error("TagLib could not verify the saved audio tags.")
                val savedPictures = TagLib.getPictures(descriptor.dup().detachFd())
                check(propertyMapsEqual(editedProperties, savedMetadata.propertyMap)) {
                    "The saved tags did not match the requested values."
                }
                check(pictureListsEqual(editedPictures.toList(), savedPictures.toList())) {
                    "The saved embedded pictures did not match the requested values."
                }
                snapshotFrom(savedMetadata.propertyMap, savedPictures)
            } catch (error: Exception) {
                if (mutationAttempted) {
                    val rollbackSucceeded = runCatching {
                        val picturesRestored = TagLib.savePictures(
                            descriptor.dup().detachFd(), originalPictures
                        )
                        val propertiesRestored = TagLib.savePropertyMap(
                            descriptor.dup().detachFd(), originalProperties
                        )
                        picturesRestored && propertiesRestored
                    }.getOrDefault(false)
                    if (!rollbackSucceeded) {
                        throw IllegalStateException(
                            "Metadata save failed and the original tags could not be fully restored.",
                            error,
                        )
                    }
                }
                throw IllegalStateException(
                    error.message ?: "Metadata could not be saved; the original tags were restored.",
                    error,
                )
            }
        }

        runCatching {
            MediaScannerConnection.scanFile(context, arrayOf(filePath), null, null)
        }
        return saved
    }

    private fun withDescriptor(
        context: Context,
        filePath: String,
        mode: String,
        block: (ParcelFileDescriptor) -> AudioMetadataSnapshot,
    ): AudioMetadataSnapshot {
        val flags = if (mode == "rw") ParcelFileDescriptor.MODE_READ_WRITE else ParcelFileDescriptor.MODE_READ_ONLY
        val directDescriptor = try {
            ParcelFileDescriptor.open(File(filePath), flags)
        } catch (_: FileNotFoundException) {
            null
        } catch (_: SecurityException) {
            null
        }
        val descriptor = directDescriptor ?: try {
            getFileDescriptorFromPath(context, filePath, mode)
        } catch (securityException: SecurityException) {
            val recoverable = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                securityException as? RecoverableSecurityException
            } else null
            if (recoverable != null) {
                throw AudioMetadataPermissionException(recoverable.userAction.actionIntent)
            }
            throw securityException
        } ?: error("This track is no longer available in the media library.")
        return descriptor.use(block)
    }

    private fun snapshotFrom(
        properties: Map<String, Array<String>>,
        pictures: Array<Picture>,
    ) = AudioMetadataSnapshot(
        properties = properties.mapValues { (_, values) -> values.toList() },
        frontCover = pictures.firstOrNull { it.pictureType.equals("Front Cover", ignoreCase = true) }
            ?: pictures.firstOrNull(),
    )
}

class AudioMetadataPermissionException(val permissionRequest: PendingIntent) :
    SecurityException("Android requires permission to edit this audio file.")

internal fun buildEditedPropertyMap(
    existing: Map<String, Array<String>>,
    edits: Map<String, String>,
): HashMap<String, Array<String>> {
    val updated = HashMap<String, Array<String>>().apply { putAll(existing) }
    edits.forEach { (rawKey, rawValue) ->
        val requestedKey = rawKey.trim()
        require(requestedKey.isNotEmpty() && requestedKey.length <= 255 &&
            requestedKey.none { it == '\n' || it == '\r' || it == '\u0000' }) {
            "Tag names must be 1–255 characters and cannot contain line breaks."
        }
        val key = updated.keys.firstOrNull { it.equals(requestedKey, ignoreCase = true) } ?: requestedKey
        if (rawValue.isBlank()) {
            updated.remove(key)
        } else {
            val values = if (key.equals("LYRICS", ignoreCase = true) || key.equals("COMMENT", ignoreCase = true)) {
                arrayOf(rawValue)
            } else {
                rawValue.lines().filter(String::isNotBlank).toTypedArray()
            }
            if (values.isEmpty()) updated.remove(key) else updated[key] = values
        }
    }
    return updated
}

internal fun editFrontCover(
    existing: Array<Picture>,
    edit: FrontCoverEdit,
): Array<Picture> = when (edit) {
    FrontCoverEdit.Keep -> existing.copyOf()
    FrontCoverEdit.Remove -> {
        val explicitFrontCoverExists = existing.any { it.pictureType.equals("Front Cover", ignoreCase = true) }
        if (explicitFrontCoverExists) {
            existing.filterNot { it.pictureType.equals("Front Cover", ignoreCase = true) }.toTypedArray()
        } else {
            existing.drop(1).toTypedArray()
        }
    }
    is FrontCoverEdit.Replace -> existing
        .filterNot { it.pictureType.equals("Front Cover", ignoreCase = true) }
        .let { listOf(edit.picture) + it }
        .toTypedArray()
}

private fun copyProperties(properties: Map<String, Array<String>>) =
    HashMap<String, Array<String>>().apply { properties.forEach { (key, values) -> put(key, values.copyOf()) } }

private fun propertyMapsEqual(left: Map<String, Array<String>>, right: Map<String, Array<String>>): Boolean =
    left.keys == right.keys && left.all { (key, values) -> right[key]?.contentEquals(values) == true }

private fun pictureListsEqual(left: List<Picture>, right: List<Picture>): Boolean =
    left.size == right.size && left.zip(right).all { (first, second) -> first == second }
