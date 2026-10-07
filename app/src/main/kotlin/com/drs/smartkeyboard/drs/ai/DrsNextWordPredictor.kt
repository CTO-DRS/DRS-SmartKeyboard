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
 *
 * v2.13.0 «السياق الأعمق» — the TRIGRAM layer: when a two-word context
 * row exists (a static chain from [DrsTrigramChains] and/or a personal
 * chain from [DrsLearningEngine.learnTrigram]), the single-word bigram
 * score is interpolated with the two-word score:
 *
 *     score(w) = TRI_WEIGHT · p_tri(w | p2, p1) + (1 − TRI_WEIGHT) · bigramScore(w)
 *
 *     p_tri(w) = TRI_PERSONAL_ALPHA · p_tri_personal(w) + (1 − TRI_PERSONAL_ALPHA) · p_tri_static(w)
 *
 * TRI_WEIGHT = 0.6 — when the two words behind the cursor match a CURATED
 * chain, the language's own statistics must not bury it: a strength-9
 * tail (بسم الله → الرحمن) carries 0.6·0.35 = 0.21, which a corpus
 * bigram word only beats with a share above ~0.8 — exactly the dominant
 * pairs the bigram layer is right about anyway. TRI_PERSONAL_ALPHA
 * mirrors ALPHA: personal chains dominate, the curated corpus never dies.
 *
 * THE NO-OP CONTRACT (pinned by tests): with BOTH trigram rows empty the
 * function returns EXACTLY the pre-v2.13.0 ordering — the trigram layer
 * exists only where it has something honest to say.
 */
object DrsNextWordPredictor {

    const val ALPHA = 0.65
    const val LEMMA_DISCOUNT = 0.7

    /** v2.13.0 — the weight of the two-word context when a trigram row exists. */
    const val TRI_WEIGHT = 0.6

    /** v2.13.0 — personal chains vs static chains inside the trigram layer. */
    const val TRI_PERSONAL_ALPHA = 0.65

    data class Prediction(val word: String, val score: Double)

    /**
     * @param personal        (word, count) row from the personal bigram table
     * @param static          (word, count) row from the static bigram table
     * @param personalFallback personal row of the previous word's LEMMA (may be empty)
     * @param staticFallback  static row of the previous word's LEMMA (may be empty)
     * @param personalTrigram (word, count) row from the personal trigram table for (p2, p1) — may be empty
     * @param staticTrigram   (word, strength) row from the static chain table for (p2, p1) — may be empty
     * @param limit           maximum predictions returned
     */
    fun predict(
        personal: List<Pair<String, Int>>,
        static: List<Pair<String, Int>>,
        personalFallback: List<Pair<String, Int>> = emptyList(),
        staticFallback: List<Pair<String, Int>> = emptyList(),
        personalTrigram: List<Pair<String, Int>> = emptyList(),
        staticTrigram: List<Pair<String, Int>> = emptyList(),
        limit: Int = 5,
    ): List<Prediction> {
        if (limit <= 0) return emptyList()
        val words = LinkedHashSet<String>()
        personal.forEach { words.add(it.first) }
        static.forEach { words.add(it.first) }
        personalFallback.forEach { words.add(it.first) }
        staticFallback.forEach { words.add(it.first) }
        personalTrigram.forEach { words.add(it.first) }
        staticTrigram.forEach { words.add(it.first) }
        if (words.isEmpty()) return emptyList()

        val pTotal = personal.sumOf { it.second }.coerceAtLeast(1)
        val sTotal = static.sumOf { it.second }.coerceAtLeast(1)
        val pfTotal = personalFallback.sumOf { it.second }.coerceAtLeast(1)
        val sfTotal = staticFallback.sumOf { it.second }.coerceAtLeast(1)
        val hasDirectRow = personal.isNotEmpty() || static.isNotEmpty()
        val fallbackWeight = if (hasDirectRow) 0.0 else LEMMA_DISCOUNT

        // v2.13.0 — the trigram layer is armed only when at least one
        // two-word row actually exists; otherwise every p_tri is 0.0 and
        // the final score is the bigram score, bit-for-bit as before.
        val ptTotal = personalTrigram.sumOf { it.second }.coerceAtLeast(1)
        val stTotal = staticTrigram.sumOf { it.second }.coerceAtLeast(1)
        val hasTrigramRow = personalTrigram.isNotEmpty() || staticTrigram.isNotEmpty()
        val triWeight = if (hasTrigramRow) TRI_WEIGHT else 0.0

        return words.map { word ->
            val p = personal.firstOrNull { it.first == word }?.second?.div(pTotal.toDouble()) ?: 0.0
            val s = static.firstOrNull { it.first == word }?.second?.div(sTotal.toDouble()) ?: 0.0
            var score = ALPHA * p + (1.0 - ALPHA) * s
            if (!hasDirectRow) {
                val pf = personalFallback.firstOrNull { it.first == word }?.second?.div(pfTotal.toDouble()) ?: 0.0
                val sf = staticFallback.firstOrNull { it.first == word }?.second?.div(sfTotal.toDouble()) ?: 0.0
                score = score * (1.0 - fallbackWeight) + LEMMA_DISCOUNT * (ALPHA * pf + (1.0 - ALPHA) * sf)
            }
            if (triWeight > 0.0) {
                val pt = personalTrigram.firstOrNull { it.first == word }?.second?.div(ptTotal.toDouble()) ?: 0.0
                val st = staticTrigram.firstOrNull { it.first == word }?.second?.div(stTotal.toDouble()) ?: 0.0
                val tri = TRI_PERSONAL_ALPHA * pt + (1.0 - TRI_PERSONAL_ALPHA) * st
                score = triWeight * tri + (1.0 - triWeight) * score
            }
            Prediction(word, score)
        }
            .sortedWith(compareByDescending<Prediction> { it.score }.thenBy { it.word })
            .take(limit)
    }
}
