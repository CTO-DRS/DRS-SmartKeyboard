/*
 * Copyright (C) 2026 The DRS Smart Keyboard Project
 */

package com.drs.smartkeyboard.drs

/**
 * DRS v1.7.0 — the deterministic financial tafqit engine (التفقيط المالي)
 * behind the TAFQIT text tool: a clean monetary amount becomes its formal
 * check-writing Arabic words — «فقط ألف ومائتان وأربعة وثلاثون ريال
 * وخمسون هللة لا غير» — the exact phrase a bank clerk expects on a cheque.
 *
 * Pure grammar, no model, no dictionary, no guesswork:
 *  - The whole field must be a clean amount: an optional leading minus,
 *    digits (Western, Arabic-Indic or Extended — locale-blind on purpose)
 *    with optional thousands groups (a comma or ٬ followed by EXACTLY
 *    three digits each), then an optional decimal separator (. or ٫)
 *    carrying EXACTLY one or two digits, and finally an OPTIONAL closed
 *    currency token peeled from the tail (whitespace-separated or attached
 *    directly to the last digit). Anything else is an honest no-op.
 *  - All number words come from [DrsNumberWords] — one source of truth.
 *    The engine adds only the closed currency catalog and the documented
 *    agreement grammar on top.
 *  - Counted-noun agreement (documented, deterministic):
 *      n == 1            → «ريال واحد» (the noun, then واحد)
 *      n == 2            → «ريالان» (the dual absorbs the number)
 *      n % 100 in 3..10  → «خمسة ريالات» (number, then broken plural)
 *      otherwise         → «مائة ريال» (number, then the bare singular —
 *        the house orthography writes the counted noun WITHOUT tanwin,
 *        exactly like the scale words of [DrsNumberWords]).
 *    Compounds that merely END in 1/2 (101, 202, 1001…) take the bare
 *    singular too: «مائة وواحد ريال» — one documented rule, no exceptions.
 *  - The subunit (هللة/سنت/قرش/فلس) follows the same selection; feminine
 *    subunits (هللة، سنت) count with the feminine forms — إحدى عشرة،
 *    ثلاث هللات، تسع وتسعون — while masculine subunits (قرش، فلس) reuse
 *    the standard words of [DrsNumberWords]. Zero subunit disappears,
 *    and a zero whole part disappears when a subunit remains.
 *  - The output always wears the formal check envelope «فقط … لا غير».
 *  - The catalog is CLOSED: five two-decimal currencies (ريال، دولار،
 *    يورو، جنيه، درهم). Three-decimal dinars are honestly absent — an
 *    amount with three decimals is a no-op, never a wrong فلس count.
 */
object DrsTafqit {

    /** Hard ceiling on the whole part: twelve digits, mirroring [DrsNumberWords]. */
    const val MAX_WHOLE_DIGITS = DrsNumberWords.MAX_DIGITS

    /** The formal check envelope. */
    private const val PREFIX = "فقط "
    private const val SUFFIX = " لا غير"

    // ---------------------------------------------------------------
    // The closed currency catalog (five two-decimal currencies).
    // ---------------------------------------------------------------

    /**
     * One catalog entry: the counted-noun forms of the main unit and of
     * its subunit, whether the subunit counts with feminine forms, and
     * the closed token list that names the currency in the field.
     */
    private data class Currency(
        val one: String,
        val two: String,
        val many: String,
        val subOne: String,
        val subTwo: String,
        val subMany: String,
        val subFeminine: Boolean,
        val tokens: List<String>,
    )

    private val CURRENCIES = listOf(
        Currency(
            "ريال", "ريالان", "ريالات",
            "هللة", "هللتان", "هللات", true,
            listOf("ريال", "ر.س", "SAR", "SR", "\uFDFC"),
        ),
        Currency(
            "دولار", "دولاران", "دولارات",
            "سنت", "سنتان", "سنتات", true,
            listOf("دولار", "USD", "$"),
        ),
        Currency(
            "يورو", "يوروان", "يورو",
            "سنت", "سنتان", "سنتات", true,
            listOf("يورو", "EUR", "€"),
        ),
        Currency(
            "جنيه", "جنيهان", "جنيهات",
            "قرش", "قرشان", "قروش", false,
            listOf("جنيه", "ج.م", "EGP"),
        ),
        Currency(
            "درهم", "درهمان", "دراهم",
            "فلس", "فلسان", "فلوس", false,
            listOf("درهم", "د.إ", "AED"),
        ),
    )

    /** The field without a currency token is read as ريال / هللة. */
    private val DEFAULT: Currency = CURRENCIES.first()

    // ---------------------------------------------------------------
    // Feminine counting words for 0..99 (subunit grammar).
    // ---------------------------------------------------------------

    /** Indices 1..10: the compound units إحدى/اثنتا live here too, and
     *  index 10 is عشر — the standalone feminine ten. */
    private val FEM_UNITS = listOf(
        "", "إحدى", "اثنتا", "ثلاث", "أربع", "خمس", "ست", "سبع", "ثمان", "تسع", "عشر",
    )

    private val FEM_TEENS = listOf(
        "إحدى عشرة", "اثنتا عشرة", "ثلاث عشرة", "أربع عشرة", "خمس عشرة",
        "ست عشرة", "سبع عشرة", "ثمان عشرة", "تسع عشرة",
    )

    /** Tens are gender-invariable in Arabic counting. */
    private val FEM_TENS = listOf(
        "", "", "عشرون", "ثلاثون", "أربعون", "خمسون", "ستون", "سبعون", "ثمانون", "تسعون",
    )

    /** The feminine words for 0..99 — the subunit counting grammar.
     *  FEM_TEENS starts at eleven (index m - 11): 11 is إحدى عشرة. */
    private fun femWords(m: Int): String = when {
        m <= 10 -> FEM_UNITS[m]
        m < 20 -> FEM_TEENS[m - 11]
        m % 10 == 0 -> FEM_TENS[m / 10]
        else -> FEM_UNITS[m % 10] + " و" + FEM_TENS[m / 10]
    }

    // ---------------------------------------------------------------
    // Field parsing: [currency token peel] then [amount grammar].
    // ---------------------------------------------------------------

    private data class Amount(val whole: Long, val sub: Int, val negative: Boolean)

    /** Maps every accepted digit to its value, mirroring [DrsNumberWords]. */
    private fun digitValue(c: Char): Int? = when (c) {
        in '0'..'9' -> c - '0'
        in '\u0660'..'\u0669' -> c - '\u0660'
        in '\u06F0'..'\u06F9' -> c - '\u06F0'
        else -> null
    }

    /**
     * Peels an optional currency token from the tail of [text]: the token
     * must END the field, stand alone (whitespace before it or attached
     * straight to a digit), and match the closed catalog — Latin tokens
     * case-insensitively, Arabic tokens byte-exact. Longest token wins.
     * No token at all means the whole field is an amount in [DEFAULT].
     */
    private fun splitCurrency(text: String): Pair<Currency, String> {
        val candidates = CURRENCIES
            .flatMap { c -> c.tokens.map { it to c } }
            .sortedByDescending { (token, _) -> token.length }
        for ((token, currency) in candidates) {
            val latin = token.any { it.code < 128 }
            val ends = if (latin) {
                text.length > token.length &&
                    text.regionMatches(text.length - token.length, token, 0, token.length, true)
            } else {
                text.endsWith(token) && text.length > token.length
            }
            if (!ends) continue
            val head = text.dropLast(token.length)
            val amountRaw = head.trim()
            if (amountRaw.isEmpty()) continue
            // The token must stand alone: whitespace before it, or glued
            // to the last DIGIT of the amount. Anything glued to a letter
            // («والدرهم») is honest prose — not this tool's business.
            val boundary = head.last()
            if (!boundary.isWhitespace() && digitValue(boundary) == null) continue
            return currency to amountRaw
        }
        return DEFAULT to text
    }

    /**
     * Parses the amount body: optional leading minus, digit groups joined
     * by thousands separators (each EXACTLY three digits), then at most
     * one decimal separator carrying EXACTLY one or two digits. Returns
     * null for every shape the grammar does not name — fail-closed.
     */
    private fun parseAmount(raw: String): Amount? {
        var body = raw.trim()
        if (body.isEmpty()) return null
        var negative = false
        if (body.first() == '-') {
            negative = true
            body = body.substring(1).trim()
        }
        if (body.isEmpty()) return null
        // At most one decimal separator (either system — never mixed),
        // carrying exactly one or two digits.
        if (body.count { it == '.' || it == '\u066B' } > 1) return null
        val sepIdx = body.indexOfFirst { it == '.' || it == '\u066B' }
        val wholePart: String
        val subPart: String?
        if (sepIdx >= 0) {
            wholePart = body.substring(0, sepIdx)
            subPart = body.substring(sepIdx + 1)
            if (subPart.isEmpty() || subPart.length > 2) return null
        } else {
            wholePart = body
            subPart = null
        }
        if (wholePart.isEmpty()) return null
        // Whole part: either ONE bare digit run of 1..12 digits, or a
        // grouped form — 1..3 leading digits then «,ddd» groups of exactly
        // three each — totaling at most 12 digits.
        val groups = wholePart.split(',', '\u066C')
        if (groups.size == 1) {
            if (groups.first().length > MAX_WHOLE_DIGITS) return null
        } else {
            if (groups.first().isEmpty() || groups.first().length > 3) return null
            if (groups.drop(1).any { it.length != 3 }) return null
        }
        var whole = 0L
        var digits = 0
        for (group in groups) {
            for (c in group) {
                val d = digitValue(c) ?: return null
                whole = whole * 10 + d
                digits++
            }
        }
        if (digits == 0 || digits > MAX_WHOLE_DIGITS) return null
        var sub = 0
        if (subPart != null) {
            for (c in subPart) {
                val d = digitValue(c) ?: return null
                sub = sub * 10 + d
            }
        }
        return Amount(whole, sub, negative)
    }

    // ---------------------------------------------------------------
    // Rendering: agreement grammar + the formal check envelope.
    // ---------------------------------------------------------------

    /** The main-unit phrase for a validated whole value [n]. */
    private fun wholePhrase(n: Long, c: Currency, omitZero: Boolean): String? {
        if (n == 0L) {
            if (omitZero) return null
            return "صفر ${c.one}"
        }
        val words = DrsNumberWords.arabicWordsOrNull(n.toString()) ?: return null
        return when {
            n == 1L -> "${c.one} واحد"
            n == 2L -> c.two
            n % 100L in 3L..10L -> "$words ${c.many}"
            else -> "$words ${c.one}"
        }
    }

    /** The subunit phrase for a validated sub value [m], null when zero. */
    private fun subPhrase(m: Int, c: Currency): String? {
        if (m == 0) return null
        if (c.subFeminine) {
            return when {
                m == 1 -> "${c.subOne} واحدة"
                m == 2 -> c.subTwo
                m in 3..10 -> "${femWords(m)} ${c.subMany}"
                else -> "${femWords(m)} ${c.subOne}"
            }
        }
        val words = DrsNumberWords.arabicWordsOrNull(m.toString()) ?: return null
        return when {
            m == 1 -> "${c.subOne} واحد"
            m == 2 -> c.subTwo
            m in 3..10 -> "$words ${c.subMany}"
            else -> "$words ${c.subOne}"
        }
    }

    /**
     * The formal check words for [text] when it is a clean amount the
     * engine understands, or null when it is an honest no-op.
     */
    fun tafqitOrNull(text: String): String? {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return null
        val (currency, amountRaw) = splitCurrency(trimmed)
        val (whole, sub, negative) = parseAmount(amountRaw) ?: return null
        val main = wholePhrase(whole, currency, omitZero = sub > 0)
        val fraction = subPhrase(sub, currency)
        val phrase = listOfNotNull(main, fraction).joinToString(" و")
        val body = if (negative) "سالب $phrase" else phrase
        return PREFIX + body + SUFFIX
    }
}
