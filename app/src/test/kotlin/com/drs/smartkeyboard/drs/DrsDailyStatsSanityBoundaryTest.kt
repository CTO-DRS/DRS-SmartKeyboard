/*
 * Copyright (C) 2025-2026 The DRS Smart Keyboard Project
 */

package com.drs.smartkeyboard.drs

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.shouldBe
import java.time.LocalDate

/**
 * DRS p9: the sanity/boundary contracts of DrsDailyStats — the closed
 * context-mode whitelist, non-negative counters, the KEEP_DAYS(+1)
 * retention tolerance, the deterministic topContextModes tie-break, and
 * mergeInto through the DRS p9 (T-5) injectable `today` seam (rollover,
 * pruning and the exact KEEP_DAYS bound, all without a clock).
 */
class DrsDailyStatsSanityBoundaryTest : FunSpec({

    fun bucket(
        day: String,
        keyPresses: Long = 1L,
        contextStarts: Map<String, Long> = emptyMap(),
    ) = DrsDayStats(day = day, keyPresses = keyPresses, contextStarts = contextStarts)

    fun days(count: Int, start: LocalDate = LocalDate.of(2026, 1, 1)): Map<String, DrsDayStats> =
        (0 until count).associate { i ->
            val stamp = start.plusDays(i.toLong()).toString()
            stamp to bucket(stamp)
        }

    test("isSane rejects unknown context-mode keys — the whitelist is closed") {
        val smuggled = mapOf(
            "2026-01-01" to bucket("2026-01-01", contextStarts = mapOf("HACK" to 1L)),
        )
        DrsDailyStats.isSane(smuggled).shouldBeFalse()
        val known = mapOf(
            "2026-01-01" to bucket("2026-01-01", contextStarts = mapOf("CHAT" to 1L)),
        )
        DrsDailyStats.isSane(known).shouldBeTrue()
        // the whitelist really is the context-mode enum
        DrsDailyStats.KNOWN_CONTEXT_MODES shouldBe
            DrsContextMode.entries.map { it.name }.toSet()
    }

    test("isSane rejects negative counters") {
        val negative = mapOf("2026-01-01" to bucket("2026-01-01", keyPresses = -1))
        DrsDailyStats.isSane(negative).shouldBeFalse()
    }

    test("the retention tolerance accepts exactly KEEP_DAYS+1 buckets but no more") {
        DrsDailyStats.isSane(days(DrsDailyStats.KEEP_DAYS + 1)).shouldBeTrue()
        DrsDailyStats.isSane(days(DrsDailyStats.KEEP_DAYS + 2)).shouldBeFalse()
    }

    test("topContextModes handles degenerate limits and breaks ties by mode name") {
        val buckets = listOf(
            bucket("2026-01-01", contextStarts = mapOf("NORMAL" to 5L, "CHAT" to 5L)),
        )
        DrsDailyStats.topContextModes(buckets, limit = 0) shouldBe emptyList()
        DrsDailyStats.topContextModes(buckets, limit = -1) shouldBe emptyList()
        // equal totals resolve to the alphabetically-first mode (thenBy name)
        // so the display can never flicker between equal entries
        DrsDailyStats.topContextModes(buckets, limit = 3) shouldBe
            listOf("CHAT" to 5L, "NORMAL" to 5L)
    }

    test("mergeInto honors the injected today stamp for rollover") {
        val merged = DrsDailyStats.mergeInto(
            emptyMap(),
            DrsUsageStats(keyPresses = 10, contextStarts = mapOf("CHAT" to 2L)),
            today = "2030-01-01",
        )
        merged.size shouldBe 1
        val day = merged["2030-01-01"]!!
        day.day shouldBe "2030-01-01"
        day.keyPresses shouldBe 10L
        day.contextStarts shouldBe mapOf("CHAT" to 2L)
    }

    test("mergeInto sums the delta into an existing today bucket (context starts included)") {
        val current = mapOf(
            "2030-01-01" to DrsDayStats(
                day = "2030-01-01", keyPresses = 5,
                contextStarts = mapOf("CHAT" to 4L),
            ),
        )
        val merged = DrsDailyStats.mergeInto(
            current,
            DrsUsageStats(keyPresses = 7, contextStarts = mapOf("CHAT" to 1L)),
            today = "2030-01-01",
        )
        val day = merged["2030-01-01"]!!
        day.keyPresses shouldBe 12L
        day.contextStarts shouldBe mapOf("CHAT" to 5L)
    }

    test("mergeInto prunes to exactly KEEP_DAYS buckets around the injected today") {
        // 60 buckets from 2029-11-02 through 2029-12-31, then a fresh
        // 2030-01-01 delta arrives: the map holds 61 buckets, the oldest
        // (2029-11-02) is pruned and today lands with its data
        val stats = days(DrsDailyStats.KEEP_DAYS, start = LocalDate.of(2029, 11, 2))
        val merged = DrsDailyStats.mergeInto(
            stats,
            DrsUsageStats(keyPresses = 3),
            today = "2030-01-01",
        )
        merged.size shouldBe DrsDailyStats.KEEP_DAYS
        merged.containsKey("2029-11-02").shouldBeFalse()
        merged["2030-01-01"]!!.keyPresses shouldBe 3L
    }

    test("the defaulted today parameter still targets the real today stamp") {
        val merged = DrsDailyStats.mergeInto(emptyMap(), DrsUsageStats(keyPresses = 1))
        merged.keys shouldBe setOf(DrsDailyStats.todayStamp())
    }
})
