package com.roserequiem.app.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView
import androidx.metrics.performance.PerformanceMetricsState

/**
 * Tags the current frame's JankStats state (see [com.roserequiem.app.MainActivity], which is
 * where the actual [androidx.metrics.performance.JankStats] instance is created and its jank
 * reports logged) with a key/value pair for as long as this composable stays in composition.
 *
 * This is what turns a jank log line from "some frame took 40ms" into "some frame took 40ms
 * while [key]=[value]" -- e.g. call `TrackJankState("screen", "home")` once near the top of a
 * screen, or `TrackJankState("home_scrolling", listState.isScrollInProgress.toString())` to
 * specifically correlate jank with active scrolling rather than just being on that screen.
 *
 * Safe to call even if no JankStats has been created yet for this view hierarchy (e.g. in a
 * Preview, or before MainActivity.onCreate has run) -- [PerformanceMetricsState.Holder.state]
 * is null in that case, and this just does nothing rather than crashing.
 */
@Composable
fun TrackJankState(key: String, value: String) {
    val view = LocalView.current
    DisposableEffect(key, value) {
        val holder = PerformanceMetricsState.getHolderForHierarchy(view)
        holder.state?.putState(key, value)
        onDispose { holder.state?.removeState(key) }
    }
}
