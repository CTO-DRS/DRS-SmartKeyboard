/*
 * Copyright (C) 2025-2026 The DRS Smart Keyboard Project
 */

package com.drs.smartkeyboard.ime.nlp

/**
 * DRS v2.7.0 «مزايا البحث الخارجي» — multilingual candidate merging.
 *
 * Researched from HeliBoard's multilingual typing: while typing with one
 * subtype active, the dictionaries of the user's OTHER enabled subtypes
 * also feed the candidate row, so a mixed sentence like «مرحبا hello»
 * never forces a manual language switch.
 *
 * The merge is a deterministic round-robin: the active subtype's own
 * candidate always comes first, then one candidate from each extra
 * language in turn, cycling until every source is exhausted. Duplicates
 * are removed by the caller-chosen key (for candidates, the word text
 * compared case-insensitively), and the row is capped.
 *
 * Pure and generic so it can be pinned with plain strings in tests — no
 * Android, no dictionaries, no scheduling surprises.
 */
object DrsMultilingualMerge {

    /** Hard ceiling of the merged candidate row. */
    const val MAX_MERGED_CANDIDATES: Int = 10

    /**
     * Hard ceiling of extra languages queried per keystroke. Two keeps the
     * merge visible and the dictionary lookups bounded; the active
     * language's own candidates always outrank the extras.
     */
    const val MAX_EXTRA_SUBTYPES: Int = 2

    /**
     * Interleaves [base] with each list in [extras] round-robin, removes
     * duplicates by [dedupKey] (first occurrence wins, base outranks
     * extras), and caps the result at [cap] entries. Base order inside
     * [base] and inside each extras list is preserved exactly.
     */
    fun <T> interleave(
        base: List<T>,
        extras: List<List<T>>,
        dedupKey: (T) -> String,
        cap: Int = MAX_MERGED_CANDIDATES,
    ): List<T> {
        if (cap <= 0) return emptyList()
        val seen = HashSet<String>()
        val out = ArrayList<T>(cap.coerceAtMost(base.size + extras.sumOf { it.size }))
        var index = 0
        while (out.size < cap) {
            var advanced = false
            // slot 0 is the base row; slots 1..n are the extras in order
            for (slot in 0..extras.size) {
                val source = if (slot == 0) base else extras[slot - 1]
                if (index < source.size) {
                    val item = source[index]
                    val key = dedupKey(item)
                    if (seen.add(key)) {
                        out.add(item)
                        if (out.size == cap) break
                    }
                    advanced = true
                }
            }
            if (!advanced) break
            index++
        }
        return out
    }
}
