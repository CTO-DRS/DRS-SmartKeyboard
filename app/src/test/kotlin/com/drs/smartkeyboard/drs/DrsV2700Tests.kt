/*
 * Copyright (C) 2025-2026 The DRS Smart Keyboard Project
 */

package com.drs.smartkeyboard.drs

import android.view.KeyEvent
import com.drs.smartkeyboard.ime.keyboard.DrsVolumeCursor
import com.drs.smartkeyboard.ime.nlp.DrsMultilingualMerge
import com.drs.smartkeyboard.ime.nlp.DrsSmartPunctuation
import com.drs.smartkeyboard.ime.text.gestures.SwipeAction
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

/**
 * DRS v2.7.0 «مزايا البحث الخارجي» — the external-research round. For the
 * first time the roadmap was driven by READING other keyboards instead of
 * introspecting our own: the feature lists of HeliBoard, FlorisBoard and
 * Simple Keyboard were pulled from their repositories and diffed against
 * an inventory of ours, feature by feature, with an honest finding up
 * front — glide typing (the loudest "gap") is NOT a gap at all: even
 * HeliBoard does not bundle it, because its only implementations are
 * closed-source. Our honest "unavailable" card matches the gold standard.
 *
 * Three REAL gaps survived the diff, all deterministic and offline:
 *  1. smart punctuation (-- → —, Gboard/AOSP heritage),
 *  2. volume-key cursor control (AOSP/OpenBoard heritage, opt-in),
 *  3. multilingual suggestions (HeliBoard heritage, opt-in).
 *
 * Each ships as a pure contract pinned below, so the round's promises
 * hold without a single emulator.
 */
class DrsV2700Tests : FunSpec({

    // -------------------------------------------------------------
    // DrsSmartPunctuation — the em-dash matcher (الشرطة الطويلة الصادقة)
    // -------------------------------------------------------------
    test("a boundary-preceded double hyphen converts to a real em dash") {
        DrsSmartPunctuation.findEmDashTail("hello --") shouldBe 2
        DrsSmartPunctuation.findEmDashTail("--") shouldBe 2 // text start counts as a boundary
        DrsSmartPunctuation.findEmDashTail("a  --") shouldBe 2 // any run of whitespace
        DrsSmartPunctuation.findEmDashTail("\t--") shouldBe 2 // tabs are whitespace too
        // Arabic text: the same rule, no special casing, no locale tricks
        DrsSmartPunctuation.findEmDashTail("مرحبا --") shouldBe 2
    }

    test("a double hyphen glued inside a token never converts") {
        DrsSmartPunctuation.findEmDashTail("a--") shouldBe 0
        DrsSmartPunctuation.findEmDashTail("1--") shouldBe 0
        DrsSmartPunctuation.findEmDashTail("مرحبا--") shouldBe 0
    }

    test("a drawn dash line is never rewritten") {
        // three or more dashes: the user meant a line, leave it intact
        DrsSmartPunctuation.findEmDashTail("---") shouldBe 0
        DrsSmartPunctuation.findEmDashTail("----") shouldBe 0
        DrsSmartPunctuation.findEmDashTail(" -----") shouldBe 0
    }

    test("short or non-matching tails are silently refused") {
        DrsSmartPunctuation.findEmDashTail("") shouldBe 0
        DrsSmartPunctuation.findEmDashTail("-") shouldBe 0
        DrsSmartPunctuation.findEmDashTail("hello -") shouldBe 0
        DrsSmartPunctuation.findEmDashTail("hello -- ") shouldBe 0 // dashes not at the tail
        DrsSmartPunctuation.findEmDashTail("hello ==") shouldBe 0
    }

    test("the committed dash is a true U+2014 em dash, never a stand-in") {
        DrsSmartPunctuation.EM_DASH shouldBe "—"
        DrsSmartPunctuation.EM_DASH.first().code shouldBe 0x2014
        DrsSmartPunctuation.EM_DASH.length shouldBe 1
        DrsSmartPunctuation.MATCH_LENGTH shouldBe 2
    }

    // -------------------------------------------------------------
    // DrsMultilingualMerge — round-robin, deduped, capped (الصف المختلط)
    // -------------------------------------------------------------
    test("candidates interleave round-robin with the active language first") {
        val merged = DrsMultilingualMerge.interleave(
            base = listOf("b1", "b2", "b3"),
            extras = listOf(listOf("x1", "x2"), listOf("y1", "y2")),
            dedupKey = { it },
        )
        merged shouldBe listOf("b1", "x1", "y1", "b2", "x2", "y2", "b3")
    }

    test("duplicates are removed case-insensitively and the base outranks extras") {
        val merged = DrsMultilingualMerge.interleave(
            base = listOf("hello"),
            extras = listOf(listOf("Hello", "world")),
            dedupKey = { it.lowercase() },
        )
        merged shouldBe listOf("hello", "world")
    }

    test("ragged extras interleave without dead slots") {
        val merged = DrsMultilingualMerge.interleave(
            base = listOf("b1", "b2"),
            extras = listOf(listOf("x1"), listOf("y1", "y2", "y3")),
            dedupKey = { it },
        )
        merged shouldBe listOf("b1", "x1", "y1", "b2", "y2", "y3")
    }

    test("an exhausted or absent extra language never starves the row") {
        // empty base: extras still fill the row
        DrsMultilingualMerge.interleave(
            base = emptyList(),
            extras = listOf(listOf("x1", "x2")),
            dedupKey = { it },
        ) shouldBe listOf("x1", "x2")
        // empty extras: the base row passes through untouched
        DrsMultilingualMerge.interleave(
            base = listOf("b1", "b2"),
            extras = emptyList(),
            dedupKey = { it },
        ) shouldBe listOf("b1", "b2")
        // all empty: nothing, and no infinite loop
        DrsMultilingualMerge.interleave(
            base = emptyList<String>(),
            extras = emptyList(),
            dedupKey = { it },
        ) shouldBe emptyList()
    }

    test("the merged row is capped and the cap keeps a deterministic prefix") {
        val merged = DrsMultilingualMerge.interleave(
            base = listOf("b1", "b2", "b3", "b4", "b5"),
            extras = listOf(listOf("x1", "x2", "x3", "x4", "x5")),
            dedupKey = { it },
        )
        merged.size shouldBe DrsMultilingualMerge.MAX_MERGED_CANDIDATES
        merged.first() shouldBe "b1" // the active language always leads
        DrsMultilingualMerge.interleave(listOf("a"), emptyList(), dedupKey = { it }, cap = 0) shouldBe emptyList()
        DrsMultilingualMerge.interleave(listOf("a"), emptyList(), dedupKey = { it }, cap = -3) shouldBe emptyList()
    }

    test("the merge bounds are part of the contract") {
        DrsMultilingualMerge.MAX_MERGED_CANDIDATES shouldBe 10
        DrsMultilingualMerge.MAX_EXTRA_SUBTYPES shouldBe 2
    }

    // -------------------------------------------------------------
    // DrsVolumeCursor — only the two volume keys speak (مفاتيح الصوت)
    // -------------------------------------------------------------
    test("volume up/down map onto the board's own cursor language") {
        DrsVolumeCursor.actionFor(KeyEvent.KEYCODE_VOLUME_UP) shouldBe SwipeAction.MOVE_CURSOR_UP
        DrsVolumeCursor.actionFor(KeyEvent.KEYCODE_VOLUME_DOWN) shouldBe SwipeAction.MOVE_CURSOR_DOWN
    }

    test("every other key falls through untouched") {
        DrsVolumeCursor.actionFor(KeyEvent.KEYCODE_BACK) shouldBe null
        DrsVolumeCursor.actionFor(KeyEvent.KEYCODE_MENU) shouldBe null
        DrsVolumeCursor.actionFor(KeyEvent.KEYCODE_SPACE) shouldBe null
        DrsVolumeCursor.actionFor(0) shouldBe null
        DrsVolumeCursor.actionFor(-1) shouldBe null
    }
})
