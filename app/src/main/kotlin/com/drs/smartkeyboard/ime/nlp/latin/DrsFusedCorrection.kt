/*
 * Copyright (C) 2026 The DRS Smart Keyboard Project
 */

package com.drs.smartkeyboard.ime.nlp.latin

import com.drs.smartkeyboard.drs.ai.DrsArabicCorrector
import com.drs.smartkeyboard.drs.ai.DrsQuantizedEngine
import com.drs.smartkeyboard.drs.ai.QwertyCostModel

/**
 * DRS v2.12.0 «تعميق محركات المرحلة 1» — the fused correction pipeline,
 * extracted PURE from LatinLanguageProvider so the real-errors benchmark
 * (DrsV21200RealErrorsBenchmark) measures THE SAME code the provider runs,
 * never a test-side copy.
 *
 * The pipeline (in trust order):
 *  1. Universal hard corrections (the «انشاء الله» class) — direct hit.
 *  2. NEW: hard corrections through AFFIX SPLITTING — a prefixed typo like
 *     «والمسئول» splits into وال + مسئول, the stem hits the universal
 *     table, and the correction re-attaches the prefix (والمسؤول).
 *  3. The quantized weighted Damerau-Levenshtein search — the direct scan
 *     AND, NEW, stem scans after affix splitting, POOLED and ranked by
 *     quantized cost then frequency (a zero-cost stem match like
 *     والجامعه → وال+جامعة outranks a cost-3 direct match).
 *  4. The legacy delete-1 index as backstop.
 *
 * Affix splitting is bounded and conservative: only the documented Arabic
 * clitics (وال فال بال كال لل ال و ف ب ل ك), stem length ≥ 3, at most
 * [MAX_AFFIX_VARIANTS] variants, at most [MAX_STEM_SEARCHES] quantized
 * stem scans, single-word queries only. Every composed form whose text
 * equals what the user typed is dropped — suggesting your own spelling
 * is not a correction.
 *
 * PURE: no Android types, no I/O, no global state. The provider owns the
 * dictionary and the candidate wrapping; this object only computes.
 */
object DrsFusedCorrection {

    /** Minimum query length for the correction scans (parity with the provider). */
    private const val CORRECTION_MIN_LENGTH = 3

    /** Minimum normalized length before affix splitting is even considered. */
    private const val AFFIX_MIN_LENGTH = 5

    /** Total affix variants considered (the no-split form + prefixed stems). */
    private const val MAX_AFFIX_VARIANTS = 4

    /** How many prefixed stems get the (heavier) quantized stem scan. */
    private const val MAX_STEM_SEARCHES = 2

    /** The Arabic clitics, longest first — وال wins over و, ال is not ا + ل. */
    private val AR_AFFIX_PREFIXES = listOf("وال", "فال", "بال", "كال", "لل", "ال", "و", "ف", "ب", "ل", "ك")

    /** A correction candidate: displayed text + its suggestion confidence. */
    data class Correction(val text: String, val confidence: Double)

    /**
     * The normalized (prefix, stem) variants of [norm], no-split first, then
     * matching clitics longest-first, capped at [MAX_AFFIX_VARIANTS]; a stem
     * must keep at least [CORRECTION_MIN_LENGTH] letters.
     */
    internal fun affixVariants(norm: String): List<Pair<String, String>> {
        val out = ArrayList<Pair<String, String>>(MAX_AFFIX_VARIANTS)
        out.add("" to norm)
        if (norm.length >= AFFIX_MIN_LENGTH) {
            for (p in AR_AFFIX_PREFIXES) {
                if (out.size >= MAX_AFFIX_VARIANTS) break
                if (norm.startsWith(p) && norm.length - p.length >= CORRECTION_MIN_LENGTH) {
                    out.add(p to norm.substring(p.length))
                }
            }
        }
        return out
    }

    /** One internal pooled hit: quantized cost + frequency + display form. */
    private data class Hit(
        val cost: Int,
        val freq: Int,
        val text: String,
        val norm: String,
        /** Personal-learning boost — computed on the STEM word, not the composed form. */
        val boost: Int,
    )

    /**
     * Runs the fused pipeline for [raw] over [index]. [normalize] is the
     * provider's normalization (one tested path); [learningBoost] feeds the
     * personal-learning frequency bonus (defaults to none for pure tests).
     * Never throws.
     */
    internal fun corrections(
        index: DictIndex,
        raw: String,
        isArabic: Boolean,
        maxCost: Int,
        lengthBand: Int,
        maxCandidateCount: Int,
        normalize: (String) -> String,
        learningBoost: (String) -> Int = { 0 },
    ): List<Correction> {
        if (maxCandidateCount <= 0 || raw.isEmpty()) return emptyList()
        val prefix = runCatching { normalize(raw) }.getOrDefault(raw)
        val out = LinkedHashMap<String, Correction>()

        // 1) Direct hard corrections — the trust ceiling (0.95).
        runCatching { DrsArabicCorrector.hardCorrectionFor(raw, normalize) }.getOrNull()?.let { correct ->
            if (correct != raw) out[correct] = Correction(correct, 0.95)
        }

        // 2) Hard corrections through affix splitting (single-word stems only —
        //    a multi-word correction under a clitic is linguistically nonsense).
        if (isArabic && prefix.length >= AFFIX_MIN_LENGTH && !raw.contains(' ')) {
            for ((p, stem) in affixVariants(prefix)) {
                if (p.isEmpty()) continue
                val stemFix = runCatching { DrsArabicCorrector.hardCorrectionFor(stem, normalize) }
                    .getOrNull() ?: continue
                if (stemFix.contains(' ')) continue
                val composed = p + stemFix
                if (composed != raw && composed !in out) out[composed] = Correction(composed, 0.94)
            }
        }

        // 3) The quantized search: direct scan + stem scans in ONE cost-ranked
        //    pool (v2.12.0 — a zero-cost stem match outranks a cost-3 direct one).
        if (prefix.length >= CORRECTION_MIN_LENGTH) {
            val costModel = if (isArabic) DrsArabicCorrector.ArabicCostModel else QwertyCostModel
            val pool = ArrayList<Hit>(64)

            runCatching {
                DrsQuantizedEngine.search(
                    query = prefix,
                    words = index.entries.asSequence().map { it.norm to it.freq },
                    costs = costModel,
                    limit = maxCandidateCount * 3,
                    maxCost = maxCost,
                    lengthBand = lengthBand,
                )
            }.getOrDefault(emptyList()).forEach { match ->
                val entry = index.byNorm[match.word] ?: return@forEach
                if (entry.word == raw) return@forEach
                pool.add(Hit(match.cost, entry.freq, entry.word, entry.norm, learningBoost(entry.word)))
            }

            if (isArabic && prefix.length >= AFFIX_MIN_LENGTH && !raw.contains(' ')) {
                var stems = 0
                for ((p, stem) in affixVariants(prefix)) {
                    if (p.isEmpty()) continue
                    if (stems >= MAX_STEM_SEARCHES) break
                    stems++
                    runCatching {
                        DrsQuantizedEngine.search(
                            query = stem,
                            words = index.entries.asSequence().map { it.norm to it.freq },
                            costs = costModel,
                            limit = maxCandidateCount,
                            maxCost = maxCost,
                            lengthBand = lengthBand,
                        )
                    }.getOrDefault(emptyList()).forEach { match ->
                        val entry = index.byNorm[match.word] ?: return@forEach
                        val composed = p + entry.word
                        if (composed == raw) return@forEach
                        pool.add(Hit(match.cost, entry.freq, composed, match.word, learningBoost(entry.word)))
                    }
                }
            }

            // Rank the pool by cost ascending, frequency (+ personal learning)
            // descending; dedupe by TEXT first (a hard layer may already hold
            // the same spelling) then by NORM (direct and stem scans overlap).
            val sorted = pool.sortedWith(
                compareBy({ it.cost }, { -(it.freq + it.boost / 4) }, { it.text }),
            )
            val normsSeen = HashSet<String>(out.size * 2)
            for (c in out.values) normsSeen.add(normalize(c.text))
            for (hit in sorted) {
                if (hit.text in out) continue
                if (!normsSeen.add(hit.norm)) continue
                // The provider's exact-match confidence semantics, preserved:
                // corpus frequency (boosted) + a 0.05 bonus for a zero-cost match.
                val boosted = (hit.freq + hit.boost / 4).coerceAtMost(255)
                val confidence = ((boosted / 255.0) + if (hit.cost == 0) 0.05 else 0.0)
                    .coerceIn(0.0, 1.0)
                out[hit.text] = Correction(hit.text, confidence)
            }
        }

        // 4) Legacy delete-1 backstop (cheap net for very short words).
        if (out.size < maxCandidateCount) {
            runCatching { index.corrections(prefix, maxCandidateCount) }.getOrDefault(emptyList())
                .forEach { entry ->
                    if (entry.word !in out) {
                        out[entry.word] = Correction(entry.word, entry.freq / 255.0)
                    }
                }
        }
        return out.values.take(maxCandidateCount)
    }
}
