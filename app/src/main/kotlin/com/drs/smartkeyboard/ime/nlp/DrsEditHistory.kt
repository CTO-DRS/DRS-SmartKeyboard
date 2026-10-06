/*
 * Copyright (C) 2024-2025 The DRS Smart Keyboard Project
 */

package com.drs.smartkeyboard.ime.nlp

/**
 * DRS v2.10.0 — «التراجع والإعادة المحليان الصادقان».
 *
 * A deterministic, purely local, self-verifying edit history for the text
 * the keyboard itself commits or deletes. Until this round, the UNDO/REDO
 * strip tools only delegated Ctrl+Z / Ctrl+Shift+Z to the host app — they
 * silently did nothing in hosts that implement no undo (chat inputs, search
 * boxes, web views). This contract gives the keyboard its own bounded,
 * verified history that works everywhere, while NEVER rewriting text it
 * cannot see exactly as it expects to find it.
 *
 * Doctrine:
 *  - No AI, no cloud, no guessing: the state is a fixed-radius text window
 *    around the cursor, captured before and after each keyboard mutation.
 *  - Self-verification before every application: the live editor window must
 *    equal the recorded window, byte for byte, at the recorded cursor. Text
 *    drift clears the history honestly; a pure cursor move merely refuses.
 *  - Fail-closed barriers: a mutation whose change cannot be proven fully
 *    enclosed inside the window pair (huge pastes, changes reaching past the
 *    snapshot radius) records nothing — the tool falls back to the host.
 *  - Bounded memory: at most [DrsEditHistoryStore.DEFAULT_CAPACITY] entries;
 *    the oldest is dropped honestly when the cap is exceeded.
 *
 * Geometry (why the diff runs on cursor-anchored strings): a deletion shifts
 * the text after the cursor leftwards, so comparing the two states at ABSOLUTE
 * offsets would align different characters and invent a replacement. Diffing
 * the window strings `before + after` — each anchored at its own cursor —
 * lets the shared prefix/suffix absorb the shift naturally, and the resulting
 * spans are then mapped back to absolute coordinates per window.
 *
 * Dual-range patches (why a patch carries both coordinate systems): an INSERT
 * occupies an empty range [5, 5) in the pre-state but [5, 6) in the post-state.
 * Undoing it must therefore delete [5, 6) — the record patch (redo direction)
 * targets the pre-state, and [DrsEditHistoryContract.undoPatch] retargets the
 * same surgery onto the post-state coordinates.
 *
 * Pure JVM contract — no Android imports — so the whole doctrine is unit
 * testable without an emulator (DrsV21000Tests).
 */

/**
 * A fixed-radius snapshot of the editor text around the cursor.
 *
 * @property cursor absolute cursor offset in the field (cursor mode only).
 * @property before up to [DrsEditHistoryContract.WINDOW_RADIUS] characters
 *   immediately before the cursor ("" when the field starts there).
 * @property after up to [DrsEditHistoryContract.WINDOW_RADIUS] characters
 *   immediately after the cursor ("" when the field ends there).
 */
data class DrsEditWindow(
    val cursor: Int,
    val before: String,
    val after: String,
) {
    val absoluteStart: Int get() = cursor - before.length
    val absoluteEnd: Int get() = cursor + after.length

    /** The full window text in cursor-anchored order (before + after). */
    val joined: String get() = before + after

    /** True when the left view was truncated by the field start, not radius. */
    val startsAtFieldStart: Boolean get() = before.length < RADIUS

    /** True when the right view was truncated by the field end, not radius. */
    val endsAtFieldEnd: Boolean get() = after.length < RADIUS

    internal companion object {
        internal const val RADIUS: Int = DrsEditHistoryContract.WINDOW_RADIUS
    }
}

/**
 * One reversible text surgery, expressed per application direction.
 *
 * @property start absolute start of the range replaced WHEN THIS PATCH APPLIES.
 * @property endExclusive absolute end (exclusive) of the applied range.
 * @property text committed by the application, replacing [start, endExclusive).
 * @property cursorAfterApply where the cursor must be placed once applied —
 *   the pre-mutation cursor for undos, the post-mutation cursor for redos.
 * @property original text the mutation had removed (provenance for tests).
 * @property replacement text the mutation had inserted (provenance).
 */
data class DrsEditPatch(
    val start: Int,
    val endExclusive: Int,
    val text: String,
    val cursorAfterApply: Int,
    val original: String,
    val replacement: String,
)

/** One recorded mutation with both snapshots, serving undo and redo alike. */
data class DrsEditEntry(
    val beforeWindow: DrsEditWindow,
    val afterWindow: DrsEditWindow,
    val patch: DrsEditPatch,
)

object DrsEditHistoryContract {

    /** Fixed snapshot radius — comfortably above word-delete distances. */
    const val WINDOW_RADIUS: Int = 96

    /**
     * Pure recording step: derive the reversible patch from the pre/post
     * snapshots, or return null when the pair must NOT be tracked:
     *  - identical snapshots (honest no-op),
     *  - the change touching a radius-truncated window edge, where this pair
     *    cannot prove the change is fully enclosed (a barrier, never a guess).
     */
    fun record(pre: DrsEditWindow, post: DrsEditWindow): DrsEditEntry? {
        if (!pre.isValid() || !post.isValid()) return null
        if (pre == post) return null

        val preText = pre.joined
        val postText = post.joined
        if (preText == postText) return null

        // Common prefix/suffix over the cursor-anchored strings; the middle
        // is the minimal changed span, fully described by both windows.
        var p = 0
        val minLen = minOf(preText.length, postText.length)
        while (p < minLen && preText[p] == postText[p]) p++
        var s = 0
        while (s < minLen - p && preText[preText.length - 1 - s] == postText[postText.length - 1 - s]) s++

        // Never cut a surrogate pair in half: pull the boundary onto the
        // pair's high side so selection/commit stay on code point edges.
        if (p > 0 && p < preText.length && p < postText.length &&
            Character.isHighSurrogate(preText[p - 1]) && Character.isLowSurrogate(preText[p])
        ) {
            p--
        }
        if (s > 0 && s < preText.length && s < postText.length &&
            Character.isLowSurrogate(preText[preText.length - s]) &&
            Character.isHighSurrogate(preText[preText.length - 1 - s])
        ) {
            s++
        }

        // Barrier rule. p == 0 means the first window characters already
        // differ — the change may reach past the left edge of a radius-
        // truncated window. That is only provably safe when both windows
        // were truncated by the FIELD start (nothing exists beyond them).
        // The same logic guards the right edge with s == 0.
        if (p == 0 && !(pre.startsAtFieldStart && post.startsAtFieldStart)) return null
        if (s == 0 && !(pre.endsAtFieldEnd && post.endsAtFieldEnd)) return null

        val preStart = pre.absoluteStart + p
        val preEnd = pre.absoluteStart + preText.length - s
        val postStart = post.absoluteStart + p
        val postEnd = post.absoluteStart + postText.length - s
        val original = preText.substring(p, preText.length - s)
        val replacement = postText.substring(p, postText.length - s)

        val entry = DrsEditEntry(
            beforeWindow = pre,
            afterWindow = post,
            // The record patch is the REDO direction: reapply the replacement
            // over the pre-state range and land on the post cursor.
            patch = DrsEditPatch(
                start = preStart,
                endExclusive = preEnd,
                text = replacement,
                cursorAfterApply = post.cursor,
                original = original,
                replacement = replacement,
            ),
        )
        return entry
    }

    /**
     * The undo patch: retargeted onto the POST-state coordinates (where the
     * field actually sits when undo runs), committing back the original text
     * and restoring the pre-mutation cursor. The applied range in post
     * coordinates spans exactly the replacement text: it starts at the same
     * prefix-trimmed offset and is `replacement.length` long — which is what
     * makes undoing an INSERT delete the inserted range instead of no-oping.
     */
    fun undoPatch(entry: DrsEditEntry): DrsEditPatch {
        val undoStart = entry.afterWindow.absoluteStart +
            (entry.patch.start - entry.beforeWindow.absoluteStart)
        return entry.patch.copy(
            start = undoStart,
            endExclusive = undoStart + entry.patch.replacement.length,
            text = entry.patch.original,
            cursorAfterApply = entry.beforeWindow.cursor,
        )
    }

    /** The redo patch: reapply the mutation over the pre-state and land on the post cursor. */
    fun redoPatch(entry: DrsEditEntry): DrsEditPatch =
        entry.patch.copy(cursorAfterApply = entry.afterWindow.cursor)

    private fun DrsEditWindow.isValid(): Boolean = cursor >= 0
}

/**
 * The bounded two-stack history. Stack discipline:
 *  - [record] pushes onto the undo stack and clears the redo stack — typing
 *    after an undo honestly kills redoability;
 *  - [undo] pops the newest entry, verifies the live window against its
 *    after-window, and parks the entry on the redo stack;
 *  - [redo] pops the redo stack, verifies against its before-window, and
 *    parks the entry back on the undo stack;
 *  - text drift clears everything — a history that cannot prove it matches
 *    the field must not be allowed to rewrite it.
 */
class DrsEditHistoryStore(private val capacity: Int = DEFAULT_CAPACITY) {

    init {
        require(capacity >= 1) { "capacity must be >= 1" }
    }

    private val undoStack = ArrayDeque<DrsEditEntry>()
    private val redoStack = ArrayDeque<DrsEditEntry>()

    val size: Int get() = undoStack.size
    val redoSize: Int get() = redoStack.size
    val canUndo: Boolean get() = undoStack.isNotEmpty()
    val canRedo: Boolean get() = redoStack.isNotEmpty()

    /** Pushes a fresh mutation; kills redoability. No-ops on null (barrier). */
    fun record(entry: DrsEditEntry?) {
        if (entry == null) return
        undoStack.addLast(entry)
        while (undoStack.size > capacity) {
            undoStack.removeFirst()
        }
        redoStack.clear()
    }

    /**
     * Verifies the live window against the newest entry's after-window and
     * returns the patch that restores the text as it was BEFORE the mutation.
     * Text drift clears the whole history; a pure cursor move refuses without
     * clearing (returning the cursor re-arms the same entry).
     */
    fun undo(current: DrsEditWindow): DrsEditPatch? {
        val entry = undoStack.lastOrNull() ?: return null
        if (current.joined != entry.afterWindow.joined) {
            clear()
            return null
        }
        if (current.cursor != entry.afterWindow.cursor) return null
        undoStack.removeLast()
        redoStack.addLast(entry)
        return DrsEditHistoryContract.undoPatch(entry)
    }

    /**
     * Verifies the live window against the redo candidate's before-window and
     * returns the patch that reapplies the mutation. Text drift clears the
     * whole history; a pure cursor move refuses without clearing.
     */
    fun redo(current: DrsEditWindow): DrsEditPatch? {
        val entry = redoStack.lastOrNull() ?: return null
        if (current.joined != entry.beforeWindow.joined) {
            clear()
            return null
        }
        if (current.cursor != entry.beforeWindow.cursor) return null
        redoStack.removeLast()
        undoStack.addLast(entry)
        return DrsEditHistoryContract.redoPatch(entry)
    }

    /** Drops the entire history honestly (input restart, raw editors, drift). */
    fun clear() {
        undoStack.clear()
        redoStack.clear()
    }

    companion object {
        const val DEFAULT_CAPACITY: Int = 50
    }
}
