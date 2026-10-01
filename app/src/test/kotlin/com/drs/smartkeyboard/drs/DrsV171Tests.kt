/*
 * Copyright (C) 2020-2025 The DRS Smart Keyboard Project
 */

package com.drs.smartkeyboard.drs

import com.drs.smartkeyboard.ime.keyboard.keyA11yLabelRes
import com.drs.smartkeyboard.ime.nlp.AutocorrectDecider
import com.drs.smartkeyboard.ime.text.key.KeyCode
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.nulls.beNull
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe

/**
 * DRS v1.17.0 (round 17): world-class quality round. Pins the three pure
 * engines added this round — the TRUE autocorrect decider, the TalkBack
 * key-label mapping, and the haraka-first backspace decision — plus the
 * locale-honesty invariant that the broken locale resource dirs are gone.
 */
class DrsV171Tests : FunSpec({

    // ---------------------------------------------------------------
    // التصحيح التلقائي الحقيقي — AutocorrectDecider
    // ---------------------------------------------------------------
    test("a genuine typo matching a very common word auto-commits") {
        // "teh" -> "the" (freq 255), no dictionary word starts with "teh".
        AutocorrectDecider.shouldAutoCommit(
            typedLength = 3,
            typedIsPrefixMatch = false,
            correctionFreq = 255,
            enabled = true,
        ) shouldBe true
    }

    test("prefix matches never auto-commit — typing an uncommon-but-valid word start is safe") {
        AutocorrectDecider.shouldAutoCommit(
            typedLength = 5,
            typedIsPrefixMatch = true,
            correctionFreq = 255,
            enabled = true,
        ) shouldBe false
    }

    test("rare corrections never auto-commit — only corpus-head frequencies qualify") {
        AutocorrectDecider.shouldAutoCommit(
            typedLength = 4,
            typedIsPrefixMatch = false,
            correctionFreq = AutocorrectDecider.MIN_CORRECTION_FREQ - 1,
            enabled = true,
        ) shouldBe false
        AutocorrectDecider.shouldAutoCommit(
            typedLength = 4,
            typedIsPrefixMatch = false,
            correctionFreq = AutocorrectDecider.MIN_CORRECTION_FREQ,
            enabled = true,
        ) shouldBe true
    }

    test("short words never auto-commit — no two-letter silent rewrites") {
        AutocorrectDecider.shouldAutoCommit(
            typedLength = AutocorrectDecider.MIN_WORD_LENGTH - 1,
            typedIsPrefixMatch = false,
            correctionFreq = 255,
            enabled = true,
        ) shouldBe false
    }

    test("the decider honors the user pref and every gate composes") {
        AutocorrectDecider.shouldAutoCommit(6, false, 255, enabled = false) shouldBe false
        AutocorrectDecider.shouldAutoCommit(6, false, 255, enabled = true) shouldBe true
        AutocorrectDecider.shouldAutoCommit(6, true, 255, enabled = true) shouldBe false
        AutocorrectDecider.shouldAutoCommit(6, false, 100, enabled = true) shouldBe false
        AutocorrectDecider.shouldAutoCommit(2, false, 255, enabled = true) shouldBe false
    }

    // ---------------------------------------------------------------
    // الوصول الشامل — TalkBack labels for the icon-only keys
    // ---------------------------------------------------------------
    test("every icon-only key resolves a localized a11y label") {
        val iconKeys = listOf(
            KeyCode.SHIFT, KeyCode.CAPS_LOCK, KeyCode.DELETE, KeyCode.FORWARD_DELETE,
            KeyCode.DELETE_WORD, KeyCode.ENTER, KeyCode.ARROW_LEFT, KeyCode.ARROW_RIGHT,
            KeyCode.ARROW_UP, KeyCode.ARROW_DOWN, KeyCode.MOVE_START_OF_LINE,
            KeyCode.MOVE_END_OF_LINE, KeyCode.VIEW_CHARACTERS, KeyCode.VIEW_SYMBOLS,
            KeyCode.VIEW_SYMBOLS2, KeyCode.VIEW_NUMERIC_ADVANCED,
            KeyCode.IME_UI_MODE_DIACRITICS,
        )
        iconKeys.forEach { code ->
            keyA11yLabelRes(code).shouldNotBeNull()
        }
    }

    test("character keys resolve no a11y mapping — their visible label speaks") {
        keyA11yLabelRes('a'.code) should beNull()
        keyA11yLabelRes(1590 /* ض */) should beNull()
        keyA11yLabelRes(KeyCode.SPACE) should beNull()
        keyA11yLabelRes(-9999) should beNull()
    }

    test("the diacritics panel entry has its own spoken label") {
        keyA11yLabelRes(KeyCode.IME_UI_MODE_DIACRITICS) shouldBe
            com.drs.smartkeyboard.R.string.key__a11y_view_harakat
    }

    // ---------------------------------------------------------------
    // حذف الحركة أولًا — haraka-first backspace decision
    // ---------------------------------------------------------------
    test("a trailing haraka is peeled off before the letter") {
        HarakatSmartInsert.shouldStripBeforeDelete("محمدّ", enabled = true) shouldBe true
        HarakatSmartInsert.shouldStripBeforeDelete("بَ", enabled = true) shouldBe true
        HarakatSmartInsert.shouldStripBeforeDelete("بُ", enabled = true) shouldBe true
        HarakatSmartInsert.shouldStripBeforeDelete("بْ", enabled = true) shouldBe true
        HarakatSmartInsert.shouldStripBeforeDelete("بٰ", enabled = true) shouldBe true
    }

    test("letters, tatweel and empty text keep the normal delete path") {
        HarakatSmartInsert.shouldStripBeforeDelete("محمد", enabled = true) shouldBe false
        HarakatSmartInsert.shouldStripBeforeDelete("ـ", enabled = true) shouldBe false // tatweel is not a mark
        HarakatSmartInsert.shouldStripBeforeDelete("", enabled = true) shouldBe false
    }

    test("the decider honors the user pref") {
        HarakatSmartInsert.shouldStripBeforeDelete("بَّ", enabled = false) shouldBe false
        HarakatSmartInsert.shouldStripBeforeDelete("بَّ", enabled = true) shouldBe true
    }

    // ---------------------------------------------------------------
    // صحة اللغات — broken locale dirs removed
    // ---------------------------------------------------------------
    test("localeConfig no longer advertises untranslated stubs") {
        val text = javaIO("app/src/main/res/xml/locales_config.xml")
        val locales = Regex("android:name=\"([^\"]+)\"").findAll(text).map { it.groupValues[1] }.toList()
        locales shouldContainExactly locales.filterNot { it == "ur-PK" || it == "nds-DE" }
        locales shouldContain "ar"
        locales shouldContain "en"
    }
})

private fun javaIO(path: String): String {
    // The unit-test working dir differs between module and root invocations.
    val candidates = listOf(
        java.io.File(path),
        java.io.File("app").resolve(path),
        java.io.File(path.removePrefix("app/")),
    )
    return candidates.firstOrNull { it.exists() }?.readText()
        ?: error("resource file not reachable from test cwd: $path")
}
