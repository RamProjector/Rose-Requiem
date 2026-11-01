package com.roserequiem.app.util

import com.kyant.taglib.Picture
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioMetadataRepositoryTest {
    @Test
    fun buildEditedPropertyMapPreservesUntouchedTagsAndUpdatesMultipleValues() {
        val existing = hashMapOf(
            "TITLE" to arrayOf("Old title"),
            "ARTIST" to arrayOf("Primary", "Guest"),
            "CUSTOM FIELD" to arrayOf("Keep me"),
        )

        val result = buildEditedPropertyMap(
            existing,
            mapOf("TITLE" to "New title", "GENRE" to "Rock\nAlternative"),
        )

        assertArrayEquals(arrayOf("New title"), result["TITLE"])
        assertArrayEquals(arrayOf("Primary", "Guest"), result["ARTIST"])
        assertArrayEquals(arrayOf("Keep me"), result["CUSTOM FIELD"])
        assertArrayEquals(arrayOf("Rock", "Alternative"), result["GENRE"])
    }

    @Test
    fun buildEditedPropertyMapClearsEmptyFieldsAndPreservesMultilineLyrics() {
        val existing = hashMapOf(
            "TITLE" to arrayOf("Old title"),
            "LYRICS" to arrayOf("old lyrics"),
        )

        val result = buildEditedPropertyMap(
            existing,
            mapOf("TITLE" to "  ", "LYRICS" to "[00:01.00]one\n[00:02.00]two"),
        )

        assertTrue("TITLE" !in result)
        assertArrayEquals(arrayOf("[00:01.00]one\n[00:02.00]two"), result["LYRICS"])
    }

    @Test
    fun replaceFrontCoverKeepsBackCoverAndOtherPictures() {
        val oldFront = picture("Front Cover", 1)
        val back = picture("Back Cover", 2)
        val artist = picture("Band", 3)
        val newFront = picture("Front Cover", 4)

        val result = editFrontCover(arrayOf(oldFront, back, artist), FrontCoverEdit.Replace(newFront))

        assertEquals(listOf(newFront, back, artist), result.toList())
    }

    @Test
    fun removeFrontCoverPreservesOtherEmbeddedPictures() {
        val front = picture("Front Cover", 1)
        val back = picture("Back Cover", 2)

        val result = editFrontCover(arrayOf(front, back), FrontCoverEdit.Remove)

        assertEquals(listOf(back), result.toList())
    }

    @Test
    fun removeFallbackCoverRemovesOnlyTheFirstPicture() {
        val untyped = picture("Other", 1)
        val back = picture("Back Cover", 2)

        val result = editFrontCover(arrayOf(untyped, back), FrontCoverEdit.Remove)

        assertEquals(listOf(back), result.toList())
    }

    private fun picture(type: String, byte: Int) = Picture(
        data = byteArrayOf(byte.toByte()),
        description = type,
        pictureType = type,
        mimeType = "image/jpeg",
    )
}
