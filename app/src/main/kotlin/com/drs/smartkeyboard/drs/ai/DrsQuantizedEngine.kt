/*
 * Copyright (C) 2026 The DRS Smart Keyboard Project
 */

package com.drs.smartkeyboard.drs.ai

/**
 * DRS Phase 2 (roadmap task 8): the on-device quantized suggestion engine.
 *
 * A self-contained, dependency-free correction engine that upgrades the
 * previous "edit distance <= 1" fallback into a weighted Damerau-
 * Levenshtein search with:
 *  - QUANTIZED integer scoring: every cost is a small Int (1..16), no
 *    floating point anywhere on the hot path. This keeps the arithmetic
 *    exact, allocation-free and fast on low-end cores (mirrors int8
 *    quantization semantics of ML runtimes without pulling any runtime).
 *  - CONFUSION-AWARE costs: substitution weights are supplied by a
 *    pluggable [DrsQuantizedEngine.CostModel] (the Arabic corrector
 *    supplies Arabic-specific weights, [QwertyCostModel] supplies Latin
 *    keyboard-adjacency ones).
 *  - BEAM cutoff: rows whose every cell already saturates the budget end
 *    the distance computation early, and the dictionary scan skips words
 *    whose length band cannot reach the budget — a full dictionary scan
 *    stays cheap enough for per-keystroke use on low-end devices.
 *
 * The engine is PURE: no Android types, no I/O, no global state. The
 * provider owns dictionaries; the engine only computes.
 */
object DrsQuantizedEngine {

    /** Maximum supported correction cost (quantized cost budget). */
    const val MAX_DISTANCE = 16

    /**
     * Cost model for the quantized edit operations. All costs are small
     * integers (0 = free — reserved for IDENTICAL characters; 1..16 =
     * progressively worse).
     */
    interface CostModel {
        /**
         * Quantized cost of replacing [a] with [b]. Implementations MUST
         * return [MAX_DISTANCE] (or a value that saturates the budget) for
         * unrelated pairs, and small values for confusable pairs.
         */
        fun substitutionCost(a: Char, b: Char): Int

        /** Quantized insertion cost (defaults to 3). */
        fun insertionCost(): Int = 3

        /** Quantized deletion cost (defaults to 3). */
        fun deletionCost(): Int = 3

        /** Quantized transposition cost (defaults to 2 — adjacent typos are common). */
        fun transpositionCost(): Int = 2
    }

    /**
     * A candidate match produced by [search]. [cost] is the quantized edit
     * cost (0 means exact match; lower is better).
     */
    data class Match(val word: String, val cost: Int)

    /**
     * Default neutral cost model: every substitution saturates unless the
     * characters are equal (cost 0). Used when no better model is supplied.
     */
    val NEUTRAL: CostModel = object : CostModel {
        override fun substitutionCost(a: Char, b: Char): Int =
            if (a == b) 0 else MAX_DISTANCE
    }

    /**
     * Computes the quantized weighted Damerau-Levenshtein distance between
     * [a] and [b] under [costs], saturating at [MAX_DISTANCE]. Pure Int
     * arithmetic; three rolling rows, no other allocation.
     */
    fun distance(a: String, b: String, costs: CostModel = NEUTRAL): Int {
        if (a == b) return 0
        val n = a.length
        val m = b.length
        if (n == 0) return (m * costs.insertionCost()).coerceAtMost(MAX_DISTANCE)
        if (m == 0) return (n * costs.deletionCost()).coerceAtMost(MAX_DISTANCE)
        // Saturation guard: the length difference is a lower bound on the
        // insertion/deletion budget; bail early when even that exceeds it.
        val insC = costs.insertionCost()
        val delC = costs.deletionCost()
        val lenDelta = if (n > m) n - m else m - n
        if (lenDelta * delC.coerceAtLeast(insC) >= MAX_DISTANCE) return MAX_DISTANCE

        var prev2 = IntArray(m + 1) // row i-2 (for transpositions)
        var prev = IntArray(m + 1)  // row i-1
        var curr = IntArray(m + 1)  // row i

        for (j in 0..m) prev[j] = (j * insC).coerceAtMost(MAX_DISTANCE)

        for (i in 1..n) {
            curr[0] = (i * delC).coerceAtMost(MAX_DISTANCE)
            val ca = a[i - 1]
            var rowMin = curr[0]
            for (j in 1..m) {
                val cb = b[j - 1]
                val sub = prev[j - 1] + if (ca == cb) 0 else costs.substitutionCost(ca, cb)
                val del = prev[j] + delC
                val ins = curr[j - 1] + insC
                var best = sub
                if (del < best) best = del
                if (ins < best) best = ins
                if (i > 1 && j > 1) {
                    val pa = a[i - 2]
                    val pb = b[j - 2]
                    if (ca == pb && pa == cb) {
                        val trans = prev2[j - 2] + costs.transpositionCost()
                        if (trans < best) best = trans
                    }
                }
                curr[j] = best.coerceAtMost(MAX_DISTANCE)
                if (best < rowMin) rowMin = best
            }
            // Beam cutoff: when every cell of this row already saturates the
            // budget, the final distance can only be worse — stop early.
            if (rowMin >= MAX_DISTANCE) return MAX_DISTANCE
            val tmp = prev2
            prev2 = prev
            prev = curr
            curr = tmp
        }
        return prev[m]
    }

    /**
     * Searches [words] — a sequence of (word, frequency) pairs — for the
     * best matches to [query] within the quantized budget, returning up to
     * [limit] matches ordered by cost ascending, then frequency descending
     * (ties break toward more frequent words). [lengthBand] is a pure
     * preprocessing cut (length delta is a lower bound on edit cost).
     */
    fun search(
        query: String,
        words: Sequence<Pair<String, Int>>,
        costs: CostModel,
        limit: Int,
        maxCost: Int = MAX_DISTANCE,
        lengthBand: Int = 4,
    ): List<Match> {
        if (limit <= 0 || query.isEmpty()) return emptyList()
        val qLen = query.length
        val found = ArrayList<Match>(limit * 4)
        val freqOf = HashMap<String, Int>()

        for ((word, freq) in words) {
            freqOf[word] = freq
            val band = word.length - qLen
            if (band > lengthBand || -band > lengthBand) continue
            val cost = distance(query, word, costs)
            // The MAX_DISTANCE value is the SATURATION SENTINEL ("nothing in
            // budget can say how far") — never a real match. Every tier
            // budget is strictly below it (see DrsAiPowerManager), so real
            // matches always cost < MAX_DISTANCE.
            if (cost < MAX_DISTANCE && cost <= maxCost) found.add(Match(word, cost))
        }

        // Order by cost ascending, then frequency descending; dedupe.
        found.sortWith(compareBy({ it.cost }, { -(freqOf[it.word] ?: 0) }))
        val seen = HashSet<String>(limit * 2)
        val out = ArrayList<Match>(limit)
        for (m in found) {
            if (seen.add(m.word)) {
                out.add(m)
                if (out.size >= limit) break
            }
        }
        return out
    }
}

/**
 * DRS Phase 2 (roadmap task 8): quantized QWERTY-adjacency cost model for
 * Latin-script corrections. Confusions between physically adjacent keys
 * (q/w, a/s, z/x, …) cost 1, same-row two-apart pairs cost 2, vertical
 * neighbors cost 2, everything else saturates. Combined with the engine's
 * budget this produces classic "fat finger" tolerance.
 */
object QwertyCostModel : DrsQuantizedEngine.CostModel {

    private val ROWS = listOf(
        "qwertyuiop",
        "asdfghjkl",
        "zxcvbnm",
    )

    private val POS: Map<Char, Pair<Int, Int>> = buildMap {
        ROWS.forEachIndexed { r, row ->
            row.forEachIndexed { c, ch -> put(ch, r to c) }
        }
    }

    override fun substitutionCost(a: Char, b: Char): Int {
        if (a == b) return 0
        val la = a.lowercaseChar()
        val lb = b.lowercaseChar()
        if (la == lb) return 0
        val pa = POS[la] ?: return DrsQuantizedEngine.MAX_DISTANCE
        val pb = POS[lb] ?: return DrsQuantizedEngine.MAX_DISTANCE
        if (pa.first == pb.first) {
            val d = pa.second - pb.second
            return when {
                d == 1 || d == -1 -> 1
                d == 2 || d == -2 -> 2
                else -> DrsQuantizedEngine.MAX_DISTANCE
            }
        }
        // Vertical neighbors (adjacent rows, near columns) cost 2.
        val dr = pa.first - pb.first
        val dc = pa.second - pb.second
        if ((dr == 1 || dr == -1) && dc <= 1 && dc >= -1) return 2
        return DrsQuantizedEngine.MAX_DISTANCE
    }
}
