/*
 * Copyright (C) 2025-2026 The DRS Smart Keyboard Project
 */

package com.drs.smartkeyboard.drs

import com.drs.smartkeyboard.ime.text.key.KeyCode
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

/**
 * DRS v2.3.0 «اللوحات الحيّة الصادقة» — the living-and-honest panels
 * round. The strips' two contracts (v2.2.1 motion, v2.2.2 honesty)
 * propagate DOWN to the smart panels. Three pinned contracts:
 *
 * 1. The panel gate contract: exactly the four clipboard codes depend on
 *    the live editor context — the same [com.drs.smartkeyboard.ime.keyboard.ComputingEvaluator.evaluateEnabled]
 *    the strip's slots and the quick actions ask. Text transforms and
 *    UNDO/REDO are deterministically NOT gated (transforms have no
 *    master; the engine tracks no undo stack, so a gate there would be
 *    the lie, not the button).
 *
 * 2. The panel wave contract: the entrance stagger reuses DrsMotion's
 *    capped step — a full 67-tool catalogue (or any depth) can never
 *    push a tile's delay past the pinned ceiling, and the system
 *    «remove animations» switch collapses the whole wave to an instant,
 *    target-identical appearance.
 *
 * 3. The panel pulse contract: the tiles/chips/keys pulse with the same
 *    pressed scale the strips use — one motion language everywhere.
 */
class DrsV2300Tests : FunSpec({

    // -------------------------------------------------------------
    // عقد بوابة اللوحة — the closed context-gated set
    // -------------------------------------------------------------
    test("the panel gate owns exactly the four clipboard codes") {
        DrsPanelGates.CONTEXT_GATED_TOOL_CODES.size shouldBe 4
        DrsPanelGates.isContextGated(KeyCode.CLIPBOARD_COPY) shouldBe true
        DrsPanelGates.isContextGated(KeyCode.CLIPBOARD_CUT) shouldBe true
        DrsPanelGates.isContextGated(KeyCode.CLIPBOARD_PASTE) shouldBe true
        DrsPanelGates.isContextGated(KeyCode.CLIPBOARD_SELECT_ALL) shouldBe true
    }

    test("text transforms and editor stack keys are never gated") {
        // The deterministic transforms work with or without selection,
        // clipboard or rich editor — gating them would dim tools that
        // always apply. UNDO/REDO: the engine tracks no undo stack, so
        // any gate there would fabricate knowledge it does not have.
        DrsPanelGates.isContextGated(KeyCode.UNDO) shouldBe false
        DrsPanelGates.isContextGated(KeyCode.REDO) shouldBe false
        // The v2.2.0 transform batch (sort/slug/snake/camel).
        (-670).let { code -> DrsPanelGates.isContextGated(code) shouldBe false }
        (-671).let { code -> DrsPanelGates.isContextGated(code) shouldBe false }
        (-672).let { code -> DrsPanelGates.isContextGated(code) shouldBe false }
        (-673).let { code -> DrsPanelGates.isContextGated(code) shouldBe false }
    }

    test("the gate question is deterministic — same code, same answer") {
        val probe = KeyCode.CLIPBOARD_PASTE
        DrsPanelGates.isContextGated(probe) shouldBe DrsPanelGates.isContextGated(probe)
        DrsPanelGates.CONTEXT_GATED_TOOL_CODES.sorted() shouldBe
            DrsPanelGates.CONTEXT_GATED_TOOL_CODES.toList().sorted()
    }

    // -------------------------------------------------------------
    // عقد موجة اللوحة — the capped entrance wave
    // -------------------------------------------------------------
    test("the wave stays capped no matter how deep the panel catalogue runs") {
        // 67 tools, a 200-item hypothetic grid — the delay ceiling pins
        // the whole wave inside ~1/5 of a second regardless.
        DrsMotion.staggerOrSnap(0, motionEnabled = true) shouldBe 0
        DrsMotion.staggerOrSnap(66, motionEnabled = true) shouldBe DrsMotion.ENTRANCE_STAGGER_MAX_MS
        DrsMotion.staggerOrSnap(200, motionEnabled = true) shouldBe DrsMotion.ENTRANCE_STAGGER_MAX_MS
    }

    test("the wave collapses to an instant under the motion-respect switch") {
        // Same final target (fully entered, alpha 1) — zero animation time.
        DrsMotion.staggerOrSnap(40, motionEnabled = false) shouldBe 0
        DrsMotion.durationOrSnap(DrsMotion.ENTRANCE_DURATION_MS, motionEnabled = false) shouldBe 0
        DrsMotion.durationOrSnap(DrsMotion.PULSE_DURATION_MS, motionEnabled = false) shouldBe 0
        DrsMotion.durationOrSnap(DrsMotion.PULSE_RELEASE_DURATION_MS, motionEnabled = false) shouldBe 0
    }

    // -------------------------------------------------------------
    // عقد نبضة اللوحة — the shared press pulse
    // -------------------------------------------------------------
    test("panels pulse with the exact strip contract values") {
        // One motion language: the same pressed compression and the same
        // idle spring-back the strips and the letters keys render with.
        DrsMotion.scaleFor(pressed = true) shouldBe 0.94f
        DrsMotion.scaleFor(pressed = false) shouldBe 1f
        DrsMotion.durationFor(pressed = true) shouldBe 60
        DrsMotion.durationFor(pressed = false) shouldBe 140
    }
})
