/*
 * Copyright (C) 2026 The DRS Smart Keyboard Project
 */

package com.drs.smartkeyboard.drs

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

/**
 * الإصدار العام v1.10.0 — التوسعة الخامسة: الكسور بالكلمات ويوم
 * الأسبوع والترتيب بالكلمات.
 *
 * الجولة الخامسة ثلاثيتها مكتملة: v1.6.0 جعلت الأعداد تتكلم، وv1.7.0
 * المبالغ، وv1.8.0 التواريخ، وv1.9.0 الأوقات — وv1.10.0 تجعل الكسور
 * تتكلم «3/4» تصير «ثلاثة أرباع»، والتواريخ تنطق يومها «السبت»،
 * والأعداد تنطق ترتيبها «الحادي والعشرون».
 *
 * عقود الكسر المثبتة هنا:
 *  - المحلل المغلق: مكونان فقط بشرطة الكسر الواحدة من { / ، ⁄ } —
 *    بلا خلط بين النظامين، والفراغ حول الحقل والشرطة مسموح.
 *  - المنطقة المغلقة: البسط 1..10 والمقام 2..10 — حيث تملك العربية
 *    كلمة كسر موحدة؛ وما وراءها تُرفض لا تُخترع.
 *  - الموافقة النحوية الموثقة: الواحد يأخذ المفرد المعرف «النصف»،
 *    والاثنان المثنى «ثلثان»، وثلاثة إلى عشرة تعدّ الجمع المكسر
 *    «ثلاثة أثلاث» و«عشرة أنصاف» — بلا تنوين (الأسلوب الدار).
 *  - الكسر يتكلم كتابته نفسها بلا تبسيط: «2/4» هي «ربعان» لا «النصف».
 *  - الفشل الصادق: بسط صفر، مقام واحد، مقام أحد عشر، بسط اثنا عشر،
 *    سالب، عشري، ثلاثة مكونات، حروف — كلها لا شيء والأداة تمرر بايتيًا.
 *  - نقطة ثابتة: المخرج بلا شرطة فإعادة التطبيق لا تغير حرفًا.
 */
class DrsPublicV1100Tests : FunSpec({

    // تعريفات محلية داخل جسم المواصفة — Kotest FunSpec lambda (بلا private).
    fun fraction(text: String): String? = DrsFractionWords.fractionWordsOrNull(text)

    // -------------------------------------------------------------
    // الكسر بالكلمات — the closed fraction-word catalog
    // -------------------------------------------------------------

    test("one takes the definite singular: النصف .. العشر") {
        fraction("1/2") shouldBe "النصف"
        fraction("1/3") shouldBe "الثلث"
        fraction("1/4") shouldBe "الربع"
        fraction("1/5") shouldBe "الخمس"
        fraction("1/6") shouldBe "السدس"
        fraction("1/7") shouldBe "السبع"
        fraction("1/8") shouldBe "الثمن"
        fraction("1/9") shouldBe "التسع"
        fraction("1/10") shouldBe "العشر"
    }

    test("two takes the dual — the field speaks its own words, never simplified") {
        fraction("2/3") shouldBe "ثلثان"
        fraction("2/2") shouldBe "نصفان"
        fraction("2/4") shouldBe "ربعان" // لا يُبسّط إلى النصف — الكتابة هي العقد
        fraction("2/10") shouldBe "عشران"
    }

    test("three to ten count the broken plural with the masculine counter") {
        fraction("3/4") shouldBe "ثلاثة أرباع"
        fraction("3/2") shouldBe "ثلاثة أنصاف"
        fraction("4/3") shouldBe "أربعة أثلاث"
        fraction("5/6") shouldBe "خمسة أسداس"
        fraction("6/7") shouldBe "ستة أسباع"
        fraction("7/8") shouldBe "سبعة أثمان"
        fraction("8/9") shouldBe "ثمانية أتساع"
        fraction("9/10") shouldBe "تسعة أعشار"
        fraction("10/2") shouldBe "عشرة أنصاف"
        fraction("10/10") shouldBe "عشرة أعشار"
    }

    // -------------------------------------------------------------
    // المحلل المغلق — digits, slash, whitespace
    // -------------------------------------------------------------

    test("the three digit systems parse — even mixed inside one fraction") {
        fraction("٣/٤") shouldBe "ثلاثة أرباع"
        fraction("۲/۳") shouldBe "ثلثان"
        fraction("1/٤") shouldBe "الربع"
        fraction("٠٣/٤") shouldBe "ثلاثة أرباع" // التعبئة بالقيمة لا بالموضع
    }

    test("whitespace around the field and the slash is tolerated") {
        fraction(" 3/4 ") shouldBe "ثلاثة أرباع"
        fraction("3 / 4") shouldBe "ثلاثة أرباع"
    }

    test("the Unicode fraction slash U+2044 belongs to the closed set") {
        fraction("3\u20444") shouldBe "ثلاثة أرباع"
        fraction("1\u20442") shouldBe "النصف"
    }

    // -------------------------------------------------------------
    // الفشل الصادق — the honest no-op
    // -------------------------------------------------------------

    test("the zone is closed: numerator 1..10 and denominator 2..10") {
        fraction("0/2") shouldBe null // بسط صفر
        fraction("1/1") shouldBe null // مقام واحد ليس كسرًا
        fraction("1/11") shouldBe null // ما وراء كلمة الكسر العربية
        fraction("11/2") shouldBe null
        fraction("12/5") shouldBe null
    }

    test("any other shape is prose, not a fraction") {
        fraction("-1/2") shouldBe null
        fraction("1.5/2") shouldBe null
        fraction("3//4") shouldBe null
        fraction("3/4/5") shouldBe null
        fraction("a/b") shouldBe null
        fraction("3/أربعة") shouldBe null
        fraction("½") shouldBe null // رمز الكسر الواحد نثر وليس مكونين
        fraction("3|4") shouldBe null
    }

    test("the engine accepts no null-shaped empty field") {
        fraction("") shouldBe null
        fraction("   ") shouldBe null
        fraction("/") shouldBe null
    }

    test("the output is a fixed point — no slash, no re-parse") {
        val out = fraction("3/4")!!
        fraction(out) shouldBe null
        out shouldBe "ثلاثة أرباع"
    }

    // -------------------------------------------------------------
    // الأداة في الكتالوج — the FRACTION_WORDS tool wiring (v1.10.0)
    // -------------------------------------------------------------

    test("FRACTION_WORDS registers at -663 in the catalogue") {
        val tool = DrsTextTool.FRACTION_WORDS
        tool.code shouldBe -663
        DrsTextTool.fromCode(-663) shouldBe tool
        DrsTextTool.CODE_RANGE.first shouldBe -668
        (tool.code in DrsTextTool.CODE_RANGE) shouldBe true
        // 56 tools through v1.9.0 + the v1.10.0 fraction, weekday and
        // ordinal tools.
        DrsTextTool.entries.size shouldBe 62
        (tool.isInfoOnly) shouldBe false
        (tool.isEditorOp) shouldBe false
        (tool.isInsertMark) shouldBe false
    }

    fun applyFraction(text: String) = DrsTextTools.apply(DrsTextTool.FRACTION_WORDS, text)

    test("the tool converts a clean fraction and passes prose byte-identical") {
        applyFraction("3/4") shouldBe "ثلاثة أرباع"
        applyFraction("1/2") shouldBe "النصف"
        applyFraction("الثلث من المبلغ") shouldBe "الثلث من المبلغ"
        applyFraction("") shouldBe ""
    }

    test("the tool output is a fixed point — applying twice changes nothing") {
        val once = applyFraction("3/4")
        applyFraction(once) shouldBe once
    }

    // -------------------------------------------------------------
    // يوم الأسبوع — the closed Sakamoto congruence (v1.10.0, batch B)
    // -------------------------------------------------------------

    fun weekday(text: String): String? = DrsDateWords.weekdayOrNull(text)

    test("known dates speak their weekday — the reference-calendar contract") {
        weekday("2026-10-03") shouldBe "السبت"
        weekday("03/10/2026") shouldBe "السبت" // العرف الوثائقي يوم-شهر-سنة يقول الشيء نفسه
        weekday("2026-01-01") shouldBe "الخميس"
        weekday("2025-01-01") shouldBe "الأربعاء"
        weekday("2024-02-29") shouldBe "الخميس" // يوم الكبيسة نفسه يدخل الحساب
        weekday("2000-01-01") shouldBe "السبت"
        weekday("1900-01-01") shouldBe "الاثنين" // 1900 ليست كبيسة والقاعدة تعرف
        weekday("0001-01-01") shouldBe "الاثنين" // أول يوم في التقويم البروليبتيك
        weekday("1.1.1") shouldBe null // لا مكوّن رباعي — السنة غامضة والمحلل يرفض
        weekday("9999-12-31") shouldBe "الجمعة" // آخر يوم يقبلها المحلل
    }

    test("the full week is reachable: Sunday through Saturday") {
        // 2026-10-04 (Sunday) .. 2026-10-10 (Saturday) — seven consecutive days.
        weekday("2026-10-04") shouldBe "الأحد"
        weekday("2026-10-05") shouldBe "الاثنين"
        weekday("2026-10-06") shouldBe "الثلاثاء"
        weekday("2026-10-07") shouldBe "الأربعاء"
        weekday("2026-10-08") shouldBe "الخميس"
        weekday("2026-10-09") shouldBe "الجمعة"
        weekday("2026-10-10") shouldBe "السبت"
    }

    test("the weekday inherits the closed date parser verbatim") {
        weekday("١٤/٠٣/٢٠٢٦") shouldBe weekday("14/03/2026") // الأرقام الهندية بلا تغيير
        weekday("29/2/2023") shouldBe null // 2023 ليست كبيسة
        weekday("2026-13-01") shouldBe null
        weekday("2026-10-03 و2026-10-04") shouldBe null
        weekday("السبت الماضي") shouldBe null
        weekday("2026-10-03T09:00") shouldBe null
    }

    fun applyWeekday(text: String) = DrsTextTools.apply(DrsTextTool.WEEKDAY, text)

    test("WEEKDAY registers at -664 and converts a clean date") {
        val tool = DrsTextTool.WEEKDAY
        tool.code shouldBe -664
        DrsTextTool.fromCode(-664) shouldBe tool
        DrsTextTool.CODE_RANGE.first shouldBe -668
        (tool.code in DrsTextTool.CODE_RANGE) shouldBe true
        // 57 tools after the fraction tool + the weekday tool.
        DrsTextTool.entries.size shouldBe 62
        (tool.isInfoOnly) shouldBe false
        (tool.isEditorOp) shouldBe false
        (tool.isInsertMark) shouldBe false
        applyWeekday("2026-10-03") shouldBe "السبت"
        applyWeekday("بكرة السبت") shouldBe "بكرة السبت"
        applyWeekday("") shouldBe ""
    }

    test("the weekday tool output is a fixed point — no separator, no re-parse") {
        val once = applyWeekday("2026-10-03")
        applyWeekday(once) shouldBe once
    }

    // -------------------------------------------------------------
    // الترتيب بالكلمات — the closed ordinal grammar (v1.10.0, batch C)
    // -------------------------------------------------------------

    fun ordinal(text: String): String? = DrsOrdinalWords.ordinalWordsOrNull(text)

    test("bare units speak الأول..العاشر — and teens keep الحادي") {
        ordinal("1") shouldBe "الأول"
        ordinal("3") shouldBe "الثالث"
        ordinal("9") shouldBe "التاسع"
        ordinal("10") shouldBe "العاشر"
        ordinal("11") shouldBe "الحادي عشر" // لا «الأول عشر» — التمييز الموثق
        ordinal("12") shouldBe "الثاني عشر"
        ordinal("19") shouldBe "التاسع عشر"
    }

    test("the compound pairs pin the الأول/الحادي written distinction") {
        ordinal("20") shouldBe "العشرون"
        ordinal("21") shouldBe "الحادي والعشرون" // الواحد يصير الحادي في المركب
        ordinal("22") shouldBe "الثاني والعشرون"
        ordinal("32") shouldBe "الثاني والثلاثون"
        ordinal("58") shouldBe "الثامن والخمسون"
        ordinal("99") shouldBe "التاسع والتسعون"
    }

    test("higher groups keep their cardinal words under the article") {
        ordinal("100") shouldBe "المائة"
        ordinal("101") shouldBe "المائة والأول"
        ordinal("121") shouldBe "المائة والحادي والعشرون"
        ordinal("200") shouldBe "المائتان" // المثنى موثق
        ordinal("300") shouldBe "الثلاثمائة"
        ordinal("345") shouldBe "الثلاثمائة والخامس والأربعون"
        ordinal("1000") shouldBe "الألف"
        ordinal("1001") shouldBe "الألف والأول"
        ordinal("2000") shouldBe "الألفان"
        ordinal("3000") shouldBe "الثلاثة آلاف"
        ordinal("1234") shouldBe "الألف والمائتان والرابع والثلاثون"
        ordinal("9999") shouldBe "التسعة آلاف والتسعمائة والتاسع والتسعون"
    }

    test("the three digit systems parse — padding by value, never position") {
        ordinal("٣") shouldBe "الثالث"
        ordinal("۲۱") shouldBe "الحادي والعشرون"
        ordinal("٠٢١") shouldBe "الحادي والعشرون" // القيمة لا الموضع
        ordinal("١٢٣٤") shouldBe "الألف والمائتان والرابع والثلاثون"
    }

    test("the closed zone refuses everything else honestly") {
        ordinal("0") shouldBe null // الصفر ليس ترتيبًا في المنطقة المغلقة
        ordinal("-3") shouldBe null
        ordinal("+3") shouldBe null
        ordinal("10000") shouldBe null // ما وراء التسعة آلاف رفض لا اختراع
        ordinal("3.5") shouldBe null
        ordinal("الثالث") shouldBe null
        ordinal("") shouldBe null
        ordinal("   ") shouldBe null
    }

    test("the ordinal output is a fixed point — no digits, no re-parse") {
        val out = ordinal("21")!!
        ordinal(out) shouldBe null
        out shouldBe "الحادي والعشرون"
    }

    fun applyOrdinal(text: String) = DrsTextTools.apply(DrsTextTool.ORDINAL_WORDS, text)

    test("ORDINAL_WORDS registers at -665 and converts a clean integer") {
        val tool = DrsTextTool.ORDINAL_WORDS
        tool.code shouldBe -665
        DrsTextTool.fromCode(-665) shouldBe tool
        DrsTextTool.CODE_RANGE.first shouldBe -668
        (tool.code in DrsTextTool.CODE_RANGE) shouldBe true
        // 58 tools after the weekday tool + the ordinal-words tool.
        DrsTextTool.entries.size shouldBe 62
        (tool.isInfoOnly) shouldBe false
        (tool.isEditorOp) shouldBe false
        (tool.isInsertMark) shouldBe false
        applyOrdinal("21") shouldBe "الحادي والعشرون"
        applyOrdinal("الطبقة الثالثة") shouldBe "الطبقة الثالثة"
        applyOrdinal("") shouldBe ""
    }

    test("the ordinal tool output is a fixed point — applying twice changes nothing") {
        val once = applyOrdinal("21")
        applyOrdinal(once) shouldBe once
    }
})
