/*
 * Copyright (C) 2025-2026 The DRS Smart Keyboard Project
 */

package com.drs.smartkeyboard.drs

import java.util.Locale

/**
 * DRS v1.3.0 — الحافظة الذكية الحتمية (the deterministic smart
 * clipboard) — the PURE content-intelligence core. The doctrine holds:
 * a deterministic on-device algorithm, no cloud, no AI — whatever the
 * user copied is classified by explicit first-wins shape rules, and
 * every ready variant (clean link, phone normalizations, the extracted
 * code, currency formats, digit flip) is computed locally by fixed
 * string surgery. Nothing is recorded, nothing leaves the device.
 *
 * الأنظمة الستة:
 *  1. مصنّف المحتوى (7 قواعد صريحة أولها يفوز): رابط، بريد، هاتف،
 *     رمز تحقق، مبلغ، رقم، نص — كلها أشكال مغلقة لا تخمين.
 *  2. تنظيف الروابط: حذف وسائط التتبع فقط (utm_*، fbclid، gclid، …)
 *     مع الحفاظ الحرفي على كل ما عداه — جراحة نصية حتمية لا تلمس شيئًا آخر.
 *  3. موافقات الهاتف السعودي: 05xxxxxxxx و5xxxxxxxx و9665… و96605…
 *     تُطبَّع إلى المحلي مجمّعًا والدولي والأرقام العربية.
 *  4. مستخرج رمز التحقق: كلمة 4–8 أرقام محدودة الحدود (ليست ذيل
 *     رقم أطول)، يرجّحها ذكر الكلمات المفتاحية، ويأبى الصدق أن يخمّن
 *     عند تعدد المرشحين بلا مفتاح.
 *  5. تنسيقات المبلغ: نفس مولّد العملات السبع للوحة الأرقام — مصدر
 *     حقيقة واحد لا تكرار.
 *  6. قلب الأرقام: غربي ↔ عربي هندية للنص الكامل بأي فئة.
 *
 * لا شيء هنا يلمس Android UI أو Context أو الحافظة نفسها — المحلل
 * نقية تمامًا، واللوحة (الجلسة القادمة) هي من تقرأ الحافظة وتستدعيه.
 */

/** فئة محتوى الحافظة — the class the first-wins rule chain assigned. */
enum class DrsClipContentClass {
    /** A link (http://, https:// or www.). */
    URL,

    /** An email address. */
    EMAIL,

    /** A phone number (Saudi local/mobile/international). */
    PHONE,

    /** A standalone verification code (4–8 digits). */
    OTP,

    /** A money amount carrying a currency glyph. */
    AMOUNT,

    /** A bare number (any length, any digit system, with separators). */
    NUMBER,

    /** Everything else — the honest default. */
    TEXT,
}

/**
 * The analysis verdict: the assigned [contentClass] and the ready
 * variants of that class (empty when the class has nothing honest to
 * offer — the panel shows no tiles rather than inventing one).
 */
data class DrsClipAnalysis(
    val contentClass: DrsClipContentClass,
    val variants: List<String>,
)

object DrsClipContentSmart {

    // ------------------------------------------------------------------
    // 1) مصنّف المحتوى — the first-wins rule chain
    // ------------------------------------------------------------------

    private val URL_SHAPE = Regex("^(https?://\\S+|www\\.\\S+\\.\\S+)$")
    private val EMAIL_SHAPE = Regex("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}")

    /** A 4–8 digit run that is NOT the tail/head of a longer digit run. */
    private val RUN_4_8 = Regex("(?<![0-9])[0-9]{4,8}(?![0-9])")

    private val NUMBER_SEPARATORS = setOf('.', ',', '٫', '٬', ' ', '-', '+')

    /** The keywords that anchor an OTP run inside a longer message. */
    val OTP_KEYWORDS: List<String> = listOf(
        "رمز", "التحقق", "كود", "code", "otp", "pin", "verification", "verify",
    )

    /** The OTP extractor never scans past this many characters. */
    const val OTP_SCAN_MAX_CHARS: Int = 4000

    /**
     * The seven rules, in order — the first that fires wins:
     *  1. URL — the first line is an http(s):// or www. link.
     *  2. EMAIL — the first line is an email address.
     *  3. PHONE — Saudi local (05…, 10 digits), Saudi mobile without
     *     the zero (5…, 9 digits), or international (+/00 prefix with
     *     9–15 digits) — digits plus phone punctuation only.
     *  4. OTP — the WHOLE first line is 4–8 digits, nothing else.
     *  5. AMOUNT — digits anywhere plus a currency glyph anywhere.
     *  6. NUMBER — digits plus number separators only (any length).
     *  7. TEXT — the honest fallback.
     */
    fun classify(rawText: String): DrsClipContentClass {
        val text = rawText.trim()
        if (text.isEmpty()) return DrsClipContentClass.TEXT
        val line = text.lines().first().trim()

        // rule 1 — the link shapes.
        if (URL_SHAPE.matches(line)) return DrsClipContentClass.URL
        // rule 2 — the email shape.
        if (EMAIL_SHAPE.matches(line)) return DrsClipContentClass.EMAIL

        val folded = DrsNumberPanelSmart.toAsciiDigits(line)
        val digits = folded.filter { it.isDigit() }
        val digitCount = digits.length

        // rule 3 — phones: digits plus phone punctuation only.
        if (digitCount > 0 && isPhoneShape(folded, digits)) {
            return DrsClipContentClass.PHONE
        }
        // rule 4 — the whole line IS the code: 4–8 digits, nothing else.
        if (digitCount in 4..8 && digitCount == folded.length) {
            return DrsClipContentClass.OTP
        }
        // rule 5 — a currency glyph anywhere over digits anywhere.
        if (digitCount > 0 &&
            DrsNumberPanelSmart.CURRENCY_HINTS.any { text.contains(it) }
        ) {
            return DrsClipContentClass.AMOUNT
        }
        // rule 6 — a bare number: digits plus separators only.
        if (digitCount > 0 &&
            folded.all { it.isDigit() || it in NUMBER_SEPARATORS }
        ) {
            return DrsClipContentClass.NUMBER
        }
        // rule 7 — the honest fallback.
        return DrsClipContentClass.TEXT
    }

    /** The phone shapes: Saudi 05/5/9665/96605, or a +/00 international. */
    private fun isPhoneShape(folded: String, digits: String): Boolean {
        val punctuationOk = folded.all {
            it.isDigit() || it == '+' || it == '-' || it == '(' || it == ')' || it == ' '
        }
        if (!punctuationOk) return false
        if (digits.length == 10 && digits.startsWith("05")) return true
        if (digits.length == 9 && digits.startsWith("5")) return true
        if (digits.length == 12 && digits.startsWith("966") && digits[3] == '5') return true
        if (digits.length == 13 && digits.startsWith("9660")) return true
        if (folded.startsWith("+") || folded.startsWith("00")) {
            return digits.length in 9..15
        }
        return false
    }

    /**
     * The class-ordered variant feed the panel renders: [variantsFor]
     * re-derives the variants of ANY class over the same text so a user
     * tap on another chip retargets the analysis honestly (the number
     * panel's context-override rule, applied to clipboard content).
     */
    fun variantsFor(contentClass: DrsClipContentClass, rawText: String): List<String> = when (contentClass) {
        DrsClipContentClass.URL -> urlVariants(rawText)
        DrsClipContentClass.EMAIL -> emailVariants(rawText)
        DrsClipContentClass.PHONE -> phoneVariants(rawText)
        DrsClipContentClass.OTP -> otpVariants(rawText)
        DrsClipContentClass.AMOUNT -> amountVariants(rawText)
        DrsClipContentClass.NUMBER -> numberVariants(rawText)
        DrsClipContentClass.TEXT -> textVariants(rawText)
    }

    /** The one-shot analysis: classify then derive that class's variants. */
    fun analyze(rawText: String): DrsClipAnalysis {
        val contentClass = classify(rawText)
        return DrsClipAnalysis(contentClass, variantsFor(contentClass, rawText))
    }

    // ------------------------------------------------------------------
    // 2) الروابط — tracking-parameter surgery
    // ------------------------------------------------------------------

    /**
     * The tracking parameters the cleaner strips — ONLY these. Every
     * other query parameter, the path, the scheme and the fragment are
     * preserved byte for byte (lowercased key match, values untouched).
     */
    val TRACKING_PARAMS: Set<String> = setOf(
        "utm_source", "utm_medium", "utm_campaign", "utm_content", "utm_term", "utm_id",
        "fbclid", "gclid", "msclkid", "dclid", "twclid", "igshid", "si",
        "ref", "ref_src", "ref_url",
    )

    /**
     * The clean form of [url]: the tracking parameters are removed from
     * the query part, everything else is preserved verbatim (order of
     * the surviving parameters included). A query that becomes empty
     * drops its «?» entirely; a URL without one returns unchanged.
     */
    fun cleanUrl(url: String): String {
        val queryStart = url.indexOf('?')
        if (queryStart < 0) return url
        val head = url.take(queryStart)
        val tail = url.drop(queryStart + 1)
        val fragmentStart = tail.indexOf('#')
        val query = if (fragmentStart >= 0) tail.take(fragmentStart) else tail
        val fragment = if (fragmentStart >= 0) tail.drop(fragmentStart) else ""
        if (query.isEmpty()) return url
        val kept = query.split('&')
            .filter { it.isNotEmpty() }
            .filterNot { pair ->
                pair.substringBefore('=').lowercase(Locale.ROOT) in TRACKING_PARAMS
            }
        return if (kept.isEmpty()) head + fragment
        else "$head?${kept.joinToString("&")}$fragment"
    }

    /** The bare host of [url]: scheme and path stripped, «www.» dropped. */
    fun hostOf(url: String): String {
        var rest = url.trim()
        val schemeEnd = rest.indexOf("://")
        if (schemeEnd >= 0) rest = rest.drop(schemeEnd + 3)
        rest = rest.substringBefore('/').substringBefore('?').substringBefore('#')
        return rest.removePrefix("www.")
    }

    /**
     * The URL variants: the clip as-is, its clean form (when the cleaner
     * actually removed something), and the bare domain — honest distinct
     * tiles, nothing else.
     */
    fun urlVariants(rawText: String): List<String> {
        val url = rawText.trim().lines().first().trim()
        if (url.isEmpty()) return emptyList()
        val clean = cleanUrl(url)
        val host = hostOf(url)
        return buildList {
            add(url)
            if (clean != url) add(clean)
            if (host.isNotEmpty() && host != url) add(host)
        }.distinct()
    }

    // ------------------------------------------------------------------
    // 3) البريد — domain lowercase
    // ------------------------------------------------------------------

    /**
     * The email variants: the clip as-is, then the domain-lowercased
     * form (when it differs) — the one deterministic normalization an
     * address can honestly take.
     */
    fun emailVariants(rawText: String): List<String> {
        val email = rawText.trim().lines().first().trim()
        if (!EMAIL_SHAPE.matches(email)) return emptyList()
        val at = email.lastIndexOf('@')
        val lowered = "${email.take(at)}@${email.drop(at + 1).lowercase(Locale.ROOT)}"
        return listOfNotNull(email, lowered.takeIf { it != email }).distinct()
    }

    // ------------------------------------------------------------------
    // 4) الهاتف — the Saudi normalizations
    // ------------------------------------------------------------------

    /**
     * The phone variants of a Saudi number in any of its four honest
     * shapes — 05XXXXXXXX, 5XXXXXXXX, 9665XXXXXXXX, 96605XXXXXXXX —
     * folded to the local grouped form, the international form, and the
     * Arabic-Indic rendering. A clip that is not one of these shapes
     * returns as-is alone (never a guessed grouping).
     */
    fun phoneVariants(rawText: String): List<String> {
        val trimmed = rawText.trim()
        val digits = DrsNumberPanelSmart.toAsciiDigits(trimmed).filter { it.isDigit() }
        if (digits.isEmpty()) return emptyList()
        val saudiLocal: String = when {
            digits.length == 10 && digits.startsWith("05") -> digits
            digits.length == 9 && digits.startsWith("5") -> "0$digits"
            digits.length == 12 && digits.startsWith("966") && digits[3] == '5' -> "0${digits.drop(3)}"
            digits.length == 13 && digits.startsWith("9660") -> digits.drop(3)
            else -> return listOf(trimmed)
        }
        val grouped = "${saudiLocal.take(4)} ${saudiLocal.drop(4).take(3)} ${saudiLocal.takeLast(3)}"
        val intl = "+966 ${saudiLocal.drop(1).take(2)} ${saudiLocal.drop(3).take(3)} ${saudiLocal.takeLast(4)}"
        return listOf(trimmed, grouped, intl, DrsNumberPanelSmart.toArabicDigits(grouped)).distinct()
    }

    // ------------------------------------------------------------------
    // 5) رمز التحقق — the bounded, keyword-anchored extractor
    // ------------------------------------------------------------------

    /**
     * The verification code inside [text] — or null when no code can be
     * named honestly. The rules:
     *  1. the whole (trimmed) text being 4–8 digits IS the code;
     *  2. otherwise the boundary-anchored 4–8 digit runs (a run inside
     *     a longer digit run is never a candidate);
     *  3. a run sharing a line with a keyword wins (first such run);
     *  4. no keyword: exactly ONE candidate wins, several refuse (null).
     * The scan is capped at [OTP_SCAN_MAX_CHARS] — bounded work on any
     * clip size, deterministic.
     */
    fun otpFromMessage(text: String): String? {
        val folded = DrsNumberPanelSmart.toAsciiDigits(text).take(OTP_SCAN_MAX_CHARS)
        val whole = folded.trim()
        if (whole.isNotEmpty() && whole.all { it.isDigit() } && whole.length in 4..8) {
            return whole
        }
        val runs = RUN_4_8.findAll(folded).map { it.value }.toList()
        if (runs.isEmpty()) return null
        val lines = folded.lines()
        for (run in runs) {
            val anchored = lines.any { line ->
                line.contains(run) &&
                    OTP_KEYWORDS.any { keyword ->
                        line.lowercase(Locale.ROOT).contains(keyword)
                    }
            }
            if (anchored) return run
        }
        return if (runs.size == 1) runs.first() else null
    }

    /** The OTP variants: the extracted code and its Arabic-Indic form. */
    fun otpVariants(rawText: String): List<String> {
        val code = otpFromMessage(rawText) ?: return emptyList()
        return listOf(code, DrsNumberPanelSmart.toArabicDigits(code)).distinct()
    }

    // ------------------------------------------------------------------
    // 6) المبلغ — the seven currencies (one source of truth)
    // ------------------------------------------------------------------

    /**
     * The amount variants: the digits-and-separators run of the clip
     * fed to the SAME currency generator the numbers panel uses — seven
     * symbols over the unified grouped amount plus the Arabic-Indic
     * rendering. Empty when the clip carries no digits.
     */
    fun amountVariants(rawText: String): List<String> {
        val folded = DrsNumberPanelSmart.toAsciiDigits(rawText)
        val amount = folded.filter { it.isDigit() || it == '.' || it == ',' }
        if (amount.filter { it.isDigit() }.isEmpty()) return emptyList()
        return DrsNumberPanelSmart.currencyFormats(amount)
    }

    // ------------------------------------------------------------------
    // 7) قلب الأرقام — Western ↔ Arabic-Indic, any class
    // ------------------------------------------------------------------

    /** The number variants: the clip as-is, Western-folded, Arabic-Indic. */
    fun numberVariants(rawText: String): List<String> {
        val text = rawText.trim()
        if (text.isEmpty() || text.none { it.isDigit() }) return emptyList()
        val ascii = DrsNumberPanelSmart.toAsciiDigits(text)
        val arabic = DrsNumberPanelSmart.toArabicDigits(text)
        return listOf(text, ascii, arabic).distinct()
    }

    /**
     * The plain-text variants: the digit flip only, and only when the
     * text actually carries digits — a text without digits gets an
     * honest empty feed, not a duplicated tile.
     */
    fun textVariants(rawText: String): List<String> {
        val text = rawText.trim()
        if (text.isEmpty() || text.none { it.isDigit() }) return emptyList()
        val ascii = DrsNumberPanelSmart.toAsciiDigits(text)
        val arabic = DrsNumberPanelSmart.toArabicDigits(text)
        return if (ascii == arabic) listOf(text) else listOf(text, ascii, arabic).distinct()
    }

    // ------------------------------------------------------------------
    // 8) سطر المعاينة — the honest one-line preview
    // ------------------------------------------------------------------

    /**
     * The one-line preview of the clip: the first non-blank line with
     * its whitespace collapsed, capped at [maxChars] with an ellipsis —
     * the label the panel header shows before any classification.
     */
    fun previewLabel(rawText: String, maxChars: Int = 32): String {
        val line = rawText.lines().firstOrNull { it.isNotBlank() }?.trim() ?: ""
        val collapsed = line.replace(Regex("\\s+"), " ")
        return if (collapsed.length <= maxChars) {
            collapsed
        } else {
            collapsed.take(maxChars - 1) + "…"
        }
    }
}
