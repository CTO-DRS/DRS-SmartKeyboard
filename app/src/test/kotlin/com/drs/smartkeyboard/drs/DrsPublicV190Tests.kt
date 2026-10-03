/*
 * Copyright (C) 2026 The DRS Smart Keyboard Project
 */

package com.drs.smartkeyboard.drs

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

/**
 * الإصدار العام v1.9.0 — التوسعة الرابعة: الوقت بالكلمات.
 *
 * الرباعية تتكلم كلها: v1.6.0 الأعداد، v1.7.0 المبالغ، v1.8.0
 * التواريخ، وv1.9.0 الأوقات — «14:45» تصير «الثالثة إلا الربع» —
 * عبارة الساعة التي تنطق بها الدعوات والمحاضر والخطابات الرسمية.
 *
 * العقود المثبتة هنا:
 *  - المحلل المغلق: مكونان فقط بفاصل النقطتين «:» الوحيد (والفراغ
 *    حول الحقل والفاصل مسموح)، كل مكوّن من خانة إلى خانتين، والأرقام
 *    ثلاثة أنظمة حتى المختلطة، والتعبئة بالقيمة لا بالموضع.
 *  - الساعة المغلقة: 0..23 والدقيقة 0..59 — «24:00» ليست وقتًا
 *    تنطقه الساعة، والدقيقة 60 غير موجودة، ومكوّن الثواني نثر.
 *  - الكلام بعرف الساعة الاثني عشرية الموثق: 00:00 و12:00 كلتاهما
 *    «الثانية عشرة»، وصيغة الساعة القياسية «الواحدة» لا «الأولى».
 *  - فروع الدقائق المغلقة: الربع «والربع» والنصف «والنصف» والإلا
 *    للساعة التالية وحدها «إلا الربع» (14:45 تصير «الثالثة إلا
 *    الربع» لا «الثالثة وخمسة وأربعون دقيقة»)، وسائر الدقائق بقواعد
 *    المعدود المؤنث الموثقة (دقيقة واحدة، دقيقتان، ثلاث دقائق،
 *    إحدى عشرة دقيقة، اثنتا عشرة دقيقة، إحدى وعشرون دقيقة).
 *  - الفشل الصادق: ساعة 24، دقيقة 60، ثلاثة مكونات، فاصل آخر من
 *    جرامر التاريخ، حروف، سالب — كلها لا شيء والأداة تمرر بايتيًا.
 *  - نقطة ثابتة: المخرج بلا نقطتين فإعادة التطبيق لا تغير حرفًا.
 */
class DrsPublicV190Tests : FunSpec({

    // تعريفات محلية داخل جسم المواصفة — Kotest FunSpec lambda (بلا private).
    fun time(text: String): String? = DrsTimeWords.timeWordsOrNull(text)

    // -------------------------------------------------------------
    // الساعة — the closed hour table
    // -------------------------------------------------------------

    test("clean hours speak the feminine ordinal the clock uses") {
        time("09:00") shouldBe "التاسعة"
        time("14:00") shouldBe "الثانية" // الثانية عصرًا — الساعة 14 هي الثانية
        time("1:00") shouldBe "الواحدة" // صيغة الساعة القياسية لا «الأولى»
        time("11:00") shouldBe "الحادية عشرة"
    }

    test("the documented 12-hour speech convention: 00:00 and 12:00 agree") {
        time("00:00") shouldBe "الثانية عشرة"
        time("12:00") shouldBe "الثانية عشرة"
        time("0:00") shouldBe "الثانية عشرة"
    }

    // -------------------------------------------------------------
    // الربع والنصف والإلا — the closed quarter/half branches
    // -------------------------------------------------------------

    test("quarter past and half past join the same hour") {
        time("09:15") shouldBe "التاسعة والربع"
        time("14:30") shouldBe "الثانية والنصف"
        time("0:15") shouldBe "الثانية عشرة والربع"
        time("12:30") shouldBe "الثانية عشرة والنصف"
    }

    test("quarter-to borrows the NEXT hour — the only إلا shape") {
        time("14:45") shouldBe "الثالثة إلا الربع"
        time("09:45") shouldBe "العاشرة إلا الربع"
        time("12:45") shouldBe "الواحدة إلا الربع"
        time("23:45") shouldBe "الثانية عشرة إلا الربع"
        time("00:45") shouldBe "الواحدة إلا الربع"
    }

    // -------------------------------------------------------------
    // قواعد المعدود المؤنث — the minute phrase branches
    // -------------------------------------------------------------

    test("one and two are the integrated forms") {
        time("9:01") shouldBe "التاسعة ودقيقة واحدة"
        time("9:02") shouldBe "التاسعة ودقيقتان"
    }

    test("three to ten count with the bare reverse-agreement counters") {
        time("9:03") shouldBe "التاسعة وثلاث دقائق"
        time("9:08") shouldBe "التاسعة وثماني دقائق"
        time("9:10") shouldBe "التاسعة وعشر دقائق"
    }

    test("eleven to nineteen take the feminine teens") {
        time("9:11") shouldBe "التاسعة وإحدى عشرة دقيقة"
        time("9:12") shouldBe "التاسعة واثنتا عشرة دقيقة"
        time("9:13") shouldBe "التاسعة وثلاث عشرة دقيقة"
        time("9:19") shouldBe "التاسعة وتسع عشرة دقيقة"
    }

    test("the compound tens carry the feminine units over the nominative tens") {
        time("9:20") shouldBe "التاسعة وعشرون دقيقة"
        time("9:21") shouldBe "التاسعة وإحدى وعشرون دقيقة"
        time("9:22") shouldBe "التاسعة واثنتان وعشرون دقيقة"
        time("9:23") shouldBe "التاسعة وثلاث وعشرون دقيقة"
        time("9:40") shouldBe "التاسعة وأربعون دقيقة"
        time("9:50") shouldBe "التاسعة وخمسون دقيقة"
    }

    test("the last minute of the clock hour speaks whole") {
        time("09:59") shouldBe "التاسعة وتسع وخمسون دقيقة"
        time("23:59") shouldBe "الحادية عشرة وتسع وخمسون دقيقة"
    }

    // -------------------------------------------------------------
    // المحلل المغلق — digits, padding, whitespace
    // -------------------------------------------------------------

    test("the three digit systems parse — even mixed inside one time") {
        time("١٤:٣٠") shouldBe "الثانية والنصف"
        time("۱۴:۴۵") shouldBe "الثالثة إلا الربع"
        time("14:٣٠") shouldBe "الثانية والنصف"
    }

    test("whitespace around the field and the separator is tolerated") {
        time(" 14:30 ") shouldBe "الثانية والنصف"
        time("14 : 30") shouldBe "الثانية والنصف"
    }

    test("padding is value-based, never positional") {
        time("9:5") shouldBe "التاسعة وخمس دقائق"
        time("09:05") shouldBe "التاسعة وخمس دقائق"
        time("9:05") shouldBe time("09:5")
    }

    // -------------------------------------------------------------
    // الفشل الصادق — the honest no-op
    // -------------------------------------------------------------

    test("the clock is closed: hour 24 and minute 60 do not exist") {
        time("24:00") shouldBe null
        time("25:00") shouldBe null
        time("12:60") shouldBe null
        time("٢٤:٠٠") shouldBe null
    }

    test("a seconds component and any other shape are prose") {
        time("12:30:45") shouldBe null
        time("12:") shouldBe null
        time(":30") shouldBe null
        time("12.30") shouldBe null // النقطة من جرامر التاريخ لا الساعة
        time("14-30") shouldBe null
        time("-1:00") shouldBe null
    }

    test("anything carrying letters is prose, not a clock time") {
        time("الثالثة") shouldBe null
        time("14:ثلاثون") shouldBe null
        time("الساعة 14:45") shouldBe null
        time("14:45 م") shouldBe null
    }

    test("the engine accepts no null-shaped empty field") {
        time("") shouldBe null
        time("   ") shouldBe null
    }

    test("the output is a fixed point — no colon, no re-parse") {
        val out = time("14:45")!!
        time(out) shouldBe null
        out shouldBe "الثالثة إلا الربع"
    }

    // -------------------------------------------------------------
    // الأداة في الكتالوج — the TIME_WORDS tool wiring (v1.9.0)
    // -------------------------------------------------------------

    test("TIME_WORDS registers at -662 in the catalogue") {
        val tool = DrsTextTool.TIME_WORDS
        tool.code shouldBe -662
        DrsTextTool.fromCode(-662) shouldBe tool
        DrsTextTool.CODE_RANGE.first shouldBe -663
        (tool.code in DrsTextTool.CODE_RANGE) shouldBe true
        // 55 tools through v1.8.0 + the clock-time-in-words tool +
        // the v1.10.0 fraction-in-words tool.
        DrsTextTool.entries.size shouldBe 57
        (tool.isInfoOnly) shouldBe false
        (tool.isEditorOp) shouldBe false
        (tool.isInsertMark) shouldBe false
    }

    fun applyTime(text: String) = DrsTextTools.apply(DrsTextTool.TIME_WORDS, text)

    test("the tool converts a clean clock time and passes prose byte-identical") {
        applyTime("14:45") shouldBe "الثالثة إلا الربع"
        applyTime("09:30") shouldBe "التاسعة والنصف"
        applyTime("الساعة 14:45") shouldBe "الساعة 14:45"
        applyTime("") shouldBe ""
    }

    test("the tool output is a fixed point — applying twice changes nothing") {
        val once = applyTime("14:45")
        applyTime(once) shouldBe once
    }
})
