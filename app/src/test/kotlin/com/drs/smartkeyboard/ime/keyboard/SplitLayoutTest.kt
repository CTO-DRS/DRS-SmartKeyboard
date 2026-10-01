/*
 * Copyright (C) 2026 The DRS Smart Keyboard Project
 */

package com.drs.smartkeyboard.ime.keyboard

import io.kotest.core.spec.style.FunSpec
import io.kotest.datatest.withData
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe

class SplitLayoutTest : FunSpec({
    context("splitApplies honors the three modes") {
        withData(
            Triple(SplitMode.AUTO, 400f, false), // portrait phone width: no split
            Triple(SplitMode.AUTO, 559f, false), // just under the AUTO threshold
            Triple(SplitMode.AUTO, 560f, true), // exactly at the AUTO threshold
            Triple(SplitMode.AUTO, 860f, true), // landscape phone / tablet
            Triple(SplitMode.ALWAYS, 320f, true), // ALWAYS splits at any width
            Triple(SplitMode.NEVER, 1200f, false), // NEVER never splits
        ) { (mode, widthDp, expected) ->
            SplitLayout.splitApplies(mode, widthDp) shouldBe expected
        }
    }

    context("gapWidthPx is zero outside the split or for degenerate inputs") {
        withData(
            Triple(SplitMode.NEVER, 1000f, 2.6f),
            Triple(SplitMode.AUTO, 400f, 2.6f), // narrow: AUTO declines
            Triple(SplitMode.ALWAYS, 0f, 2.6f), // no keyboard yet
            Triple(SplitMode.ALWAYS, 1000f, 0f), // degenerate density
            Triple(SplitMode.ALWAYS, -50f, 2.6f), // degenerate width
        ) { (mode, widthPx, density) ->
            SplitLayout.gapWidthPx(mode, widthPx, density) shouldBe 0f
        }
    }

    test("gapWidthPx scales with width and clamps to the dp bounds") {
        val density = 2.6f
        // Mid-size wide screen: 9% of 1200px is between the dp clamps -> raw fraction.
        SplitLayout.gapWidthPx(SplitMode.ALWAYS, 1200f, density) shouldBe (1200f * SplitLayout.GAP_FRACTION)
        // Huge screen: 9% of 4000px exceeds the 64dp max -> clamped down.
        SplitLayout.gapWidthPx(SplitMode.ALWAYS, 4000f, density) shouldBe (SplitLayout.GAP_MAX_DP * density)
        // Narrow width: 9% of 200px (18px) falls below the 24dp min (62.4px) -> clamped up.
        SplitLayout.gapWidthPx(SplitMode.ALWAYS, 200f, density) shouldBe (SplitLayout.GAP_MIN_DP * density)
    }

    context("splitIndexForRow puts the gap at the row midpoint") {
        withData(
            Pair(floatArrayOf(1f, 1f, 1f, 1f), 1), // equal factors: midpoint at index 1
            Pair(floatArrayOf(1f, 1f, 1f), 1), // odd count: 1.5 of 3 reached at index 1
            Pair(floatArrayOf(2f, 1f, 1f, 1f), 1), // wider head: 2+1 = 3 of 5 reaches half (2.5) at index 1
            Pair(floatArrayOf(1f, 1f, 1f, 3f), 2), // wide tail: 3.0 of 6 reached at index 2
            Pair(floatArrayOf(1.5f), 0), // single key: gap degenerates after it
            Pair(floatArrayOf(), -1), // empty row
            Pair(floatArrayOf(0f, 0f, 0f), 2), // degenerate all-zero factors -> last index
        ) { (factors, expected) ->
            SplitLayout.splitIndexForRow(factors) shouldBe expected
        }
    }

    test("splitIndexForRow never returns out of bounds for arbitrary positive factors") {
        for (count in 1..12) {
            val factors = FloatArray(count) { it * 0.37f + 0.5f }
            val idx = SplitLayout.splitIndexForRow(factors)
            (idx >= 0) shouldBe true
            (idx < count) shouldBe true
        }
    }

    test("AUTO threshold constant matches the documented 560dp behavior") {
        SplitLayout.AUTO_MIN_WIDTH_DP shouldBe 560
        SplitMode.entries.shouldHaveSize(3)
    }
})
