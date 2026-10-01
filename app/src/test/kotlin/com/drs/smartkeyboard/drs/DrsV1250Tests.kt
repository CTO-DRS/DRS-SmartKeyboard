/*
 * Copyright (C) 2025-2026 The DRS Smart Keyboard Project
 */

package com.drs.smartkeyboard.drs

import com.drs.smartkeyboard.assertStringsExist
import com.drs.smartkeyboard.ime.voice.DrsVoiceInputBus
import com.drs.smartkeyboard.ime.voice.VoiceInputRoute
import com.drs.smartkeyboard.ime.voice.VoiceRecognizerMode
import com.drs.smartkeyboard.ime.voice.VoiceUiState
import com.drs.smartkeyboard.ime.voice.decideVoiceInputRoute
import com.drs.smartkeyboard.ime.voice.rmsToAmplitude
import com.drs.smartkeyboard.ime.window.ImeWindowMode
import com.drs.smartkeyboard.readStringsXml
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * DRS v1.25.0: the preference-and-live-pulse round — «المستخدم يختار أين
 * يُسمع كلامه، والشريط يتنفس بصوته الحقيقي». The second voice gate turns
 * the recognizer choice into a real decision the mic key honors on the
 * very next press (auto / strictly on-device / standard), the RMS
 * channel gives the listening bar the user's actual voice amplitude, and
 * the oldest window-mode placeholder (THUMBS) finally leaves the enum.
 * Every pure contract introduced this round is pinned here.
 */
class DrsV1250Tests : FunSpec({

    // -------------------------------------------------------------
    // rmsToAmplitude — the honest dB→amplitude window
    // -------------------------------------------------------------
    test("rmsToAmplitude maps the -2..12 dB window onto 0..1") {
        rmsToAmplitude(-2f) shouldBe 0f          // the floor is silence
        rmsToAmplitude(5f) shouldBe 0.5f         // the exact middle of the window
        rmsToAmplitude(12f) shouldBe 1f          // the ceiling is full voice
    }

    test("rmsToAmplitude clamps out-of-band reports instead of lying") {
        rmsToAmplitude(-100f) shouldBe 0f        // deep silence stays zero
        rmsToAmplitude(100f) shouldBe 1f         // a shout stays capped
    }

    test("rmsToAmplitude quantizes to 2% steps so the bar stops jittering") {
        // (0.3 + 2) / 14 = 0.1642857… → quantized to 0.16, not the raw value
        rmsToAmplitude(0.3f) shouldBe 0.16f
        // (11 + 2) / 14 = 0.92857… → quantized to 0.92
        rmsToAmplitude(11f) shouldBe 0.92f
    }

    test("rmsToAmplitude never decreases as the voice grows inside the window") {
        var previous = rmsToAmplitude(-2f)
        for (db in -1..12) {
            val current = rmsToAmplitude(db.toFloat())
            (current >= previous).shouldBeTrue()
            previous = current
        }
    }

    // -------------------------------------------------------------
    // The second voice gate — the recognizer mode is a real decision
    // -------------------------------------------------------------
    test("a strict on-device demand a ROM cannot honor is refused honestly") {
        decideVoiceInputRoute(
            recognitionAvailable = true,
            permissionGranted = true,
            isSensitive = false,
            userEnabled = true,
            recognizerMode = VoiceRecognizerMode.ON_DEVICE_ONLY,
            onDeviceAvailable = false,
        ) shouldBe VoiceInputRoute.ON_DEVICE_UNAVAILABLE
        // the refusal is deterministic — the same inputs, the same
        // honest answer, never a silent second press leaking through
        decideVoiceInputRoute(
            recognitionAvailable = true,
            permissionGranted = true,
            isSensitive = false,
            userEnabled = true,
            recognizerMode = VoiceRecognizerMode.ON_DEVICE_ONLY,
            onDeviceAvailable = false,
        ) shouldBe VoiceInputRoute.ON_DEVICE_UNAVAILABLE
    }

    test("a strict on-device demand the ROM can honor flows to the normal contract") {
        decideVoiceInputRoute(
            recognitionAvailable = true,
            permissionGranted = true,
            isSensitive = false,
            userEnabled = true,
            recognizerMode = VoiceRecognizerMode.ON_DEVICE_ONLY,
            onDeviceAvailable = true,
        ) shouldBe VoiceInputRoute.START_INTERNAL
        decideVoiceInputRoute(
            recognitionAvailable = true,
            permissionGranted = false,
            isSensitive = false,
            userEnabled = true,
            recognizerMode = VoiceRecognizerMode.ON_DEVICE_ONLY,
            onDeviceAvailable = true,
        ) shouldBe VoiceInputRoute.REQUEST_PERMISSION
    }

    test("the standard mode skips the on-device gate entirely") {
        // on-device unavailable is irrelevant when the user pinned the
        // classic recognizer — the gate must not refuse
        decideVoiceInputRoute(
            recognitionAvailable = true,
            permissionGranted = true,
            isSensitive = false,
            userEnabled = true,
            recognizerMode = VoiceRecognizerMode.STANDARD,
            onDeviceAvailable = false,
        ) shouldBe VoiceInputRoute.START_INTERNAL
        // and the standard availability check still guards it
        decideVoiceInputRoute(
            recognitionAvailable = false,
            permissionGranted = true,
            isSensitive = false,
            userEnabled = true,
            recognizerMode = VoiceRecognizerMode.STANDARD,
            onDeviceAvailable = false,
        ) shouldBe VoiceInputRoute.FALLBACK_EXTERNAL
    }

    test("auto keeps the v1.24 contract and never demands on-device") {
        decideVoiceInputRoute(
            recognitionAvailable = true,
            permissionGranted = true,
            isSensitive = false,
            userEnabled = true,
            recognizerMode = VoiceRecognizerMode.AUTO,
            onDeviceAvailable = false,
        ) shouldBe VoiceInputRoute.START_INTERNAL
        // the default arguments keep every v1.23/v1.24 call site honest
        decideVoiceInputRoute(
            recognitionAvailable = true,
            permissionGranted = true,
            isSensitive = false,
            userEnabled = true,
        ) shouldBe VoiceInputRoute.START_INTERNAL
    }

    test("privacy and the user setting still lead the mode gate") {
        // sensitivity beats the mode demand
        decideVoiceInputRoute(
            recognitionAvailable = true,
            permissionGranted = true,
            isSensitive = true,
            userEnabled = true,
            recognizerMode = VoiceRecognizerMode.ON_DEVICE_ONLY,
            onDeviceAvailable = false,
        ) shouldBe VoiceInputRoute.DISABLED_SENSITIVE
        // a chosen-off mic beats the mode demand too
        decideVoiceInputRoute(
            recognitionAvailable = true,
            permissionGranted = true,
            isSensitive = false,
            userEnabled = false,
            recognizerMode = VoiceRecognizerMode.ON_DEVICE_ONLY,
            onDeviceAvailable = false,
        ) shouldBe VoiceInputRoute.DISABLED_BY_SETTING
    }

    test("the route enum carries exactly the six honest answers") {
        VoiceInputRoute.entries.map { it.name }.toSet() shouldBe setOf(
            "START_INTERNAL",
            "REQUEST_PERMISSION",
            "FALLBACK_EXTERNAL",
            "DISABLED_SENSITIVE",
            "DISABLED_BY_SETTING",
            "ON_DEVICE_UNAVAILABLE",
        )
    }

    test("the recognizer mode enum round-trips by name and refuses junk") {
        VoiceRecognizerMode.entries.map { it.name }.toSet() shouldBe setOf(
            "AUTO", "ON_DEVICE_ONLY", "STANDARD",
        )
        VoiceRecognizerMode.valueOf("ON_DEVICE_ONLY") shouldBe VoiceRecognizerMode.ON_DEVICE_ONLY
        shouldThrow<IllegalArgumentException> { VoiceRecognizerMode.valueOf("JUNK") }
    }

    // -------------------------------------------------------------
    // The RMS bus channel — silence in, silence out
    // -------------------------------------------------------------
    test("the bus amplitude channel resets with the session") {
        val rms = DrsVoiceInputBus.rmsAmplitude as MutableStateFlow<Float>
        rms.value = 0.9f
        DrsVoiceInputBus.reset()
        rms.value shouldBe 0f
        DrsVoiceInputBus.uiState.value shouldBe VoiceUiState.Idle
        rms.value = 0.9f // restore global state for other suites
        DrsVoiceInputBus.reset()
    }

    // -------------------------------------------------------------
    // THUMBS leaves the window-mode enum
    // -------------------------------------------------------------
    test("the fixed window mode is exactly NORMAL and COMPACT — no THUMBS ghost") {
        ImeWindowMode.Fixed.entries.map { it.name } shouldBe listOf("NORMAL", "COMPACT")
        ImeWindowMode.Floating.entries.map { it.name } shouldBe listOf("NORMAL")
        // the persisted-config decoder can therefore never produce a
        // THUMBS mode again; stale JSON falls back to the empty map
        ImeWindowMode.entries.map { it.name } shouldBe listOf("FIXED", "FLOATING")
    }

    test("strings parity baseline: the v1.25.0 keys exist in both languages") {
        // the reads live inside the test so a missing strings.xml fails
        // this test alone, with the absolute path in the message
        val ar = readStringsXml("values")
        val en = readStringsXml("values-en")
        val keys = listOf(
            "pref__voice__recognizer_mode__label",
            "enum__voice_recognizer_mode__auto",
            "enum__voice_recognizer_mode__on_device_only",
            "enum__voice_recognizer_mode__standard",
            "voice__on_device_unavailable",
        )
        assertStringsExist(ar, keys)
        assertStringsExist(en, keys)
    }
})
