/*
 * Copyright (C) 2026 The DRS Smart Keyboard Project
 */

package com.drs.smartkeyboard.drs

import com.drs.smartkeyboard.drs.ai.DrsArabicCorrector
import com.drs.smartkeyboard.drs.ai.DrsArabicLetters
import com.drs.smartkeyboard.drs.ai.DrsArabicMorphology
import com.drs.smartkeyboard.drs.ai.LatinNormBridge
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

/**
 * الإصدار العام v1.6.0 — التوسعة الأولى للخارطة: الذكاء العربي الأعمق.
 *
 * Two engines grow in this release, both pure-deterministic and both
 * contract-tested here:
 *
 *  1. [DrsArabicMorphology] gains FIVE documented weights (15 → 20):
 *     تفعّل، فعّيل، فعّول، مفعَل، مُفاعلة. The additions carry a collision
 *     audit as tests: تابع stays فاعل (root تبع) and تراب stays فعّال
 *     (root ترب) — the template constants disambiguate the shared shapes.
 *
 *  2. [DrsArabicLetters] is the new sun/moon classification
 *     (الشمسية والقمرية): exhaustive and disjoint over the 28 base
 *     letters, honest NOT_DEFINITE for anything that is not a definite
 *     article, and a vocalized-article helper that never invents a mark
 *     on a non-article token.
 */
class DrsPublicV160Tests : FunSpec({

    // تعريفات محلية داخل جسم المواصفة — Kotest FunSpec lambda.
    val morph = DrsArabicMorphology

    // -------------------------------------------------------------
    // الأوزان الخمسة الجديدة — the five new weights (v1.6.0)
    // -------------------------------------------------------------

    test("تفعّل: تدرس strips the morphological ت and finds root درس") {
        val a = morph.analyze("تدرس")
        a.pattern shouldBe "تفعل"
        a.root shouldBe "درس"
        a.prefixes shouldBe emptyList()
        a.core shouldBe "تدرس"
    }

    test("تفعّل: تقدم and تكلم follow the same family") {
        morph.analyze("تقدم").root shouldBe "قدم"
        morph.analyze("تكلم").root shouldBe "كلم"
    }

    test("the collision guard: تابع is فاعل, never eaten by تفعل") {
        // ت + ا ب ع: the ف constant at index 1 of تفعل rejects it (ا ≠ ف),
        // while فاعل — earlier in the table — takes it: root تبع.
        val a = morph.analyze("تابع")
        a.pattern shouldBe "فاعل"
        a.root shouldBe "تبع"
    }

    test("the collision guard: تراب is فعّال (root ترب), not تفعل") {
        // فعال sits before تفعل in the table, so the ا at index 2 wins.
        val a = morph.analyze("تراب")
        a.pattern shouldBe "فعال"
        a.root shouldBe "ترب"
    }

    test("فعّيل: جميل and سعيد carry the long-vowel constant at index 2") {
        val a = morph.analyze("جميل")
        a.pattern shouldBe "فعيل"
        a.root shouldBe "جمل"
        morph.analyze("سعيد").root shouldBe "سعد"
        morph.analyze("بديل").root shouldBe "بدل"
    }

    test("فعّول: قعود and هموم follow the و family") {
        val a = morph.analyze("قعود")
        a.pattern shouldBe "فعول"
        a.root shouldBe "قعد"
        morph.analyze("هموم").root shouldBe "همم"
    }

    test("مفعَل: ملعب and مسجد — اسم المكان finds its root") {
        val a = morph.analyze("ملعب")
        a.pattern shouldBe "مفعل"
        a.root shouldBe "لعب"
        morph.analyze("مسجد").root shouldBe "سجد"
        morph.analyze("مدرس").root shouldBe "درس"
    }

    test("مُفاعلة: مصاحبة roots صحب and the lemma strips the ة") {
        val a = morph.analyze("مصاحبة")
        a.pattern shouldBe "مفاعلة"
        a.root shouldBe "صحب"
        a.lemma shouldBe "مصاحب"
    }

    test("مُفاعلة: the ة/ت rotation inherited from مفعلة") {
        val a = morph.analyze("مصاحبت")
        a.pattern shouldBe "مفاعلة"
        a.root shouldBe "صحب"
    }

    test("the new weights compose with affixes: بالتدريس keeps its core") {
        val a = morph.analyze("بالتدريس")
        a.prefixes shouldBe listOf("بال")
        a.core shouldBe "تدريس"
        a.root shouldBe "درس"
        a.pattern shouldBe "تفعيل"
    }

    test("the established contracts are untouched by the expansion") {
        // The v1.0-era pins that any careless weight would break:
        morph.analyze("كتب").root shouldBe "كتب"
        morph.analyze("كاتب").pattern shouldBe "فاعل"
        morph.analyze("كتاب").root shouldBe "كتب"
        morph.analyze("مكتوب").pattern shouldBe "مفعول"
        morph.analyze("مدرسة").lemma shouldBe "مدرس"
        morph.analyze("الكتاب").prefixes shouldBe listOf("ال")
    }

    test("the LRU cache serves the SAME instance for the new families too") {
        val first = morph.analyze("تدرس")
        val second = morph.analyze("تدرس")
        (first === second) shouldBe true
    }

    // -------------------------------------------------------------
    // الشمسية والقمرية — the sun/moon classification
    // -------------------------------------------------------------

    test("the 28 base letters are classified EXHAUSTIVELY and DISJOINTLY") {
        val base = "ابتثجحخدذرزسشصضطظعغفقكلمنهوي"
        base.forEach { ch ->
            val sun = DrsArabicLetters.isSun(ch)
            val moon = DrsArabicLetters.isMoon(ch)
            (sun xor moon) shouldBe true
        }
        // Exactly fourteen of each, no overlap, no strays:
        DrsArabicLetters.SUN.size shouldBe 14
        DrsArabicLetters.MOON.size shouldBe 14
        (DrsArabicLetters.SUN intersect DrsArabicLetters.MOON).isEmpty() shouldBe true
    }

    test("the ta-marbuta and the bare hamza refuse to be classified") {
        (DrsArabicLetters.isSun('ة') || DrsArabicLetters.isMoon('ة')) shouldBe false
        (DrsArabicLetters.isSun('ء') || DrsArabicLetters.isMoon('ء')) shouldBe false
    }

    test("الشمس assimilates: the lam goes silent before the sun letter") {
        DrsArabicLetters.lamAssimilation("الشمس") shouldBe DrsArabicLetters.LamAssimilation.ASSIMILATED
        DrsArabicLetters.lamAssimilation("النور") shouldBe DrsArabicLetters.LamAssimilation.ASSIMILATED
        DrsArabicLetters.lamAssimilation("الصبر") shouldBe DrsArabicLetters.LamAssimilation.ASSIMILATED
    }

    test("القمر stays distinct: the lam is pronounced before the moon letter") {
        DrsArabicLetters.lamAssimilation("القمر") shouldBe DrsArabicLetters.LamAssimilation.DISTINCT
        DrsArabicLetters.lamAssimilation("الكتاب") shouldBe DrsArabicLetters.LamAssimilation.DISTINCT
        DrsArabicLetters.lamAssimilation("الأمل") shouldBe DrsArabicLetters.LamAssimilation.DISTINCT
    }

    test("a token without the article is honestly NOT_DEFINITE") {
        DrsArabicLetters.lamAssimilation("كتاب") shouldBe DrsArabicLetters.LamAssimilation.NOT_DEFINITE
        DrsArabicLetters.lamAssimilation("ال") shouldBe DrsArabicLetters.LamAssimilation.NOT_DEFINITE
        DrsArabicLetters.lamAssimilation("") shouldBe DrsArabicLetters.LamAssimilation.NOT_DEFINITE
        DrsArabicLetters.lamAssimilation("في") shouldBe DrsArabicLetters.LamAssimilation.NOT_DEFINITE
    }

    test("the vocalized article marks sun tokens with sukun+shadda") {
        // الشمس → الْ + شّ + مس : the lam is silent, the sun letter doubles.
        DrsArabicLetters.vocalizedArticle("الشمس") shouldBe "الْشّمس"
        DrsArabicLetters.vocalizedArticle("النور") shouldBe "الْنّور"
    }

    test("the vocalized article marks non-sun tokens with fatha on the lam") {
        DrsArabicLetters.vocalizedArticle("القمر") shouldBe "الَقمر"
        DrsArabicLetters.vocalizedArticle("الكتاب") shouldBe "الَكتاب"
        // The hamza carriers are NOT sun — the lam stays pronounced:
        DrsArabicLetters.vocalizedArticle("الأمل") shouldBe "الَأمل"
    }

    test("non-article and already-vocalized tokens pass through byte-identical") {
        DrsArabicLetters.vocalizedArticle("كتاب") shouldBe "كتاب"
        DrsArabicLetters.vocalizedArticle("") shouldBe ""
        // Once the marks are in place the token is a fixed point —
        // the engine never invents a second layer of marks:
        val marked = DrsArabicLetters.vocalizedArticle("الشمس")
        DrsArabicLetters.vocalizedArticle(marked) shouldBe marked
        marked shouldNotBe "الشمس"
    }

    // -------------------------------------------------------------
    // الأرقام بالكلمات — the NUMBER_WORDS tool (v1.6.0)
    // -------------------------------------------------------------

    fun numberWords(text: String) = DrsTextTools.apply(DrsTextTool.NUMBER_WORDS, text)

    test("NUMBER_WORDS registers at -659 in the catalogue") {
        val tool = DrsTextTool.NUMBER_WORDS
        tool.code shouldBe -659
        DrsTextTool.fromCode(-659) shouldBe tool
        (tool.code in DrsTextTool.CODE_RANGE) shouldBe true
        // 52 tools through v1.4.0 (public) + the number-to-words tool.
        DrsTextTool.entries.size shouldBe 54
        (tool.isInfoOnly) shouldBe false
        (tool.isEditorOp) shouldBe false
        (tool.isInsertMark) shouldBe false
    }

    test("small integers speak their words: units, teens, tens, hundreds") {
        numberWords("0") shouldBe "صفر"
        numberWords("5") shouldBe "خمسة"
        numberWords("10") shouldBe "عشرة"
        numberWords("11") shouldBe "أحد عشر"
        numberWords("12") shouldBe "اثنا عشر"
        numberWords("21") shouldBe "واحد وعشرون"
        numberWords("99") shouldBe "تسعة وتسعون"
        numberWords("100") shouldBe "مائة"
        numberWords("101") shouldBe "مائة وواحد"
        numberWords("200") shouldBe "مائتان"
        numberWords("345") shouldBe "ثلاثمائة وخمسة وأربعون"
    }

    test("scales follow the documented selection: singular, dual, plural") {
        numberWords("1000") shouldBe "ألف"
        numberWords("2000") shouldBe "ألفان"
        numberWords("3000") shouldBe "ثلاثة آلاف"
        numberWords("10000") shouldBe "عشرة آلاف"
        numberWords("11000") shouldBe "أحد عشر ألف"
        numberWords("2100") shouldBe "ألفان ومائة"
        numberWords("1234") shouldBe "ألف ومائتان وأربعة وثلاثون"
    }

    test("millions and billions compose group by group with the prefixed waw") {
        numberWords("1000000") shouldBe "مليون"
        numberWords("2000000") shouldBe "مليونان"
        numberWords("3500000") shouldBe "ثلاثة ملايين وخمسمائة ألف"
        numberWords("1234567") shouldBe "مليون ومائتان وأربعة وثلاثون ألف وخمسمائة وسبعة وستون"
        numberWords("999999999999") shouldBe
            "تسعمائة وتسعة وتسعون مليار وتسعمائة وتسعة وتسعون مليون" +
            " وتسعمائة وتسعة وتسعون ألف وتسعمائة وتسعة وتسعون"
    }

    test("Arabic-Indic and Extended digits parse like Western digits") {
        numberWords("١٢٣") shouldBe "مائة وثلاثة وعشرون"
        numberWords("١٠٠") shouldBe "مائة"
        numberWords("۵۰") shouldBe "خمسون"
    }

    test("a negative number keeps its sign as the word سالب") {
        numberWords("-5") shouldBe "سالب خمسة"
    }

    test("surrounding whitespace is tolerated, content is not") {
        numberWords(" 5 ") shouldBe "خمسة"
    }

    test("anything that is not a clean integer stays byte-identical") {
        numberWords("عام 2026") shouldBe "عام 2026"
        numberWords("12.5") shouldBe "12.5"
        numberWords("12\n34") shouldBe "12\n34"
        numberWords("") shouldBe ""
        numberWords("1234567890123") shouldBe "1234567890123"
        numberWords("-") shouldBe "-"
    }

    test("the words output is itself a fixed point (no digits to re-render)") {
        val once = numberWords("1234")
        numberWords(once) shouldBe once
    }

    // -------------------------------------------------------------
    // التصحيحات الصلبة الموسّعة — the v1.6.0 hard-correction batch
    // -------------------------------------------------------------

    test("the twelve universal orthographic fixes answer on the normalized form") {
        val norm = LatinNormBridge::basicNormalize
        DrsArabicCorrector.hardCorrectionFor("انشاءالله", norm) shouldBe "إن شاء الله"
        DrsArabicCorrector.hardCorrectionFor("بأذن الله", norm) shouldBe "بإذن الله"
        DrsArabicCorrector.hardCorrectionFor("اذن", norm) shouldBe "إذن"
        DrsArabicCorrector.hardCorrectionFor("الان", norm) shouldBe "الآن"
        DrsArabicCorrector.hardCorrectionFor("اكثر", norm) shouldBe "أكثر"
        DrsArabicCorrector.hardCorrectionFor("اقل", norm) shouldBe "أقل"
        DrsArabicCorrector.hardCorrectionFor("اولئك", norm) shouldBe "أولئك"
        DrsArabicCorrector.hardCorrectionFor("مسئولية", norm) shouldBe "مسؤولية"
        DrsArabicCorrector.hardCorrectionFor("مسئولة", norm) shouldBe "مسؤولة"
        DrsArabicCorrector.hardCorrectionFor("مسئلة", norm) shouldBe "مسألة"
        DrsArabicCorrector.hardCorrectionFor("تسئولات", norm) shouldBe "تساؤلات"
        DrsArabicCorrector.hardCorrectionFor("شئون", norm) shouldBe "شؤون"
    }

    test("correct spellings and ordinary words stay outside the table") {
        val norm = LatinNormBridge::basicNormalize
        DrsArabicCorrector.hardCorrectionFor("مسألة", norm) shouldBe null
        DrsArabicCorrector.hardCorrectionFor("مدرسة", norm) shouldBe null
        DrsArabicCorrector.hardCorrectionFor("", norm) shouldBe null
    }
})
