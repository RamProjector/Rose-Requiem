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
}
