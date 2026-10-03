/*
 * Copyright (C) 2026 The DRS Smart Keyboard Project
 */

package com.drs.smartkeyboard.drs

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

/**
 * الإصدار العام v1.10.0 — التوسعة الخامسة: الكسور بالكلمات والترتيب
 * بالكلمات ويوم الأسبوع.
 *
 * الجولة الخامسة تفتح: v1.6.0 جعلت الأعداد تتكلم، وv1.7.0 المبالغ،
 * وv1.8.0 التواريخ، وv1.9.0 الأوقات — وv1.10.0 تجعل الكسور تتكلم
 * «3/4» تصير «ثلاثة أرباع».
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
        DrsTextTool.CODE_RANGE.first shouldBe -663
        (tool.code in DrsTextTool.CODE_RANGE) shouldBe true
        // 56 tools through v1.9.0 + the fraction-in-words tool.
        DrsTextTool.entries.size shouldBe 57
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
})
