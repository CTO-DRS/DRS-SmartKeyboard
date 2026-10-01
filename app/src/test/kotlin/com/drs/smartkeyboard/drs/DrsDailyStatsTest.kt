/*
 * Copyright (C) 2025-2026 The DRS Smart Keyboard Project
 */

package com.drs.smartkeyboard.drs

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import java.time.LocalDate

/**
 * DRS v1.0.8: unit tests for the pure daily statistics layer — merge and
 * day rollover, retention pruning, sanity validation and the last-days
 * window used by the statistics screen.
 */
class DrsDailyStatsTest : FunSpec({

    fun bucket(
        day: String,
        keys: Long = 0,
        tools: Long = 0,
        numbers: Long = 0,
        symbols: Long = 0,
    ): DrsDayStats =
        DrsDayStats(day = day, keyPresses = keys, numberPresses = numbers, symbolPresses = symbols, toolUses = tools)

    // -----------------------------------------------------------
    // mergeInto
    // -----------------------------------------------------------

    test("zero delta never modifies the map") {
        val current = mapOf("2026-09-24" to bucket("2026-09-24", keys = 10))
        val merged = DrsDailyStats.mergeInto(current, DrsUsageStats())
        merged shouldBe current
    }

    test("delta lands in today's bucket and rolls over on day change") {
        val today = DrsDailyStats.todayStamp()
        val current = mapOf("2020-01-01" to bucket("2020-01-01", keys = 5))
        val merged = DrsDailyStats.mergeInto(
            current,
            DrsUsageStats(keyPresses = 7, toolUses = mapOf(-35 to 2L)),
        )
        // today's bucket exists now with the merged counters
        merged[today] shouldBe DrsDayStats(day = today, keyPresses = 7, toolUses = 2)
        // the old day is untouched
        merged["2020-01-01"] shouldBe bucket("2020-01-01", keys = 5)
    }

    test("repeated merges accumulate into the same day bucket") {
        val today = DrsDailyStats.todayStamp()
        var stats = DrsDailyStats.mergeInto(emptyMap(), DrsUsageStats(keyPresses = 10))
        stats = DrsDailyStats.mergeInto(stats, DrsUsageStats(keyPresses = 15, gestureUses = 3))
        stats[today] shouldBe DrsDayStats(day = today, keyPresses = 25, gestureUses = 3)
    }

    // -----------------------------------------------------------
    // prune
    // -----------------------------------------------------------

    test("prune keeps only the newest buckets within the retention window") {
        val stats = (1..DrsDailyStats.KEEP_DAYS + 10).associate { index ->
            val day = LocalDate.of(2026, 1, 1).plusDays((index - 1).toLong()).toString()
            day to bucket(day, keys = index.toLong())
        }
        val pruned = DrsDailyStats.prune(stats)
        pruned.size shouldBe DrsDailyStats.KEEP_DAYS
        // the ten oldest days must be gone; the newest sixty kept
        val oldestDropped = LocalDate.of(2026, 1, 1).plusDays(9L).toString()
        val oldestKept = LocalDate.of(2026, 1, 1).plusDays(10L).toString()
        val newestKept = LocalDate.of(2026, 1, 1).plusDays((DrsDailyStats.KEEP_DAYS + 10 - 1).toLong()).toString()
        pruned.containsKey(oldestDropped) shouldBe false
        pruned.containsKey(oldestKept) shouldBe true
        pruned.containsKey(newestKept) shouldBe true
    }

    test("prune is a no-op below the retention bound") {
        val stats = mapOf(
            "2026-09-01" to bucket("2026-09-01", keys = 1),
            "2026-09-02" to bucket("2026-09-02", keys = 2),
        )
        DrsDailyStats.prune(stats) shouldBe stats
    }

    // -----------------------------------------------------------
    // isSane
    // -----------------------------------------------------------

    test("sane map with valid days passes") {
        val stats = mapOf(
            "2026-09-23" to bucket("2026-09-23", keys = 100, tools = 4),
            "2026-09-24" to bucket("2026-09-24", keys = 50),
        )
        DrsDailyStats.isSane(stats) shouldBe true
    }

    test("invalid day key, day mismatch or negative value fails sanity") {
        DrsDailyStats.isSane(mapOf("24-09-2026" to bucket("24-09-2026"))) shouldBe false
        DrsDailyStats.isSane(mapOf("2026-09-24" to bucket("2026-09-23"))) shouldBe false
        DrsDailyStats.isSane(mapOf("2026-09-24" to bucket("2026-09-24", keys = -1))) shouldBe false
        // oversized map fails too
        val oversized = (1..DrsDailyStats.KEEP_DAYS + 2).associate { index ->
            val day = LocalDate.of(2025, 1, 1).plusDays(index.toLong()).toString()
            day to bucket(day)
        }
        DrsDailyStats.isSane(oversized) shouldBe false
    }

    test("isValidDay rejects garbage and accepts ISO stamps") {
        DrsDailyStats.isValidDay("2026-09-24") shouldBe true
        DrsDailyStats.isValidDay("24/09/2026") shouldBe false
        DrsDailyStats.isValidDay("") shouldBe false
        DrsDailyStats.isValidDay("garbage") shouldBe false
    }

    // -----------------------------------------------------------
    // lastDays + sum
    // -----------------------------------------------------------

    test("lastDays returns only recorded active days, newest first") {
        val today = DrsDailyStats.todayStamp()
        val yesterday = LocalDate.parse(today).minusDays(1).toString()
        val stats = mapOf(
            today to bucket(today, keys = 10, tools = 1),
            yesterday to bucket(yesterday, keys = 20),
        )
        val last7 = DrsDailyStats.lastDays(stats, 7)
        last7.shouldHaveSize(2)
        last7.first().day shouldBe today
        last7.last().day shouldBe yesterday
        // days without buckets are absent, not zero-filled
        DrsDailyStats.lastDays(emptyMap(), 7).shouldBeEmpty()
    }

    test("lastDays ignores all-zero buckets") {
        val today = DrsDailyStats.todayStamp()
        val stats = mapOf(today to bucket(today, keys = 0, tools = 0))
        DrsDailyStats.lastDays(stats, 7).shouldBeEmpty()
    }

    test("sum aggregates counters across buckets") {
        val total = DrsDailyStats.sum(
            listOf(
                bucket("2026-09-22", keys = 100, tools = 2),
                bucket("2026-09-23", keys = 50, tools = 3),
            ),
        )
        total.keyPresses shouldBe 150
        total.toolUses shouldBe 5
    }

    // -----------------------------------------------------------
    // DRS v1.1.0: lastDaysZeroFilled (bar-chart / CSV window)
    // -----------------------------------------------------------

    test("lastDaysZeroFilled returns an ascending zero-filled window") {
        val today = DrsDailyStats.todayStamp()
        val twoAgo = LocalDate.parse(today).minusDays(2).toString()
        val threeAgo = LocalDate.parse(today).minusDays(3).toString()
        val fourAgo = LocalDate.parse(today).minusDays(4).toString()
        val stats = mapOf(
            today to bucket(today, keys = 10),
            threeAgo to bucket(threeAgo, keys = 30),
        )
        val window = DrsDailyStats.lastDaysZeroFilled(stats, 5, today)
        window.shouldHaveSize(5)
        // ascending: oldest (today-4) first, today last
        window.first().day shouldBe fourAgo
        window[1].day shouldBe threeAgo
        window[2].day shouldBe twoAgo
        window.last().day shouldBe today
        // missing days are zero-filled, present days keep their counters
        window[2].keyPresses shouldBe 0
        window[1].keyPresses shouldBe 30
        window.last().keyPresses shouldBe 10
    }

    test("bestDay picks the most active day, ties resolve to the later day") {
        val today = DrsDailyStats.todayStamp()
        val d1 = LocalDate.parse(today).minusDays(3).toString()
        val d2 = LocalDate.parse(today).minusDays(2).toString()
        val d3 = LocalDate.parse(today).minusDays(1).toString()
        val stats = mapOf(
            d1 to bucket(d1, keys = 40),
            d2 to bucket(d2, keys = 90),
            d3 to bucket(d3, keys = 90),
            today to bucket(today), // recorded but inactive — never "best"
        )
        val best = DrsDailyStats.bestDay(stats)
        best?.day shouldBe d3
        best?.keyPresses shouldBe 90L
        // no activity at all -> null
        DrsDailyStats.bestDay(emptyMap()) shouldBe null
    }

    test("currentStreak counts consecutive active days ending today or yesterday") {
        val today = DrsDailyStats.todayStamp()
        fun day(offset: Long) = LocalDate.parse(today).minusDays(offset).toString()
        // today active: 3-day chain (today, -1, -2), gap at -3
        val stats = mapOf(
            day(0) to bucket(day(0), keys = 5),
            day(1) to bucket(day(1), keys = 5),
            day(2) to bucket(day(2), keys = 5),
            day(4) to bucket(day(4), keys = 99), // older, must not extend
        )
        DrsDailyStats.currentStreak(stats, today) shouldBe 3
        // today inactive but yesterday active -> live streak still counts
        DrsDailyStats.currentStreak(
            mapOf(day(1) to bucket(day(1), keys = 5), day(2) to bucket(day(2), keys = 5)),
            today,
        ) shouldBe 2
        // nothing active -> 0
        DrsDailyStats.currentStreak(emptyMap(), today) shouldBe 0
        // malformed today -> 0
        DrsDailyStats.currentStreak(stats, "garbage") shouldBe 0
    }

    test("lastDaysZeroFilled handles empty stats and invalid today") {
        val today = DrsDailyStats.todayStamp()
        val empty = DrsDailyStats.lastDaysZeroFilled(emptyMap(), 7, today)
        empty.shouldHaveSize(7)
        empty.all { it.keyPresses == 0L } shouldBe true
        empty.last().day shouldBe today
        DrsDailyStats.lastDaysZeroFilled(emptyMap(), 5, "garbage").shouldBeEmpty()
        DrsDailyStats.lastDaysZeroFilled(emptyMap(), 0, today).shouldBeEmpty()
    }

    // -----------------------------------------------------------
    // DRS v1.3.0: number/symbol counters + week-over-week growth
    // -----------------------------------------------------------

    test("mergeInto persists the number and symbol counters of the delta") {
        val today = DrsDailyStats.todayStamp()
        val merged = DrsDailyStats.mergeInto(
            emptyMap(),
            DrsUsageStats(keyPresses = 20, numberPresses = 6, symbolPresses = 3),
        )
        merged[today] shouldBe DrsDayStats(
            day = today, keyPresses = 20, numberPresses = 6, symbolPresses = 3,
        )
    }

    test("a purely numeric delta is not treated as zero") {
        val today = DrsDailyStats.todayStamp()
        val merged = DrsDailyStats.mergeInto(emptyMap(), DrsUsageStats(numberPresses = 4))
        merged[today]?.numberPresses shouldBe 4
    }

    test("sum aggregates number and symbol counters") {
        val total = DrsDailyStats.sum(
            listOf(
                bucket("2026-09-22", keys = 100, numbers = 12, symbols = 5),
                bucket("2026-09-23", keys = 50, numbers = 8, symbols = 7),
            ),
        )
        total.numberPresses shouldBe 20
        total.symbolPresses shouldBe 12
    }

    test("sanity rejects negative number or symbol counters") {
        DrsDailyStats.isSane(mapOf("2026-09-24" to bucket("2026-09-24", numbers = -1))) shouldBe false
        DrsDailyStats.isSane(mapOf("2026-09-24" to bucket("2026-09-24", symbols = -1))) shouldBe false
        DrsDailyStats.isSane(mapOf("2026-09-24" to bucket("2026-09-24", numbers = 3, symbols = 4))) shouldBe true
    }

    test("number or symbol activity alone makes a day active") {
        val today = DrsDailyStats.todayStamp()
        val numericOnly = mapOf(today to bucket(today, numbers = 10))
        DrsDailyStats.bestDay(numericOnly)?.day shouldBe today
        DrsDailyStats.currentStreak(numericOnly, today) shouldBe 1
        // ...and lastDays keeps it (activity-only window).
        DrsDailyStats.lastDays(numericOnly, 7, today).shouldHaveSize(1)
    }

    test("lastWeeksGrowthPercent compares the last 7 days with the previous 7") {
        val today = LocalDate.of(2026, 9, 24)
        val stats = buildMap {
            // previous 7-day window = offsets 13..7 from today: 100 presses/day
            (13 downTo 7).forEach { offset ->
                val day = today.minusDays(offset.toLong()).toString()
                put(day, bucket(day, keys = 100))
            }
            // last 7-day window = offsets 6..0 from today: 150 presses/day
            (6 downTo 0).forEach { offset ->
                val day = today.minusDays(offset.toLong()).toString()
                put(day, bucket(day, keys = 150))
            }
        }
        val growth = DrsDailyStats.lastWeeksGrowthPercent(stats, today.toString())
        // (150-100)*7 * 100 / (100*7) = exactly 50.0
        growth shouldBe 50.0
    }

    test("lastWeeksGrowthPercent returns null when the previous week is empty") {
        val today = LocalDate.of(2026, 9, 24)
        val day = today.minusDays(1).toString()
        val stats = mapOf(day to bucket(day, keys = 10))
        DrsDailyStats.lastWeeksGrowthPercent(stats, today.toString()) shouldBe null
        DrsDailyStats.lastWeeksGrowthPercent(emptyMap(), today.toString()) shouldBe null
        DrsDailyStats.lastWeeksGrowthPercent(emptyMap(), "garbage") shouldBe null
    }

    // -----------------------------------------------------------
    // DRS v1.4.0: activeDaysCount + dailyAveragePresses
    // -----------------------------------------------------------

    test("activeDaysCount counts only days with real activity") {
        val stats = mapOf(
            "2026-09-22" to bucket("2026-09-22", keys = 120),
            "2026-09-23" to bucket("2026-09-23"), // recorded but idle
            "2026-09-24" to bucket("2026-09-24", tools = 3), // activity without presses
        )
        DrsDailyStats.activeDaysCount(stats) shouldBe 2
        DrsDailyStats.activeDaysCount(emptyMap()) shouldBe 0
    }

    test("dailyAveragePresses averages over active days only") {
        val stats = mapOf(
            "2026-09-22" to bucket("2026-09-22", keys = 100),
            "2026-09-23" to bucket("2026-09-23"), // idle day must not dilute
            "2026-09-24" to bucket("2026-09-24", keys = 50),
        )
        DrsDailyStats.dailyAveragePresses(stats) shouldBe 75L
        DrsDailyStats.dailyAveragePresses(emptyMap()) shouldBe 0L
    }
})
