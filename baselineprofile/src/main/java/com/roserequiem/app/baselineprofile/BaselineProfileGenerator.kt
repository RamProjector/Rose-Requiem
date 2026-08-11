package com.roserequiem.app.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test

/**
 * Captures a Baseline Profile for Rose Requiem's single highest-traffic path: cold start
 * into the home screen, then scrolling the song list. That's the screen almost every
 * session touches, so it's the one worth precompiling first.
 *
 * Extend `generate()` with more `device.findObject(...)` interactions (e.g. tapping
 * into a song's lyrics screen, opening the batch download dialog) as those flows
 * stabilize -- every additional real journey you record here gets folded into the
 * profile AGP ships inside app/src/main/baseline-prof.txt.
 *
 * HOW TO RUN THIS:
 * `./gradlew :baselineprofile:generateBaselineProfile`
 *
 * This must run on a device or emulator, not just compile -- it's an instrumented
 * test that plays back these actions and records which class/method paths got hit.
 * `useConnectedDevices = false` in this module's build.gradle.kts means it'll try to
 * launch the "pixel6Api34" Gradle Managed Device automatically, which needs
 * hardware virtualization (a PC/Mac, or CI). Android Code Studio running on-device
 * can't provide that -- if you hit that wall, either flip `useConnectedDevices` to
 * `true` and plug in a second physical device over ADB, or run this task from a
 * desktop machine / CI runner against this same project instead.
 */
class BaselineProfileGenerator {

    @get:Rule
    val baselineProfileRule = BaselineProfileRule()

    @Test
    fun generate() = baselineProfileRule.collect(
        packageName = "com.roserequiem.app",
    ) {
        // Cold start into the home screen (the MediaStore query + LazyColumn
        // covered in HomeViewModel/HomeScreen).
        pressHome()
        startActivityAndWait()

        // Let the song list load, then scroll it -- covers recomposition and
        // Coil's image-loading path for SongItem.
        device.wait(Until.hasObject(By.scrollable(true)), 5_000)
        device.findObject(By.scrollable(true))?.let { list ->
            list.fling(Direction.DOWN)
            device.waitForIdle()
            list.fling(Direction.UP)
            device.waitForIdle()
        }
    }
}
