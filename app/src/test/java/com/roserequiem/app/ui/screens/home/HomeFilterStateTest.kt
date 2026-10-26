package com.roserequiem.app.ui.screens.home

import com.roserequiem.app.domain.model.Song
import org.junit.Assert.assertEquals
import org.junit.Test

class HomeFilterStateTest {
    private val songA = Song(title = "Alpha", artist = "Artist A", imgUri = null, filePath = "/music/alpha.mp3")
    private val songB = Song(title = "Beta", artist = "Artist B", imgUri = null, filePath = "/music/beta.mp3")

    @Test
    fun noActiveFiltersShowsEntireLibrary() {
        assertEquals(
            listOf(songA, songB),
            resolveHomeDisplaySongs(
                allSongs = listOf(songA, songB),
                filteredSongs = emptyList(),
                searchResults = emptyList(),
                searchActive = false,
                filtersActive = false,
            ),
        )
    }

    @Test
    fun activeFiltersWithNoMatchesShowsEmptyResultInsteadOfEntireLibrary() {
        assertEquals(
            emptyList<Song>(),
            resolveHomeDisplaySongs(
                allSongs = listOf(songA, songB),
                filteredSongs = emptyList(),
                searchResults = emptyList(),
                searchActive = false,
                filtersActive = true,
            ),
        )
    }

    @Test
    fun activeFiltersWithMatchesShowsOnlyFilteredSongs() {
        assertEquals(
            listOf(songB),
            resolveHomeDisplaySongs(
                allSongs = listOf(songA, songB),
                filteredSongs = listOf(songB),
                searchResults = emptyList(),
                searchActive = false,
                filtersActive = true,
            ),
        )
    }

    @Test
    fun activeSearchUsesSearchResultsEvenWhenTheyAreEmpty() {
        assertEquals(
            emptyList<Song>(),
            resolveHomeDisplaySongs(
                allSongs = listOf(songA, songB),
                filteredSongs = listOf(songB),
                searchResults = emptyList(),
                searchActive = true,
                filtersActive = true,
            ),
        )
    }
}
