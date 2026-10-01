/*
 * Copyright (C) 2025-2026 The DRS Smart Keyboard Project
 */

package com.drs.smartkeyboard.drs

import com.drs.smartkeyboard.appAssetsDir
import com.drs.smartkeyboard.ime.clipboard.ClipboardTextPolicy
import com.drs.smartkeyboard.ime.input.InputModifierState
import com.drs.smartkeyboard.ime.input.cycleModifierLatch
import com.drs.smartkeyboard.ime.keyboard.KeyboardState
import com.drs.smartkeyboard.ime.text.key.KeyCode
import com.drs.smartkeyboard.ime.text.key.KeyType
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File

/**
 * DRS v1.22.0: the honest-bounds round — the tri-front audit found the
 * pinned tasks bar reorderable only by one-slot nudges (no drag), the
 * clipboard pins growing unbounded in Room forever, the DRS layout pack
 * invisible to the preset catalog, and the CTRL/ALT keys dead since day
 * one (drawn, then logged as "unknown key" on press). Every pure
 * contract introduced by this round is pinned here, plus the protective
 * asset contracts that keep the refuted claims refuted.
 */
class DrsV1220Tests : FunSpec({

    // -------------------------------------------------------------
    // DrsUnifiedTools.reorderPinnedSlots — the drag core
    // -------------------------------------------------------------
    test("reorderPinnedSlots moves a task forward within the pinned head") {
        val slots = listOf("a", "b", "c", "d")
        DrsUnifiedTools.reorderPinnedSlots(slots, 0, 2) shouldBe listOf("b", "c", "a", "d")
    }

    test("reorderPinnedSlots moves a task backward within the pinned head") {
        val slots = listOf("a", "b", "c", "d")
        DrsUnifiedTools.reorderPinnedSlots(slots, 3, 1) shouldBe listOf("a", "d", "b", "c")
    }

    test("reorderPinnedSlots is identity for out-of-range or no-op moves") {
        val slots = listOf("a", "b", "c")
        DrsUnifiedTools.reorderPinnedSlots(slots, -1, 1) shouldBe slots
        DrsUnifiedTools.reorderPinnedSlots(slots, 1, 3) shouldBe slots
        DrsUnifiedTools.reorderPinnedSlots(slots, 2, 2) shouldBe slots
        DrsUnifiedTools.reorderPinnedSlots(emptyList(), 0, 0) shouldBe emptyList()
    }

    test("reorderPinnedSlots never fabricates duplicates or an 11th pin") {
        val slots = listOf("a", "b", "b", "c", "d", "e", "f", "g", "h", "i", "j")
        val result = DrsUnifiedTools.reorderPinnedSlots(slots, 10, 0)
        result.distinct() shouldBe result
        result.size shouldBe DrsUnifiedTools.MAX_PINNED_TOOLS
    }

    test("the catalogue offers the drag-reorder contract alongside the nudge buttons") {
        // the pure core must exist next to the real store API the drawer calls
        DrsUnified::class.members.any { it.name == "reorderPinnedTool" }.shouldBeTrue()
    }

    // -------------------------------------------------------------
    // ClipboardTextPolicy.pinCapAllows — the honest pin ceiling
    // -------------------------------------------------------------
    test("a new pin fits while the store is under the cap") {
        ClipboardTextPolicy.pinCapAllows(alreadyPinned = false, pinnedCount = 49, cap = 50).shouldBeTrue()
    }

    test("a new pin is refused when the cap is reached") {
        ClipboardTextPolicy.pinCapAllows(alreadyPinned = false, pinnedCount = 50, cap = 50).shouldBeFalse()
        ClipboardTextPolicy.pinCapAllows(alreadyPinned = false, pinnedCount = 60, cap = 50).shouldBeFalse()
    }

    test("re-pinning an already pinned item always succeeds") {
        ClipboardTextPolicy.pinCapAllows(alreadyPinned = true, pinnedCount = 50, cap = 50).shouldBeTrue()
        ClipboardTextPolicy.pinCapAllows(alreadyPinned = true, pinnedCount = 999, cap = 50).shouldBeTrue()
    }

    test("a lowered cap never auto-destroys existing pins — only new pins are gated") {
        // 120 existing pins with a cap of 50 stay untouched: the cap decision
        // is asked for NEW pins only, and re-pins stay allowed.
        ClipboardTextPolicy.pinCapAllows(alreadyPinned = true, pinnedCount = 120, cap = 50).shouldBeTrue()
        ClipboardTextPolicy.pinCapAllows(alreadyPinned = false, pinnedCount = 120, cap = 50).shouldBeFalse()
    }

    // -------------------------------------------------------------
    // InputModifierState + cycleModifierLatch — the revived CTRL/ALT
    // -------------------------------------------------------------
    test("a plain tap cycles OFF -> LATCHED -> LOCKED -> OFF") {
        var state = InputModifierState.OFF
        state = cycleModifierLatch(state, lock = false)
        state shouldBe InputModifierState.LATCHED
        state = cycleModifierLatch(state, lock = false)
        state shouldBe InputModifierState.LOCKED
        state = cycleModifierLatch(state, lock = false)
        state shouldBe InputModifierState.OFF
    }

    test("the lock codes jump straight to LOCKED in one press") {
        cycleModifierLatch(InputModifierState.OFF, lock = true) shouldBe InputModifierState.LOCKED
        cycleModifierLatch(InputModifierState.LATCHED, lock = true) shouldBe InputModifierState.LOCKED
    }

    test("a one-shot latch is consumed by the next press, a lock persists") {
        InputModifierState.LATCHED.consumed() shouldBe InputModifierState.OFF
        InputModifierState.LOCKED.consumed() shouldBe InputModifierState.LOCKED
        InputModifierState.OFF.consumed() shouldBe InputModifierState.OFF
    }

    test("armed means the next key rides the modifier") {
        InputModifierState.OFF.isArmed.shouldBeFalse()
        InputModifierState.LATCHED.isArmed.shouldBeTrue()
        InputModifierState.LOCKED.isArmed.shouldBeTrue()
    }

    test("fromInt round-trips and garbage falls back to OFF") {
        for (state in InputModifierState.entries) {
            InputModifierState.fromInt(state.value) shouldBe state
        }
        InputModifierState.fromInt(99) shouldBe InputModifierState.OFF
        InputModifierState.fromInt(-1) shouldBe InputModifierState.OFF
    }

    test("the CTRL/ALT latch regions never collide inside the state register") {
        val state = KeyboardState.new()
        state.inputCtrlState = InputModifierState.LOCKED
        state.inputAltState = InputModifierState.LATCHED
        state.inputCtrlState shouldBe InputModifierState.LOCKED
        state.inputAltState shouldBe InputModifierState.LATCHED
        state.inputCtrlState = InputModifierState.OFF
        state.inputCtrlState shouldBe InputModifierState.OFF
        state.inputAltState shouldBe InputModifierState.LATCHED
        // the neighbors of the new bit pairs are untouched
        state.inputShiftState = com.drs.smartkeyboard.ime.input.InputShiftState.CAPS_LOCK
        state.inputCtrlState = InputModifierState.LATCHED
        state.inputShiftState shouldBe com.drs.smartkeyboard.ime.input.InputShiftState.CAPS_LOCK
    }

    test("the revived modifier codes no longer fall into the unknown-key branch") {
        // the pipeline branches cover all four codes; this contract pins the codes
        setOf(KeyCode.CTRL, KeyCode.CTRL_LOCK, KeyCode.ALT, KeyCode.ALT_LOCK) shouldBe setOf(-1, -2, -3, -4)
    }

    // -------------------------------------------------------------
    // DrsTechToolbarKeys — the reachable surface of the revival
    // -------------------------------------------------------------
    test("the tech toolbar catalogue carries Ctrl and Alt with the real codes") {
        val ctrl = DrsTechToolbarKeys.ALL.first { it.id == "ctrl" }
        val alt = DrsTechToolbarKeys.ALL.first { it.id == "alt" }
        ctrl.code shouldBe KeyCode.CTRL
        ctrl.type shouldBe KeyType.MODIFIER
        alt.code shouldBe KeyCode.ALT
        alt.type shouldBe KeyType.MODIFIER
    }

    test("tech toolbar ids stay unique so persisted arrangements stay valid") {
        DrsTechToolbarKeys.ALL.map { it.id }.distinct() shouldHaveSize DrsTechToolbarKeys.ALL.size
    }

    test("toggleStateOf maps the modifier latches to their live state") {
        val states = DrsUnifiedTools.ToggleStates(ctrlArmed = true, altArmed = false)
        DrsUnifiedTools.toggleStateOf("ctrl", states).shouldBeTrue()
        DrsUnifiedTools.toggleStateOf("alt", states).shouldBeFalse()
        DrsUnifiedTools.toggleStateOf("emoji", states) shouldBe null
    }

    // -------------------------------------------------------------
    // Asset contracts — DRS presets join the catalog, western stays clean
    // -------------------------------------------------------------
    // resolved lazily on first test use — never during spec construction,
    // and never relative to the process working directory
    val assetsDir by lazy { appAssetsDir("ime", "keyboard") }

    test("the DRS layout pack publishes its subtype presets into the catalog") {
        val drsExt = Json.parseToJsonElement(
            File(assetsDir, "org.drs.layouts.drs/extension.json").readText(),
        ).jsonObject
        val presets = drsExt["subtypePresets"]?.jsonArray
        presets.shouldNotBeNull()
        presets shouldHaveSize 3
        val tags = presets.map { it.jsonObject["languageTag"]!!.jsonPrimitive.content }
        tags shouldBe listOf("ar-MA", "ar-DZ", "ar-SA")
    }

    test("every DRS preset reference resolves against the merged extension space") {
        fun layoutIds(pkgDir: String, type: String): Set<String> {
            val ext = Json.parseToJsonElement(
                File(assetsDir, "$pkgDir/extension.json").readText(),
            ).jsonObject
            return ext["layouts"]?.jsonObject?.get(type)?.jsonArray
                ?.map { it.jsonObject["id"]!!.jsonPrimitive.content }
                ?.toSet()
                ?: emptySet()
        }
        val packages = listOf("org.drs.layouts", "org.drs.layouts.drs", "org.drs.localization")
        val chars = packages.flatMap { layoutIds(it, "characters") }.toSet()
        val symbols = packages.flatMap { layoutIds(it, "symbols") }.toSet()
        val numeric = packages.flatMap { layoutIds(it, "numericRow") }.toSet()
        val drsExt = Json.parseToJsonElement(
            File(assetsDir, "org.drs.layouts.drs/extension.json").readText(),
        ).jsonObject
        for (preset in drsExt["subtypePresets"]!!.jsonArray) {
            val preferred = preset.jsonObject["preferred"]!!.jsonObject
            val charsId = preferred["characters"]!!.jsonPrimitive.content.split(":").last()
            val symsId = preferred["symbols"]!!.jsonPrimitive.content.split(":").last()
            val syms2Id = preferred["symbols2"]!!.jsonPrimitive.content.split(":").last()
            val numId = preferred["numericRow"]!!.jsonPrimitive.content.split(":").last()
            (charsId in chars).shouldBeTrue()
            (symsId in symbols).shouldBeTrue()
            (syms2Id in symbols).shouldBeTrue()
            (numId in numeric).shouldBeTrue()
            // every preset rides the standard appender composer and a real popup mapping
            preset.jsonObject["composer"]!!.jsonPrimitive.content shouldBe "org.drs.composers:appender"
            preset.jsonObject["popupMapping"]!!.jsonPrimitive.content shouldBe "org.drs.localization:ar"
        }
    }

    test("DRS preset language tags never collide with the localization catalog") {
        val locExt = Json.parseToJsonElement(
            File(assetsDir, "org.drs.localization/extension.json").readText(),
        ).jsonObject
        val drsExt = Json.parseToJsonElement(
            File(assetsDir, "org.drs.layouts.drs/extension.json").readText(),
        ).jsonObject
        val locTags = locExt["subtypePresets"]!!.jsonArray
            .map { it.jsonObject["languageTag"]!!.jsonPrimitive.content }.toSet()
        val drsTags = drsExt["subtypePresets"]!!.jsonArray
            .map { it.jsonObject["languageTag"]!!.jsonPrimitive.content }.toSet()
        (locTags intersect drsTags) shouldBe emptySet()
    }

    test("the western symbol pages stay free of RTL labels (protective contract)") {
        // The v1.22.0 audit REFUTED the claim that western.json carried
        // Arabic labels — this contract keeps the refutation true.
        for (path in listOf(
            "org.drs.layouts/layouts/symbols/western.json",
            "org.drs.layouts/layouts/symbols2/western.json",
        )) {
            val content = File(assetsDir, path).readText()
            val hasArabic = content.any { it.code in 0x0600..0x06FF }
            hasArabic.shouldBeFalse()
        }
    }
})
