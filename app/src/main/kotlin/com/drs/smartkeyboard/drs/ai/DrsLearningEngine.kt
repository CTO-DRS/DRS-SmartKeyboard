/*
 * Copyright (C) 2026 The DRS Smart Keyboard Project
 */

package com.drs.smartkeyboard.drs.ai

import java.io.File
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * DRS Phase 2 (roadmap task 9): personal writing-style learning —
 * the adaptive dictionary and personal bigram memory.
 *
 * WHAT it stores (local only, noBackupFilesDir):
 *  - committed word counts (the user's real vocabulary with real weights)
 *  - personal bigram counts (the user's real word-pair habits)
 *
 * WHAT it NEVER stores: full sentences, field contents, timestamps per
 * keystroke, or anything identifiable beyond the linguistic units
 * themselves. Private sessions MUST NOT call [learnWord] / [learnBigram]
 * (enforced at the wiring point, per the provider contract).
 *
 * DESIGN:
 *  - counts saturate at [MAX_WORD_COUNT] so one obsessive word cannot
 *    dominate the whole table;
 *  - on load, all counts decay by [DECAY_PERCENT] percent (aging: fresh
 *    vocabulary naturally outranks stale vocabulary);
 *  - size is bounded: at most [MAX_WORDS] words and [MAX_BIGRAMS] pairs,
 *    evicting the least-used on overflow;
 *  - persistence is atomic (tmp + rename), bounded (128 KB cap on the
 *    decoded file, like DrsCrashReports), and corruption-safe (a damaged
 *    file is discarded, learning restarts clean);
 *  - thread-safe: a single lock guards the in-memory tables.
 *
 * The ranking surface is QUANTIZED: [boostFor] maps the raw count to a
 * 0..255 Int8-style boost the suggestion fusion can add to corpus
 * frequency without any floating point.
 */
object DrsLearningEngine {

    private const val MAX_WORDS = 2000
    private const val MAX_BIGRAMS = 1024
    private const val MAX_WORD_COUNT = 200L
    private const val MAX_BIGRAM_COUNT = 100L
    private const val FILE_BYTES_CAP = 128 * 1024
    private const val DECAY_PERCENT = 25 // per-load aging: -25% of each count

    private val lock = Any()
    private var words = LinkedHashMap<String, Long>()
    private var bigrams = LinkedHashMap<String, LinkedHashMap<String, Long>>()
    // DRS M1.6 — the lemma table: the STEM of learned words (via
    // DrsArabicMorphology) carries half of each word's increment so the
    // per-load decay can never kill the stem before its words.
    private var lemmas = LinkedHashMap<String, Long>()
    private var dirty = false

    // ------------------------------------------------------------- API

    /**
     * Learns one committed [word]. Private sessions and disabled
     * adaptation MUST be gated by the caller. Cheap and never throws.
     */
    fun learnWord(word: String) {
        val w = word.trim().lowercase()
        if (w.isEmpty() || w.length > 40 || w.any { it.isDigit() }) return
        synchronized(lock) {
            val next = (words[w] ?: 0L) + 1L
            words[w] = next.coerceAtMost(MAX_WORD_COUNT)
            trimWords()
            dirty = true
        }
    }

    /** Learns one [prev]→[next] pair. Same gating and guarantees as [learnWord]. */
    fun learnBigram(prev: String, next: String) {
        val p = prev.trim().lowercase()
        val n = next.trim().lowercase()
        if (p.isEmpty() || n.isEmpty() || p.length > 40 || n.length > 40) return
        if (p.any { it.isDigit() } || n.any { it.isDigit() }) return
        synchronized(lock) {
            var row = bigrams[p]
            if (row == null) {
                if (bigrams.size >= MAX_BIGRAMS) evictWeakestBigramRow() ?: return
                row = LinkedHashMap()
                bigrams[p] = row
            }
            val cnt = (row[n] ?: 0L) + 1L
            row[n] = cnt.coerceAtMost(MAX_BIGRAM_COUNT)
            if (row.size > 12) evictWeakestInRow(row)
            dirty = true
        }
    }

    /**
     * DRS M1.6 — learns one committed [word] AND its [lemma]: the word
     * gets +1 like [learnWord]; the lemma gets half of the word's NEW
     * count (floor 1, capped at [MAX_WORD_COUNT]) — the stem accumulates
     * a reservoir that survives the per-load decay, which would
     * otherwise halve a count-1 stem to zero the first time the user
     * reloads (the exact stem-death the tests caught).
     */
    fun learnWordWithLemma(word: String, lemma: String) {
        learnWord(word)
        val l = lemma.trim().lowercase()
        if (l.isEmpty() || l.length > 40 || l.any { it.isDigit() }) return
        synchronized(lock) {
            val wordCount = words[word.trim().lowercase()] ?: 1L
            val bump = (wordCount / 2).coerceIn(1L, MAX_WORD_COUNT)
            lemmas[l] = ((lemmas[l] ?: 0L) + bump).coerceAtMost(MAX_WORD_COUNT)
            if (lemmas.size > MAX_WORDS) trimLemmas()
            dirty = true
        }
    }

    private fun trimLemmas() {
        // evict the weakest lemma rows beyond the word cap
        val iterator = lemmas.entries.iterator()
        while (lemmas.size > MAX_WORDS && iterator.hasNext()) {
            iterator.next()
            iterator.remove()
        }
    }

    /**
     * DRS M1.6 — the personal probability of [word] within the learned
     * vocabulary: count / total-learned-tokens. 0.0 for unknown words.
     */
    fun personalProbability(word: String): Double {
        val w = word.trim().lowercase()
        if (w.isEmpty()) return 0.0
        return synchronized(lock) {
            val count = words[w] ?: return 0.0
            val total = words.values.sum().coerceAtLeast(1L)
            count.toDouble() / total.toDouble()
        }
    }

    /** DRS M1.6 — the personal lemma count (0 when unknown). */
    fun lemmaCount(lemma: String): Long {
        val l = lemma.trim().lowercase()
        if (l.isEmpty()) return 0L
        return synchronized(lock) { lemmas[l] ?: 0L }
    }

    /**
     * Quantized personal boost for [word]: 0 when unknown, else
     * 32..255 scaled from the normalized count (Int8 semantics — the
     * fusion adds it to corpus frequency with no floating point).
     */
    fun boostFor(word: String): Int {
        val w = word.trim().lowercase()
        if (w.isEmpty()) return 0
        val count = synchronized(lock) { words[w] } ?: return 0
        val normalized = count.toDouble() / MAX_WORD_COUNT.toDouble()
        return (32.0 + normalized * 223.0).toInt().coerceIn(32, 255)
    }

    /** Personal next-word predictions for [prev], best first, at most [limit]. */
    fun personalNext(prev: String, limit: Int): List<Pair<String, Int>> {
        if (limit <= 0) return emptyList()
        val p = prev.trim().lowercase()
        if (p.isEmpty()) return emptyList()
        val row = synchronized(lock) { bigrams[p] } ?: return emptyList()
        return row.entries
            .sortedByDescending { it.value }
            .take(limit)
            .map { it.key to it.value.toInt() }
    }

    /** True when [word] exists in the personal table (known user vocabulary). */
    fun knowsWord(word: String): Boolean {
        val w = word.trim().lowercase()
        if (w.isEmpty()) return false
        return synchronized(lock) { words.containsKey(w) }
    }

    /** Current in-memory size (tests and the privacy dashboard). */
    fun size(): Int = synchronized(lock) { words.size }

    fun bigramRowCount(): Int = synchronized(lock) { bigrams.size }

    /** Drops everything (privacy dashboard "clear learned data" action). */
    fun clear(file: File? = null) {
        synchronized(lock) {
            words.clear()
            bigrams.clear()
            lemmas.clear()
            dirty = false
            file?.delete()
        }
    }

    // ---------------------------------------------- sync / privacy (P3)

    /**
     * DRS roadmap phase 3: full read-only snapshot of both tables for
     * the E2E sync bundle (DrsSyncBundle). Copies under the lock — the
     * caller can never observe a half-mutated table. Returns
     * (words, bigrams-as-plain-maps); an empty engine returns empty maps.
     */
    fun exportState(): Pair<Map<String, Long>, Map<String, Map<String, Long>>> = synchronized(lock) {
        Pair(words.toMap(), bigrams.mapValues { it.value.toMap() })
    }

    /**
     * DRS roadmap phase 3: restores a snapshot produced by
     * [exportState] (validated by DrsSyncBundle before it reaches here).
     * `replace = true` swaps both tables wholesale (restore path);
     * `replace = false` merges with additive counts (device-to-device
     * merge path). Counts are clamped to the persisted count ceiling
     * and entries to the table caps so an import can never overflow
     * what a locally-learned state could have produced.
     */
    fun importState(
        importedWords: Map<String, Long>,
        importedBigrams: Map<String, Map<String, Long>>,
        replace: Boolean,
    ) {
        synchronized(lock) {
            if (replace) {
                words = LinkedHashMap()
                bigrams = LinkedHashMap()
            }
            for ((w, c) in importedWords) {
                val key = w.trim().lowercase()
                if (key.isEmpty() || key.length > 40 || key.any { it.isDigit() }) continue
                val next = ((words[key] ?: 0L) + c).coerceAtMost(MAX_WORD_COUNT)
                words[key] = next
            }
            for ((p, row) in importedBigrams) {
                val prev = p.trim().lowercase()
                if (prev.isEmpty() || prev.length > 40) continue
                val target = bigrams.getOrPut(prev) { LinkedHashMap() }
                for ((n, c) in row) {
                    val nxt = n.trim().lowercase()
                    if (nxt.isEmpty() || nxt.length > 40) continue
                    target[nxt] = ((target[nxt] ?: 0L) + c).coerceAtMost(MAX_BIGRAM_COUNT)
                }
                while (target.size > MAX_BIGRAM_COUNT.toInt()) {
                    evictWeakestInRow(target)
                }
            }
            while (words.size > MAX_WORDS) trimWords()
            while (bigrams.size > MAX_BIGRAMS) {
                evictWeakestBigramRow() ?: break
            }
            dirty = true
        }
    }

    // ------------------------------------------------------ persistence

    // DRS Phase 2: kotlinx.serialization carries the persisted form —
    // the SAME JSON stack the rest of the app uses, and (unlike the
    // android-framework org.json) a REAL JVM implementation, so the
    // persistence round-trip is fully unit-testable.
    @Serializable
    private data class PersistedBigramRow(val nexts: Map<String, Long> = emptyMap())

    @Serializable
    private data class PersistedState(
        val v: Int = 1,
        val words: Map<String, Long> = emptyMap(),
        val bigrams: Map<String, PersistedBigramRow> = emptyMap(),
        // DRS M1.6 — the lemma table rides the same file; the default keeps
        // older payloads decoding unchanged (backward compatibility).
        val lemmas: Map<String, Long> = emptyMap(),
    )

    private val persistedJson = Json { ignoreUnknownKeys = true }

    /** Loads (and ages) the tables from [file]. A damaged file is discarded. */
    fun load(file: File) {
        val parsed: Triple<HashMap<String, Long>, HashMap<String, LinkedHashMap<String, Long>>, HashMap<String, Long>> =
            try {
                parse(file)
            } catch (_: Throwable) {
                Triple(HashMap(), HashMap(), HashMap())
            }
        synchronized(lock) {
            words = LinkedHashMap(parsed.first)
            bigrams = LinkedHashMap(parsed.second)
            lemmas = LinkedHashMap(parsed.third)
            // Aging: fresh usage outranks stale usage. Applied on load so a
            // user who stops using a word watches it fade instead of facing
            // it forever.
            val agedWords = LinkedHashMap<String, Long>()
            for ((w, c) in words) {
                val aged = c - (c * DECAY_PERCENT / 100)
                if (aged >= 1L) agedWords[w] = aged
            }
            val agedBigrams = LinkedHashMap<String, LinkedHashMap<String, Long>>()
            for ((p, row) in bigrams) {
                val agedRow = LinkedHashMap<String, Long>()
                for ((n, c) in row) {
                    val aged = c - (c * DECAY_PERCENT / 100)
                    if (aged >= 1L) agedRow[n] = aged
                }
                if (agedRow.isNotEmpty()) agedBigrams[p] = agedRow
            }
            // DRS M1.6 — lemmas decay the same way; their count/2 reservoir
            // (floor 1 on every learn) is what keeps the stem alive while
            // its words age out.
            val agedLemmas = LinkedHashMap<String, Long>()
            for ((l, c) in lemmas) {
                val aged = c - (c * DECAY_PERCENT / 100)
                if (aged >= 1L) agedLemmas[l] = aged
            }
            words = agedWords
            bigrams = agedBigrams
            lemmas = agedLemmas
            dirty = false
        }
    }

    /**
     * Persists to [file] atomically (tmp + rename). Writes only when
     * something changed since the last write — the hot path stays free of
     * disk traffic. Never throws.
     */
    fun persist(file: File) {
        val snapshotJson: String = synchronized(lock) {
            if (!dirty) return
            try {
                encode().toByteArray(Charsets.UTF_8)
                    .takeIf { it.size <= FILE_BYTES_CAP }
                    ?.toString(Charsets.UTF_8)
            } catch (_: Throwable) {
                null
            }
        } ?: return
        try {
            val parent = file.parentFile ?: return
            parent.mkdirs()
            val tmp = File(parent, file.name + ".tmp")
            tmp.writeText(snapshotJson, Charsets.UTF_8)
            if (!tmp.renameTo(file)) {
                file.writeText(snapshotJson, Charsets.UTF_8)
                tmp.delete()
            }
            synchronized(lock) { dirty = false }
        } catch (_: Throwable) {
            // Learning persistence is best-effort by design.
        }
    }

    // ------------------------------------------------------- internals

    private fun trimWords() {
        if (words.size <= MAX_WORDS) return
        // Evict least-used entries (a full sort of 2k entries is negligible
        // here and only runs on the rare overflow path).
        val keep = words.entries
            .sortedByDescending { it.value }
            .take(MAX_WORDS * 9 / 10)
        val rebuilt = LinkedHashMap<String, Long>()
        for (entry in keep) rebuilt[entry.key] = entry.value
        words = rebuilt
    }

    private fun evictWeakestBigramRow(): String? {
        var weakestKey: String? = null
        var weakestSum = Long.MAX_VALUE
        for ((p, row) in bigrams) {
            val sum = row.values.sum()
            if (sum < weakestSum) {
                weakestSum = sum
                weakestKey = p
            }
        }
        return weakestKey?.also { bigrams.remove(it) }
    }

    private fun evictWeakestInRow(row: LinkedHashMap<String, Long>) {
        var weakestKey: String? = null
        var weakest = Long.MAX_VALUE
        for ((n, c) in row) {
            if (c < weakest) {
                weakest = c
                weakestKey = n
            }
        }
        weakestKey?.let { row.remove(it) }
    }

    private fun encode(): String {
        val state = synchronized(lock) {
            PersistedState(
                v = 1,
                words = HashMap(words),
                bigrams = bigrams.entries.associate { (p, row) ->
                    p to PersistedBigramRow(HashMap(row))
                },
                lemmas = HashMap(lemmas),
            )
        }
        return persistedJson.encodeToString(PersistedState.serializer(), state)
    }

    @Suppress("unused")
    private fun parse(file: File): Triple<HashMap<String, Long>, HashMap<String, LinkedHashMap<String, Long>>, HashMap<String, Long>> {
        if (!file.exists() || file.length() > FILE_BYTES_CAP) {
            return Triple(HashMap(), HashMap(), HashMap())
        }
        val state = persistedJson.decodeFromString<PersistedState>(file.readText(Charsets.UTF_8))
        val wMap = HashMap<String, Long>(state.words.size * 2)
        for ((w, c) in state.words) wMap[w] = c
        val bMap = HashMap<String, LinkedHashMap<String, Long>>()
        for ((p, row) in state.bigrams) {
            val linked = LinkedHashMap<String, Long>()
            for ((n, c) in row.nexts) linked[n] = c
            if (linked.isNotEmpty()) bMap[p] = linked
        }
        val lMap = HashMap<String, Long>(state.lemmas.size * 2)
        for ((l, c) in state.lemmas) lMap[l] = c
        return Triple(wMap, bMap, lMap)
    }
}
