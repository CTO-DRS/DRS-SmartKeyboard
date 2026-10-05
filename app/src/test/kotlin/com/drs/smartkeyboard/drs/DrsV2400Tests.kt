/*
 * Copyright (C) 2025-2026 The DRS Smart Keyboard Project
 */

package com.drs.smartkeyboard.drs

import com.drs.smartkeyboard.ime.input.InputShiftState
import com.drs.smartkeyboard.ime.keyboard.shiftStateA11yRes
import com.drs.smartkeyboard.ime.text.key.KeyCode
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

/**
 * DRS v2.4.0 «اللوحة الحية الشاملة» — the comprehensive board round.
 * After the two strips (v2.2.1/v2.2.2) and the smart panels (v2.3.0),
 * the BOARD ITSELF joins the honesty and motion doctrine. Three pinned
 * contracts:
 *
 * 1. The accelerating delete ladder: holding delete keeps the user's
 *    configured rate for the first six repeats, then descends a FIXED
 *    three-gear ladder (×1.5 → ×2 → ×2.5) floored at MIN_DELAY_MS —
 *    pure, monotonic, delete-family only.
 *
 * 2. The caps-lock honesty pair: the shift key carries a small dot on
 *    the exact v2.2.1 toggle-dot contract, AND the shift state is
 *    spoken — every InputShiftState maps to one distinct localized
 *    state description.
 *
 * 3. The board motion completeness: the key pulse and the glide-trail
 *    fade run through durationOrSnap (the system «remove animations»
 *    switch collapses them to target-identical snaps), and the
 *    row-entrance wave reuses the same capped stagger the candidates
 *    row has used since v2.2.1 — a 4-row board can never exceed the
 *    pinned wave ceiling.
 */
class DrsV2400Tests : FunSpec({

    // -------------------------------------------------------------
    // سلّم الحذف المتسارع — the deterministic gear ladder
    // -------------------------------------------------------------
    test("the first six repeats run at the exact base rate") {
        for (index in 0..5) {
            DrsAcceleratedRepeat.speedFactorFor(index) shouldBe 1f
        }
    }

    test("the ladder lands on three fixed gears at the documented indices") {
        // Gear one: repeats 6–8.
        for (index in 6..8) {
            DrsAcceleratedRepeat.speedFactorFor(index) shouldBe DrsAcceleratedRepeat.GEAR_ONE_FACTOR
        }
        // Gear two: repeats 9–11.
        for (index in 9..11) {
            DrsAcceleratedRepeat.speedFactorFor(index) shouldBe DrsAcceleratedRepeat.GEAR_TWO_FACTOR
        }
        // Gear three: repeats 12+ — the final cap, forever.
        for (index in listOf(12, 13, 50, 10_000)) {
            DrsAcceleratedRepeat.speedFactorFor(index) shouldBe DrsAcceleratedRepeat.GEAR_THREE_FACTOR
        }
    }

    test("the speed factor is total and clamped for any index") {
        DrsAcceleratedRepeat.speedFactorFor(-3) shouldBe 1f
        DrsAcceleratedRepeat.speedFactorFor(0) shouldBe 1f
        for (index in -10..200) {
            val factor = DrsAcceleratedRepeat.speedFactorFor(index)
            (factor >= 1f) shouldBe true
            (factor <= DrsAcceleratedRepeat.GEAR_THREE_FACTOR) shouldBe true
        }
    }

    test("delayFor is an exact passthrough when acceleration is off") {
        // The pinned pre-2.4.0 behavior: one fixed delay forever.
        for (index in listOf(0, 5, 6, 12, 999)) {
            DrsAcceleratedRepeat.delayFor(50L, index, accelerated = false) shouldBe 50L
        }
    }

    test("delayFor descends the ladder and never crosses the floor") {
        // 50ms base (platform default at rate 100): gears divide it.
        DrsAcceleratedRepeat.delayFor(50L, 0, accelerated = true) shouldBe 50L
        DrsAcceleratedRepeat.delayFor(50L, 6, accelerated = true) shouldBe 33L // 50 / 1.5
        DrsAcceleratedRepeat.delayFor(50L, 9, accelerated = true) shouldBe 25L // 50 / 2
        DrsAcceleratedRepeat.delayFor(50L, 12, accelerated = true) shouldBe 20L // 50 / 2.5
        // A tiny base (fast repeat-rate pref) still respects the floor.
        DrsAcceleratedRepeat.delayFor(20L, 12, accelerated = true) shouldBe DrsAcceleratedRepeat.MIN_DELAY_MS
        DrsAcceleratedRepeat.delayFor(1L, 999, accelerated = true) shouldBe DrsAcceleratedRepeat.MIN_DELAY_MS
    }

    test("the accelerated delay is monotonically non-increasing in the index") {
        var previous = DrsAcceleratedRepeat.delayFor(120L, 0, accelerated = true)
        for (index in 1..40) {
            val current = DrsAcceleratedRepeat.delayFor(120L, index, accelerated = true)
            (current <= previous) shouldBe true
            previous = current
        }
    }

    test("the acceleration is scoped to the delete family only") {
        DrsAcceleratedRepeat.appliesTo(KeyCode.DELETE) shouldBe true
        DrsAcceleratedRepeat.appliesTo(KeyCode.FORWARD_DELETE) shouldBe true
        DrsAcceleratedRepeat.appliesTo(KeyCode.DELETE_WORD) shouldBe true
        DrsAcceleratedRepeat.appliesTo(KeyCode.FORWARD_DELETE_WORD) shouldBe true
        // Cursor motion must not change gears under a held key —
        // that would be a lie about position.
        DrsAcceleratedRepeat.appliesTo(KeyCode.ARROW_LEFT) shouldBe false
        DrsAcceleratedRepeat.appliesTo(KeyCode.ARROW_RIGHT) shouldBe false
        DrsAcceleratedRepeat.appliesTo(KeyCode.MOVE_WORD_LEFT) shouldBe false
        DrsAcceleratedRepeat.appliesTo(KeyCode.MOVE_WORD_RIGHT) shouldBe false
        DrsAcceleratedRepeat.appliesTo(KeyCode.UNDO) shouldBe false
        DrsAcceleratedRepeat.appliesTo(KeyCode.REDO) shouldBe false
        DrsAcceleratedRepeat.appliesTo(KeyCode.SPACE) shouldBe false
        DrsAcceleratedRepeat.appliesTo('a'.code) shouldBe false
    }

    // -------------------------------------------------------------
    // صدق قفل الأحرف — the spoken shift state
    // -------------------------------------------------------------
    test("every shift state maps to one distinct spoken state") {
        val unshifted = shiftStateA11yRes(InputShiftState.UNSHIFTED)
        val shifted = shiftStateA11yRes(InputShiftState.SHIFTED_MANUAL)
        val automatic = shiftStateA11yRes(InputShiftState.SHIFTED_AUTOMATIC)
        val capsLock = shiftStateA11yRes(InputShiftState.CAPS_LOCK)
        // Manual and automatic shifting read the same — the listener
        // cannot see the difference and the contract does not invent one.
        shifted shouldBe automatic
        unshifted shouldNotBe shifted
        shifted shouldNotBe capsLock
        unshifted shouldNotBe capsLock
    }

    test("the caps-lock dot rides the exact v2.2.1 toggle-dot contract") {
        // Active: grown, appear duration. Inactive: collapsed, disappear
        // duration. The dot adds NO new timings of its own.
        DrsMotion.dotScaleFor(true) shouldBe DrsMotion.DOT_ACTIVE_SCALE
        DrsMotion.dotScaleFor(false) shouldBe DrsMotion.DOT_IDLE_SCALE
        DrsMotion.dotDurationFor(true) shouldBe DrsMotion.DOT_APPEAR_DURATION_MS
        DrsMotion.dotDurationFor(false) shouldBe DrsMotion.DOT_DISAPPEAR_DURATION_MS
    }

    // -------------------------------------------------------------
    // اكتمال الإيقاع على اللوحة — motion completeness
    // -------------------------------------------------------------
    test("the row-entrance wave cannot exceed the pinned board ceiling") {
        // The tallest realistic board (extension row + 3 letters +
        // mod row = 5) must stay inside the candidates-wave bound:
        // per-row capped stagger + one entrance duration.
        val rows = 5
        val lastRowDelay = DrsMotion.staggerFor(rows - 1)
        val totalWaveMs = lastRowDelay + DrsMotion.ENTRANCE_DURATION_MS
        (totalWaveMs <= 250) shouldBe true
        // And the per-row delay itself respects the global stagger cap.
        (lastRowDelay <= DrsMotion.ENTRANCE_STAGGER_MAX_MS) shouldBe true
    }

    test("the motion gate collapses board timings without changing targets") {
        // Pulses, dots and the wave all run through durationOrSnap —
        // with animations off every duration is 0 and every scale
        // target is untouched.
        DrsMotion.durationOrSnap(DrsMotion.PULSE_DURATION_MS, motionEnabled = false) shouldBe 0
        DrsMotion.durationOrSnap(DrsMotion.PULSE_RELEASE_DURATION_MS, motionEnabled = false) shouldBe 0
        DrsMotion.durationOrSnap(DrsMotion.DOT_APPEAR_DURATION_MS, motionEnabled = false) shouldBe 0
        DrsMotion.durationOrSnap(DrsMotion.ENTRANCE_DURATION_MS, motionEnabled = false) shouldBe 0
        // Motion on: the pinned timings run verbatim.
        DrsMotion.durationOrSnap(DrsMotion.PULSE_DURATION_MS, motionEnabled = true) shouldBe DrsMotion.PULSE_DURATION_MS
        DrsMotion.durationOrSnap(DrsMotion.ENTRANCE_DURATION_MS, motionEnabled = true) shouldBe DrsMotion.ENTRANCE_DURATION_MS
        // The wave stagger collapses too.
        DrsMotion.staggerOrSnap(4, motionEnabled = false) shouldBe 0
        DrsMotion.staggerOrSnap(4, motionEnabled = true) shouldBe DrsMotion.staggerFor(4)
    }
})
