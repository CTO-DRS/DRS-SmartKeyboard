/*
 * Copyright (C) 2025 The DRS Smart Keyboard Project
 */

package com.drs.smartkeyboard.ime.nlp

/**
 * DRS v2.9.0 «التراجع الصادق» — the pure contract behind "backspace undoes
 * the auto-correction" (HeliBoard/OpenBoard heritage, the same affordance
 * AOSP-derived keyboards have shipped for years).
 *
 * The problem it solves is the silent rewrite: the user types «helo», hits
 * space, and the keyboard auto-commits «hello » — the typed word is gone.
 * HeliBoard answers with one honest affordance: the FIRST backspace right
 * after that silent commit restores the word exactly as typed. Manual picks
 * from the suggestion row are deliberate choices and are never reverted;
 * that distinction is enforced at the arming call sites (auto-commit paths
 * arm, manual taps never do).
 *
 * Everything here is pure and deterministic — no Android types, no clocks,
 * no randomness — so the full behavior surface is testable without an
 * instrumented environment. The integration (KeyboardManager) owns the
 * state machine; this contract owns the decisions:
 *
 *  - [arm] decides whether a silent commit deserves a revert at all. The
 *    honest no-op is central: if the committed text equals what the user
 *    typed, nothing was silently rewritten and there is nothing to restore
 *    — arming is refused. Blanks and oversized words are refused too (the
 *    same 32-char word cap the learning path uses).
 *  - [plan] is self-verifying: before any rewrite it re-checks the editor
 *    text still ends with exactly the committed word (plus a bounded tail
 *    of non-word characters — the trailing space, or «, » after interword
 *    punctuation). A stale or drifted state — the user typed more letters,
 *    moved the cursor, the word grew into a longer word, the field changed
 *    — fails the verification and yields null, so a stale arm can NEVER
 *    rewrite an unrelated word. The matched region covers the corrected
 *    word ONLY; the tail (space/punctuation) stays untouched, and the
 *    character before the word must be a non-word boundary guard, so
 *    «Xhello » never matches «hello » inside a longer word.
 *
 * The caller applies the plan as a single selection-replace (the editor's
 * battle-tested setSelection + commitText pair) — one undoable operation
 * in the host editor, cursor landing right after the restored word, the
 * trailing space preserved.
 */
object DrsCorrectionRevert {

    /**
     * An armed revert opportunity: [typed] is what the user actually typed
     * (to be restored), [corrected] is what the auto-commit silently wrote
     * (to be replaced). Created ONLY for genuinely different, in-cap words.
     */
    data class Pending(val typed: String, val corrected: String)

    /**
     * A verified rewrite plan: replace the half-open region
     * [replaceStart, replaceEndExclusive) of the editor text (coordinates
     * into the full editor string, equivalent to the suffix of
     * `beforeCursor` handed to [plan]) with [Pending.typed].
     */
    data class Plan(val replaceStart: Int, val replaceEndExclusive: Int)

    /** Word-length cap, mirroring the learning path's 2..32 letter window. */
    const val MAX_WORD_LENGTH: Int = 32

    /**
     * Maximum number of non-word characters allowed between the committed
     * word and the cursor: « » after a space commit, «, » or «. » after
     * interword punctuation. A longer run means the state drifted — refuse.
     */
    const val MAX_TAIL_LENGTH: Int = 2

    /**
     * Decides whether a silent auto-commit deserves a revert.
     *
     * @param typed     the word the user typed, captured BEFORE the commit.
     * @param committed the word the auto-commit actually wrote.
     * @return the pending revert to hold, or null when there is honestly
     *         nothing to revert: identical texts (no silent rewrite
     *         happened), blank words, or words over [MAX_WORD_LENGTH].
     */
    fun arm(typed: String, committed: String): Pending? {
        if (typed.isBlank() || committed.isBlank()) return null
        // The honest no-op: an auto-commit that wrote exactly what was typed
        // (e.g. the top suggestion merely matched) is not a correction.
        if (typed == committed) return null
        if (typed.length > MAX_WORD_LENGTH || committed.length > MAX_WORD_LENGTH) return null
        return Pending(typed = typed, corrected = committed)
    }

    /**
     * Verifies the editor state still matches the armed revert and computes
     * the rewrite region when it does.
     *
     * The corrected word must appear ending at most [MAX_TAIL_LENGTH]
     * non-word characters before the cursor, preceded by a word boundary
     * (start of text or a non-letter-or-digit character). Any letter or
     * digit inside the tail, a longer word swallowing the corrected one,
     * extra letters typed after the commit — all refuse the plan.
     *
     * @param pending      the armed revert opportunity.
     * @param beforeCursor the full editor text before the (collapsed) cursor.
     * @return the rewrite plan, or null when verification fails (the caller
     *         disarms and falls through to the normal delete).
     */
    fun plan(pending: Pending, beforeCursor: String): Plan? {
        val corrected = pending.corrected
        if (corrected.isEmpty()) return null
        for (tail in 0..MAX_TAIL_LENGTH) {
            val wordEndExclusive = beforeCursor.length - tail
            val wordStart = wordEndExclusive - corrected.length
            if (wordStart < 0) break
            if (!beforeCursor.regionMatches(wordStart, corrected, 0, corrected.length)) continue
            // Word-boundary guard before the word: a letter/digit there
            // means the matched region is a suffix of a longer word —
            // rewriting it would corrupt the user's text, so refuse.
            if (wordStart > 0 && beforeCursor[wordStart - 1].isLetterOrDigit()) continue
            // The tail itself must be a bounded run of non-word characters
            // (space, punctuation) — letters/digits mean the user kept
            // typing after the commit, so the state has drifted.
            var tailClean = true
            for (i in wordEndExclusive until beforeCursor.length) {
                if (beforeCursor[i].isLetterOrDigit()) {
                    tailClean = false
                    break
                }
            }
            if (!tailClean) continue
            return Plan(replaceStart = wordStart, replaceEndExclusive = wordEndExclusive)
        }
        return null
    }
}
