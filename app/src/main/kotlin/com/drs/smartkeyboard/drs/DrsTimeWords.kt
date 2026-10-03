/*
 * Copyright (C) 2026 The DRS Smart Keyboard Project
 */

package com.drs.smartkeyboard.drs

/**
 * DRS v1.9.0 — the deterministic clock-time-to-Arabic-words engine
 * (الوقت بالكلمات) behind the TIME_WORDS text tool: a clean numeric
 * clock time becomes its formal spoken phrase — «14:45» → «الثالثة
 * إلا الربع»، «09:30» → «التاسعة والنصف» — the phrase invitations,
 * minutes and official letters spell out. The quartet closes: v1.6.0
 * made numbers speak, v1.7.0 made amounts speak, v1.8.0 made dates
 * speak, v1.9.0 makes clock times speak.
 *
 * Pure clock grammar, no model, no dictionary, no guesswork:
 *  - The whole field must be a clean two-component clock time over the
 *    single separator «:» (optional whitespace around the field and
 *    around the separator). The colon is the clock's one universal
 *    mark — dot, dash and slash belong to the date grammar (v1.8.0)
 *    and are deliberately NOT accepted here. Anything else is an
 *    honest no-op.
 *  - Exactly two components, 1..2 digits each; digits are locale-blind
 *    on purpose: Western, Arabic-Indic (٠-٩) and Extended (۰-۹) all
 *    parse — even mixed inside one time. Padding is value-based:
 *    9:5, 09:05 and 9:5 are one time.
 *  - The clock is closed: hours 0..23, minutes 0..59. «24:00» is not
 *    a time the clock speaks (no invented ISO end-of-day), minute 60
 *    does not exist, a seconds component is prose, and AM/PM suffixes
 *    are not part of the numeric field — every such shape comes back
 *    byte-identical.
 *  - Speech follows the conventional Arabic 12-hour clock: hour12 =
 *    h % 12 with 0 → 12, so «00:00» and «12:00» both speak «الثانية
 *    عشرة» — the documented convention of human speech; the phrase
 *    itself carries no morning/evening marker. The hour word is the
 *    feminine ordinal the clock actually uses — «الواحدة» (the
 *    standard hour form), «الثالثة»، «الحادية عشرة»، «الثانية عشرة».
 *  - Minutes branch over a closed, documented set: :00 speaks the
 *    hour alone, :15 adds «والربع», :30 adds «والنصف», :45 borrows
 *    the NEXT hour with «إلا الربع» — the only إلا shape, so «14:45»
 *    is «الثالثة إلا الربع» and never «الثالثة وخمسة وأربعون دقيقة».
 *    Every other minute speaks «وN دقيقة/دقائق» under the documented
 *    feminine counted-noun rules (reverse agreement): bare counters
 *    for الدقائق 3..10 («وثلاث دقائق»، «وثماني دقائق»)، the feminine
 *    compounds 11..19 («وإحدى عشرة دقيقة»، «واثنتا عشرة دقيقة»)، and
 *    the compound tens with feminine units («وإحدى وعشرون دقيقة»،
 *    «واثنتان وعشرون دقيقة»، «وثلاث وعشرون دقيقة»). One is the
 *    integrated «ودقيقة واحدة» and two the dual «ودقيقتان».
 *  - Fail-closed everywhere: mixed separators, a leading minus,
 *    embedded letters, three components, hour 24, minute 60 — every
 *    shape the grammar does not name comes back byte-identical. The
 *    output carries no colon, so applying the tool to its own output
 *    is a fixed point.
 */
object DrsTimeWords {

    /**
     * The closed hour table, indexed by h % 12: index 0 is the twelve
     * (00:00 and 12:00), index 1 the documented standard hour form
     * «الواحدة» (not the bare ordinal الأولى), then the feminine
     * ordinals through «الحادية عشرة».
     */
    private val HOURS = listOf(
        "الثانية عشرة", "الواحدة", "الثانية", "الثالثة", "الرابعة",
        "الخامسة", "السادسة", "السابعة", "الثامنة", "التاسعة",
        "العاشرة", "الحادية عشرة",
    )

    /** The closed tens, nominative: عشرون..تسعون. Index = m / 10. */
    private val TENS = listOf(
        "", "", "عشرون", "ثلاثون", "أربعون", "خمسون", "ستون", "سبعون", "ثمانون", "تسعون",
    )

    /**
     * The bare counters 3..10 that agree with the feminine counted
     * noun الدقائق (reverse agreement): ثلاث، أربع، خمس، ست، سبع،
     * ثماني، تسع، عشر. Index = m - 3.
     */
    private val BARE = listOf(
        "ثلاث", "أربع", "خمس", "ست", "سبع", "ثماني", "تسع", "عشر",
    )

    /**
     * The feminine teens 11..19 for the counted noun الدقيقة:
     * إحدى عشرة، اثنتا عشرة، ثلاث عشرة .. تسع عشرة. Index = m - 11.
     */
    private val FEM_TEENS = listOf(
        "إحدى عشرة", "اثنتا عشرة", "ثلاث عشرة", "أربع عشرة", "خمس عشرة",
        "ست عشرة", "سبع عشرة", "ثماني عشرة", "تسع عشرة",
    )

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
     * The spoken minute phrase for 1..59, by the documented feminine
     * counted-noun rules. The caller handles :00/:15/:30/:45 — those
     * four never reach this branch.
     */
    private fun minutePhrase(m: Int): String = when {
        m == 1 -> "ودقيقة واحدة"
        m == 2 -> "ودقيقتان"
        m <= 10 -> "و" + BARE[m - 3] + " دقائق"
        m <= 19 -> "و" + FEM_TEENS[m - 11] + " دقيقة"
        else -> {
            val tens = TENS[m / 10]
            when (val unit = m % 10) {
                0 -> "و$tens دقيقة"
                1 -> "وإحدى و$tens دقيقة"
                2 -> "واثنتان و$tens دقيقة"
                else -> "و" + BARE[unit - 3] + " و$tens دقيقة"
            }
        }
    }

    /**
     * The formal Arabic clock words for [text] when it is a clean clock
     * time the engine understands, or null when it is an honest no-op.
     */
    fun timeWordsOrNull(text: String): String? {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return null
        // The clock has exactly one separator, used exactly once: a
        // second colon means a seconds component — prose, not a time.
        val parts = trimmed.split(':').map { it.trim() }
        if (parts.size != 2) return null
        val (hourRun, minuteRun) = parts
        if (!isDigits(hourRun) || !isDigits(minuteRun)) return null
        // Closed component grammar: 1..2 digits each, hours 0..23,
        // minutes 0..59 — no invented ISO end-of-day, no minute sixty.
        if (hourRun.length > 2 || minuteRun.length > 2) return null
        val hour = toNumber(hourRun)
        val minute = toNumber(minuteRun)
        if (hour !in 0..23) return null
        if (minute !in 0..59) return null
        return when (minute) {
            0 -> HOURS[hour % 12]
            15 -> HOURS[hour % 12] + " والربع"
            30 -> HOURS[hour % 12] + " والنصف"
            45 -> HOURS[(hour + 1) % 12] + " إلا الربع"
            else -> HOURS[hour % 12] + " " + minutePhrase(minute)
        }
    }
}
