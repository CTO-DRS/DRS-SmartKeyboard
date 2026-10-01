/*
 * Copyright (C) 2025-2026 The DRS Smart Keyboard Project
 */

package com.drs.smartkeyboard.drs

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import java.util.Locale

/**
 * DRS p9: Unicode boundaries of DrsTextTools — Turkish locale casing, the
 * per-char TOGGLE_CASE semantics, the TITLE_CASE intra-word separator
 * rule, emoji stripping (sequences, flags, VS16, ZWNJ), URLDecoder
 * semantics, list split/join edges, the countInfo triple and the
 * SENTENCE_PER_LINE boundary rule.
 *
 * Every expectation below was verified against the real implementations
 * (and the desktop JDK semantics the tests actually run on); where the
 * original audit guess differed, the REAL behavior is pinned and noted.
 */
class DrsTextToolsUnicodeBoundaryTest : FunSpec({

    val en = Locale.US
    val tr = Locale.forLanguageTag("tr")
    val ar = Locale.forLanguageTag("ar")

    test("Turkish casing goes through the locale-aware API (i → İ, İ → i)") {
        DrsTextTools.apply(DrsTextTool.UPPERCASE, "dikkat", tr) shouldBe "DİKKAT"
        DrsTextTools.apply(DrsTextTool.LOWERCASE, "DİKKAT", tr) shouldBe "dikkat"
        DrsTextTools.apply(DrsTextTool.SENTENCE_CASE, "istanbul", tr) shouldBe "İstanbul"
    }

    test("TOGGLE_CASE inverts per char: ß is caseless, İ drops to i") {
        // 'ß' has no single-char uppercase mapping (Character.toUpperCase
        // returns it unchanged), so it passes through while the rest of the
        // lowercase word flips — the audit's "unchanged" guess was wrong.
        DrsTextTools.apply(DrsTextTool.TOGGLE_CASE, "ßeispiel", en) shouldBe "ßEISPIEL"
        // 'İ' has a simple lowercase mapping to 'i' (U+0130 → U+0069).
        DrsTextTools.apply(DrsTextTool.TOGGLE_CASE, "İzmir", en) shouldBe "iZMIR"
        // Arabic and digits are caseless and pass through untouched.
        DrsTextTools.apply(DrsTextTool.TOGGLE_CASE, "مرحبا 123", en) shouldBe "مرحبا 123"
    }

    test("TITLE_CASE keeps apostrophes and hyphens inside the word (real output pin)") {
        // The exception list ('-', '\'', ’, NBSP) means a hyphen does NOT
        // start a word and the letter after it stays lowercase — while the
        // SPACE before "stop" still capitalizes it. The audit's
        // "Don't stop-me-now" guess missed the space; real behavior:
        DrsTextTools.apply(DrsTextTool.TITLE_CASE, "don't stop-me-now", en) shouldBe
            "Don't Stop-me-now"
    }

    test("STRIP_EMOJI strips full sequences and flags, VS16 but not the base, and keeps ZWNJ") {
        // 👍🏽 = U+1F44D + U+1F3FD skin-tone modifier — both in the emoji blocks
        DrsTextTools.apply(DrsTextTool.STRIP_EMOJI, "hi👍🏽!", en) shouldBe "hi!"
        // the flag is two regional indicators, both stripped
        DrsTextTools.apply(DrsTextTool.STRIP_EMOJI, "🇹🇷TR", en) shouldBe "TR"
        // ℹ = U+2139 is OUTSIDE the emoji blocks — only VS16 (U+FE0F) goes
        DrsTextTools.apply(DrsTextTool.STRIP_EMOJI, "ℹ️info", en) shouldBe "ℹinfo"
        // ZWNJ (نصف المسافة) is deliberately not in the strip set
        DrsTextTools.apply(DrsTextTool.STRIP_EMOJI, "نصف\u200Cمسافة", en) shouldBe
            "نصف\u200Cمسافة"
    }

    test("URL_DECODE pins URLDecoder semantics: %41→A, a+b→a b, malformed escapes preserved") {
        DrsTextTools.apply(DrsTextTool.URL_DECODE, "%41", en) shouldBe "A"
        DrsTextTools.apply(DrsTextTool.URL_DECODE, "a+b", en) shouldBe "a b"
        // URLDecoder throws IllegalArgumentException on the malformed "% r"
        // escape → the tool's documented catch path returns the input untouched
        DrsTextTools.apply(DrsTextTool.URL_DECODE, "100% right", en) shouldBe "100% right"
        // A truncated multibyte escape does NOT throw (only an incomplete
        // trailing '%' does): URLDecoder replaces it with U+FFFD — the
        // audit's "unchanged" expectation was wrong, real behavior pinned.
        DrsTextTools.apply(DrsTextTool.URL_DECODE, "%E0%A4", en) shouldBe "\uFFFD"
    }

    test("SPLIT_TO_LINES keeps the trailing empty item; JOIN_LINES skips blank lines") {
        DrsTextTools.apply(DrsTextTool.SPLIT_TO_LINES, "a,b,", en) shouldBe "a\nb\n"
        DrsTextTools.apply(DrsTextTool.JOIN_LINES, "a\n\nb", en) shouldBe "a, b"
        DrsTextTools.apply(DrsTextTool.JOIN_LINES, "a\n\nb", ar) shouldBe "a، b"
    }

    test("countInfo pins the (chars, words, lines) triple") {
        DrsTextTools.countInfo("") shouldBe Triple(0, 0, 0)
        // whitespace-only text: chars ARE counted (the audit's (0,0,1) was
        // wrong on the chars slot — text.length is reported verbatim)
        DrsTextTools.countInfo("   ") shouldBe Triple(3, 0, 1)
        DrsTextTools.countInfo("one") shouldBe Triple(3, 1, 1)
    }

    test("SENTENCE_PER_LINE only breaks where horizontal whitespace follows the ender") {
        // no whitespace after the ender → untouched
        DrsTextTools.apply(DrsTextTool.SENTENCE_PER_LINE, "Hi.Next", en) shouldBe "Hi.Next"
        DrsTextTools.apply(DrsTextTool.SENTENCE_PER_LINE, "Hi. Next", en) shouldBe "Hi.\nNext"
        // the lookahead requires a non-space char after the gap: an ender at
        // the very end stays untouched
        DrsTextTools.apply(DrsTextTool.SENTENCE_PER_LINE, "Next is. ", en) shouldBe "Next is. "
        // idempotent — existing newlines are never re-broken
        DrsTextTools.apply(DrsTextTool.SENTENCE_PER_LINE, "Hi.\nNext", en) shouldBe "Hi.\nNext"
    }
})
