# Changelog

All notable changes to this project are documented here.

The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and version numbers follow [Semantic Versioning](https://semver.org/) as
loosely as an app (rather than a library) reasonably can.

Versions before 4.4.0 predate this file — there's no reliable record of
what changed in them, so they aren't reconstructed here.

## [4.4.0]

### Added
- Release year is now captured from Apple and Musixmatch (both already
  parsed it and discarded it) and embedded into the file's `DATE` tag
  alongside lyrics, for both the batch download flow and the single-song
  flow. Spotify, LRCLib, Netease, and QQMusic don't return a release date
  today, so year stays empty for lyrics sourced from those.
- Undo: removing a download record, clearing finished downloads, or
  swiping a row away now offers a Snackbar with Undo instead of deleting
  immediately.
- Confirmation dialog before cancelling an active batch download, since it
  marks anything mid-download as failed rather than pausing it.
- Swipe-to-dismiss on individual Downloads rows.
- Haptic feedback on long-press-to-select, on both Home and Downloads.
- Status chips for `NO_LYRICS` and `SKIPPED` are now distinguishable by
  icon, not just color.

### Changed
- Renamed from Shanker to Rose Requiem — package ID, class names, theme
  names, app display name, default export folder, and README all updated
  together. This changes the app's Android package ID, so it installs as
  a separate app from any existing Shanker install rather than updating it.
- Migrated Ktor 2.3.6 → 3.5.1 (skipping 3.5.0, which has a confirmed crash
  on pre-Android-8 devices).
- Migrated Coil 2.7.0 → 3.5.0, including the network-loading split (now
  using the OkHttp fetcher explicitly, since coil-core no longer bundles
  network loading by default).
- Bumped `activity-compose`, Kotlin, AGP, and target/compile SDK to 36.
- Back gesture handling (closing search, clearing a selection) now uses
  `PredictiveBackHandler`, restoring the system's swipe-preview animation
  that a raw `OnBackPressedCallback` was suppressing.
- The adaptive icon's foreground artwork was sized to ~65% of canvas
  instead of the actual 66dp/61.1% safe zone, so stricter launcher masks
  (confirmed: Vivo/OriginOS) were clipping the rose and hilt. Rescaled
  with margin to spare.
- The Settings "Contributors" section is now explicitly labeled "Original
  SongSync Team," since it lists SongSync's contributors, not this fork's.

### Fixed
- A second batch download could start while one was already running,
  corrupting shared progress state and notifications, and could cause
  whichever batch finished first to silently kill the other via the
  service's own shutdown path.
- Build failure on Kotlin 2.2+: the `kotlinOptions { }` DSL was removed
  outright at that version (deprecated since 2.0), and this project's
  `kotlinOptions` blocks predated the Kotlin bump in this same version.
  Migrated to `compilerOptions`.
- Build failure from the Coil 2→3 migration above: `MemoryCache.Builder`,
  `DiskCache.Builder().directory()`, and the image loader's
  `placeholder()`/`error()` setters all changed signature in Coil 3.5.0
  (`Context` moved out of the `MemoryCache.Builder` constructor and into
  `maxSizePercent(context, fraction)`; `directory()` now takes an Okio
  `Path` instead of `java.io.File`; placeholders take a `coil3.Image`
  instead of a raw drawable resource ID), and `respectCacheHeaders()`
  was removed from the loader builder entirely. Updated both app-wide
  and quick-search image loaders, plus the two composables that set
  placeholders, to match.
- Build failure in the quick-search image loader, also left over from the
  Coil 2→3 migration above: `allowHardware(true)`, `crossfade(true)`, and
  `bitmapFactoryMaxParallelism(12)` stopped resolving, since Coil 3.5.0
  ships each as an extension function (`coil3.request.allowHardware`,
  `coil3.request.crossfade`, `coil3.bitmapFactoryMaxParallelism`) rather
  than a member of `ImageLoader.Builder`, so each needs its own import.
  Separately, `dispatcher(Dispatchers.IO)` no longer exists at all --
  Coil renamed it to `coroutineContext` back in `3.0.0-alpha08` -- so
  that call is now `coroutineContext(Dispatchers.IO)`. These surfaced
  one build at a time rather than all together, since Kotlin stops
  reporting unresolved references further down a chained expression
  once an earlier link in the chain is itself unresolved.

## [4.3.3] and earlier
Untracked. This is the version the project was at when this changelog
was introduced.
