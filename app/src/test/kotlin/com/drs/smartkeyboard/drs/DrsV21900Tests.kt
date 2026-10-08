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

/**
 * DRS v2.19.0 «العقد الخاص» — the glued-particle contract v2.17.0
 * explicitly deferred («الملتصقة بحروف الجر صامتة حتى يأتي قرارها بعقد
 * خاص»), now DECIDED by determinism, not by omission:
 *
 *  1. **The collapsed form لل is CERTIFIED** — ل + ال is written لل with
 *     the alif dropped by orthographic law, and every token opening with
 *     لل + letter IS the preposition + the article with no second
 *     reading (للناس، للشمس، للدار). The sun/moon law belongs to the
 *     article's lam wherever it stands, so a sun follower after لل takes
 *     the same partial-style shadda as after the bare article: via the
 *     engine's new [DrsArabicLetters.gluedShaddaForm] in the law layer,
 *     and via the advisor's R3c rule while typing — one law, one chip,
 *     no new strings (DEFINITE_SUN is reused).
 *  2. **The other glued shapes stay silent BY PROOF** — [particle + ال +
 *     sun] tokens (بالش فالش كالش والش) share their exact shape with real
 *     non-article words: والد/والدين is simultaneously و + الدِّين and
 *     the noun والدِين; بالطو is a single loanword. Separating them needs
 *     dictionary knowledge — the guessing territory the creed forbids.
 *     The deferral «صامتة حتى يأتي قرارها» is hereby resolved into a
 *     reasoned silence, PINNED by tests so no future round can revive
 *     the shape without confronting the proof.
 *
 * The seed lexicon itself confirms the style: it writes its known glued
 * sun word as لِلزَّوْج — shadda on the sun letter and nothing else.
 */
class DrsV21900Tests : FunSpec({

    fun advise(text: String) = DrsHarakatAdvisor.advise(text)
    fun f(text: String) = DrsTextTools.apply(DrsTextTool.TASHKEEL_TEXT, text)

    // ------------------------------------------------------------------
    // 1) المحرك — gluedShaddaForm: شهادة للّ المنحلة
    // ------------------------------------------------------------------

    test("the collapsed لل takes the sun follower's shadda") {
        // the law adds the shadda and nothing else — partial style,
        // the same style the seed lexicon writes for لِلزَّوْج:
        DrsArabicLetters.gluedShaddaForm("للناس") shouldBe "للن\u0651اس"
        DrsArabicLetters.gluedShaddaForm("للصبر") shouldBe "للص\u0651بر"
        DrsArabicLetters.gluedShaddaForm("للدار") shouldBe "للد\u0651ار"
    }

    test("every one of the fourteen sun letters fires after لل") {
        for (sun in DrsArabicLetters.SUN) {
            DrsArabicLetters.gluedShaddaForm("لل$sun") shouldBe "لل$sun\u0651"
        }
    }

    test("gluedShaddaForm refuses moon followers, hamza carriers and non-لل tokens") {
        // moon letters — the law fixes nothing for them:
        DrsArabicLetters.gluedShaddaForm("للقمر").shouldBeNull()
        DrsArabicLetters.gluedShaddaForm("للكتاب").shouldBeNull()
        // hamza carriers are NOT sun (للأمل: lam pronounced, no doubling):
        DrsArabicLetters.gluedShaddaForm("للأمل").shouldBeNull()
        // the bare لل itself has no follower to classify:
        DrsArabicLetters.gluedShaddaForm("لل").shouldBeNull()
        // the bare-article shape belongs to shaddaForm, not this contract:
        DrsArabicLetters.gluedShaddaForm("الشمس").shouldBeNull()
        // the PROOF shapes — real words, no article inside, never touched:
        DrsArabicLetters.gluedShaddaForm("والد").shouldBeNull()
        DrsArabicLetters.gluedShaddaForm("بالش").shouldBeNull()
        DrsArabicLetters.gluedShaddaForm("بالطو").shouldBeNull()
    }

    test("gluedShaddaForm is idempotent and refuses already-marked tokens") {
        // a token carrying any mark is not the engine's to touch:
        DrsArabicLetters.gluedShaddaForm("للش\u0651مس").shouldBeNull()
        DrsArabicLetters.gluedShaddaForm("للشَمس").shouldBeNull()
        // the law output carries a mark — a second call refuses it:
        val once = DrsArabicLetters.gluedShaddaForm("للصبر")
        once.shouldNotBeNull()
        DrsArabicLetters.gluedShaddaForm(once).shouldBeNull()
    }

    // ------------------------------------------------------------------
    // 2) المستشار — R3c: الشمسية الطازجة بعد للّ تطلب شدتها
    // ------------------------------------------------------------------

    test("a sun letter fresh after لل asks for its shadda") {
        val picks = advise("للش")
        picks.size shouldBe 1
        picks.first().commit shouldBe DrsHarakat.SHADDA.toString()
        // ONE law, ONE chip — the bare-article reason is reused, no new string:
        picks.first().reason shouldBe DrsHarakatAdvisor.AdviceReason.DEFINITE_SUN
    }

    test("every one of the fourteen sun letters fires R3c") {
        for (sun in DrsArabicLetters.SUN) {
            val picks = advise("لل$sun")
            picks.size shouldBe 1
            picks.first().reason shouldBe DrsHarakatAdvisor.AdviceReason.DEFINITE_SUN
            picks.first().commit shouldBe DrsHarakat.SHADDA.toString()
        }
    }

    test("R3c fires only when لل opens a fresh token") {
        // a boundary before the double lam (space) keeps the law:
        advise("و للش").size shouldBe 1
        // an Arabic letter before it means a longer token the law does not own:
        advise("خلالش").shouldBeEmpty()
        advise("وللس").shouldBeEmpty()
        advise("اللش").shouldBeEmpty()
        advise("لللش").shouldBeEmpty()
    }

    test("moon followers and hamza carriers stay honestly silent after لل") {
        advise("للق").shouldBeEmpty()
        advise("للم").shouldBeEmpty()
        advise("للأ").shouldBeEmpty()
        // the bare double lam offers nothing yet:
        advise("لل").shouldBeEmpty()
    }

    // ------------------------------------------------------------------
    // 3) الدليل المعلَّل — أشكال [حرف + ال + شمسية] تبقى حرفية
    // ------------------------------------------------------------------

    test("the proof: particle+article+sun shapes are byte-identical in the law layer") {
        // والد is simultaneously و + الدِّين and the noun والدِين —
        // deciding needs dictionary knowledge, the forbidden ground.
        // The honest verdict: the tool never invents what it cannot prove:
        f("والد") shouldBe "والد"
        f("والدين") shouldBe "والدين"
        f("والش") shouldBe "والش"
        f("بالش") shouldBe "بالش"
        f("فالش") shouldBe "فالش"
        f("كالش") shouldBe "كالش"
        f("بالطو") shouldBe "بالطو"
    }

    test("the proof: the advisor stays silent on the same shapes while typing") {
        advise("والد").shouldBeEmpty()
        advise("بالش").shouldBeEmpty()
        advise("فالش").shouldBeEmpty()
        advise("كالش").shouldBeEmpty()
        advise("والش").shouldBeEmpty()
    }

    // ------------------------------------------------------------------
    // 4) الأداة — للّ في طبقة القانون بالنص الكامل
    // ------------------------------------------------------------------

    test("an unknown unmarked glued sun token takes its law shadda in text") {
        f("للصبر للدار") shouldBe "للص\u0651بر للد\u0651ار"
        // moon glued tokens pass through byte-identical:
        f("للقمر") shouldBe "للقمر"
        // punctuation and spacing survive verbatim:
        f("«للصبر»!") shouldBe "«للص\u0651بر»!"
    }

    test("the seed lexicon wins over the glued law (البذرة تفوز)") {
        // In pure-JVM tests the lexicon starts empty (the asset loads on a
        // background thread in the app process) — so the seed precedence is
        // proven by installing the REAL seed rows for the two glued tokens,
        // parsed through the production parser (format contract honored).
        // The law layer must consult the lexicon BEFORE applying the law:
        DrsTashkeelLexicon.install(
            DrsTashkeelLexicon.parse(
                listOf(
                    // verbatim rows from app/src/main/assets/drs/tashkeel_lexicon.txt:
                    "للزوج\tلِلزَّوْج",
                    "للقاضي\tلِلْقَاضِي",
                ),
            ).entries,
        )
        // hand-reviewed forms stay untouched by the law:
        f("للزوج") shouldBe "لِلزَّوْج"
        f("للقاضي") shouldBe "لِلْقَاضِي"
        // ...while an unknown glued sun token right next to them still
        // takes the law shadda (the lexicon first, the law after):
        f("للصبر") shouldBe "للص\u0651بر"
    }

    test("the glued layer is idempotent on its own output") {
        val once = f("للصبر")
        f(once) shouldBe once
    }

    // ------------------------------------------------------------------
    // 5) الشلال — R3c لا يسرق قاعدةً قبله ولا بعده
    // ------------------------------------------------------------------

    test("R3c nests into the rule cascade without stealing earlier rules") {
        // the bare article still gets R3's lam sukun:
        advise("ال").first().reason shouldBe DrsHarakatAdvisor.AdviceReason.DEFINITE_LAM
        // R3b still owns the bare-article sun form:
        advise("الش").first().reason shouldBe DrsHarakatAdvisor.AdviceReason.DEFINITE_SUN
        // a fresh shadda after لل+ش still asks for its vowel (R2 first):
        advise("للشّ").first().reason shouldBe DrsHarakatAdvisor.AdviceReason.AFTER_SHADDA
        // a mark under the follower is R4's business (replacement):
        advise("للشَ").first().reason shouldBe DrsHarakatAdvisor.AdviceReason.OVER_MARK
        // a longer typed word ending in a moon letter stays silent:
        advise("للشمس").shouldBeEmpty()
    }
})
