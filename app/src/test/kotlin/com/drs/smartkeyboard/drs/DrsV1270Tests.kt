/*
 * Copyright (C) 2025-2026 The DRS Smart Keyboard Project
 */

package com.drs.smartkeyboard.drs

import com.drs.smartkeyboard.assertStringsExist
import com.drs.smartkeyboard.ime.clipboard.ClipDictationPlan
import com.drs.smartkeyboard.ime.theme.DrsImeUi
import com.drs.smartkeyboard.readStringsXml
import com.drs.smartkeyboard.lib.ext.extensionLicenseDisplayName
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.drs.lib.snygg.SnyggElementRule

/**
 * DRS v1.27.0: the round that lets themes tell the listening bar apart
 * without breaking a single existing one, gives the popup clipboard
 * editor a microphone of its own, and speaks the world's license names.
 * Every pure contract introduced this round is pinned here; the
 * attribute-matching engine semantics live in lib/snygg's own tests.
 */
class DrsV1270Tests : FunSpec({

    // -------------------------------------------------------------
    // The listening-state attribute — DrsImeUi.Attr.Voice
    // -------------------------------------------------------------
    test("the voice attribute keeps its stable rule name") {
        DrsImeUi.Attr.Voice shouldBe "voice"
    }

    test("the smartbar voice rule parses and serializes round-trip") {
        val rule = SnyggElementRule.fromOrNull("smartbar[voice=`true`]")
        // the rule form must be a first-class citizen of the grammar
        val parsed = rule.shouldNotBeNull()
        parsed.elementName shouldBe "smartbar"
        parsed.attributes["voice"] shouldBe listOf("true")
        // and the serialization the editor writes is the one it parses back
        parsed.toString() shouldBe "smartbar[voice=`true`]"
        // a plain smartbar rule carries no voice attribute at all
        val plain = SnyggElementRule.fromOrNull("smartbar").shouldNotBeNull()
        plain.attributes["voice"] shouldBe null
    }

    // -------------------------------------------------------------
    // The popup editor's dictation plan — ClipDictationPlan.join
    // -------------------------------------------------------------
    test("blank speech never fabricates an edit") {
        ClipDictationPlan.join("hello", "", 500).shouldBeNull()
        ClipDictationPlan.join("hello", "   ", 500).shouldBeNull()
        ClipDictationPlan.join("", "", 500).shouldBeNull()
    }

    test("speech into an empty field lands as the trimmed transcript") {
        ClipDictationPlan.join("", "  مرحبًا بالعالم  ", 500) shouldBe "مرحبًا بالعالم"
    }

    test("speech joins with a single space after non-whitespace text") {
        ClipDictationPlan.join("hello", "world", 500) shouldBe "hello world"
        ClipDictationPlan.join("مرحبًا", "بكم", 500) shouldBe "مرحبًا بكم"
    }

    test("the user's own trailing whitespace is preserved, never doubled") {
        ClipDictationPlan.join("line one\n", "line two", 500) shouldBe "line one\nline two"
        ClipDictationPlan.join("hello ", "world", 500) shouldBe "hello world"
        // a transcript that itself starts with whitespace keeps its shape
        ClipDictationPlan.join("a", " b", 500) shouldBe "a b"
    }

    test("the storage limit truncates the merged text like the typed path") {
        // dictation must behave EXACTLY like typing the transcript —
        // typing " def" into "abc" under a 5-char limit yields "abc d"
        // through the very same ClipboardTextPolicy truncation
        ClipDictationPlan.join("abc", "def", 5) shouldBe "abc d"
    }

    test("a transcript swallowed whole by the limit is an honest no-op") {
        // the text already sits at the limit — the spoken word would be
        // cut off entirely, so the result must not pretend an edit
        ClipDictationPlan.join("abcde", "f", 5).shouldBeNull()
        // one free char fits the separator but not the word — again,
        // exactly what typing would leave behind
        ClipDictationPlan.join("abcd", "f", 5) shouldBe "abcd "
    }

    // -------------------------------------------------------------
    // The human license names — extensionLicenseDisplayName
    // -------------------------------------------------------------
    test("known SPDX ids become their official titles, case-insensitively") {
        extensionLicenseDisplayName("Apache-2.0") shouldBe "Apache License 2.0"
        extensionLicenseDisplayName("mit") shouldBe "MIT License"
        extensionLicenseDisplayName("MIT") shouldBe "MIT License"
        extensionLicenseDisplayName("gpl-3.0-or-later") shouldBe "GNU General Public License v3.0 or later"
        extensionLicenseDisplayName("MPL-2.0") shouldBe "Mozilla Public License 2.0"
    }

    test("unknown ids fall through honestly untouched") {
        extensionLicenseDisplayName("Proprietary-DRS-1.0") shouldBe "Proprietary-DRS-1.0"
        extensionLicenseDisplayName("LicenseRef-MyCustom") shouldBe "LicenseRef-MyCustom"
    }

    test("blank input stays blank") {
        extensionLicenseDisplayName("") shouldBe ""
        extensionLicenseDisplayName("   ") shouldBe "   "
    }

    test("OR and AND expressions map each operand, keeping the operators") {
        extensionLicenseDisplayName("MIT OR Apache-2.0") shouldBe
            "MIT License OR Apache License 2.0"
        extensionLicenseDisplayName("Apache-2.0 AND MPL-2.0") shouldBe
            "Apache License 2.0 AND Mozilla Public License 2.0"
        // triple chains keep every hop
        extensionLicenseDisplayName("MIT AND ISC OR Apache-2.0") shouldBe
            "MIT License AND ISC License OR Apache License 2.0"
    }

    test("WITH exceptions keep the unknown exception operand raw") {
        extensionLicenseDisplayName("GPL-2.0-only WITH Classpath-exception-2.0") shouldBe
            "GNU General Public License v2.0 only WITH Classpath-exception-2.0"
    }

    test("parentheses survive the mapping") {
        extensionLicenseDisplayName("(MIT OR Apache-2.0)") shouldBe
            "(MIT License OR Apache License 2.0)"
    }

    // -------------------------------------------------------------
    // String parity — the six new keys exist in AR and EN
    // -------------------------------------------------------------
    test("the v1.27.0 theme and dictation strings ship in both locales") {
        // the reads live inside the test so a missing strings.xml fails
        // this test alone, with the absolute path in the message
        val arXml = readStringsXml("values")
        val enXml = readStringsXml("values-en")
        val bothLocales = listOf(
            "settings__theme_editor__rule_window_mode",
            "settings__theme_editor__rule_voice",
            "settings__theme_editor__rule_voice_value",
            "clip__dictation_start",
            "clip__dictation_stop",
            "clip__dictation_hint",
        )
        assertStringsExist(arXml, bothLocales)
        assertStringsExist(enXml, bothLocales)
        // the reused voice toasts must still exist for the popup's paths
        assertStringsExist(
            arXml,
            listOf(
                "voice__permission_denied",
                "voice__permission_permanent",
                "voice__error_no_match",
            ),
        )
    }
})
