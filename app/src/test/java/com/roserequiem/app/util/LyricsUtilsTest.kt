package com.roserequiem.app.util

import org.junit.Assert.assertEquals
import org.junit.Test

class LyricsUtilsTest {
    @Test
    fun extractYear_returnsYearFromIsoDate() {
        assertEquals("2022", extractYear("2022-06-09"))
    }

    @Test
    fun extractYear_acceptsFourDigitYearWithoutDate() {
        assertEquals("1998", extractYear("1998"))
    }

    @Test
    fun extractYear_returnsNullForMissingOrEmptyValue() {
        assertEquals(null, extractYear(null))
        assertEquals(null, extractYear(""))
    }

    @Test
    fun extractYear_rejectsShortOrMalformedYear() {
        assertEquals(null, extractYear("22"))
        assertEquals(null, extractYear("20x2-06-09"))
        assertEquals(null, extractYear("abcd-06-09"))
    }

    @Test
    fun extractYear_doesNotWriteDigitsFromLaterInMalformedDate() {
        assertEquals(null, extractYear("date-2022-06-09"))
    }
    @Test
    fun parseLyrics_convertsFractionalSecondsToMilliseconds() {
        assertEquals(
            listOf("00:01.230" to "hello", "00:02.500" to "world"),
            parseLyrics("[00:01.23]hello\n[00:02.5]world")
        )
    }

    @Test
    fun parseLyrics_expandsMultipleTimestampsForTheSameLine() {
        assertEquals(
            listOf("00:01.230" to "repeated lyric", "00:02.450" to "repeated lyric"),
            parseLyrics("[00:01.23][00:02.45]repeated lyric")
        )
    }

    @Test
    fun parseLyrics_supportsAngleBracketTimestampsAndIgnoresUnsyncedLines() {
        assertEquals(
            listOf("00:03.700" to "angle lyric"),
            parseLyrics("[ti:Title]\nunsynced lyric\n<00:03.7>angle lyric")
        )
    }


    @Test
    fun applyOffsetToLyrics_normalizesHundredthsAndPreservesMilliseconds() {
        assertEquals(
            "[00:02.500]hundredths\n[00:02.734]milliseconds",
            applyOffsetToLyrics("[00:02.30]hundredths\n[00:02.534]milliseconds", 200)
        )
    }

    @Test
    fun applyOffsetToLyrics_carriesAcrossMinuteBoundaryWithoutWrappingLongTracks() {
        assertEquals(
            "[01:00.100]minute boundary\n[60:00.100]long track",
            applyOffsetToLyrics("[00:59.90]minute boundary\n[59:59.900]long track", 200)
        )
    }

    @Test
    fun applyOffsetToLyrics_clampsNegativeTimestampsToZero() {
        assertEquals(
            "[00:00.000]first lyric",
            applyOffsetToLyrics("[00:00.10]first lyric", -200)
        )
    }

    @Test
    fun resolveSdCardRelativePathSegments_supportsSelectingCardRoot() {
        assertEquals(
            listOf("Music", "Albums"),
            resolveSdCardRelativePathSegments(
                sdRoot = "/storage/ABCD-1234",
                outputPath = "/storage/ABCD-1234/Music/Albums/song.lrc",
                treeDocumentId = "ABCD-1234:"
            )
        )
    }

    @Test
    fun resolveSdCardRelativePathSegments_supportsSelectingNestedFolder() {
        assertEquals(
            listOf("Albums"),
            resolveSdCardRelativePathSegments(
                sdRoot = "/storage/ABCD-1234",
                outputPath = "/storage/ABCD-1234/Music/Albums/song.lrc",
                treeDocumentId = "ABCD-1234:Music"
            )
        )
    }

    @Test(expected = IllegalStateException::class)
    fun resolveSdCardRelativePathSegments_rejectsOutputOutsideSelectedFolder() {
        resolveSdCardRelativePathSegments(
            sdRoot = "/storage/ABCD-1234",
            outputPath = "/storage/ABCD-1234/Music/Albums/song.lrc",
            treeDocumentId = "ABCD-1234:Podcasts"
        )
    }

    @Test(expected = IllegalStateException::class)
    fun resolveSdCardRelativePathSegments_rejectsDifferentVolume() {
        resolveSdCardRelativePathSegments(
            sdRoot = "/storage/ABCD-1234",
            outputPath = "/storage/ABCD-1234/Music/song.lrc",
            treeDocumentId = "EFGH-5678:"
        )
    }

    @Test(expected = IllegalStateException::class)
    fun resolveSdCardRelativePathSegments_rejectsPathOutsideStorageRoot() {
        resolveSdCardRelativePathSegments(
            sdRoot = "/storage/ABCD-1234",
            outputPath = "/storage/emulated/0/Music/song.lrc",
            treeDocumentId = "ABCD-1234:"
        )
    }

}
