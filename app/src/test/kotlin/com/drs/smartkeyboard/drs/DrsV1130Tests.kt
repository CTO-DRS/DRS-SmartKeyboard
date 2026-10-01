/*
 * Copyright (C) 2025-2026 The DRS Smart Keyboard Project
 */

package com.drs.smartkeyboard.drs

import com.drs.smartkeyboard.ime.clipboard.ClipLineLayout
import com.drs.smartkeyboard.ime.clipboard.ClipResultJump
import com.drs.smartkeyboard.ime.clipboard.ClipSearchEngine
import com.drs.smartkeyboard.ime.clipboard.ClipSearchResults
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

/**
 * DRS v1.13.0: the direct line jump contract («اضغط على البطاقة فينتقل
 * مباشرة إلى السطر») — tapping a result card (or walking the matches with
 * the navigation arrows) scrolls the editor straight to the row holding
 * that match. The pure jump math is pinned here against tiny fake line
 * layouts: centering, the taller-than-viewport pinning, both clamps, and
 * the honest nulls. Wrapping-aware rows, no fudge.
 */
class DrsV1130Tests : FunSpec({

    /** A fake laid-out text: uniform rows plus an injectable row lookup. */
    fun uniformLayout(
        rowCount: Int,
        rowHeight: Int = 12,
        textLength: Int = rowCount * 10,
        rowOf: (Int) -> Int = { (it / 10).coerceAtMost(rowCount - 1) },
    ): ClipLineLayout = object : ClipLineLayout {
        override val textLength = textLength
        override val rowCount = rowCount
        override fun rowForOffset(offset: Int) = rowOf(offset).coerceIn(0, rowCount - 1)
        override fun rowTop(row: Int) = row * rowHeight
        override fun rowBottom(row: Int) = row * rowHeight + rowHeight
    }

    // -------------------------------------------------------------
    // The honest nulls
    // -------------------------------------------------------------

    test("no layout yet yields null — the editor simply does not scroll") {
        ClipResultJump.scrollOffsetFor(null, offset = 5, viewportPx = 100, maxScrollPx = 400) shouldBe null
    }

    test("a negative offset is outside the text and yields null") {
        ClipResultJump.scrollOffsetFor(uniformLayout(10), offset = -1, viewportPx = 100, maxScrollPx = 400) shouldBe null
    }

    test("an offset beyond the laid-out text yields null") {
        ClipResultJump.scrollOffsetFor(uniformLayout(10, textLength = 100), offset = 101, viewportPx = 100, maxScrollPx = 400) shouldBe null
    }

    test("an empty layout (no rows) yields null") {
        ClipResultJump.scrollOffsetFor(uniformLayout(0, textLength = 0), offset = 0, viewportPx = 100, maxScrollPx = 0) shouldBe null
    }

    // -------------------------------------------------------------
    // The centering contract
    // -------------------------------------------------------------

    test("a fitting row is centered inside the viewport") {
        // Row 4 of 20 uniform rows: top = 48, height = 12, viewport 96.
        val target = ClipResultJump.scrollOffsetFor(uniformLayout(20), offset = 45, viewportPx = 96, maxScrollPx = 400)
        target shouldBe 48 - (96 - 12) / 2
        target shouldBe 6
    }

    test("the very first row clamps the centering to zero") {
        // Row 0: top = 0, centered target is negative — pinned to 0.
        val target = ClipResultJump.scrollOffsetFor(uniformLayout(20), offset = 0, viewportPx = 96, maxScrollPx = 400)
        target shouldBe 0
    }

    test("the last row clamps the centering to the scroll maximum") {
        // Row 19: top = 228, centered = 204 — beyond maxScroll 180.
        val target = ClipResultJump.scrollOffsetFor(uniformLayout(20), offset = 195, viewportPx = 96, maxScrollPx = 180)
        target shouldBe 180
    }

    // -------------------------------------------------------------
    // The wrapped giant row
    // -------------------------------------------------------------

    test("a taller-than-viewport row pins its head under the top edge") {
        val layout = object : ClipLineLayout {
            override val textLength = 100
            override val rowCount = 10
            override fun rowForOffset(offset: Int) = 7
            // Row 7 is a huge wrapped line: 300..500 (200px tall).
            override fun rowTop(row: Int) = row * 20
            override fun rowBottom(row: Int) = if (row == 7) row * 20 + 200 else row * 20 + 20
        }
        val target = ClipResultJump.scrollOffsetFor(layout, offset = 75, viewportPx = 120, maxScrollPx = 900)
        target shouldBe (7 * 20) - ClipResultJump.TOP_PEEK_PX
        target shouldBe 124
    }

    test("zero viewport knowledge falls back to pinning the row head") {
        val target = ClipResultJump.scrollOffsetFor(uniformLayout(20), offset = 45, viewportPx = 0, maxScrollPx = 400)
        target shouldBe 48 - ClipResultJump.TOP_PEEK_PX
    }

    // -------------------------------------------------------------
    // The degenerate scroll ranges
    // -------------------------------------------------------------

    test("a fully visible text (maxScroll 0) never scrolls") {
        val target = ClipResultJump.scrollOffsetFor(uniformLayout(5), offset = 42, viewportPx = 96, maxScrollPx = 0)
        target shouldBe 0
    }

    test("a negative maxScroll is treated as zero, never as a negative offset") {
        val target = ClipResultJump.scrollOffsetFor(uniformLayout(20), offset = 0, viewportPx = 96, maxScrollPx = -7)
        target shouldBe 0
    }

    test("an out-of-range row verdict from the layout is coerced, not crashed") {
        val layout = uniformLayout(rowCount = 10, rowOf = { 99 })
        val target = ClipResultJump.scrollOffsetFor(layout, offset = 5, viewportPx = 96, maxScrollPx = 400)
        // Row coerced to the last row (9): top = 108, centered = 66.
        target shouldBe 108 - (96 - 12) / 2
    }

    test("the offset at the very end of the text is allowed") {
        val target = ClipResultJump.scrollOffsetFor(uniformLayout(20, textLength = 200), offset = 200, viewportPx = 96, maxScrollPx = 400)
        // Row of the last offset coerced into the last row: top = 228.
        target shouldBe 228 - (96 - 12) / 2
    }

    // -------------------------------------------------------------
    // The end-to-end card → jump handshake
    // -------------------------------------------------------------

    test("every built card hands the jump a valid offset inside the text") {
        val text = "alpha needle\nplain middle line\nbeta needle here\ngamma"
        val matches = ClipSearchEngine.findMatches(text, "needle", ignoreCase = false)
        matches.size shouldBe 2
        val cards = ClipSearchResults.buildCards(text, matches)
        val layout = uniformLayout(rowCount = 4, textLength = text.length, rowOf = { offset ->
            text.take(offset).count { it == '\n' }.coerceIn(0, 3)
        })
        for (card in cards) {
            val target = ClipResultJump.scrollOffsetFor(layout, card.start, viewportPx = 48, maxScrollPx = 600)
            // Row of the card's line: top = (line-1)*12, centered by 18,
            // clamped at zero for the first lines.
            val row = card.lineNumber - 1
            target shouldBe (row * 12 - 18).coerceIn(0, 600)
        }
        cards[1].lineNumber shouldBe 3
    }

    test("the navigation wraps around the same matches the cards card") {
        val text = "hit one\nhit two\nhit three"
        val matches = ClipSearchEngine.findMatches(text, "hit", ignoreCase = false)
        matches.size shouldBe 3
        ClipSearchEngine.nextMatchIndex(matches.size, 2) shouldBe 0
        ClipSearchEngine.prevMatchIndex(matches.size, 0) shouldBe 2
    }
})
