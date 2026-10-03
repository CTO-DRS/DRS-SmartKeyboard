/*
 * Copyright (C) 2026 The DRS Smart Keyboard Project
 */

package com.drs.smartkeyboard.drs

import java.time.LocalDate
import java.time.chrono.HijrahDate
import java.time.temporal.ChronoField

/**
 * DRS v2.1.0 — the deterministic calendar-conversion engine (التحويل
 * بين التقويمين) behind the GREGORIAN_TO_HIJRI text tool: a clean
 * Gregorian date becomes its Hijri documentary phrase — «2026-02-18» →
 * «الأول من رمضان عام ألف وأربعمائة وسبعة وأربعين هجري» — the phrase
 * official letters and announcements write when the date is kept in
 * both calendars.
 *
 * The seventh round opens: the calendar speaks. v1.8.0 made Gregorian
 * dates speak; v2.1.0 lets them cross to the other calendar and back.
 *
 * Pure projection over the platform's embedded Umm al-Qura table, no
 * model, no network, no guesswork:
 *  - The documented convention IS the platform's HijrahDate table
 *    (hijrah-config-umalqura) — the same source of truth the number
 *    panel's Hijri formatting already speaks («1 رمضان 1447 هـ»), so
 *    the whole app speaks ONE Hijri. Umm al-Qura table revisions are
 *    known to differ by one day between editions; the convention is
 *    named, not guessed: whatever the platform says, the tool says.
 *  - The input is the SAME closed Gregorian parser as DATE_WORDS
 *    (shared after the v2.1.0 visibility refactor): three components
 *    over ONE consistent separator from { - , / , . }, the year
 *    wherever the four-digit component stands, proleptic-Gregorian
 *    validation, digits locale-blind on purpose.
 *  - The platform zone is closed: 1300-01-01 .. 1600-12-30 AH
 *    (1882-11-12 .. 2174-11-25 Gregorian). Anything outside throws
 *    inside the platform — caught and turned into the honest no-op,
 *    never a crash, never an invented date.
 *  - The day renders through the SAME closed 31-entry day-ordinal
 *    table as [DrsDateWords] (v2.1.0 sharing); the month renders from
 *    the closed 12-name Hijri catalog (محرم..ذو الحجة); the year
 *    renders through [DrsNumberWords] in the GENITIVE — one source of
 *    truth for number words.
 *  - The era suffix «هجري» is the documented phrase convention: the
 *    genitive adjective after the year words, no tanwin (house
 *    style) — «عام ألف وأربعمائة وسبعة وأربعين هجري».
 *  - Fail-closed everywhere: prose, mixed separators, two-digit
 *    years, wrong months, wrong days and out-of-zone dates come back
 *    byte-identical. The output carries no digits and no separator,
 *    so applying the tool to its own output is a fixed point.
 */
object DrsHijriWords {

    /**
     * The closed Hijri month catalog: the twelve canonical names,
     * 1..12 (محرم، صفر، ربيع الأول، ربيع الآخر، جمادى الأولى،
     * جمادى الآخرة، رجب، شعبان، رمضان، شوال، ذو القعدة، ذو الحجة).
     */
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
}
