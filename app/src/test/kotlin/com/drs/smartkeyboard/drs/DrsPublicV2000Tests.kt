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
 *
 * عقود العكس الحتمي المثبتة هنا (الدفعة B):
 *  - المعجم المغلق: حرفيًا كلمات DrsNumberWords المصدرة لا غير —
 *    الوحدات وعشرة وعشر الذيل المجرد والمراهقان والمئات والعشرات
 *    بالحالتين، وأسر الوتائر الثلاث بصيغها.
 *  - قفل الحالة على أول كلمة حالة والخلط رفض («مائتين وعشرون» لا شيء).
 *  - الواو اللاصقة إلزامية عند كل صلة («ألف وواحد» قانونية و«ألف
 *    واحد» مرفوضة)، و«واحد» مجردة لأن «احد» ليست معجمية.
 *  - عشر الذيل مجردة لا تقف، وكلمة الوتيرة بعد مجموعتها مجردة قطعًا
 *    («خمسة عشر ألف»)، والمجموعتان 1 و2 تقفان وتيرةً وحدهما.
 *  - المقاطع نازلة صارمة وكل كلمة تُستهلك — وما لم يُستهلك رفض.
 *  - الround-trip الشامل: 0..999999 مرفوعًا و0..99999 مجرورًا وحدود
 *    المليون بالحالتين — 1,120,000 قيمة كلها تعود.
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
        DrsTextTool.CODE_RANGE.first shouldBe -669
        (tool.code in DrsTextTool.CODE_RANGE) shouldBe true
        // 59 tools through v1.10.0 + the v2.0.0 decimal and mirror tools.
        DrsTextTool.entries.size shouldBe 63
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

    // -------------------------------------------------------------
    // الكلمات إلى عدد — the release-lexicon mirror (v2.0.0, batch B)
    // -------------------------------------------------------------

    fun wordsToNumber(text: String): Long? = DrsWordsToNumber.wordsToNumberOrNull(text)

    test("the release round-trip: every number 0..999999 returns from its words") {
        val bad = (0L..999_999L).firstOrNull { n ->
            val words = DrsNumberWords.arabicWordsOrNull(n.toString())!!
            DrsWordsToNumber.wordsToNumberOrNull(words) != n
        }
        bad shouldBe null
    }

    test("the genitive round-trip: 0..99999 in the oblique written case") {
        val bad = (0L..99_999L).firstOrNull { n ->
            val words = DrsNumberWords.arabicWordsOrNull(n.toString(), DrsNumberWords.Case.GENITIVE)!!
            DrsWordsToNumber.wordsToNumberOrNull(words) != n
        }
        bad shouldBe null
    }

    test("the million boundary sweep: 999000..1008999 in both cases") {
        val badNom = (999_000L..1_008_999L).firstOrNull { n ->
            DrsWordsToNumber.wordsToNumberOrNull(DrsNumberWords.arabicWordsOrNull(n.toString())!!) != n
        }
        badNom shouldBe null
        val badGen = (999_000L..1_008_999L).firstOrNull { n ->
            DrsWordsToNumber.wordsToNumberOrNull(
                DrsNumberWords.arabicWordsOrNull(n.toString(), DrsNumberWords.Case.GENITIVE)!!,
            ) != n
        }
        badGen shouldBe null
    }

    test("scale selection mirrors the documented grammar") {
        wordsToNumber("مائة ألف") shouldBe 100_000L
        wordsToNumber("عشرة آلاف") shouldBe 10_000L
        wordsToNumber("عشرون ألف") shouldBe 20_000L
        wordsToNumber("خمسة عشر ألف") shouldBe 15_000L
        wordsToNumber("مائة وعشرة ألف") shouldBe 110_000L
        wordsToNumber("مليون وألف") shouldBe 1_001_000L
        wordsToNumber("ملياران ومائتان وخمسة وعشرون ألف") shouldBe 2_000_225_000L
        wordsToNumber("تسعمائة وتسعة وتسعون مليار وتسعمائة وتسعة وتسعون مليون وتسعمائة وتسعة وتسعون ألف وتسعمائة وتسعة وتسعون") shouldBe
            999_999_999_999L // سقف الأسرة المشترك
    }

    test("the attached waw is the join — and واحد stays bare") {
        wordsToNumber("مائة وواحد وعشرون") shouldBe 121L // «وواحد» لاصقة
        wordsToNumber("ألف وواحد") shouldBe 1_001L
        wordsToNumber("ألف وواحد وعشرون") shouldBe 1_021L
        wordsToNumber("واحد وعشرون") shouldBe 21L
        wordsToNumber("واحد") shouldBe 1L
        wordsToNumber("اثنا عشر ألف") shouldBe 12_000L
        wordsToNumber("اثني عشر ألف") shouldBe 12_000L
    }

    test("the written case locks on the first inflected word and refuses the mix") {
        wordsToNumber("مائتان وعشرون") shouldBe 220L
        wordsToNumber("مائتين وعشرين") shouldBe 220L
        wordsToNumber("ألفان ومائتان") shouldBe 2_200L // ألفان وتيرة الألف + مائتان
        wordsToNumber("ألفين ومائتين") shouldBe 2_200L
        wordsToNumber("ألف ومائتان") shouldBe 1_200L // الوترى المفردة: ألف لا ألفان
        wordsToNumber("مائتين وعشرون") shouldBe null // الحالتان لا يختلطان
        wordsToNumber("ألفين ومائتان") shouldBe null
        wordsToNumber("اثنين وعشرون") shouldBe null // المختلط رفض حتى داخل المركب
        wordsToNumber("اثنين وعشرين") shouldBe 22L // المجرور الكامل يكفي للقفل
    }

    test("shapes the engine never writes are refused") {
        wordsToNumber("ألف مائة") shouldBe null // الرأس المجرد بعد مقطع
        wordsToNumber("مليون ألف") shouldBe null
        wordsToNumber("ألف واحد") shouldBe null
        wordsToNumber("مائة واحد وعشرون") shouldBe null
        wordsToNumber("ثلاثة ألف") shouldBe null
        wordsToNumber("اثنان ألف") shouldBe null
        wordsToNumber("ألف ألف") shouldBe null
        wordsToNumber("عشر") shouldBe null // الذيل المجرد لا يقف
        wordsToNumber("عشرة وعشرون") shouldBe null
        wordsToNumber("اثنان عشر") shouldBe null // 12 هي «اثنا عشر» لا «اثنان عشر»
        wordsToNumber("خمسة وثلاثة وعشرون") shouldBe null
        wordsToNumber("آلاف") shouldBe null
        wordsToNumber("صفر واحد") shouldBe null
        wordsToNumber("ألف ومائة ألف") shouldBe null
    }

    test("the minus returns the negative and zero stands alone") {
        wordsToNumber("سالب خمسة") shouldBe -5L
        wordsToNumber("سالب مائة وعشرون") shouldBe -120L
        wordsToNumber("صفر") shouldBe 0L
        wordsToNumber("سالب") shouldBe null
        wordsToNumber("سالب صفر") shouldBe null
    }

    test("WORDS_TO_NUMBER registers at -667 in the catalogue") {
        val tool = DrsTextTool.WORDS_TO_NUMBER
        tool.code shouldBe -667
        DrsTextTool.fromCode(-667) shouldBe tool
        DrsTextTool.CODE_RANGE.first shouldBe -669
        (tool.code in DrsTextTool.CODE_RANGE) shouldBe true
        // 60 tools after the decimal tool + the mirror tool.
        DrsTextTool.entries.size shouldBe 63
        (tool.isInfoOnly) shouldBe false
        (tool.isEditorOp) shouldBe false
        (tool.isInsertMark) shouldBe false
    }

    fun applyWordsToNumber(text: String) = DrsTextTools.apply(DrsTextTool.WORDS_TO_NUMBER, text)

    test("the mirror tool returns the number and passes prose byte-identical") {
        applyWordsToNumber("ثلاثة وعشرون") shouldBe "23"
        applyWordsToNumber("مائة ألف") shouldBe "100000"
        applyWordsToNumber("ثلاثة رجال") shouldBe "ثلاثة رجال" // ليس الحقل كله تركيبًا
        applyWordsToNumber("") shouldBe ""
    }

    test("the mirror tool output is a fixed point — digits are not words") {
        val once = applyWordsToNumber("ثلاثة وعشرون")
        once shouldBe "23"
        applyWordsToNumber(once) shouldBe once
    }
})
