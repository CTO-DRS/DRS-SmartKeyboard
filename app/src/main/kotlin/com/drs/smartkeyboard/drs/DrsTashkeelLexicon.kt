/*
 * Copyright (C) 2025-2026 The DRS Smart Keyboard Project
 */

package com.drs.smartkeyboard.drs

/**
 * DRS v1.2.0 — قاموس التشكيل الموسع (the 3000-word vocalization
 * lexicon) — the loader and the strict contract parser of the asset
 * `assets/drs/tashkeel_lexicon.txt`.
 *
 * The file maps the STRIPPED form of 3000 frequent Arabic words to
 * their canonical vocalization (standard MSA, pause-friendly). The
 * doctrine holds: a local, deterministic lookup — no cloud, no AI, no
 * invented diacritics. An unknown word returns null and the UI keeps
 * the action disabled (honest by design, exactly like the seed).
 *
 * عقد الملف (enforced by [parse], a violation is REJECTED, never
 * patched):
 *  1. every data line is `stripped<TAB>vocalized` (one tab);
 *  2. `strip(vocalized) == stripped` — the key IS the stripped form;
 *  3. the only combining marks allowed are the nine canonical harakat
 *     of [DrsHarakat.MARKS] (plus the tatweel as a stretch, never a
 *     mark);
 *  4. a repeated key keeps its FIRST form («البذرة تفوز» — the
 *     hand-reviewed seed order wins over any later occurrence).
 *
 * The parsed map is installed through an atomic reference write and
 * read without locks ([install]/[vocalize]) — the loader runs on a
 * background thread at process start, and until it lands the seed
 * lexicon of [DrsWordTashkeel] serves alone.
 */
object DrsTashkeelLexicon {

    /** The outcome of one parse: the accepted map and the reject count. */
    data class ParseResult(val entries: Map<String, String>, val rejected: Int)

    /**
     * The strict parser. Comment lines start with '#', empty lines are
     * skipped, everything else must satisfy the full file contract —
     * any violation counts as one rejection and is dropped (the file
     * itself ships with zero rejections, pinned by tests).
     */
    fun parse(lines: List<String>): ParseResult {
        var rejected = 0
        val entries = LinkedHashMap<String, String>()
        for (raw in lines) {
            val line = raw.trim()
            if (line.isEmpty() || line.startsWith("#")) continue
            val tab = line.indexOf('\t')
            val key = if (tab < 0) "" else line.take(tab).trim()
            val vocalized = if (tab < 0) "" else line.drop(tab + 1).trim()
            if (key.isEmpty() || vocalized.isEmpty() || !isValidEntry(key, vocalized)) {
                rejected++
                continue
            }
            // «البذرة تفوز» — the first occurrence of a key wins.
            if (entries.containsKey(key)) {
                rejected++
                continue
            }
            entries[key] = vocalized
        }
        return ParseResult(entries, rejected)
    }

    /**
     * The entry contract: no whitespace inside either side, every
     * character of the vocalized form is either one of the nine
     * canonical harakat, the tatweel (a stretch, never a mark), or a
     * letter that is NOT a combining mark of any other kind (the
     * madda/hamza-above family is rejected here) — and stripping the
     * vocalized form must reproduce the key exactly.
     */
    private fun isValidEntry(key: String, vocalized: String): Boolean {
        if (key.any { it.isWhitespace() } || vocalized.any { it.isWhitespace() }) return false
        var hasMark = false
        for (c in vocalized) {
            when {
                c in markSet -> hasMark = true
                c == DrsHarakat.TATWEEL -> {}
                c.isLetter() -> {
                    val type = Character.getType(c)
                    if (type == Character.NON_SPACING_MARK.toInt() ||
                        type == Character.COMBINING_SPACING_MARK.toInt()
                    ) {
                        // A combining mark OUTSIDE the nine — rejected.
                        return false
                    }
                }
                else -> return false
            }
        }
        if (!hasMark) return false
        return DrsHarakatWordOps.stripDiacritics(vocalized) == key
    }

    private val markSet = DrsHarakat.MARKS.toHashSet()

    /** The installed map — written once, read everywhere, never locked. */
    @Volatile
    private var installed: Map<String, String> = emptyMap()

    /**
     * Atomically swaps the loaded map in. A plain @Volatile write: the
     * loader thread publishes, every reader sees either the previous
     * complete map or the new complete map — never a mixture.
     */
    fun install(entries: Map<String, String>) {
        installed = entries
    }

    /** The vocalized form of a STRIPPED word, or null when unknown. */
    fun vocalize(stripped: String): String? = installed[stripped]

    /** True once the asset lexicon has landed (diagnostics + tests). */
    val isLoaded: Boolean get() = installed.isNotEmpty()

    /** The installed size — surfaced in tests and diagnostics. */
    val size: Int get() = installed.size
}
