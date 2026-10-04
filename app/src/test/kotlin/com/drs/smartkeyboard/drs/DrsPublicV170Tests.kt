/*
 * Copyright (C) 2026 The DRS Smart Keyboard Project
 */

package com.drs.smartkeyboard.drs

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

/**
 * الإصدار العام v1.7.0 — التوسعة الثانية: التفقيط المالي الحتمي.
 *
 * [DrsTafqit] turns a clean monetary amount into its formal check-writing
 * Arabic words — «فقط … لا غير» — with every number word coming from
 * [DrsNumberWords] (one source of truth) and every agreement decision
 * documented and pinned here:
 *
 *  - counted-noun selection: ريال واحد / ريالان / ثلاثة ريالات / مائة ريال
 *    (bare singular without tanwin, the house orthography);
 *  - feminine subunit counting (هللة، سنت): إحدى عشرة، ثلاث هللات،
 *    تسع وتسعون — versus the masculine reuse (قرش، فلس);
 *  - the closed five-currency catalog (ريال، دولار، يورو، جنيه، درهم)
 *    with tail-peeled tokens (Latin case-insensitive, Arabic byte-exact);
 *  - the fail-closed grammar: thousands groups of exactly three digits,
 *    one or two decimal digits at most, twelve-digit ceiling — anything
 *    else returns null and the tool leaves the field byte-identical.
 */
class DrsPublicV170Tests : FunSpec({

    // تعريفات محلية داخل جسم المواصفة — Kotest FunSpec lambda.
    fun tafqit(text: String): String? = DrsTafqit.tafqitOrNull(text)

    // -------------------------------------------------------------
    // صيغة العدد المعدود — the counted-noun selection (whole part)
    // -------------------------------------------------------------

    test("the singular noun stands before واحد and the dual absorbs the two") {
        tafqit("1") shouldBe "فقط ريال واحد لا غير"
        tafqit("2") shouldBe "فقط ريالان لا غير"
    }

    test("three to ten count with the broken plural") {
        tafqit("5") shouldBe "فقط خمسة ريالات لا غير"
        tafqit("10") shouldBe "فقط عشرة ريالات لا غير"
    }

    test("eleven and above take the bare singular — the house no-tanwin orthography") {
        tafqit("12") shouldBe "فقط اثنا عشر ريال لا غير"
        tafqit("100") shouldBe "فقط مائة ريال لا غير"
        tafqit("200") shouldBe "فقط مائتان ريال لا غير"
        tafqit("1000") shouldBe "فقط ألف ريال لا غير"
        tafqit("1000000") shouldBe "فقط مليون ريال لا غير"
    }

    test("compounds ending in one or two still take the bare singular — one documented rule") {
        tafqit("101") shouldBe "فقط مائة وواحد ريال لا غير"
        tafqit("105") shouldBe "فقط مائة وخمسة ريالات لا غير"
    }

    // -------------------------------------------------------------
    // الهلالة المؤنثة — the feminine subunit grammar
    // -------------------------------------------------------------

    test("the decimal part counts with the feminine forms") {
        tafqit("12.05") shouldBe "فقط اثنا عشر ريال وخمس هللات لا غير"
        tafqit("12.5") shouldBe "فقط اثنا عشر ريال وخمس هللات لا غير"
        tafqit("0.10") shouldBe "فقط عشر هللات لا غير"
        tafqit("11.11") shouldBe "فقط أحد عشر ريال وإحدى عشرة هللة لا غير"
        tafqit("21.21") shouldBe "فقط واحد وعشرون ريال وإحدى وعشرون هللة لا غير"
        tafqit("99.99") shouldBe "فقط تسعة وتسعون ريال وتسع وتسعون هللة لا غير"
    }

    test("the subunit specials mirror the whole-part selection") {
        tafqit("1.01") shouldBe "فقط ريال واحد وهللة واحدة لا غير"
        tafqit("1.02") shouldBe "فقط ريال واحد وهللتان لا غير"
        tafqit("3.03") shouldBe "فقط ثلاثة ريالات وثلاث هللات لا غير"
    }

    test("a zero whole part disappears when a subunit remains, and zero-amount speaks itself") {
        tafqit("0.50") shouldBe "فقط خمسون هللة لا غير"
        tafqit("0.00") shouldBe "فقط صفر ريال لا غير"
    }

    // -------------------------------------------------------------
    // الكتالوج المغلق — the closed currency catalog
    // -------------------------------------------------------------

    test("peeled tokens select the currency: dollar, euro, gineih, dirham") {
        tafqit("25.75$") shouldBe "فقط خمسة وعشرون دولار وخمس وسبعون سنت لا غير"
        tafqit("25 يورو") shouldBe "فقط خمسة وعشرون يورو لا غير"
        tafqit("25.75 يورو") shouldBe "فقط خمسة وعشرون يورو وخمس وسبعون سنت لا غير"
        tafqit("2 يورو") shouldBe "فقط يوروان لا غير"
        tafqit("3 جنيه") shouldBe "فقط ثلاثة جنيهات لا غير"
        tafqit("2.02 جنيه") shouldBe "فقط جنيهان وقرشان لا غير"
        tafqit("12.30 درهم") shouldBe "فقط اثنا عشر درهم وثلاثون فلس لا غير"
        tafqit("5.25 درهم") shouldBe "فقط خمسة دراهم وخمسة وعشرون فلس لا غير"
    }

    test("tokens may be attached to the last digit and Latin ones are case-insensitive") {
        tafqit("12\uFDFC") shouldBe "فقط اثنا عشر ريال لا غير"
        tafqit("5.5USD") shouldBe "فقط خمسة دولارات وخمس سنتات لا غير"
        tafqit("5.5usd") shouldBe "فقط خمسة دولارات وخمس سنتات لا غير"
    }

    test("masculine subunits reuse the standard number words of DrsNumberWords") {
        tafqit("2.02 درهم") shouldBe "فقط درهمان وفلسان لا غير"
        tafqit("1.01 جنيه") shouldBe "فقط جنيه واحد وقرش واحد لا غير"
    }

    // -------------------------------------------------------------
    // الأرقام العربية والمجموعات — locale-blind digits and grouping
    // -------------------------------------------------------------

    test("Arabic-Indic and Extended digits, Arabic separators and Western commas all parse") {
        tafqit("١٢٣٤٫٥٠") shouldBe "فقط ألف ومائتان وأربعة وثلاثون ريال وخمسون هللة لا غير"
        tafqit("١٢٬٣٤٥") shouldBe "فقط اثنا عشر ألف وثلاثمائة وخمسة وأربعون ريال لا غير"
        tafqit("12,345.60") shouldBe "فقط اثنا عشر ألف وثلاثمائة وخمسة وأربعون ريال وستون هللة لا غير"
        tafqit("1,000,000") shouldBe "فقط مليون ريال لا غير"
        tafqit("1234.50") shouldBe "فقط ألف ومائتان وأربعة وثلاثون ريال وخمسون هللة لا غير"
    }

    test("a negative amount keeps its sign as the word سالب") {
        tafqit("-5.50") shouldBe "فقط سالب خمسة ريالات وخمسون هللة لا غير"
    }

    test("surrounding whitespace is tolerated") {
        tafqit(" 5 ") shouldBe "فقط خمسة ريالات لا غير"
        tafqit(" 12.30 درهم ") shouldBe "فقط اثنا عشر درهم وثلاثون فلس لا غير"
    }

    // -------------------------------------------------------------
    // الفشل الصادق — the fail-closed grammar (honest no-ops)
    // -------------------------------------------------------------

    test("anything that is not a clean amount returns null and stays untouched") {
        tafqit("عام 2026") shouldBe null
        tafqit("12.345") shouldBe null // three decimals — never a wrong subunit
        tafqit("12,34") shouldBe null // a thousands group is EXACTLY three digits
        tafqit("1.2.3") shouldBe null
        tafqit("12.") shouldBe null
        tafqit(".50") shouldBe null
        tafqit("abc ريال") shouldBe null
        tafqit("12 ريالات") shouldBe null // only the bare singular token peels
        tafqit("ريال 12") shouldBe null // the token must END the field
        tafqit("$25.75") shouldBe null // the prefix position is not a token
        tafqit("١٢٣٤٥٦٧٨٩٠١٢٣٤") shouldBe null // thirteen digits — beyond the ceiling
        tafqit("") shouldBe null
        tafqit("   ") shouldBe null
        tafqit("ريال") shouldBe null
        tafqit("-") shouldBe null
        tafqit("1٬٬234") shouldBe null
        tafqit("xusd") shouldBe null // glued to a letter, not a digit
        tafqit("pay 5 usd") shouldBe null // the token peels, the rest fails — fail-closed
    }

    test("the envelope output is itself a no-op (no digits to re-parse)") {
        val once = tafqit("1234.50")!!
        tafqit(once) shouldBe null
    }

    // -------------------------------------------------------------
    // الأداة في الكتالوج — the TAFQIT tool wiring (v1.7.0)
    // -------------------------------------------------------------

    test("TAFQIT registers at -660 in the catalogue") {
        val tool = DrsTextTool.TAFQIT
        tool.code shouldBe -660
        DrsTextTool.fromCode(-660) shouldBe tool
        DrsTextTool.CODE_RANGE.first shouldBe -673
        (tool.code in DrsTextTool.CODE_RANGE) shouldBe true
        // 53 tools through v1.6.0 + the tafqit tool + the v1.8.0 date
        // tool + the v1.9.0 clock-time tool + the v1.10.0 fraction tool.
        DrsTextTool.entries.size shouldBe 67
        (tool.isInfoOnly) shouldBe false
        (tool.isEditorOp) shouldBe false
        (tool.isInsertMark) shouldBe false
    }

    fun applyTafqit(text: String) = DrsTextTools.apply(DrsTextTool.TAFQIT, text)

    test("the tool converts a clean amount and passes prose byte-identical") {
        applyTafqit("1234.50") shouldBe "فقط ألف ومائتان وأربعة وثلاثون ريال وخمسون هللة لا غير"
        applyTafqit("12,345.60 ريال") shouldBe
            "فقط اثنا عشر ألف وثلاثمائة وخمسة وأربعون ريال وستون هللة لا غير"
        applyTafqit("عام 2026") shouldBe "عام 2026"
        applyTafqit("") shouldBe ""
    }

    test("the tool output is a fixed point — applying it twice changes nothing") {
        val once = applyTafqit("99.99")
        applyTafqit(once) shouldBe once
    }
})
