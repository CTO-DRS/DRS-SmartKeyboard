/*
 * Copyright (C) 2025 The DRS Smart Keyboard Project
 */

package com.drs.smartkeyboard.benchmark

import android.view.KeyEvent
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * DRS M0.4 — the typing-latency budget benchmark. Frames produced while
 * the IME is open and keys are being tapped are the honest proxy for
 * "does typing feel instant": a 60fps input surface must keep its frame
 * budget (16.7ms p90 — mirrored in DrsPerformanceBudgets and gated by
 * ci/perf_budget_gate.py).
 */
@RunWith(AndroidJUnit4::class)
class KeyTapLatencyBenchmark {

    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @Test
    fun keyTapFrameTiming() = benchmarkRule.measureRepeated(
        packageName = "com.drs.smartkeyboard",
        metrics = listOf(FrameTimingMetric()),
        compilationMode = CompilationMode.Partial(),
        iterations = 10,
        startupMode = StartupMode.WARM,
    ) {
        openImeAndTapKeys()
    }
}

/**
 * The interaction under measurement: focus a text field so the IME
 * surfaces, then emit a burst of key events through the real input
 * pipeline (hardware keycodes reach the IME exactly like soft-key
 * commits do downstream of InputEventDispatcher).
 */
private fun MacrobenchmarkScope.openImeAndTapKeys() {
    startActivityAndWait()
    device.waitForIdle()
    // A burst of taps across the alphabet — long enough to produce a
    // stable frame population, short enough to stay inside the
    // per-iteration time budget.
    repeat(3) {
        for (keyCode in intArrayOf(
            KeyEvent.KEYCODE_A, KeyEvent.KEYCODE_B, KeyEvent.KEYCODE_C,
            KeyEvent.KEYCODE_D, KeyEvent.KEYCODE_E, KeyEvent.KEYCODE_SPACE,
            KeyEvent.KEYCODE_F, KeyEvent.KEYCODE_G, KeyEvent.KEYCODE_H,
        )) {
            device.pressKeyCode(keyCode)
            device.waitForIdle()
        }
    }
}
