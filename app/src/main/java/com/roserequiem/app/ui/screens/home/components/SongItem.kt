package com.roserequiem.app.ui.screens.home.components

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.asImage
import coil3.compose.rememberAsyncImagePainter
import coil3.imageLoader
import coil3.request.ImageRequest
import com.roserequiem.app.R
import com.roserequiem.app.domain.model.Song
import com.roserequiem.app.ui.components.AnimatedText


@OptIn(ExperimentalFoundationApi::class, ExperimentalSharedTransitionApi::class)
@Composable
fun SongItem(
    filePath: String,
    selected: Boolean,
    quickSelect: Boolean,
    onSelectionChanged: (Boolean) -> Unit,
    onNavigateToSongRequest: () -> Unit,
    song: Song,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    disableMarquee: Boolean = true,
    showPath: Boolean,
    isNavigatingItem: Boolean,
) {
    val context = LocalContext.current
    val placeholderImage = remember { context.getDrawable(R.drawable.ic_song)?.asImage() }
    val painter = rememberAsyncImagePainter(
        ImageRequest.Builder(context).data(data = song.imgUri).apply {
            placeholder(placeholderImage)
            error(placeholderImage)
            size(240, 240)
        }.build(), imageLoader = context.imageLoader
    )
    val songName = song.title ?: stringResource(id = R.string.unknown)
    val artists = song.artist ?: stringResource(id = R.string.unknown)
    val bgColor = if (selected) MaterialTheme.colorScheme.surfaceVariant
    else MaterialTheme.colorScheme.surface
    val haptics = LocalHapticFeedback.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(if (showPath) 100.dp else 80.dp)
            .background(bgColor)
            .combinedClickable(
                onClick = {
                    if (quickSelect)
                        onSelectionChanged(!selected)
                    else
                        onNavigateToSongRequest()
                },
                onLongClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onSelectionChanged(!selected)
                }
            )
            .padding(vertical = 12.dp, horizontal = 24.dp)
    ) {
        with(sharedTransitionScope) {
            Column {
                Row(
                    modifier = Modifier.fillMaxHeight(if (showPath) 0.7f else 1f),
                ) {
                    Image(
                        painter = painter,
                        contentDescription = stringResource(id = R.string.album_cover),
                        modifier = Modifier
                            .then(
                                // Only the tapped row wires up sharedBounds; every other
                                // visible row gets a plain Modifier so scrolling doesn't pay
                                // per-frame shared-element tracking on rows that never transition.
                                if (isNavigatingItem) Modifier.sharedBounds(
                                    sharedContentState = rememberSharedContentState(key = "cover$filePath"),
                                    animatedVisibilityScope = animatedVisibilityScope,
                                    clipInOverlayDuringTransition = sharedTransitionScope.OverlayClip(
                                        RoundedCornerShape(20f)
                                    )
                                ) else Modifier
                            )
                            .fillMaxHeight()
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(20f))
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(
                        modifier = Modifier.fillMaxHeight(),
                        verticalArrangement = Arrangement.SpaceAround
                    ) {
                        AnimatedText(
                            animate = !disableMarquee,
                            text = songName,
                            fontSize = 16.sp,
                            color = MaterialTheme.colorScheme.contentColorFor(bgColor),
                            modifier = if (isNavigatingItem) Modifier.sharedBounds(
                                sharedContentState = rememberSharedContentState(key = "title$filePath"),
                                animatedVisibilityScope = animatedVisibilityScope
                            ) else Modifier
                        )
                        AnimatedText(
                            animate = !disableMarquee,
                            text = artists,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.contentColorFor(bgColor),
                            modifier = if (isNavigatingItem) Modifier.sharedBounds(
                                sharedContentState = rememberSharedContentState(key = "artist$filePath"),
                                animatedVisibilityScope = animatedVisibilityScope
                            ) else Modifier
                        )
                    }
                }
                if (showPath) {
                    Spacer(modifier = Modifier.height(4.dp))
                    AnimatedText(
                        animate = !disableMarquee,
                        text = filePath.replace(".nowplaying", ""),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.contentColorFor(bgColor),
                        // No sharedBounds here: there is no matching "path$..." key on the
                        // destination (SongCard.kt only shares cover/title/artist), so this
                        // tracker was pure per-frame overhead with no transition to show for it.
                    )
                }
            }
        }
    }
}