/*
 * Copyright (C) 2025-2026 The DRS Smart Keyboard Project
 */

package com.drs.smartkeyboard.drs

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

/**
 * DRS v2.2.2 «الشريطان الصادقان سياقيًا» — the context-honest strips
 * round. Two contracts join the motion source of truth:
 *
 * 1. The honest-disabled slot contract: a task-slot whose action the
 *    editor context makes a silent no-op (paste on an empty clipboard,
 *    copy/cut with no selection, language switch with a single subtype)
 *    renders dimmed at [DrsMotion.DISABLED_SLOT_ALPHA], is announced
 *    disabled to TalkBack with a localized reason suffix, and REFUSES
 *    to dispatch — while its slot-editor long-press stays alive.
 *
 * 2. The motion-respect contract: the system «remove animations»
 *    accessibility switch (ANIMATOR_DURATION_SCALE = 0) snaps every
 *    DrsMotion duration and stagger to 0 — identical final targets,
 *    zero animation time. The snap helpers are pure passthroughs while
 *    motion is enabled, so the pinned v2.2.1 timings are untouched.
 */
class DrsV2220Tests : FunSpec({

    // -------------------------------------------------------------
    // عقد الخانة المعطَّلة سياقيًا — the honest-disabled alpha
    // -------------------------------------------------------------
    test("the disabled slot alpha is a visible dim, not a vanishing act") {
        // The tile must stay readable and in place: strictly between
        // transparent and opaque, and pinned to the exact contract value
        // the strip renders with.
        DrsMotion.DISABLED_SLOT_ALPHA shouldBe 0.38f
        (DrsMotion.DISABLED_SLOT_ALPHA > 0f) shouldBe true
        (DrsMotion.DISABLED_SLOT_ALPHA < 1f) shouldBe true
    }

    // -------------------------------------------------------------
    // عقد احترام الإيقاع — duration snap passthrough (motion ON)
    // -------------------------------------------------------------
    test("with motion enabled every duration passes through untouched") {
        // The pinned v2.2.1 contracts must survive the helper verbatim.
        DrsMotion.durationOrSnap(DrsMotion.PULSE_DURATION_MS, motionEnabled = true) shouldBe 60
        DrsMotion.durationOrSnap(DrsMotion.PULSE_RELEASE_DURATION_MS, motionEnabled = true) shouldBe 140
        DrsMotion.durationOrSnap(DrsMotion.DOT_APPEAR_DURATION_MS, motionEnabled = true) shouldBe 140
        DrsMotion.durationOrSnap(DrsMotion.DOT_DISAPPEAR_DURATION_MS, motionEnabled = true) shouldBe 90
        DrsMotion.durationOrSnap(DrsMotion.ENTRANCE_DURATION_MS, motionEnabled = true) shouldBe 130
        // Identity holds for arbitrary durations too — pure passthrough.
        DrsMotion.durationOrSnap(0, motionEnabled = true) shouldBe 0
        DrsMotion.durationOrSnap(999, motionEnabled = true) shouldBe 999
    }

    // -------------------------------------------------------------
    // عقد احترام الإيقاع — duration snap (motion OFF)
    // -------------------------------------------------------------
    test("with motion disabled every duration snaps to zero") {
        DrsMotion.durationOrSnap(DrsMotion.PULSE_DURATION_MS, motionEnabled = false) shouldBe 0
        DrsMotion.durationOrSnap(DrsMotion.PULSE_RELEASE_DURATION_MS, motionEnabled = false) shouldBe 0
        DrsMotion.durationOrSnap(DrsMotion.DOT_APPEAR_DURATION_MS, motionEnabled = false) shouldBe 0
        DrsMotion.durationOrSnap(DrsMotion.DOT_DISAPPEAR_DURATION_MS, motionEnabled = false) shouldBe 0
        DrsMotion.durationOrSnap(DrsMotion.ENTRANCE_DURATION_MS, motionEnabled = false) shouldBe 0
        DrsMotion.durationOrSnap(999, motionEnabled = false) shouldBe 0
    }

    // -------------------------------------------------------------
    // عقد احترام الإيقاع — stagger snap passthrough (motion ON)
    // -------------------------------------------------------------
    test("with motion enabled the stagger keeps its capped wave shape") {
        DrsMotion.staggerOrSnap(0, motionEnabled = true) shouldBe 0
        DrsMotion.staggerOrSnap(1, motionEnabled = true) shouldBe 28
        DrsMotion.staggerOrSnap(2, motionEnabled = true) shouldBe 56
        DrsMotion.staggerOrSnap(4, motionEnabled = true) shouldBe 112 // at the cap
        DrsMotion.staggerOrSnap(500, motionEnabled = true) shouldBe 112 // clamped
        DrsMotion.staggerOrSnap(-1, motionEnabled = true) shouldBe 0 // defensive clamp
    }

    // -------------------------------------------------------------
    // عقد احترام الإيقاع — stagger snap (motion OFF)
    // -------------------------------------------------------------
    test("with motion disabled the whole wave collapses to an instant") {
        DrsMotion.staggerOrSnap(0, motionEnabled = false) shouldBe 0
        DrsMotion.staggerOrSnap(1, motionEnabled = false) shouldBe 0
        DrsMotion.staggerOrSnap(4, motionEnabled = false) shouldBe 0
        DrsMotion.staggerOrSnap(500, motionEnabled = false) shouldBe 0
        DrsMotion.staggerOrSnap(-1, motionEnabled = false) shouldBe 0
    }

    // -------------------------------------------------------------
    // الاتساق الصادق — the snap never changes a final target
    // -------------------------------------------------------------
    test("snapping preserves the pinned scales — only time collapses") {
        // The motion gate is honest precisely because it touches TIME
        // only: the target scale/space of every contract stays fixed,
        // so a snapped animation lands exactly where the animated one
        // would have.
        DrsMotion.PULSE_PRESSED_SCALE shouldBe 0.94f
        DrsMotion.PULSE_IDLE_SCALE shouldBe 1f
        DrsMotion.DOT_ACTIVE_SCALE shouldBe 1f
        DrsMotion.DOT_IDLE_SCALE shouldBe 0f
        DrsMotion.ENTRANCE_STAGGER_MAX_MS shouldBe 112
    }
})
