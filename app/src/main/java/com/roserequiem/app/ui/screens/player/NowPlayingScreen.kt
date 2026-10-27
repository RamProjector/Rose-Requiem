package com.roserequiem.app.ui.screens.player

import android.content.ComponentName
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.roserequiem.app.R
import com.roserequiem.app.services.PlaybackService
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@UnstableApi
@Composable
fun NowPlayingScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var controller by remember { mutableStateOf<MediaController?>(null) }
    var connectionFailed by remember { mutableStateOf(false) }
    var isPlaying by remember { mutableStateOf(false) }
    var currentTitle by remember { mutableStateOf("") }
    var currentArtist by remember { mutableStateOf("") }
    var queue by remember { mutableStateOf<List<MediaItem>>(emptyList()) }
    var currentIndex by remember { mutableStateOf(0) }
    var duration by remember { mutableLongStateOf(0L) }
    var position by remember { mutableLongStateOf(0L) }
    var shuffleEnabled by remember { mutableStateOf(false) }
    var repeatMode by remember { mutableStateOf(Player.REPEAT_MODE_OFF) }

    DisposableEffect(context) {
        val appContext = context.applicationContext
        val future: ListenableFuture<MediaController> = MediaController.Builder(
            appContext,
            SessionToken(appContext, ComponentName(appContext, PlaybackService::class.java))
        ).buildAsync()
        var connectedController: MediaController? = null

        fun refresh(player: Player) {
            isPlaying = player.isPlaying
            currentTitle = player.currentMediaItem?.mediaMetadata?.title?.toString().orEmpty()
            currentArtist = player.currentMediaItem?.mediaMetadata?.artist?.toString().orEmpty()
            queue = (0 until player.mediaItemCount).mapNotNull(player::getMediaItemAt)
            currentIndex = player.currentMediaItemIndex.coerceAtLeast(0)
            duration = player.duration.takeIf { it > 0L } ?: 0L
            position = player.currentPosition.coerceAtLeast(0L)
            shuffleEnabled = player.shuffleModeEnabled
            repeatMode = player.repeatMode
        }

        val listener = object : Player.Listener {
            override fun onEvents(player: Player, events: Player.Events) = refresh(player)
        }

        future.addListener({
            runCatching { future.get() }.onSuccess { connected ->
                connectedController = connected
                controller = connected
                connected.addListener(listener)
                refresh(connected)
            }.onFailure {
                connectionFailed = true
            }
        }, ContextCompat.getMainExecutor(appContext))

        onDispose {
            connectedController?.removeListener(listener)
            MediaController.releaseFuture(future)
        }
    }

    LaunchedEffect(controller) {
        val player = controller ?: return@LaunchedEffect
        while (isActive) {
            position = player.currentPosition.coerceAtLeast(0L)
            duration = player.duration.takeIf { it > 0L } ?: 0L
            delay(500)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.now_playing)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            when {
                controller == null && !connectionFailed -> Column(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) { CircularProgressIndicator() }
                queue.isEmpty() -> Column(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(stringResource(if (connectionFailed) R.string.player_connection_failed else R.string.player_empty_queue),
                        style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
                    Text(stringResource(R.string.player_empty_queue_hint), textAlign = TextAlign.Center)
                }
                else -> {
                    Column(
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(currentTitle.ifBlank { stringResource(R.string.unknown) },
                            style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
                        Text(currentArtist.ifBlank { stringResource(R.string.unknown) },
                            style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center)
                        Slider(
                            value = if (duration > 0) position.coerceAtMost(duration).toFloat() else 0f,
                            onValueChange = { position = it.toLong() },
                            onValueChangeFinished = { controller?.seekTo(position) },
                            valueRange = 0f..(duration.coerceAtLeast(1L).toFloat()),
                            enabled = duration > 0
                        )
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(formatTime(position), style = MaterialTheme.typography.labelSmall)
                            Text(formatTime(duration), style = MaterialTheme.typography.labelSmall)
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(onClick = { controller?.shuffleModeEnabled = !shuffleEnabled }) {
                                Icon(Icons.Default.Shuffle, contentDescription = stringResource(R.string.toggle_shuffle),
                                    tint = if (shuffleEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                            }
                            IconButton(onClick = { controller?.seekToPreviousMediaItem() }, enabled = controller?.hasPreviousMediaItem() == true) {
                                Icon(Icons.Default.SkipPrevious, contentDescription = stringResource(R.string.previous_track))
                            }
                            IconButton(onClick = {
                                val player = controller ?: return@IconButton
                                if (player.isPlaying) player.pause() else player.play()
                            }) {
                                Icon(if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = stringResource(if (isPlaying) R.string.pause_playback else R.string.resume_playback))
                            }
                            IconButton(onClick = { controller?.seekToNextMediaItem() }, enabled = controller?.hasNextMediaItem() == true) {
                                Icon(Icons.Default.SkipNext, contentDescription = stringResource(R.string.next_track))
                            }
                            IconButton(onClick = {
                                val player = controller ?: return@IconButton
                                player.repeatMode = when (player.repeatMode) {
                                    Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                                    Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                                    else -> Player.REPEAT_MODE_OFF
                                }
                            }) {
                                Icon(Icons.Default.Repeat, contentDescription = stringResource(R.string.toggle_repeat),
                                    tint = if (repeatMode == Player.REPEAT_MODE_OFF) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                    Text(stringResource(R.string.playback_queue, queue.size), style = MaterialTheme.typography.titleMedium)
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(queue.indices.toList(), key = { index -> queue[index].mediaId.ifBlank { index.toString() } }) { index ->
                            val item = queue[index]
                            Card(
                                modifier = Modifier.fillMaxWidth().clickable {
                                    controller?.seekToDefaultPosition(index)
                                    controller?.play()
                                }
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(item.mediaMetadata.title?.toString().orEmpty().ifBlank { stringResource(R.string.unknown) })
                                        Text(item.mediaMetadata.artist?.toString().orEmpty(),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    if (index == currentIndex) {
                                        Text(stringResource(R.string.playing), color = MaterialTheme.colorScheme.primary)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun formatTime(milliseconds: Long): String {
    val totalSeconds = milliseconds.coerceAtLeast(0L) / 1000L
    return String.format(Locale.getDefault(), "%d:%02d", totalSeconds / 60, totalSeconds % 60)
}
