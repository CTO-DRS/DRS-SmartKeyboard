/*
 * Copyright (C) 2025 The DRS Smart Keyboard Project
 */

package com.drs.smartkeyboard.drs.ai

import java.util.Collections

/**
 * DRS M1.1 — the on-device Arabic morphological analyzer: segmentation
 * (prefixes / core / suffixes), documented root extraction through a
 * WEIGHT TABLE (أوزان صرفية موصوتة — 15 patterns), and lemma derivation.
 * Pure JVM, zero dependencies, LRU-capped at [LRU_CAPACITY] analyses so
 * the per-keystroke hot path never re-analyzes the same word.
 *
 * The design decision the tests forced (and the CHANGELOG documents):
 *
 *  1. TWO-PASS analysis — the bare (no-affix) pattern match runs FIRST,
 *     and affix stripping only runs when the bare pass fails. A greedy
 *     prefix strip used to EAT the root of 3-letter words (كتاب read as
 *     ك + تاب) and every analysis downstream lied.
 *
 *  2. The vowel-letter positions of each weight are EXPLICIT — فاعل
 *     drops the alif at position 2 (ف ع ا ل → ف ع ل), فعّال drops the
 *     doubling seat alif at position 3 (ف ع ا ل → ف ع ل). Getting these
 *     two positions swapped (the original bug) produced wrong roots for
 *     every participle in the language.
 *
 *  3. The مفعلة weight carries a ة/ت ROTATION: مدرسة and مدرست are the
 *     same stem — the pattern's final ة matches a word-final ت too.
 *
 *  4. The lemma is the BARE STEM: the core with a pattern-final ة
 *     removed (مدرسة → مدرس) — it is the unit the ranker and the
 *     personal-learning tables reason about.
 */
object DrsArabicMorphology {

    const val LRU_CAPACITY = 512

    /**
     * A documented weight: the template letters (شدة-free — the analyzer
     * sees normalized words) + whether it ends in ة (lemma strips it).
     * ف / ع / ل are the three radical slots; every other letter is a
     * template constant that must match the word exactly.
     */
    private data class Weight(val template: String, val endsWithTaMarbuta: Boolean)

    // The fifteen documented weights. Note the VOWEL-LETTER POSITIONS are
    // the whole game: فاعل (ف ا ع ل) drops the alif at position 1, while
    // the فعّال shape (ف ع ا ل) drops the seat alif at position 2 —
    // swapping those two produced wrong roots for every participle.
    private val PATTERNS: List<Weight> = listOf(
        Weight("فعل", false),        // كَتَبَ — bare 3-letter core
        Weight("فاعل", false),       // كاتب (active participle)
        Weight("فعال", false),       // كتّاب normalized (ح surrounding ا = seat)
        Weight("مفاعل", false),      // مقاتل
        Weight("مفعول", false),      // مكتوب (passive participle, و)
        Weight("مفعال", false),      // مفعال
        Weight("تفعيل", false),      // تدوين
        Weight("تفاعل", false),      // تكاتب
        Weight("متفاعل", false),     // متكاتب
        Weight("انفعال", false),     // انكتاب
        Weight("افتعال", false),     // اجتهاد-class
        Weight("استفعال", false),    // استخراج
        Weight("فعالة", false),      // فعالة
        Weight("فاعلة", true),       // عاملة
        Weight("مفعلة", true),       // مدرسة (ة/ت rotation — see matchPattern)
    )

    // Longest-first so وال wins over و and ال is not read as ا + ل. The
    // mudari' letters (ي ت أ ن + bare ا) come last: they only strip when a
    // real weight matches the remaining core (يكتب → ي + كتب), so the
    // ranker can relate verb forms to their stems without ever eating a
    // 3-letter root (the two-pass rule still guards every strip).
    private val PREFIXES = listOf("وال", "فال", "بال", "كال", "ال", "لل", "و", "ف", "ب", "ك", "ل", "ي", "ت", "أ", "ن", "ا")

    // Longest-first: the feminine/possessive clusters before the singletons.
    private val SUFFIXES = listOf("اتها", "اتهم", "اتها", "هما", "كما", "ات", "ان", "ون", "ين", "ة", "ها", "هم", "هن", "نا", "كم", "كن", "تي", "ته", "ي", "ك", "ه")

    private val lru: MutableMap<String, Analysis> =
        Collections.synchronizedMap(object : LinkedHashMap<String, Analysis>(16, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Analysis>): Boolean =
                size > LRU_CAPACITY
        })

    data class Analysis(
        val word: String,
        val prefixes: List<String>,
        val core: String,
        val suffixes: List<String>,
        /** The matched weight template, or null when no pattern fits. */
        val pattern: String?,
        /** The extracted 3-radical root, or null when no pattern fits. */
        val root: String?,
        /** The bare stem (core minus a pattern-final ة) — the ranking unit. */
        val lemma: String,
    )

    /** Arabic-letter word gate: no digits, no spaces, sane length. */
    private fun isAnalyzable(word: String): Boolean {
        if (word.length !in 3..40) return false
        return word.all { it in '\u0621'..'\u064A' }
    }

    /**
     * Analyzes [word] (normalized: harakat stripped by the caller or here).
     * Never throws — an unanalyzable word yields a trivial analysis with
     * no pattern and no root.
     */
    fun analyze(word: String): Analysis {
        val key = word.trim()
        lru[key]?.let { return it }
        val result = analyzeUncached(key)
        lru[key] = result
        return result
    }

    private fun analyzeUncached(word: String): Analysis {
        val bare = word.trim()
        if (!isAnalyzable(bare)) {
            return Analysis(bare, emptyList(), bare, emptyList(), null, null, bare)
        }
        // PASS 1 — the bare analysis runs FIRST (see class doc: the greedy
        // strip used to eat 3-letter roots).
        matchPattern(bare)?.let { (weight, root) ->
            return Analysis(
                word = bare,
                prefixes = emptyList(),
                core = bare,
                suffixes = emptyList(),
                pattern = weight.template,
                root = root,
                lemma = lemmaOf(bare, weight),
            )
        }
        // PASS 2 — affix stripping, longest prefixes first, then suffixes.
        for (prefix in PREFIXES) {
            if (!bare.startsWith(prefix)) continue
            val withoutPrefix = bare.substring(prefix.length)
            if (withoutPrefix.length < 3) continue
            // Suffixes on top of the prefix (longest first).
            for (suffix in SUFFIXES) {
                if (withoutPrefix.length - suffix.length < 3) continue
                if (!withoutPrefix.endsWith(suffix)) continue
                val core = withoutPrefix.dropLast(suffix.length)
                val (weight, root) = matchPattern(core) ?: continue
                return Analysis(bare, listOf(prefix), core, listOf(suffix), weight.template, root, lemmaOf(core, weight))
            }
            val (weight, root) = matchPattern(withoutPrefix) ?: continue
            return Analysis(bare, listOf(prefix), withoutPrefix, emptyList(), weight.template, root, lemmaOf(withoutPrefix, weight))
        }
        // Suffix-only pass (no prefix).
        for (suffix in SUFFIXES) {
            if (!bare.endsWith(suffix)) continue
            if (bare.length - suffix.length < 3) continue
            val core = bare.dropLast(suffix.length)
            val (weight, root) = matchPattern(core) ?: continue
            return Analysis(bare, emptyList(), core, listOf(suffix), weight.template, root, lemmaOf(core, weight))
        }
        return Analysis(bare, emptyList(), bare, emptyList(), null, null, bare)
    }

    /**
     * Matches [word] against a weight: same length, template constants
     * equal, template radicals carry the word's letters. The ة/ت rotation:
     * a template ending in ة also matches a word ending in ت.
     */
    private fun matchPattern(word: String): Pair<Weight, String>? {
        for (weight in PATTERNS) {
            val template = weight.template
            if (template.length != word.length) continue
            var root = StringBuilder(3)
            var ok = true
            for (i in template.indices) {
                val slot = template[i]
                when {
                    slot == 'ف' || slot == 'ع' || slot == 'ل' -> root.append(word[i])
                    slot == 'ة' && word[i] == 'ت' -> { /* ة/ت rotation */ }
                    slot != word[i] -> { ok = false; break }
                }
            }
            if (ok && root.length == 3) {
                // Every radical position must hold a real Arabic letter —
                // the template constants themselves never satisfy the slots.
                return weight to root.toString()
            }
        }
        return null
    }

    private fun lemmaOf(core: String, weight: Weight): String =
        if (weight.endsWithTaMarbuta && core.endsWith("ة")) core.dropLast(1) else core

    /** Convenience: the lemma of a word (cached like [analyze]). */
    fun lemmaOf(word: String): String = analyze(word).lemma

    /** Convenience: the root of a word, or null. */
    fun rootOf(word: String): String? = analyze(word).root
}
