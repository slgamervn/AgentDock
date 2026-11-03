package com.slg.agentdock.macrobenchmark

import android.view.KeyEvent
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.MacrobenchmarkRule
import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Measures frame timing while a long draft is typed one key at a time
 * into the Home Compose text field.
 *
 * Run on a physical Android device. Emulator frame timings are not representative
 * of the performance users experience on their phones.
 */
@LargeTest
@RunWith(AndroidJUnit4::class)
class DraftTypingFrameTimingBenchmark {
    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @Test
    fun typeLongDraftOneKeyAtATime() {
        benchmarkRule.measureRepeated(
            packageName = TARGET_PACKAGE,
            metrics = listOf(FrameTimingMetric()),
            iterations = ITERATIONS,
            setupBlock = {
                pressHome()
                startActivityAndWait()

                val field = findDraftField()
                field.click()
                field.text = ""
                device.pressBack()
            },
        ) {
            findDraftField().click()
            LONG_DRAFT.forEach { character ->
                typeCharacter(character)
            }
        }
    }

    private fun MacrobenchmarkScope.findDraftField(): UiObject2 =
        checkNotNull(
            device.wait(Until.findObject(By.res(DRAFT_FIELD_RESOURCE_ID)), UI_TIMEOUT_MS),
        ) {
            "Home draft input was not found. Check benchmark test-tag semantics."
        }

    private fun MacrobenchmarkScope.typeCharacter(character: Char) {
        val keyCode = when (character) {
            ' ' -> KeyEvent.KEYCODE_SPACE
            in 'a'..'z' -> KeyEvent.KEYCODE_A + (character - 'a')
            else -> error("Unsupported benchmark character: $character")
        }
        check(device.pressKeyCode(keyCode)) {
            "Could not inject key event for character: $character"
        }
    }

    private companion object {
        const val TARGET_PACKAGE = "com.slg.agentdock"
        const val DRAFT_FIELD_RESOURCE_ID = "home.taskInput"
        const val UI_TIMEOUT_MS = 5_000L
        const val ITERATIONS = 5
        val LONG_DRAFT = (
            "review project structure identify performance risks prepare concise plan " +
                "validate changes summarize results preserve current behavior and keep " +
                "the editor responsive while text is entered "
            ).repeat(3)
    }
}
