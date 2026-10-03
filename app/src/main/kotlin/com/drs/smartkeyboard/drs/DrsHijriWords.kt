/*
 * Copyright (C) 2026 The DRS Smart Keyboard Project
 */

package com.drs.smartkeyboard.drs

import java.time.LocalDate
import java.time.chrono.HijrahDate
import java.time.temporal.ChronoField

/**
 * DRS v2.1.0 — the deterministic calendar-conversion engine (التحويل
 * بين التقويمين) behind the GREGORIAN_TO_HIJRI and HIJRI_TO_GREGORIAN
 * text tools:
 *  - GREGORIAN_TO_HIJRI: a clean Gregorian date becomes its Hijri
 *    documentary phrase — «2026-02-18» → «الأول من رمضان عام ألف
 *    وأربعمائة وسبعة وأربعين هجري».
 *  - HIJRI_TO_GREGORIAN: the exact inverse — a clean Hijri date in
 *    digits becomes its Gregorian documentary phrase through the SAME
 *    formatter as DATE_WORDS — «1447/9/1» → «الثامن عشر من فبراير
 *    عام ألفين وستة وعشرين». Both directions on one source of truth:
 *    the whole calendar family round-trips.
 *
 * The seventh round closes: the calendar speaks both ways.
 *
 * Pure projection over the platform's embedded Umm al-Qura table, no
 * model, no network, no guesswork:
 *  - The documented convention IS the platform's HijrahDate table
 *    (hijrah-config-umalqura) — the same source of truth the number
 *    panel's Hijri formatting already speaks («1 رمضان 1447 هـ»), so
 *    the whole app speaks ONE Hijri. Umm al-Qura table revisions are
 *    known to differ by one day between editions; the convention is
 *    named, not guessed: whatever the platform says, the tool says.
 *  - The Gregorian direction parses through the SAME closed Gregorian
 *    parser as DATE_WORDS (shared after the v2.1.0 visibility
 *    refactor); the Hijri direction parses through a closed Hijri
 *    parser mirroring those shape rules exactly: three components
 *    over ONE consistent separator from { - , / , . }, the year
 *    wherever the four-digit component stands (Hijri years 1300..1600
 *    are four digits), day and month 1..2 digits, digits locale-blind
 *    on purpose.
 *  - The platform zone is closed: 1300-01-01 .. 1600-12-30 AH
 *    (1882-11-12 .. 2174-11-25 Gregorian). The Hijri parser pins the
 *    year range explicitly; month lengths stay the platform's own
 *    (a 29-day month rejects its 30th through the honest no-op).
 *    Anything outside the zone throws inside the platform — caught
 *    and turned into the honest no-op, never a crash, never an
 *    invented date.
 *  - The day renders through the SAME closed 31-entry day-ordinal
 *    table as [DrsDateWords] (v2.1.0 sharing); the month renders from
 *    the closed 12-name Hijri catalog (محرم..ذو الحجة); the year
 *    renders through [DrsNumberWords] in the GENITIVE — one source of
 *    truth for number words. The inverse direction renders through
 *    the SAME Gregorian-words formatter as DATE_WORDS.
 *  - The era suffix «هجري» is the documented phrase convention: the
 *    genitive adjective after the year words, no tanwin (house
 *    style) — «عام ألف وأربعمائة وسبعة وأربعين هجري». The inverse
 *    phrase carries no era word — the Gregorian month names (يناير..
 *    ديسمبر) already name the calendar, exactly as DATE_WORDS speaks.
 *  - Fail-closed everywhere: prose, mixed separators, two-digit
 *    years, wrong months, wrong days and out-of-zone dates come back
 *    byte-identical. The outputs carry no digits and no separator,
 *    so applying either tool to its own output is a fixed point.
 */
object DrsHijriWords {

    /** The closed Hijri month catalog: the twelve canonical names,
     *  1..12 (محرم، صفر، ربيع الأول، ربيع الآخر، جمادى الأولى،
     *  جمادى الآخرة، رجب، شعبان، رمضان، شوال، ذو القعدة، ذو الحجة). */
    private val HIJRI_MONTHS = listOf(
        "محرم", "صفر", "ربيع الأول", "ربيع الآخر", "جمادى الأولى", "جمادى الآخرة",
        "رجب", "شعبان", "رمضان", "شوال", "ذو القعدة", "ذو الحجة",
    )

    /** The documented era suffix of the Hijri phrase (no tanwin). */
    private const val ERA_HIJRI = "هجري"

    /**
     * The Hijri documentary phrase for [text] when it is a clean
     * Gregorian date inside the platform's closed Umm al-Qura zone,
     * or null when it is an honest no-op.
     */
    fun gregorianToHijriWordsOrNull(text: String): String? {
        val parsed = DrsDateWords.parseDateOrNull(text) ?: return null
        return try {
            val hijri = HijrahDate.from(
                LocalDate.of(parsed.year, parsed.month, parsed.day),
            )
            val year = hijri.get(ChronoField.YEAR)
            val month = hijri.get(ChronoField.MONTH_OF_YEAR)
            val day = hijri.get(ChronoField.DAY_OF_MONTH)
            val yearWords = DrsNumberWords.arabicWordsOrNull(
                year.toString(), DrsNumberWords.Case.GENITIVE,
            ) ?: return null
            "${DrsDateWords.DAY_ORDINALS[day]} من ${HIJRI_MONTHS[month - 1]} عام $yearWords $ERA_HIJRI"
        } catch (e: Exception) {
            // Out of the platform's closed Umm al-Qura zone (or a date
            // the table does not carry) — the honest no-op, never a
            // guess. The platform also throws raw IndexOutOfBounds at
            // the zone edges (not only DateTimeException), so the
            // catch is deliberately broad: fail-closed, never crash.
            null
        }
    }

    /**
     * The Gregorian documentary phrase for [text] when it is a clean
     * Hijri date inside the platform's closed Umm al-Qura zone, or
     * null when it is an honest no-op. The phrase renders through the
     * SAME formatter as DATE_WORDS — one shape for one calendar.
     */
    fun hijriToGregorianWordsOrNull(text: String): String? {
        val parsed = parseHijriOrNull(text) ?: return null
        return try {
            val gregorian = LocalDate.from(
                HijrahDate.of(parsed.year, parsed.month, parsed.day),
            )
            DrsDateWords.gregorianWordsFor(
                gregorian.year, gregorian.monthValue, gregorian.dayOfMonth,
            )
        } catch (e: Exception) {
            // Out of the platform's closed Umm al-Qura zone (or a day
            // beyond the month's own length) — the honest no-op.
            null
        }
    }

    /** One parsed Hijri date: the year, the month, the day of month. */
    internal data class ParsedHijriDate(val year: Int, val month: Int, val day: Int)

    /** All-digits check for one component (never empty when true). */
    private fun isDigits(component: String): Boolean =
        component.isNotEmpty() && component.all { DrsDateWords.digitValue(it) != null }

    /** A digit run to its value — the caller has validated every char. */
    private fun toNumber(run: String): Int =
        run.fold(0) { acc, c -> acc * 10 + DrsDateWords.digitValue(c)!! }

    /**
     * The closed Hijri date parser — the mirror of the Gregorian one
     * in [DrsDateWords]: the trimmed field must be a clean
     * three-component date over ONE consistent separator from
     * { - , / , . }, the year is wherever the four-digit component
     * stands (both ends or neither is ambiguous — rejected), day and
     * month are 1..2 digits, and the year is pinned to the platform's
     * closed Umm al-Qura zone 1300..1600. Null for every other shape.
     */
    internal fun parseHijriOrNull(text: String): ParsedHijriDate? {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return null
        // Exactly one separator system, used twice — never mixed
        // systems, never zero separators (a bare number is not this
        // tool's date).
        val present = HijriSeparators.ALL.filter { trimmed.contains(it) }
        if (present.size != 1) return null
        val parts = trimmed.split(present.single()).map { it.trim() }
        if (parts.size != 3) return null
        val (first, middle, last) = parts
        if (!isDigits(first) || !isDigits(middle) || !isDigits(last)) return null
        // The year is wherever the four-digit component stands; both
        // ends four-digit (or neither) is ambiguous — fail-closed.
        val firstIsYear = first.length == 4
        val lastIsYear = last.length == 4
        if (firstIsYear == lastIsYear) return null
        val year: Int
        val monthRun: String
        val dayRun: String
        if (firstIsYear) {
            year = toNumber(first); monthRun = middle; dayRun = last
        } else {
            dayRun = first; monthRun = middle; year = toNumber(last)
        }
        // Closed component grammar: day and month are 1..2 digits.
        if (dayRun.length > 2 || monthRun.length > 2) return null
        val month = toNumber(monthRun)
        val day = toNumber(dayRun)
        // The platform's closed Umm al-Qura zone, pinned by year; the
        // month is 1..12 by the calendar; the day is left to the
        // platform's own month lengths (29 or 30 — never invented).
        if (year !in HIJRI_YEAR_FIRST..HIJRI_YEAR_LAST) return null
        if (month !in 1..12) return null
        if (day !in 1..30) return null
        return ParsedHijriDate(year, month, day)
    }

    /** The closed Hijri year zone of the platform's Umm al-Qura table. */
    internal const val HIJRI_YEAR_FIRST = 1300
    internal const val HIJRI_YEAR_LAST = 1600

    /** The closed separator set — exactly one of these per date. */
    private object HijriSeparators {
        val ALL = listOf('-', '/', '.')
    }
}
