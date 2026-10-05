/*
 * Copyright (C) 2025-2026 The DRS Smart Keyboard Project
 */

package com.drs.smartkeyboard.drs

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

/**
 * DRS v2.6.0 «المنبثق الحي الصادق» — the living-popup round. After the
 * two strips (v2.2.1/v2.2.2), the smart panels (v2.3.0) and the board
 * itself (v2.4.0) received the motion and honesty doctrines, the
 * long-press popups remained the LAST silent surface: the active element
 * glowed by color only (FOCUS selector) with no pulse and no haptic, and
 * the bubble popped in with no entrance.
 *
 * This round's new pure contract (DrsPopupMotion) is pinned here. The
 * popup's active-element pulse intentionally has NO new constants of its
 * own — it reuses the pressed-key pulse contract verbatim, and that
 * identity is pinned below too: the popup must speak the exact language
 * of the keys, never a dialect.
 */
class DrsV2600Tests : FunSpec({

    // -------------------------------------------------------------
    // DrsMotion — the pulse contract the popup now reuses, untouched
    // -------------------------------------------------------------
    test("the popup pulse speaks the pressed-key language verbatim") {
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
    // DrsPopupMotion — the entrance contract (المنبثق يتنفّس)
    // -------------------------------------------------------------
    test("the popup entrance grows from a subtle scale, faster than the candidates wave") {
        // a from-scale strictly below 1 — a grow, never a jump from zero
        // (zero would fake a blink; the popup is attached to the finger)
        DrsPopupMotion.ENTRANCE_SCALE_FROM shouldBe 0.86f
        // attached to the finger: quicker than the candidates row (130ms)
        // and than the dot appear (140ms) — it is a momentary shadow, not
        // a standing surface
        DrsPopupMotion.ENTRANCE_DURATION_MS shouldBe 90
        (DrsPopupMotion.ENTRANCE_DURATION_MS < DrsMotion.ENTRANCE_DURATION_MS) shouldBe true
    }

    test("element stagger steps deterministically and caps below the candidates cap") {
        DrsPopupMotion.elementStaggerFor(0) shouldBe 0
        DrsPopupMotion.elementStaggerFor(-3) shouldBe 0 // defensively silent
        DrsPopupMotion.elementStaggerFor(1) shouldBe 16
        DrsPopupMotion.elementStaggerFor(2) shouldBe 32
        DrsPopupMotion.elementStaggerFor(5) shouldBe 80
        DrsPopupMotion.elementStaggerFor(6) shouldBe 96 // exactly at the cap
        DrsPopupMotion.elementStaggerFor(7) shouldBe 96 // clamped
        DrsPopupMotion.elementStaggerFor(100) shouldBe 96 // deeply clamped
        // a popup is at most two rows — its wave must be TIGHTER than the
        // candidates row's (112ms), never looser
        (DrsPopupMotion.ELEMENT_STAGGER_MAX_MS <= DrsMotion.ENTRANCE_STAGGER_MAX_MS) shouldBe true
    }

    test("the full popup wave stays inside the project's 250ms motion budget") {
        val totalWorstCase = DrsPopupMotion.ENTRANCE_DURATION_MS + DrsPopupMotion.ELEMENT_STAGGER_MAX_MS
        (totalWorstCase <= 250) shouldBe true
        totalWorstCase shouldBe 186
    }

    // -------------------------------------------------------------
    // DrsPopupMotion — the honest hover gate (صدق التحويل بين العناصر)
    // -------------------------------------------------------------
    test("hover feedback fires only on a real move to a valid element") {
        // first real hover after extend() init (-1 -> valid) IS a move
        DrsPopupMotion.shouldAnnounceHover(-1, 0) shouldBe true
        // re-hovering the same element stays silent — sliding within one
        // key fires dozens of move events, only the CHANGES speak
        DrsPopupMotion.shouldAnnounceHover(0, 0) shouldBe false
        DrsPopupMotion.shouldAnnounceHover(2, 2) shouldBe false
        // real moves between two valid elements fire, in both directions
        DrsPopupMotion.shouldAnnounceHover(2, 5) shouldBe true
        DrsPopupMotion.shouldAnnounceHover(5, 2) shouldBe true
        // a slide-out (index reset to -1) is silent — leaving is not a
        // selection, and the release feedback already owns that moment
        DrsPopupMotion.shouldAnnounceHover(3, -1) shouldBe false
        DrsPopupMotion.shouldAnnounceHover(-1, -1) shouldBe false
    }

    test("the hover gate never fires for invalid targets regardless of history") {
        // the previous index may legitimately be anything (init state,
        // slide-out residue) — only the NEW index gates the decision
        DrsPopupMotion.shouldAnnounceHover(-5, 1) shouldBe true
        DrsPopupMotion.shouldAnnounceHover(99, -1) shouldBe false
        DrsPopupMotion.shouldAnnounceHover(-1, 3) shouldBe true
    }
})
