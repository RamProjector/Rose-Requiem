package com.roserequiem.app.ui.screens.downloads.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Downloading
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.PauseCircle
import androidx.compose.material.icons.filled.RemoveCircleOutline
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.roserequiem.app.R
import com.roserequiem.app.domain.model.DownloadStatus

/**
 * Fixed semantic colors independent of the app's Material theme, so a status reads the
 * same regardless of dynamic color/pure-black settings -- same idea as the outlined
 * status chips in Soulseek clients (green/blue/yellow/red no matter the app's own skin).
 *
 * Each status also carries its own [icon] so it doesn't read by color alone -- NO_LYRICS
 * and SKIPPED share the same gray and were previously distinguishable only by their text
 * label, which is a real problem for anyone scanning quickly or with color-vision
 * deficiency (WCAG 1.4.1: color is not used as the only visual means of conveying info).
 */
private data class StatusStyle(val color: Color, val labelRes: Int, val icon: ImageVector)

private fun statusStyle(status: DownloadStatus): StatusStyle = when (status) {
    DownloadStatus.QUEUED -> StatusStyle(Color(0xFFC9A227), R.string.status_queued, Icons.Filled.Schedule)
    DownloadStatus.DOWNLOADING -> StatusStyle(Color(0xFF4C8DFF), R.string.status_downloading, Icons.Filled.Downloading)
    DownloadStatus.SUCCESS -> StatusStyle(Color(0xFF3FB673), R.string.status_success, Icons.Filled.CheckCircle)
    DownloadStatus.NO_LYRICS -> StatusStyle(Color(0xFF8A8A8A), R.string.status_no_lyrics, Icons.Filled.RemoveCircleOutline)
    DownloadStatus.FAILED -> StatusStyle(Color(0xFFE0524C), R.string.status_failed, Icons.Filled.Error)
    DownloadStatus.SKIPPED -> StatusStyle(Color(0xFF8A8A8A), R.string.status_skipped, Icons.Filled.SkipNext)
    DownloadStatus.PAUSED -> StatusStyle(Color(0xFF9E7FE0), R.string.status_paused, Icons.Filled.PauseCircle)
}

/** Small filled pill showing a record's status, e.g. next to a row in the history list. */
@Composable
fun DownloadStatusChip(status: DownloadStatus, modifier: Modifier = Modifier) {
    val style = statusStyle(status)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .background(
                color = style.color.copy(alpha = 0.14f),
                shape = RoundedCornerShape(6.dp)
            )
            .padding(horizontal = 8.dp, vertical = 3.dp),
    ) {
        Icon(
            imageVector = style.icon,
            contentDescription = null, // decorative -- the Text right after already labels the status for TalkBack
            tint = style.color,
            modifier = Modifier.size(12.dp),
        )
        Text(
            text = stringResource(style.labelRes),
            color = style.color,
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(start = 4.dp),
        )
    }
}

fun statusColor(status: DownloadStatus): Color = statusStyle(status).color

fun statusLabelRes(status: DownloadStatus): Int = statusStyle(status).labelRes
