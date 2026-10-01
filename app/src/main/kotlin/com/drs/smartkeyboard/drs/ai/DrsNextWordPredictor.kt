/*
 * Copyright (C) 2025 The DRS Smart Keyboard Project
 */

package com.drs.smartkeyboard.drs.ai

/**
 * DRS M1.3 — the interpolated next-word predictor: the personal bigram
 * table (DrsLearningEngine — what THIS user actually types) and the
 * static corpus bigram row (what the language does overall) are merged
 * by linear interpolation:
 *
 *     score(w) = ALPHA · p_personal(w | prev) + (1 − ALPHA) · p_static(w | prev)
 *
 * ALPHA = 0.65 — personal history dominates but never silences the
 * corpus (a brand-new device still predicts like a speaker, and a heavy
 * user's habits surface fast).
 *
 * Lemma fallback: when the previous word has no usable row in EITHER
 * source (a rare inflected form), the predictor falls back to the rows
 * of the word's LEMMA, discounted by [LEMMA_DISCOUNT] = 0.7 — the stem
 * («كتب») carries the same next-word tendency as its inflection
 * («يكتبها»), just at reduced trust.
 */
object DrsNextWordPredictor {

    const val ALPHA = 0.65
    const val LEMMA_DISCOUNT = 0.7

    data class Prediction(val word: String, val score: Double)

    /**
     * @param personal        (word, count) row from the personal bigram table
     * @param static          (word, count) row from the static bigram table
     * @param personalFallback personal row of the previous word's LEMMA (may be empty)
     * @param staticFallback  static row of the previous word's LEMMA (may be empty)
     * @param limit           maximum predictions returned
     */
    fun predict(
        personal: List<Pair<String, Int>>,
        static: List<Pair<String, Int>>,
        personalFallback: List<Pair<String, Int>> = emptyList(),
        staticFallback: List<Pair<String, Int>> = emptyList(),
        limit: Int = 5,
    ): List<Prediction> {
        if (limit <= 0) return emptyList()
        val words = LinkedHashSet<String>()
        personal.forEach { words.add(it.first) }
        static.forEach { words.add(it.first) }
        personalFallback.forEach { words.add(it.first) }
        staticFallback.forEach { words.add(it.first) }
        if (words.isEmpty()) return emptyList()

        val pTotal = personal.sumOf { it.second }.coerceAtLeast(1)
        val sTotal = static.sumOf { it.second }.coerceAtLeast(1)
        val pfTotal = personalFallback.sumOf { it.second }.coerceAtLeast(1)
        val sfTotal = staticFallback.sumOf { it.second }.coerceAtLeast(1)
        val hasDirectRow = personal.isNotEmpty() || static.isNotEmpty()
        val fallbackWeight = if (hasDirectRow) 0.0 else LEMMA_DISCOUNT

        return words.map { word ->
            val p = personal.firstOrNull { it.first == word }?.second?.div(pTotal.toDouble()) ?: 0.0
            val s = static.firstOrNull { it.first == word }?.second?.div(sTotal.toDouble()) ?: 0.0
            var score = ALPHA * p + (1.0 - ALPHA) * s
            if (!hasDirectRow) {
                val pf = personalFallback.firstOrNull { it.first == word }?.second?.div(pfTotal.toDouble()) ?: 0.0
                val sf = staticFallback.firstOrNull { it.first == word }?.second?.div(sfTotal.toDouble()) ?: 0.0
                score = score * (1.0 - fallbackWeight) + LEMMA_DISCOUNT * (ALPHA * pf + (1.0 - ALPHA) * sf)
            }
            Prediction(word, score)
        }
            .sortedWith(compareByDescending<Prediction> { it.score }.thenBy { it.word })
            .take(limit)
    }
}
