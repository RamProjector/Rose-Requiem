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

}
