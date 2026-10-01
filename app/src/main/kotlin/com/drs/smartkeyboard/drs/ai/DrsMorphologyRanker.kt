/*
 * Copyright (C) 2025 The DRS Smart Keyboard Project
 */

package com.drs.smartkeyboard.drs.ai

/**
 * DRS M1.2 — re-ranks next-word/suggestion candidates by the morphology
 * of the word before the cursor. The boosts (documented contract):
 *
 *   +2 — the candidate IS the previous word's lemma (the stem itself:
 *        after «يكتبُ» the bare «كتب» ranks first);
 *   +1 — the candidate shares the previous word's lemma (same stem family:
 *        after «يكتب» both «كتابة» and «مكتب» rise together);
 *   +1 — the candidate shares the previous word's root.
 *
 * The ORIGINAL design carried a `root != word` guard meant to skip trivial
 * 3-letter words — the tests exposed it as the KILLER GUARD: it voided the
 * root bonus for exactly the short, frequent words the ranking exists for,
 * so it is gone. Bonuses now apply whenever roots match, root == word
 * included.
 *
 * The sort is STABLE: candidates with equal scores keep their input order,
 * so the ranker can never scramble the frequency work of the layers below.
 */
object DrsMorphologyRanker {

    const val BOOST_IS_LEMMA = 2
    const val BOOST_SAME_LEMMA = 1
    const val BOOST_SAME_ROOT = 1

    fun rerank(previousWord: String?, candidates: List<String>): List<String> {
        if (previousWord.isNullOrBlank() || candidates.size < 2) return candidates
        val prev = DrsArabicMorphology.analyze(previousWord)
        if (prev.root == null && prev.lemma == previousWord) return candidates

        data class Scored(val text: String, val index: Int, val score: Int)

        return candidates
            .mapIndexed { index, candidate ->
                val analysis = DrsArabicMorphology.analyze(candidate)
                var score = 0
                if (prev.lemma.isNotEmpty() && candidate == prev.lemma) {
                    score += BOOST_IS_LEMMA
                } else if (prev.lemma.isNotEmpty() &&
                    analysis.lemma.isNotEmpty() &&
                    analysis.lemma == prev.lemma
                ) {
                    score += BOOST_SAME_LEMMA
                }
                if (analysis.root != null && analysis.root == prev.root) {
                    score += BOOST_SAME_ROOT
                }
                Scored(candidate, index, score)
            }
            .sortedWith(compareByDescending<Scored> { it.score }.thenBy { it.index })
            .map { it.text }
    }
}
