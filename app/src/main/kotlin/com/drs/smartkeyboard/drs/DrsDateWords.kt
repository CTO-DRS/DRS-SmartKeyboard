/*
 * Copyright (C) 2026 The DRS Smart Keyboard Project
 */

package com.drs.smartkeyboard.drs

/**
 * DRS v1.8.0 — the deterministic date-to-Arabic-words engine (التاريخ
 * بالكلمات) behind the DATE_WORDS text tool: a clean numeric date becomes
 * its formal documentary phrase — «2026-10-03» → «الثالث من أكتوبر عام
 * ألفين وستة وعشرين» — the phrase official letters, contracts and
 * invitations spell out. The trilogy closes: v1.6.0 made numbers speak,
 * v1.7.0 made amounts speak, v1.8.0 makes dates speak.
 *
 * Pure calendar grammar, no model, no dictionary, no guesswork:
 *  - The whole field must be a clean three-component date over ONE
 *    consistent separator from the closed set { - , / , . } (optional
 *    whitespace around the field and around the separator). Anything
 *    else is an honest no-op.
 *  - The year is wherever the four-digit component stands: FIRST means
 *    ISO (yyyy-m-d), LAST means the Arabic documentary convention
 *    (d-m-yyyy). A four-digit component in both ends is ambiguous —
 *    rejected. Two-digit years are rejected: no invented century rule.
 *  - Validation is proleptic-Gregorian and closed: month 1..12, day
 *    1..length-of-month with the documented leap rule (y%4==0 and
 *    (y%100!=0 or y%400==0)), year 1..9999. 29-2-2024 passes, 29-2-2023
 *    and 29-2-1900 do not.
 *  - Digits are locale-blind on purpose: Western, Arabic-Indic (٠-٩)
 *    and Extended (۰-۹) all parse — even mixed inside one date.
 *  - The day renders from a closed 31-entry ordinal table in the
 *    written oblique (الصيغة المجرورة، بلا تنوين — house style):
 *    العاشر، الحادي عشر، العشرين، الحادي والعشرين، الثلاثين —
 *    matching «في الثالث من أكتوبر».
 *  - The month renders from a closed 12-name catalog (يناير..ديسمبر —
 *    the official Gregorian names, indeclinable).
 *  - The year renders through [DrsNumberWords] in the GENITIVE case —
 *    one source of truth for number words, now case-aware:
 *    «عام ألفين وستة وعشرين»، «عام ألف وتسعمائة وخمسة وأربعين».
 *  - Fail-closed everywhere: a wrong month, a wrong day, a two-digit
 *    year, mixed separators, embedded letters, a leading minus — every
 *    shape the grammar does not name comes back byte-identical. The
 *    output itself carries no separator, so applying the tool to its
 *    own output is a fixed point.
 *
 * DRS v1.10.0 — the same closed date parser now also speaks the
 * weekday (يوم الأسبوع): «2026-10-03» → «السبت». The day-of-week is
 * the documented Sakamoto congruence over the proleptic Gregorian
 * calendar (exhaustively verified against the reference calendar for
 * every accepted date 1..9999) — pure integer arithmetic, no platform
 * calendar, no guesswork. The weekday is a read-only projection of a
 * clean date: it never mutates the field, and prose stays untouched.
 */
object DrsDateWords {

    /** The closed month catalog: the official Gregorian names, 1..12. */
    private val MONTHS = listOf(
        "يناير", "فبراير", "مارس", "أبريل", "مايو", "يونيو",
        "يوليو", "أغسطس", "سبتمبر", "أكتوبر", "نوفمبر", "ديسمبر",
    )

    /**
     * The closed day-ordinal table, 1..31, written oblique (مجرور،
     * بلا تنوين — house style): «في العاشر»، «في العشرين»،
     * «في الحادي والعشرين». Index 0 is unused padding.
     */
    private val DAY_ORDINALS = listOf(
        "", "الأول", "الثاني", "الثالث", "الرابع", "الخامس",
        "السادس", "السابع", "الثامن", "التاسع", "العاشر",
        "الحادي عشر", "الثاني عشر", "الثالث عشر", "الرابع عشر", "الخامس عشر",
        "السادس عشر", "السابع عشر", "الثامن عشر", "التاسع عشر",
        "العشرين", "الحادي والعشرين", "الثاني والعشرين", "الثالث والعشرين",
        "الرابع والعشرين", "الخامس والعشرين", "السادس والعشرين",
        "السابع والعشرين", "الثامن والعشرين", "التاسع والعشرين",
        "الثلاثين", "الحادي والثلاثين",
    )

    /** The closed separator set — exactly one of these per date. */
    private val SEPARATORS = listOf('-', '/', '.')

    /** The closed weekday catalog, Sunday-first (Sakamoto 0 = الأحد). */
    private val WEEKDAYS = listOf(
        "الأحد", "الاثنين", "الثلاثاء", "الأربعاء", "الخميس", "الجمعة", "السبت",
    )

    /** The documented Sakamoto month-offset table (Jan..Dec). */
    private val SAKAMOTO_OFFSETS = intArrayOf(0, 3, 2, 5, 0, 3, 5, 1, 4, 6, 2, 4)

    /** Maps every accepted digit to its value, mirroring [DrsNumberWords]. */
    private fun digitValue(c: Char): Int? = when (c) {
        in '0'..'9' -> c - '0'
        in '\u0660'..'\u0669' -> c - '\u0660'
        in '\u06F0'..'\u06F9' -> c - '\u06F0'
        else -> null
    }

    /** The documented proleptic-Gregorian leap rule. */
    private fun isLeap(year: Int): Boolean =
        year % 4 == 0 && (year % 100 != 0 || year % 400 == 0)

    /** The closed month-length table under the leap rule. */
    private fun lengthOfMonth(year: Int, month: Int): Int = when (month) {
        1, 3, 5, 7, 8, 10, 12 -> 31
        4, 6, 9, 11 -> 30
        else -> if (isLeap(year)) 29 else 28
    }

    /** All-digits check for one component (never empty when true). */
    private fun isDigits(component: String): Boolean =
        component.isNotEmpty() && component.all { digitValue(it) != null }

    /** A digit run to its value — the caller has validated every char. */
    private fun toNumber(run: String): Int =
        run.fold(0) { acc, c -> acc * 10 + digitValue(c)!! }

    /** One parsed calendar date: the year, the month, the day of month. */
    private data class ParsedDate(val year: Int, val month: Int, val day: Int)

    /**
     * The closed date parser shared by the date-words and the weekday
     * engines (v1.10.0 refactor — behavior-preserving): the trimmed
     * field must be a clean three-component date over ONE consistent
     * separator, the year is wherever the four-digit component stands
     * (both ends or neither is ambiguous — rejected), and validation
     * is proleptic-Gregorian closed. Null for every other shape.
     */
    private fun parseDateOrNull(text: String): ParsedDate? {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return null
        // Exactly one separator system, used twice — never mixed systems,
        // never zero separators (a bare number is not this tool's date).
        val present = SEPARATORS.filter { trimmed.contains(it) }
        if (present.size != 1) return null
        val parts = trimmed.split(present.single()).map { it.trim() }
        if (parts.size != 3) return null
        val (first, middle, last) = parts
        if (!isDigits(first) || !isDigits(middle) || !isDigits(last)) return null
        // The year is wherever the four-digit component stands; both ends
        // four-digit (or neither) is ambiguous — fail-closed.
        val firstIsYear = first.length == 4
        val lastIsYear = last.length == 4
        if (firstIsYear == lastIsYear) return null
        val yearRun: String
        val monthRun: String
        val dayRun: String
        if (firstIsYear) {
            yearRun = first; monthRun = middle; dayRun = last
        } else {
            dayRun = first; monthRun = middle; yearRun = last
        }
        // Closed component grammar: day and month are 1..2 digits, the
        // year is exactly 4 digits (the branch selection guarantees it).
        if (dayRun.length > 2 || monthRun.length > 2) return null
        val year = toNumber(yearRun)
        val month = toNumber(monthRun)
        val day = toNumber(dayRun)
        if (year !in 1..9999) return null
        if (month !in 1..12) return null
        if (day !in 1..lengthOfMonth(year, month)) return null
        return ParsedDate(year, month, day)
    }

    /**
     * The formal Arabic date words for [text] when it is a clean date the
     * engine understands, or null when it is an honest no-op.
     */
    fun dateWordsOrNull(text: String): String? {
        val parsed = parseDateOrNull(text) ?: return null
        val yearWords = DrsNumberWords.arabicWordsOrNull(
            parsed.year.toString(), DrsNumberWords.Case.GENITIVE,
        ) ?: return null
        return "${DAY_ORDINALS[parsed.day]} من ${MONTHS[parsed.month - 1]} عام $yearWords"
    }

    /**
     * The Arabic weekday name for [text] when it is a clean date the
     * engine understands (the same closed parser as [dateWordsOrNull]),
     * or null when it is an honest no-op (v1.10.0). The day-of-week is
     * the documented Sakamoto congruence: pure integer arithmetic over
     * the proleptic Gregorian calendar, 0 = الأحد .. 6 = السبت.
     */
    fun weekdayOrNull(text: String): String? {
        val parsed = parseDateOrNull(text) ?: return null
        var y = parsed.year
        if (parsed.month < 3) y -= 1
        val dow = (y + y / 4 - y / 100 + y / 400 +
            SAKAMOTO_OFFSETS[parsed.month - 1] + parsed.day) % 7
        return WEEKDAYS[dow]
    }
}
