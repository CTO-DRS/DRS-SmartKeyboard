/*
 * Copyright (C) 2025-2026 The DRS Smart Keyboard Project
 */

package com.drs.smartkeyboard.drs

import java.time.LocalDate

/**
 * DRS v1.0.8: pure logic of the daily usage statistics — day buckets,
 * merge/rollover, retention pruning and sanity validation. Everything here
 * is side-effect free and JVM-testable; the Android layers (adaptation
 * engine, stats screen, diagnostics) only call these helpers.
 *
 * Privacy: the buckets hold COUNTS only (how many keys, tools, gestures…),
 * never text, never timestamps of individual keystrokes, and the map never
 * leaves the device.
 */
object DrsDailyStats {

    /** How many day buckets are retained (oldest dropped on flush). */
    const val KEEP_DAYS = 60

    /** ISO day stamp of "now", e.g. "2026-09-24". */
    fun todayStamp(): String = LocalDate.now().toString()

    /** True when [day] is a valid ISO day stamp (yyyy-MM-dd). */
    fun isValidDay(day: String): Boolean {
        return try {
            LocalDate.parse(day).toString() == day
        } catch (_: Throwable) {
            false
        }
    }

    /** True when the delta adds nothing (avoids rewriting the state file for no reason). */
    private fun DrsUsageStats.isZero(): Boolean =
        keyPresses == 0L && numberPresses == 0L && symbolPresses == 0L &&
            emojiUses == 0L && clipboardUses == 0L &&
            shortcutUses == 0L && techToolUses == 0L && gestureUses == 0L &&
            suggestionAccepts == 0L && toolUses.isEmpty() && contextStarts.isEmpty() &&
            // DRS v2.22.0: the per-feature counters count too — a flush
            // carrying only a panel press is still a real delta.
            myTextsUses == 0L && myLexiconUses == 0L && harakatUses == 0L &&
            symbolUses == 0L && letterUses == 0L && numberUses == 0L

    /**
     * Merges [delta] (the drained adaptation counters) into the TODAY
     * bucket, performing the day rollover implicitly (a new day gets
     * a fresh bucket) and pruning buckets older than [KEEP_DAYS]. The
     * per-tool map of the delta is summed into the day's tool press count —
     * the drained map is uncapped, so the sum is exact for the window.
     *
     * DRS p9 (T-5): the day stamp is an injectable [today] seam (default
     * [todayStamp]) so tests can pin the rollover/pruning deterministically
     * without a clock; production callers keep the old behavior untouched.
     */
    fun mergeInto(
        current: Map<String, DrsDayStats>,
        delta: DrsUsageStats,
        today: String = todayStamp(),
    ): Map<String, DrsDayStats> {
        if (delta.isZero()) return current
        val existing = current[today] ?: DrsDayStats(day = today)
        val merged = existing.copy(
            keyPresses = existing.keyPresses + delta.keyPresses,
            // DRS v1.3.0: the digit/symbol counters were recorded by the
            // adaptation engine but silently dropped here — now they persist.
            numberPresses = existing.numberPresses + delta.numberPresses,
            symbolPresses = existing.symbolPresses + delta.symbolPresses,
            toolUses = existing.toolUses + delta.toolUses.values.sum(),
            techToolUses = existing.techToolUses + delta.techToolUses,
            gestureUses = existing.gestureUses + delta.gestureUses,
            emojiUses = existing.emojiUses + delta.emojiUses,
            clipboardUses = existing.clipboardUses + delta.clipboardUses,
            shortcutUses = existing.shortcutUses + delta.shortcutUses,
            // DRS v2.22.0: the per-feature counters persist too — v2.21.0
            // added myTextsUses to the model but this merge silently
            // dropped it (the day buckets never saw a saved-text
            // insertion). The whole closed set now merges.
            myTextsUses = existing.myTextsUses + delta.myTextsUses,
            myLexiconUses = existing.myLexiconUses + delta.myLexiconUses,
            harakatUses = existing.harakatUses + delta.harakatUses,
            symbolUses = existing.symbolUses + delta.symbolUses,
            letterUses = existing.letterUses + delta.letterUses,
            numberUses = existing.numberUses + delta.numberUses,
            // DRS v1.6.0: committed suggestion-row entries persist too.
            suggestionAccepts = existing.suggestionAccepts + delta.suggestionAccepts,
            // DRS v1.7.0: context-mode starts persist as a summed map —
            // keys are bounded by the known modes (kept via mergeContextMap).
            contextStarts = mergeContextMap(existing.contextStarts, delta.contextStarts),
        )
        return prune(current + (today to merged))
    }

    /**
     * DRS v1.7.0: the known context-mode names — the whitelist both the
     * merge cap and the sanity check validate against, so a hand-tampered
     * state file can never smuggle arbitrary keys into the stats.
     */
    val KNOWN_CONTEXT_MODES: Set<String> =
        DrsContextMode.entries.map { it.name }.toSet()

    /** Sums two context maps, keeping known mode keys only. Pure. */
    fun mergeContextMap(
        current: Map<String, Long>,
        delta: Map<String, Long>,
    ): Map<String, Long> {
        if (delta.isEmpty()) return current
        val merged = HashMap(current)
        for ((mode, count) in delta) {
            if (mode !in KNOWN_CONTEXT_MODES) continue
            merged[mode] = (merged[mode] ?: 0L) + count
        }
        return merged
    }

    /**
     * DRS v1.7.0: the [limit] most frequent context modes across [buckets],
     * descending, ties broken deterministically by mode name so the display
     * never flickers. Pure — zero-total modes are dropped so an empty
     * recording window yields an empty list.
     */
    fun topContextModes(
        buckets: Collection<DrsDayStats>,
        limit: Int = 3,
    ): List<Pair<String, Long>> {
        if (buckets.isEmpty() || limit <= 0) return emptyList()
        return KNOWN_CONTEXT_MODES
            .map { mode -> mode to buckets.sumOf { it.contextStarts[mode] ?: 0L } }
            .filter { it.second > 0 }
            .sortedWith(compareByDescending<Pair<String, Long>> { it.second }.thenBy { it.first })
            .take(limit)
    }

    /**
     * Keeps only the newest [keepDays] buckets. ISO day stamps sort
     * lexicographically == chronologically, so ordering is trivial and
     * stable.
     */
    fun prune(stats: Map<String, DrsDayStats>, keepDays: Int = KEEP_DAYS): Map<String, DrsDayStats> {
        if (stats.size <= keepDays) return stats
        return stats.entries
            .sortedByDescending { it.key }
            .take(keepDays)
            .associate { it.toPair() }
    }

    /**
     * Sanity of a persisted map, used by the diagnostics runner: every key
     * must be a valid ISO day, the bucket's own day field must match its
     * key, all counters non-negative, and the size within the retention
     * bound (+1 tolerance for a state written by a slightly newer build).
     */
    fun isSane(stats: Map<String, DrsDayStats>): Boolean {
        if (stats.size > KEEP_DAYS + 1) return false
        return stats.all { (day, bucket) ->
            isValidDay(day) &&
                bucket.day == day &&
                bucket.keyPresses >= 0 &&
                bucket.numberPresses >= 0 &&
                bucket.symbolPresses >= 0 &&
                bucket.toolUses >= 0 &&
                bucket.techToolUses >= 0 &&
                bucket.gestureUses >= 0 &&
                bucket.emojiUses >= 0 &&
                bucket.clipboardUses >= 0 &&
                bucket.shortcutUses >= 0 &&
                bucket.suggestionAccepts >= 0 &&
                // DRS v1.7.0: context-mode starts — counts non-negative,
                // keys restricted to the known modes, no smuggled keys.
                bucket.contextStarts.all { (mode, count) ->
                    mode in KNOWN_CONTEXT_MODES && count >= 0
                }
        }
    }

    /** Sum of several buckets (used by the last-7-days aggregate row). */
    fun sum(buckets: Collection<DrsDayStats>): DrsDayStats {
        return buckets.fold(DrsDayStats()) { acc, bucket ->
            acc.copy(
                keyPresses = acc.keyPresses + bucket.keyPresses,
                numberPresses = acc.numberPresses + bucket.numberPresses,
                symbolPresses = acc.symbolPresses + bucket.symbolPresses,
                toolUses = acc.toolUses + bucket.toolUses,
                techToolUses = acc.techToolUses + bucket.techToolUses,
                gestureUses = acc.gestureUses + bucket.gestureUses,
                emojiUses = acc.emojiUses + bucket.emojiUses,
                clipboardUses = acc.clipboardUses + bucket.clipboardUses,
                shortcutUses = acc.shortcutUses + bucket.shortcutUses,
                // DRS v2.22.0: the per-feature counters aggregate like
                // their siblings — the totals view never drops a feature.
                myTextsUses = acc.myTextsUses + bucket.myTextsUses,
                myLexiconUses = acc.myLexiconUses + bucket.myLexiconUses,
                harakatUses = acc.harakatUses + bucket.harakatUses,
                symbolUses = acc.symbolUses + bucket.symbolUses,
                letterUses = acc.letterUses + bucket.letterUses,
                numberUses = acc.numberUses + bucket.numberUses,
                suggestionAccepts = acc.suggestionAccepts + bucket.suggestionAccepts,
                // DRS v1.7.0: the lifetime context view aggregates too.
                contextStarts = mergeContextMap(acc.contextStarts, bucket.contextStarts),
            )
        }
    }

    /**
     * The buckets of the last [days] days (today included), newest first.
     * Days without data are simply absent from the result — the UI shows
     * the real recorded days only.
     */
    fun lastDays(stats: Map<String, DrsDayStats>, days: Int, today: String = todayStamp()): List<DrsDayStats> {
        if (days <= 0) return emptyList()
        val todayDate = try {
            LocalDate.parse(today)
        } catch (_: Throwable) {
            return emptyList()
        }
        return (0 until days)
            .map { todayDate.minusDays(it.toLong()).toString() }
            .mapNotNull { stamp -> stats[stamp]?.takeIf { bucket -> bucket.hasActivity() } }
    }

    /**
     * DRS v1.1.0: a continuous, ASCENDING (oldest -> today) window of the
     * last [days] day buckets with every missing day zero-filled — the
     * exact shape the bar chart and the CSV export need. Returns exactly
     * [days] buckets when [today] parses (oldest first, today last).
     */
    fun lastDaysZeroFilled(
        stats: Map<String, DrsDayStats>,
        days: Int,
        today: String = todayStamp(),
    ): List<DrsDayStats> {
        if (days <= 0) return emptyList()
        val todayDate = try {
            LocalDate.parse(today)
        } catch (_: Throwable) {
            return emptyList()
        }
        return ((days - 1) downTo 0)
            .map { offset -> todayDate.minusDays(offset.toLong()).toString() }
            .map { stamp -> stats[stamp] ?: DrsDayStats(day = stamp) }
    }

    /**
     * DRS v1.2.0: the single most active recorded day by key presses.
     * Ties resolve to the LATER day so the result is deterministic.
     * Null when no recorded day has any activity at all.
     */
    fun bestDay(stats: Map<String, DrsDayStats>): DrsDayStats? =
        stats.values
            .filter { it.hasActivity() }
            .maxWithOrNull(compareBy({ it.keyPresses }, { it.day }))

    /**
     * DRS v1.2.0: consecutive active days ending today — or ending
     * yesterday when today has no activity yet (a live streak is not
     * broken while the current day is still ongoing). Pure and
     * JVM-testable; a malformed [today] degrades to 0.
     */
    fun currentStreak(
        stats: Map<String, DrsDayStats>,
        today: String = todayStamp(),
    ): Int {
        val todayDate = try {
            LocalDate.parse(today)
        } catch (_: Throwable) {
            return 0
        }
        var cursor = todayDate
        if (stats[cursor.toString()]?.hasActivity() != true) {
            cursor = cursor.minusDays(1)
        }
        var streak = 0
        while (stats[cursor.toString()]?.hasActivity() == true) {
            streak++
            cursor = cursor.minusDays(1)
        }
        return streak
    }

    /**
     * DRS v1.3.0: week-over-week growth of key presses, computed from two
     * real zero-filled 7-day windows ending today. Null when the previous
     * week had no activity at all (a percentage would be meaningless).
     * Pure and JVM-testable.
     */
    fun lastWeeksGrowthPercent(
        stats: Map<String, DrsDayStats>,
        today: String = todayStamp(),
    ): Double? {
        val window = lastDaysZeroFilled(stats, 14, today)
        if (window.size != 14) return null
        val prev7 = window.take(7).sumOf { it.keyPresses }
        val last7 = window.takeLast(7).sumOf { it.keyPresses }
        if (prev7 <= 0L) return null
        return (last7 - prev7) * 100.0 / prev7
    }

    /**
     * DRS v1.4.0: number of recorded days with at least one non-zero
     * counter. Pure and JVM-testable.
     */
    fun activeDaysCount(stats: Map<String, DrsDayStats>): Int =
        stats.values.count { it.hasActivity() }

    /**
     * DRS v1.4.0: mean key presses per ACTIVE day — days without any
     * activity are excluded so idle days never dilute the average.
     * 0 when no recorded day has activity. Pure and JVM-testable.
     */
    fun dailyAveragePresses(stats: Map<String, DrsDayStats>): Long {
        val active = stats.values.filter { it.hasActivity() }
        if (active.isEmpty()) return 0L
        return active.sumOf { it.keyPresses } / active.size
    }

    /**
     * DRS v1.5.0: the LONGEST run of consecutive active days anywhere in
     * the recorded window — the all-time sibling of [currentStreak], which
     * is anchored to today. A malformed day stamp simply cannot form a
     * consecutive pair, so it never inflates the run. Pure and
     * JVM-testable.
     */
    fun longestStreak(stats: Map<String, DrsDayStats>): Int {
        val activeDays = stats.values
            .filter { it.hasActivity() }
            .mapNotNull { bucket ->
                try {
                    LocalDate.parse(bucket.day)
                } catch (_: Throwable) {
                    null
                }
            }
            .sorted()
        if (activeDays.isEmpty()) return 0
        var longest = 1
        var run = 1
        for (i in 1 until activeDays.size) {
            run = if (activeDays[i] == activeDays[i - 1].plusDays(1)) run + 1 else 1
            if (run > longest) longest = run
        }
        return longest
    }

    /**
     * DRS v1.5.0: the weekday with the highest total of key presses across
     * all recorded days (e.g. "you type most on Sundays"). Ties resolve
     * deterministically to the first-encountered weekday (the one whose
     * total was reached first); null when no recorded day has any key
     * presses. Pure and JVM-testable.
     */
    fun busiestWeekday(stats: Map<String, DrsDayStats>): java.time.DayOfWeek? {
        val byWeekday = LinkedHashMap<java.time.DayOfWeek, Long>()
        for ((day, bucket) in stats) {
            val date = try {
                LocalDate.parse(day)
            } catch (_: Throwable) {
                continue
            }
            if (bucket.keyPresses <= 0L) continue
            val key = date.dayOfWeek
            byWeekday[key] = (byWeekday[key] ?: 0L) + bucket.keyPresses
        }
        var best: java.time.DayOfWeek? = null
        var bestTotal = 0L
        for ((key, total) in byWeekday) {
            if (total > bestTotal) {
                best = key
                bestTotal = total
            }
        }
        return best
    }

    /**
     * DRS v1.5.0: how many days the statistics window spans — from the
     * OLDEST recorded day through [today] inclusive. Null when nothing is
     * recorded or [today] is malformed. Pure and JVM-testable.
     */
    fun recordedSpanDays(
        stats: Map<String, DrsDayStats>,
        today: String = todayStamp(),
    ): Int? {
        if (stats.isEmpty()) return null
        val todayDate = try {
            LocalDate.parse(today)
        } catch (_: Throwable) {
            return null
        }
        val oldest = stats.keys
            .mapNotNull { day -> try { LocalDate.parse(day) } catch (_: Throwable) { null } }
            .minOrNull() ?: return null
        return java.time.temporal.ChronoUnit.DAYS.between(oldest, todayDate).toInt() + 1
    }

    /** True when at least one counter of the bucket is non-zero. */
    fun DrsDayStats.hasActivity(): Boolean =
        keyPresses > 0L || numberPresses > 0L || symbolPresses > 0L || toolUses > 0L ||
            techToolUses > 0L || gestureUses > 0L ||
            emojiUses > 0L || clipboardUses > 0L || shortcutUses > 0L ||
            suggestionAccepts > 0L ||
            // DRS v2.22.0: the per-feature counters count as activity —
            // a day of panel work is a real usage day.
            myTextsUses > 0L || myLexiconUses > 0L || harakatUses > 0L ||
            symbolUses > 0L || letterUses > 0L || numberUses > 0L ||
            // DRS v1.7.0: input starts alone count as activity — a day
            // spent opening password/number fields is a real usage day.
            contextStarts.values.any { it > 0L }

    /**
     * DRS v1.6.0: how many days of the recorded span have NO activity —
     * the exact complement of the active-day count inside the recorded
     * window (span − active). Null when the span itself is unknown.
     * Pure and JVM-testable.
     */
    fun missedDaysCount(
        stats: Map<String, DrsDayStats>,
        today: String = todayStamp(),
    ): Int? {
        val span = recordedSpanDays(stats, today) ?: return null
        return span - activeDaysCount(stats)
    }

    /**
     * DRS v1.6.0: the share of ACTIVE days inside the recorded window,
     * as a percentage (0–100). Null when the span is unknown. Pure and
     * JVM-testable.
     */
    fun activeDayRatioPercent(
        stats: Map<String, DrsDayStats>,
        today: String = todayStamp(),
    ): Double? {
        val span = recordedSpanDays(stats, today) ?: return null
        return activeDaysCount(stats) * 100.0 / span
    }

    /**
     * DRS v1.6.0: the weekday with the LOWEST total of key presses among
     * the weekdays that have at least one key press recorded — the quiet
     * counterpart of [busiestWeekday]. Ties resolve deterministically to
     * the first-encountered weekday; null when no recorded day has key
     * presses. Pure and JVM-testable.
     */
    fun quietestWeekday(stats: Map<String, DrsDayStats>): java.time.DayOfWeek? {
        val byWeekday = LinkedHashMap<java.time.DayOfWeek, Long>()
        for ((day, bucket) in stats) {
            val date = try {
                LocalDate.parse(day)
            } catch (_: Throwable) {
                continue
            }
            if (bucket.keyPresses <= 0L) continue
            val key = date.dayOfWeek
            byWeekday[key] = (byWeekday[key] ?: 0L) + bucket.keyPresses
        }
        var quietest: java.time.DayOfWeek? = null
        var quietestTotal = -1L
        for ((key, total) in byWeekday) {
            if (quietestTotal < 0L || total < quietestTotal) {
                quietest = key
                quietestTotal = total
            }
        }
        return quietest
    }
}
