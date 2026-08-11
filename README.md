# Rose Requiem

A personal fork of [SongSync](https://github.com/Lambada10/SongSync) — an Android app that finds and downloads synced lyrics (`.lrc` files) for your local music library and can embed them directly into your song files.

### Features

- Download lyrics for your whole music library at once — now runs as a background download with a real notification (progress bar + cancel), so it keeps going if you leave the app
- Batch downloads process several songs concurrently instead of one at a time
- Skip songs that already have embedded lyrics during a batch run
- Download lyrics for individual songs, or search for lyrics for songs not in your library
- Embed lyrics directly into the song file, or save as a standalone `.lrc`
- Multiple lyrics providers: Spotify, LRCLib, Apple Music, Musixmatch, QQ Music, Netease

### About this fork

Rose Requiem started from SongSync and has since diverged: a custom visual identity (color palette, typography, icon), the background download service, concurrency and correctness fixes to the lyrics pipeline, and various other changes described in the commit history.

### License

This project is licensed under the GNU General Public License v3.0 - see the [LICENSE](LICENSE) file for details. As a derivative of SongSync, it inherits SongSync's GPL-3.0 licensing and copyright.

### Thanks to

- [Lambada10](https://github.com/Lambada10) for SongSync, the project this fork is built on
- [Spotify](https://developer.spotify.com/documentation/web-api)
- [SpotifyLyricsAPI](https://github.com/akashrchandran/spotify-lyrics-api)
- [syncedlyrics](https://github.com/0x7d4/syncedlyrics)
- [Statusbar Lyric Ext](https://github.com/cjybyjk/StatusBarLyricExt)
- [Alex](https://github.com/paxsenix0) for access to various apis
