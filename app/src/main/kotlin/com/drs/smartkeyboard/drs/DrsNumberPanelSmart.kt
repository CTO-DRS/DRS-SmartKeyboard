/*
 * Copyright (C) 2025-2026 The DRS Smart Keyboard Project
 */

package com.drs.smartkeyboard.drs

import java.time.LocalDate
import java.time.chrono.HijrahDate
import java.time.temporal.ChronoField

/**
 * DRS v1.2.0 — لوحة الأرقام الذكية (the smart numbers panel) — the PURE
 * logic core. The doctrine holds: a deterministic on-device algorithm,
 * no cloud, no AI — the field class reported by the editor plus the live
 * text before the cursor decide the active context, and every ready
 * format (phone, Gregorian, Hijri, currency) is computed locally from
 * fixed arithmetic rules.
 *
 * الأنظمة الأربعة:
 *  1. كاشف السياق (7 قواعد صريحة): فئة الحقل الخام من EditorInfo ثم
 *     تنقيح صادق من النص قبل المؤشر (+/00 → هاتف، رمز عملة → مبلغ،
 *     سلسلة أرقام طويلة → رقمية).
 *  2. مولّد تنسيقات الهاتف: التجميع السعودي المحلي (0555 123 456)
 *     والدولي (+966 55 512 3456) والصورة بالأرقام العربية.
 *  3. مولّد التواريخ: الميلادي بأسماء الأشهر العربية والهجري عبر
 *     HijrahDate — كلها حساب محلي خالص بلا أي طلب شبكة.
 *  4. مولّد العملات السبع: توحيد المبلغ (آخر فاصل عشري يفوز) ثم
 *     التجميع الثلاثي ثم لصق رموز العملات السبع.
 *
 * لا شيء هنا يلمس Android UI أو Context، فكل عقد مغطى باختبارات JVM
 * تمامًا كبقية طبقة DRS.
 */

/**
 * فئة حقل الإدخال الرقمي — the raw class of the focused editor as the
 * number panel sees it. Derived once per input start from EditorInfo
 * (see [DrsRuntimeState]) then refined by the live text.
 */
enum class DrsNumberFieldClass {
    /** A one-time-code field (number+password). Digits only — no separators. */
    OTP,

    /** A phone field (or a +/00 number being typed in a free field). */
    PHONE,

    /** A money field (decimal number, or a currency glyph in the text). */
    MONEY,

    /** A date/time field. */
    DATE,

    /** A plain number field (counts, quantities, math). */
    MATH,

    /** A long digit run in a free field (national ID, order number...). */
    ID,

    /** Everything else — the honest default. */
    GENERAL,
}

object DrsNumberPanelSmart {

    // ------------------------------------------------------------------
    // 1) كاشف السياق — the 7 explicit rules
    // ------------------------------------------------------------------

    /** The glyphs that mark a money context wherever they appear. */
    val CURRENCY_HINTS: List<String> = listOf(
        "﷼", "ر.س", "د.إ", "ج.م", "د.ك", "ر.ق", "$", "€", "£", "¥", "₽", "₹", "₺",
    )

    private val MATH_OP_TAIL = setOf('+', '−', '-', '×', '÷', '=', '/', '^', '%')

    /**
     * The seven rules, in order — the first that fires wins:
     *  1. [DrsNumberFieldClass.OTP] stays OTP (a password-number field
     *     must never grow separators or hints).
     *  2. PHONE field, or a leading +/00 run in the text → PHONE.
     *  3. DATE field → DATE.
     *  4. MONEY field, or a currency glyph in the text → MONEY.
     *  5. MATH field, or a trailing math operator → MATH.
     *  6. a free field whose tail holds a digit run of 10+ → ID.
     *  7. otherwise the field class itself (GENERAL for free fields).
     */
    fun detectContext(fieldClass: DrsNumberFieldClass, textBeforeCursor: String): DrsNumberFieldClass {
        // rule 1 — the OTP field is non-negotiable: digits only.
        if (fieldClass == DrsNumberFieldClass.OTP) return DrsNumberFieldClass.OTP
        // rule 2 — phones: the field itself, or an international prefix.
        if (fieldClass == DrsNumberFieldClass.PHONE) return DrsNumberFieldClass.PHONE
        val tail = trailingNumberRun(textBeforeCursor)
        if (tail.startsWith("+") || tail.startsWith("00")) return DrsNumberFieldClass.PHONE
        // rule 3 — the platform already knows it is a date/time field.
        if (fieldClass == DrsNumberFieldClass.DATE) return DrsNumberFieldClass.DATE
        // rule 4 — money: the field class or a currency glyph in the text.
        if (fieldClass == DrsNumberFieldClass.MONEY) return DrsNumberFieldClass.MONEY
        if (CURRENCY_HINTS.any { textBeforeCursor.contains(it) }) return DrsNumberFieldClass.MONEY
        // rule 5 — math: the field class or a trailing operator.
        if (fieldClass == DrsNumberFieldClass.MATH) return DrsNumberFieldClass.MATH
        if (textBeforeCursor.lastOrNull() in MATH_OP_TAIL) return DrsNumberFieldClass.MATH
        // rule 6 — a long bare digit run in a free field is an ID.
        if (fieldClass == DrsNumberFieldClass.GENERAL && tail.length >= 10) {
            return DrsNumberFieldClass.ID
        }
        // rule 7 — the honest fallback.
        return fieldClass
    }

    /**
     * The trailing number-ish run of [text] (ASCII or Arabic-Indic digits
     * with . , ٫ ٬ and a leading +) — the window the rules read.
     */
    fun trailingNumberRun(text: String): String {
        if (text.isEmpty()) return ""
        var start = text.length
        while (start > 0) {
            val c = text[start - 1]
            val isNum = c.isDigit() || c in "+.,٫٬-"
            if (!isNum) break
            start--
        }
        return toAsciiDigits(text.substring(start))
            .replace('٫', '.')
            .replace('٬', ',')
    }

    // ------------------------------------------------------------------
    // 2) مولّد تنسيقات الهاتف
    // ------------------------------------------------------------------

    /**
     * The ready phone formats for the typed digits: the Saudi local
     * grouping (0555 123 456), the international form (+966 55 512 3456)
     * and the Arabic-Indic rendering — or an honest empty list when no
     * digits were typed at all.
     */
    fun phoneFormats(raw: String): List<String> {
        val digits = toAsciiDigits(raw).filter { it.isDigit() }
        if (digits.isEmpty()) return emptyList()
        val saudiMobile = digits.length == 10 && digits.startsWith("05")
        val local = if (saudiMobile) {
            "${digits.take(4)} ${digits.drop(4).take(3)} ${digits.takeLast(3)}"
        } else {
            digits.chunked(3).joinToString(" ")
        }
        val intl = if (saudiMobile) {
            "+966 ${digits.drop(1).take(2)} ${digits.drop(3).take(3)} ${digits.takeLast(4)}"
        } else {
            null
        }
        return listOfNotNull(local, intl, toArabicDigits(local)).distinct()
    }

    // ------------------------------------------------------------------
    // 3) مولّد التواريخ (ميلادي + هجري)
    // ------------------------------------------------------------------

    /** The Gregorian month names (Arabic, deterministic — no locale IO). */
    val GREGORIAN_MONTHS_AR: List<String> = listOf(
        "يناير", "فبراير", "مارس", "أبريل", "مايو", "يونيو",
        "يوليو", "أغسطس", "سبتمبر", "أكتوبر", "نوفمبر", "ديسمبر",
    )

    /** The Hijri month names (Arabic). */
    val HIJRI_MONTHS_AR: List<String> = listOf(
        "محرم", "صفر", "ربيع الأول", "ربيع الآخر", "جمادى الأولى", "جمادى الآخرة",
        "رجب", "شعبان", "رمضان", "شوال", "ذو القعدة", "ذو الحجة",
    )

    /** The ready Gregorian formats of [date] (named, ISO, Arabic digits). */
    fun gregorianFormats(date: LocalDate): List<String> {
        val named = "${date.dayOfMonth} ${GREGORIAN_MONTHS_AR[date.monthValue - 1]} ${date.year}"
        val iso = "%04d/%02d/%02d".format(date.year, date.monthValue, date.dayOfMonth)
        val arabic = toArabicDigits("%02d/%02d/%04d".format(date.dayOfMonth, date.monthValue, date.year))
        return listOf(named, iso, arabic)
    }

    /** The ready Hijri formats of [date] (named western, named Arabic digits). */
    fun hijriFormats(date: HijrahDate): List<String> {
        val year = date.get(ChronoField.YEAR)
        val month = HIJRI_MONTHS_AR[date.get(ChronoField.MONTH_OF_YEAR) - 1]
        val day = date.get(ChronoField.DAY_OF_MONTH)
        val western = "$day $month $year هـ"
        val arabic = "${toArabicDigits("$day")} $month ${toArabicDigits("$year")} هـ"
        return listOf(western, arabic)
    }

    // ------------------------------------------------------------------
    // 4) مولّد العملات السبع
    // ------------------------------------------------------------------

    /** The seven currencies of the ready-format row (symbol first). */
    val CURRENCIES: List<String> = listOf(
        "ر.س", "د.إ", "ج.م", "د.ك", "ر.ق", "$", "€",
    )

    /**
     * The canonical amount of [raw]: Arabic-Indic digits and Arabic
     * separators are folded to ASCII, then the LAST '.' or ',' decides —
     * a ',' followed by EXACTLY 3 digits is a thousands separator (no
     * fraction), any '.' and any ',' followed by 1–2 digits is the
     * DECIMAL point — deterministic, no guessing:
     *  - «12.5»     → «12.5»
     *  - «12,345.6» → «12345.6»
     *  - «12,345»   → «12345» (a 3-digit group after a comma is thousands)
     *  - «12.345»   → «12.345» (a dot is always a decimal point)
     *  - «12.5.6»   → «125.6» (garbage folded honestly)
     *  - «١٢٬٣٤٥»   → «12345»
     */
    fun normaliseAmount(raw: String): String {
        val ascii = toAsciiDigits(raw).replace('٫', '.').replace('٬', ',')
        val tail = ascii.filter { it.isDigit() || it == '.' || it == ',' }
        if (tail.isEmpty()) return ""
        val lastSep = tail.indexOfLast { it == '.' || it == ',' }
        if (lastSep < 0) return tail
        val after = tail.length - lastSep - 1
        val isThousands = tail[lastSep] == ',' && after == 3
        if (isThousands) return tail.filter { it.isDigit() }
        val intPart = tail.take(lastSep).filter { it.isDigit() }
        val fracPart = tail.drop(lastSep + 1).filter { it.isDigit() }
        return "$intPart.$fracPart"
    }

    /**
     * The unified money form of [raw]: [normaliseAmount] then a hard
     * two-decimal pad/truncate — «12.5» → «12.50», «12345.678» →
     * «12345.67» (an explicit cut, never a hidden rounding).
     */
    fun unifyAmount(raw: String): String {
        val normal = normaliseAmount(raw)
        if (normal.isEmpty()) return ""
        val dot = normal.indexOf('.')
        val intPart = if (dot < 0) normal else normal.take(dot)
        val frac = if (dot < 0) "" else normal.drop(dot + 1)
        val frac2 = frac.take(2).padEnd(2, '0')
        return "$intPart.$frac2"
    }

    /**
     * The three-by-three grouping of a unified amount — «12345.60» →
     * «12,345.60» — the step the v1.2.0 currency generator used to get
     * wrong («12345.6.0»); now a single pure pass.
     */
    fun formatGrouped(unified: String): String {
        val dot = unified.indexOf('.')
        if (dot < 0) return groupThrees(unified)
        val intPart = groupThrees(unified.take(dot))
        return "$intPart.${unified.drop(dot + 1)}"
    }

    private fun groupThrees(digits: String): String {
        if (digits.length <= 3) return digits
        val head = digits.length % 3
        val parts = mutableListOf<String>()
        if (head > 0) parts.add(digits.take(head))
        var i = head
        while (i < digits.length) {
            parts.add(digits.substring(i, i + 3))
            i += 3
        }
        return parts.joinToString(",")
    }

    /**
     * The ready currency formats: the seven symbols over the unified
     * grouped amount, then the Arabic-Indic riyal rendering — eight
     * honest chips, all computed locally.
     */
    fun currencyFormats(raw: String): List<String> {
        val unified = unifyAmount(raw)
        if (unified.isEmpty() || unified == ".") return emptyList()
        val grouped = formatGrouped(unified)
        return CURRENCIES.map { "$it $grouped" } +
            "${toArabicMoney(grouped)} ${CURRENCIES.first()}"
    }

    // ------------------------------------------------------------------
    // 5) كتالوج الشبكة لكل سياق — the per-context tile catalogue
    // ------------------------------------------------------------------

    private const val DIGITS = "١٢٣٤٥٦٧٨٩٠"

    /**
     * The grid tiles per context — every context carries the ten
     * Arabic-Indic digits plus its own honest extras; the OTP context
     * carries the digits ALONE (a code field never grows a separator).
     */
    fun gridFor(context: DrsNumberFieldClass): List<String> = when (context) {
        DrsNumberFieldClass.GENERAL -> tiles("$DIGITS٫٬+−×÷")
        DrsNumberFieldClass.PHONE -> tiles("$DIGITS+#*()−")
        DrsNumberFieldClass.MONEY -> tiles("$DIGITS٫٬") + listOf("٠٠", "﷼", "ر.س", "€")
        DrsNumberFieldClass.DATE -> tiles("$DIGITS/−:صم")
        DrsNumberFieldClass.OTP -> tiles(DIGITS)
        DrsNumberFieldClass.MATH -> tiles("$DIGITS.+−×÷=")
        DrsNumberFieldClass.ID -> tiles("$DIGITS/−")
    }

    private fun tiles(chars: String): List<String> = chars.map { it.toString() }

    // ------------------------------------------------------------------
    // 6) أدوات الأرقام المشتركة
    // ------------------------------------------------------------------

    /** «0123456789» → «٠١٢٣٤٥٦٧٨٩» (ASCII digits only, pass-through else). */
    fun toArabicDigits(text: String): String = buildString {
        for (c in text) {
            append(if (c in '0'..'9') ('٠' + (c - '0')) else c)
        }
    }

    /** «٠١٢٣٤٥٦٧٨٩» → «0123456789» (Arabic-Indic digits only). */
    fun toAsciiDigits(text: String): String = buildString {
        for (c in text) {
            append(if (c in '٠'..'٩') ('0' + (c - '٠')) else c)
        }
    }

    /** The Arabic-Indic money rendering: digits + ٬ + ٫. */
    fun toArabicMoney(text: String): String = toArabicDigits(text)
        .replace(',', '٬')
        .replace('.', '٫')

    /** The trailing digits run (either digit system) — the phone feed. */
    fun trailingDigits(text: String): String {
        val run = trailingNumberRun(text)
        return toAsciiDigits(run).filter { it.isDigit() }
    }

    /** The trailing amount run (digits with decimal separators) — the money feed. */
    fun trailingAmount(text: String): String {
        val run = trailingNumberRun(text)
        return run.filter { it.isDigit() || it == '.' || it == ',' }
    }
}
