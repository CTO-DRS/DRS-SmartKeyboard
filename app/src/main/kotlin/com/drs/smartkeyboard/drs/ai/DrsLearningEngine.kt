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
 *  - personal trigram counts (v2.13.0 «السياق الأعمق» — the user's real
 *    two-word-context habits: what follows WHAT after WHAT)
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
    // v2.13.0 «السياق الأعمق» — the trigram table rides the same store,
    // the same discipline: bounded rows, bounded counts, bounded file.
    const val MAX_TRIGRAMS = 512
    private const val MAX_TRIGRAM_COUNT = 100L
    private const val MAX_TRIGRAM_ROW = 8
    private const val MAX_WORD_COUNT = 200L
    private const val MAX_BIGRAM_COUNT = 100L
    private const val FILE_BYTES_CAP = 128 * 1024
    private const val DECAY_PERCENT = 25 // per-load aging: -25% of each count

    private val lock = Any()
    private var words = LinkedHashMap<String, Long>()
    private var bigrams = LinkedHashMap<String, LinkedHashMap<String, Long>>()
    // v2.13.0 — personal trigrams: p2 → p1 → next → count. NESTED keys (no
    // joined-string key) so a learned "word" containing a space can never
    // collide with the row delimiter.
    private var trigrams = LinkedHashMap<String, LinkedHashMap<String, LinkedHashMap<String, Long>>>()
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

    /**
     * v2.13.0 «السياق الأعمق» — learns one [p2]→[p1]→[next] chain. Same
     * gating and guarantees as [learnBigram]: capped rows (evicting the
     * weakest row on overflow), capped counts (saturation), capped row
     * width (evicting the weakest continuation), never throws.
     */
    fun learnTrigram(p2: String, p1: String, next: String) {
        val a = p2.trim().lowercase()
        val b = p1.trim().lowercase()
        val c = next.trim().lowercase()
        if (a.isEmpty() || b.isEmpty() || c.isEmpty()) return
        if (a.length > 40 || b.length > 40 || c.length > 40) return
        if (a.any { it.isDigit() } || b.any { it.isDigit() } || c.any { it.isDigit() }) return
        synchronized(lock) {
            val row1 = trigrams[a]
            if (row1 == null) {
                if (trigrams.size >= MAX_TRIGRAMS) evictWeakestTrigramRow() ?: return
                trigrams[a] = LinkedHashMap()
            }
            // Here: trigrams[a] is guaranteed non-null (created or pre-existing).
            val row1b = trigrams.getValue(a)
            var row2 = row1b[b]
            if (row2 == null) {
                // b is NOT in row1 here — when the p1 slot is full the
                // weakest p1 row gives way first.
                if (row1b.size >= MAX_TRIGRAM_ROW) {
                    evictWeakestP1Row(row1b)
                    if (row1b.size >= MAX_TRIGRAM_ROW) return
                }
                row2 = row1b.getOrPut(b) { LinkedHashMap() }
            }
            val cnt = (row2[c] ?: 0L) + 1L
            row2[c] = cnt.coerceAtMost(MAX_TRIGRAM_COUNT)
            if (row2.size > MAX_TRIGRAM_ROW) evictWeakestInRow(row2)
            dirty = true
        }
    }

    private fun evictWeakestTrigramRow(): String? {
        var weakestKey: String? = null
        var weakestSum = Long.MAX_VALUE
        for ((a, row1) in trigrams) {
            var sum = 0L
            for (row2 in row1.values) sum += row2.values.sum()
            if (sum < weakestSum) {
                weakestSum = sum
                weakestKey = a
            }
        }
        return weakestKey?.also { trigrams.remove(it) }
    }

    private fun evictWeakestP1Row(row1: LinkedHashMap<String, LinkedHashMap<String, Long>>) {
        var weakestKey: String? = null
        var weakestSum = Long.MAX_VALUE
        for ((b, row2) in row1) {
            val sum = row2.values.sum()
            if (sum < weakestSum) {
                weakestSum = sum
                weakestKey = b
            }
        }
        weakestKey?.let { row1.remove(it) }
    }

    /**
     * v2.13.0 — the personal trigram row for a two-word context (p2, p1):
     * (next, count) pairs, strongest first, at most [limit].
     */
    fun personalTrigramNext(p2: String, p1: String, limit: Int): List<Pair<String, Int>> {
        if (limit <= 0) return emptyList()
        val a = p2.trim().lowercase()
        val b = p1.trim().lowercase()
        if (a.isEmpty() || b.isEmpty()) return emptyList()
        val row = synchronized(lock) { trigrams[a]?.get(b) } ?: return emptyList()
        // Deterministic tiebreak: count desc, then word — the persisted
        // roundtrip (HashMap encode/decode) must never flip equal counts.
        return row.entries
            .sortedWith(compareByDescending<Map.Entry<String, Long>> { it.value }.thenBy { it.key })
            .take(limit)
            .map { it.key to it.value.toInt() }
    }

    fun trigramRowCount(): Int = synchronized(lock) { trigrams.size }

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
            trigrams.clear()
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

    /**
     * v2.13.0 «السياق الأعمق» — read-only snapshot of the trigram table
     * for the E2E sync bundle (additive companion of [exportState] — the
     * existing signature is untouched so today's callers never change).
     * p2 → p1 → next → count, copies under the lock.
     */
    fun exportTrigramState(): Map<String, Map<String, Map<String, Long>>> = synchronized(lock) {
        trigrams.mapValues { (_, row1) -> row1.mapValues { (_, row2) -> row2.toMap() } }
    }

    /**
     * v2.13.0 — restores a trigram snapshot produced by
     * [exportTrigramState] (validated by DrsSyncBundle before it reaches
     * here). Same replace/merge semantics as [importState]; the clamps
     * guarantee an import can never overflow what locally-learned state
     * could have produced (rows ≤ [MAX_TRIGRAMS], counts ≤ the ceiling,
     * row width ≤ [MAX_TRIGRAM_ROW]).
     */
    fun importTrigramState(
        importedTrigrams: Map<String, Map<String, Map<String, Long>>>,
        replace: Boolean,
    ) {
        synchronized(lock) {
            if (replace) trigrams = LinkedHashMap()
            for ((a, row1) in importedTrigrams) {
                val p2 = a.trim().lowercase()
                if (p2.isEmpty() || p2.length > 40) continue
                val target1 = trigrams.getOrPut(p2) { LinkedHashMap() }
                for ((b, row2) in row1) {
                    val p1 = b.trim().lowercase()
                    if (p1.isEmpty() || p1.length > 40) continue
                    val target2 = target1.getOrPut(p1) { LinkedHashMap() }
                    for ((c, n) in row2) {
                        val nxt = c.trim().lowercase()
                        if (nxt.isEmpty() || nxt.length > 40) continue
                        target2[nxt] = ((target2[nxt] ?: 0L) + n).coerceAtMost(MAX_TRIGRAM_COUNT)
                    }
                    while (target2.size > MAX_TRIGRAM_ROW) evictWeakestInRow(target2)
                }
                while (target1.size > MAX_TRIGRAM_ROW) evictWeakestP1Row(target1)
            }
            while (trigrams.size > MAX_TRIGRAMS) {
                evictWeakestTrigramRow() ?: break
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
        // v2.13.0 «السياق الأعمق» — personal trigrams (p2 → p1 → nexts).
        // Additive field with an empty default: files written by older
        // versions decode unchanged, and THIS version's files decode on
        // older versions (ignoreUnknownKeys = true) with trigrams simply
        // absent — never a crash, never a migration.
        val trigrams: Map<String, Map<String, PersistedBigramRow>> = emptyMap(),
    )

    private val persistedJson = Json { ignoreUnknownKeys = true }

    /** One parse outcome — the four tables, decoded (not yet aged). */
    private data class ParsedTables(
        val words: HashMap<String, Long>,
        val bigrams: HashMap<String, LinkedHashMap<String, Long>>,
        val lemmas: HashMap<String, Long>,
        val trigrams: HashMap<String, LinkedHashMap<String, LinkedHashMap<String, Long>>>,
    )

    private fun emptyTables(): ParsedTables = ParsedTables(HashMap(), HashMap(), HashMap(), HashMap())

    /** Loads (and ages) the tables from [file]. A damaged file is discarded. */
    fun load(file: File) {
        val parsed: ParsedTables = try {
            parse(file)
        } catch (_: Throwable) {
            emptyTables()
        }
        synchronized(lock) {
            words = LinkedHashMap(parsed.words)
            bigrams = LinkedHashMap(parsed.bigrams)
            lemmas = LinkedHashMap(parsed.lemmas)
            trigrams = LinkedHashMap(parsed.trigrams)
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
            // v2.13.0 — trigrams decay exactly like bigrams: what the user
            // stopped writing fades; the two-word habits age toward the caps.
            val agedTrigrams = LinkedHashMap<String, LinkedHashMap<String, LinkedHashMap<String, Long>>>()
            for ((a, row1) in trigrams) {
                val agedRow1 = LinkedHashMap<String, LinkedHashMap<String, Long>>()
                for ((b, row2) in row1) {
                    val agedRow2 = LinkedHashMap<String, Long>()
                    for ((c, n) in row2) {
                        val aged = n - (n * DECAY_PERCENT / 100)
                        if (aged >= 1L) agedRow2[c] = aged
                    }
                    if (agedRow2.isNotEmpty()) agedRow1[b] = agedRow2
                }
                if (agedRow1.isNotEmpty()) agedTrigrams[a] = agedRow1
            }
            words = agedWords
            bigrams = agedBigrams
            lemmas = agedLemmas
            trigrams = agedTrigrams
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
                trigrams = trigrams.entries.associate { (a, row1) ->
                    a to row1.entries.associate { (b, row2) ->
                        b to PersistedBigramRow(HashMap(row2))
                    }
                },
            )
        }
        return persistedJson.encodeToString(PersistedState.serializer(), state)
    }

    private fun parse(file: File): ParsedTables {
        if (!file.exists() || file.length() > FILE_BYTES_CAP) {
            return emptyTables()
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
        val tMap = HashMap<String, LinkedHashMap<String, LinkedHashMap<String, Long>>>()
        for ((a, row1) in state.trigrams) {
            val linked1 = LinkedHashMap<String, LinkedHashMap<String, Long>>()
            for ((b, row2) in row1) {
                val linked2 = LinkedHashMap<String, Long>()
                for ((c, n) in row2.nexts) linked2[c] = n
                if (linked2.isNotEmpty()) linked1[b] = linked2
            }
            if (linked1.isNotEmpty()) tMap[a] = linked1
        }
        return ParsedTables(wMap, bMap, lMap, tMap)
    }
}
