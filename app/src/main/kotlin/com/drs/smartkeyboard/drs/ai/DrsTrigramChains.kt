/*
 * Copyright (C) 2026 The DRS Smart Keyboard Project
 */

package com.drs.smartkeyboard.drs.ai

/**
 * DRS v2.13.0 «السياق الأعمق» — جدول السلاسل الثلاثية الثابتة (the static
 * trigram-chain table) — the loader and the strict contract parser of the
 * asset `assets/drs/trigram_chains.txt`.
 *
 * The file maps a TWO-WORD Arabic context (the words before the cursor,
 * in order) to the continuations that carry REAL contextual certainty —
 * the fixed expressions and function chains of the language («إن شاء
 * الله»، «بسم الله الرحمن»، «على الرغم من»، «السلام عليكم ورحمة») —
 * the exact class where a bigram-only predictor cannot see the tie
 * between the two words and the continuation.
 *
 * عقد الملف (enforced by [parse], a violation is REJECTED, never patched):
 *  1. every data line is `p2<TAB>p1<TAB>next<TAB>strength` (exactly three
 *     tabs, four non-empty fields);
 *  2. no word carries whitespace or a digit, and no word exceeds 40
 *     characters;
 *  3. [strength] is an integer 5..9 — everything weaker is stylistic
 *     preference, NOT contextual certainty, and has no place here (the
 *     same يقين doctrine as the v2.12.0 real-errors reference);
 *  4. the lookup keys are the NORMALIZED forms — produced by the
 *     CALLER-INJECTED normalizer, the exact same function the provider
 *     uses for its bigram keys ([LatinWordNormalize.normalize]: alef
 *     forms unified, ى→ي, ة→ه, harakat stripped, lowercase) — while the
 *     NEXT word is kept in its natural display form: the suggestion the
 *     user sees is always correct written Arabic;
 *  5. a repeated normalized (p2, p1, next) key keeps its FIRST row
 *     («البذرة تفوز» — the hand-reviewed seed order wins).
 *
 * The parsed table is immutable — no global mutable state, no locks; the
 * provider caches it exactly like the bigram tables, and a missing/invalid
 * asset degrades to an empty table (the trigram layer is an honest no-op,
 * never a crash). The normalizer is INJECTED (the DrsFusedCorrection
 * pattern): one function serves both the file's parse-time keys and the
 * provider's lookup-time keys, so the two can never drift apart.
 */
object DrsTrigramChains {

    /** The shared empty table — the honest no-op for languages without chains. */
    val EMPTY: Table = Table(emptyMap())

    /** The weakest strength admitted by the contract (5..9 — يقين only). */
    const val MIN_STRENGTH = 5

    /** The strongest strength admitted by the contract (near-certain tail). */
    const val MAX_STRENGTH = 9

    /** One curated continuation: natural display word + its strength. */
    data class Chain(val next: String, val strength: Int)

    /**
     * The outcome of one parse: the accepted immutable table and the
     * reject count. The asset ships with zero rejections — pinned by
     * tests.
     */
    data class ParseResult(val table: Table, val rejected: Int)

    /**
     * An immutable chain table. Keys are the NORMALIZED two-word context
     * (p2, p1); each row is the continuation list sorted by strength
     * descending, insertion order breaking ties — fully deterministic.
     */
    class Table(private val rows: Map<Pair<String, String>, List<Chain>>) {

        /** The number of populated two-word rows. */
        val rowCount: Int get() = rows.size

        /**
         * The continuations for a normalized two-word context, strongest
         * first. An unknown context returns an empty list — the honest
         * no-op the predictor's backward compatibility depends on.
         */
        fun rowFor(p2: String, p1: String): List<Chain> =
            rows[pairKey(p2, p1)] ?: emptyList()

        internal fun rowMap(): Map<Pair<String, String>, List<Chain>> = rows
    }

    /** The normalized (p2, p1) row key — shared by the parser and tests. */
    internal fun pairKey(p2: String, p1: String): Pair<String, String> = p2 to p1

    /**
     * The strict parser. Comment lines start with '#', empty lines are
     * skipped, everything else must satisfy the full file contract —
     * any violation counts as one rejection and is dropped (the file
     * itself ships with zero rejections, pinned by tests).
     *
     * @param normalize the SAME normalizer the caller's lookups will use
     *   (the provider passes its bigram-key normalizer; passing a different
     *   function at lookup time than at parse time is a caller bug the
     *   contract refuses to forgive — the benchmark pins the real one).
     */
    fun parse(lines: List<String>, normalize: (String) -> String): ParseResult {
        var rejected = 0
        val seen = HashSet<Triple<String, String, String>>()
        val rows = LinkedHashMap<Pair<String, String>, MutableList<Chain>>()
        for (raw in lines) {
            val line = raw.trim()
            if (line.isEmpty() || line.startsWith("#")) continue
            val fields = line.split('\t')
            if (fields.size != 4) {
                rejected++
                continue
            }
            val p2 = fields[0].trim()
            val p1 = fields[1].trim()
            val next = fields[2].trim()
            val strength = fields[3].trim().toIntOrNull()
            if (p2.isEmpty() || p1.isEmpty() || next.isEmpty() || strength == null ||
                strength !in MIN_STRENGTH..MAX_STRENGTH ||
                !isValidWord(p2) || !isValidWord(p1) || !isValidWord(next)
            ) {
                rejected++
                continue
            }
            // «البذرة تفوز» — the first occurrence of a normalized chain wins.
            val dedupKey = Triple(normalize(p2), normalize(p1), normalize(next))
            if (!seen.add(dedupKey)) {
                rejected++
                continue
            }
            val key = pairKey(dedupKey.first, dedupKey.second)
            rows.getOrPut(key) { mutableListOf() }.add(Chain(next, strength))
        }
        val sorted = LinkedHashMap<Pair<String, String>, List<Chain>>()
        for ((key, chains) in rows) {
            // Strength descending; the insertion order below breaks ties —
            // the list was built in seed order, so stable sort is enough.
            sorted[key] = chains.sortedByDescending { it.strength }
        }
        return ParseResult(Table(sorted), rejected)
    }

    /** The word contract: letters (and Arabic marks) only, 1..40 chars, no digits, no whitespace. */
    private fun isValidWord(word: String): Boolean {
        if (word.length !in 1..40) return false
        if (word.any { it.isWhitespace() || it.isDigit() }) return false
        return word.all { it.isLetter() || it in '\u064B'..'\u065F' || it == '\u0670' }
    }
}
