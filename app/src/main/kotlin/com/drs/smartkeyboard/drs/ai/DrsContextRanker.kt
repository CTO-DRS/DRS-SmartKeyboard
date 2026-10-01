/*
 * Copyright (C) 2025 The DRS Smart Keyboard Project
 */

package com.drs.smartkeyboard.drs.ai

/**
 * DRS M1.4 — the context ranker: detects an INTERROGATIVE context and
 * promotes the candidates that typically open an answer.
 *
 * The window is TWO tokens, not one — the tests caught that a single
 * window misses the common «ماذا تريد أن…» shape, where the
 * interrogative sits two words back and the immediate previous token is
 * the conjunction that swallowed it. Detection scans the last
 * [WINDOW] tokens (normalized, case/alef-insensitive matching below).
 *
 * Promotion set = answer openers (affirmation, negation, causation,
 * temporal resume). The reorder is STABLE — equal candidates keep their
 * relative order, and outside an interrogative context the call is a
 * documented no-op.
 */
object DrsContextRanker {

    /** The interrogative detection window, in tokens. */
    const val WINDOW = 2

    /** Arabic interrogatives — stored PRE-NORMALIZED so matching is exact. */
    val INTERROGATIVES: Set<String> = setOf(
        "من", "ما", "ماذا", "متى", "اين", "أين", "كيف", "لماذا", "ليمذة",
        "هل", "كم", "اي", "أي", "ايان", "ليش", "مين",
    ).map { normalize(it) }.toSet()

    /** Candidates promoted inside an interrogative context — pre-normalized. */
    val ANSWER_OPENERS: Set<String> = setOf(
        "نعم", "ايه", "أجل", "حسنا", "لا", "لأن", "لان", "لانه", "لانها",
        "لأني", "لاني", "عند", "عندما", "حين", "حينها", "سوف", "ربما", "اكيد", "أكيد",
    ).map { normalize(it) }.toSet()

    /** True when an interrogative sits within the last [WINDOW] tokens. */
    fun isInterrogativeContext(previousTokens: List<String>): Boolean {
        if (previousTokens.size < WINDOW) return false
        return previousTokens.takeLast(WINDOW).any { token ->
            normalize(token) in INTERROGATIVES
        }
    }

    /**
     * Reorders [candidates] so the answer openers come first (input
     * order preserved within each group) when [previousTokens] ends in
     * an interrogative context; otherwise returns [candidates] as-is.
     */
    fun <T> rerank(previousTokens: List<String>, candidates: List<T>, textOf: (T) -> String): List<T> {
        if (candidates.isEmpty()) return candidates
        if (!isInterrogativeContext(previousTokens)) return candidates
        return candidates.sortedBy { candidate ->
            if (normalize(textOf(candidate)) in ANSWER_OPENERS) 0 else 1
        }
    }

    /** Shared normalization: alef forms unified, ta-marbuta → ha, lowercase. */
    internal fun normalize(token: String): String = token
        .lowercase()
        .replace('أ', 'ا')
        .replace('إ', 'ا')
        .replace('آ', 'ا')
        .replace('ى', 'ي')
        .replace('ة', 'ه')
}
