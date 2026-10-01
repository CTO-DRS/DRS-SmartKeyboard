/*
 * Copyright (C) 2025-2026 The DRS Smart Keyboard Project
 */

package com.drs.smartkeyboard.drs

import com.drs.smartkeyboard.appAssetsDir
import com.drs.smartkeyboard.ime.input.InputModifierState
import com.drs.smartkeyboard.ime.input.cycleModifierLatch
import com.drs.smartkeyboard.ime.input.fnFunctionKeyCodeOf
import com.drs.smartkeyboard.ime.keyboard.KeyboardMode
import com.drs.smartkeyboard.ime.keyboard.KeyboardState
import com.drs.smartkeyboard.ime.text.key.KeyCode
import com.drs.smartkeyboard.ime.voice.VoiceInputRoute
import com.drs.smartkeyboard.ime.voice.decideVoiceInputRoute
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File

/**
 * DRS v1.23.0: the voice-and-complete-face round — the tri-front audit
 * found the mic key ending in dead ends (external switch or a toast),
 * the FN/FN_LOCK modifier pair dead since day one (the exact failure
 * shape CTRL/ALT had before v1.22.0), CTRL/ALT/FN keys rendering as
 * blank boxes with no latch feedback, a shipped 21-entry kaomoji asset
 * whose loader was a stubbed null, three deprecated KeyboardMode values
 * with no producer, and seven string scripts without a parity gate.
 * Every pure contract introduced this round is pinned here.
 */
class DrsV1230Tests : FunSpec({

    // -------------------------------------------------------------
    // FN — the last dead modifier: digits become real F-keys
    // -------------------------------------------------------------
    test("fnFunctionKeyCodeOf maps digits onto F1–F10 exactly like a physical Fn row") {
        fnFunctionKeyCodeOf(49) shouldBe 131 // '1' → KEYCODE_F1
        fnFunctionKeyCodeOf(50) shouldBe 132 // '2' → F2
        fnFunctionKeyCodeOf(57) shouldBe 139 // '9' → F9
        fnFunctionKeyCodeOf(48) shouldBe 140 // '0' → F10
    }

    test("fnFunctionKeyCodeOf refuses everything that is not an ASCII digit key") {
        fnFunctionKeyCodeOf(47).shouldBeNull()  // '/'
        fnFunctionKeyCodeOf(58).shouldBeNull()  // ':'
        fnFunctionKeyCodeOf('a'.code).shouldBeNull()
        fnFunctionKeyCodeOf(KeyCode.SPACE).shouldBeNull()
        fnFunctionKeyCodeOf(KeyCode.DELETE).shouldBeNull()
    }

    test("the FN latch cycles through the same contract as CTRL/ALT") {
        cycleModifierLatch(InputModifierState.OFF, lock = false) shouldBe InputModifierState.LATCHED
        cycleModifierLatch(InputModifierState.LATCHED, lock = false) shouldBe InputModifierState.LOCKED
        cycleModifierLatch(InputModifierState.LOCKED, lock = false) shouldBe InputModifierState.OFF
        cycleModifierLatch(InputModifierState.OFF, lock = true) shouldBe InputModifierState.LOCKED
    }

    test("the FN latch lives in its own bit region without colliding with CTRL/ALT or modes") {
        val state = KeyboardState.new()
        state.inputFnState = InputModifierState.LATCHED
        state.inputCtrlState = InputModifierState.LOCKED
        state.inputAltState = InputModifierState.LATCHED
        state.keyboardMode = KeyboardMode.SYMBOLS
        state.isIncognitoMode = true

        state.inputFnState shouldBe InputModifierState.LATCHED
        state.inputCtrlState shouldBe InputModifierState.LOCKED
        state.inputAltState shouldBe InputModifierState.LATCHED
        state.keyboardMode shouldBe KeyboardMode.SYMBOLS
        state.isIncognitoMode.shouldBeTrue()

        state.inputFnState = InputModifierState.LOCKED
        state.inputFnState shouldBe InputModifierState.LOCKED
        state.inputCtrlState shouldBe InputModifierState.LOCKED
        state.inputAltState shouldBe InputModifierState.LATCHED

        // a consumed one-shot FN releases while a lock persists
        state.inputFnState = InputModifierState.LATCHED
        state.inputFnState = state.inputFnState.consumed()
        state.inputFnState shouldBe InputModifierState.OFF
    }

    // -------------------------------------------------------------
    // Voice dictation — the pure route decision
    // -------------------------------------------------------------
    test("sensitive contexts refuse the microphone before any other check") {
        decideVoiceInputRoute(
            recognitionAvailable = true,
            permissionGranted = true,
            isSensitive = true,
        ) shouldBe VoiceInputRoute.DISABLED_SENSITIVE
        decideVoiceInputRoute(
            recognitionAvailable = false,
            permissionGranted = false,
            isSensitive = true,
        ) shouldBe VoiceInputRoute.DISABLED_SENSITIVE
    }

    test("a ROM without a recognition service keeps the external voice-IME fallback") {
        decideVoiceInputRoute(
            recognitionAvailable = false,
            permissionGranted = false,
            isSensitive = false,
        ) shouldBe VoiceInputRoute.FALLBACK_EXTERNAL
        decideVoiceInputRoute(
            recognitionAvailable = false,
            permissionGranted = true,
            isSensitive = false,
        ) shouldBe VoiceInputRoute.FALLBACK_EXTERNAL
    }

    test("an armed start requires both service and microphone permission") {
        decideVoiceInputRoute(
            recognitionAvailable = true,
            permissionGranted = true,
            isSensitive = false,
        ) shouldBe VoiceInputRoute.START_INTERNAL
        decideVoiceInputRoute(
            recognitionAvailable = true,
            permissionGranted = false,
            isSensitive = false,
        ) shouldBe VoiceInputRoute.REQUEST_PERMISSION
    }

    // -------------------------------------------------------------
    // KeyboardMode — the three deprecated values are gone, safely
    // -------------------------------------------------------------
    test("the deprecated KeyboardMode values no longer exist in the enum") {
        KeyboardMode.entries.any { it.name == "EDITING" }.shouldBeFalse()
        KeyboardMode.entries.any { it.name == "SMARTBAR_CLIPBOARD_CURSOR_ROW" }.shouldBeFalse()
        KeyboardMode.entries.any { it.name == "SMARTBAR_NUMBER_ROW" }.shouldBeFalse()
        KeyboardMode.entries shouldHaveSize 9
    }

    test("stale mode ints fall back to CHARACTERS instead of crashing") {
        KeyboardMode.fromInt(1) shouldBe KeyboardMode.CHARACTERS   // old EDITING
        KeyboardMode.fromInt(8) shouldBe KeyboardMode.CHARACTERS   // old CLIPBOARD_CURSOR_ROW
        KeyboardMode.fromInt(9) shouldBe KeyboardMode.CHARACTERS   // old SMARTBAR_NUMBER_ROW
        KeyboardMode.fromInt(0) shouldBe KeyboardMode.CHARACTERS
        KeyboardMode.fromInt(10) shouldBe KeyboardMode.SMARTBAR_QUICK_ACTIONS
    }

    // -------------------------------------------------------------
    // Tech toolbar — FN joins CTRL/ALT with the live dot
    // -------------------------------------------------------------
    test("the tech toolbar catalogue carries the complete modifier family") {
        val ids = DrsTechToolbarKeys.ALL.map { it.id }
        ids.contains("ctrl").shouldBeTrue()
        ids.contains("alt").shouldBeTrue()
        ids.contains("fn").shouldBeTrue()
        val fn = DrsTechToolbarKeys.ALL.first { it.id == "fn" }
        fn.code shouldBe KeyCode.FN
        fn.type.name shouldBe "MODIFIER"
        // the honest dot reads the FN latch through the pure mapping
        DrsUnifiedTools.toggleStateOf("fn", DrsUnifiedTools.ToggleStates(fnArmed = true)).shouldBeTrue()
        DrsUnifiedTools.toggleStateOf("fn", DrsUnifiedTools.ToggleStates()).shouldBeFalse()
    }

    // -------------------------------------------------------------
    // Asset contract — the kaomoji asset stays parseable and honest
    // -------------------------------------------------------------
    // resolved lazily on first test use — never during spec construction,
    // and never relative to the process working directory
    val mediaAssetsDir by lazy { appAssetsDir("ime", "media") }

    test("the shipped kaomoji asset keeps its structure (rows of icon + meanings)") {
        val root = Json.parseToJsonElement(
            File(mediaAssetsDir, "emoticon/emoticons.json").readText(),
        ).jsonObject
        root["type"]!!.jsonPrimitive.content shouldBe "emoticon"
        root["direction"]!!.jsonPrimitive.content shouldBe "ltr"
        val rows = root["arrangement"]!!.jsonArray
        (rows.size >= 2).shouldBeTrue()
        val all = rows.flatMap { row -> row.jsonArray }
        all shouldHaveSize 21
        for (entry in all) {
            val obj = entry.jsonObject
            val icon = obj["icon"]!!.jsonPrimitive.content
            (icon.isNotBlank()).shouldBeTrue()
            // the commit path reads the icon as pure ASCII text payload
            icon.all { it.code < 128 }.shouldBeTrue()
            (obj["meaning"]?.jsonArray?.isEmpty() == false).shouldBeTrue()
        }
    }

    test("every emoticon icon survives the fabricated-key commit contract") {
        // code = 0 (< SPACE) makes asString return the label verbatim;
        // the contract test pins the mechanism at the data level: the
        // icons must not contain characters that would break the label
        // round-trip through the XML-less plain-text commit path.
        val root = Json.parseToJsonElement(
            File(mediaAssetsDir, "emoticon/emoticons.json").readText(),
        ).jsonObject
        val all = root["arrangement"]!!.jsonArray.flatMap { it.jsonArray }
        val icons = all.map { it.jsonObject["icon"]!!.jsonPrimitive.content }
        icons.distinct() shouldHaveSize icons.size
        icons.forEach { icon ->
            (icon.length <= 8).shouldBeTrue()
            (icon.none { it.code < 32 }).shouldBeTrue()
        }
    }
})
