package com.roserequiem.app.ui.screens.home

import org.junit.Assert.assertEquals
import org.junit.Test

class HomeEmptyStateTest {
    @Test
    fun searchFeedbackTakesPrecedenceOverActiveFilters() {
        assertEquals(
            HomeEmptyStateKind.SEARCH,
            resolveHomeEmptyStateKind(searchQuery = "night", filtersActive = true),
        )
    }

    @Test
    fun activeFiltersExplainAnEmptyFilteredLibrary() {
        assertEquals(
            HomeEmptyStateKind.FILTER,
            resolveHomeEmptyStateKind(searchQuery = "", filtersActive = true),
        )
    }

    @Test
    fun emptyUnfilteredLibraryGetsLibraryGuidance() {
        assertEquals(
            HomeEmptyStateKind.LIBRARY,
            resolveHomeEmptyStateKind(searchQuery = "   ", filtersActive = false),
        )
    }
}
