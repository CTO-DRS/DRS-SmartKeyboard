/*
 * Copyright (C) 2025 The DRS Smart Keyboard Project
 */

package com.drs.smartkeyboard.ime.clipboard

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

/**
 * DRS p9 — regression contracts for the p7 clipboard-editor security/stability
 * fixes:
 *  - p7 (F3): [ClipSearchResults.replaceOneIfLive] — the stale-match guard.
 *    The search match list is debounced while the live editor text keeps
 *    moving; a match whose end outruns the LIVE text used to reach the raw
 *    substring replacement and throw IndexOutOfBoundsException (panel: kills
 *    the IME process; popup: kills the activity). The guard must return null
 *    instead.
 *  - p7 (F6): [ClipItemCategoryDetector.detect] — the LARGE_TEXT_CHARS (100k)
 *    gate. The per-tile regex storm of ClipCodeDetector.analyze must never see
 *    oversized texts; the cheap URL/EMAIL/PHONE checks still run first.
 */
class ClipSearchGuardTest : FunSpec({

    context("replaceOneIfLive — stale-match guard (DRS p7 F3)") {
        test("live match is replaced") {
            ClipSearchResults.replaceOneIfLive("hello world", ClipMatch(0, 5), "hi") shouldBe "hi world"
        }

        test("THE REGRESSION: match end beyond the shrunken live text returns null, never IOOBE") {
            ClipSearchResults.replaceOneIfLive("hi", ClipMatch(0, 5), "x") shouldBe null
            ClipSearchResults.replaceOneIfLive("abc", ClipMatch(2, 9), "x") shouldBe null
        }

        test("empty match (end == start) is rejected") {
            ClipSearchResults.replaceOneIfLive("hello", ClipMatch(1, 1), "x") shouldBe null
        }

        test("match covering the whole text replaces everything") {
            ClipSearchResults.replaceOneIfLive("hello", ClipMatch(0, 5), "hey") shouldBe "hey"
        }

        test("unicode/emoji text and Arabic replacement round-trip") {
            val text = "salaam 😀 world"
            val match = ClipSearchEngine.findMatches(text, "world").first()

            val replaced = ClipSearchResults.replaceOneIfLive(text, match, "العالم")
            replaced shouldBe "salaam 😀 العالم"

            val back = ClipSearchResults.replaceOneIfLive(
                replaced!!,
                ClipSearchEngine.findMatches(replaced, "العالم").first(),
                "world",
            )
            back shouldBe text
        }

        test("null match is an honest no-op") {
            ClipSearchResults.replaceOneIfLive("hello world", null, "hi") shouldBe null
        }
    }

    context("ClipItemCategoryDetector — LARGE_TEXT_CHARS gate (DRS p7 F6)") {
        test("code-looking text beyond the 100k gate classifies TEXT") {
            // 9090 x 11 chars + an 11-char final line = exactly 100_001 chars,
            // no leading/trailing whitespace (detect() trims first), multi-line
            // so the single-line URL/EMAIL/PHONE checks are skipped.
            val big = "val x = 1;\n".repeat(9090) + "val x = 1;a"
            big.length shouldBe 100_001

            ClipItemCategoryDetector.detect(big) shouldBe ClipItemCategory.TEXT
        }

        test("exactly 100_000 chars of code still classifies CODE (<= boundary)") {
            val big = "val x = 1;\n".repeat(9090) + "val x = 1;" // exactly 100_000 chars
            big.length shouldBe 100_000

            ClipItemCategoryDetector.detect(big) shouldBe ClipItemCategory.CODE
        }

        test("oversized single-line URL still classifies URL (cheap checks run before the gate)") {
            // Pinned priority: URL/EMAIL/PHONE pattern checks happen before the
            // LARGE_TEXT_CHARS gate, so even a >100k URL-shaped line stays URL.
            val url = "www." + "a".repeat(100_001)
            url.length shouldBe 100_005

            ClipItemCategoryDetector.detect(url) shouldBe ClipItemCategory.URL
        }

        test("oversized whitespace classifies TEXT") {
            ClipItemCategoryDetector.detect(" ".repeat(100_001)) shouldBe ClipItemCategory.TEXT
        }
    }
})
