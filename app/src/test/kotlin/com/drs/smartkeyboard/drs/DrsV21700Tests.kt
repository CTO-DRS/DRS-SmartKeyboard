/*
 * Copyright (C) 2026 The DRS Smart Keyboard Project
 */

package com.drs.smartkeyboard.drs

import com.drs.smartkeyboard.drs.ai.DrsArabicLetters
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

/**
 * DRS v2.17.0 «قانون أل الصادق» — contract tests for the revival of the
 * sun/moon-letter engine [DrsArabicLetters] (orphaned since v1.6.0 with
 * zero production callers) through TWO live integrations:
 *
 *  1. **The advisor's R3b rule** — a sun letter fresh after the definite
 *     article asks for its shadda (الشَّ): the one mark the assimilation
 *     law demands, decided by the pure engine, not a hand list. Moon
 *     followers and hamza carriers stay honestly silent, and article-
 *     attached-to-preposition forms (بالش) keep R3's word-position guard.
 *  2. **The whole-text vocalizer's law layer** — an unknown unmarked
 *     definite-article sun token takes the partial-style shadda exactly
 *     like the seed lexicon writes its own known sun words (النَّاس)،
 *     so one text never speaks two styles. Everything the law does not
 *     fix stays byte-identical.
 *
 * Plus the honest engine correction: the moon branch of
 * [DrsArabicLetters.vocalizedArticle] now emits the lam's sukun (الْقمر)
 * instead of the nonstandard fatha-on-lam (الَقمر) — aligned with the
 * live definite-lam advisor rule that has always offered the sukun.
 */
class DrsV21700Tests : FunSpec({

    fun advise(text: String) = DrsHarakatAdvisor.advise(text)
    fun f(text: String) = DrsTextTools.apply(DrsTextTool.TASHKEEL_TEXT, text)

    // ------------------------------------------------------------------
    // 1) المحرك — shaddaForm: طبقة القانون الجزئية
    // ------------------------------------------------------------------

    test("shaddaForm gives an unmarked definite-article sun token its shadda") {
        // the LAW adds the shadda and nothing else — the haraka after it is
        // lexical knowledge the law cannot invent (partial style):
        DrsArabicLetters.shaddaForm("الشرق") shouldBe "الش\u0651رق"
        DrsArabicLetters.shaddaForm("الصبر") shouldBe "الص\u0651بر"
        // sun letter at position 3, mark inserted right after it:
        DrsArabicLetters.shaddaForm("الشمس") shouldBe "الش\u0651مس"
    }

    test("shaddaForm refuses moon followers, hamza carriers and non-article tokens") {
        // moon letters — the law fixes nothing for them:
        DrsArabicLetters.shaddaForm("المغرب").shouldBeNull()
        DrsArabicLetters.shaddaForm("الكتاب").shouldBeNull()
        // hamza carriers are NOT sun (الأمل: lam pronounced, no doubling):
        DrsArabicLetters.shaddaForm("الأمل").shouldBeNull()
        // not a definite-article token at all:
        DrsArabicLetters.shaddaForm("كتاب").shouldBeNull()
        DrsArabicLetters.shaddaForm("ال").shouldBeNull()
        DrsArabicLetters.shaddaForm("").shouldBeNull()
    }

    test("a three-letter article-plus-sun token is already a law token") {
        // lamAssimilation's floor is exactly three chars — the article
        // plus ONE follower is enough for the law to speak:
        DrsArabicLetters.shaddaForm("الش") shouldBe "الش\u0651"
    }

    test("shaddaForm never touches a token that carries any mark") {
        // the engine's own output is a fixed point (idempotency):
        val once = DrsArabicLetters.shaddaForm("الشرق").shouldNotBeNull()
        DrsArabicLetters.shaddaForm(once).shouldBeNull()
        // a partially-marked token is not the engine's to touch:
        DrsArabicLetters.shaddaForm("الشَّرق").shouldBeNull()
        DrsArabicLetters.shaddaForm("السُّكون").shouldBeNull()
    }

    test("the corrected vocalizedArticle gives the moon lam its sukun") {
        // DRS v2.17.0 honest correction — the old fatha-on-lam form
        // (الَقمر) is written by no standard orthography; the lam of the
        // definite article is sakinah in BOTH branches, which is what
        // the live definite-lam advisor rule has always offered:
        DrsArabicLetters.vocalizedArticle("القمر") shouldBe "الْقمر"
        DrsArabicLetters.vocalizedArticle("الكتاب") shouldBe "الْكتاب"
        DrsArabicLetters.vocalizedArticle("الأمل") shouldBe "الْأمل"
        // the sun branch keeps its sukun + shadda:
        DrsArabicLetters.vocalizedArticle("الشمس") shouldBe "الْشّمس"
        // and both branches are fixed points:
        DrsArabicLetters.vocalizedArticle("الْقمر") shouldBe "الْقمر"
        DrsArabicLetters.vocalizedArticle("الْشّمس") shouldBe "الْشّمس"
    }

    // ------------------------------------------------------------------
    // 2) المستشار — R3b: الشمسية الطازجة بعد أل تطلب شدتها
    // ------------------------------------------------------------------

    test("a sun letter fresh after the article asks for its shadda") {
        val picks = advise("الش")
        picks.size shouldBe 1
        picks.first().commit shouldBe DrsHarakat.SHADDA.toString()
        picks.first().reason shouldBe DrsHarakatAdvisor.AdviceReason.DEFINITE_SUN
    }

    test("every one of the fourteen sun letters fires R3b") {
        for (sun in DrsArabicLetters.SUN) {
            val picks = advise("ال$sun")
            picks.size shouldBe 1
            picks.first().reason shouldBe DrsHarakatAdvisor.AdviceReason.DEFINITE_SUN
            picks.first().commit shouldBe DrsHarakat.SHADDA.toString()
        }
    }

    test("moon followers and hamza carriers stay honestly silent") {
        advise("الق").shouldBeEmpty()   // moon — nothing the law fixes here
        advise("الم").shouldBeEmpty()   // moon
        advise("الأ").shouldBeEmpty()   // hamza carrier — not sun
        advise("الإ").shouldBeEmpty()   // hamza carrier
    }

    test("the word-position guard keeps preposition-attached forms silent") {
        // the SAME guard R3 uses: ال glued to a letter is not the article:
        advise("بالش").shouldBeEmpty()
        advise("وللس").shouldBeEmpty()
        advise("فالس").shouldBeEmpty()
    }

    test("R3b nests into the rule cascade without stealing earlier rules") {
        // bare article still gets R3's lam sukun, not R3b:
        val r3 = advise("ال")
        r3.first().reason shouldBe DrsHarakatAdvisor.AdviceReason.DEFINITE_LAM
        // a fresh shadda after ال+ش still asks for its vowel (R2 first):
        val r2 = advise("الشّ")
        r2.first().reason shouldBe DrsHarakatAdvisor.AdviceReason.AFTER_SHADDA
        // a mark under the sun letter is R4's business (replacement):
        val r4 = advise("الشَ")
        r4.first().reason shouldBe DrsHarakatAdvisor.AdviceReason.OVER_MARK
        // a longer typed word ending in a moon letter stays silent:
        advise("الشمس").shouldBeEmpty()
    }

    // ------------------------------------------------------------------
    // 3) الأداة — طبقة القانون في المُشكِّل الكامل
    // ------------------------------------------------------------------

    test("an unknown unmarked sun token takes its law shadda") {
        f("الشرق") shouldBe "الش\u0651رق"
        f("الطائرات") shouldBe "الط\u0651ائرات"
    }

    test("the law layer speaks the seed lexicon's own partial style") {
        // ONE partial style in one line: shadda on the sun letter, lam
        // left bare — the known word keeps its hand-reviewed haraka
        // (النَّاس) while the unknown one carries only what the law fixes:
        f("من الشرق إلى الناس") shouldBe "مِنْ الش\u0651رق إِلَى النَّاس"
    }

    test("moon, hamza and marked unknown tokens stay byte-identical") {
        f("المغرب") shouldBe "المغرب"   // moon — the law fixes nothing
        f("الأمل") shouldBe "الأمل"     // hamza carrier
        f("الْشرق") shouldBe "الْشرق"   // already marked — never a second layer
        f("شمسات") shouldBe "شمسات"     // not an article token
    }

    test("the seed lexicon wins over the law layer («البذرة تفوز»)") {
        // الناس is a known token: its hand-reviewed form is canonical:
        f("الناس") shouldBe "النَّاس"
    }

    test("the vocalizer stays a fixed point over the law layer") {
        val before = "من المغرب إلى الشرق، في 2026"
        val once = f(before)
        val twice = f(once)
        twice shouldBe once
        once shouldNotBe before
    }
})
