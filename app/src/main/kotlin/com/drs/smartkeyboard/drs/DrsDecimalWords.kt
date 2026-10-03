/*
 * Copyright (C) 2026 The DRS Smart Keyboard Project
 */

package com.drs.smartkeyboard.drs

/**
 * DRS v2.0.0 — the deterministic decimal-to-Arabic-words engine
 * (العشري بالكلمات) behind the DECIMAL_WORDS text tool: a clean decimal
 * number becomes its formal spoken words — «1.5» → «واحد فاصلة خمسة»،
 * «1.50» → «واحد فاصلة خمسة صفر» — digit-by-digit, no simplification.
 * The words family gains its second direction in this release: the
 * integer side has spoken since v1.6.0, and v2.0.0 makes the decimal
 * tail speak the same way.
 *
 * Pure decimal grammar, no model, no dictionary, no guesswork:
 *  - The whole field must be a clean two-component decimal over ONE
 *    separator from the closed pair { . (U+002E) ، ٫ (U+066B) }.
 *    Anything else is an honest no-op.
 *  - Both ends must be pure digits over the three systems — Western,
 *    Arabic-Indic (٠-٩) and Extended (۰-۹) — each character mapped
 *    independently, so even a mixed field parses (the same
 *    locale-blindness the fraction engine documents).
 *  - The integer part renders through [DrsNumberWords] — one source of
 *    truth, by VALUE not position («007.5» speaks «سبعة فاصلة خمسة»),
 *    under its twelve-digit ceiling. A leading minus is honored
 *    («-1.5» → «سالب واحد فاصلة خمسة») and negative zero is refused,
 *    exactly as the integer engine refuses it.
 *  - The tail speaks digit-by-digit through the shared
 *    [DrsNumberWords.DIGIT_WORDS] alphabet — the written form is the
 *    contract, never a rounded or simplified reading: «1.50» keeps its
 *    trailing صفر because the field says fifty hundredths in digits.
 *    The tail carries one to fifteen digits; beyond that the engine
 *    refuses rather than invents.
 *  - Fail-closed everywhere: an empty half, embedded letters or signs,
 *    two separators, an Arabic comma (never a decimal point) — every
 *    shape the grammar does not name comes back byte-identical. The
 *    output itself carries no dot, so applying the tool to its own
 *    output is a fixed point.
 */
object DrsDecimalWords {

    /** The closed tail ceiling: fifteen spoken digits — beyond it the
     *  engine refuses instead of inventing a reading. */
    const val MAX_TAIL_DIGITS = 15

    /** The closed decimal-separator pair — exactly one per field. The
     *  Arabic comma «،» (U+060C) is NOT a decimal point and never was. */
    private val SEPARATORS = listOf('.', '\u066B')

    /** Maps every accepted digit to its value, mirroring [DrsNumberWords]. */
    private fun digitValue(c: Char): Int? = when (c) {
        in '0'..'9' -> c - '0'
        in '\u0660'..'\u0669' -> c - '\u0660'
        in '\u06F0'..'\u06F9' -> c - '\u06F0'
        else -> null
    }

    /**
     * The formal Arabic decimal words for [text] when it is a clean
     * decimal the engine understands, or null when it is an honest no-op.
     */
    fun decimalWordsOrNull(text: String): String? {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return null
        // Exactly one decimal separator — never both systems, never zero.
        val present = SEPARATORS.filter { trimmed.contains(it) }
        if (present.size != 1) return null
        val parts = trimmed.split(present.single())
        if (parts.size != 2) return null
        val (intRun, tailRun) = parts
        // The integer part through the one source of truth: value not
        // position, the twelve-digit ceiling, the minus, no negative zero.
        val intWords = DrsNumberWords.arabicWordsOrNull(intRun) ?: return null
        // The tail: one..fifteen pure digits, spoken digit-by-digit
        // through the shared alphabet — the written form is the contract.
        if (tailRun.isEmpty() || tailRun.length > MAX_TAIL_DIGITS) return null
        val tailWords = buildString {
            for ((index, c) in tailRun.withIndex()) {
                val value = digitValue(c) ?: return null
                if (index > 0) append(' ')
                append(DrsNumberWords.DIGIT_WORDS[value])
            }
        }
        return "$intWords فاصلة $tailWords"
    }
}
