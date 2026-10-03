/*
 * Copyright (C) 2026 The DRS Smart Keyboard Project
 */

package com.drs.smartkeyboard.drs

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

/**
 * الإصدار العام v2.0.0 — التوسعة السادسة: العشري والعكس الحتمي.
 *
 * الجولة السادسة ثنائيتها: محرك الكسور العشرية بالكلمات يجعل الذيل
 * العشري يتكلم رقمًا رقمًا بلا تبسيط «1.5» تصير «واحد فاصلة خمسة»،
 * ومحرك العكس الحتمي يغلق الأسرة العددية باتجاهين على معجم إصدار
 * واحد: «ثلاثة وعشرون» تعود 23.
 *
 * عقود العشري المثبتة هنا (الدفعة A):
 *  - زوج الفواصل المغلق { . ، ٫ } — فاصلة واحدة بالضبط، والفاصلة
 *    العربية «،» ليست فاصلة عشرية قط.
 *  - الطرفان رقميان خالصان على الأنظمة الثلاثة، وكل محرف يُقرأ
 *    مستقلاً فالحقل المختلط يقبل (العمى اللغوي الموثق نفسه).
 *  - الجزء الصحيح عبر DrsNumberWords مصدر الحقيقة الوحيد: قيمة لا
 *    موضع («007.5» تقول «سبعة»)، سقف الاثني عشر رقمًا، السالب
 *    يُكرَّم، والصفر السالب مرفوض.
 *  - الذيل رقمًا رقمًا عبر أبجدية DIGIT_WORDS المشتركة — الكتابة هي
 *    العقد لا القراءة المبسطة: «1.50» تحتفظ بصفرها.
 *  - سقف الذيل خمسة عشر رقمًا — وما وراءه رفض لا اختراع.
 *  - نقطة ثابتة: المخرج بلا فاصلة عشرية فإعادة التطبيق لا تغير حرفًا.
 */
class DrsPublicV2000Tests : FunSpec({

    // تعريفات محلية داخل جسم المواصفة — Kotest FunSpec lambda (بلا private).
    fun decimal(text: String): String? = DrsDecimalWords.decimalWordsOrNull(text)

    // -------------------------------------------------------------
    // العشري بالكلمات — the digit-by-digit tail (v2.0.0, batch A)
    // -------------------------------------------------------------

    test("clean decimals speak digit-by-digit — no simplification") {
        decimal("1.5") shouldBe "واحد فاصلة خمسة"
        decimal("1.50") shouldBe "واحد فاصلة خمسة صفر" // الكتابة هي العقد
        decimal("0.5") shouldBe "صفر فاصلة خمسة"
        decimal("12.34") shouldBe "اثنا عشر فاصلة ثلاثة أربعة"
        decimal("100.07") shouldBe "مائة فاصلة صفر سبعة"
    }

    test("the three digit systems parse — even mixed inside one field") {
        decimal("١.٥") shouldBe "واحد فاصلة خمسة" // عربي-هندي
        decimal("۱.۵") shouldBe "واحد فاصلة خمسة" // ممدد (فارسي/أردو)
        decimal("1.٥") shouldBe "واحد فاصلة خمسة" // مختلط — كل محرف مستقل
        decimal("٢٠.١٤") shouldBe "عشرون فاصلة واحد أربعة"
    }

    test("the integer part speaks by value, not position") {
        decimal("007.5") shouldBe "سبعة فاصلة خمسة"
        decimal("1000000.1") shouldBe "مليون فاصلة واحد"
        decimal("999999999999.9") shouldBe
            "تسعمائة وتسعة وتسعون مليار وتسعمائة وتسعة وتسعون مليون وتسعمائة وتسعة وتسعون ألف وتسعمائة وتسعة وتسعون فاصلة تسعة"
    }

    test("the minus is honored and negative zero is refused") {
        decimal("-1.5") shouldBe "سالب واحد فاصلة خمسة"
        decimal("-12.75") shouldBe "سالب اثنا عشر فاصلة سبعة خمسة"
        decimal("-0.5") shouldBe null // الصفر السالب مرفوض كما في مصدر الحقيقة
        decimal("0.0") shouldBe "صفر فاصلة صفر"
    }

    test("the closed separator pair — exactly one, never the Arabic comma") {
        decimal("1٫5") shouldBe "واحد فاصلة خمسة" // الفاصلة العشرية العربية U+066B
        decimal("١٫٥") shouldBe "واحد فاصلة خمسة"
        decimal("1.5.2") shouldBe null // فاصلتان — رفض
        decimal("1،5") shouldBe null // الفاصلة العربية ليست فاصلة عشرية قط
    }

    test("an empty half, signs and letters are refused") {
        decimal("") shouldBe null
        decimal("1.") shouldBe null
        decimal(".5") shouldBe null
        decimal("+1.5") shouldBe null
        decimal("1a.5") shouldBe null
        decimal("1.5a") shouldBe null
        decimal("1 5.5") shouldBe null
    }

    test("the tail ceiling is fifteen digits — beyond is refused, not invented") {
        val tail15 = "1.123456789012345"
        decimal(tail15) shouldBe "واحد فاصلة واحد اثنان ثلاثة أربعة خمسة ستة سبعة ثمانية تسعة صفر واحد اثنان ثلاثة أربعة خمسة"
        decimal("1.1234567890123456") shouldBe null // ستة عشر — رفض
        val int13 = "1234567890123.5"
        decimal(int13) shouldBe null // ثلثة عشر رقمًا صحيحًا — فوق سقف مصدر الحقيقة
    }

    test("the output is a fixed point — no dot, no re-parse") {
        val out = decimal("1.5")!!
        decimal(out) shouldBe null
        out shouldBe "واحد فاصلة خمسة"
    }

    // -------------------------------------------------------------
    // الأداة في الكتالوج — the DECIMAL_WORDS tool wiring (v2.0.0)
    // -------------------------------------------------------------

    test("DECIMAL_WORDS registers at -666 in the catalogue") {
        val tool = DrsTextTool.DECIMAL_WORDS
        tool.code shouldBe -666
        DrsTextTool.fromCode(-666) shouldBe tool
        DrsTextTool.CODE_RANGE.first shouldBe -666
        (tool.code in DrsTextTool.CODE_RANGE) shouldBe true
        // 59 tools through v1.10.0 + the v2.0.0 decimal tool.
        DrsTextTool.entries.size shouldBe 60
        (tool.isInfoOnly) shouldBe false
        (tool.isEditorOp) shouldBe false
        (tool.isInsertMark) shouldBe false
    }

    fun applyDecimal(text: String) = DrsTextTools.apply(DrsTextTool.DECIMAL_WORDS, text)

    test("the tool converts a clean decimal and passes prose byte-identical") {
        applyDecimal("1.5") shouldBe "واحد فاصلة خمسة"
        applyDecimal("الثمن 1.5 من المبلغ") shouldBe "الثمن 1.5 من المبلغ" // ليس الحقل كله عددًا
        applyDecimal("") shouldBe ""
    }

    test("the decimal tool output is a fixed point — applying twice changes nothing") {
        val once = applyDecimal("1.50")
        applyDecimal(once) shouldBe once
    }
})
