/*
 * Copyright (C) 2026 The DRS Smart Keyboard Project
 */

package com.drs.smartkeyboard.ime.nlp.latin

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

/**
 * DRS p6 (A2): JVM contract tests for the transposition-aware edit-distance
 * gate used by the "did you mean?" corrections filter.
 *
 * TEST HONESTY ROUND notes (per the worklog):
 *  - The substitution cases (catt/cart, kitten/sitten) were ALREADY accepted
 *    by the pre-A2 transposition-unaware check — they are kept as regression
 *    guards, not claimed as new A2 behavior.
 *  - "thier" -> "their" and the other adjacent-swap cases are the NEW A2
 *    behavior: the correction index collected swapped-pair candidates all
 *    along (delete-1 keys collide), the old filter simply rejected them.
 */
class LatinCorrectionDistanceTest : FunSpec({
    context("zero-edit and single-edit classes (regression guards, pre-A2 behavior)") {
        test("identical strings are at distance zero") {
            isEditDistanceAtMostOneWithTransposition("their", "their") shouldBe true
        }

        test("empty vs single character is one insertion") {
            isEditDistanceAtMostOneWithTransposition("", "a") shouldBe true
        }

        test("single character vs empty is one deletion") {
            isEditDistanceAtMostOneWithTransposition("a", "") shouldBe true
        }

        test("suffix deletion (their -> thei) is one edit") {
            isEditDistanceAtMostOneWithTransposition("their", "thei") shouldBe true
        }

        test("middle deletion (abcde -> abde) is one edit") {
            isEditDistanceAtMostOneWithTransposition("abcde", "abde") shouldBe true
        }

        // HONESTY: this is a plain SUBSTITUTION — accepted by the old
        // transposition-unaware check too (worklog TEST HONESTY ROUND).
        test("mid-word substitution (catt -> cart) is one edit") {
            isEditDistanceAtMostOneWithTransposition("catt", "cart") shouldBe true
        }

        // HONESTY: plain first-character SUBSTITUTION — pre-A2 behavior.
        test("first-character substitution (kitten -> sitten) is one edit") {
            isEditDistanceAtMostOneWithTransposition("kitten", "sitten") shouldBe true
        }

        test("two substitutions (kitten -> sissen) exceed one edit") {
            isEditDistanceAtMostOneWithTransposition("kitten", "sissen") shouldBe false
        }

        test("length gap of two (abc vs a) is rejected outright") {
            isEditDistanceAtMostOneWithTransposition("abc", "a") shouldBe false
        }
    }

    context("adjacent transposition counts as THE single edit (NEW with A2)") {
        test("headline case: thier -> their") {
            isEditDistanceAtMostOneWithTransposition("thier", "their") shouldBe true
        }

        test("recieve -> receive") {
            isEditDistanceAtMostOneWithTransposition("recieve", "receive") shouldBe true
        }

        test("hte -> the") {
            isEditDistanceAtMostOneWithTransposition("hte", "the") shouldBe true
        }

        test("two-character pair swap (ab -> ba)") {
            isEditDistanceAtMostOneWithTransposition("ab", "ba") shouldBe true
        }

        test("mid-word transposition (abcd -> abdc)") {
            isEditDistanceAtMostOneWithTransposition("abcd", "abdc") shouldBe true
        }
    }

    context("a transposition plus ANY further edit is two edits") {
        test("transposition + trailing extra character (thiers vs their)") {
            isEditDistanceAtMostOneWithTransposition("thiers", "their") shouldBe false
        }

        test("two transpositions (abcd -> badc)") {
            isEditDistanceAtMostOneWithTransposition("abcd", "badc") shouldBe false
        }

        test("transposition + insertion (ab vs bax)") {
            isEditDistanceAtMostOneWithTransposition("ab", "bax") shouldBe false
        }

        test("non-adjacent swap (abcd -> adcb) is not a single transposition") {
            isEditDistanceAtMostOneWithTransposition("abcd", "adcb") shouldBe false
        }
    }
})
