package com.roserequiem.app.activities.quicksearch

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalBottomSheetDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.ImageLoader
import coil3.bitmapFactoryMaxParallelism
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.allowHardware
import coil3.request.crossfade
import kotlinx.coroutines.Dispatchers
import okhttp3.OkHttpClient
import okio.Path.Companion.toOkioPath
import com.roserequiem.app.R
import com.roserequiem.app.activities.quicksearch.viewmodel.QuickLyricsSearchViewModel
import com.roserequiem.app.activities.quicksearch.viewmodel.QuickLyricsSearchViewModelFactory
import com.roserequiem.app.data.UserSettingsController
import com.roserequiem.app.data.remote.lyrics_providers.LyricsProviderService
import com.roserequiem.app.ui.theme.RoseRequiemTheme
import com.roserequiem.app.util.dataStore

class QuickLyricsSearchActivity : AppCompatActivity() {
    private val lyricsProviderService = LyricsProviderService()


    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val userSettingsController = UserSettingsController(dataStore)
        val viewModel: QuickLyricsSearchViewModel by viewModels {
            QuickLyricsSearchViewModelFactory(userSettingsController, lyricsProviderService)
        }
        activityImageLoader = ImageLoader.Builder(this)
            .components {
                add(OkHttpNetworkFetcherFactory(callFactory = { OkHttpClient() }))
            }
            .memoryCache {
                MemoryCache.Builder()
                    .maxSizePercent(this, 0.35)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(this.cacheDir.resolve("image_cache").toOkioPath())
                    .maxSizeBytes(7 * 1024 * 1024)
                    .build()
            }
            .allowHardware(true)
            .crossfade(true)
            .bitmapFactoryMaxParallelism(12)
            .coroutineContext(Dispatchers.IO)
            .build()

        enableEdgeToEdge()
        handleShareIntent(intent, sendEvent = viewModel::onEvent)

        setContent {
            val sheetState = rememberModalBottomSheetState()
            val viewModelState = viewModel.state.collectAsStateWithLifecycle()
            RoseRequiemTheme(
                pureBlack = userSettingsController.pureBlack,
                dynamicColor = userSettingsController.useDynamicColor
            ) {
                ModalBottomSheet(
                    sheetState = sheetState,
                    properties = ModalBottomSheetDefaults.properties,
                    onDismissRequest = { finish() }
                ) {
                    QuickLyricsSearchPage(
                        state = viewModelState,
                        onSendLyrics = { lyrics ->
                            val resultIntent = Intent().apply {
                                action = Intent.ACTION_SEND
                                putExtra("lyrics", lyrics)
                                type = "text/plain"
                            }
                            setResult(RESULT_OK, resultIntent)
                            finish()
                        }
                    )
                }
            }
        }
    }


    private fun handleShareIntent(
        intent: Intent,
        sendEvent: (QuickLyricsSearchViewModel.Event) -> Unit
    ) {
        when (intent.action) {
            Intent.ACTION_SEND -> {
                val songName =
                    intent.getStringExtra("songName")
                val artistName = intent.getStringExtra("artistName")
                    ?: "" // Artist name is optional. This may be misleading sometimes.

                if (songName.isNullOrBlank()) {
                    Toast.makeText(
                        this,
                        this.getString(R.string.song_name_not_provided),
                        Toast.LENGTH_SHORT
                    ).show()
                    finish()
                    return
                }

                sendEvent(
                    QuickLyricsSearchViewModel.Event.Fetch(
                        song = songName to artistName,
                        context = this
                    )
                )
            }
        }
    }

    companion object {
        lateinit var activityImageLoader: ImageLoader
        lateinit var userSettingsController: UserSettingsController
    }
}