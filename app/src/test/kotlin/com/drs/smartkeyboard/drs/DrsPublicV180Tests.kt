/*
 * Copyright (C) 2026 The DRS Smart Keyboard Project
 */

package com.drs.smartkeyboard.drs

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

/**
 * الإصدار العام v1.8.0 — التوسعة الثالثة: التاريخ بالكلمات.
 *
 * الثلاثية تكتمل: v1.6.0 جعلت الأعداد تتكلم، v1.7.0 جعلت المبالغ
 * تتكلم، وv1.8.0 يجعل التواريخ تتكلم — «2026-10-03» تصير «الثالث من
 * أكتوبر عام ألفين وستة وعشرين» — العبارة الوثائقية الرسمية.
 *
 * العقود المثبتة هنا:
 *  - امتداد الحالة الإعرابية في [DrsNumberWords]: الافتراضي مرفوع
 *    (عقد v1.6.0 حرفيًا كما هو)، والمجرور المكتوب خمسة أسر (اثنين،
 *    اثني عشر، عشرين..تسعين، مائتين، ألفين) — تُثبَّت في الاتجاهين.
 *  - محلل [DrsDateWords] المغلق: ثلاث مكونات بفاصل واحد متسق من
 *    {- / .}، والسنة حيث يقوم المكوّن الرباعي (الأول = ISO، الأخير =
 *    العرف الوثائقي يوم-شهر-سنة)، والتحقق الميلادي البروليبتيك
 *    (كبيسة 2024 نعم، 1900 لا، فبراير 29 فقط في الكبيسة)، وأرقام
 *    ثلاثة أنظمة حتى المختلطة.
 *  - جدول الأيام المجرور المغلق (31 مدخلًا): الأول..العاشر، الحادي
 *    عشر، العشرين، الحادي والعشرين، الثلاثين، الحادي والثلاثين.
 *  - الفشل الصادق: شهر 13، يوم 32، سنة ثنائية الخانات، سنتان رباعيتان،
 *    فواصل مختلطة، حروف، سالب — كلها لا شيء والأداة تمرر بايتيًا.
 *  - نقطة ثابتة: المخرج بلا فاصل فإعادة التطبيق لا تغير حرفًا.
 */
class DrsPublicV180Tests : FunSpec({

    // تعريفات محلية داخل جسم المواصفة — Kotest FunSpec lambda (بلا private).
    fun words(text: String): String? = DrsNumberWords.arabicWordsOrNull(text)
    fun wordsGen(text: String): String? =
        DrsNumberWords.arabicWordsOrNull(text, DrsNumberWords.Case.GENITIVE)
    fun date(text: String): String? = DrsDateWords.dateWordsOrNull(text)

    // -------------------------------------------------------------
    // امتداد الحالة الإعرابية — the case-aware number words (v1.8.0)
    // -------------------------------------------------------------

    test("the v1.6.0 nominative contract is byte-identical — the default case") {
        words("2026") shouldBe "ألفان وستة وعشرون"
        words("2000") shouldBe "ألفان"
        words("2002") shouldBe "ألفان واثنان"
        words("2012") shouldBe "ألفان واثنا عشر"
        words("2020") shouldBe "ألفان وعشرون"
        words("2200") shouldBe "ألفان ومائتان"
        words("1945") shouldBe "ألف وتسعمائة وخمسة وأربعون"
        words("0") shouldBe "صفر"
    }

    test("the genitive inflects exactly five written families") {
        wordsGen("2026") shouldBe "ألفين وستة وعشرين" // ألفان→ألفين، وعشرون→وعشرين
        wordsGen("2000") shouldBe "ألفين"
        wordsGen("2002") shouldBe "ألفين واثنين" // اثنان→اثنين
        wordsGen("2012") shouldBe "ألفين واثني عشر" // اثنا عشر→اثني عشر
        wordsGen("2020") shouldBe "ألفين وعشرين"
        wordsGen("2200") shouldBe "ألفين ومائتين" // مائتان→مائتين
        wordsGen("1945") shouldBe "ألف وتسعمائة وخمسة وأربعين"
        wordsGen("2112") shouldBe "ألفين ومائة واثني عشر"
        wordsGen("0") shouldBe "صفر"
    }

    test("the oblique families never leak into the nominative and vice versa") {
        // كل عائلة مجرورة يقابلها مرفوعة حرفيًا.
        wordsGen("120") shouldBe "مائة وعشرين"
        words("120") shouldBe "مائة وعشرون"
        wordsGen("2000000") shouldBe "مليونين"
        words("2000000") shouldBe "مليونان"
        wordsGen("3000000") shouldBe "ثلاثة ملايين" // الجمع لا يتغير كتابةً
        words("3000000") shouldBe "ثلاثة ملايين"
    }

    // -------------------------------------------------------------
    // المحلل المغلق — the closed date grammar
    // -------------------------------------------------------------

    test("ISO first-year and documentary last-year agree on the same phrase") {
        date("2026-10-03") shouldBe "الثالث من أكتوبر عام ألفين وستة وعشرين"
        date("3-10-2026") shouldBe "الثالث من أكتوبر عام ألفين وستة وعشرين"
        date("2026/10/3") shouldBe "الثالث من أكتوبر عام ألفين وستة وعشرين"
        date("3.10.2026") shouldBe "الثالث من أكتوبر عام ألفين وستة وعشرين"
    }

    test("whitespace around the field and the separator is tolerated") {
        date(" 2026-10-03 ") shouldBe "الثالث من أكتوبر عام ألفين وستة وعشرين"
        date("3 / 10 / 2026") shouldBe "الثالث من أكتوبر عام ألفين وستة وعشرين"
    }

    test("the three digit systems parse — even mixed inside one date") {
        date("٣-١٠-٢٠٢٦") shouldBe "الثالث من أكتوبر عام ألفين وستة وعشرين"
        date("۲۰۲۶-۱۰-۳") shouldBe "الثالث من أكتوبر عام ألفين وستة وعشرين"
        date("2026-١٠-۰۳") shouldBe "الثالث من أكتوبر عام ألفين وستة وعشرين"
    }

    test("zero padding is value-based, never positional") {
        date("2026-10-03") shouldBe date("2026-10-3")
        date("03-10-2026") shouldBe date("3-10-2026")
        date("03/03/2026") shouldBe "الثالث من مارس عام ألفين وستة وعشرين"
    }

    test("the closed day-ordinal table speaks the written oblique") {
        date("1-1-2026") shouldBe "الأول من يناير عام ألفين وستة وعشرين"
        date("10-1-2026") shouldBe "العاشر من يناير عام ألفين وستة وعشرين"
        date("11-1-2026") shouldBe "الحادي عشر من يناير عام ألفين وستة وعشرين"
        date("19-1-2026") shouldBe "التاسع عشر من يناير عام ألفين وستة وعشرين"
        date("20-1-2026") shouldBe "العشرين من يناير عام ألفين وستة وعشرين"
        date("21-1-2026") shouldBe "الحادي والعشرين من يناير عام ألفين وستة وعشرين"
        date("22-1-2026") shouldBe "الثاني والعشرين من يناير عام ألفين وستة وعشرين"
        date("30-1-2026") shouldBe "الثلاثين من يناير عام ألفين وستة وعشرين"
        date("31-1-2026") shouldBe "الحادي والثلاثين من يناير عام ألفين وستة وعشرين"
    }

    test("the month catalog is closed and ordered") {
        date("1-1-2026") shouldBe "الأول من يناير عام ألفين وستة وعشرين"
        date("1-12-2026") shouldBe "الأول من ديسمبر عام ألفين وستة وعشرين"
        date("15-7-2030") shouldBe "الخامس عشر من يوليو عام ألفين وثلاثين"
    }

    test("the year speaks through DrsNumberWords in the genitive — one source of truth") {
        date("1-1-2000") shouldBe "الأول من يناير عام ألفين"
        date("1-1-1000") shouldBe "الأول من يناير عام ألف"
        date("1-1-1945") shouldBe "الأول من يناير عام ألف وتسعمائة وخمسة وأربعين"
        date("1-1-2002") shouldBe "الأول من يناير عام ألفين واثنين"
        date("1-1-2012") shouldBe "الأول من يناير عام ألفين واثني عشر"
        date("1-1-2200") shouldBe "الأول من يناير عام ألفين ومائتين"
    }

    test("the documented leap rule: 2024 yes, 2023 no, 2000 yes, 1900 no") {
        date("29-2-2024") shouldBe "التاسع والعشرين من فبراير عام ألفين وأربعة وعشرين"
        date("29-2-2023") shouldBe null
        date("29-2-2000") shouldBe "التاسع والعشرين من فبراير عام ألفين"
        date("29-2-1900") shouldBe null
    }

    test("the closed month-length table is honored") {
        date("31-4-2026") shouldBe null // أبريل ثلاثون يومًا
        date("30-4-2026") shouldBe "الثلاثين من أبريل عام ألفين وستة وعشرين"
        date("31-6-2026") shouldBe null
        date("31-12-2026") shouldBe "الحادي والثلاثين من ديسمبر عام ألفين وستة وعشرين"
    }

    // -------------------------------------------------------------
    // الفشل الصادق — the honest no-op
    // -------------------------------------------------------------

    test("a two-digit year is rejected — no invented century rule") {
        date("3-10-26") shouldBe null
        date("26-10-3") shouldBe null
    }

    test("a four-digit component in both ends is ambiguous — rejected") {
        date("2026-10-2026") shouldBe null
    }

    test("out-of-range months and days are rejected") {
        date("2026-13-01") shouldBe null
        date("13-13-2026") shouldBe null
        date("32-1-2026") shouldBe null
        date("0-1-2026") shouldBe null
        date("2026-0-10") shouldBe null
        date("2026-10-0") shouldBe null
        date("2026-10-32") shouldBe null
    }

    test("mixed separators and wrong shapes are rejected") {
        date("3-10/2026") shouldBe null
        date("3-10-2026-5") shouldBe null
        date("3-10") shouldBe null
        date("2026-10") shouldBe null
        date("-3-10-2026") shouldBe null
        date("3--10-2026") shouldBe null
        date("3-10-2026-") shouldBe null
    }

    test("anything carrying letters is prose, not a date") {
        date("أكتوبر 2026") shouldBe null
        date("2026-أكتوبر-03") shouldBe null
        date("يوم 3-10-2026") shouldBe null
    }

    test("the output is a fixed point — no separator, no re-parse") {
        val out = date("2026-10-03")!!
        date(out) shouldBe null
        out shouldBe "الثالث من أكتوبر عام ألفين وستة وعشرين"
    }

    test("the engine accepts no null-shaped empty field") {
        date("") shouldBe null
        date("   ") shouldBe null
    }
})
