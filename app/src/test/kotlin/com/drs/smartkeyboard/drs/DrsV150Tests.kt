/*
 * Copyright (C) 2025-2026 The DRS Smart Keyboard Project
 */

package com.drs.smartkeyboard.drs

import com.drs.smartkeyboard.R
import com.drs.smartkeyboard.ime.smartbar.quickaction.SmartToolCodes
import com.drs.smartkeyboard.ime.text.key.KeyCode
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import java.time.DayOfWeek
import java.util.Locale

/**
 * DRS v1.5.0: the fifth comprehensive round — eight new pure text tools,
 * the Persian-digit extension of TO_ARABIC_DIGITS, three new daily-stats
 * aggregations, the catalogue's real-code dispatch for the v1.5.0 tools,
 * and the backup schema version ceiling. Every behavior is pinned where
 * the engine owns it: DrsTextTools, DrsDailyStats, DrsUnifiedTools,
 * SmartToolCodes and DrsBackup.validateParsed.
 */
class DrsV150Tests : FunSpec({

    val en = Locale.US

    // -------------------------------------------------------------
    // Text tools — the eight v1.5.0 operations
    // -------------------------------------------------------------

    test("toggle case inverts cased letters and leaves Arabic alone") {
        DrsTextTools.apply(DrsTextTool.TOGGLE_CASE, "Hello World", en) shouldBe "hELLO wORLD"
        DrsTextTools.apply(DrsTextTool.TOGGLE_CASE, "abc123", en) shouldBe "ABC123"
        // Arabic has no case pairs: everything passes through unchanged.
        DrsTextTools.apply(DrsTextTool.TOGGLE_CASE, "مرحبا بالعالم", en) shouldBe "مرحبا بالعالم"
        // Double application is the identity.
        val mixed = "MiXeD 42!"
        val once = DrsTextTools.apply(DrsTextTool.TOGGLE_CASE, mixed, en)
        once shouldBe "mIxEd 42!"
        DrsTextTools.apply(DrsTextTool.TOGGLE_CASE, once, en) shouldBe mixed
    }

    test("Arabic punctuation maps the three Latin sentence marks only") {
        DrsTextTools.apply(DrsTextTool.TO_ARABIC_PUNCTUATION, "سلام, عالم; كيف؟", en) shouldBe
            "سلام، عالم؛ كيف؟"
        // Letters, digits and already-Arabic marks are untouched.
        DrsTextTools.apply(DrsTextTool.TO_ARABIC_PUNCTUATION, "a1?؟,،", en) shouldBe "a1؟؟،،"
        // A Latin ? inside an English sentence is still converted — the
        // tool's purpose IS the mapping.
        DrsTextTools.apply(DrsTextTool.TO_ARABIC_PUNCTUATION, "x.y", en) shouldBe "x.y"
    }

    test("zero-width cleanup strips invisible marks and keeps ZWNJ") {
        val dirty = "أ\u200Bب\u200Dج\u200Eد\u200Fه\uFEFFو\u2066ي\u2069ز"
        val clean = DrsTextTools.apply(DrsTextTool.REMOVE_ZERO_WIDTH, dirty, en)
        // Every invisible mark is gone; the eight letters stay in order.
        clean shouldBe listOf("أ", "ب", "ج", "د", "ه", "و", "ي", "ز").joinToString("")
        // The Arabic half-space (U+200C) is a REAL letter-like character
        // and must survive by design.
        val halfSpace = "می\u200Cخواهم"
        DrsTextTools.apply(DrsTextTool.REMOVE_ZERO_WIDTH, halfSpace, en) shouldBe halfSpace
        // Plain text is returned unchanged (same content).
        DrsTextTools.apply(DrsTextTool.REMOVE_ZERO_WIDTH, "سلام", en) shouldBe "سلام"
    }

    test("tabs to spaces converts every tab to four real spaces") {
        DrsTextTools.apply(DrsTextTool.TABS_TO_SPACES, "a\tb", en) shouldBe "a    b"
        DrsTextTools.apply(DrsTextTool.TABS_TO_SPACES, "\t\ta", en) shouldBe "        a"
        // Newlines and plain spaces pass through untouched.
        DrsTextTools.apply(DrsTextTool.TABS_TO_SPACES, "a b\nc", en) shouldBe "a b\nc"
    }

    test("sort lines by length is stable and preserves the trailing newline") {
        DrsTextTools.apply(DrsTextTool.SORT_LINES_BY_LENGTH, "ccc\na\ndd", en) shouldBe
            "a\ndd\nccc"
        // Ties keep the original relative order (stable sort).
        DrsTextTools.apply(DrsTextTool.SORT_LINES_BY_LENGTH, "bb\naa\ncc", en) shouldBe
            "bb\naa\ncc"
        // A trailing newline survives exactly like SORT_LINES.
        DrsTextTools.apply(DrsTextTool.SORT_LINES_BY_LENGTH, "bbb\na\n", en) shouldBe
            "a\nbbb\n"
    }

    test("duplicate words are removed across the text with separators kept") {
        DrsTextTools.apply(DrsTextTool.REMOVE_DUPLICATE_WORDS, "سلام عليكم سلام", en) shouldBe
            "سلام عليكم"
        // The whitespace run before a DROPPED word goes with it; kept
        // words keep their original separators verbatim.
        DrsTextTools.apply(DrsTextTool.REMOVE_DUPLICATE_WORDS, "a b\nb\tc a", en) shouldBe
            "a b\tc"
        // Case-sensitive: acronyms are never merged away.
        DrsTextTools.apply(DrsTextTool.REMOVE_DUPLICATE_WORDS, "DRS drs", en) shouldBe
            "DRS drs"
        // Punctuation is part of the word: "سلام," differs from "سلام".
        DrsTextTools.apply(DrsTextTool.REMOVE_DUPLICATE_WORDS, "سلام، سلام", en) shouldBe
            "سلام، سلام"
    }

    test("wrap parens surrounds the text without touching it") {
        DrsTextTools.apply(DrsTextTool.WRAP_PARENS, "ملاحظة", en) shouldBe "(ملاحظة)"
        DrsTextTools.apply(DrsTextTool.WRAP_PARENS, "", en) shouldBe ""
    }

    test("one sentence per line breaks only between sentences") {
        DrsTextTools.apply(
            DrsTextTool.SENTENCE_PER_LINE,
            "صباح الخير. كيف حالك؟ بخير! إلى اللقاء",
            en,
        ) shouldBe "صباح الخير.\nكيف حالك؟\nبخير!\nإلى اللقاء"
        // English enders work too.
        DrsTextTools.apply(DrsTextTool.SENTENCE_PER_LINE, "One. Two? Three!", en) shouldBe
            "One.\nTwo?\nThree!"
        // Already-broken text is idempotent (no double newlines).
        DrsTextTools.apply(DrsTextTool.SENTENCE_PER_LINE, "One.\nTwo.", en) shouldBe "One.\nTwo."
        // A trailing ender with no following text adds no line break.
        DrsTextTools.apply(DrsTextTool.SENTENCE_PER_LINE, "نهاية. ", en) shouldBe "نهاية. "
    }

    test("TO_ARABIC_DIGITS now also maps Persian/Urdu digits") {
        DrsTextTools.apply(DrsTextTool.TO_ARABIC_DIGITS, "123", en) shouldBe "١٢٣"
        // Persian ۰-۹ lands on Arabic-Indic ٠-٩ as well.
        DrsTextTools.apply(DrsTextTool.TO_ARABIC_DIGITS, "۰۱۲", en) shouldBe "٠١٢"
        // Mixed text keeps everything else untouched.
        DrsTextTools.apply(DrsTextTool.TO_ARABIC_DIGITS, "a٤7", en) shouldBe "a٤٧"
    }

    // -------------------------------------------------------------
    // Daily stats — the three v1.5.0 aggregations
    // -------------------------------------------------------------

    test("longest streak finds the best run anywhere, not anchored to today") {
        val stats = mapOf(
            "2026-01-01" to bucketOf("2026-01-01", 10),
            "2026-01-02" to bucketOf("2026-01-02", 10),
            "2026-01-03" to bucketOf("2026-01-03", 10),
            "2026-01-05" to bucketOf("2026-01-05", 10),
        )
        DrsDailyStats.longestStreak(stats) shouldBe 3
        // Empty or all-idle maps have no streak.
        DrsDailyStats.longestStreak(emptyMap()) shouldBe 0
        DrsDailyStats.longestStreak(mapOf("2026-01-01" to bucketOf("2026-01-01", 0))) shouldBe 0
        // A single active day is a streak of one.
        DrsDailyStats.longestStreak(mapOf("2026-01-07" to bucketOf("2026-01-07", 4))) shouldBe 1
    }

    test("busiest weekday aggregates presses per weekday with deterministic ties") {
        // 2026-01-04 is a Sunday, 2026-01-05 a Monday. Sunday totals 45,
        // Monday 50 — Monday wins outright.
        val stats = mapOf(
            "2026-01-04" to bucketOf("2026-01-04", 30),
            "2026-01-05" to bucketOf("2026-01-05", 50),
            "2026-01-11" to bucketOf("2026-01-11", 15),
        )
        DrsDailyStats.busiestWeekday(stats) shouldBe DayOfWeek.MONDAY
        // A perfect tie (Sunday 30+20 = 50 vs Monday 50) resolves
        // deterministically to the FIRST-encountered weekday: Sunday.
        val tied = mapOf(
            "2026-01-04" to bucketOf("2026-01-04", 30),
            "2026-01-05" to bucketOf("2026-01-05", 50),
            "2026-01-11" to bucketOf("2026-01-11", 20),
        )
        DrsDailyStats.busiestWeekday(tied) shouldBe DayOfWeek.SUNDAY
        // No key presses at all -> null (nothing to claim).
        DrsDailyStats.busiestWeekday(
            mapOf("2026-01-04" to bucketOf("2026-01-04", 0)),
        ).shouldBeNull()
        DrsDailyStats.busiestWeekday(emptyMap()).shouldBeNull()
        // Idle days never win the crown.
        DrsDailyStats.busiestWeekday(
            mapOf(
                "2026-01-04" to bucketOf("2026-01-04", 0),
                "2026-01-05" to bucketOf("2026-01-05", 1),
            ),
        ) shouldBe DayOfWeek.MONDAY
    }

    test("recorded span counts from the oldest day through today") {
        val stats = mapOf(
            "2026-09-20" to bucketOf("2026-09-20", 5),
            "2026-09-24" to bucketOf("2026-09-24", 5),
        )
        DrsDailyStats.recordedSpanDays(stats, "2026-09-24") shouldBe 5
        // Nothing recorded -> null; broken today -> null.
        DrsDailyStats.recordedSpanDays(emptyMap(), "2026-09-24").shouldBeNull()
        DrsDailyStats.recordedSpanDays(stats, "not-a-day").shouldBeNull()
    }

    // -------------------------------------------------------------
    // Smart tool codes — v1.5.0 additions count as tool presses
    // -------------------------------------------------------------

    test("v1.5.0 text tools and catalog actions joined the smart-tool set") {
        SmartToolCodes shouldContain KeyCode.TEXT_TOOL_TOGGLE_CASE
        SmartToolCodes shouldContain KeyCode.TEXT_TOOL_TO_ARABIC_PUNCTUATION
        SmartToolCodes shouldContain KeyCode.TEXT_TOOL_REMOVE_ZERO_WIDTH
        SmartToolCodes shouldContain KeyCode.TEXT_TOOL_TABS_TO_SPACES
        SmartToolCodes shouldContain KeyCode.TEXT_TOOL_SORT_LINES_BY_LENGTH
        SmartToolCodes shouldContain KeyCode.TEXT_TOOL_REMOVE_DUPLICATE_WORDS
        SmartToolCodes shouldContain KeyCode.TEXT_TOOL_WRAP_PARENS
        SmartToolCodes shouldContain KeyCode.TEXT_TOOL_SENTENCE_PER_LINE
        SmartToolCodes shouldContain KeyCode.CLIPBOARD_CLEAR_HISTORY
        SmartToolCodes shouldContain KeyCode.IME_NEXT_SUBTYPE
    }

    // -------------------------------------------------------------
    // Backup — schema version ceiling (pure validation)
    // -------------------------------------------------------------

    test("backup validation rejects a state from a newer schema") {
        val newer = DrsState(version = DrsBackup.SUPPORTED_STATE_VERSION + 1)
        DrsBackup.validateParsed(newer) shouldBe
            R.string.drs__diagnostics__backup_error_version_newer
    }

    test("backup validation accepts a current state and rejects bad paths") {
        DrsBackup.validateParsed(DrsState(version = 1)).shouldBeNull()
        val badPath = DrsState(version = 1, userPath = "SOMETHING_ELSE")
        DrsBackup.validateParsed(badPath).shouldNotBeNull()
        // Version 0 (pre-release shape) stays rejected as before.
        DrsBackup.validateParsed(DrsState(version = 0)).shouldNotBeNull()
    }
})

/** Local bucket builder for the stats tests (activity = key presses). */
private fun bucketOf(day: String, keys: Long): DrsDayStats = DrsDayStats(day = day, keyPresses = keys)
