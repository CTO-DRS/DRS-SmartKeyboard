/*
 * Copyright (C) 2025-2026 The DRS Smart Keyboard Project
 */

package com.drs.smartkeyboard.drs

import com.drs.smartkeyboard.ime.smartbar.quickaction.SmartToolCodes
import com.drs.smartkeyboard.ime.text.key.KeyCode
import com.drs.smartkeyboard.ime.window.ImeWindowSpec
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import java.time.DayOfWeek

/**
 * DRS v1.6.0: the sixth comprehensive round — four new pure text tools
 * (inverse tab/punctuation mappings, edge-only blank trim, word-order
 * reversal), five new catalogue tools with real engine dispatch codes,
 * the two most-used-tile placeholder fixes, the suggestion-accept
 * counter end-to-end, the key-preview scale bounds and the three new
 * daily-stats aggregations. Every behavior is pinned where the engine
 * owns it.
 */
class DrsV160Tests : FunSpec({

    val en = java.util.Locale.US

    // -------------------------------------------------------------
    // Text tools — the four v1.6.0 operations
    // -------------------------------------------------------------

    test("spaces to tabs converts runs of four and roundtrips") {
        DrsTextTools.apply(DrsTextTool.SPACES_TO_TABS, "a    b", en) shouldBe "a\tb"
        // Deterministic splitting of longer runs.
        DrsTextTools.apply(DrsTextTool.SPACES_TO_TABS, "a        b", en) shouldBe "a\t\tb"
        DrsTextTools.apply(DrsTextTool.SPACES_TO_TABS, "a     b", en) shouldBe "a\t b"
        // Shorter runs and single spaces are never touched.
        DrsTextTools.apply(DrsTextTool.SPACES_TO_TABS, "a   b", en) shouldBe "a   b"
        // Roundtrip: TABS_TO_SPACES then SPACES_TO_TABS restores the tabs.
        val tabbed = "func\t()\n\treturn"
        val round = DrsTextTools.apply(
            DrsTextTool.SPACES_TO_TABS,
            DrsTextTools.apply(DrsTextTool.TABS_TO_SPACES, tabbed, en),
            en,
        )
        round shouldBe tabbed
        // Tabs inside the input stay as-is (already tabs).
        DrsTextTools.apply(DrsTextTool.SPACES_TO_TABS, "a\tb", en) shouldBe "a\tb"
    }

    test("western punctuation maps the three Arabic marks only and roundtrips") {
        DrsTextTools.apply(DrsTextTool.TO_WESTERN_PUNCTUATION, "سلام، عالم؛ كيف؟", en) shouldBe
            "سلام, عالم; كيف?"
        // Letters, digits and other punctuation pass through.
        DrsTextTools.apply(DrsTextTool.TO_WESTERN_PUNCTUATION, "abc 123 (…) [!] ة", en) shouldBe
            "abc 123 (…) [!] ة"
        // Roundtrip with the v1.5.0 inverse.
        val latin = "one, two; three?"
        val round = DrsTextTools.apply(
            DrsTextTool.TO_WESTERN_PUNCTUATION,
            DrsTextTools.apply(DrsTextTool.TO_ARABIC_PUNCTUATION, latin, en),
            en,
        )
        round shouldBe latin
    }

    test("trim blank edges removes edge blanks only") {
        val text = "\n\n  \nalpha\n\nbeta\n \n\n"
        // The input ends with a newline, so the trailing newline survives.
        DrsTextTools.apply(DrsTextTool.TRIM_BLANK_EDGES, text, en) shouldBe "alpha\n\nbeta\n"
        // Blank separators inside stay exactly as they are.
        val mid = "a\n\n\nb"
        DrsTextTools.apply(DrsTextTool.TRIM_BLANK_EDGES, mid, en) shouldBe mid
        // A trailing newline survives when content remains.
        DrsTextTools.apply(DrsTextTool.TRIM_BLANK_EDGES, "x\n\n", en) shouldBe "x\n"
        // A text of only blank lines collapses to empty.
        DrsTextTools.apply(DrsTextTool.TRIM_BLANK_EDGES, " \n\t\n", en) shouldBe ""
        // Clean text is untouched.
        DrsTextTools.apply(DrsTextTool.TRIM_BLANK_EDGES, "solo", en) shouldBe "solo"
    }

    test("reverse words flips the word order of every line") {
        DrsTextTools.apply(DrsTextTool.REVERSE_WORDS, "Hello World Foo", en) shouldBe "Foo World Hello"
        // Per-line, blank lines stay blank, trailing newline preserved.
        DrsTextTools.apply(DrsTextTool.REVERSE_WORDS, "one two\n\nthree four\n", en) shouldBe
            "two one\n\nfour three\n"
        // Arabic words behave the same.
        DrsTextTools.apply(DrsTextTool.REVERSE_WORDS, "مرحبا بالعالم", en) shouldBe "بالعالم مرحبا"
        // A single word is its own reversal.
        DrsTextTools.apply(DrsTextTool.REVERSE_WORDS, "solo", en) shouldBe "solo"
        // Double application restores the original word order.
        val words = "alpha beta gamma"
        val once = DrsTextTools.apply(DrsTextTool.REVERSE_WORDS, words, en)
        DrsTextTools.apply(DrsTextTool.REVERSE_WORDS, once, en) shouldBe words
    }

    // -------------------------------------------------------------
    // Catalogue — the five v1.6.0 tools
    // -------------------------------------------------------------

    test("v1.6.0 tools exist and dispatch real engine codes") {
        DrsUnifiedTools.byId("clipboard_full_clear")?.code shouldBe
            KeyCode.CLIPBOARD_CLEAR_FULL_HISTORY
        DrsUnifiedTools.byId("prev_language")?.code shouldBe
            KeyCode.IME_PREV_SUBTYPE
        DrsUnifiedTools.byId("one_handed_left")?.code shouldBe
            KeyCode.COMPACT_LAYOUT_TO_LEFT
        DrsUnifiedTools.byId("one_handed_right")?.code shouldBe
            KeyCode.COMPACT_LAYOUT_TO_RIGHT
        DrsUnifiedTools.byId("next_keyboard_app")?.code shouldBe
            KeyCode.SYSTEM_NEXT_INPUT_METHOD
    }

    test("v1.6.0 tools keep the tail-append order contract of the catalogue") {
        // DRS v1.7.0 appended CLIPBOARD_PIN after the v1.6.0 tail, DRS
        // v1.8.0 two more, DRS v1.15.0 three smart panels, DRS
        // v1.19.0 the split/merge pair, the DRS v1.2.0 release the
        // smart numbers panel and the DRS v1.3.0 release the smart
        // clipboard panel, so the
        // v1.6.0 block is now dropLast(11). The contract itself (stable
        // positions, first tool pinned) is unchanged.
        DrsUnifiedTools.ALL.dropLast(11).takeLast(5).map { it.id } shouldBe
            listOf(
                "clipboard_full_clear", "prev_language", "one_handed_left",
                "one_handed_right", "next_keyboard_app",
            )
        DrsUnifiedTools.ALL.first().id shouldBe "emoji"
        // The v1.5.0 tail is still directly ahead of the new tail; DRS
        // v1.15.0 appended three smart panels after the v1.8.0 pair,
        // DRS v1.19.0 the split/merge pair after those and the
        // DRS v1.2.0 release the smart numbers panel after that, the
        // DRS v1.3.0 release the smart clipboard panel after that and
        // the DRS v2.21.0 release the saved-texts panel after that, so
        // the combined tail after the v1.5.0 block is now 16.
        DrsUnifiedTools.ALL.dropLast(16).takeLast(3).map { it.id } shouldBe
            listOf("clipboard_history_clear", "next_language", "resize_mode")
    }

    test("v1.6.0 catalogue and text tools count in the smart-tool stats") {
        SmartToolCodes shouldContain KeyCode.CLIPBOARD_CLEAR_FULL_HISTORY
        SmartToolCodes shouldContain KeyCode.IME_PREV_SUBTYPE
        SmartToolCodes shouldContain KeyCode.COMPACT_LAYOUT_TO_LEFT
        SmartToolCodes shouldContain KeyCode.COMPACT_LAYOUT_TO_RIGHT
        SmartToolCodes shouldContain KeyCode.SYSTEM_NEXT_INPUT_METHOD
        SmartToolCodes shouldContain KeyCode.TEXT_TOOL_SPACES_TO_TABS
        SmartToolCodes shouldContain KeyCode.TEXT_TOOL_TO_WESTERN_PUNCTUATION
        SmartToolCodes shouldContain KeyCode.TEXT_TOOL_TRIM_BLANK_EDGES
        SmartToolCodes shouldContain KeyCode.TEXT_TOOL_REVERSE_WORDS
    }

    // -------------------------------------------------------------
    // Daily stats — suggestion accepts + the three new aggregations
    // -------------------------------------------------------------

    test("suggestion accepts persist through merge, sum and sanity") {
        val today = DrsDailyStats.todayStamp()
        val merged = DrsDailyStats.mergeInto(
            emptyMap(),
            DrsUsageStats(suggestionAccepts = 7L),
        )
        merged[today]?.suggestionAccepts shouldBe 7L
        // An accepts-only delta is NOT zero, so it actually creates a bucket.
        merged.containsKey(today) shouldBe true
        // Sum across buckets.
        val total = DrsDailyStats.sum(merged.values)
        total.suggestionAccepts shouldBe 7L
        // Accepts alone count as activity (the bucket is not "empty").
        with(DrsDailyStats) { merged[today]!!.hasActivity() shouldBe true }
        // Sanity rejects negative counters.
        DrsDailyStats.isSane(mapOf(today to merged[today]!!.copy(suggestionAccepts = -1L))) shouldBe false
        DrsDailyStats.isSane(merged) shouldBe true
    }

    test("missed days and active ratio describe the recorded window") {
        val today = "2026-01-10"
        // Two active days inside a 5-day span (Jan 6..Jan 10) → 3 missed, 40%.
        val stats = mapOf(
            "2026-01-06" to DrsDayStats(day = "2026-01-06", keyPresses = 10),
            "2026-01-10" to DrsDayStats(day = "2026-01-10", keyPresses = 30),
        )
        DrsDailyStats.missedDaysCount(stats, today) shouldBe 3
        DrsDailyStats.activeDayRatioPercent(stats, today) shouldBe 40.0
        // Every recorded day active → 0 missed, 100%.
        val full = mapOf("2026-01-10" to DrsDayStats(day = "2026-01-10", keyPresses = 5))
        DrsDailyStats.missedDaysCount(full, today) shouldBe 0
        DrsDailyStats.activeDayRatioPercent(full, today) shouldBe 100.0
        // No data at all → unknown (null).
        DrsDailyStats.missedDaysCount(emptyMap(), today) shouldBe null
        DrsDailyStats.activeDayRatioPercent(emptyMap(), today) shouldBe null
    }

    test("quietest weekday is the counterpart of busiest weekday") {
        // 2026-01-05 is a Monday, 2026-01-06 a Tuesday, 2026-01-07 a Wednesday.
        val stats = mapOf(
            "2026-01-05" to DrsDayStats(day = "2026-01-05", keyPresses = 50),
            "2026-01-06" to DrsDayStats(day = "2026-01-06", keyPresses = 5),
            "2026-01-07" to DrsDayStats(day = "2026-01-07", keyPresses = 20),
        )
        DrsDailyStats.busiestWeekday(stats) shouldBe DayOfWeek.MONDAY
        DrsDailyStats.quietestWeekday(stats) shouldBe DayOfWeek.TUESDAY
        // Days without key presses never win the title.
        val onlyTools = mapOf(
            "2026-01-05" to DrsDayStats(day = "2026-01-05", toolUses = 3),
        )
        DrsDailyStats.quietestWeekday(onlyTools) shouldBe null
        DrsDailyStats.quietestWeekday(emptyMap()) shouldBe null
    }

    // -------------------------------------------------------------
    // Appearance — the key-preview scale bounds
    // -------------------------------------------------------------

    test("key preview scale clamps into its documented bounds") {
        ImeWindowSpec.sanitizeKeyPreviewScale(0) shouldBe 0.70f
        ImeWindowSpec.sanitizeKeyPreviewScale(70) shouldBe 0.70f
        ImeWindowSpec.sanitizeKeyPreviewScale(100) shouldBe 1.00f
        ImeWindowSpec.sanitizeKeyPreviewScale(200) shouldBe 2.00f
        ImeWindowSpec.sanitizeKeyPreviewScale(9999) shouldBe 2.00f
        // The slider bounds and the default stay coherent.
        ImeWindowSpec.KEY_PREVIEW_SCALE_DEFAULT_PERCENT shouldBe 100
    }
})
