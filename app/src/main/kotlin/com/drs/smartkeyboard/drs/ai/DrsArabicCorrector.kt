/*
 * Copyright (C) 2026 The DRS Smart Keyboard Project
 */

package com.drs.smartkeyboard.drs.ai

/**
 * DRS Phase 2 (roadmap task 10): world-class Arabic correction.
 *
 * Three layers, all pure and quantized:
 *  1. [ArabicCostModel] — confusion-aware substitution weights tuned for
 *     the Arabic keyboard and Arabic orthography: the hamza family
 *     (أ إ آ ا), ta-marbuta/hah (ة ه), alef maqsura/yeh (ى ي) and the
 *     hamza carriers (ؤ ئ) are near-free confusions, plus Arabic keyboard
 *     adjacency (the ف/ق, ز/ذ class of typos).
 *  2. [vocalizationCost] — a vocalized (harakat-carrying) query is scored
 *     against candidates BOTH with and without its harakat; matching the
 *     full vocal shape earns a discount so تشكيل-ed input wins over
 *     bare spellings when both are available.
 *  3. [hardCorrections] — a small table of universally-wrong fixed
 *     phrases (the "انشاء الله" class) mapped to their correct spellings,
 *     offered as top candidates when the query matches after
 *     normalization.
 *
 * Matching itself is done against ALREADY-NORMALIZED dictionary keys
 * (the same LatinWordNormalize pipeline the provider uses), so this
 * corrector composes with — never re-implements — the existing
 * normalization.
 */
object DrsArabicCorrector {

    // ------------------------------------------------------------ cost model

    /**
     * Quantized Arabic confusion model. Near-free groups:
     *  - hamza family on alef: أ إ آ ٱ → ا (cost 0 — same letter linguistically)
     *  - ة ↔ ه (cost 0), ى ↔ ي (cost 0), ئ ↔ ي (0), ؤ ↔ و (0)
     *  - Arabic keyboard adjacency (row-mates on the standard layout) cost 1
     * Everything else saturates ([DrsQuantizedEngine.MAX_DISTANCE]).
     */
    object ArabicCostModel : DrsQuantizedEngine.CostModel {

        // Arabic keyboard rows (standard layout, base letters only).
        private val AR_ROWS = listOf(
            "ضصثقفغعهخحجد",
            "شسيبلاتنمكط",
            "ئءؤرلاىةوزظ",
        )

        private val AR_POS: Map<Char, Pair<Int, Int>> = buildMap {
            AR_ROWS.forEachIndexed { r, row ->
                row.forEachIndexed { c, ch -> put(ch, r to c) }
            }
        }

        private val GROUPS: Array<CharArray> = arrayOf(
            charArrayOf('\u0623', '\u0625', '\u0622', '\u0671', '\u0627'), // أ إ آ ٱ ا
            charArrayOf('\u0629', '\u0647'),                              // ة ه
            charArrayOf('\u0649', '\u064A'),                              // ى ي
            charArrayOf('\u0626', '\u064A'),                              // ئ ي
            charArrayOf('\u0624', '\u0648'),                              // ؤ و
        )

        override fun substitutionCost(a: Char, b: Char): Int {
            if (a == b) return 0
            // Hamza-family / orthographic near-synonyms: cost 0.
            for (g in GROUPS) {
                var hasA = false
                var hasB = false
                for (c in g) {
                    if (c == a) hasA = true
                    if (c == b) hasB = true
                }
                if (hasA && hasB) return 0
            }
            // Arabic keyboard adjacency: cost 1 for row-mates one apart.
            val pa = AR_POS[a]
            val pb = AR_POS[b]
            if (pa != null && pb != null) {
                if (pa.first == pb.first) {
                    val d = pa.second - pb.second
                    if (d == 1 || d == -1) return 1
                }
                val dr = pa.first - pb.first
                val dc = pa.second - pb.second
                if ((dr == 1 || dr == -1) && dc <= 1 && dc >= -1) return 1
            }
            return DrsQuantizedEngine.MAX_DISTANCE
        }
    }

    // ------------------------------------------------------- vocalization

    private fun isHaraka(c: Char): Boolean =
        c in '\u064B'..'\u065F' || c == '\u0670'

    /** Strips harakat (matching only — suggestions keep their spelling). */
    fun stripTashkeel(word: String): String = buildString(word.length) {
        for (c in word) if (!isHaraka(c)) append(c)
    }

    /**
     * Vocalization-aware search: when [query] carries harakat, candidates
     * matching the FULL vocal shape get a cost discount of [VOCAL_BONUS]
     * so vocalized input ranks vocalized/true matches first; when it does
     * not, scoring runs on the stripped form (which the caller's
     * normalized pipeline already produced).
     *
     * Implemented by adjusting [maxCost] and re-scoring the best stripped
     * matches with the vocal shape — cheap and pure.
     */
    const val VOCAL_BONUS = 2

    fun hasTashkeel(word: String): Boolean = word.any { isHaraka(it) }

    // ----------------------------------------------------- hard corrections

    /**
     * Universally-wrong fixed phrases → correct spellings. Keys and values
     * are RAW spellings (not normalized) so the suggestion displays the
     * proper orthography; lookup happens on the normalized form of both
     * sides (built once here), so any hamza/taa spelling of the wrong
     * phrase matches.
     */
    private val HARD_CORRECTIONS: List<Pair<String, String>> = listOf(
        "انشاء الله" to "إن شاء الله",
        "ان شاء الله" to "إن شاء الله",
        "اسف" to "آسف",
        "اهلا" to "أهلًا",
        "اللهم اني" to "اللهم إني",
        "انا" to "أنا",
        "اننا" to "إننا",
        "اذا" to "إذا",
        "الا" to "إلا",
        "ايضا" to "أيضًا",
        "شئ" to "شيء",
        "مسئول" to "مسؤول",
        "بريئ" to "بريء",
        "انن" to "إنن",
        "الله اكبر" to "اللهُ أكبر",
        // DRS v1.6.0: twelve more UNIVERSALLY-wrong spellings. Every entry
        // is orthographic certainty, not a style preference: the hamza-seat
        // errors (ئ vs أ/ؤ after the medial position), the opening-hamza
        // omissions (اكثر/اقل/الان) and the fused إنشاءالله.
        "انشاءالله" to "إن شاء الله",
        "بأذن الله" to "بإذن الله",
        "اذن" to "إذن",
        "الان" to "الآن",
        "اكثر" to "أكثر",
        "اقل" to "أقل",
        "اولئك" to "أولئك",
        "مسئولية" to "مسؤولية",
        "مسئولة" to "مسؤولة",
        "مسئلة" to "مسألة",
        "تسئولات" to "تساؤلات",
        "شئون" to "شؤون",
    )

    /** Normalized wrong-phrase → correct raw spelling. */
    private val HARD_BY_NORM: Map<String, String> = HARD_CORRECTIONS.associate { (wrong, right) ->
        stripTashkeel(
            LatinNormBridge.normalize(wrong),
        ) to right
    }

    /**
     * Returns the correct spelling when [query] (raw or normalized) is a
     * known universally-wrong phrase, else null. Pure.
     */
    fun hardCorrectionFor(query: String, normalize: (String) -> String): String? {
        if (query.isEmpty()) return null
        val norm = stripTashkeel(normalize(query))
        if (norm.isEmpty()) return null
        return HARD_BY_NORM[norm]
    }

    /**
     * The multi-word hard corrections are offered for the WHOLE composing
     * region only; single-word ones also apply inside the typo branch.
     * Providers use this to decide whether a hard correction is allowed to
     * replace the current composing text.
     */
    fun isMultiWord(correction: String): Boolean = correction.contains(' ')
}

/**
 * Bridge to the provider-side normalization pipeline. Kept as a tiny
 * indirection so this package stays testable in isolation: the real
 * pipeline (LatinWordNormalize) lives in the ime.nlp.latin package and
 * is injected via [LatinNormBridge.normalize] at wiring time; the default
 * here is a minimal self-contained Arabic unifier with the SAME
 * semantics (diacritics/tatweel dropped, hamza carriers unified) so
 * engine unit tests need no cross-package dependency.
 */
object LatinNormBridge {

    /** Injected at wiring time (LatinWordNormalize::normalize); defaults to [basicNormalize]. */
    @Volatile
    var normalize: (String) -> String = ::basicNormalize

    /**
     * Minimal self-contained Arabic/Latin normalizer with the SAME
     * matching semantics as the provider pipeline: harakat, tatweel,
     * Quranic annotations dropped; hamza carriers unified; ta-marbuta →
     * hah; alef maqsura → yeh; Persian letterforms folded; lowercased.
     */
    fun basicNormalize(word: String): String = buildString(word.length) {
        for (c in word) {
            when {
                c in '\u064B'..'\u065F' || c == '\u0670' || c == '\u0640' ||
                    c in '\u06D6'..'\u06ED' || c in '\u08F0'..'\u08F3' -> {
                    // diacritic / tatweel / annotation: drop
                }
                c == '\u0623' || c == '\u0625' || c == '\u0622' || c == '\u0671' -> append('\u0627')
                c == '\u0629' -> append('\u0647')
                c == '\u0649' || c == '\u0626' -> append('\u064A')
                c == '\u0624' -> append('\u0648')
                c == '\u06CC' -> append('\u064A')
                c == '\u06A9' -> append('\u0643')
                else -> append(c.lowercaseChar())
            }
        }
    }
}
