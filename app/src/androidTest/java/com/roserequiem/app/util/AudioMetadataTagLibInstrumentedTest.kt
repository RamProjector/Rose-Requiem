package com.roserequiem.app.util

import android.os.ParcelFileDescriptor
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.kyant.taglib.Picture
import com.kyant.taglib.TagLib
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class AudioMetadataTagLibInstrumentedTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val testContext = instrumentation.context

    @Test
    fun mp3Id3v2TextTagsCustomPropertyAndEmbeddedArtRoundTrip() {
        val audio = copyAsset("metadata-id3v2.mp3")
        val cover = copyAsset("metadata-cover.jpg").readBytes()
        val backCover = Picture(cover, "Back Cover", "Back Cover", "image/jpeg")
        ParcelFileDescriptor.open(audio, ParcelFileDescriptor.MODE_READ_WRITE).use { descriptor ->
            assertTrue(TagLib.savePictures(descriptor.dup().detachFd(), arrayOf(backCover)))
        }
        val original = AudioMetadataRepository.load(context, audio.absolutePath)

        assertEquals("Rose Test Original", original.properties["TITLE"]?.single())
        assertEquals("Fixture Artist", original.properties["ARTIST"]?.single())
        assertEquals("Fixture Album", original.properties["ALBUM"]?.single())

        val expectedCover = Picture(cover, "Front Cover", "Front Cover", "image/jpeg")
        val saved = AudioMetadataRepository.save(
            context = context,
            filePath = audio.absolutePath,
            textEdits = mapOf(
                "TITLE" to "Rose Test Edited",
                "ROSE_REQUIEM_TEST" to "custom ID3v2 property",
            ),
            frontCoverEdit = FrontCoverEdit.Replace(expectedCover),
        )

        assertEquals("Rose Test Edited", saved.properties["TITLE"]?.single())
        assertEquals("Fixture Artist", saved.properties["ARTIST"]?.single())
        assertEquals("Fixture Album", saved.properties["ALBUM"]?.single())
        assertEquals("custom ID3v2 property", saved.properties["ROSE_REQUIEM_TEST"]?.single())
        assertEquals(expectedCover, saved.frontCover)

        // Re-open the file independently to ensure the change survived the file close/reopen boundary.
        ParcelFileDescriptor.open(audio, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
            val readBack = TagLib.getMetadata(descriptor.dup().detachFd(), false)
            assertNotNull(readBack)
            assertEquals("Rose Test Edited", readBack!!.propertyMap["TITLE"]?.single())
            assertEquals(
                listOf(expectedCover, backCover),
                TagLib.getPictures(descriptor.dup().detachFd()).toList(),
            )
        }
    }

    @Test
    fun flacVorbisCommentsAndEmbeddedArtRoundTrip() {
        val audio = copyAsset("metadata-flac.flac")
        val cover = copyAsset("metadata-cover.jpg").readBytes()
        val original = AudioMetadataRepository.load(context, audio.absolutePath)
        assertEquals("Rose FLAC Original", original.properties["TITLE"]?.single())

        val expectedCover = Picture(cover, "Front Cover", "Front Cover", "image/jpeg")
        val saved = AudioMetadataRepository.save(
            context = context,
            filePath = audio.absolutePath,
            textEdits = mapOf("TITLE" to "Rose FLAC Edited"),
            frontCoverEdit = FrontCoverEdit.Replace(expectedCover),
        )

        assertEquals("Rose FLAC Edited", saved.properties["TITLE"]?.single())
        assertEquals("Fixture Artist", saved.properties["ARTIST"]?.single())
        assertEquals(expectedCover, saved.frontCover)
    }

    private fun copyAsset(name: String): File {
        val file = File(context.cacheDir, name)
        testContext.assets.open(name).use { input ->
            file.outputStream().use { output -> input.copyTo(output) }
        }
        return file
    }
}
