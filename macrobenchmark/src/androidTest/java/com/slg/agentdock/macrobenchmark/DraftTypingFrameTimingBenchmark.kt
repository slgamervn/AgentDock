package com.slg.agentdock.macrobenchmark

import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.MacrobenchmarkRule
import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.benchmark.macro.StartupMode
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Measures frame timing while a long draft is entered in the Home Compose text field.
 *
 * Run on a physical Android device. Emulator frame timings are not representative
 * of the performance users experience on their phones.
 */
@RunWith(AndroidJUnit4::class)
class DraftTypingFrameTimingBenchmark {
    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @Test
    fun typeLongDraftInHomeEditor() = benchmarkRule.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics = listOf(FrameTimingMetric()),
        iterations = ITERATIONS,
        startupMode = StartupMode.WARM,
        setupBlock = {
            pressHome()
            startActivityAndWait()

            val field = findDraftField()
            field.click()
            field.setText("")
            device.pressBack()
        },
    ) {
        val field = findDraftField()
        field.click()
        field.setText(LONG_DRAFT)
    }

    private fun MacrobenchmarkScope.findDraftField(): UiObject2 =
        checkNotNull(
            device.wait(Until.findObject(By.res(DRAFT_FIELD_RESOURCE_ID)), UI_TIMEOUT_MS),
        ) {
            "Home draft input was not found. Check benchmark test-tag semantics."
        }

    private companion object {
        const val TARGET_PACKAGE = "com.slg.agentdock"
        const val DRAFT_FIELD_RESOURCE_ID = "home.taskInput"
        const val UI_TIMEOUT_MS = 5_000L
        const val ITERATIONS = 5
        val LONG_DRAFT =
            "Review the project structure, identify the main implementation risks, " +
                "prepare a concise plan, validate the changes, and summarize the test results. " +
                "Preserve existing behavior and keep the workspace responsive while editing. "
                    .repeat(4)
    }
}
