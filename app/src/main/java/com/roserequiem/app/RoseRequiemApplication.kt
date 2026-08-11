package com.roserequiem.app

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import com.roserequiem.app.data.DownloadHistoryRepository
import okhttp3.OkHttpClient

/**
 * Gives Coil an explicit cache budget instead of relying on its default singleton
 * config. [SongItem][com.roserequiem.app.ui.screens.home.components.SongItem] loads a
 * cover per row on a screen that can scroll through thousands of songs, so an
 * unbounded/default memory cache either over-allocates on high-RAM devices or gets
 * evicted too aggressively on low-RAM ones -- both show up as covers "popping in"
 * on fast scrolls.
 *
 * 25% of available app memory for decoded bitmaps is the commonly recommended
 * starting point for an image-heavy list screen; the disk cache holds decoded,
 * downsampled art so re-scrolling back to a song doesn't re-decode its embedded
 * artwork from scratch.
 *
 * Implements [SingletonImageLoader.Factory] rather than the old (Coil 2.x)
 * `ImageLoaderFactory` -- same role, renamed in Coil 3. One shared [OkHttpClient]
 * registered as the network fetcher, since coil-core no longer bundles network
 * loading by default.
 */
class RoseRequiemApplication : Application(), SingletonImageLoader.Factory {

    private val okHttpClient = OkHttpClient()

    override fun onCreate() {
        super.onCreate()
        DownloadHistoryRepository.init(this)
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader {
        return ImageLoader.Builder(context)
            .components {
                add(OkHttpNetworkFetcherFactory(callFactory = { okHttpClient }))
            }
            .memoryCache {
                MemoryCache.Builder(context)
                    .maxSizePercent(0.25)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("album_art_cache"))
                    .maxSizePercent(0.02)
                    .build()
            }
            .build()
    }
}
