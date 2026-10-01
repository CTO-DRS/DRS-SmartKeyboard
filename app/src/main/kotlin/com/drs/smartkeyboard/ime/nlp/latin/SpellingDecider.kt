/*
 * Copyright (C) 2025 The DRS Smart Keyboard Project
 */

package com.drs.smartkeyboard.ime.nlp.latin

/**
 * DRS v1.19.0: pure, testable decision engine for real spell-check typo marking.
 *
 * Until now [LatinLanguageProvider.spell] unconditionally returned
 * `SpellingResult.validWord()`, so the fully-wired DrsSpellCheckerService could
 * never mark a single misspelling system-wide — a dead channel behind ~180
 * advertised locale subtypes. The engine ships real marking now, but the
 * project's core honesty rule stands: «المجهول لا ينذر كاذبًا» (the unknown
 * never warns falsely). Both functions below therefore encode deliberately
 * conservative gates; every gate is unit-tested.
 */
internal object SpellingDecider {

    /** Words shorter than this are too noisy to judge (2-letter abbreviations etc.). */
    const val MIN_SPELL_LENGTH = 3

    /** Mirror of LatinLanguageProvider.MAX_WORD_LENGTH. */
    const val MAX_SPELL_LENGTH = 32

    /**
     * Typo marking is only honest when the dictionary is rich enough that a
     * miss usually has a close neighbor and a hit usually exists. The bundled
     * Arabic (50k) and English (50k) dictionaries pass; the eight curated
     * v1.18.0 dictionaries (700–1,515 words) intentionally do not — flagging
     * against them would underline half of every sentence.
     */
    const val MIN_DICT_ENTRIES_FOR_TYPO = 10_000

    /**
     * Gate for words that must never be judged regardless of dictionary state.
     *
     * @param word the raw word (already trimmed, non-empty).
     * @param midSentence true when the caller knows previous context words exist;
     *   a capitalized word mid-sentence is very likely a proper noun (a name,
     *   a brand, a place) and is therefore excluded from judgement entirely.
     */
    fun isSpellableWord(word: String, midSentence: Boolean): Boolean {
        if (word.length !in MIN_SPELL_LENGTH..MAX_SPELL_LENGTH) return false
        // Numbers, emails/URL fragments, abbreviations with dots: out of scope.
        if (word.any { it.isDigit() }) return false
        if (word.any { !it.isLetter() }) return false
        // All-caps acronyms (DRS, NASA, OK) never flagged.
        if (word.all { it.isUpperCase() }) return false
        // Proper-noun protection: "Ahmed" mid-sentence is a name, not a typo of "ahead".
        if (midSentence && word.first().isUpperCase()) return false
        return true
    }

    /**
     * Final decision: flag a red underline only when the word is unknown to BOTH
     * the dictionary and the user's personal/learned words, the dictionary is
     * rich enough to judge, and at least one plausible correction exists (a
     * real "did you mean?" target within edit distance 1).
     */
    fun shouldFlagTypo(
        wordInDict: Boolean,
        wordInUserWords: Boolean,
        dictEntries: Int,
        correctionCount: Int,
    ): Boolean {
        if (wordInDict || wordInUserWords) return false
        if (dictEntries < MIN_DICT_ENTRIES_FOR_TYPO) return false
        return correctionCount > 0
    }
}
