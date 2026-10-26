package com.roserequiem.app.baselineprofile

import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test

/**
 * Optimization plan §4.4: the number to check §4.1-4.3 against, instead of assuming
 * they helped. `FrameTimingMetric` turns a scripted scroll into janky-frame-percentage
 * and P50/P90/P99 frame-duration numbers -- repeatable across runs, and diffable
 * before/after a change, unlike eyeballing the list scroll by hand.
 *
 * Mirrors [BaselineProfileGenerator]'s cold-start-then-fling script (same UiAutomator
 * selectors), so both instrumented tests exercise the same interaction -- but only the
 * fling itself is inside `measureBlock` here; cold start goes in `setupBlock` instead,
 * so app-launch time doesn't get counted as "scroll" jank.
 *
 * HOW TO RUN THIS:
 * On a real device: `./gradlew :baselineprofile:pixel6Api34DebugAndroidTest --tests "*HomeScrollBenchmark*"`
 * (same hardware-virtualization requirement as BaselineProfileGenerator -- see its kdoc
 * if you hit that wall; the managed-device task name follows Gradle's
 * `<deviceName><BuildVariant>AndroidTest` convention, so `./gradlew :baselineprofile:tasks --all`
 * will show the real one if that string doesn't resolve on your AGP version).
 *
 * On an emulator (including CI -- see .github/workflows/scroll-performance.yml, which
 * runs this exact task), MacrobenchmarkRule refuses to run at all unless you add
 * `-Pandroid.testInstrumentationRunnerArguments.androidx.benchmark.suppressErrors=EMULATOR`:
 * an emulator's absolute timings aren't representative of a real device, so the library
 * guards against them by default. Suppressing the guard still gives you numbers that
 * are valid for *comparing one commit against another on the same hardware* -- which is
 * this file's actual purpose -- just don't read the absolute frame-duration numbers as
 * "what a user's phone will see."
 *
 * HOW TO READ THE RESULT: results land in Logcat (filter for "Benchmark") regardless of
 * device -- that's the reliable path. They're also written as JSON somewhere under
 * `baselineprofile/build/outputs/` (exact subpath depends on managed vs. connected
 * device and wasn't something I could confirm without a real run to check against).
 * `frameDurationCpuMs` P50/P90/P99 and the janky-frame percentage are the numbers that
 * matter. Run once before a change, once after -- or, per the plan's "measured
 * independently" guidance for §4.2 and §4.3, once per commit rather than only
 * before/after all three -- and compare. That comparison is the actual answer to
 * whether any of this helped; nothing above this file can substitute for it.
 */
class HomeScrollBenchmark {

    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @Test
    fun scrollHomeList() = benchmarkRule.measureRepeated(
        packageName = "com.roserequiem.app",
        metrics = listOf(FrameTimingMetric()),
        iterations = 5,
        setupBlock = {
            pressHome()
            startActivityAndWait()
            check(device.wait(Until.hasObject(By.scrollable(true)), 5_000)) {
                "Home did not expose a scrollable list within 5 seconds; refusing to record a non-scroll benchmark."
            }
        }
    ) {
        // Fail rather than silently reporting app-idle frames as scroll performance if
        // the selector stops matching after startup or a future UI change.
        val list = requireNotNull(device.findObject(By.scrollable(true))) {
            "Home scroll benchmark could not find the scrollable list."
        }

        // 3 down/up cycles per iteration, not just 1: a single fling is a thin
        // sample to compute P90/P99 out of. This gives each of the 5 iterations more
        // frames to work with, without changing what's actually being exercised.
        repeat(3) {
            list.fling(Direction.DOWN)
            device.waitForIdle()
            list.fling(Direction.UP)
            device.waitForIdle()
        }
    }
}
