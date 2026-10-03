/*
 * Copyright (C) 2026 The DRS Smart Keyboard Project
 */

package com.drs.smartkeyboard.drs

/**
 * DRS v1.10.0 — the deterministic ordinal-to-Arabic-words engine
 * (الترتيب بالكلمات) behind the ORDINAL_WORDS text tool: a clean integer
 * becomes its formal written ordinal — «3» → «الثالث»، «21» → «الحادي
 * والعشرون»، «1234» → «الألف والمائة والرابع والثلاثون» — the shapes
 * formal prose spells out for floors, chapters, clauses and pages.
 *
 * Pure ordinal grammar, no model, no dictionary, no guesswork:
 *  - The whole field must be a clean integer over the three digit
 *    systems (Western, Arabic-Indic ٠-٩, Extended ۰-۹ — even mixed),
 *    no sign, value 1..9999, padding by value never by position.
 *    Anything else is an honest no-op: zero has no ordinal in this
 *    closed zone, negatives are prose, and beyond 9999 the engine
 *    refuses rather than invents.
 *  - The documented composition convention (masculine, nominative,
 *    with the definite article — the citation form of Arabic ordinals):
 *    the FINAL 1..99 group takes the ordinal tables (الأول..العاشر،
 *    الحادي عشر..التاسع عشر، العشرون..التسعون، and the compounds where
 *    ONE becomes الحادي: الحادي والعشرون، الثاني والثلاثون); every
 *    HIGHER group keeps its cardinal words under the article — الألف،
 *    الألفان، الثلاثة آلاف، المائة، المائتان، الثلاثمائة — one source
 *    of truth via [DrsNumberWords]. Groups join descending with the
 *    prefixed waw: «الألف والمائة والرابع والثلاثون».
 *  - The classic pair is pinned: الأول for the bare unit, الحادي
 *    inside a compound («الأول» لكن «الحادي والعشرون») — the documented
 *    written distinction, never flattened.
 *  - No tanwin is written — the plain formal orthography, house style.
 *  - Fail-closed everywhere: the output itself carries no digits, so
 *    applying the tool to its own output is a fixed point; prose and
 *    any shape the grammar does not name come back byte-identical.
 */
object DrsOrdinalWords {

    /** Hard ceiling: the closed ordinal zone 1..9999 (through التسعة آلاف). */
    const val MAX_VALUE = 9999

    /** The bare ordinal units 1..10: الأول..العاشر. Index 0 unused. */
    private val ORD_UNITS = listOf(
        "", "الأول", "الثاني", "الثالث", "الرابع", "الخامس",
        "السادس", "السابع", "الثامن", "التاسع", "العاشر",
    )

    /** The ordinal teens 11..19: الحادي عشر..التاسع عشر. Index 0 = 11. */
    private val ORD_TEENS = listOf(
        "الحادي عشر", "الثاني عشر", "الثالث عشر", "الرابع عشر", "الخامس عشر",
        "السادس عشر", "السابع عشر", "الثامن عشر", "التاسع عشر",
    )

    /** The unit inside a compound (21..99): ONE becomes الحادي — the
     *  documented written distinction from the bare الأول. Index 0 unused. */
    private val ORD_UNIT_IN_COMPOUND = listOf(
        "", "الحادي", "الثاني", "الثالث", "الرابع", "الخامس",
        "السادس", "السابع", "الثامن", "التاسع",
    )

    /** Maps every accepted digit to its value, mirroring [DrsNumberWords]. */
    private fun digitValue(c: Char): Int? = when (c) {
        in '0'..'9' -> c - '0'
        in '\u0660'..'\u0669' -> c - '\u0660'
        in '\u06F0'..'\u06F9' -> c - '\u06F0'
        else -> null
    }

    /** The cardinal words of [value] under the definite article — one
     *  source of truth: الألف، الألفان، الثلاثة آلاف، المائة، المائتان،
     *  الثلاثمائة، العشرون..التسعون. The caller passes a value the
     *  engine has already bounded. */
    private fun alCardinal(value: Int): String =
        "ال" + DrsNumberWords.arabicWordsOrNull(value.toString())!!

    /** The ordinal words of the final group, 1..99. */
    private fun ordinalRest(rest: Int): String = when {
        rest <= 10 -> ORD_UNITS[rest]
        rest < 20 -> ORD_TEENS[rest - 11]
        rest % 10 == 0 -> alCardinal(rest)
        else -> ORD_UNIT_IN_COMPOUND[rest % 10] + " و" + alCardinal(rest / 10 * 10)
    }

    /**
     * The formal Arabic ordinal words for [text] when it is a clean
     * integer the engine understands, or null when it is an honest no-op.
     */
    fun ordinalWordsOrNull(text: String): String? {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return null
        // No sign of any kind: the ordinal zone starts at ONE.
        if (trimmed[0] == '-' || trimmed[0] == '+') return null
        var value = 0L
        for (c in trimmed) {
            val d = digitValue(c) ?: return null
            value = value * 10 + d
        }
        if (value == 0L || value > MAX_VALUE) return null
        val n = value.toInt()
        val parts = mutableListOf<String>()
        val thousands = n / 1000
        val hundreds = n % 1000 / 100
        val rest = n % 100
        if (thousands > 0) parts += alCardinal(thousands * 1000)
        if (hundreds > 0) parts += alCardinal(hundreds * 100)
        if (rest > 0) parts += ordinalRest(rest)
        return parts.joinToString(" و")
    }
}
