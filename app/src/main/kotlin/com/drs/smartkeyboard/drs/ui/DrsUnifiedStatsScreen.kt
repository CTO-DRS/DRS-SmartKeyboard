/*
 * Copyright (C) 2025-2026 The DRS Smart Keyboard Project
 */

@file:OptIn(ExperimentalMaterial3Api::class)

package com.drs.smartkeyboard.drs.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.drs.smartkeyboard.R
import com.drs.smartkeyboard.drs.DrsContextMode
import com.drs.smartkeyboard.drs.DrsDailyStats
import com.drs.smartkeyboard.drs.DrsDayStats
import com.drs.smartkeyboard.drs.DrsStore
import com.drs.smartkeyboard.drs.DrsUnified
import com.drs.smartkeyboard.lib.compose.DrsScreen
import org.drs.lib.android.showShortToastSync
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import org.drs.lib.compose.stringRes

/**
 * DRS v1.0.8 — الإحصاءات اليومية (Unified Daily Usage Statistics).
 *
 * A REAL usage statistics screen: every number comes from the local daily
 * buckets recorded by the adaptation engine while typing (counts only —
 * never text, never leaves the device). The screen shows today, the
 * last-7-days aggregate, the recorded day list, a master on/off switch
 * that really stops the recording, and a local reset action.
 */
@Composable
fun DrsUnifiedStatsScreen() = DrsScreen {
    title = stringRes(R.string.drs__unified__stats_title)
    navigationIconVisible = true
    previewFieldVisible = false

    val context = LocalContext.current
    val drsState by DrsStore.state.collectAsState()
    var showResetDialog by remember { mutableStateOf(false) }

    // DRS v1.1.0: local CSV export of the recorded daily buckets through
    // SAF (user picks the destination; nothing leaves without a choice).
    val csvPayload = remember { mutableStateOf<String?>(null) }
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv"),
    ) { uri ->
        val payload = csvPayload.value
        if (uri != null && payload != null) {
            val ok = runCatching {
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    out.write(payload.toByteArray(Charsets.UTF_8))
                } ?: throw IllegalStateException("output stream null")
            }.isSuccess
            context.showShortToastSync(
                if (ok) R.string.drs__unified__stats_export_done
                else R.string.drs__unified__stats_export_failed,
            )
        }
        csvPayload.value = null
    }

    content {
        // ---------------- recording switch (real) ----------------
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.6f),
            ),
        ) {
            Row(
                modifier = Modifier.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringRes(R.string.drs__unified__stats_enable_switch),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = stringRes(R.string.drs__unified__stats_enable_summary),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = drsState.dailyStatsEnabled,
                    onCheckedChange = { DrsUnified.setDailyStatsEnabled(it) },
                )
            }
        }

        Text(
            text = stringRes(R.string.drs__unified__stats_privacy_note),
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 2.dp),
        )

        if (!drsState.dailyStatsEnabled) {
            Text(
                text = stringRes(R.string.drs__unified__stats_disabled_hint),
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            )
            return@content
        }

        val today = DrsDailyStats.todayStamp()
        val todayStats = drsState.dailyStats[today] ?: DrsDayStats(day = today)
        val last7 = DrsDailyStats.sum(DrsDailyStats.lastDays(drsState.dailyStats, 7, today))

        // ---------------- today ----------------
        StatsCard(title = stringRes(R.string.drs__unified__stats_today)) {
            StatsRow(stringRes(R.string.drs__unified__stats_keys), todayStats.keyPresses)
            StatsRow(stringRes(R.string.drs__unified__stats_numbers), todayStats.numberPresses)
            StatsRow(stringRes(R.string.drs__unified__stats_symbols), todayStats.symbolPresses)
            StatsRow(stringRes(R.string.drs__unified__stats_tools), todayStats.toolUses)
            StatsRow(stringRes(R.string.drs__unified__stats_tech_tools), todayStats.techToolUses)
            StatsRow(stringRes(R.string.drs__unified__stats_gestures), todayStats.gestureUses)
            StatsRow(stringRes(R.string.drs__unified__stats_emoji), todayStats.emojiUses)
            StatsRow(stringRes(R.string.drs__unified__stats_clipboard), todayStats.clipboardUses)
            StatsRow(stringRes(R.string.drs__unified__stats_shortcuts), todayStats.shortcutUses)
            // DRS v1.6.0: committed suggestion-row entries.
            StatsRow(stringRes(R.string.drs__unified__stats_suggestion_accepts), todayStats.suggestionAccepts)
            if (!dayHasActivity(todayStats)) {
                Text(
                    text = stringRes(R.string.drs__unified__stats_empty),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }

        // ---------------- last 7 days ----------------
        StatsCard(title = stringRes(R.string.drs__unified__stats_last7)) {
            StatsRow(stringRes(R.string.drs__unified__stats_keys), last7.keyPresses)
            StatsRow(stringRes(R.string.drs__unified__stats_tools), last7.toolUses)
            StatsRow(stringRes(R.string.drs__unified__stats_gestures), last7.gestureUses)
            StatsRow(stringRes(R.string.drs__unified__stats_emoji), last7.emojiUses)
            StatsRow(stringRes(R.string.drs__unified__stats_clipboard), last7.clipboardUses)
        }

        // ---------------- all-time totals (DRS v1.2.0) ----------------
        // Every number is a pure aggregation of the local day buckets —
        // the same counters the rest of this screen renders.
        val totalAll = DrsDailyStats.sum(drsState.dailyStats.values)
        val best = DrsDailyStats.bestDay(drsState.dailyStats)
        val streak = DrsDailyStats.currentStreak(drsState.dailyStats, today)
        // DRS v1.3.0: week-over-week growth from two real 7-day windows.
        val wowPercent = DrsDailyStats.lastWeeksGrowthPercent(drsState.dailyStats, today)
        // DRS v1.4.0: real active-day count + per-active-day average.
        val activeDays = DrsDailyStats.activeDaysCount(drsState.dailyStats)
        val dailyAvg = DrsDailyStats.dailyAveragePresses(drsState.dailyStats)
        // DRS v1.5.0: all-time best streak, the busiest weekday of the
        // recorded window, and how many days that window spans.
        val longestStreak = DrsDailyStats.longestStreak(drsState.dailyStats)
        val busiestWeekday = DrsDailyStats.busiestWeekday(drsState.dailyStats)
        val recordedSpan = DrsDailyStats.recordedSpanDays(drsState.dailyStats, today)
        // DRS v1.6.0: missed days inside the recorded window, the active
        // share of it, and the quietest typing weekday (all pure).
        val missedDays = DrsDailyStats.missedDaysCount(drsState.dailyStats, today)
        val activeRatio = DrsDailyStats.activeDayRatioPercent(drsState.dailyStats, today)
        val quietestWeekday = DrsDailyStats.quietestWeekday(drsState.dailyStats)
        // DRS v1.7.0: how input starts split across the detected context
        // modes (password/numbers/coding/…) — the mode was detected all
        // along but never recorded; now the top of the real distribution
        // shows here (all pure aggregations of the same buckets).
        val topContexts = DrsDailyStats.topContextModes(drsState.dailyStats.values)
        StatsCard(title = stringRes(R.string.drs__unified__stats_totals_title)) {
            StatsRow(stringRes(R.string.drs__unified__stats_keys), totalAll.keyPresses)
            StatsRow(stringRes(R.string.drs__unified__stats_numbers), totalAll.numberPresses)
            StatsRow(stringRes(R.string.drs__unified__stats_symbols), totalAll.symbolPresses)
            StatsRow(stringRes(R.string.drs__unified__stats_tools), totalAll.toolUses)
            StatsRow(stringRes(R.string.drs__unified__stats_tech_tools), totalAll.techToolUses)
            StatsRow(stringRes(R.string.drs__unified__stats_gestures), totalAll.gestureUses)
            StatsRow(stringRes(R.string.drs__unified__stats_emoji), totalAll.emojiUses)
            StatsRow(stringRes(R.string.drs__unified__stats_clipboard), totalAll.clipboardUses)
            StatsRow(stringRes(R.string.drs__unified__stats_shortcuts), totalAll.shortcutUses)
            // DRS v1.6.0: committed suggestion-row entries.
            StatsRow(stringRes(R.string.drs__unified__stats_suggestion_accepts), totalAll.suggestionAccepts)
            // DRS v1.4.0: recorded active days and the mean presses of
            // those days — both pure aggregations of the same buckets.
            StatsRow(stringRes(R.string.drs__unified__stats_active_days), activeDays.toLong())
            StatsRow(stringRes(R.string.drs__unified__stats_daily_average), dailyAvg)
            // DRS v1.5.0: longest streak, busiest weekday and the span of
            // the recorded window (all pure aggregations, hidden when the
            // data cannot answer them).
            if (longestStreak > 0) {
                StatsRow(stringRes(R.string.drs__unified__stats_longest_streak), longestStreak.toLong())
            }
            if (busiestWeekday != null) {
                Text(
                    text = stringRes(
                        R.string.drs__unified__stats_busiest_weekday,
                        "day" to busiestWeekday.getDisplayName(
                            java.time.format.TextStyle.FULL,
                            java.util.Locale.getDefault(),
                        ),
                    ),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (recordedSpan != null && recordedSpan > 0) {
                Text(
                    text = stringRes(
                        R.string.drs__unified__stats_recorded_span,
                        "days" to recordedSpan.toString(),
                    ),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            // DRS v1.6.0: missed days + the active share of the window.
            if (missedDays != null) {
                Text(
                    text = stringRes(
                        R.string.drs__unified__stats_missed_days,
                        "days" to missedDays.toString(),
                    ),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (activeRatio != null) {
                Text(
                    text = stringRes(
                        R.string.drs__unified__stats_active_ratio,
                        "ratio" to formatRatio(activeRatio),
                    ),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (quietestWeekday != null) {
                Text(
                    text = stringRes(
                        R.string.drs__unified__stats_quietest_weekday,
                        "day" to quietestWeekday.getDisplayName(
                            java.time.format.TextStyle.FULL,
                            java.util.Locale.getDefault(),
                        ),
                    ),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            // DRS v1.7.0: the real context-mode mix — hidden when no input
            // start was ever recorded so an empty install stays clean.
            if (topContexts.isNotEmpty()) {
                // @Composable display names resolve in this scope; the for
                // loop (not a lambda) keeps the calls composable-legal.
                val parts = ArrayList<String>(topContexts.size)
                for ((mode, count) in topContexts) {
                    parts.add(contextModeDisplayName(mode) + " " + count)
                }
                Text(
                    text = stringRes(
                        R.string.drs__unified__stats_context_mix,
                        "mix" to parts.joinToString(" · "),
                    ),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (wowPercent != null) {
                Text(
                    text = stringRes(
                        R.string.drs__unified__stats_week_over_week,
                        "delta" to formatGrowth(wowPercent),
                    ),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            Text(
                text = stringRes(
                    R.string.drs__unified__stats_streak,
                    "count" to streak.toString(),
                ),
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 8.dp),
            )
            if (best != null) {
                Text(
                    text = stringRes(
                        R.string.drs__unified__stats_best_day,
                        "date" to formatDayLabel(best.day),
                        "keys" to best.keyPresses.toString(),
                    ),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // ---------------- 14-day bar chart (DRS v1.1.0) ----------------
        val chartDays = DrsDailyStats.lastDaysZeroFilled(drsState.dailyStats, 14, today)
        StatsCard(title = stringRes(R.string.drs__unified__stats_chart_title)) {
            StatsBarChart(buckets = chartDays)
            Spacer(Modifier.height(6.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = formatDayLabel(chartDays.first().day),
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = formatDayLabel(chartDays.last().day),
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            val peak = chartDays.maxByOrNull { it.keyPresses }
            if (peak != null && peak.keyPresses > 0) {
                Text(
                    text = stringRes(
                        R.string.drs__unified__stats_chart_peak,
                        "date" to formatDayLabel(peak.day),
                        "keys" to peak.keyPresses.toString(),
                    ),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }

        // ---------------- per-day list ----------------
        val recordedDays = DrsDailyStats.lastDays(drsState.dailyStats, 14, today)
        if (recordedDays.isNotEmpty()) {
            StatsCard(title = stringRes(R.string.drs__unified__stats_days_list)) {
                recordedDays.forEach { bucket ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = formatDayLabel(bucket.day),
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = stringRes(
                                R.string.drs__unified__stats_day_line,
                                "keys" to bucket.keyPresses.toString(),
                                "tools" to (bucket.toolUses + bucket.techToolUses).toString(),
                            ),
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        // ---------------- local actions: export + reset ----------------
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(
                onClick = {
                    // DRS v1.5.0: the CSV is now a CONTINUOUS ascending
                    // zero-filled window across the full retention span
                    // (exactly the documented lastDaysZeroFilled contract),
                    // so exported timelines have no missing-day gaps. The
                    // empty toast still keys on real activity.
                    val hasAnyActivity = drsState.dailyStats.values.any { dayHasActivity(it) }
                    if (!hasAnyActivity) {
                        context.showShortToastSync(R.string.drs__unified__stats_export_empty)
                    } else {
                        val window = DrsDailyStats.lastDaysZeroFilled(
                            drsState.dailyStats,
                            DrsDailyStats.KEEP_DAYS,
                            today,
                        )
                        csvPayload.value = buildStatsCsv(window)
                        exportLauncher.launch("drs-daily-stats-" + csvStamp() + ".csv")
                    }
                },
                modifier = Modifier.weight(1f),
            ) {
                Text(stringRes(R.string.drs__unified__stats_export))
            }
            OutlinedButton(
                onClick = { showResetDialog = true },
                modifier = Modifier.weight(1f),
            ) {
                Text(stringRes(R.string.drs__unified__stats_reset))
            }
        }
        Spacer(Modifier.height(8.dp))

        if (showResetDialog) {
            AlertDialog(
                onDismissRequest = { showResetDialog = false },
                title = { Text(stringRes(R.string.drs__unified__stats_reset)) },
                text = { Text(stringRes(R.string.drs__unified__stats_reset_confirm)) },
                confirmButton = {
                    TextButton(
                        onClick = {
                            DrsUnified.resetDailyStats()
                            showResetDialog = false
                        },
                    ) { Text(stringRes(R.string.action__ok)) }
                },
                dismissButton = {
                    TextButton(onClick = { showResetDialog = false }) {
                        Text(stringRes(R.string.action__cancel))
                    }
                },
            )
        }
    }
}

/** ISO day stamp -> localized readable label (weekday + date). */
private fun formatDayLabel(isoDay: String): String {
    return try {
        val date = LocalDate.parse(isoDay)
        date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
    } catch (_: Throwable) {
        isoDay
    }
}

/** File-stamp for exported CSV names (locale-independent digits). */
private fun csvStamp(): String = SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date())

/**
 * DRS v1.3.0: renders the week-over-week growth with a real sign and
 * locale-independent digits (e.g. "+12.5%" / "-4.0%").
 */
private fun formatGrowth(percent: Double): String {
    val rounded = (percent * 10).toLong() / 10.0
    val sign = if (rounded >= 0) "+" else "-"
    return sign + kotlin.math.abs(rounded) + "%"
}

/**
 * DRS v1.6.0: renders a percentage with one decimal and locale-independent
 * digits (e.g. "83.3%"), mirroring [formatGrowth].
 */
private fun formatRatio(percent: Double): String {
    val rounded = (percent * 10).toLong() / 10.0
    return rounded.toString() + "%"
}

/**
 * DRS v1.7.0: localized display name of a context-mode enum name (the
 * stats surface shows real names, never raw enum tokens). Falls back to
 * the raw name for unknown tokens so a future mode never renders empty.
 */
@Composable
private fun contextModeDisplayName(modeName: String): String = when (modeName) {
    DrsContextMode.NORMAL.name -> stringRes(R.string.drs__context_mode__normal)
    DrsContextMode.CHAT.name -> stringRes(R.string.drs__context_mode__chat)
    DrsContextMode.WRITING.name -> stringRes(R.string.drs__context_mode__writing)
    DrsContextMode.CODING.name -> stringRes(R.string.drs__context_mode__coding)
    DrsContextMode.NUMBERS.name -> stringRes(R.string.drs__context_mode__numbers)
    DrsContextMode.SEARCH.name -> stringRes(R.string.drs__context_mode__search)
    DrsContextMode.PASSWORD.name -> stringRes(R.string.drs__context_mode__password)
    DrsContextMode.TECHNICAL.name -> stringRes(R.string.drs__context_mode__technical)
    else -> modeName
}

/**
 * DRS v1.1.0: builds the CSV payload of the recorded day buckets
 * (ascending). Counts only — the file can never contain typed text.
 */
private fun buildStatsCsv(buckets: List<DrsDayStats>): String {
    val header = "day,key_presses,number_presses,symbol_presses,tool_uses,tech_tool_uses,gesture_uses,emoji_uses,clipboard_uses,shortcut_uses,suggestion_accepts,context_starts"
    return header + "\n" + buckets.joinToString("\n") { b ->
        listOf(
            b.day,
            b.keyPresses,
            b.numberPresses,
            b.symbolPresses,
            b.toolUses,
            b.techToolUses,
            b.gestureUses,
            b.emojiUses,
            b.clipboardUses,
            b.shortcutUses,
            b.suggestionAccepts,
            // DRS v1.7.0: the day's full context-start map, compact
            // 'mode:count;mode:count' — empty cell when nothing recorded.
            b.contextStarts.entries
                .sortedWith(compareByDescending<Map.Entry<String, Long>> { it.value }.thenBy { it.key })
                .joinToString(";") { "${it.key}:${it.value}" },
        ).joinToString(",")
    } + "\n"
}

/**
 * DRS v1.1.0: real bar chart of the daily key presses (ascending window).
 * Drawn on Canvas from the local counters only; today's bar is emphasized.
 */
@Composable
private fun StatsBarChart(buckets: List<DrsDayStats>) {
    if (buckets.isEmpty()) return
    val barColor = MaterialTheme.colorScheme.primary
    val fadedColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
    val baselineColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
    val maxValue = buckets.maxOf { it.keyPresses }.coerceAtLeast(1L)

    Canvas(modifier = Modifier.fillMaxWidth().height(120.dp)) {
        val slot = size.width / buckets.size
        val barWidth = slot * 0.62f
        val gap = (slot - barWidth) / 2f
        val chartHeight = size.height * 0.94f
        // baseline
        drawLine(
            color = baselineColor,
            start = Offset(0f, size.height - 1f),
            end = Offset(size.width, size.height - 1f),
            strokeWidth = 1f,
        )
        buckets.forEachIndexed { index, bucket ->
            val value = bucket.keyPresses.coerceAtLeast(0L).toFloat()
            val barHeight = (value / maxValue.toFloat()) * chartHeight
            if (barHeight > 0f) {
                drawRoundRect(
                    color = if (index == buckets.lastIndex) barColor else fadedColor,
                    topLeft = Offset(index * slot + gap, size.height - barHeight),
                    size = Size(barWidth, barHeight),
                    cornerRadius = CornerRadius(barWidth / 3f, barWidth / 3f),
                )
            }
        }
    }
}

/** True when at least one counter is non-zero (member extension via DrsDailyStats). */
private fun dayHasActivity(bucket: DrsDayStats): Boolean = with(DrsDailyStats) { bucket.hasActivity() }

/** A titled statistics section. */
@Composable
private fun StatsCard(
    title: String,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.6f),
        ),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = title,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(4.dp))
            content()
        }
    }
}

/** One label:value statistics line with real numbers only. */
@Composable
private fun StatsRow(label: String, value: Long) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(text = label, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface)
        Text(
            text = value.toString(),
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
