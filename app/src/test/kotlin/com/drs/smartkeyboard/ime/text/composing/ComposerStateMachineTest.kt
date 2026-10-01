/*
 * Copyright (C) 2025-2026 The DRS Smart Keyboard Project
 */

package com.drs.smartkeyboard.ime.text.composing

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

/**
 * Pins the pure composing transforms of [KanaUnicode.getActions] and
 * [HangulUnicode.getActions]. Both composers return a pair of
 * `(deleteCount, replacement)`, where `deleteCount = 1` consumes the last
 * composed character of [Composer.getActions]' precedingText and replaces it
 * with the returned string (`deleteCount = 0` only inserts).
 */
class ComposerStateMachineTest : FunSpec({

    context("KanaUnicode.getActions") {
        val kana = KanaUnicode()

        test("dakuten composes ka into ga") {
            // "゙" = U+3099 COMBINING KATAKANA-HIRAGANA VOICED SOUND MARK
            kana.getActions("か", "゙") shouldBe (1 to "が")
        }

        test("dakuten toggles ga back to ka") {
            kana.getActions("が", "゙") shouldBe (1 to "か")
        }

        test("handakuten composes ha into pa") {
            // "ﾟ" = U+FF9F HALFWIDTH KATAKANA SEMI-VOICED SOUND MARK
            kana.getActions("は", "ﾟ") shouldBe (1 to "ぱ")
        }

        test("handakuten toggles pa back to ha") {
            kana.getActions("ぱ", "ﾟ") shouldBe (1 to "は")
        }

        test("handakuten on inapplicable kana passes the mark through") {
            // き has no semi-voiced variant: deleteCount 0, mark inserted as-is.
            kana.getActions("き", "ﾟ") shouldBe (0 to "ﾟ")
        }

        test("small-kana sentinel turns tsu into small tsu") {
            // "〓" = U+3013 GETA MARK — KanaUnicode.smallSentinel
            kana.getActions("つ", "〓") shouldBe (1 to "っ")
        }

        test("small-kana sentinel toggles back") {
            kana.getActions("っ", "〓") shouldBe (1 to "つ")
        }

        test("small-kana sentinel on non-kana text is swallowed") {
            kana.getActions("a", "〓") shouldBe (0 to "")
        }

        test("composing mark without preceding text is swallowed") {
            kana.getActions("", "゙") shouldBe (0 to "")
        }

        test("empty toInsert is a no-op") {
            kana.getActions("か", "") shouldBe (0 to "")
        }

        test("halfwidth katakana has no dakuten composition (pinned limitation)") {
            // "ｶ" = U+FF76 HALFWIDTH KATAKANA LETTER KA,
            // "ﾞ" = U+FF9E HALFWIDTH KATAKANA VOICED SOUND MARK.
            // The daku map only covers full-width katakana, so the mark is
            // passed through unchanged and nothing is deleted.
            kana.getActions("ｶ", "ﾞ") shouldBe (0 to "ﾞ")
        }
    }

    context("HangulUnicode.getActions") {
        val hangul = HangulUnicode()

        test("initial plus medial composes a syllable") {
            hangul.getActions("ㄱ", "ㅏ") shouldBe (1 to "가")
        }

        test("medial plus final composes a syllable") {
            hangul.getActions("가", "ㄴ") shouldBe (1 to "간")
        }

        test("final plus consonant merges into a composed final cluster") {
            // 각 (ㄱ + ㅏ + ㄱ) + ㅅ merges the final ㄱㅅ into the cluster ㄳ → 갃
            hangul.getActions("각", "ㅅ") shouldBe (1 to "갃")
        }

        test("composed final plus medial splits into two syllables") {
            // 갃 (final cluster ㄳ) + ㅏ splits into 각 + 사
            hangul.getActions("갃", "ㅏ") shouldBe (1 to "각사")
        }

        test("simple final plus medial splits into two syllables") {
            // 간 (final ㄴ) + ㅏ splits into 가 + 나
            hangul.getActions("간", "ㅏ") shouldBe (1 to "가나")
        }

        test("medial merge composes a diphthong") {
            // 고 (ㅗ) + ㅏ merges the medial into ㅘ → 과
            hangul.getActions("고", "ㅏ") shouldBe (1 to "과")
        }

        test("underscore sentinel inside a syllable is a no-op") {
            // "_" is the "no final" sentinel in the finals table — never merged.
            hangul.getActions("가", "_") shouldBe (0 to "_")
        }

        test("latin preceding text passes the jamo through") {
            hangul.getActions("a", "ㅏ") shouldBe (0 to "ㅏ")
        }

        test("empty preceding text passes the jamo through") {
            hangul.getActions("", "ㄱ") shouldBe (0 to "ㄱ")
        }

        test("standalone medial jamo merges into the composed medial") {
            // ㅗ + ㅏ → ㅘ (medial + final branch on bare jamo, no syllable yet)
            hangul.getActions("ㅗ", "ㅏ") shouldBe (1 to "ㅘ")
        }

        test("final plus consonant merges into the composed final jamo") {
            // 갑 (final ㅂ) + ㅅ merges into the cluster jamo ㅄ → 값
            // (finalComp only contains ㄱ, ㄴ, ㄹ, ㅂ — ㅁ is NOT mergeable,
            // so "감" + ㅅ would honestly pass through as (0, "ㅅ")).
            hangul.getActions("갑", "ㅅ") shouldBe (1 to "값")
        }
    }
})
