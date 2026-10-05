/*
 * Copyright (C) 2025 The DRS Smart Keyboard Project
 */

package com.drs.smartkeyboard.drs

import com.drs.smartkeyboard.ime.nlp.DrsCorrectionRevert
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe

/**
 * DRS v2.9.0 «التراجع الصادق» — contract tests for backspace-undoes-autocorrect
 * (HeliBoard/OpenBoard heritage): the pure [DrsCorrectionRevert] decisions the
 * integration (KeyboardManager) leans on.
 *
 *  - **arm** refuses the honest no-ops: identical texts (nothing was silently
 *    rewritten), blanks, oversized words — and arms only genuinely different
 *    in-cap words (case-only differences ARE corrections).
 *  - **plan** is self-verifying: the corrected word must sit right before the
 *    cursor behind a bounded non-word tail, with a word boundary in front —
 *    longer words, extra letters typed after the commit, digits in the tail,
 *    and entirely different words all refuse, so a stale arm can never
 *    rewrite unrelated text.
 */
class DrsV2900Tests : FunSpec({

    // ------------------------------------------------------------------
    // 1) arm — deciding whether a silent commit deserves a revert
    // ------------------------------------------------------------------

    test("arm accepts a genuine silent correction") {
        val pending = DrsCorrectionRevert.arm(typed = "helo", committed = "hello").shouldNotBeNull()
        pending.typed shouldBe "helo"
        pending.corrected shouldBe "hello"
    }

    test("arm refuses the honest no-op: committed equals typed") {
        // The top suggestion merely matched what was typed — nothing was
        // silently rewritten, so there is nothing to restore.
        DrsCorrectionRevert.arm(typed = "hello", committed = "hello").shouldBeNull()
    }

    test("arm refuses blank words on either side") {
        DrsCorrectionRevert.arm(typed = "", committed = "hello").shouldBeNull()
        DrsCorrectionRevert.arm(typed = "helo", committed = "").shouldBeNull()
        DrsCorrectionRevert.arm(typed = "   ", committed = "hello").shouldBeNull()
        DrsCorrectionRevert.arm(typed = "helo", committed = "  ").shouldBeNull()
    }

    test("arm refuses oversized words beyond the 32-char cap") {
        val longTyped = "a".repeat(DrsCorrectionRevert.MAX_WORD_LENGTH + 1)
        DrsCorrectionRevert.arm(typed = longTyped, committed = "hello").shouldBeNull()
        val longCommitted = "b".repeat(DrsCorrectionRevert.MAX_WORD_LENGTH + 1)
        DrsCorrectionRevert.arm(typed = "helo", committed = longCommitted).shouldBeNull()
    }

    test("arm accepts words exactly at the cap") {
        val typedAtCap = "a".repeat(DrsCorrectionRevert.MAX_WORD_LENGTH)
        val committedAtCap = "b".repeat(DrsCorrectionRevert.MAX_WORD_LENGTH)
        DrsCorrectionRevert.arm(typed = typedAtCap, committed = committedAtCap).shouldNotBeNull()
    }

    test("arm accepts case-only differences as real corrections") {
        // Typed with shift intent, auto-committed with different casing —
        // HeliBoard restores the typed case; so must the arm.
        val pending = DrsCorrectionRevert.arm(typed = "HELO", committed = "Hello").shouldNotBeNull()
        pending.typed shouldBe "HELO"
    }

    // ------------------------------------------------------------------
    // 2) plan — self-verifying the editor state before any rewrite
    // ------------------------------------------------------------------

    test("plan matches the corrected word behind a single-space tail") {
        // «...hello |» — the space commit; only the word is replaced.
        val pending = DrsCorrectionRevert.Pending(typed = "helo", corrected = "hello")
        val plan = DrsCorrectionRevert.plan(pending, beforeCursor = "Hi there, hello ").shouldNotBeNull()
        plan.replaceStart shouldBe "Hi there, ".length
        plan.replaceEndExclusive shouldBe "Hi there, hello".length
    }

    test("plan matches behind an interword punctuation tail") {
        val pending = DrsCorrectionRevert.Pending(typed = "helo", corrected = "hello")
        DrsCorrectionRevert.plan(pending, beforeCursor = "well hello, ").shouldNotBeNull()
        DrsCorrectionRevert.plan(pending, beforeCursor = "well hello.").shouldNotBeNull()
        DrsCorrectionRevert.plan(pending, beforeCursor = "well hello;").shouldNotBeNull()
    }

    test("plan matches with no tail at all (cursor right after the word)") {
        val pending = DrsCorrectionRevert.Pending(typed = "helo", corrected = "hello")
        val plan = DrsCorrectionRevert.plan(pending, beforeCursor = "hello").shouldNotBeNull()
        plan.replaceStart shouldBe 0
        plan.replaceEndExclusive shouldBe "hello".length
    }

    test("plan matches at the exact two-character tail cap") {
        val pending = DrsCorrectionRevert.Pending(typed = "helo", corrected = "hello")
        // «, » — comma + space, the widest honest interword tail
        DrsCorrectionRevert.plan(pending, beforeCursor = "hello, ").shouldNotBeNull()
        // two plain spaces (double-space commit)
        DrsCorrectionRevert.plan(pending, beforeCursor = "hello  ").shouldNotBeNull()
    }

    test("plan refuses a tail longer than the two-character cap") {
        val pending = DrsCorrectionRevert.Pending(typed = "helo", corrected = "hello")
        // three spaces of drift — the state moved on, refuse honestly
        DrsCorrectionRevert.plan(pending, beforeCursor = "hello   ").shouldBeNull()
    }

    test("plan refuses when the matched word is a suffix of a longer word") {
        val pending = DrsCorrectionRevert.Pending(typed = "helo", corrected = "hello")
        // «Xhello » ends with «hello » but the region belongs to «Xhello» —
        // rewriting it would corrupt the user's word.
        DrsCorrectionRevert.plan(pending, beforeCursor = "Xhello ").shouldBeNull()
        DrsCorrectionRevert.plan(pending, beforeCursor = "123hello ").shouldBeNull()
    }

    test("plan refuses when letters were typed after the commit") {
        val pending = DrsCorrectionRevert.Pending(typed = "helo", corrected = "hello")
        // the user kept typing — the tail is no longer a bounded non-word run
        DrsCorrectionRevert.plan(pending, beforeCursor = "helloX").shouldBeNull()
        DrsCorrectionRevert.plan(pending, beforeCursor = "hello world").shouldBeNull()
    }

    test("plan refuses digits inside the tail") {
        val pending = DrsCorrectionRevert.Pending(typed = "helo", corrected = "hello")
        // «hello 5» — the digit is a word character, not an honest tail
        DrsCorrectionRevert.plan(pending, beforeCursor = "hello 5").shouldBeNull()
    }

    test("plan refuses entirely different text under the cursor") {
        val pending = DrsCorrectionRevert.Pending(typed = "helo", corrected = "hello")
        DrsCorrectionRevert.plan(pending, beforeCursor = "goodbye ").shouldBeNull()
        DrsCorrectionRevert.plan(pending, beforeCursor = "").shouldBeNull()
        DrsCorrectionRevert.plan(pending, beforeCursor = "hi ").shouldBeNull()
    }

    test("plan honors the word boundary guard in front of the word") {
        val pending = DrsCorrectionRevert.Pending(typed = "helo", corrected = "hello")
        // «(hello » — a punctuation boundary in front is honest and matches
        DrsCorrectionRevert.plan(pending, beforeCursor = "(hello ").shouldNotBeNull()
        // «Xhello» with no tail — the boundary guard still refuses
        DrsCorrectionRevert.plan(pending, beforeCursor = "Xhello").shouldBeNull()
    }

    test("plan is case-sensitive: the corrected word must match exactly") {
        val pending = DrsCorrectionRevert.Pending(typed = "helo", corrected = "hello")
        // the editor holds «Hello » but the arm recorded «hello » — refuse
        DrsCorrectionRevert.plan(pending, beforeCursor = "Hello ").shouldBeNull()
        val cased = DrsCorrectionRevert.Pending(typed = "HELO", corrected = "Hello")
        DrsCorrectionRevert.plan(cased, beforeCursor = "Hello ").shouldNotBeNull()
    }

    // ------------------------------------------------------------------
    // 3) the full honest loop: arm → verify → region coordinates
    // ------------------------------------------------------------------

    test("full loop: typed helo, auto-committed hello, space pressed") {
        val pending = DrsCorrectionRevert.arm(typed = "helo", committed = "hello").shouldNotBeNull()
        // the editor now holds «hello » with the cursor after the space
        val editorBeforeCursor = "hello "
        val plan = DrsCorrectionRevert.plan(pending, editorBeforeCursor).shouldNotBeNull()
        plan.replaceStart shouldBe 0
        plan.replaceEndExclusive shouldBe "hello".length
        // the caller replaces [0, 5) with «helo» — the trailing space
        // survives, and the cursor lands right after the restored word
        plan.replaceStart + pending.typed.length shouldBe "helo".length
    }

    test("full loop: mid-sentence with punctuation tail restores in place") {
        val pending = DrsCorrectionRevert.arm(typed = "wrld", committed = "world").shouldNotBeNull()
        val beforeCursor = "hello world, "
        val plan = DrsCorrectionRevert.plan(pending, beforeCursor).shouldNotBeNull()
        plan.replaceStart shouldBe "hello ".length
        plan.replaceEndExclusive shouldBe "hello world".length
        beforeCursor.substring(plan.replaceStart, plan.replaceEndExclusive) shouldBe "world"
    }

    test("full loop: the honest no-op never arms so nothing can fire") {
        // typed word matched the top suggestion exactly — arm refuses, and
        // with no Pending the integration's backspace check never rewrites
        DrsCorrectionRevert.arm(typed = "hi", committed = "hi").shouldBeNull()
    }
})
