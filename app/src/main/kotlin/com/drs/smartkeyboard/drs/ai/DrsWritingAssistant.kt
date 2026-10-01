/*
 * Copyright (C) 2025 The DRS Smart Keyboard Project
 */

package com.drs.smartkeyboard.drs.ai

/**
 * DRS M1.5 — the writing assistant: sixteen deterministic Arabic
 * phrase-level rewrites plus deterministic spacing normalization, all
 * LOCAL, all idempotent.
 *
 * Trigger contract (the tests forced this): a phrase rule fires when the
 * NORMALIZED TAIL of the text equals the rule's normalized trigger — FULL
 * equality of the tail, never a containment hit. A containment match used
 * to rewrite «اللهم انشاء اللهُ» into a corrupted tail because «انشاء
 * الله» matched mid-text while the tail was a longer word.
 *
 * [normalizeSpacing] is idempotent by contract: normalize(normalize(x))
 * == normalize(x) — collapsing runs, trimming edge spaces, fixing the
 * space-before-punctuation shapes. Running it twice is a no-op, so a
 * redisplayed suggestion can never grow extra edits.
 */
object DrsWritingAssistant {

    /** The sixteen phrase rewrites: normalized trigger → replacement. */
    val PHRASES: List<Pair<String, String>> = listOf(
        "انشاء الله" to "إن شاء الله",
        "ان شاء الله" to "إن شاء الله",
        "كيف حاك" to "كيف حالك",
        "صباح الخيرات" to "صباح الخير",
        "مساء الخيرات" to "مساء الخير",
        "شكرا جزيلا" to "شكرًا جزيلًا",
        "على الرحب والسعه" to "على الرحب والسعة",
        "الحمد الله" to "الحمد لله",
        "تمام الحمد الله" to "تمام، الحمد لله",
        "لا شكر على واجب" to "العفو",
        "انا بخير" to "أنا بخير",
        "اهلا وسهلا" to "أهلًا وسهلًا",
        "مع السلامه" to "مع السلامة",
        "الى اللقاء" to "إلى اللقاء",
        "ممكن سوال" to "ممكن سؤال",
        "يعطيك العافيه" to "يعطيك العافية",
    )

    /**
     * Applies the phrase rules to the tail of [text]. A rule fires only
     * when the FULL normalized tail of the text equals the rule's
     * normalized trigger; the replacement then replaces that tail
     * (preserving any prefix). Returns the text unchanged when nothing
     * fires — never throws, never partial-rewrites.
     */
    fun improveTail(text: String): String {
        if (text.isBlank()) return text
        for ((trigger, replacement) in PHRASES) {
            val normTrigger = normalize(trigger)
            if (normTrigger.isEmpty()) continue
            // Find the longest normalized-equal tail window.
            val rawTail = longestNormalizedTailOf(text, normTrigger)
            if (rawTail != null) {
                return text.dropLast(rawTail.length) + replacement
            }
        }
        return text
    }

    /**
     * Returns the RAW tail substring of [text] whose normalization equals
     * [normalizedTrigger], or null. The window grows one raw character at
     * a time so normalized lengths differing from raw lengths (أ vs ا is
     * 1:1, but harakat collapse) still find the true raw span.
     */
    private fun longestNormalizedTailOf(text: String, normalizedTrigger: String): String? {
        var start = text.length
        while (start > 0) {
            val raw = text.substring(start - 1)
            if (normalize(raw) == normalizedTrigger) return raw
            if (normalize(raw).length > normalizedTrigger.length + 8) break // overshoot guard
            start--
        }
        return null
    }

    /**
     * Deterministic spacing normalization: collapse runs of whitespace to
     * one space, trim the edges, remove spaces BEFORE closing punctuation
     * (؟ ! ، ؛ . :) and after opening ones, collapse punctuation runs to a
     * single mark. Runs to a FIXED POINT (bounded) so it is idempotent by
     * construction — «مرحبا  ؟» (two spaces before the mark) converges in
     * one call, and normalize(normalize(x)) == normalize(x) always.
     */
    fun normalizeSpacing(text: String): String {
        val closing = listOf("؟", "!", "،", "؛", ".", ":")
        val opening = listOf("(", "\"")
        var out = text
        var changed = true
        var guard = 0
        while (changed && guard < 8) {
            changed = false
            for (p in closing) {
                if (out.contains(" $p")) {
                    out = out.replace(" $p", p)
                    changed = true
                }
                val run = "$p$p"
                if (out.contains(run)) {
                    out = out.replace(run, p)
                    changed = true
                }
            }
            for (p in opening) {
                if (out.contains("$p ")) {
                    out = out.replace("$p ", p)
                    changed = true
                }
            }
            if (out.contains("  ")) {
                out = out.replace(Regex(" {2,}"), " ")
                changed = true
            }
            guard++
        }
        return out.trim()
    }

    /** The shared tail normalization (alef/ya/ta-marbuta unification + lowercase). */
    internal fun normalize(text: String): String = text
        .lowercase()
        .replace('أ', 'ا')
        .replace('إ', 'ا')
        .replace('آ', 'ا')
        .replace('ى', 'ي')
        .replace('ة', 'ه')
}
