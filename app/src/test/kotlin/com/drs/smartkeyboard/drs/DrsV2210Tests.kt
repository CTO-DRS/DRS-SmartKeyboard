/*
 * Copyright (C) 2025-2026 The DRS Smart Keyboard Project
 */

package com.drs.smartkeyboard.drs

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

/**
 * DRS v2.2.1 «الشريطان الحيّان» — the living-strips round. The two bars
 * above the keyboard (the tasks bar and the suggestions strip) receive
 * the Design-2030 motion language and honest feedback: the same key-tap
 * pulse the keyboard keys already use, a toggle dot that grows in and
 * shrinks out instead of popping, an honest accent halo behind ACTIVE
 * toggles, capped-stagger entrance waves for the suggestions row, and
 * haptics at the interaction site for controls that dispatched through
 * the input pipeline but never spoke (the dispatcher owns no feedback).
 *
 * Every NEW pure contract introduced by this round is pinned here; the
 * pre-existing pulse contract stays pinned by DrsM2DesignTests and must
 * be untouched by the extension.
 */
class DrsV2210Tests : FunSpec({

    // -------------------------------------------------------------
    // DrsMotion — the pulse contract is EXTENDED, never redefined
    // -------------------------------------------------------------
    test("the pre-existing key pulse contract is byte-identical") {
        DrsMotion.PULSE_PRESSED_SCALE shouldBe 0.94f
        DrsMotion.PULSE_IDLE_SCALE shouldBe 1f
        DrsMotion.PULSE_DURATION_MS shouldBe 60
        DrsMotion.PULSE_RELEASE_DURATION_MS shouldBe 140
        DrsMotion.scaleFor(pressed = true) shouldBe 0.94f
        DrsMotion.scaleFor(pressed = false) shouldBe 1f
        DrsMotion.durationFor(pressed = true) shouldBe 60
        DrsMotion.durationFor(pressed = false) shouldBe 140
    }

    // -------------------------------------------------------------
    // DrsMotion — the toggle dot contract (نقطة التفعيل تتنفّس)
    // -------------------------------------------------------------
    test("the dot grows to full scale when active and to zero when idle") {
        DrsMotion.DOT_ACTIVE_SCALE shouldBe 1f
        DrsMotion.DOT_IDLE_SCALE shouldBe 0f
        DrsMotion.dotScaleFor(active = true) shouldBe 1f
        DrsMotion.dotScaleFor(active = false) shouldBe 0f
    }

    test("the dot appears slower than it disappears — states fade, not linger") {
        DrsMotion.DOT_APPEAR_DURATION_MS shouldBe 140
        DrsMotion.DOT_DISAPPEAR_DURATION_MS shouldBe 90
        DrsMotion.dotDurationFor(active = true) shouldBe 140
        DrsMotion.dotDurationFor(active = false) shouldBe 90
    }

    // -------------------------------------------------------------
    // DrsMotion — the capped candidate entrance stagger (دخول متدرّج)
    // -------------------------------------------------------------
    test("the first candidate enters immediately and the wave steps per index") {
        DrsMotion.ENTRANCE_DURATION_MS shouldBe 130
        DrsMotion.ENTRANCE_STAGGER_STEP_MS shouldBe 28
        DrsMotion.staggerFor(0) shouldBe 0
        DrsMotion.staggerFor(1) shouldBe 28
        DrsMotion.staggerFor(2) shouldBe 56
        DrsMotion.staggerFor(3) shouldBe 84
    }

    test("the stagger is hard-capped so deep candidates never feel laggy") {
        DrsMotion.ENTRANCE_STAGGER_MAX_MS shouldBe 112
        // 4 * 28 = 112 exactly — at the cap.
        DrsMotion.staggerFor(4) shouldBe 112
        // Anything deeper clamps: index 9 and index 500 wait the same.
        DrsMotion.staggerFor(9) shouldBe 112
        DrsMotion.staggerFor(500) shouldBe 112
    }

    test("a negative index is defensively clamped to zero delay") {
        DrsMotion.staggerFor(-1) shouldBe 0
        DrsMotion.staggerFor(-100) shouldBe 0
    }

    test("the whole visible wave stays within a fifth of a second") {
        // The contract the cap exists for: last delay + one entrance
        // duration must never exceed ~240ms even for a wide row.
        val worstCase = DrsMotion.staggerFor(Int.MAX_VALUE) + DrsMotion.ENTRANCE_DURATION_MS
        (worstCase <= 240) shouldBe true
    }
})
