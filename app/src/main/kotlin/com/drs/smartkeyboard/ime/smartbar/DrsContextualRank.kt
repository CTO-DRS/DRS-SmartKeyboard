/*
 * Copyright (C) 2026 The DRS Smart Keyboard Project
 */

package com.drs.smartkeyboard.ime.smartbar

import com.drs.smartkeyboard.ime.smartbar.quickaction.QuickAction
import com.drs.smartkeyboard.ime.text.key.KeyCode
import com.drs.smartkeyboard.ime.text.keyboard.TextKeyData

/**
 * DRS v2.11.0 «المُرتّب السياقي الصادق» — the honest contextual ranker for
 * the smartbar's quick-action rows (roadmap M1.4).
 *
 * WHAT it does: for a focused field of kind [DrsFieldKind], a closed,
 * hand-audited promotion matrix lifts the actions the USER ALREADY HAS to
 * the front of the row — URL fields surface paste/share/select-all, chat
 * boxes surface insert-date/undo, password fields surface only
 * undo/redo/incognito and NEVER clipboard or voice.
 *
 * The four honesty oaths (each one test-enforced):
 *  1. **No invented tiles** — promotion only applies to actions already
 *     present in the user's arrangement. A missing action is never added;
 *     the matrix is a ranking, not a catalogue dump.
 *  2. **Pure permutation** — the result is always the same multiset in
 *     the same count: front-promoted entries keep their base relative
 *     order, everything else keeps its exact base order. Same tiles,
 *     same row geometry, zero layout flicker.
 *  3. **The user is the base truth** — their manual arrangement is the
 *     order being permuted; nothing is removed, hidden or disabled. Every
 *     tile keeps its own evaluateEnabled verdict (the v2.2.2 oath), so a
 *     promoted tile on an empty clipboard still refuses to lie.
 *  4. **The privacy claw** — password fields never promote clipboard or
 *     voice actions, full stop. The matrix itself is the suppression: no
 *     runtime gate to forget, no branch to regress.
 *
 * Deterministic: same kind + same signals + same base → same order,
 * forever. No AI, no network, no content reads — the signals are the
 * field's declared kind plus two boolean flags (selection exists,
 * clipboard was just written).
 */
object DrsContextualRank {

    /**
     * Momentary editor signals that shape the promotion order. Both are
     * booleans read from the same sources the tiles' own enablement uses —
     * no content, no clipboard text, just states.
     */
    data class Signals(
        val hasSelection: Boolean,
        val clipboardFresh: Boolean,
    ) {
        companion object {
            val NONE = Signals(hasSelection = false, clipboardFresh = false)
        }
    }

    /** How long after a copy/cut the clipboard counts as «fresh» (30s). */
    const val FRESH_CLIPBOARD_WINDOW_MS: Long = 30_000L

    // ------------------------------------------------------------------
    // The closed promotion matrix — every entry is a REAL action the
    // keyboard ships; every list is tiny and hand-audited.
    // ------------------------------------------------------------------

    private val URI_PROMOTIONS = listOf(
        KeyCode.CLIPBOARD_PASTE,
        KeyCode.CLIPBOARD_SHARE,
        KeyCode.CLIPBOARD_SELECT_ALL,
    )

    private val EMAIL_PROMOTIONS = listOf(
        KeyCode.CLIPBOARD_PASTE,
        KeyCode.UNDO,
        KeyCode.REDO,
    )

    private val MESSAGE_PROMOTIONS = listOf(
        KeyCode.INSERT_DATE_TIME,
        KeyCode.UNDO,
        KeyCode.REDO,
    )

    private val SEARCH_FILTER_PROMOTIONS = listOf(
        KeyCode.CLIPBOARD_PASTE,
        KeyCode.CLIPBOARD_SELECT_ALL,
        KeyCode.CLIPBOARD_CLEAR_PRIMARY_CLIP,
    )

    /** Number fields: the smart number panel already owns this context —
     *  the ranker stays honestly silent instead of inventing value. */
    private val NUMBER_PROMOTIONS: List<Int> = emptyList()

    /** Password fields: the privacy claw. ONLY navigation-free, content-free
     *  tools; clipboard and voice are structurally absent. */
    private val PASSWORD_PROMOTIONS = listOf(
        KeyCode.UNDO,
        KeyCode.REDO,
        KeyCode.TOGGLE_INCOGNITO_MODE,
    )

    private val CODE_PROMOTIONS = listOf(
        KeyCode.MOVE_WORD_LEFT,
        KeyCode.MOVE_WORD_RIGHT,
        KeyCode.UNDO,
        KeyCode.REDO,
    )

    private val MULTILINE_PROMOTIONS = listOf(
        KeyCode.MOVE_START_OF_LINE,
        KeyCode.MOVE_END_OF_LINE,
        KeyCode.UNDO,
        KeyCode.REDO,
    )

    /** GENERAL fields: identity ranking — the user's order IS the context. */
    private val GENERAL_PROMOTIONS: List<Int> = emptyList()

    /** Selection boost (any non-password text field): grab/cut first. */
    private val SELECTION_BOOST = listOf(
        KeyCode.CLIPBOARD_COPY,
        KeyCode.CLIPBOARD_CUT,
    )

    /** Fresh-clipboard boost (any non-password text field): the roadmap's
     *  «بعد النسخ يبرز أفعال الحافظة» clause. */
    private val FRESH_CLIP_BOOST = listOf(
        KeyCode.CLIPBOARD_PASTE,
        KeyCode.CLIPBOARD_SHARE,
    )

    /**
     * The ordered promotion keycodes for [kind] under [signals]. Pure and
     * closed: the union of the kind's matrix and (for non-password kinds)
     * the momentary boosts. Password fields get the claw — kind matrix
     * only, boosts structurally unreachable.
     */
    fun promotedCodes(kind: DrsFieldKind, signals: Signals): List<Int> {
        val base = when (kind) {
            DrsFieldKind.URI -> URI_PROMOTIONS
            DrsFieldKind.EMAIL -> EMAIL_PROMOTIONS
            DrsFieldKind.MESSAGE -> MESSAGE_PROMOTIONS
            DrsFieldKind.SEARCH_FILTER -> SEARCH_FILTER_PROMOTIONS
            DrsFieldKind.NUMBER -> NUMBER_PROMOTIONS
            DrsFieldKind.PASSWORD -> PASSWORD_PROMOTIONS
            DrsFieldKind.CODE -> CODE_PROMOTIONS
            DrsFieldKind.MULTILINE -> MULTILINE_PROMOTIONS
            DrsFieldKind.GENERAL -> GENERAL_PROMOTIONS
        }
        if (kind == DrsFieldKind.PASSWORD || kind == DrsFieldKind.GENERAL || kind == DrsFieldKind.NUMBER) {
            return base
        }
        val boosted = ArrayList<Int>(base.size + 4)
        if (signals.hasSelection) boosted += SELECTION_BOOST
        if (signals.clipboardFresh) boosted += FRESH_CLIP_BOOST
        boosted += base
        return boosted.distinct()
    }

    /**
     * Ranks [base] for [kind]/[signals]: promoted actions move to the
     * front (first occurrence wins, base relative order kept among them),
     * every other action keeps its exact base position. A strict
     * permutation — same size, same elements, no additions, no removals.
     */
    fun rank(base: List<QuickAction>, kind: DrsFieldKind, signals: Signals): List<QuickAction> {
        val promote = promotedCodes(kind, signals)
        if (promote.isEmpty() || base.isEmpty()) return base

        val taken = BooleanArray(base.size)
        val front = ArrayList<QuickAction>(promote.size)
        for (code in promote) {
            for (i in base.indices) {
                if (taken[i]) continue
                val action = base[i]
                if (action is QuickAction.InsertKey && action.data.code == code) {
                    front.add(action)
                    taken[i] = true
                    break
                }
            }
        }
        if (front.isEmpty()) return base
        return front + base.filterIndexed { i, _ -> !taken[i] }
    }

    /**
     * The proof helper the tests pin: true when [ranked] is a strict
     * permutation of [base] (same multiset, same size). The ranker must
     * never break this — it is the formal form of «لا كذب، لا بلاطات».
     */
    fun isPermutationOf(base: List<QuickAction>, ranked: List<QuickAction>): Boolean {
        if (base.size != ranked.size) return false
        val counts = HashMap<QuickAction, Int>(base.size)
        for (a in base) counts.merge(a, 1, Int::plus)
        for (a in ranked) {
            val left = counts.merge(a, -1, Int::plus) ?: return false
            if (left < 0) return false
        }
        return counts.values.all { it == 0 }
    }

    /** Mirrors the arrangement's action → keycode projection (pure). */
    fun codeOf(action: QuickAction): Int = (action as? QuickAction.InsertKey)?.data?.code ?: -1

    /** Convenience: the TextKeyData singletons for test fixtures. */
    val FIXTURE_CODES: Set<Int> = setOf(
        TextKeyData.CLIPBOARD_COPY.code,
        TextKeyData.CLIPBOARD_CUT.code,
        TextKeyData.CLIPBOARD_PASTE.code,
        TextKeyData.CLIPBOARD_SHARE.code,
        TextKeyData.CLIPBOARD_SELECT_ALL.code,
        TextKeyData.CLIPBOARD_CLEAR_PRIMARY_CLIP.code,
        TextKeyData.UNDO.code,
        TextKeyData.REDO.code,
        TextKeyData.SETTINGS.code,
        TextKeyData.TOGGLE_INCOGNITO_MODE.code,
        TextKeyData.INSERT_DATE_TIME.code,
        TextKeyData.MOVE_WORD_LEFT.code,
        TextKeyData.MOVE_WORD_RIGHT.code,
        TextKeyData.MOVE_START_OF_LINE.code,
        TextKeyData.MOVE_END_OF_LINE.code,
        TextKeyData.FORWARD_DELETE.code,
    )
}
