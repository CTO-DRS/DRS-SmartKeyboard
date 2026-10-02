/*
 * Copyright (C) 2025-2026 The DRS Smart Keyboard Project
 */

package com.drs.smartkeyboard.drs

import com.drs.smartkeyboard.ime.clipboard.readableTextColor
import com.drs.smartkeyboard.ime.smartbar.quickaction.SmartToolCodes
import com.drs.smartkeyboard.ime.text.key.KeyCode
import com.drs.smartkeyboard.ime.text.keyboard.TextKeyData
import com.drs.smartkeyboard.ime.window.ImeWindowSpec
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotContain
import androidx.compose.ui.graphics.Color

/**
 * DRS v1.7.0: the seventh comprehensive round — the internal-keys fix for
 * the floating/resize tiles plus the never-counting LANGUAGE tool, the
 * IME_SUBTYPE_PICKER alias, the active-clip pin catalogue tool, seven
 * new pure text operations (PDF presentation-form repair, list<->lines,
 * hashtag space removal, emoji stripping, URL encode/decode), the glide
 * trail / emoji visual scales, the context-mode starts pipeline end to
 * end, the backup describe-before-restore preview, the diagnostic report
 * export and the shortcut-template diagnostics check. Every behavior is
 * pinned where the engine owns it.
 */
class DrsV170Tests : FunSpec({

    val en = java.util.Locale.US
    val ar = java.util.Locale("ar")

    // -------------------------------------------------------------
    // Integration fixes — codes resolve to real key data + counting
    // -------------------------------------------------------------

    test("floating and resize toggles resolve as internal keys (tile fix)") {
        // Since v1.4.0/v1.5.0 their usage was counted but the most-used
        // tile silently dropped them (getCodeInfoAsTextKeyData -> null).
        TextKeyData.getCodeInfoAsTextKeyData(KeyCode.TOGGLE_FLOATING_WINDOW)
            .shouldNotBeNull()
        TextKeyData.getCodeInfoAsTextKeyData(KeyCode.TOGGLE_RESIZE_MODE)
            .shouldNotBeNull()
    }

    test("the LANGUAGE tool's SHOW_SUBTYPE_PICKER now counts in the stats") {
        TextKeyData.getCodeInfoAsTextKeyData(KeyCode.SHOW_SUBTYPE_PICKER)
            .shouldNotBeNull()
        SmartToolCodes shouldContain KeyCode.SHOW_SUBTYPE_PICKER
    }

    test("the actions-editor toggle has a registered display identity") {
        SmartToolCodes shouldContain KeyCode.TOGGLE_ACTIONS_EDITOR
        TextKeyData.getCodeInfoAsTextKeyData(KeyCode.TOGGLE_ACTIONS_EDITOR)
            .shouldNotBeNull()
    }

    test("the active-clip pin tool exists end to end") {
        val tool = DrsUnifiedTools.byId("clipboard_pin")
        tool.shouldNotBeNull()
        tool.code shouldBe KeyCode.CLIPBOARD_PIN_ACTIVE
        SmartToolCodes shouldContain KeyCode.CLIPBOARD_PIN_ACTIVE
        TextKeyData.getCodeInfoAsTextKeyData(KeyCode.CLIPBOARD_PIN_ACTIVE)
            .shouldNotBeNull()
        // DRS v1.8.0 appended the quick-actions pair after this tail, and
        // DRS v1.15.0 the three smart panels after that, DRS v1.19.0
        // the split/merge pair after those and the DRS v1.2.0 release
        // the smart numbers panel after that.
        DrsUnifiedTools.ALL.dropLast(8).last().id shouldBe "clipboard_pin"
        DrsUnifiedTools.ALL.first().id shouldBe "emoji"
    }

    // -------------------------------------------------------------
    // Text tools — the seven v1.7.0 operations
    // -------------------------------------------------------------

    test("Arabic presentation forms fold back to base letters") {
        // PDF/web copy: U+FE8D (alef final), U+FE91 (beh initial),
        // U+FEFB (lam-alef ligature), U+FE8E (alef isolated).
        val pdf = "\uFE8D\uFE91" // alef + beh in presentation forms
        DrsTextTools.apply(DrsTextTool.NORMALIZE_ARABIC_FORMS, pdf, en) shouldBe "اب"
        val ligature = "\uFEFB" // لا
        DrsTextTools.apply(DrsTextTool.NORMALIZE_ARABIC_FORMS, ligature, en) shouldBe "لا"
        // Plain text passes through unchanged (idempotent).
        DrsTextTools.apply(DrsTextTool.NORMALIZE_ARABIC_FORMS, "سلام", en) shouldBe "سلام"
    }

    test("list to lines splits Arabic and Latin separators") {
        DrsTextTools.apply(DrsTextTool.SPLIT_TO_LINES, "a, b؛ c،d", en) shouldBe "a\nb\nc\nd"
        // Semicolons split too; spacing is trimmed.
        DrsTextTools.apply(DrsTextTool.SPLIT_TO_LINES, "x ; y", en) shouldBe "x\ny"
    }

    test("lines to list joins with the locale comma and roundtrips") {
        DrsTextTools.apply(DrsTextTool.JOIN_LINES, "a\nb\nc", ar) shouldBe "a، b، c"
        DrsTextTools.apply(DrsTextTool.JOIN_LINES, "a\nb", en) shouldBe "a, b"
        // Blank lines are skipped.
        DrsTextTools.apply(DrsTextTool.JOIN_LINES, "a\n\nb", en) shouldBe "a, b"
        // The established inverse-pair convention holds (modulo trimming).
        val split = DrsTextTools.apply(DrsTextTool.SPLIT_TO_LINES, "واحد، اثنان، ثلاثة", ar)
        split shouldBe "واحد\nاثنان\nثلاثة"
        DrsTextTools.apply(DrsTextTool.JOIN_LINES, split, ar) shouldBe "واحد، اثنان، ثلاثة"
    }

    test("remove all spaces keeps newlines but kills horizontal space") {
        DrsTextTools.apply(DrsTextTool.REMOVE_ALL_SPACES, "# القدس  عربية", en) shouldBe "#القدسعربية"
        // Newlines survive (multi-line text stays line-per-tag).
        DrsTextTools.apply(DrsTextTool.REMOVE_ALL_SPACES, "a b\nc\td", en) shouldBe "ab\ncd"
        // NBSP family is horizontal too.
        DrsTextTools.apply(DrsTextTool.REMOVE_ALL_SPACES, "a\u00A0b", en) shouldBe "ab"
    }

    test("strip emoji removes pictographs and keeps text") {
        DrsTextTools.apply(DrsTextTool.STRIP_EMOJI, "شكرا 🙏👍", en) shouldBe "شكرا "
        // VS16/selector blocks too (sparkles).
        DrsTextTools.apply(DrsTextTool.STRIP_EMOJI, "ok\u2728", en) shouldBe "ok"
        // Letters, digits and punctuation are never touched.
        DrsTextTools.apply(DrsTextTool.STRIP_EMOJI, "Hi 123!", ar) shouldBe "Hi 123!"
    }

    test("URL encode and decode roundtrip Arabic links") {
        val encoded = DrsTextTools.apply(DrsTextTool.URL_ENCODE, "سلام 123", en)
        encoded shouldBe "%D8%B3%D9%84%D8%A7%D9%85%20123"
        DrsTextTools.apply(DrsTextTool.URL_DECODE, encoded, en) shouldBe "سلام 123"
        // Spaces become %20, not '+' (RFC 3986 paths).
        encoded shouldNotContain "+"
        // Malformed sequences return the input untouched (never throws).
        DrsTextTools.apply(DrsTextTool.URL_DECODE, "%ZZ%E0%80", en) shouldBe "%ZZ%E0%80"
    }

    // -------------------------------------------------------------
    // Visual scales — the glide trail and emoji bounds
    // -------------------------------------------------------------

    test("glide trail scale clamps to the 70-200 band") {
        ImeWindowSpec.sanitizeGlideTrailScale(50) shouldBe 0.70f
        ImeWindowSpec.sanitizeGlideTrailScale(100) shouldBe 1.0f
        ImeWindowSpec.sanitizeGlideTrailScale(250) shouldBe 2.0f
        ImeWindowSpec.GLIDE_TRAIL_SCALE_DEFAULT_PERCENT shouldBe 100
    }

    test("emoji scale clamps to the 70-200 band") {
        ImeWindowSpec.sanitizeEmojiScale(0) shouldBe 0.70f
        ImeWindowSpec.sanitizeEmojiScale(100) shouldBe 1.0f
        ImeWindowSpec.sanitizeEmojiScale(1000) shouldBe 2.0f
        ImeWindowSpec.EMOJI_SCALE_DEFAULT_PERCENT shouldBe 100
    }

    test("readable text color follows background luminance") {
        readableTextColor(Color.White) shouldBe Color.Black
        readableTextColor(Color.Black) shouldBe Color.White
        readableTextColor(Color(0xFFF5F5F5)) shouldBe Color.Black // light surface
        readableTextColor(Color(0xFF101010)) shouldBe Color.White // dark surface
    }

    // -------------------------------------------------------------
    // Context-mode starts — the drain/merge pipeline
    // -------------------------------------------------------------

    test("context map merge keeps known modes and sums counts") {
        val current = mapOf("PASSWORD" to 3L)
        val delta = mapOf("PASSWORD" to 2L, "NUMBERS" to 1L, "EVIL_MODE" to 9L)
        val merged = DrsDailyStats.mergeContextMap(current, delta)
        merged shouldBe mapOf("PASSWORD" to 5L, "NUMBERS" to 1L)
        // Empty delta is the identity.
        DrsDailyStats.mergeContextMap(current, emptyMap()) shouldBe current
    }

    test("top context modes rank descending with deterministic ties") {
        val day = DrsDayStats(
            day = "2026-09-25",
            contextStarts = mapOf(
                "WRITING" to 5L, "PASSWORD" to 9L, "NUMBERS" to 2L, "CODING" to 5L,
            ),
        )
        val top = DrsDailyStats.topContextModes(listOf(day))
        top shouldBe listOf("PASSWORD" to 9L, "CODING" to 5L, "WRITING" to 5L)
        // CODING precedes WRITING on the tie (name order), deterministically.
        // Zero-total modes never appear.
        val empty = DrsDailyStats.topContextModes(listOf(DrsDayStats(day = "2026-09-25")))
        empty shouldBe emptyList()
    }

    test("context starts merge into the day bucket and count as activity") {
        val today = DrsDailyStats.todayStamp()
        val stats = mapOf(today to DrsDayStats(day = today))
        val delta = DrsUsageStats(
            contextStarts = mapOf("PASSWORD" to 4L, "GHOST" to 2L),
        )
        val merged = DrsDailyStats.mergeInto(stats, delta)
        merged[today].shouldNotBeNull().contextStarts shouldBe mapOf("PASSWORD" to 4L)
        // A context-only bucket is real activity (hasActivity) ...
        with(DrsDailyStats) {
            merged[today].shouldNotBeNull().hasActivity() shouldBe true
        }
        // ... and stays sane (the smuggled GHOST key was dropped).
        DrsDailyStats.isSane(merged) shouldBe true
    }

    // -------------------------------------------------------------
    // Backup preview — describe before restore
    // -------------------------------------------------------------

    test("backup preview describes the real contents of a state") {
        val state = DrsState(
            version = 1,
            shortcuts = listOf(
                DrsShortcut(id = 1, shortcut = "km", expansion = "كلمتي"),
                DrsShortcut(id = 2, shortcut = "th", expansion = "شكرا"),
            ),
            profiles = listOf(
                DrsProfile(
                    id = "p1",
                    name = "الأساس",
                    path = "NORMAL",
                    dayThemeId = "org.drs.themes:drs_day",
                    nightThemeId = "org.drs.themes:drs_night",
                    numberRow = false,
                    techStripEnabled = false,
                    suggestionsEnabled = true,
                    clipboardHistoryEnabled = true,
                    audioFeedbackEnabled = false,
                    hapticFeedbackEnabled = false,
                ),
            ),
            wallet = DrsWallet(normal = 12L, technical = 8L, hybrid = 5L),
            dailyStats = mapOf(
                "2026-09-24" to DrsDayStats(day = "2026-09-24", keyPresses = 10),
                "2026-09-25" to DrsDayStats(day = "2026-09-25"),
            ),
        )
        val preview = DrsBackup.describeBackup(state)
        preview.version shouldBe 1
        preview.shortcuts shouldBe 2
        preview.profiles shouldBe 1
        preview.walletTotal shouldBe 25L
        preview.daysRecorded shouldBe 2
        // Only the day that really has activity counts as active.
        preview.activeDayCount shouldBe 1
    }

    test("importParsed keeps the v1.5.0 version-cap contract") {
        DrsBackup.importParsed(DrsState(version = DrsBackup.SUPPORTED_STATE_VERSION + 1)).success shouldBe false
        DrsBackup.importParsed(DrsState(version = 0)).success shouldBe false
    }

    // -------------------------------------------------------------
    // Shortcut templates — the diagnostics check
    // -------------------------------------------------------------

    test("template validity flags typos, accepts known names and passes plain text") {
        // Unknown but well-formed variable — exactly what the check can
        // catch (a MISSING closing brace like {dat] is invisible to the
        // template scanner by design and stays literal, which is safe).
        val typo = DrsState(
            shortcuts = listOf(DrsShortcut(id = 1, shortcut = "d", expansion = "{dat} يوم")),
        )
        DrsShortcuts.templatesValid(typo) shouldBe false

        val known = DrsState(
            shortcuts = listOf(
                DrsShortcut(id = 1, shortcut = "d", expansion = "{date} {TIME}"),
                DrsShortcut(id = 2, shortcut = "n", expansion = "سطر{newline}ثان"),
            ),
        )
        DrsShortcuts.templatesValid(known) shouldBe true
        // Case-insensitive match against the known set (expandTemplate
        // lowercases the name) — a typo like {Datee} must still fail.
        val nearMiss = DrsState(
            shortcuts = listOf(DrsShortcut(id = 1, shortcut = "d", expansion = "{Datee}")),
        )
        DrsShortcuts.templatesValid(nearMiss) shouldBe false

        val plain = DrsState(
            shortcuts = listOf(DrsShortcut(id = 1, shortcut = "x", expansion = "بلا قوالب")),
        )
        // No templates at all: null (pass — no false alarm).
        DrsShortcuts.templatesValid(plain).shouldBeNull()
        DrsShortcuts.templatesValid(DrsState()).shouldBeNull()
    }
})
