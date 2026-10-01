/*
 * Copyright (C) 2021-2025 The DRS Smart Keyboard Project
 * Copyright (C) 2025 DRS Smart Keyboard contributors
 */

@file:OptIn(ExperimentalMaterial3Api::class)

package com.drs.smartkeyboard.drs.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import org.drs.jetpref.datastore.model.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.drs.smartkeyboard.R
import com.drs.smartkeyboard.app.DrsPreferenceStore
import com.drs.smartkeyboard.app.Routes
import com.drs.smartkeyboard.app.LocalNavController
import com.drs.smartkeyboard.drs.DrsEventLog
import com.drs.smartkeyboard.drs.DrsHybridViewMode
import com.drs.smartkeyboard.drs.DrsPerformance
import com.drs.smartkeyboard.drs.DrsProfileManager
import com.drs.smartkeyboard.drs.DrsStore
import com.drs.smartkeyboard.drs.DrsSystems
import com.drs.smartkeyboard.drs.DrsUnified
import com.drs.smartkeyboard.drs.DrsUnifiedTools
import com.drs.smartkeyboard.drs.DrsUserPath
import com.drs.smartkeyboard.lib.compose.DrsScreen
import org.drs.lib.compose.stringRes
import com.drs.smartkeyboard.lib.util.InputMethodUtils
import com.drs.smartkeyboard.subtypeManager
import com.drs.smartkeyboard.clipboardManager
import com.drs.smartkeyboard.themeManager

/**
 * DRS v1.0.7 — لوحة كلاهما (Unified Dashboard).
 *
 * The single control panel of the unified system: every number shown here
 * is MEASURED from live state (store, prefs, clipboard, performance
 * instrumentation) - never estimated - and every button performs a real
 * action. Display level switching (بسيط/تقني/مزدوج) is lossless: it only
 * changes visibility, never profiles, shortcuts, clipboard data or themes.
 */
@Composable
fun DrsUnifiedDashboardScreen() = DrsScreen {
    title = stringRes(R.string.drs__unified__dashboard_title)
    navigationIconVisible = true
    previewFieldVisible = false

    val navController = LocalNavController.current
    val context = LocalContext.current
    val prefs by DrsPreferenceStore

    val drsState by DrsStore.state.collectAsState()
    val isEnabled by InputMethodUtils.observeIsDrsKeyboardEnabled(foregroundOnly = true)
    val isSelected by InputMethodUtils.observeIsDrsKeyboardSelected(foregroundOnly = true)
    val subtypeManager by context.subtypeManager()
    val clipboardManager by context.clipboardManager()
    val subtypes by subtypeManager.subtypesFlow.collectAsState()
    val activeSubtype by subtypeManager.activeSubtypeFlow.collectAsState()
    val clipboardHistory by clipboardManager.historyFlow.collectAsState()
    val dayThemeId by prefs.theme.dayThemeId.collectAsState()
    val nightThemeId by prefs.theme.nightThemeId.collectAsState()
    val themeManager by context.themeManager()
    val dayThemeLabel = remember(dayThemeId) {
        themeManager.indexedThemeConfigs.value.first[dayThemeId]?.label ?: dayThemeId.toString()
    }
    val nightThemeLabel = remember(nightThemeId) {
        themeManager.indexedThemeConfigs.value.first[nightThemeId]?.label ?: nightThemeId.toString()
    }

    // Real gesture-binding count: gesture slots currently holding a
    // non-default action.
    val gestureSlots = listOf(
        prefs.gestures.swipeUp, prefs.gestures.swipeDown,
        prefs.gestures.swipeLeft, prefs.gestures.swipeRight,
        prefs.gestures.spaceBarSwipeUp, prefs.gestures.spaceBarSwipeLeft,
        prefs.gestures.spaceBarSwipeRight, prefs.gestures.spaceBarLongPress,
        prefs.gestures.deleteKeySwipeLeft, prefs.gestures.deleteKeyLongPress,
    )
    val boundGestures = gestureSlots.count { it.get() != com.drs.smartkeyboard.ime.text.gestures.SwipeAction.NO_ACTION }

    val activeSpec = DrsSystems.specOfName(drsState.userPath)
    val accent = if (isSystemInDarkTheme()) activeSpec.accentNight else activeSpec.accent
    val view = DrsUnifiedTools.viewForSystem(drsState.userPath, drsState.hybridViewMode)
    val activeProfile = DrsProfileManager.activeProfile(drsState)
    val tools = remember(
        view, drsState.unifiedToolOrder, drsState.hiddenUnifiedTools,
        drsState.pinnedUnifiedTools, drsState.unifiedToolViews,
    ) {
        DrsUnifiedTools.resolveFor(
            view, drsState.hiddenUnifiedTools, drsState.pinnedUnifiedTools,
            drsState.unifiedToolOrder, drsState.unifiedToolViews,
        )
    }

    content {
        // DRS fix (v1.0.9): DrsScreen already wraps the content in its own
        // vertical scroll (with the shared scrollbar) — a nested verticalScroll
        // here was measured with infinite height constraints and crashed the
        // screen on open ("Vertically scrollable component was measured with
        // an infinity maximum height constraints").
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // ---------------- system + level hero ----------------
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.6f),
                ),
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = stringRes(pathTitleRes(activeSpec)),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = accent,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = stringRes(
                            R.string.drs__unified__dashboard_profile_line,
                            "profile" to (activeProfile?.name ?: "-"),
                        ),
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = stringRes(R.string.drs__unified__level_title),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        DrsHybridViewMode.entries.forEach { mode ->
                            FilterChip(
                                selected = view == mode,
                                onClick = { DrsUnified.setViewMode(mode) },
                                label = {
                                    Text(
                                        when (mode) {
                                            DrsHybridViewMode.SIMPLE -> stringRes(R.string.drs__unified__level_simple)
                                            DrsHybridViewMode.ADVANCED -> stringRes(R.string.drs__unified__level_advanced)
                                            DrsHybridViewMode.DUAL -> stringRes(R.string.drs__unified__level_dual)
                                        }
                                    )
                                },
                            )
                        }
                    }
                    Text(
                        text = stringRes(R.string.drs__unified__level_switch_hint),
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // ---------------- keyboard status (live) ----------------
            UnifiedStatusCard(title = stringRes(R.string.drs__unified__dashboard_ime_status)) {
                // DRS v1.28.0 audit fix (UI consistency): state glyphs moved from
                // translated strings into themed vector icons (mirror of the
                // control center card).
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (isEnabled) Icons.Filled.Check else Icons.Filled.Close,
                        contentDescription = null,
                        tint = if (isEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringRes(
                            if (isEnabled) R.string.drs__control_center__ime_enabled
                            else R.string.drs__control_center__ime_not_enabled,
                        ),
                        fontSize = 14.sp,
                        color = if (isEnabled) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.error,
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (isSelected) Icons.Filled.Check else Icons.Filled.Close,
                        contentDescription = null,
                        tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringRes(
                            if (isSelected) R.string.drs__control_center__ime_selected
                            else R.string.drs__control_center__ime_not_selected,
                        ),
                        fontSize = 14.sp,
                    )
                }
                if (!isEnabled) {
                    OutlinedButton(onClick = { InputMethodUtils.showImeEnablerActivity(context) }) {
                        Text(stringRes(R.string.drs__control_center__enable_ime))
                    }
                } else if (!isSelected) {
                    OutlinedButton(onClick = { InputMethodUtils.showImePicker(context) }) {
                        Text(stringRes(R.string.drs__control_center__pick_ime))
                    }
                }
            }

            // ---------------- language / layout / theme (live) ----------------
            UnifiedStatusCard(title = stringRes(R.string.drs__unified__dashboard_language_theme)) {
                DashboardRow(
                    label = stringRes(R.string.drs__unified__dashboard_current_language),
                    value = activeSubtype.primaryLocale.base.displayName.ifBlank {
                        stringRes(R.string.drs__unified__dashboard_no_language)
                    },
                )
                DashboardRow(
                    label = stringRes(R.string.drs__unified__dashboard_layouts_count),
                    value = subtypes.size.toString(),
                )
                DashboardRow(
                    label = stringRes(R.string.drs__unified__dashboard_day_theme),
                    value = dayThemeLabel,
                )
                DashboardRow(
                    label = stringRes(R.string.drs__unified__dashboard_night_theme),
                    value = nightThemeLabel,
                )
            }

            // ---------------- active tools (live resolution) ----------------
            UnifiedStatusCard(title = stringRes(R.string.drs__unified__dashboard_active_tools)) {
                DashboardRow(
                    label = stringRes(R.string.drs__unified__dashboard_tools_in_strip),
                    value = tools.size.toString(),
                )
                DashboardRow(
                    label = stringRes(R.string.drs__unified__dashboard_tools_hidden),
                    value = drsState.hiddenUnifiedTools.size.toString(),
                )
                DashboardRow(
                    label = stringRes(R.string.drs__unified__dashboard_tools_pinned),
                    value = drsState.pinnedUnifiedTools.size.toString(),
                )
                TextButton(onClick = { navController.navigate(Routes.Settings.DrsUnifiedTools) }) {
                    Text(stringRes(R.string.drs__unified__dashboard_manage_tools))
                }
            }

            // ---------------- gestures / shortcuts / clipboard (live) ----------------
            UnifiedStatusCard(title = stringRes(R.string.drs__unified__dashboard_writing_section)) {
                DashboardRow(
                    label = stringRes(R.string.drs__unified__dashboard_bound_gestures),
                    value = boundGestures.toString(),
                )
                DashboardRow(
                    label = stringRes(R.string.drs__unified__dashboard_shortcuts_count),
                    value = stringRes(
                        R.string.drs__unified__dashboard_shortcuts_value,
                        "total" to drsState.shortcuts.size.toString(),
                        "enabled" to drsState.shortcuts.count { it.enabled }.toString(),
                    ),
                )
                DashboardRow(
                    label = stringRes(R.string.drs__unified__dashboard_clipboard_count),
                    value = clipboardHistory.all.size.toString(),
                )
                TextButton(onClick = { navController.navigate(Routes.Settings.DrsShortcuts) }) {
                    Text(stringRes(R.string.drs__shortcuts__title))
                }
            }

            // ---------------- performance (measured, never estimated) ----------------
            UnifiedStatusCard(title = stringRes(R.string.drs__unified__dashboard_performance)) {
                if (DrsPerformance.hasSamples()) {
                    DashboardRow(
                        label = stringRes(R.string.drs__unified__dashboard_key_latency_avg),
                        value = formatMicros(DrsPerformance.averageMicros()),
                    )
                    DashboardRow(
                        label = stringRes(R.string.drs__unified__dashboard_key_latency_p95),
                        value = formatMicros(DrsPerformance.percentileMicros(95)),
                    )
                } else {
                    Text(
                        text = stringRes(R.string.drs__unified__dashboard_no_samples),
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                val memory = DrsPerformance.memorySnapshot()
                DashboardRow(
                    label = stringRes(R.string.drs__unified__dashboard_memory),
                    value = formatBytes(memory.javaHeapUsedBytes),
                )
                DashboardRow(
                    label = stringRes(R.string.drs__unified__dashboard_state_file),
                    value = formatBytes(DrsStore.fileSizeBytes()),
                )
                DashboardRow(
                    label = stringRes(R.string.drs__unified__dashboard_log_events),
                    value = DrsEventLog.size().toString(),
                )
                Text(
                    text = stringRes(R.string.drs__unified__dashboard_measured_note),
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(onClick = { navController.navigate(Routes.Settings.DrsPerformance) }) {
                    Text(stringRes(R.string.drs__performance__title))
                }
            }

            // ---------------- quick links ----------------
            UnifiedStatusCard(title = stringRes(R.string.drs__unified__dashboard_quick_links)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = { navController.navigate(Routes.Settings.DrsUnifiedBasics) },
                        modifier = Modifier.weight(1f),
                    ) { Text(stringRes(R.string.drs__unified__basics_title), maxLines = 1) }
                    OutlinedButton(
                        onClick = { navController.navigate(Routes.Settings.DrsUnifiedWriting) },
                        modifier = Modifier.weight(1f),
                    ) { Text(stringRes(R.string.drs__unified__writing_title), maxLines = 1) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = { navController.navigate(Routes.Settings.DrsUnifiedSystem) },
                        modifier = Modifier.weight(1f),
                    ) { Text(stringRes(R.string.drs__unified__system_title), maxLines = 1) }
                    OutlinedButton(
                        onClick = { navController.navigate(Routes.Settings.DrsDiagnostics) },
                        modifier = Modifier.weight(1f),
                    ) { Text(stringRes(R.string.drs__diagnostics__title), maxLines = 1) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = { navController.navigate(Routes.Settings.Theme) },
                        modifier = Modifier.weight(1f),
                    ) { Text(stringRes(R.string.settings__theme__title), maxLines = 1) }
                    OutlinedButton(
                        onClick = { navController.navigate(Routes.Settings.DrsGestures) },
                        modifier = Modifier.weight(1f),
                    ) { Text(stringRes(R.string.drs__gestures__title), maxLines = 1) }
                }
                // DRS v1.0.8: daily usage statistics + performance quick links.
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = { navController.navigate(Routes.Settings.DrsUnifiedStats) },
                        modifier = Modifier.weight(1f),
                    ) { Text(stringRes(R.string.drs__unified__stats_title), maxLines = 1) }
                    OutlinedButton(
                        onClick = { navController.navigate(Routes.Settings.DrsPerformance) },
                        modifier = Modifier.weight(1f),
                    ) { Text(stringRes(R.string.drs__performance__title), maxLines = 1) }
                }
            }

            // ---------------- important switches (live) ----------------
            UnifiedStatusCard(title = stringRes(R.string.drs__unified__dashboard_important_switches)) {
                DashboardSwitchRow(
                    title = stringRes(R.string.drs__unified__system_advanced_controls),
                    summary = stringRes(R.string.drs__unified__system_advanced_controls_summary),
                    checked = drsState.advancedControlsEnabled,
                ) { DrsUnified.setAdvancedControls(it) }
                DashboardSwitchRow(
                    title = stringRes(R.string.drs__unified__tools_strip_for_normal),
                    summary = stringRes(R.string.drs__unified__tools_strip_for_normal_summary),
                    checked = drsState.unifiedStripForNormal,
                ) { DrsUnified.setStripForNormal(it) }
                DashboardSwitchRow(
                    title = stringRes(R.string.drs__control_center__shortcuts),
                    summary = stringRes(R.string.drs__shortcuts__enable_summary),
                    checked = drsState.shortcutsEnabled,
                ) { com.drs.smartkeyboard.drs.DrsShortcuts.setEnabled(it) }
            }

            Spacer(Modifier.height(8.dp))
        }
    }
}

/** Real-value formatter: microseconds of measured key latency. */
private fun formatMicros(micros: Long): String = "$micros µs"

/** Real-value formatter: byte counts (same scheme as the performance screen). */
private fun formatBytes(bytes: Long): String = when {
    bytes >= 1_048_576 -> "%.1f MB".format(bytes / 1_048_576.0)
    bytes >= 1_024 -> "%.1f KB".format(bytes / 1_024.0)
    else -> "$bytes B"
}

/** A titled status section of the unified dashboard. */
@Composable
private fun UnifiedStatusCard(
    title: String,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
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
            Spacer(Modifier.height(8.dp))
            content()
        }
    }
}

/** label: value row with real data. */
@Composable
private fun DashboardRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            modifier = Modifier.weight(1f),
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = value,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
        )
    }
}

@Composable
private fun DashboardSwitchRow(
    title: String,
    summary: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, fontSize = 14.sp)
            Text(
                text = summary,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
