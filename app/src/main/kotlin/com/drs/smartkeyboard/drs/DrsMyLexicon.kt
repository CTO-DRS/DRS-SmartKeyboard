/*
 * Copyright (C) 2026 The DRS Smart Keyboard Project
 */

package com.drs.smartkeyboard.drs

/**
 * DRS v2.22.0 — نظام «قاموسي التشكيلي» (the personal tashkeel lexicon) —
 * the deterministic engine behind the harakat system's USER-TAUGHT
 * vocabulary and its manager screen.
 *
 * The doctrine is the reference lexicons' mirror: the seed
 * ([DrsWordTashkeel]) and the 3000-word asset ([DrsTashkeelLexicon]) are
 * READ-ONLY references, while a personal entry is the user's explicit
 * teaching — a stripped word mapped to its full vocalization. The lookup
 * chain is «المستخدم تفوز» (the user wins): the personal entry is consulted
 * BEFORE both references, and the whole chain stays closed — no marks are
 * ever invented, an unknown word still returns null honestly.
 *
 * The entry contract is the SAME closed contract the references obey
 * (pinned by [validate], a violation is REJECTED, never patched):
 *  1. the word is stored STRIPPED — it must not carry a combining mark
 *     or a tatweel (the key IS the stripped form);
 *  2. `strip(vocalized) == word` — the taught vocalization collapses to
 *     exactly the word it teaches;
 *  3. the only combining marks allowed are the nine canonical harakat of
 *     [DrsHarakat.MARKS] (plus the tatweel as a stretch, never a mark) —
 *     enforced structurally by [DrsHarakatWordOps.stripDiacritics], which
 *     keeps every non-mark character verbatim.
 *
 * One entry per word key: re-teaching the same word UPDATES its entry in
 * place (the newest teaching wins, the id never changes) — a dictionary,
 * not a log. The [MAX_ITEMS] cap REFUSES (fail-closed) instead of
 * silently evicting: taught knowledge is never destroyed behind the
 * user's back.
 *
 * Pure core: every mutation takes and returns [DrsState] so the compiler
 * and the unit tests can pin the whole contract without Android or the
 * store. The thin [DrsStore] wrappers at the bottom only wire the pure
 * core into the persisted state flow.
 */
object DrsMyLexicon {

    /** Hard cap of taught words — curated knowledge, not an infinite log. */
    const val MAX_ITEMS = 200

    const val MAX_WORD_LEN = 32
    const val MAX_VOCALIZED_LEN = 48

    /** Validation failures, each named so callers can localize honestly. */
    enum class Error {
        EMPTY_WORD,
        EMPTY_VOCALIZED,
        WORD_TOO_LONG,
        VOCALIZED_TOO_LONG,
        WORD_HAS_MARKS,
        VOCALIZED_MISMATCH,
        DUPLICATE_WORD,
        FULL,
    }

    // ------------------------------------------------------------------
    // The pure core — takes DrsState, returns DrsState (or a rejection).
    // ------------------------------------------------------------------

    /** The stripped form of [word] — the same surgery the references obey. */
    private fun stripped(word: String): String =
        DrsHarakatWordOps.stripDiacritics(word.trim())

    /**
     * Validates a candidate (after trimming). Returns null when
     * acceptable. The word must be mark-free (it IS the stripped key) and
     * the vocalization must collapse to exactly that key.
     */
    fun validate(word: String, vocalized: String): Error? {
        val w = word.trim()
        val v = vocalized.trim()
        return when {
            w.isEmpty() -> Error.EMPTY_WORD
            v.isEmpty() -> Error.EMPTY_VOCALIZED
            w.length > MAX_WORD_LEN -> Error.WORD_TOO_LONG
            v.length > MAX_VOCALIZED_LEN -> Error.VOCALIZED_TOO_LONG
            // The stored key carries no marks: a mark typed in the word
            // field means the user pasted a vocalized word — strip it FOR
            // them is a guess; rejecting is honest (the dialog strips the
            // word before calling, so this is a defensive last gate).
            w.any { DrsHarakat.isCombiningMark(it) || it == DrsHarakat.TATWEEL } ->
                Error.WORD_HAS_MARKS
            stripped(v) != w -> Error.VOCALIZED_MISMATCH
            else -> null
        }
    }

    /**
     * Adds (or re-teaches) a word. Returns the new state, or the
     * rejection reason. The id allocator [DrsState.nextMyLexiconId]
     * never skips or wraps.
     *
     * One entry per word: teaching an EXISTING word again updates its
     * vocalization in place (id and pinned stay, updatedAt rises) — the
     * newest teaching wins. Renaming an edit onto another entry's word is
     * rejected with [Error.DUPLICATE_WORD] (use the other entry).
     */
    fun addAt(
        state: DrsState,
        word: String,
        vocalized: String,
        nowMs: Long,
    ): Result {
        val w = word.trim()
        val v = vocalized.trim()
        validate(w, v)?.let { return Result.Rejected(it) }
        val existing = state.myLexicon.firstOrNull { it.word == w }
        if (existing != null) {
            val updated = state.myLexicon.map {
                if (it.id == existing.id) {
                    it.copy(vocalized = v, updatedAtMs = nowMs)
                } else {
                    it
                }
            }
            return Result.Accepted(state.copy(myLexicon = updated))
        }
        if (state.myLexicon.size >= MAX_ITEMS) return Result.Rejected(Error.FULL)
        val id = state.nextMyLexiconId
        val entry = DrsMyLexiconEntry(
            id = id,
            word = w,
            vocalized = v,
            pinned = false,
            createdAtMs = nowMs,
            updatedAtMs = nowMs,
        )
        return Result.Accepted(
            state.copy(myLexicon = state.myLexicon + entry, nextMyLexiconId = id + 1),
        )
    }

    /**
     * Edits a taught entry in place (id stays, updatedAt rises). Unknown
     * ids are a no-op returning the same state — an honest no-failure
     * edit. Renaming the word onto another entry's word is rejected —
     * two entries can never own the same key.
     */
    fun editAt(
        state: DrsState,
        id: Long,
        word: String,
        vocalized: String,
        nowMs: Long,
    ): Result {
        val w = word.trim()
        val v = vocalized.trim()
        validate(w, v)?.let { return Result.Rejected(it) }
        val target = state.myLexicon.firstOrNull { it.id == id }
            ?: return Result.Accepted(state)
        if (state.myLexicon.any { it.id != id && it.word == w }) {
            return Result.Rejected(Error.DUPLICATE_WORD)
        }
        val updated = state.myLexicon.map {
            if (it.id == id) {
                it.copy(word = w, vocalized = v, updatedAtMs = nowMs)
            } else {
                it
            }
        }
        return Result.Accepted(state.copy(myLexicon = updated))
    }

    /** Removes by id; unknown ids are a no-op. */
    fun removeAt(state: DrsState, id: Long): DrsState =
        state.copy(myLexicon = state.myLexicon.filter { it.id != id })

    /** Removes every taught word (the manager's remove-all, confirmed twice in UI). */
    fun removeAllAt(state: DrsState): DrsState = state.copy(myLexicon = emptyList())

    /** Flips the pin flag; unknown ids are a no-op. Pinning never reorders storage. */
    fun togglePinAt(state: DrsState, id: Long): DrsState =
        state.copy(
            myLexicon = state.myLexicon.map {
                if (it.id == id) it.copy(pinned = !it.pinned) else it
            },
        )

    /**
     * The deterministic display order: pinned first, each group
     * alphabetical by the stripped word (Locale.ROOT), ties broken by id
     * descending. The same input always yields the same order — a
     * dictionary reads alphabetically, not by edit time.
     */
    fun order(entries: List<DrsMyLexiconEntry>): List<DrsMyLexiconEntry> =
        entries.sortedWith(
            compareByDescending<DrsMyLexiconEntry> { it.pinned }
                .thenBy { it.word.lowercase(java.util.Locale.ROOT) }
                .thenByDescending { it.id },
        )

    /**
     * The manager's search: a trimmed, case-insensitive (ROOT) contains
     * over word + vocalized. An empty query selects everything. No
     * normalization games — what the user taught is what matches.
     */
    fun search(entries: List<DrsMyLexiconEntry>, query: String): List<DrsMyLexiconEntry> {
        val needle = query.trim().lowercase(java.util.Locale.ROOT)
        if (needle.isEmpty()) return entries
        return entries.filter {
            it.word.lowercase(java.util.Locale.ROOT).contains(needle) ||
                it.vocalized.lowercase(java.util.Locale.ROOT).contains(needle)
        }
    }

    /**
     * The lookup seam the vocalize chain consumes: the enabled personal
     * overrides as a stripped-word → vocalized map. A disabled lexicon
     * (or an empty one) yields an empty map — the references serve alone,
     * exactly the pre-v2.22.0 behavior.
     */
    fun overridesOf(state: DrsState): Map<String, String> {
        if (!state.myLexiconEnabled) return emptyMap()
        return state.myLexicon.associate { it.word to it.vocalized }
    }

    // ------------------------------------------------------------------
    // The thin store wrappers (UI calls these; tests call the pure core).
    // ------------------------------------------------------------------

    /** Mutation result of the pure core, or the rejection reason. */
    sealed interface Result {
        data class Accepted(val state: DrsState) : Result
        data class Rejected(val error: Error) : Result
    }

    /**
     * Master switch — gates the override lookup (the vocalize chain),
     * never management. Off, the taught words stay stored and editable
     * but the seed and the asset serve alone.
     */
    fun setEnabled(enabled: Boolean) {
        DrsStore.update { it.copy(myLexiconEnabled = enabled) }
    }

    /**
     * Synchronous pre-check + fire-and-forget store write — the exact
     * contract [DrsMyTexts.add] established: the caller gets the honest
     * answer of the pure pre-check (validate + capacity + re-teach), and
     * the store transform re-runs [addAt] as the single source of truth
     * (rejections inside it are a no-op).
     */
    fun add(word: String, vocalized: String): Boolean {
        val w = word.trim()
        val v = vocalized.trim()
        if (validate(w, v) != null) return false
        val state = DrsStore.state.value
        val isReteach = state.myLexicon.any { it.word == w }
        if (!isReteach && state.myLexicon.size >= MAX_ITEMS) return false
        val now = System.currentTimeMillis()
        DrsStore.update { s ->
            when (val r = addAt(s, w, v, now)) {
                is Result.Accepted -> r.state
                is Result.Rejected -> s
            }
        }
        return true
    }

    /** See [add] — the pure pre-check answers synchronously and honestly. */
    fun edit(id: Long, word: String, vocalized: String): Boolean {
        val w = word.trim()
        val v = vocalized.trim()
        if (validate(w, v) != null) return false
        if (DrsStore.state.value.myLexicon.none { it.id == id }) return false
        val now = System.currentTimeMillis()
        DrsStore.update { s ->
            when (val r = editAt(s, id, w, v, now)) {
                is Result.Accepted -> r.state
                is Result.Rejected -> s
            }
        }
        return true
    }

    fun remove(id: Long) {
        DrsStore.update { removeAt(it, id) }
    }

    fun removeAll() {
        DrsStore.update { removeAllAt(it) }
    }

    fun togglePin(id: Long) {
        DrsStore.update { togglePinAt(it, id) }
    }

    /**
     * One anonymous hit counter bump (counts only — never WHICH word was
     * vocalized). Routed through the adaptation engine's single drain
     * point like every other feature counter.
     */
    fun recordUse() {
        DrsAdaptationEngine.recordMyLexiconUse()
    }
}
