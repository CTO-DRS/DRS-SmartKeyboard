/*
 * Copyright (C) 2025-2026 The DRS Smart Keyboard Project
 */

package com.drs.smartkeyboard.drs

import com.drs.smartkeyboard.assertStringsExist
import com.drs.smartkeyboard.ime.text.key.KeyCode
import com.drs.smartkeyboard.ime.voice.PermissionNextAction
import com.drs.smartkeyboard.ime.voice.VoiceRecognizerMode
import com.drs.smartkeyboard.ime.voice.VoiceUiState
import com.drs.smartkeyboard.ime.voice.nextPermissionAction
import com.drs.smartkeyboard.ime.voice.usesOnDeviceRecognizer
import com.drs.smartkeyboard.readStringsXml
import com.drs.smartkeyboard.lib.ext.FINGERPRINT_MARKER_SUFFIX
import com.drs.smartkeyboard.lib.ext.canReuseCache
import com.drs.smartkeyboard.lib.ext.fingerprintOfEntries
import com.drs.smartkeyboard.lib.util.NetworkUtils
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldEndWith
import io.kotest.matchers.string.shouldStartWith

/**
 * DRS v1.26.0: the honest-face round — «الشارة الصادقة والباب الذي لا
 * يُغلق». The listening bar shows the session's privacy truth (an
 * «بدون شبكة» chip driven by the state itself), a permanently denied mic
 * permission finally has a real way out (the app settings page), the
 * technical strip carries the microphone, the extension cache stops
 * rebuilding when its source did not change, and the phone-number
 * normalization is a tested pure step. Every pure contract introduced
 * this round is pinned here.
 */
class DrsV1260Tests : FunSpec({

    // -------------------------------------------------------------
    // The badge truth — usesOnDeviceRecognizer
    // -------------------------------------------------------------
    test("AUTO sessions show the badge exactly when the local engine exists") {
        usesOnDeviceRecognizer(VoiceRecognizerMode.AUTO, onDevicePossible = true).shouldBeTrue()
        usesOnDeviceRecognizer(VoiceRecognizerMode.AUTO, onDevicePossible = false).shouldBeFalse()
    }

    test("a strict on-device session that never starts must not promise the badge") {
        // ON_DEVICE_ONLY + incapable ROM never reaches Listening at all
        // (createRecognizer yields null → the honest error path), so the
        // badge flag is false there — it must never be shown by mistake.
        usesOnDeviceRecognizer(VoiceRecognizerMode.ON_DEVICE_ONLY, onDevicePossible = false)
            .shouldBeFalse()
        usesOnDeviceRecognizer(VoiceRecognizerMode.ON_DEVICE_ONLY, onDevicePossible = true)
            .shouldBeTrue()
    }

    test("the pinned STANDARD session never claims to stay on the device") {
        usesOnDeviceRecognizer(VoiceRecognizerMode.STANDARD, onDevicePossible = true).shouldBeFalse()
        usesOnDeviceRecognizer(VoiceRecognizerMode.STANDARD, onDevicePossible = false).shouldBeFalse()
    }

    test("the badge flag rides the state and defaults honestly to hidden") {
        VoiceUiState.Listening(partial = "hello", onDevice = true).onDevice.shouldBeTrue()
        VoiceUiState.Listening(partial = "hello").onDevice.shouldBeFalse()
    }

    // -------------------------------------------------------------
    // The denied-permission way out — nextPermissionAction
    // -------------------------------------------------------------
    test("a plain denial keeps the light explanation") {
        nextPermissionAction(canShowRationale = true) shouldBe PermissionNextAction.RE_EXPLAIN
    }

    test("a permanent denial opens the app settings page") {
        nextPermissionAction(canShowRationale = false) shouldBe PermissionNextAction.OPEN_SETTINGS
    }

    test("the permission actions cover both worlds with no third way") {
        PermissionNextAction.values().size shouldBe 2
    }

    // -------------------------------------------------------------
    // The technical strip carries the microphone
    // -------------------------------------------------------------
    test("the tech toolbar catalogue carries a mic key bound to VOICE_INPUT") {
        // first{} throws when the key is missing — the existence assertion
        val mic = DrsTechToolbarKeys.ALL.first { it.id == "mic" }
        mic.code shouldBe KeyCode.VOICE_INPUT
        mic.type.name shouldBe "FUNCTION"
        // a momentary key — unique id, no duplicates in the catalogue
        DrsTechToolbarKeys.ALL.map { it.id }.distinct() shouldHaveSize DrsTechToolbarKeys.ALL.size
    }

    // -------------------------------------------------------------
    // The honest cache — canReuseCache + fingerprintOfEntries
    // -------------------------------------------------------------
    test("the cache is reused only when the source fingerprint is known and matches") {
        canReuseCache(force = false, "aaa", "aaa").shouldBeTrue()
        canReuseCache(force = false, "aaa", "bbb").shouldBeFalse()
        canReuseCache(force = false, null, "aaa").shouldBeFalse()   // legacy cache, no marker
        canReuseCache(force = false, "aaa", null).shouldBeFalse()   // unreadable source
        canReuseCache(force = false, null, null).shouldBeFalse()
        // a forced reload must always rebuild, no matter what matches
        canReuseCache(force = true, "aaa", "aaa").shouldBeFalse()
    }

    test("fingerprintOfEntries is order-independent") {
        val a = listOf("ime/theme/a.json" to 10L, "ime/theme/b.png" to 200L)
        val shuffled = a.shuffled()
        fingerprintOfEntries(a) shouldBe fingerprintOfEntries(shuffled)
    }

    test("fingerprintOfEntries changes with any path or size change") {
        val base = listOf("ime/theme/a.json" to 10L, "ime/theme/b.png" to 200L)
        val renamed = listOf("ime/theme/a.json" to 10L, "ime/theme/c.png" to 200L)
        val resized = listOf("ime/theme/a.json" to 11L, "ime/theme/b.png" to 200L)
        val fp = fingerprintOfEntries(base)
        fingerprintOfEntries(renamed) shouldNotBe fp
        fingerprintOfEntries(resized) shouldNotBe fp
    }

    test("fingerprintOfEntries is prefixed tree: and stable for empty trees") {
        fingerprintOfEntries(emptyList()) shouldStartWith "tree:"
        fingerprintOfEntries(emptyList()) shouldBe fingerprintOfEntries(emptyList())
    }

    test("the fingerprint marker is a sibling suffix, never a file inside the cache") {
        // workingDir must stay clean for the language-pack path resolution —
        // the marker lives next to the cache dir under the fixed suffix.
        FINGERPRINT_MARKER_SUFFIX shouldBe ".drs-fp"
        "org.drs.themes" + FINGERPRINT_MARKER_SUFFIX shouldEndWith ".drs-fp"
    }

    // -------------------------------------------------------------
    // The phone-number normalization — the TODO becomes a contract
    // -------------------------------------------------------------
    test("a fully wrapped number loses its wrapper") {
        NetworkUtils.normalizePhoneNumberMatch("(0541234567)") shouldBe "0541234567"
        NetworkUtils.normalizePhoneNumberMatch("(+98 21 1234 5678)") shouldBe "+98 21 1234 5678"
    }

    test("a truncated leading paren is unwrapped honestly") {
        NetworkUtils.normalizePhoneNumberMatch("(054 1234567") shouldBe "054 1234567"
    }

    test("balanced parens stay exactly as the user wrote them") {
        NetworkUtils.normalizePhoneNumberMatch("(054) 123 4567") shouldBe "(054) 123 4567"
        NetworkUtils.normalizePhoneNumberMatch("054 123 4567") shouldBe "054 123 4567"
        NetworkUtils.normalizePhoneNumberMatch("+98 21 1234 5678") shouldBe "+98 21 1234 5678"
    }

    test("isParenBalanced walks the depth honestly") {
        NetworkUtils.isParenBalanced("(054) 123").shouldBeTrue()
        NetworkUtils.isParenBalanced("(054 123").shouldBeFalse()
        NetworkUtils.isParenBalanced("054) 123(").shouldBeFalse()
        NetworkUtils.isParenBalanced("no parens").shouldBeTrue()
    }

    test("tiny and blank inputs pass through untouched") {
        NetworkUtils.normalizePhoneNumberMatch("") shouldBe ""
        NetworkUtils.normalizePhoneNumberMatch("5") shouldBe "5"
        NetworkUtils.normalizePhoneNumberMatch("(  ") shouldBe "("
    }

    // -------------------------------------------------------------
    // String parity — the two new keys exist in AR and EN
    // -------------------------------------------------------------
    test("the v1.26.0 voice strings ship in both locales") {
        // the reads live inside the test so a missing strings.xml fails
        // this test alone, with the absolute path in the message
        val arXml = readStringsXml("values")
        val enXml = readStringsXml("values-en")
        val keys = listOf("voice__on_device_badge", "voice__permission_permanent")
        assertStringsExist(arXml, keys)
        assertStringsExist(enXml, keys)
    }
})
