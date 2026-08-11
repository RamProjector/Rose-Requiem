package com.roserequiem.app.data.remote.lyrics_providers

import android.util.Log
import com.roserequiem.app.data.remote.lyrics_providers.apple.AppleAPI
import com.roserequiem.app.data.remote.lyrics_providers.others.LRCLibAPI
import com.roserequiem.app.data.remote.lyrics_providers.others.MusixmatchAPI
import com.roserequiem.app.data.remote.lyrics_providers.others.NeteaseAPI
import com.roserequiem.app.data.remote.lyrics_providers.others.QQMusicAPI
import com.roserequiem.app.data.remote.lyrics_providers.spotify.SpotifyAPI
import com.roserequiem.app.data.remote.lyrics_providers.spotify.SpotifyLyricsAPI
import com.roserequiem.app.domain.model.SongInfo
import com.roserequiem.app.util.EmptyQueryException
import com.roserequiem.app.util.InternalErrorException
import com.roserequiem.app.util.NoTrackFoundException
import com.roserequiem.app.util.Providers
import java.io.FileNotFoundException
import java.net.UnknownHostException

/**
 * Service class for interacting with different lyrics providers.
 */
class LyricsProviderService {
    // Spotify API token
    private val spotifyAPI = SpotifyAPI()

    // Apple API
    private val appleAPI = AppleAPI()

    /**
     * Refreshes the access token by sending a request to the Spotify API.
     */
    suspend fun refreshSpotifyToken() = kotlin.runCatching {
        spotifyAPI.refreshToken()
    }

    /**
     * Gets song information from the Spotify API.
     * @param query The SongInfo object with songName and artistName fields filled.
     * @param offset (optional) The offset used for trying to find a better match or searching again.
     * @return The SongInfo object containing the song information.
     */
    @Throws(
        UnknownHostException::class,
        FileNotFoundException::class,
        NoTrackFoundException::class,
        EmptyQueryException::class,
        InternalErrorException::class
    )
    suspend fun getSongInfo(query: SongInfo, offset: Int = 0, provider: Providers): SongInfo? {
        return try {
            when (provider) {
                Providers.SPOTIFY -> spotifyAPI.getSongInfo(query, offset)
                    ?: throw NoTrackFoundException()

                Providers.LRCLIB -> LRCLibAPI().getSongInfo(query, offset)
                    ?: throw NoTrackFoundException()

                Providers.NETEASE -> NeteaseAPI().getSongInfo(query, offset)
                    ?: throw NoTrackFoundException()

                Providers.QQMUSIC -> QQMusicAPI().getSongInfo(query, offset)
                    ?: throw NoTrackFoundException()

                Providers.APPLE -> appleAPI.getSongInfo(query, offset)
                    ?: throw NoTrackFoundException()

                Providers.MUSIXMATCH -> MusixmatchAPI().getSongInfo(query, offset)
                    ?: throw NoTrackFoundException()
            }
        } catch (e: Exception) {
            when (e) {
                is InternalErrorException, is NoTrackFoundException, is EmptyQueryException -> throw e
                else -> throw InternalErrorException(Log.getStackTraceString(e))
            }
        }
    }

    /**
     * Gets synced lyrics using the identifiers carried on [songInfo] (the object returned by
     * [getSongInfo] for this same song) and returns them as a string formatted as an LRC file.
     *
     * [songInfo] is taken as an explicit parameter rather than read from a field set by a
     * previous [getSongInfo] call, so that looking up multiple songs concurrently (e.g. a
     * parallel batch download) can't have one song's identifier overwritten by another's
     * before it's used.
     *
     * @param songInfo The SongInfo previously returned by [getSongInfo] for this song.
     */
    suspend fun getSyncedLyrics(
        songInfo: SongInfo,
        provider: Providers,
        // TODO providers could be a sealed interface to include such parameters
        includeTranslationNetEase: Boolean = false,
        includeRomanizationNetEase: Boolean = false,
        multiPersonWordByWord: Boolean = false,
        unsyncedFallbackMusixmatch: Boolean = true
    ): String? {
        return when (provider) {
            Providers.SPOTIFY -> SpotifyLyricsAPI().getSyncedLyrics(songInfo.songLink ?: "")
            Providers.LRCLIB -> LRCLibAPI().getSyncedLyrics(songInfo.lrcLibID ?: 0)
            Providers.NETEASE -> NeteaseAPI().getSyncedLyrics(
                songInfo.neteaseID ?: 0L, includeTranslationNetEase, includeRomanizationNetEase
            )

            Providers.QQMUSIC -> QQMusicAPI().getSyncedLyrics(songInfo.qqPayload ?: "", multiPersonWordByWord)

            Providers.APPLE -> appleAPI.getSyncedLyrics(
                songInfo.appleID ?: 0L, multiPersonWordByWord
            )

            Providers.MUSIXMATCH -> MusixmatchAPI().getLyrics(
                songInfo,
                unsyncedFallbackMusixmatch
            )
        }
    }

    suspend fun getLyricsInLanguage(songId: Long, language: String): String? {
        return MusixmatchAPI().getLyricsInLanguage(songId, language)
    }
}
