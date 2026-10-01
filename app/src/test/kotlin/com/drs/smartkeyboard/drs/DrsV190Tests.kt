/*
 * Copyright (C) 2025-2026 The DRS Smart Keyboard Project
 */

package com.drs.smartkeyboard.drs

import com.drs.smartkeyboard.ime.clipboard.ClipFileNamer
import com.drs.smartkeyboard.ime.clipboard.ClipTextStats
import com.drs.smartkeyboard.ime.clipboard.ClipboardEditPlan
import com.drs.smartkeyboard.ime.clipboard.ClipboardHistoryExport
import com.drs.smartkeyboard.ime.clipboard.ClipboardTextPolicy
import com.drs.smartkeyboard.ime.clipboard.provider.ClipboardItem
import com.drs.smartkeyboard.ime.clipboard.provider.ItemType
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldEndWith
import io.kotest.matchers.string.shouldMatch
import java.time.Instant
import java.time.ZoneId
import java.util.Locale
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * DRS v1.9.0: the integrated smart clipboard system — the 50,000-character
 * storage policy per text entry (with surrogate-safe truncation), the
 * save-as-file namer (illegal characters, edge trimming, extension
 * enforcement, length cap, timestamped defaults), the real text statistics
 * (chars/words/lines), the edit plan (policy-capped, timestamp-bumped,
 * pin-and-id preserving), and the portable history JSON export.
 * Every rule is pinned where the engine owns it — pure, no fakes.
 */
class DrsV190Tests : FunSpec({

    // -------------------------------------------------------------
    // The storage policy cap — DRS v1.12.0 raised it from 50,000 to
    // 500,000 characters, so the historical contract is re-pinned at
    // the new boundary (the truncation tests below use the two-arg
    // overload so they stay true to the truncation behavior itself).
    // -------------------------------------------------------------

    test("the storage policy caps one text at exactly 500000 characters") {
        ClipboardTextPolicy.MAX_TEXT_CHARS shouldBe 500_000
        val boundary = "أ".repeat(500_000)
        ClipboardTextPolicy.fits(boundary) shouldBe true
        ClipboardTextPolicy.truncateForStorage(boundary) shouldBe boundary
    }

    test("one character over the cap truncates to exactly 500000") {
        val over = "x".repeat(500_001)
        ClipboardTextPolicy.fits(over) shouldBe false
        val stored = ClipboardTextPolicy.truncateForStorage(over)
        stored.length shouldBe 500_000
        stored shouldBe over.take(500_000)
    }

    test("truncation never splits a surrogate pair at the boundary") {
        // 499,999 ASCII units then one emoji (a high+low surrogate pair) =
        // 500,001 units; cutting at 500,000 would strand a lone high surrogate.
        val text = "a".repeat(499_999) + "😀"
        text.length shouldBe 500_001
        val stored = ClipboardTextPolicy.truncateForStorage(text)
        stored.length shouldBe 499_999
        Character.isHighSurrogate(stored.last()) shouldBe false
    }

    test("under-cap Arabic text passes through unchanged and is idempotent") {
        val text = "مرحبا بالعالم — hello world 123"
        val once = ClipboardTextPolicy.truncateForStorage(text)
        once shouldBe text
        ClipboardTextPolicy.truncateForStorage(once) shouldBe once
    }

    // -------------------------------------------------------------
    // The save-as-file namer
    // -------------------------------------------------------------

    test("the namer replaces illegal file characters with underscores") {
        ClipFileNamer.sanitize("a/b\\c:d*e?f\"g<h>i|j") shouldBe "a_b_c_d_e_f_g_h_i_j.txt"
    }

    test("the namer trims spaces and trailing dots (a Windows hazard)") {
        ClipFileNamer.sanitize("  ملاحظات ...  ") shouldBe "ملاحظات.txt"
        ClipFileNamer.sanitize("notes...") shouldBe "notes.txt"
    }

    test("the namer enforces the txt extension without doubling it") {
        ClipFileNamer.sanitize("meeting") shouldBe "meeting.txt"
        ClipFileNamer.sanitize("meeting.txt") shouldBe "meeting.txt"
        ClipFileNamer.sanitize("MEETING.TXT") shouldBe "MEETING.TXT"
    }

    test("the namer caps the full name at 80 characters preserving the extension") {
        val long = "س".repeat(90) + ".txt"
        val name = ClipFileNamer.sanitize(long)
        name.length shouldBe 80
        name shouldEndWith ".txt"
        // no extension at all -> the appended one is part of the cap
        ClipFileNamer.sanitize("n".repeat(100)).length shouldBe 80
    }

    test("a foreign extension survives inside the base under the txt contract") {
        // The saved file always opens as plain text, so the user suffix is
        // kept in the base and the txt extension is appended after it.
        ClipFileNamer.sanitize("notes.json") shouldBe "notes.json.txt"
        ClipFileNamer.sanitize("backup.md") shouldBe "backup.md.txt"
    }

    test("the namer falls back on empty, blank and null input") {
        ClipFileNamer.sanitize("") shouldBe "drs-clip.txt"
        ClipFileNamer.sanitize("   ") shouldBe "drs-clip.txt"
        ClipFileNamer.sanitize(null) shouldBe "drs-clip.txt"
    }

    test("the default file name is a timestamped pattern at a fixed zone") {
        val zone = ZoneId.of("UTC")
        val ts = Instant.parse("2026-09-25T14:30:00Z").toEpochMilli()
        ClipFileNamer.defaultFileName(ts, zone) shouldBe "drs-clip-20260925-1430.txt"
    }

    test("the default file name stays Latin-digit under an Arabic system locale") {
        // p7-F11 regression guard: the stamp formatter is pinned to
        // Locale.ROOT, so an Arabic-Indic default locale must never leak
        // into the saved name (drs-clip-٢٠٢٦… used to happen on ar devices).
        val original = Locale.getDefault()
        val zone = ZoneId.of("UTC")
        val ts = Instant.parse("2026-09-25T14:30:00Z").toEpochMilli()
        try {
            Locale.setDefault(Locale("ar", "SA"))
            val name = ClipFileNamer.defaultFileName(ts, zone)
            name shouldBe "drs-clip-20260925-1430.txt"
            name shouldMatch Regex("""drs-clip-\d{8}-\d{4}\.txt""")
        } finally {
            Locale.setDefault(original)
        }
    }

    // -------------------------------------------------------------
    // The real text statistics
    // -------------------------------------------------------------

    test("stats of empty text are all zero") {
        ClipTextStats.of("") shouldBe ClipTextStats(chars = 0, words = 0, lines = 0)
    }

    test("stats count Arabic and Latin words and lines for real") {
        val stats = ClipTextStats.of("مرحبا بالعالم\nhello world\n\nthird")
        stats.chars shouldBe "مرحبا بالعالم\nhello world\n\nthird".length
        stats.words shouldBe 5 // مرحبا بالعالم hello world third
        stats.lines shouldBe 4
    }

    test("stats of whitespace-only text keep one line and zero words") {
        val stats = ClipTextStats.of("   \t  ")
        stats.words shouldBe 0
        stats.lines shouldBe 1
    }

    // -------------------------------------------------------------
    // The edit plan
    // -------------------------------------------------------------

    test("the edit plan replaces the text and bumps the timestamp") {
        val item = ClipboardItem.text("old")
            .copy(id = 7, isPinned = true, creationTimestampMs = 1_000L)
        val planned = ClipboardEditPlan.plan(item, "نص جديد", nowMs = 9_999L)
        planned.text shouldBe "نص جديد"
        planned.creationTimestampMs shouldBe 9_999L
        planned.id shouldBe 7
        planned.isPinned shouldBe true
        planned.type shouldBe ItemType.TEXT
    }

    test("the edit plan truncates over-policy input through the same cap") {
        val item = ClipboardItem.text("short")
        val planned = ClipboardEditPlan.plan(item, "y".repeat(600_000), nowMs = 5L)
        planned.text!!.length shouldBe 500_000
    }

    // -------------------------------------------------------------
    // The portable history export
    // -------------------------------------------------------------

    test("the export serializes text items only with pin state preserved") {
        val items = listOf(
            ClipboardItem.text("نص عربي"),
            ClipboardItem.text("pinned note").copy(isPinned = true),
        )
        val document = ClipboardHistoryExport.toJson(items)
        val array = Json.parseToJsonElement(document).jsonArray
        array.size shouldBe 2
        val first = array[0].jsonObject
        first["text"]!!.jsonPrimitive.content shouldBe "نص عربي"
        first["pinned"]!!.jsonPrimitive.content shouldBe "false"
        val second = array[1].jsonObject
        second["pinned"]!!.jsonPrimitive.content shouldBe "true"
    }

    test("the export round-trips through its own parser") {
        val items = listOf(
            ClipboardItem.text("سطر أول\nسطر ثان").copy(creationTimestampMs = 42L),
            ClipboardItem.text("plain"),
        )
        val parsed = ClipboardHistoryExport.parse(ClipboardHistoryExport.toJson(items))
        parsed.size shouldBe 2
        parsed[0].text shouldBe "سطر أول\nسطر ثان"
        parsed[0].createdAt shouldBe 42L
        parsed[1].pinned shouldBe false
    }

    test("the export of an empty history is an empty JSON array") {
        ClipboardHistoryExport.toJson(emptyList()) shouldBe "[]"
        ClipboardHistoryExport.parse("not json") shouldBe emptyList()
    }
})
