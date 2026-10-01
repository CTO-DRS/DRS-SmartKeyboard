/*
 * Copyright (C) 2025-2026 The DRS Smart Keyboard Project
 */

package com.drs.smartkeyboard.drs

import com.drs.smartkeyboard.appAssetsDir
import com.drs.smartkeyboard.ime.input.fnFunctionKeyCodeOf
import com.drs.smartkeyboard.ime.input.fnSurvivesKey
import com.drs.smartkeyboard.ime.text.key.KeyCode
import com.drs.smartkeyboard.ime.voice.VoiceInputRoute
import com.drs.smartkeyboard.ime.voice.decideVoiceInputRoute
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File

/**
 * DRS v1.24.0: the permission-and-extension round — «الميكروفون في قبضة
 * المستخدم وصف Fn الأخير». The tri-front audit found the v1.23 voice
 * dictation shipped with no explicit user-facing gate (a chosen-off
 * feature must stay off), the on-screen Fn row stopping at F10 while
 * '-' and '=' sit right after '0' exactly like F11/F12 sit after F10
 * on a physical keyboard, and the latch cleanup consuming the armed FN
 * on page hops — which would have made the extended row unreachable.
 * Every pure contract introduced this round is pinned here, including
 * the asset contracts that keep '-'/'=' genuinely reachable.
 */
class DrsV1240Tests : FunSpec({

    // -------------------------------------------------------------
    // The Fn row reaches its natural end: '-' → F11, '=' → F12
    // -------------------------------------------------------------
    test("fnFunctionKeyCodeOf maps '-' and '=' onto F11 and F12") {
        fnFunctionKeyCodeOf(45) shouldBe 141 // '-' → KEYCODE_F11
        fnFunctionKeyCodeOf(61) shouldBe 142 // '=' → KEYCODE_F12
    }

    test("the digit mapping of the Fn row stays exactly as v1.23 pinned it") {
        fnFunctionKeyCodeOf(49) shouldBe 131 // '1' → F1
        fnFunctionKeyCodeOf(50) shouldBe 132 // '2' → F2
        fnFunctionKeyCodeOf(57) shouldBe 139 // '9' → F9
        fnFunctionKeyCodeOf(48) shouldBe 140 // '0' → F10
    }

    test("keys outside the Fn row are still refused honestly") {
        fnFunctionKeyCodeOf(46).shouldBeNull()  // '.' — the row is exact, not fuzzy
        fnFunctionKeyCodeOf(47).shouldBeNull()  // '/'
        fnFunctionKeyCodeOf(64).shouldBeNull()  // '@'
        fnFunctionKeyCodeOf(43).shouldBeNull()  // '+'
        fnFunctionKeyCodeOf('a'.code).shouldBeNull()
        fnFunctionKeyCodeOf(KeyCode.SPACE).shouldBeNull()
        fnFunctionKeyCodeOf(KeyCode.DELETE).shouldBeNull()
    }

    // -------------------------------------------------------------
    // The FN latch survives page hops — or the row would be a lie
    // -------------------------------------------------------------
    test("page and mode switches keep the armed FN latch alive") {
        val pageHops = listOf(
            KeyCode.VIEW_CHARACTERS,
            KeyCode.VIEW_SYMBOLS,
            KeyCode.VIEW_SYMBOLS2,
            KeyCode.VIEW_NUMERIC,
            KeyCode.VIEW_NUMERIC_ADVANCED,
            KeyCode.VIEW_PHONE,
            KeyCode.VIEW_PHONE2,
            KeyCode.IME_UI_MODE_TEXT,
            KeyCode.IME_UI_MODE_MEDIA,
            KeyCode.IME_UI_MODE_CLIPBOARD,
            KeyCode.IME_UI_MODE_TEXT_TOOLS,
            KeyCode.IME_UI_MODE_DIACRITICS,
            KeyCode.IME_UI_MODE_SMART_SYMBOLS,
            KeyCode.IME_UI_MODE_ARABIC_LETTERS,
        )
        for (code in pageHops) {
            fnSurvivesKey(code).shouldBeTrue()
        }
        // the FN keys themselves never consume themselves
        fnSurvivesKey(KeyCode.FN).shouldBeTrue()
        fnSurvivesKey(KeyCode.FN_LOCK).shouldBeTrue()
    }

    test("real consuming keys still release a one-shot FN latch") {
        fnSurvivesKey('a'.code).shouldBeFalse()
        fnSurvivesKey(KeyCode.SPACE).shouldBeFalse()
        fnSurvivesKey(KeyCode.ENTER).shouldBeFalse()
        // the Fn-row keys themselves ARE the consuming press
        fnSurvivesKey(45).shouldBeFalse() // '-' is the F11 press
        fnSurvivesKey(61).shouldBeFalse() // '=' is the F12 press
        fnSurvivesKey(49).shouldBeFalse() // '1' is the F1 press
        fnSurvivesKey(48).shouldBeFalse() // '0' is the F10 press
    }

    // -------------------------------------------------------------
    // The voice settings gate — a chosen-off mic stays off
    // -------------------------------------------------------------
    test("the explicit user setting silences the mic after privacy wins") {
        // a disabled setting answers with DISABLED_BY_SETTING…
        decideVoiceInputRoute(
            recognitionAvailable = true,
            permissionGranted = true,
            isSensitive = false,
            userEnabled = false,
        ) shouldBe VoiceInputRoute.DISABLED_BY_SETTING
        decideVoiceInputRoute(
            recognitionAvailable = false,
            permissionGranted = false,
            isSensitive = false,
            userEnabled = false,
        ) shouldBe VoiceInputRoute.DISABLED_BY_SETTING
        // …but privacy still beats the setting: sensitive contexts never
        // leak whether the mic could have listened
        decideVoiceInputRoute(
            recognitionAvailable = true,
            permissionGranted = true,
            isSensitive = true,
            userEnabled = false,
        ) shouldBe VoiceInputRoute.DISABLED_SENSITIVE
    }

    test("the enabled path keeps the v1.23 route contract intact") {
        decideVoiceInputRoute(
            recognitionAvailable = true,
            permissionGranted = true,
            isSensitive = false,
            userEnabled = true,
        ) shouldBe VoiceInputRoute.START_INTERNAL
        decideVoiceInputRoute(
            recognitionAvailable = true,
            permissionGranted = false,
            isSensitive = false,
            userEnabled = true,
        ) shouldBe VoiceInputRoute.REQUEST_PERMISSION
        decideVoiceInputRoute(
            recognitionAvailable = false,
            permissionGranted = true,
            isSensitive = false,
            userEnabled = true,
        ) shouldBe VoiceInputRoute.FALLBACK_EXTERNAL
        // the default argument keeps every v1.23 call site honest
        decideVoiceInputRoute(
            recognitionAvailable = true,
            permissionGranted = true,
            isSensitive = false,
        ) shouldBe VoiceInputRoute.START_INTERNAL
    }

    // -------------------------------------------------------------
    // Asset contracts — '-'/'=' must genuinely exist on reachable pages
    // -------------------------------------------------------------
    // resolved lazily on first test use — never during spec construction,
    // and never relative to the process working directory
    val layoutsDir by lazy { appAssetsDir("ime", "keyboard", "org.drs.layouts", "layouts") }

    fun collectCodes(file: File): Set<Int> {
        val found = mutableSetOf<Int>()
        fun walk(element: kotlinx.serialization.json.JsonElement) {
            when (element) {
                is kotlinx.serialization.json.JsonObject -> {
                    val code = element["code"]
                    if (code is kotlinx.serialization.json.JsonPrimitive && code.content.toIntOrNull() != null) {
                        found.add(code.content.toInt())
                    }
                    element.values.forEach { walk(it) }
                }
                is kotlinx.serialization.json.JsonArray -> element.forEach { walk(it) }
                else -> Unit
            }
        }
        walk(Json.parseToJsonElement(file.readText()))
        return found
    }

    test("the numeric page ships '-' next to the digits (F11 is arm-and-press)") {
        val codes = collectCodes(File(layoutsDir, "numeric/western_arabic.json"))
        codes.contains(45).shouldBeTrue()   // '-' → F11
        codes.containsAll((48..57).toList()).shouldBeTrue() // the digit row stays intact
    }

    test("the symbols page ships '-' and the symbols2 page ships '=' (F12 reachable by a surviving latch)") {
        collectCodes(File(layoutsDir, "symbols/western.json")).contains(45).shouldBeTrue()
        collectCodes(File(layoutsDir, "symbols2/western.json")).contains(61).shouldBeTrue()
    }
})
