/*
 * Copyright (C) 2025-2026 The DRS Smart Keyboard Project
 */

package com.drs.smartkeyboard.drs

import com.drs.smartkeyboard.ime.ImeUiMode
import com.drs.smartkeyboard.ime.text.key.KeyCode
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import java.time.LocalDate
import java.time.chrono.HijrahDate

/**
 * الإصدار العام v1.2.0 — لوحة الأرقام الذكية (the smart numbers panel):
 * the 7-rule context detector, the phone/Gregorian/Hijri/currency
 * format generators, the per-context grid catalogues and the tail-append
 * catalogue contract of the new tool. Every contract here is pure and
 * deterministic — the doctrine: an algorithm, not AI, all on device.
 */
class DrsPublicV120Tests : FunSpec({

    // -------------------------------------------------------------
    // كاشف السياق — the 7 explicit rules
    // -------------------------------------------------------------

    test("rule 1: an OTP field stays OTP regardless of the typed text") {
        DrsNumberPanelSmart.detectContext(
            DrsNumberFieldClass.OTP,
            "12,345.6﷼ +",
        ) shouldBe DrsNumberFieldClass.OTP
    }

    test("rule 2: a phone field and a +/00 run both report PHONE") {
        DrsNumberPanelSmart.detectContext(
            DrsNumberFieldClass.PHONE,
            "",
        ) shouldBe DrsNumberFieldClass.PHONE
        DrsNumberPanelSmart.detectContext(
            DrsNumberFieldClass.GENERAL,
            "اتصل بي على +96655",
        ) shouldBe DrsNumberFieldClass.PHONE
        DrsNumberPanelSmart.detectContext(
            DrsNumberFieldClass.GENERAL,
            "0096655123456",
        ) shouldBe DrsNumberFieldClass.PHONE
    }

    test("rule 3: a datetime field reports DATE") {
        DrsNumberPanelSmart.detectContext(
            DrsNumberFieldClass.DATE,
            "",
        ) shouldBe DrsNumberFieldClass.DATE
    }

    test("rule 4: a decimal field or a currency glyph reports MONEY") {
        DrsNumberPanelSmart.detectContext(
            DrsNumberFieldClass.MONEY,
            "",
        ) shouldBe DrsNumberFieldClass.MONEY
        DrsNumberPanelSmart.detectContext(
            DrsNumberFieldClass.GENERAL,
            "المبلغ 1500﷼",
        ) shouldBe DrsNumberFieldClass.MONEY
        DrsNumberPanelSmart.detectContext(
            DrsNumberFieldClass.GENERAL,
            "Paid $50",
        ) shouldBe DrsNumberFieldClass.MONEY
    }

    test("rule 5: a number field or a trailing operator reports MATH") {
        DrsNumberPanelSmart.detectContext(
            DrsNumberFieldClass.MATH,
            "",
        ) shouldBe DrsNumberFieldClass.MATH
        DrsNumberPanelSmart.detectContext(
            DrsNumberFieldClass.GENERAL,
            "12+",
        ) shouldBe DrsNumberFieldClass.MATH
    }

    test("rule 6: a long bare digit run in a free field reports ID") {
        DrsNumberPanelSmart.detectContext(
            DrsNumberFieldClass.GENERAL,
            "الرقم 1098765432",
        ) shouldBe DrsNumberFieldClass.ID
    }

    test("rule 7: the honest fallback is the field class itself") {
        DrsNumberPanelSmart.detectContext(
            DrsNumberFieldClass.GENERAL,
            "hello",
        ) shouldBe DrsNumberFieldClass.GENERAL
    }

    test("the trailing number run folds the Arabic separators") {
        DrsNumberPanelSmart.trailingNumberRun("abc ١٢٫٥") shouldBe "12.5"
        DrsNumberPanelSmart.trailingNumberRun("x 12٬345") shouldBe "12,345"
        DrsNumberPanelSmart.trailingNumberRun("سلام") shouldBe ""
    }

    // -------------------------------------------------------------
    // مولّد تنسيقات الهاتف
    // -------------------------------------------------------------

    test("phoneFormats groups a Saudi mobile locally and internationally") {
        val formats = DrsNumberPanelSmart.phoneFormats("0555123456")
        formats shouldContainExactly listOf(
            "0555 123 456",
            "+966 55 512 3456",
            "٠٥٥٥ ١٢٣ ٤٥٦",
        )
    }

    test("phoneFormats accepts Arabic-Indic digits and degrades honestly") {
        DrsNumberPanelSmart.phoneFormats("٠٥٥٥١٢٣٤٥٦").first() shouldBe "0555 123 456"
        // The generic grouping is threes from the left — it evolves as
        // the user types, and the Arabic-Indic rendering follows it.
        DrsNumberPanelSmart.phoneFormats("12345") shouldContainExactly listOf(
            "123 45",
            "١٢٣ ٤٥",
        )
        DrsNumberPanelSmart.phoneFormats("abc") shouldContainExactly emptyList()
    }

    // -------------------------------------------------------------
    // مولّد التواريخ (ميلادي + هجري)
    // -------------------------------------------------------------

    test("gregorianFormats renders the named, ISO and Arabic forms") {
        DrsNumberPanelSmart.gregorianFormats(LocalDate.of(2026, 4, 20)) shouldContainExactly listOf(
            "20 أبريل 2026",
            "2026/04/20",
            "٢٠/٠٤/٢٠٢٦",
        )
    }

    test("hijriFormats renders Ramadan 1447 in both digit systems") {
        val formats = DrsNumberPanelSmart.hijriFormats(HijrahDate.of(1447, 9, 1))
        formats shouldContainExactly listOf(
            "1 رمضان 1447 هـ",
            "١ رمضان ١٤٤٧ هـ",
        )
    }

    // -------------------------------------------------------------
    // مولّد العملات السبع
    // -------------------------------------------------------------

    test("normaliseAmount folds Arabic digits and the last separator wins") {
        DrsNumberPanelSmart.normaliseAmount("12.5") shouldBe "12.5"
        DrsNumberPanelSmart.normaliseAmount("12,345.6") shouldBe "12345.6"
        DrsNumberPanelSmart.normaliseAmount("12.5.6") shouldBe "125.6"
        DrsNumberPanelSmart.normaliseAmount("١٢٬٣٤٥") shouldBe "12345"
        DrsNumberPanelSmart.normaliseAmount("١٢٫٥") shouldBe "12.5"
        DrsNumberPanelSmart.normaliseAmount("abc") shouldBe ""
    }

    test("unifyAmount pads or cuts to exactly two decimals") {
        DrsNumberPanelSmart.unifyAmount("12.5") shouldBe "12.50"
        DrsNumberPanelSmart.unifyAmount("12345.678") shouldBe "12345.67"
        DrsNumberPanelSmart.unifyAmount("12345") shouldBe "12345.00"
    }

    test("formatGrouped never produces a double separator again") {
        // The v1.2.0 regression: «12.5» once came out as «12345.6.0» —
        // the grouped form is now a single pure pass.
        DrsNumberPanelSmart.formatGrouped("12345.60") shouldBe "12,345.60"
        DrsNumberPanelSmart.formatGrouped("123.00") shouldBe "123.00"
        DrsNumberPanelSmart.formatGrouped("1000000.00") shouldBe "1,000,000.00"
    }

    test("currencyFormats yields the seven symbols plus the Arabic riyal") {
        val formats = DrsNumberPanelSmart.currencyFormats("12345.6")
        formats shouldHaveSize 8
        formats.first() shouldBe "ر.س 12,345.60"
        formats.take(7).forEach { it shouldContain "12,345.60" }
        formats.last() shouldBe "١٢٬٣٤٥٫٦٠ ر.س"
        DrsNumberPanelSmart.currencyFormats("abc") shouldContainExactly emptyList()
    }

    // -------------------------------------------------------------
    // كتالوج الشبكة لكل سياق
    // -------------------------------------------------------------

    test("every context grid carries the ten digits and no duplicates") {
        val arabicDigits = "١٢٣٤٥٦٧٨٩٠".map { it.toString() }
        DrsNumberFieldClass.entries.forEach { context ->
            val grid = DrsNumberPanelSmart.gridFor(context)
            arabicDigits.forEach { digit -> grid.contains(digit) shouldBe true }
            grid.size shouldBe grid.toSet().size
        }
    }

    test("the OTP grid is digits only — a code field never grows a separator") {
        val grid = DrsNumberPanelSmart.gridFor(DrsNumberFieldClass.OTP)
        grid shouldHaveSize 10
        grid.forEach { tile ->
            tile.length shouldBe 1
            (tile.first() in '٠'..'٩') shouldBe true
        }
    }

    test("the general and money grids carry 16 tiles each") {
        DrsNumberPanelSmart.gridFor(DrsNumberFieldClass.GENERAL) shouldHaveSize 16
        DrsNumberPanelSmart.gridFor(DrsNumberFieldClass.MONEY) shouldHaveSize 16
    }

    // -------------------------------------------------------------
    // الربط الحقيقي — the real engine wiring
    // -------------------------------------------------------------

    test("SMART_NUMBER is UI mode 7 and round-trips") {
        ImeUiMode.SMART_NUMBER.value shouldBe 7
        ImeUiMode.fromInt(7) shouldBe ImeUiMode.SMART_NUMBER
        ImeUiMode.SMART_NUMBER.toInt() shouldBe 7
    }

    test("the panel tool dispatches the real engine code") {
        val tool = DrsUnifiedTools.byId("smart_number_panel").shouldNotBeNull()
        tool.code shouldBe KeyCode.IME_UI_MODE_SMART_NUMBER
        KeyCode.IME_UI_MODE_SMART_NUMBER shouldBe -229
    }

    test("the panel joins the catalogue tail with the same append contract") {
        // DRS v1.3.0 appended the smart clipboard panel after the numbers
        // panel — the append contract keeps every persisted arrangement.
        DrsUnifiedTools.ALL.last().id shouldBe "smart_clipboard_panel"
        DrsUnifiedTools.ALL.size shouldBe 51
        DrsUnifiedTools.ALL[DrsUnifiedTools.ALL.size - 2].id shouldBe "smart_number_panel"
        DrsUnifiedTools.ALL[DrsUnifiedTools.ALL.size - 3].id shouldBe "merge_keyboard"
    }

    test("the context bar keeps the selected context first, then usage") {
        val usage = mapOf("PHONE" to 5, "MONEY" to 9)
        val order = DrsPanelOrder.smartSwitcher(
            DrsNumberFieldClass.DATE,
            usage,
            listOf(
                DrsNumberFieldClass.GENERAL,
                DrsNumberFieldClass.PHONE,
                DrsNumberFieldClass.MONEY,
                DrsNumberFieldClass.DATE,
            ),
        )
        order shouldContainExactly listOf(
            DrsNumberFieldClass.DATE,
            DrsNumberFieldClass.MONEY,
            DrsNumberFieldClass.PHONE,
            DrsNumberFieldClass.GENERAL,
        )
    }
})
