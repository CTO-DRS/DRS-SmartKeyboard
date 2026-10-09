/*
 * Copyright (C) 2026 The DRS Smart Keyboard Project
 */

package com.drs.smartkeyboard.drs

/**
 * DRS v2.21.0 — نظام «نصوصي» (the saved-texts system) — the deterministic
 * engine behind the SIXTH smart panel and its manager screen.
 *
 * The doctrine is the clipboard's mirror image: the clipboard history is
 * AUTO-CAPTURED and TRANSIENT (copies come and go, cleaned by policy),
 * while a saved text is USER-CURATED and PERMANENT — the user explicitly
 * types or saves it, browses it from the panel, and inserts it with one
 * tap. Nothing is ever captured implicitly, nothing is guessed, nothing
 * leaves the device.
 *
 * Pure core: every mutation takes and returns [DrsState] so the compiler
 * and the unit tests can pin the whole contract without Android or the
 * store. The thin [DrsStore] wrappers at the bottom only wire the pure
 * core into the persisted state flow.
 *
 * Templates reuse the shortcuts engine's variables verbatim
 * ({date}, {time}, {hijri}, {clipboard}, {newline}, {cursor}) — one
 * expansion contract across both writing-assist systems, expanded at
 * INSERT time so {date} never lies.
 */
object DrsMyTexts {

    /** Hard cap of saved texts — curated content, not an infinite log. */
    const val MAX_ITEMS = 100

    const val MAX_TEXT_LEN = 500
    const val MAX_LABEL_LEN = 40
    const val MAX_CATEGORY_LEN = 24

    /** The honest on-panel preview width (chars) before ellipsis. */
    const val PREVIEW_MAX_LEN = 24

    /** The general bucket's stored category value (empty string = عام). */
    const val GENERAL_CATEGORY = ""

    /** Validation failures, each named so callers can localize honestly. */
    enum class Error {
        EMPTY_TEXT,
        TEXT_TOO_LONG,
        LABEL_TOO_LONG,
        CATEGORY_TOO_LONG,
        FULL,
    }

    // ------------------------------------------------------------------
    // The pure core — takes DrsState, returns DrsState (or a rejection).
    // ------------------------------------------------------------------

    /** Validates a candidate (after trimming). Returns null when acceptable. */
    fun validate(text: String, label: String, category: String): Error? {
        val t = text.trim()
        val l = label.trim()
        val c = category.trim()
        return when {
            t.isEmpty() -> Error.EMPTY_TEXT
            t.length > MAX_TEXT_LEN -> Error.TEXT_TOO_LONG
            l.length > MAX_LABEL_LEN -> Error.LABEL_TOO_LONG
            c.length > MAX_CATEGORY_LEN -> Error.CATEGORY_TOO_LONG
            else -> null
        }
    }

    /**
     * Adds a saved text. Returns the new state, or the rejection reason.
     * The id allocator [DrsState.nextMyTextId] never skips or wraps.
     *
     * Honest dedup: saving the EXACT same (text + label + category) triple
     * again does not create a twin — it refreshes the existing entry's
     * updatedAt (it rises to the top) and keeps its id. A double tap of
     * any save button can therefore never duplicate a row.
     *
     * The [MAX_ITEMS] cap REFUSES (fail-closed) instead of silently
     * evicting: curated content is never destroyed behind the user's back.
     */
    fun addAt(
        state: DrsState,
        text: String,
        label: String,
        category: String,
        nowMs: Long,
    ): Result {
        val t = text.trim()
        val l = label.trim()
        val c = category.trim()
        validate(t, l, c)?.let { return Result.Rejected(it) }
        val existing = state.myTexts.firstOrNull {
            it.text == t && it.label == l && it.category == c
        }
        if (existing != null) {
            val updated = state.myTexts.map {
                if (it.id == existing.id) it.copy(updatedAtMs = nowMs) else it
            }
            return Result.Accepted(state.copy(myTexts = updated))
        }
        if (state.myTexts.size >= MAX_ITEMS) return Result.Rejected(Error.FULL)
        val id = state.nextMyTextId
        val entry = DrsMyText(
            id = id,
            text = t,
            label = l,
            category = c,
            pinned = false,
            createdAtMs = nowMs,
            updatedAtMs = nowMs,
        )
        return Result.Accepted(
            state.copy(myTexts = state.myTexts + entry, nextMyTextId = id + 1),
        )
    }

    /**
     * Edits a saved text in place (id stays, updatedAt rises). Unknown ids
     * are a no-op returning the same state — an honest no-failure edit.
     */
    fun editAt(
        state: DrsState,
        id: Long,
        text: String,
        label: String,
        category: String,
        nowMs: Long,
    ): Result {
        val t = text.trim()
        val l = label.trim()
        val c = category.trim()
        validate(t, l, c)?.let { return Result.Rejected(it) }
        val target = state.myTexts.firstOrNull { it.id == id }
            ?: return Result.Accepted(state)
        val updated = state.myTexts.map {
            if (it.id == id) {
                it.copy(text = t, label = l, category = c, updatedAtMs = nowMs)
            } else {
                it
            }
        }
        return Result.Accepted(state.copy(myTexts = updated))
    }

    /** Removes by id; unknown ids are a no-op. */
    fun removeAt(state: DrsState, id: Long): DrsState =
        state.copy(myTexts = state.myTexts.filter { it.id != id })

    /** Removes every saved text (the manager's remove-all, confirmed twice in UI). */
    fun removeAllAt(state: DrsState): DrsState = state.copy(myTexts = emptyList())

    /** Flips the pin flag; unknown ids are a no-op. Pinning never reorders storage. */
    fun togglePinAt(state: DrsState, id: Long): DrsState =
        state.copy(
            myTexts = state.myTexts.map {
                if (it.id == id) it.copy(pinned = !it.pinned) else it
            },
        )

    /**
     * The deterministic display order: pinned first, each group by
     * updatedAtMs descending, ties broken by id descending (the newer
     * allocation wins). The same input always yields the same order.
     */
    fun order(texts: List<DrsMyText>): List<DrsMyText> =
        texts.sortedWith(
            compareByDescending<DrsMyText> { it.pinned }
                .thenByDescending { it.updatedAtMs }
                .thenByDescending { it.id },
        )

    /**
     * The panel/manager search: a trimmed, case-insensitive (ROOT) contains
     * over text + label + category. An empty query selects everything.
     * No normalization games — what the user saved is what matches.
     */
    fun search(texts: List<DrsMyText>, query: String): List<DrsMyText> {
        val needle = query.trim().lowercase(java.util.Locale.ROOT)
        if (needle.isEmpty()) return texts
        return texts.filter {
            it.text.lowercase(java.util.Locale.ROOT).contains(needle) ||
                it.label.lowercase(java.util.Locale.ROOT).contains(needle) ||
                it.category.lowercase(java.util.Locale.ROOT).contains(needle)
        }
    }

    /** Distinct non-blank categories, sorted — the panel's chip bar source. */
    fun categoriesOf(texts: List<DrsMyText>): List<String> =
        texts.map { it.category }
            .filter { it.isNotBlank() }
            .distinct()
            .sorted()

    /**
     * The honest row label: the user's label when present, otherwise the
     * first line of the text, trimmed and capped at [PREVIEW_MAX_LEN]
     * with a real ellipsis — the panel never shows a fake summary.
     */
    fun previewLabel(text: String, label: String): String {
        val source = label.trim().ifBlank { text.trim().lineSequence().firstOrNull().orEmpty().trim() }
        if (source.length <= PREVIEW_MAX_LEN) return source
        return source.take(PREVIEW_MAX_LEN).trimEnd() + "…"
    }

    /**
     * Insert-time expansion — delegates verbatim to the shortcuts engine's
     * template expander so {date}/{time}/{hijri}/{clipboard}/{newline} keep
     * ONE behavior across both systems. Unknown variables stay literal, so
     * the user can never lose text. Never throws.
     */
    fun expand(text: String, clipboardText: String? = null): String =
        DrsShortcuts.expandTemplate(text, clipboardText)

    /** True when the saved text carries at least one template variable. */
    fun isTemplate(text: String): Boolean = DrsShortcuts.isTemplate(text)

    /** True when the text places the cursor via {cursor}. */
    fun hasCursorMarker(text: String): Boolean = DrsShortcuts.hasCursorMarker(text)

    /** See [DrsShortcuts.extractCursorMarker] — the shared insert contract. */
    fun extractCursorMarker(text: String): Pair<String, Int> =
        DrsShortcuts.extractCursorMarker(text)

    // ------------------------------------------------------------------
    // The thin store wrappers (UI calls these; tests call the pure core).
    // ------------------------------------------------------------------

    /** Mutation result of the pure core, or the rejection reason. */
    sealed interface Result {
        data class Accepted(val state: DrsState) : Result
        data class Rejected(val error: Error) : Result
    }

    /**
     * Master switch — gates the panel and insertion, never management.
     */
    fun setEnabled(enabled: Boolean) {
        DrsStore.update { it.copy(myTextsEnabled = enabled) }
    }

    /**
     * Synchronous pre-check + fire-and-forget store write. The coalesced
     * [DrsStore.update] is ASYNC, so the boolean CANNOT come from inside
     * the transform — instead the caller gets the honest answer of the
     * pure pre-check (validate + capacity + dedup), the same contract
     * [addAt] enforces, and the store transform re-runs [addAt] as the
     * single source of truth (rejections inside it are a no-op).
     */
    fun add(text: String, label: String, category: String): Boolean {
        val t = text.trim()
        val l = label.trim()
        val c = category.trim()
        if (validate(t, l, c) != null) return false
        val state = DrsStore.state.value
        val isDuplicate = state.myTexts.any { it.text == t && it.label == l && it.category == c }
        if (!isDuplicate && state.myTexts.size >= MAX_ITEMS) return false
        val now = System.currentTimeMillis()
        DrsStore.update { s ->
            when (val r = addAt(s, t, l, c, now)) {
                is Result.Accepted -> r.state
                is Result.Rejected -> s
            }
        }
        return true
    }

    /** See [add] — the pure pre-check answers synchronously and honestly. */
    fun edit(id: Long, text: String, label: String, category: String): Boolean {
        val t = text.trim()
        val l = label.trim()
        val c = category.trim()
        if (validate(t, l, c) != null) return false
        if (DrsStore.state.value.myTexts.none { it.id == id }) return false
        val now = System.currentTimeMillis()
        DrsStore.update { s ->
            when (val r = editAt(s, id, t, l, c, now)) {
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
     * One anonymous insertion counter bump (counts only — never WHICH
     * text). Mirrors recordShortcutUse's coalesced path.
     */
    fun recordUse() {
        DrsStore.update { state ->
            state.copy(usage = state.usage.copy(myTextsUses = state.usage.myTextsUses + 1))
        }
    }
}
