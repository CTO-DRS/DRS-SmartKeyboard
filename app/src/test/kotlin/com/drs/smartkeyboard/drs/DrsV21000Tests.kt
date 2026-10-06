/*
 * Copyright (C) 2025 The DRS Smart Keyboard Project
 */

package com.drs.smartkeyboard.drs

import com.drs.smartkeyboard.ime.nlp.DrsEditHistoryContract
import com.drs.smartkeyboard.ime.nlp.DrsEditHistoryStore
import com.drs.smartkeyboard.ime.nlp.DrsEditWindow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe

/**
 * DRS v2.10.0 «التراجع والإعادة المحليان الصادقان» — contract tests for the
 * deterministic local edit history the strip's UNDO/REDO tools now apply
 * themselves (instead of only delegating Ctrl+Z to the host app).
 *
 *  - **record** derives a minimal reversible patch from cursor-anchored
 *    window snapshots — inserts, deletes, composer rewrites, field-boundary
 *    edits — and refuses the honest no-ops plus every barrier it cannot
 *    prove fully enclosed (radius-truncated edges mid-field).
 *  - **store** enforces the stack discipline: typing kills redoability,
 *    undo/redo park entries on the opposite stack, verification failure
 *    clears everything, and the capacity cap drops the oldest entry.
 *  - **surgeon's oath**: every simulated application must reproduce the
 *    exact expected text — the history never guesses.
 */
class DrsV21000Tests : FunSpec({

    // Mirrors the editor capture: window clamped at the field boundaries.
    fun win(text: String, cursor: Int): DrsEditWindow {
        val from = (cursor - DrsEditHistoryContract.WINDOW_RADIUS).coerceAtLeast(0)
        val to = (cursor + DrsEditHistoryContract.WINDOW_RADIUS).coerceAtMost(text.length)
        return DrsEditWindow(
            cursor = cursor,
            before = text.substring(from, cursor),
            after = text.substring(cursor, to),
        )
    }

    /** Simulates the keyboard surgery: select range, commit replacement. */
    fun apply(text: String, start: Int, endExclusive: Int, replacement: String): String =
        text.replaceRange(start, endExclusive, replacement)

    // ------------------------------------------------------------------
    // 1) record — deriving a reversible patch from two snapshots
    // ------------------------------------------------------------------

    test("record tracks a mid-field insert at the exact absolute position") {
        val pre = win("hello world", cursor = 5)
        val post = win("hellox world", cursor = 6)
        val entry = DrsEditHistoryContract.record(pre, post).shouldNotBeNull()
        entry.patch.start shouldBe 5
        entry.patch.endExclusive shouldBe 5
        entry.patch.original shouldBe ""
        entry.patch.replacement shouldBe "x"
        // record patch = redo direction → lands on the POST cursor.
        entry.patch.cursorAfterApply shouldBe 6
    }

    test("record tracks a backspace deletion with the shifted right side intact") {
        val pre = win("hello world", cursor = 5)
        val post = win("hell world", cursor = 4)
        val entry = DrsEditHistoryContract.record(pre, post).shouldNotBeNull()
        entry.patch.start shouldBe 4
        entry.patch.endExclusive shouldBe 5
        entry.patch.original shouldBe "o"
        entry.patch.replacement shouldBe ""
        entry.patch.cursorAfterApply shouldBe 4
    }

    test("record tracks a forward deletion without conflating it with a replacement") {
        // "AB|CD" forward-delete "C" → "AB|D". Comparing absolute offsets
        // would align "D" over "C" and invent a wrong patch; the cursor-
        // anchored string diff must see the shift for what it is.
        val pre = win("ABCD", cursor = 2)
        val post = win("ABD", cursor = 2)
        val entry = DrsEditHistoryContract.record(pre, post).shouldNotBeNull()
        entry.patch.original shouldBe "C"
        entry.patch.replacement shouldBe ""
        entry.patch.start shouldBe 2
    }

    test("record tracks a composer rewrite (replace letters before the cursor)") {
        // SHARK2-style path: rm 2 chars, insert "x" — "hell|o" → "hex|o".
        val pre = win("hello", cursor = 4)
        val post = win("hexo", cursor = 3)
        val entry = DrsEditHistoryContract.record(pre, post).shouldNotBeNull()
        entry.patch.original shouldBe "ll"
        entry.patch.replacement shouldBe "x"
        apply("hello", entry.patch.start, entry.patch.endExclusive, entry.patch.replacement) shouldBe "hexo"
    }

    test("record allows an edit at the field start edge") {
        // p == 0 here, but both windows are truncated by the FIELD start —
        // nothing exists beyond them, so the change is provably enclosed.
        val pre = win("abc", cursor = 0)
        val post = win("xabc", cursor = 1)
        val entry = DrsEditHistoryContract.record(pre, post).shouldNotBeNull()
        entry.patch.start shouldBe 0
        entry.patch.original shouldBe ""
        entry.patch.replacement shouldBe "x"
    }

    test("record allows an edit at the field end edge") {
        // s == 0 with both windows truncated by the FIELD end.
        val pre = win("abc", cursor = 3)
        val post = win("abcx", cursor = 4)
        val entry = DrsEditHistoryContract.record(pre, post).shouldNotBeNull()
        entry.patch.original shouldBe ""
        entry.patch.replacement shouldBe "x"
        entry.patch.cursorAfterApply shouldBe 4
    }

    test("record refuses the honest no-op: identical windows") {
        DrsEditHistoryContract.record(win("same", 2), win("same", 2)).shouldBeNull()
    }

    test("record refuses a pure cursor move: same text, different cursor") {
        DrsEditHistoryContract.record(win("same text", 2), win("same text", 5)).shouldBeNull()
    }

    test("record refuses a huge mid-field paste that swallows both window edges") {
        // Both windows are radius-truncated (length == RADIUS) and fully
        // different — the change cannot be proven enclosed. A barrier.
        val pre = win("a".repeat(300), cursor = 150)
        val post = win("a".repeat(40) + "PASTE".repeat(60) + "a".repeat(40), cursor = 190)
        DrsEditHistoryContract.record(pre, post).shouldBeNull()
    }

    test("record refuses a change reaching past the left edge mid-field") {
        // Left edge differs (p == 0) while both windows are radius-truncated:
        // whatever starts before the window cannot be described — barrier.
        val base = "x".repeat(200)
        val pre = win(base, cursor = 150)
        val changed = base.take(52) + "Y" + base.drop(53) // one char inside window
        val post = win(changed, cursor = 150)
        DrsEditHistoryContract.record(pre, post).shouldBeNull()
    }

    test("record keeps surrogate pairs whole across the patch boundary") {
        // 😀 is two UTF-16 units; deleting it must yield a patch whose
        // original is the intact pair, and applying must reproduce the text.
        val pre = win("a😀b", cursor = 3)
        val post = win("ab", cursor = 1)
        val entry = DrsEditHistoryContract.record(pre, post).shouldNotBeNull()
        entry.patch.original shouldBe "😀"
        apply("a😀b", entry.patch.start, entry.patch.endExclusive, entry.patch.replacement) shouldBe "ab"
    }

    test("record tracks Arabic text with exact code-unit coordinates") {
        val pre = win("مرحبا بالعالم", cursor = 5)
        val post = win("مرحباً بالعالم", cursor = 6)
        val entry = DrsEditHistoryContract.record(pre, post).shouldNotBeNull()
        entry.patch.original shouldBe ""
        entry.patch.replacement shouldBe "ً"
        apply("مرحبا بالعالم", entry.patch.start, entry.patch.endExclusive, entry.patch.replacement) shouldBe "مرحباً بالعالم"
    }

    // ------------------------------------------------------------------
    // 2) store — stack discipline and the surgeon's verification
    // ------------------------------------------------------------------

    test("undo restores the pre-mutation text and parks the entry for redo") {
        val store = DrsEditHistoryStore()
        val entry = DrsEditHistoryContract.record(win("hello world", 5), win("hellox world", 6))
            .shouldNotBeNull()
        store.record(entry)
        store.canUndo shouldBe true
        store.canRedo shouldBe false

        val patch = store.undo(win("hellox world", 6)).shouldNotBeNull()
        patch.text shouldBe ""
        patch.cursorAfterApply shouldBe 5
        apply("hellox world", patch.start, patch.endExclusive, patch.text) shouldBe "hello world"
        store.canUndo shouldBe false
        store.canRedo shouldBe true
    }

    test("redo reapplies the mutation and returns the entry to the undo stack") {
        val store = DrsEditHistoryStore()
        store.record(DrsEditHistoryContract.record(win("hello world", 5), win("hellox world", 6)))
        store.undo(win("hellox world", 6))
        val patch = store.redo(win("hello world", 5)).shouldNotBeNull()
        apply("hello world", patch.start, patch.endExclusive, patch.text) shouldBe "hellox world"
        store.canUndo shouldBe true
        store.canRedo shouldBe false
    }

    test("a fresh edit after an undo honestly kills redoability") {
        val store = DrsEditHistoryStore()
        store.record(DrsEditHistoryContract.record(win("one", 3), win("onetwo", 6)))
        store.undo(win("onetwo", 6))
        store.canRedo shouldBe true
        store.record(DrsEditHistoryContract.record(win("onetwo", 6), win("onetwothree", 11)))
        store.canRedo shouldBe false
    }

    test("undo on an empty history is an honest null") {
        DrsEditHistoryStore().undo(win("anything", 4)).shouldBeNull()
    }

    test("redo with nothing to redo is an honest null") {
        DrsEditHistoryStore().redo(win("anything", 4)).shouldBeNull()
    }

    test("verification failure clears the whole history instead of guessing") {
        val store = DrsEditHistoryStore()
        store.record(DrsEditHistoryContract.record(win("hello world", 5), win("hellox world", 6)))
        // The field drifted: the live window no longer matches the recorded
        // after-state. The history must refuse AND wipe itself.
        store.undo(win("hello DIFFERENT world", 6)).shouldBeNull()
        store.canUndo shouldBe false
        store.canRedo shouldBe false
    }

    test("cursor moved elsewhere refuses undo without clearing the history") {
        // A cursor-only difference means the text is untouched — the entry
        // stays parked; returning to the recorded position restores undo.
        val store = DrsEditHistoryStore()
        store.record(DrsEditHistoryContract.record(win("hello world", 5), win("hellox world", 6)))
        store.undo(win("hellox world", 2)).shouldBeNull()
        store.canUndo shouldBe true
        store.undo(win("hellox world", 6)).shouldNotBeNull()
    }

    test("verification failure on redo clears the redo stack") {
        val store = DrsEditHistoryStore()
        store.record(DrsEditHistoryContract.record(win("hello world", 5), win("hellox world", 6)))
        store.undo(win("hellox world", 6))
        store.redo(win("different text", 5)).shouldBeNull()
        store.canRedo shouldBe false
        store.canUndo shouldBe false
    }

    test("the capacity cap drops the oldest entry honestly") {
        val store = DrsEditHistoryStore(capacity = 3)
        var text = ""
        repeat(5) { i ->
            val pre = win(text, text.length)
            text += i.toString()
            store.record(DrsEditHistoryContract.record(pre, win(text, text.length)))
        }
        store.size shouldBe 3
        // The two oldest edits ("0", "1") are gone: only three undos land.
        var current = text
        repeat(3) {
            val patch = store.undo(win(current, current.length)).shouldNotBeNull()
            current = apply(current, patch.start, patch.endExclusive, patch.text)
        }
        current shouldBe "01"
        store.undo(win(current, current.length)).shouldBeNull()
    }

    test("a full type-undo-redo cycle round-trips the exact text") {
        val store = DrsEditHistoryStore()
        var text = ""
        val edits = listOf("مرحبا", "،", " كيف حالك")
        for (fragment in edits) {
            val pre = win(text, text.length)
            text += fragment
            store.record(DrsEditHistoryContract.record(pre, win(text, text.length)))
        }
        text shouldBe "مرحبا، كيف حالك"
        repeat(3) {
            val patch = store.undo(win(text, text.length)).shouldNotBeNull()
            text = apply(text, patch.start, patch.endExclusive, patch.text)
        }
        text shouldBe ""
        repeat(3) {
            val patch = store.redo(win(text, text.length)).shouldNotBeNull()
            text = apply(text, patch.start, patch.endExclusive, patch.text)
        }
        text shouldBe "مرحبا، كيف حالك"
    }

    test("clear empties both stacks unconditionally") {
        val store = DrsEditHistoryStore()
        store.record(DrsEditHistoryContract.record(win("a", 1), win("ab", 2)))
        store.undo(win("ab", 2))
        store.clear()
        store.canUndo shouldBe false
        store.canRedo shouldBe false
    }
})
