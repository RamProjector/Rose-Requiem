package com.roserequiem.app.util.ext

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Handles the system back gesture for same-screen state changes (closing search,
 * clearing a Downloads/Home selection) rather than actual navigation. Built on
 * [PredictiveBackHandler] rather than a raw OnBackPressedCallback so the gesture still
 * gets the system's live swipe-preview animation -- registering a plain callback
 * suppresses that entirely, which is what this used to do.
 *
 * [onBackPressed] only fires once the gesture actually commits (finger lifted past the
 * threshold). Swiping partway and letting go cancels collection of the progress flow
 * with a [CancellationException] and does nothing -- state never changed, matching what
 * the system's own preview already showed the user mid-swipe.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BackPressHandler(
    enabled: Boolean = true,
    onBackPressed: () -> Unit,
) {
    val realEnabled = enabled && !WindowInsets.isImeVisible
    val currentOnBackPressed by rememberUpdatedState(onBackPressed)

    PredictiveBackHandler(enabled = realEnabled) { progress ->
        try {
            progress.collect { /* no custom per-frame animation -- system preview covers it */ }
            currentOnBackPressed()
        } catch (e: CancellationException) {
            // Gesture released before committing -- nothing to do.
            throw e
        }
    }
}

fun Modifier.repeatingClickable(
    interactionSource: MutableInteractionSource,
    enabled: Boolean,
    maxDelayMillis: Long = 1000,
    minDelayMillis: Long = 5,
    delayDecayFactor: Float = .20f,
    onClick: () -> Unit
): Modifier = this.then(
    composed {
        val currentClickListener by rememberUpdatedState(onClick)
        val scope = rememberCoroutineScope()

        pointerInput(interactionSource, enabled) {
            scope.launch {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    // Create a down press interaction
                    val downPress = PressInteraction.Press(down.position)
                    val heldButtonJob = launch {
                        // Send the press through the interaction source
                        interactionSource.emit(downPress)
                        var currentDelayMillis = maxDelayMillis
                        while (enabled && down.pressed) {
                            currentClickListener()
                            delay(currentDelayMillis)
                            val nextMillis = currentDelayMillis - (currentDelayMillis * delayDecayFactor)
                            currentDelayMillis = nextMillis.toLong().coerceAtLeast(minDelayMillis)
                        }
                    }
                    val up = waitForUpOrCancellation()
                    heldButtonJob.cancel()
                    // Determine whether a cancel or release occurred, and create the interaction
                    val releaseOrCancel = when (up) {
                        null -> PressInteraction.Cancel(downPress)
                        else -> PressInteraction.Release(downPress)
                    }
                    launch {
                        // Send the result through the interaction source
                        interactionSource.emit(releaseOrCancel)
                    }
                }
            }
        }
    }
)