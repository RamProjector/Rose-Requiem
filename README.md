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

Rose Requiem started from SongSync and has since diverged: a custom visual identity (color palette, typography, icon), the background download service, concurrency and correctness fixes to the lyrics pipeline, and more — see [CHANGELOG.md](CHANGELOG.md) for what's actually changed release to release.

### License

This project is licensed under the GNU General Public License v3.0 - see the [LICENSE](LICENSE) file for details. As a derivative of SongSync, it inherits SongSync's GPL-3.0 licensing and copyright.

### Thanks to

- [Lambada10](https://github.com/Lambada10) for SongSync, the project this fork is built on
- [Spotify](https://developer.spotify.com/documentation/web-api)
- [SpotifyLyricsAPI](https://github.com/akashrchandran/spotify-lyrics-api)
- [syncedlyrics](https://github.com/0x7d4/syncedlyrics)
- [Statusbar Lyric Ext](https://github.com/cjybyjk/StatusBarLyricExt)
- [Alex](https://github.com/paxsenix0) for access to various apis

### Download safety

Authorized direct FLAC downloads are checked locally before they are saved. The scanner validates the FLAC metadata structure, checks the EICAR test signature, and runs YARA-X against a pinned snapshot of [ReversingLabs' MIT-licensed YARA rules](https://github.com/reversinglabs/reversinglabs-yara-rules). Audio files are not uploaded for scanning. The bundled rules update only with reviewed app updates. This signature scan can miss new or obfuscated threats and may produce false positives; a clean result is not a guarantee that a file is safe. If the native engine or rules fail to load, the download is not published.

### Building from source

In addition to JDK 17 and the Android SDK, building the app requires Rust 1.95.0, Android NDK 27.2.12479018, and `cargo-ndk` 4.1.2. The Gradle build compiles the YARA-X native library for `armeabi-v7a`, `arm64-v8a`, `x86`, and `x86_64`. Install the Rust toolchain from `rust-toolchain.toml`, install the pinned NDK through Android SDK Manager, then install `cargo-ndk` with `cargo install cargo-ndk --version 4.1.2 --locked` before running Gradle.
