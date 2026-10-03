/*
 * Copyright (C) 2026 The DRS Smart Keyboard Project
 */

package com.drs.smartkeyboard.drs

/**
 * DRS v1.10.0 — the deterministic fraction-to-Arabic-words engine
 * (الكسور بالكلمات) behind the FRACTION_WORDS text tool: a clean numeric
 * fraction becomes its formal written words — «3/4» → «ثلاثة أرباع»،
 * «1/2» → «النصف»، «2/3» → «ثلثان» — the shapes formal prose spells out.
 * The numbers family widens: v1.6.0 made numbers speak, v1.7.0 amounts,
 * v1.8.0 dates, v1.9.0 times, and v1.10.0 makes fractions speak.
 *
 * Pure fraction grammar, no model, no dictionary, no guesswork:
 *  - The whole field must be a clean two-component fraction over ONE
 *    fraction slash from the closed set { / , ⁄ (U+2044) } (optional
 *    whitespace around the field and around the slash). Anything else
 *    is an honest no-op.
 *  - The numerator is a clean value 1..10; the denominator a clean
 *    value 2..10 — the closed zone where Arabic owns a distinct
 *    fraction word (النصف .. العشر). Beyond ten the classical forms
 *    turn into genitive constructions Arabic never fixed as single
 *    words, so the engine refuses rather than invents.
 *  - Agreement follows the documented counted-noun rules: ONE takes
 *    the definite singular «النصف»، «الثلث»؛ TWO takes the dual
 *    «نصفان»، «ثلثان»؛ THREE..TEN take the broken plural with the
 *    masculine counter «ثلاثة أثلاث»، «عشرة أنصاف» (the denominator
 *    is masculine in classical usage). No tanwin is written — the
 *    plain formal orthography, house style.
 *  - Digits are locale-blind on purpose: Western, Arabic-Indic (٠-٩)
 *    and Extended (۰-۹) all parse — even mixed inside one fraction.
 *  - Fail-closed everywhere: zero numerator, a denominator of 1,
 *    negative components, decimals, three components, embedded
 *    letters — every shape the grammar does not name comes back
 *    byte-identical. The output itself carries no slash, so applying
 *    the tool to its own output is a fixed point.
 */
object DrsFractionWords {

    /** The closed fraction-word catalog, denominator 2..10: the definite
     *  singular (for ONE), the dual (for TWO), the broken plural
     *  (for 3..10). Index 0 and 1 are unused padding. */
    private val SINGULAR = listOf("", "", "النصف", "الثلث", "الربع", "الخمس", "السدس", "السبع", "الثمن", "التسع", "العشر")
    private val DUAL = listOf("", "", "نصفان", "ثلثان", "ربعان", "خمسان", "سدسان", "سبعان", "ثمنان", "تسعان", "عشران")
    private val PLURAL = listOf("", "", "أنصاف", "أثلاث", "أرباع", "أخماس", "أسداس", "أسباع", "أثمان", "أتساع", "أعشار")

    /** The closed numerator ceiling: Arabic owns the fraction word zone
     *  2..10; the engine refuses beyond it instead of inventing forms. */
    private const val MAX_NUMERATOR = 10
    private const val MAX_DENOMINATOR = 10

    /** The closed fraction-slash set — exactly one of these per field. */
    private val SLASHES = listOf('/', '\u2044')

    /** Maps every accepted digit to its value, mirroring [DrsNumberWords]. */
    private fun digitValue(c: Char): Int? = when (c) {
        in '0'..'9' -> c - '0'
        in '\u0660'..'\u0669' -> c - '\u0660'
        in '\u06F0'..'\u06F9' -> c - '\u06F0'
        else -> null
    }

    /** All-digits check for one component (never empty when true). */
    private fun isDigits(component: String): Boolean =
        component.isNotEmpty() && component.all { digitValue(it) != null }

    /** A digit run to its value — the caller has validated every char. */
    private fun toNumber(run: String): Int =
        run.fold(0) { acc, c -> acc * 10 + digitValue(c)!! }

    /**
     * The formal Arabic fraction words for [text] when it is a clean
     * fraction the engine understands, or null when it is an honest no-op.
     */
    fun fractionWordsOrNull(text: String): String? {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return null
        // Exactly one fraction slash — never both systems, never zero.
        val present = SLASHES.filter { trimmed.contains(it) }
        if (present.size != 1) return null
        val parts = trimmed.split(present.single()).map { it.trim() }
        if (parts.size != 2) return null
        val (numRun, denRun) = parts
        if (!isDigits(numRun) || !isDigits(denRun)) return null
        val numerator = toNumber(numRun)
        val denominator = toNumber(denRun)
        if (numerator !in 1..MAX_NUMERATOR) return null
        if (denominator !in 2..MAX_DENOMINATOR) return null
        return when (numerator) {
            1 -> SINGULAR[denominator]
            2 -> DUAL[denominator]
            else -> {
                // One source of truth for the counter words: the numerator
                // renders through [DrsNumberWords] (value-based padding
                // included), then counts the broken plural.
                val counter = DrsNumberWords.arabicWordsOrNull(numRun) ?: return null
                "$counter ${PLURAL[denominator]}"
            }
        }
    }
}
