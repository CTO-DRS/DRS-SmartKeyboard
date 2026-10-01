/*
 * Copyright (C) 2020-2025 The DRS Smart Keyboard Project
 */

package com.drs.smartkeyboard.ime.nlp

/**
 * DRS v1.17.0: the pure decider behind TRUE autocorrect. Until now no
 * candidate was ever marked auto-commit eligible, so pressing space
 * always committed the typo verbatim. This decider gates the (already
 * existing) auto-commit plumbing conservatively — an auto-fix may fire
 * ONLY when every one of these holds:
 *
 *  1. the user typed a full word candidate of at least [MIN_WORD_LENGTH]
 *     letters (no single/two-letter "fixes", no mid-word firing);
 *  2. the typed word is NOT a prefix of any dictionary word (i.e. the
 *     provider served its "did you mean?" fallback — the word is a real
 *     typo, not just an uncommon inflection);
 *  3. the correction is a known word with a frequency of at least
 *     [MIN_CORRECTION_FREQ] (out of 255) — only very common words earn
 *     the right to silently replace what the user typed;
 *  4. the caller already honored session guards (password fields,
 *     incognito) by passing [enabled] = pref AND safe session.
 *
 * Pure and JVM-testable; the provider owns the actual candidate marking.
 */
object AutocorrectDecider {

    /** Words shorter than this are never auto-corrected. */
    const val MIN_WORD_LENGTH = 3

    /**
     * Minimum dictionary frequency (0..255) for a correction to qualify.
     * Only the head of the corpus passes — "the", "the", «من», «في»…
     */
    const val MIN_CORRECTION_FREQ = 170

    /**
     * @param typedLength        length of the typed (composing) word
     * @param typedIsPrefixMatch true when dictionary words START with the
     *        typed word (meaning it is plausibly a valid unfinished word)
     * @param correctionFreq     frequency of the top correction (0..255)
     * @param enabled            pref + session guards folded in by the caller
     */
    fun shouldAutoCommit(
        typedLength: Int,
        typedIsPrefixMatch: Boolean,
        correctionFreq: Int,
        enabled: Boolean,
    ): Boolean {
        if (!enabled) return false
        if (typedLength < MIN_WORD_LENGTH) return false
        if (typedIsPrefixMatch) return false
        if (correctionFreq < MIN_CORRECTION_FREQ) return false
        return true
    }
}
