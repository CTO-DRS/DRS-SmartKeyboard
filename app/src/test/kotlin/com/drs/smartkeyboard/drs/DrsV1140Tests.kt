/*
 * Copyright (C) 2025-2026 The DRS Smart Keyboard Project
 */

package com.drs.smartkeyboard.drs

import com.drs.smartkeyboard.ime.clipboard.ClipEditorPopupPolicy
import com.drs.smartkeyboard.ime.clipboard.ClipEditorPopupSize
import com.drs.smartkeyboard.ime.clipboard.ClipEditorPopupSpec
import com.drs.smartkeyboard.ime.clipboard.ClipEditorRoute
import com.drs.smartkeyboard.ime.clipboard.ClipEditorScrim
import com.drs.smartkeyboard.ime.clipboard.ClipLineLayout
import com.drs.smartkeyboard.ime.clipboard.ClipResultJump
import com.drs.smartkeyboard.ime.clipboard.ClipSearchEngine
import com.drs.smartkeyboard.ime.clipboard.ClipSearchResults
import com.drs.smartkeyboard.ime.clipboard.provider.ItemType
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.shouldBe

/**
 * DRS v1.14.0: the comprehensive settings list in the app («قائمة
 * الإعدادات الشاملة») — the pure contracts behind the new settings:
 * the edit-surface routing honoring the user's choice, the popup window
 * size/dimming geometry, and the jump alignment (centered vs pinned).
 * Every default preserves the previous rounds' behavior exactly.
 */
class DrsV1140Tests : FunSpec({

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
    // The edit surface: the user's choice joins the routing
    // -------------------------------------------------------------

    test("a preferred in-panel editor keeps real text in the panel") {
        ClipEditorPopupPolicy.routeFor(ItemType.TEXT, "hello", ClipEditorRoute.IN_PANEL) shouldBe
            ClipEditorRoute.IN_PANEL
    }

    test("a preferred in-panel editor keeps media in the panel too") {
        ClipEditorPopupPolicy.routeFor(ItemType.IMAGE, null, ClipEditorRoute.IN_PANEL) shouldBe
            ClipEditorRoute.IN_PANEL
    }

    test("a preferred popup window still refuses media — honesty is kept") {
        ClipEditorPopupPolicy.routeFor(ItemType.VIDEO, null, ClipEditorRoute.POPUP_WINDOW) shouldBe
            ClipEditorRoute.IN_PANEL
    }

    test("a preferred popup window still refuses the null-text defense") {
        ClipEditorPopupPolicy.routeFor(ItemType.TEXT, null, ClipEditorRoute.POPUP_WINDOW) shouldBe
            ClipEditorRoute.IN_PANEL
    }

    test("a preferred popup window serves real text from the window") {
        ClipEditorPopupPolicy.routeFor(ItemType.TEXT, "hello", ClipEditorRoute.POPUP_WINDOW) shouldBe
            ClipEditorRoute.POPUP_WINDOW
    }

    // -------------------------------------------------------------
    // The popup window geometry: size and dimming as settings
    // -------------------------------------------------------------

    test("the normal size is the pinned v1.11.0 geometry") {
        for (size in ClipEditorPopupSize.entries) {
            val w = ClipEditorPopupSpec.widthFractionFor(size)
            val h = ClipEditorPopupSpec.heightFractionFor(size)
            if (size == ClipEditorPopupSize.NORMAL) {
                w shouldBe ClipEditorPopupSpec.WIDTH_FRACTION
                h shouldBe ClipEditorPopupSpec.HEIGHT_FRACTION
            }
        }
    }

    test("bigger sizes give bigger cards in both axes") {
        // DRS p9 (Q1): the four comparisons used to be bare discarded
        // expressions that could never fail — each is now a real assertion.
        (
            ClipEditorPopupSpec.widthFractionFor(ClipEditorPopupSize.COMPACT) <
                ClipEditorPopupSpec.widthFractionFor(ClipEditorPopupSize.NORMAL)
            ).shouldBeTrue()
        (
            ClipEditorPopupSpec.widthFractionFor(ClipEditorPopupSize.NORMAL) <
                ClipEditorPopupSpec.widthFractionFor(ClipEditorPopupSize.NEARLY_FULL)
            ).shouldBeTrue()
        (
            ClipEditorPopupSpec.heightFractionFor(ClipEditorPopupSize.COMPACT) <
                ClipEditorPopupSpec.heightFractionFor(ClipEditorPopupSize.NORMAL)
            ).shouldBeTrue()
        (
            ClipEditorPopupSpec.heightFractionFor(ClipEditorPopupSize.NORMAL) <
                ClipEditorPopupSpec.heightFractionFor(ClipEditorPopupSize.NEARLY_FULL)
            ).shouldBeTrue()
    }

    test("every size fraction stays inside (0, 1] — the card never leaves the screen") {
        for (size in ClipEditorPopupSize.entries) {
            (ClipEditorPopupSpec.widthFractionFor(size) in 0f..1f) shouldBe true
            (ClipEditorPopupSpec.heightFractionFor(size) in 0f..1f) shouldBe true
        }
    }

    test("the normal scrim is the pinned v1.11.0 dimming and none is transparent") {
        ClipEditorPopupSpec.scrimAlphaFor(ClipEditorScrim.NORMAL) shouldBe ClipEditorPopupSpec.SCRIM_ALPHA
        ClipEditorPopupSpec.scrimAlphaFor(ClipEditorScrim.NONE) shouldBe 0f
    }

    test("stronger scrims dim more, and never fully black out the app") {
        // DRS p9 (Q1): all three ordering comparisons are asserted for real.
        (
            ClipEditorPopupSpec.scrimAlphaFor(ClipEditorScrim.NONE) <
                ClipEditorPopupSpec.scrimAlphaFor(ClipEditorScrim.LIGHT)
            ).shouldBeTrue()
        (
            ClipEditorPopupSpec.scrimAlphaFor(ClipEditorScrim.LIGHT) <
                ClipEditorPopupSpec.scrimAlphaFor(ClipEditorScrim.NORMAL)
            ).shouldBeTrue()
        (
            ClipEditorPopupSpec.scrimAlphaFor(ClipEditorScrim.NORMAL) <
                ClipEditorPopupSpec.scrimAlphaFor(ClipEditorScrim.STRONG)
            ).shouldBeTrue()
        (ClipEditorPopupSpec.scrimAlphaFor(ClipEditorScrim.STRONG) < 1f).shouldBeTrue()
    }

    // -------------------------------------------------------------
    // The jump alignment: centered vs pinned
    // -------------------------------------------------------------

    test("the pinned alignment brings the row head just under the top edge") {
        // Row 4 of 20 uniform rows: top = 48; pinned = 48 - 16.
        val target = ClipResultJump.scrollOffsetFor(
            uniformLayout(20), offset = 45, viewportPx = 96, maxScrollPx = 400, centerRow = false,
        )
        target shouldBe 48 - ClipResultJump.TOP_PEEK_PX
        target shouldBe 32
    }

    test("the pinned alignment still clamps the first row to zero") {
        val target = ClipResultJump.scrollOffsetFor(
            uniformLayout(20), offset = 0, viewportPx = 96, maxScrollPx = 400, centerRow = false,
        )
        target shouldBe 0
    }

    test("the pinned alignment still clamps the last row to the scroll maximum") {
        // Row 19: top = 228, pinned = 212 — beyond maxScroll 180.
        val target = ClipResultJump.scrollOffsetFor(
            uniformLayout(20), offset = 195, viewportPx = 96, maxScrollPx = 180, centerRow = false,
        )
        target shouldBe 180
    }

    test("a taller-than-viewport row pins identically in both alignments") {
        val layout = object : ClipLineLayout {
            override val textLength = 100
            override val rowCount = 10
            override fun rowForOffset(offset: Int) = 7
            override fun rowTop(row: Int) = row * 20
            override fun rowBottom(row: Int) = if (row == 7) row * 20 + 200 else row * 20 + 20
        }
        val pinned = ClipResultJump.scrollOffsetFor(layout, offset = 75, viewportPx = 120, maxScrollPx = 900, centerRow = false)
        val centered = ClipResultJump.scrollOffsetFor(layout, offset = 75, viewportPx = 120, maxScrollPx = 900, centerRow = true)
        pinned shouldBe centered
        pinned shouldBe (7 * 20) - ClipResultJump.TOP_PEEK_PX
    }

    test("with no viewport knowledge both alignments pin the row head") {
        val layout = uniformLayout(20)
        val pinned = ClipResultJump.scrollOffsetFor(layout, offset = 45, viewportPx = 0, maxScrollPx = 400, centerRow = false)
        val centered = ClipResultJump.scrollOffsetFor(layout, offset = 45, viewportPx = 0, maxScrollPx = 400, centerRow = true)
        pinned shouldBe centered
        pinned shouldBe 48 - ClipResultJump.TOP_PEEK_PX
    }

    test("centerRow = true is exactly the default (the v1.13.0 contract unchanged)") {
        val layout = uniformLayout(20)
        for (offset in listOf(0, 1, 45, 100, 150, 199)) {
            val explicit = ClipResultJump.scrollOffsetFor(layout, offset, 96, 400, centerRow = true)
            val defaulted = ClipResultJump.scrollOffsetFor(layout, offset, 96, 400)
            explicit shouldBe defaulted
        }
    }

    test("the honest nulls survive the new parameter") {
        ClipResultJump.scrollOffsetFor(null, offset = 5, viewportPx = 100, maxScrollPx = 400, centerRow = false) shouldBe null
        ClipResultJump.scrollOffsetFor(uniformLayout(10), offset = -1, viewportPx = 100, maxScrollPx = 400, centerRow = false) shouldBe null
        ClipResultJump.scrollOffsetFor(uniformLayout(10, textLength = 100), offset = 999, viewportPx = 100, maxScrollPx = 400, centerRow = false) shouldBe null
    }

    // -------------------------------------------------------------
    // The end-to-end card → pinned-jump handshake
    // -------------------------------------------------------------

    test("every built card hands the pinned jump a valid row-head offset") {
        val text = "alpha needle\nplain middle line\nbeta needle here\ngamma"
        val matches = ClipSearchEngine.findMatches(text, "needle", ignoreCase = false)
        matches.size shouldBe 2
        val cards = ClipSearchResults.buildCards(text, matches)
        val layout = uniformLayout(rowCount = 4, textLength = text.length, rowOf = { offset ->
            text.take(offset).count { it == '\n' }.coerceIn(0, 3)
        })
        for (card in cards) {
            val target = ClipResultJump.scrollOffsetFor(
                layout, card.start, viewportPx = 48, maxScrollPx = 600, centerRow = false,
            )
            val row = card.lineNumber - 1
            target shouldBe (row * 12 - ClipResultJump.TOP_PEEK_PX).coerceIn(0, 600)
        }
        cards[0].lineNumber shouldBe 1
        cards[1].lineNumber shouldBe 3
    }
})
