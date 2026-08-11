package com.roserequiem.app.activities.quicksearch.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import com.roserequiem.app.R
import com.roserequiem.app.data.UserSettingsController
import com.roserequiem.app.data.remote.lyrics_providers.LyricsProviderService
import com.roserequiem.app.domain.model.SongInfo
import com.roserequiem.app.util.ResourceState
import com.roserequiem.app.util.ScreenState
import com.roserequiem.app.util.ext.getVersion
import com.roserequiem.app.util.parseLyrics

class QuickLyricsSearchViewModel(
    val userSettingsController: UserSettingsController,
    private val lyricsProviderService: LyricsProviderService
) : ViewModel() {
    private val mutableState = MutableStateFlow(QuickSearchViewState())
    val state = mutableState.asStateFlow()

    data class QuickSearchViewState(
        val song: Pair<String, String>? = null, // Pair of song title and artist's name
        val screenState: ScreenState<SongInfo> = ScreenState.Loading,
        val lyricsState: ResourceState<String> = ResourceState.Loading(),
        val parsedLyrics: List<Pair<String, String>> = emptyList()
    )

    private fun fetchSongData(song: Pair<String, String>, context: Context) {
        updateScreenState(ScreenState.Loading)

        viewModelScope.launch(Dispatchers.IO) {
            val songInfoCall = runCatching {
                lyricsProviderService
                    .getSongInfo(
                        query = SongInfo(song.first, song.second),
                        offset = 0,
                        provider = userSettingsController.selectedProvider
                    )
            }

            if (songInfoCall.isSuccess) {
                val result = songInfoCall.getOrNull()

                if (result == null) {
                    updateScreenState(
                        ScreenState.Error(
                            Exception("The song information retrieved is null")
                        )
                    )
                } else {
                    updateScreenState(ScreenState.Success(result))
                    fetchLyrics(result, context)
                }

            } else {
                val exception = songInfoCall.exceptionOrNull()
                updateScreenState(
                    ScreenState.Error(
                        exception ?: Exception("An unknown error has occurred")
                    )
                )
            }
        }
    }

    private fun fetchLyrics(songInfo: SongInfo, context: Context) {
        updateLyricsState(ResourceState.Loading())
        viewModelScope.launch(Dispatchers.IO) {

            val lyricsCall = runCatching {
                getSyncedLyrics(songInfo)
            }

            if (lyricsCall.isSuccess) {
                val syncedLyrics = lyricsCall.getOrNull()

                if (syncedLyrics == null) updateLyricsState(
                    ResourceState.Error("The fetched lyrics content is null.")
                ) else {
                    updateLyricsState(ResourceState.Success(syncedLyrics))
                    parseLyrics(syncedLyrics).let { parsedLyrics ->
                        mutableState.update {
                            it.copy(parsedLyrics = parsedLyrics)
                        }
                    }
                }

            } else {
                val exception = lyricsCall.exceptionOrNull()
                updateLyricsState(
                    ResourceState.Error(
                        exception?.localizedMessage
                            ?: (context.getString(R.string.unknown) + exception?.stackTrace.toString())
                    )
                )
            }
        }
    }

    private suspend fun getSyncedLyrics(songInfo: SongInfo): String? =
        lyricsProviderService.getSyncedLyrics(
            songInfo,
            userSettingsController.selectedProvider,
            userSettingsController.includeTranslation,
            userSettingsController.includeRomanization,
            userSettingsController.multiPersonWordByWord,
            userSettingsController.unsyncedFallbackMusixmatch
        )

    private fun updateScreenState(screenState: ScreenState<SongInfo>) {
        if (screenState != mutableState.value.screenState) {
            mutableState.update {
                it.copy(screenState = screenState)
            }
        }
    }

    private fun updateLyricsState(lyricsState: ResourceState<String>) {
        if (lyricsState != mutableState.value.lyricsState) {
            mutableState.update {
                it.copy(lyricsState = lyricsState)
            }
        }
    }

    fun onEvent(event: Event) {
        when (event) {
            is Event.Fetch -> {
                mutableState.update {
                    it.copy(song = event.song)
                }
                fetchSongData(event.song, event.context)
            }
        }
    }

    interface Event {
        data class Fetch(val song: Pair<String, String>, val context: Context) : Event
    }
}