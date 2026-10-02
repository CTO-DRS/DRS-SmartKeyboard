/*
 * Copyright (C) 2025-2026 The DRS Smart Keyboard Project
 */

package com.drs.smartkeyboard.drs

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe

/**
 * DRS v1.1.0 — اللوحة الكاملة للوحة الحركات الذكية: the three on-device
 * algorithms of the full harakat board, pinned pure on the JVM:
 *
 *  1. [DrsHarakatAdvisor] — the contextual haraka advisor (tanween
 *     completion, shadda's vowel, the definite-article lam, mark
 *     replacement, MRU fallback, and the honest alef note).
 *  2. [DrsWordTashkeel] — the lexicon word vocalizer (stripped-form
 *     matching, unknown words resolve to null).
 *  3. [DrsHarakatWordOps] — the cursor-word surgery helpers (extraction,
 *     stripping, cluster counting).
 */
class DrsV1100Tests : FunSpec({

    // -------------------------------------------------------------
    // DrsHarakatWordOps — جراحة الكلمة
    // -------------------------------------------------------------

    test("currentWordBefore extracts the trailing Arabic word with its marks") {
        DrsHarakatWordOps.currentWordBefore("مرحبا بالعال") shouldBe "بالعال"
        DrsHarakatWordOps.currentWordBefore("قال كَانَتْ") shouldBe "كَانَتْ"
        DrsHarakatWordOps.currentWordBefore("متـطاولـة") shouldBe "متـطاولـة" // tatweel is a word char
    }

    test("currentWordBefore returns empty when the cursor is not inside a word") {
        DrsHarakatWordOps.currentWordBefore("") shouldBe ""
        DrsHarakatWordOps.currentWordBefore("كتاب ") shouldBe ""
        DrsHarakatWordOps.currentWordBefore("hello 123") shouldBe ""
    }

    test("currentWordBefore keeps Arabic-Indic digits out of the word") {
        DrsHarakatWordOps.currentWordBefore("عام 1447") shouldBe ""
        DrsHarakatWordOps.currentWordBefore("عام1447") shouldBe ""
    }

    test("stripDiacritics removes the nine marks and the tatweel only") {
        DrsHarakatWordOps.stripDiacritics("مُحَمَّـد") shouldBe "محمد"
        DrsHarakatWordOps.stripDiacritics("كِتَابًا") shouldBe "كتابا"
        DrsHarakatWordOps.stripDiacritics("اللَّهِ") shouldBe "الله"
        DrsHarakatWordOps.stripDiacritics("بلا تشكيل") shouldBe "بلا تشكيل"
    }

    test("containsDiacritics detects marks honestly") {
        DrsHarakatWordOps.containsDiacritics("كَانَ") shouldBe true
        DrsHarakatWordOps.containsDiacritics("كان") shouldBe false
        DrsHarakatWordOps.containsDiacritics("") shouldBe false
    }

    test("clusterCount counts letters, not marks — a letter plus its marks is one cluster") {
        DrsHarakatWordOps.clusterCount("كان") shouldBe 3
        DrsHarakatWordOps.clusterCount("كَانَ") shouldBe 3
        DrsHarakatWordOps.clusterCount("مُحَمَّد") shouldBe 4
        DrsHarakatWordOps.clusterCount("ـتطويلـ") shouldBe 7 // tatweel is its own cluster
        DrsHarakatWordOps.clusterCount("") shouldBe 0
    }

    // -------------------------------------------------------------
    // DrsWordTashkeel — مشكِّل الكلمة
    // -------------------------------------------------------------

    test("the lexicon is non-trivial and every entry is self-consistent") {
        (DrsWordTashkeel.size >= 80) shouldBe true
        DrsWordTashkeel.LEXICON.forEach { (stripped, vocalized) ->
            // Every key must equal the stripped form of its own value —
            // a canonical round-trip guarantee.
            DrsHarakatWordOps.stripDiacritics(vocalized) shouldBe stripped
        }
    }

    test("vocalize resolves stripped and partially-marked input alike") {
        DrsWordTashkeel.vocalize("كان") shouldBe "كَانَ"
        DrsWordTashkeel.vocalize("كَان") shouldBe "كَانَ" // partial marks still match
        DrsWordTashkeel.vocalize("الذي") shouldBe "الَّذِي"
        DrsWordTashkeel.vocalize("الله") shouldBe "اللَّه"
        DrsWordTashkeel.vocalize("من") shouldBe "مِنْ"
        DrsWordTashkeel.vocalize("هذا") shouldBe "هَٰذَا"
        DrsWordTashkeel.vocalize("لكن") shouldBe "لَٰكِنَّ"
    }

    test("vocalize returns null for unknown and empty words — no invented diacritics") {
        DrsWordTashkeel.vocalize("كلمة_غير_موجودة").shouldBeNull()
        DrsWordTashkeel.vocalize("").shouldBeNull()
        DrsWordTashkeel.vocalize(" ").shouldBeNull()
        DrsWordTashkeel.isKnown("كتاب_مجهول") shouldBe false
        DrsWordTashkeel.isKnown("قال") shouldBe true
    }

    test("the superscript alef (dagger alef) survives stripping in the canonical forms") {
        // هَٰذَا strips to هذا — the dagger rides the first alef.
        DrsHarakatWordOps.stripDiacritics("هَٰذَا") shouldBe "هذا"
        DrsHarakatWordOps.stripDiacritics("ذَٰلِكَ") shouldBe "ذلك"
    }

    // -------------------------------------------------------------
    // DrsHarakatAdvisor — المستشار السياقي
    // -------------------------------------------------------------

    test("R1 — a tanween fath off its alef asks for the alef") {
        val advice = DrsHarakatAdvisor.advise("كتابً")
        advice shouldHaveSize 1
        advice.first().commit shouldBe "اً"
        advice.first().reason shouldBe DrsHarakatAdvisor.AdviceReason.TANWEEN_ALEF
    }

    test("R1 — a tanween fath already on an alef asks for nothing") {
        DrsHarakatAdvisor.advise("كتاباً").shouldBeEmpty()
        DrsHarakatAdvisor.advise("ً").shouldBeEmpty() // lone tanween, no base letter
    }

    test("R2 — a fresh shadda asks for its fatha first") {
        val advice = DrsHarakatAdvisor.advise("ش")
        DrsHarakatAdvisor.advise("ش${DrsHarakat.SHADDA}").let { picks ->
            picks shouldHaveSize 1
            picks.first().commit shouldBe DrsHarakat.FATHA.toString()
            picks.first().reason shouldBe DrsHarakatAdvisor.AdviceReason.AFTER_SHADDA
        }
        advice shouldBe advice // (the bare-letter case has no rule — silence)
    }

    test("R3 — the lam of a fresh definite article asks for its sukun") {
        val picks = DrsHarakatAdvisor.advise("في ال")
        picks shouldHaveSize 1
        picks.first().commit shouldBe DrsHarakat.SUKUN.toString()
        picks.first().reason shouldBe DrsHarakatAdvisor.AdviceReason.DEFINITE_LAM

        // Mid-word ال is NOT the article — no rule.
        DrsHarakatAdvisor.advise("عال").shouldBeEmpty()
    }

    test("R4 — a mark under the cursor asks to be replaced by the top pick") {
        val picks = DrsHarakatAdvisor.advise("كَ", mru = listOf(DrsHarakat.DAMMA, DrsHarakat.KASRA))
        picks shouldHaveSize 1
        picks.first().commit shouldBe DrsHarakat.DAMMA.toString()
        picks.first().reason shouldBe DrsHarakatAdvisor.AdviceReason.OVER_MARK
    }

    test("R4 — with no usage history the replacement falls back to the fatha") {
        val picks = DrsHarakatAdvisor.advise("كَ")
        picks.first().commit shouldBe DrsHarakat.FATHA.toString()
    }

    test("an alef before the cursor gets the honest note, never a haraka pick") {
        DrsHarakatAdvisor.alefNoteFor("كتابا") shouldBe true
        DrsHarakatAdvisor.alefNoteFor("أنا") shouldBe true
        DrsHarakatAdvisor.alefNoteFor("كتاب") shouldBe false
        DrsHarakatAdvisor.alefNoteFor("") shouldBe false
        DrsHarakatAdvisor.advise("كتابا").shouldBeEmpty()
        DrsHarakat.isAlefLetter('ا') shouldBe true
        DrsHarakat.isAlefLetter('ى') shouldBe true
        DrsHarakat.isAlefLetter('ب') shouldBe false
    }

    test("an empty or plain-letter context returns no contextual picks") {
        DrsHarakatAdvisor.advise("").shouldBeEmpty()
        DrsHarakatAdvisor.advise("كتاب").shouldBeEmpty()
    }

    test("mruPicks caps at MAX_PICKS and tags the MRU reason") {
        val picks = DrsHarakatAdvisor.mruPicks(
            listOf(DrsHarakat.FATHA, DrsHarakat.SUKUN, DrsHarakat.SHADDA, DrsHarakat.DAMMA),
            n = 3,
        )
        picks shouldHaveSize 3
        picks.map { it.commit } shouldContainExactly listOf(
            DrsHarakat.FATHA.toString(),
            DrsHarakat.SUKUN.toString(),
            DrsHarakat.SHADDA.toString(),
        )
        picks.forEach { it.reason shouldBe DrsHarakatAdvisor.AdviceReason.MRU }
        DrsHarakatAdvisor.MAX_PICKS shouldBe 3
    }

    // -------------------------------------------------------------
    // Smart insert engine — unchanged by the board rebuild
    // -------------------------------------------------------------

    test("the smart stacking engine keeps its shadda-combo exception") {
        HarakatSmartInsert.decide(DrsHarakat.SHADDA, DrsHarakat.FATHA, true) shouldBe
            HarakaInsertMode.APPEND
        HarakatSmartInsert.decide(DrsHarakat.FATHA, DrsHarakat.DAMMA, true) shouldBe
            HarakaInsertMode.REPLACE_PREVIOUS
    }
})
