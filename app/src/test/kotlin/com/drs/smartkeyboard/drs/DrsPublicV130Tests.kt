/*
 * Copyright (C) 2025-2026 The DRS Smart Keyboard Project
 */

package com.drs.smartkeyboard.drs

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe

/**
 * الإصدار العام v1.3.0 — الحافظة الذكية الحتمية (the deterministic smart
 * clipboard): the first-wins content classifier, the tracking-parameter
 * link surgery, the Saudi phone normalizations, the bounded keyword-
 * anchored OTP extractor, the seven-currency amount feed, the digit
 * flip, and the honest preview label. Every contract pure, every rule
 * explicit — the doctrine: an algorithm, not AI, all on device.
 */
class DrsPublicV130Tests : FunSpec({

    // -------------------------------------------------------------
    // مصنّف المحتوى — the first-wins rule chain
    // -------------------------------------------------------------

    test("blank text is honestly TEXT with nothing to offer") {
        DrsClipContentSmart.classify("") shouldBe DrsClipContentClass.TEXT
        DrsClipContentSmart.classify("   \n  ") shouldBe DrsClipContentClass.TEXT
    }

    test("rule 1: http(s) and www links classify URL") {
        DrsClipContentSmart.classify("https://example.com/page?q=1") shouldBe DrsClipContentClass.URL
        DrsClipContentSmart.classify("http://example.org") shouldBe DrsClipContentClass.URL
        DrsClipContentSmart.classify("www.example.com/page") shouldBe DrsClipContentClass.URL
    }

    test("rule 2: an email address classifies EMAIL") {
        DrsClipContentSmart.classify("user.name+tag@example.co.uk") shouldBe DrsClipContentClass.EMAIL
    }

    test("rule 3: the Saudi phone shapes classify PHONE") {
        DrsClipContentSmart.classify("0555123456") shouldBe DrsClipContentClass.PHONE
        DrsClipContentSmart.classify("555123456") shouldBe DrsClipContentClass.PHONE
        DrsClipContentSmart.classify("+966555123456") shouldBe DrsClipContentClass.PHONE
        DrsClipContentSmart.classify("9660555123456") shouldBe DrsClipContentClass.PHONE
        DrsClipContentSmart.classify("+12025550123") shouldBe DrsClipContentClass.PHONE
        DrsClipContentSmart.classify("٠٥٥٥١٢٣٤٥٦") shouldBe DrsClipContentClass.PHONE
        DrsClipContentSmart.classify("+966 55 512 3456") shouldBe DrsClipContentClass.PHONE
    }

    test("rule 4: a standalone 4-8 digit line is OTP") {
        DrsClipContentSmart.classify("1234") shouldBe DrsClipContentClass.OTP
        DrsClipContentSmart.classify("482913") shouldBe DrsClipContentClass.OTP
        DrsClipContentSmart.classify("12345678") shouldBe DrsClipContentClass.OTP
        DrsClipContentSmart.classify("٤٨٢٩١٣") shouldBe DrsClipContentClass.OTP
    }

    test("rule 5: digits plus a currency glyph classify AMOUNT") {
        DrsClipContentSmart.classify("ر.س 12345.67") shouldBe DrsClipContentClass.AMOUNT
        DrsClipContentSmart.classify("$1,234.56") shouldBe DrsClipContentClass.AMOUNT
    }

    test("rule 6: a bare number with separators classifies NUMBER") {
        DrsClipContentSmart.classify("123456789012") shouldBe DrsClipContentClass.NUMBER
        DrsClipContentSmart.classify("12.5") shouldBe DrsClipContentClass.NUMBER
        DrsClipContentSmart.classify("12,345") shouldBe DrsClipContentClass.NUMBER
        DrsClipContentSmart.classify("١٢٬٣٤٥") shouldBe DrsClipContentClass.NUMBER
    }

    test("rule 7: everything else is TEXT") {
        DrsClipContentSmart.classify("مرحبا بالعالم") shouldBe DrsClipContentClass.TEXT
        DrsClipContentSmart.classify("order shipped today") shouldBe DrsClipContentClass.TEXT
    }

    test("precedence: a URL never becomes a phone, a phone never an OTP") {
        DrsClipContentSmart.classify("https://0555123456.example") shouldBe DrsClipContentClass.URL
        DrsClipContentSmart.classify("0555123456") shouldBe DrsClipContentClass.PHONE
        DrsClipContentSmart.classify("9-digit 123456789 not a code") shouldBe DrsClipContentClass.TEXT
    }

    // -------------------------------------------------------------
    // الروابط — the tracking-parameter surgery
    // -------------------------------------------------------------

    test("cleanUrl strips only tracking parameters, order preserved") {
        DrsClipContentSmart.cleanUrl("https://example.com/page?id=7&utm_source=app&x=2") shouldBe "https://example.com/page?id=7&x=2"
    }

    test("cleanUrl is case-insensitive on the parameter key only") {
        DrsClipContentSmart.cleanUrl("https://example.com/?UTM_SOURCE=x&id=1") shouldBe "https://example.com/?id=1"
        DrsClipContentSmart.cleanUrl("https://example.com/?FBCLID=abc") shouldBe "https://example.com/"
    }

    test("cleanUrl keeps lookalike keys that are not tracking keys") {
        DrsClipContentSmart.cleanUrl("https://example.com/?referrer=site&ref=drop") shouldBe "https://example.com/?referrer=site"
    }

    test("cleanUrl drops an emptied query entirely and keeps the fragment") {
        DrsClipContentSmart.cleanUrl("https://example.com/?utm_source=a#section") shouldBe "https://example.com/#section"
    }

    test("cleanUrl returns a query-less URL unchanged") {
        DrsClipContentSmart.cleanUrl("https://example.com/page") shouldBe "https://example.com/page"
    }

    test("hostOf strips scheme, path and www") {
        DrsClipContentSmart.hostOf("https://www.example.com/a/b?q=1#f") shouldBe "example.com"
        DrsClipContentSmart.hostOf("www.example.org") shouldBe "example.org"
    }

    test("urlVariants: as-is, clean when it differs, and the bare domain") {
        DrsClipContentSmart.urlVariants("https://example.com/?utm_source=a&id=2")
            .shouldContainExactly(
                listOf("https://example.com/?utm_source=a&id=2", "https://example.com/?id=2", "example.com"),
            )
    }

    test("urlVariants dedup when there is nothing to clean") {
        DrsClipContentSmart.urlVariants("https://example.com/page")
            .shouldContainExactly(listOf("https://example.com/page", "example.com"))
    }

    // -------------------------------------------------------------
    // البريد — the domain lowercase
    // -------------------------------------------------------------

    test("emailVariants lowercases only the domain") {
        DrsClipContentSmart.emailVariants("User.Name@EXAMPLE.com")
            .shouldContainExactly(listOf("User.Name@EXAMPLE.com", "User.Name@example.com"))
    }

    test("emailVariants dedup when the domain is already lowercase") {
        DrsClipContentSmart.emailVariants("user@example.com")
            .shouldContainExactly(listOf("user@example.com"))
    }

    // -------------------------------------------------------------
    // الهاتف — the Saudi normalizations
    // -------------------------------------------------------------

    test("phoneVariants fold 05XXXXXXXX to grouped, intl and Arabic") {
        DrsClipContentSmart.phoneVariants("0555123456")
            .shouldContainExactly(
                listOf("0555123456", "0555 123 456", "+966 55 512 3456", "٠٥٥٥ ١٢٣ ٤٥٦"),
            )
    }

    test("phoneVariants restore the zero on a 9-digit mobile") {
        DrsClipContentSmart.phoneVariants("555123456")[1] shouldBe "0555 123 456"
    }

    test("phoneVariants fold 9665 and 96605 prefixes to the same local") {
        DrsClipContentSmart.phoneVariants("+966555123456")[1] shouldBe "0555 123 456"
        DrsClipContentSmart.phoneVariants("9660555123456")[2] shouldBe "+966 55 512 3456"
    }

    test("phoneVariants of a foreign number return the clip as-is alone") {
        DrsClipContentSmart.phoneVariants("+12025550123")
            .shouldContainExactly(listOf("+12025550123"))
    }

    // -------------------------------------------------------------
    // رمز التحقق — the bounded, keyword-anchored extractor
    // -------------------------------------------------------------

    test("otpFromMessage: the whole 4-8 digit text IS the code") {
        DrsClipContentSmart.otpFromMessage("1234") shouldBe "1234"
        DrsClipContentSmart.otpFromMessage("12345678") shouldBe "12345678"
    }

    test("otpFromMessage: a whole 9+ digit run refuses honestly") {
        DrsClipContentSmart.otpFromMessage("123456789").shouldBeNull()
    }

    test("otpFromMessage: a single run in a message wins") {
        DrsClipContentSmart.otpFromMessage("hello, your number 4829 is ready") shouldBe "4829"
    }

    test("otpFromMessage: the keyword-anchored run wins over another run") {
        DrsClipContentSmart.otpFromMessage("رمز التحقق 482913 صالح حتى 2026") shouldBe "482913"
        DrsClipContentSmart.otpFromMessage("Your verification code is 778899, invoice 2024") shouldBe "778899"
    }

    test("otpFromMessage: several candidates with no keyword refuse") {
        DrsClipContentSmart.otpFromMessage("meetings 1234 and 5678 this week").shouldBeNull()
    }

    test("otpFromMessage: a run inside a longer digit run is never a candidate") {
        DrsClipContentSmart.otpFromMessage("order 123456789012 confirmed").shouldBeNull()
    }

    test("otpFromMessage reads Arabic-Indic digits") {
        DrsClipContentSmart.otpFromMessage("رمز التحقق هو ٤٨٢٩١٣") shouldBe "482913"
    }

    test("otpVariants: the code and its Arabic-Indic form") {
        DrsClipContentSmart.otpVariants("رمز التحقق 482913")
            .shouldContainExactly(listOf("482913", "٤٨٢٩١٣"))
    }

    test("otpVariants of text with no code are empty") {
        DrsClipContentSmart.otpVariants("no numbers here").shouldContainExactly(emptyList())
    }

    // -------------------------------------------------------------
    // المبلغ — the seven currencies
    // -------------------------------------------------------------

    test("amountVariants feed the SAME currency generator") {
        val variants = DrsClipContentSmart.amountVariants("ر.س 12345.67")
        variants.first() shouldBe "ر.س 12,345.67"
        // سبع عملات + الرسم العربي الهندي — نفس عقد مولّد لوحة الأرقام.
        variants.size shouldBe 8
    }

    test("amountVariants of text without digits are empty") {
        DrsClipContentSmart.amountVariants("no money here").shouldContainExactly(emptyList())
    }

    // -------------------------------------------------------------
    // قلب الأرقام والنصوص
    // -------------------------------------------------------------

    test("numberVariants flip both directions with dedup") {
        DrsClipContentSmart.numberVariants("12345")
            .shouldContainExactly(listOf("12345", "١٢٣٤٥"))
        DrsClipContentSmart.numberVariants("١٢٣٤٥")
            .shouldContainExactly(listOf("١٢٣٤٥", "12345"))
    }

    test("textVariants: the digit flip only when digits exist") {
        DrsClipContentSmart.textVariants("سنة 2026 في نص")
            .shouldContainExactly(listOf("سنة 2026 في نص", "سنة ٢٠٢٦ في نص"))
        DrsClipContentSmart.textVariants("بدون أرقام").shouldContainExactly(emptyList())
    }

    test("variantsFor retargets honestly when the user picks another class") {
        DrsClipContentSmart.variantsFor(DrsClipContentClass.NUMBER, "12.50")
            .shouldContainExactly(listOf("12.50", "١٢.٥٠"))
        DrsClipContentSmart.variantsFor(DrsClipContentClass.AMOUNT, "12.50").isNotEmpty() shouldBe true
    }

    // -------------------------------------------------------------
    // التحليل الكامل وسطر المعاينة
    // -------------------------------------------------------------

    test("analyze assigns the class and its own variants") {
        val analysis = DrsClipContentSmart.analyze("0555123456")
        analysis.contentClass shouldBe DrsClipContentClass.PHONE
        analysis.variants.first() shouldBe "0555123456"
    }

    test("previewLabel collapses whitespace and caps with an ellipsis") {
        DrsClipContentSmart.previewLabel("مرحبا  بالعالم") shouldBe "مرحبا بالعالم"
        val long = "a very long clipboard line that keeps going and going forever"
        val preview = DrsClipContentSmart.previewLabel(long, maxChars = 32)
        preview.length shouldBe 32
        preview.endsWith("…") shouldBe true
    }

    test("previewLabel takes the first non-blank line") {
        DrsClipContentSmart.previewLabel("\n\n  الثانية أولًا \n الثانية") shouldBe "الثانية أولًا"
    }
})
