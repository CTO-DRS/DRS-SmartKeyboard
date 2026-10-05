/*
 * Copyright (C) 2025-2026 The DRS Smart Keyboard Project
 */

package com.drs.smartkeyboard.ime.nlp

/**
 * DRS v2.7.0 «مزايا البحث الخارجي» — smart punctuation, the honest slice.
 *
 * Researched from the reference keyboards (Gboard / AOSP LatinIME
 * heritage): when the user types a double hyphen directly after whitespace
 * (or at the very start of the text) and then commits a space, the two
 * dashes are rewritten into a real em dash (—) before that space lands.
 *
 * The doctrine of this round is restraint, so the matcher is deliberately
 * narrow:
 *  - a run of THREE or more dashes is never touched — a user drawing a
 *    horizontal line meant a line, not a dash;
 *  - a double dash glued to a letter or digit (a--b, 1--2) is never
 *    touched — inside a token the dashes belong to the token;
 *  - nothing is ever inserted, only rewritten: the conversion happens
 *    exactly where the dashes already are.
 *
 * Pure and deterministic: no regex, no locale, no context beyond the text
 * that already sits before the cursor.
 */
object DrsSmartPunctuation {

    /** The em dash this round commits. A real U+2014, never a fallback. */
    const val EM_DASH: String = "—"

    /** Number of trailing characters a positive match rewrites. */
    const val MATCH_LENGTH: Int = 2

    /**
     * Returns [MATCH_LENGTH] when the text before the cursor ends with a
     * convertible double hyphen, else 0. A convertible tail is exactly
     * "--" preceded by nothing (text start) or by whitespace, and not
     * preceded by a third dash.
     */
    fun findEmDashTail(text: CharSequence): Int {
        if (text.length < MATCH_LENGTH) return 0
        val dashStart = text.length - MATCH_LENGTH
        if (text[dashStart] != '-' || text[dashStart + 1] != '-') return 0
        // A longer dash run is the user drawing a line — leave it intact.
        if (dashStart > 0 && text[dashStart - 1] == '-') return 0
        if (dashStart == 0) return MATCH_LENGTH
        return if (text[dashStart - 1].isWhitespace()) MATCH_LENGTH else 0
    }
}
