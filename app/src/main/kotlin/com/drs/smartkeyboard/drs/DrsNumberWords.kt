/*
 * Copyright (C) 2026 The DRS Smart Keyboard Project
 */

package com.drs.smartkeyboard.drs

/**
 * DRS v1.6.0 — the deterministic number-to-Arabic-words engine
 * (الأرقام إلى كلمات عربية) behind the NUMBER_WORDS text tool.
 *
 * Pure integer grammar, no model, no dictionary, no guesswork:
 *  - Western digits (0-9), Arabic-Indic (٠-٩) and Extended Arabic-Indic
 *    (۰-۹) all parse — the tool is locale-blind on purpose.
 *  - Group grammar: units before tens inside a group (ثلاثة وعشرون),
 *    groups joined with the prefixed waw (مائة وثلاثة وعشرون).
 *  - Scale words follow the documented classical selection: singular for
 *    group == 1 (ألف), dual for group == 2 (ألفان), broken plural for
 *    3..10 (آلاف), singular again for 11..999 (ألف) — written WITHOUT
 *    tanwin (the plain formal orthography, deterministic by rule).
 *  - ZERO tolerance for invented output: anything that is not a clean
 *    integer the engine understands comes back byte-identical. Decimals,
 *    embedded letters, empty strings and values beyond twelve digits
 *    (beyond المليارات) are all honest no-ops.
 *
 * A negative number keeps its sign as the word سالب before the words.
 */
object DrsNumberWords {

    /** Hard ceiling: twelve digits cover up to 999,999,999,999 (999 مليار). */
    const val MAX_DIGITS = 12

    private val UNITS = listOf(
        "", "واحد", "اثنان", "ثلاثة", "أربعة", "خمسة", "ستة", "سبعة", "ثمانية", "تسعة",
    )

    private val TENS = listOf(
        "", "", "عشرون", "ثلاثون", "أربعون", "خمسون", "ستون", "سبعون", "ثمانون", "تسعون",
    )

    private val TEENS = listOf(
        "عشرة", "أحد عشر", "اثنا عشر", "ثلاثة عشر", "أربعة عشر",
        "خمسة عشر", "ستة عشر", "سبعة عشر", "ثمانية عشر", "تسعة عشر",
    )

    private val HUNDREDS = listOf(
        "", "مائة", "مائتان", "ثلاثمائة", "أربعمائة", "خمسمائة",
        "ستمائة", "سبعمائة", "ثمانمائة", "تسعمائة",
    )

    /** The scale-word family: singular, dual, plural (3..10). */
    private data class Scale(val one: String, val two: String, val many: String)

    private val THOUSAND = Scale("ألف", "ألفان", "آلاف")
    private val MILLION = Scale("مليون", "مليونان", "ملايين")
    private val BILLION = Scale("مليار", "ملياران", "مليارات")

    /** Maps every accepted digit to its value, or null when not a digit. */
    private fun digitValue(c: Char): Int? = when (c) {
        in '0'..'9' -> c - '0'
        in '\u0660'..'\u0669' -> c - '\u0660'
        in '\u06F0'..'\u06F9' -> c - '\u06F0'
        else -> null
    }

    /**
     * The Arabic words for [text] when it is a clean integer the engine
     * understands, or null when it is an honest no-op (empty, signed with
     * something other than a leading minus, carrying letters or separators
     * inside, longer than [MAX_DIGITS], or numerically beyond the scales).
     */
    fun arabicWordsOrNull(text: String): String? {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return null
        var negative = false
        var body = trimmed
        if (body[0] == '-') {
            negative = true
            body = body.substring(1).trim()
        }
        if (body.isEmpty() || body.length > MAX_DIGITS) return null
        var value = 0L
        for (c in body) {
            val d = digitValue(c) ?: return null
            value = value * 10 + d
        }
        if (value == 0L) return if (negative) null else "صفر"
        val words = render(value)
        return if (negative) "سالب $words" else words
    }

    /** The words for a value in 1..999,999,999,999 — used by [arabicWordsOrNull]. */
    private fun render(value: Long): String {
        val billions = value / 1_000_000_000L
        val millions = value % 1_000_000_000L / 1_000_000L
        val thousands = value % 1_000_000L / 1_000L
        val units = value % 1_000L

        val parts = mutableListOf<String>()
        if (billions > 0) parts += groupWithScale(billions, BILLION)
        if (millions > 0) parts += groupWithScale(millions, MILLION)
        if (thousands > 0) parts += groupWithScale(thousands, THOUSAND)
        if (units > 0) parts += group3(units)
        return parts.joinToString(" و")
    }

    /** A three-digit group plus its scale word, when the scale exists. */
    private fun groupWithScale(group: Long, scale: Scale): String {
        val words = group3(group)
        return when (group) {
            1L -> scale.one // the group IS the scale word: ألف / مليون / مليار
            2L -> scale.two
            in 3L..10L -> "$words ${scale.many}"
            else -> "$words ${scale.one}"
        }
    }

    /** Renders 1..999: hundreds first, then units before tens inside the rest. */
    private fun group3(value: Long): String {
        val hundreds = (value / 100).toInt()
        val rest = (value % 100).toInt()
        val parts = mutableListOf<String>()
        if (hundreds > 0) parts += HUNDREDS[hundreds]
        if (rest > 0) parts += rest2(rest)
        return parts.joinToString(" و")
    }

    /** Renders 1..99: teens whole, tens alone, otherwise units BEFORE tens. */
    private fun rest2(rest: Int): String = when {
        rest < 10 -> UNITS[rest]
        rest < 20 -> TEENS[rest - 10]
        rest % 10 == 0 -> TENS[rest / 10]
        else -> UNITS[rest % 10] + " و" + TENS[rest / 10]
    }
}
