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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.drs.smartkeyboard.R
import com.drs.smartkeyboard.app.Routes
import com.drs.smartkeyboard.drs.DrsHybridViewMode
import com.drs.smartkeyboard.drs.DrsSettingScope
import com.drs.smartkeyboard.drs.DrsStore
import com.drs.smartkeyboard.drs.DrsSystems
import com.drs.smartkeyboard.drs.DrsToolGroup
import com.drs.smartkeyboard.drs.DrsToolView
import com.drs.smartkeyboard.drs.DrsUnified
import com.drs.smartkeyboard.drs.DrsUnifiedTools
import com.drs.smartkeyboard.lib.compose.DrsScreen
import org.drs.lib.compose.stringRes
import androidx.navigation.NavHostController
import com.drs.smartkeyboard.app.LocalNavController

/**
 * DRS v1.0.7 — مركز الأدوات الذكية الموحد.
 *
 * Full manager over the unified strip's catalogue: every tool here is a
 * real keyboard action (see [DrsUnifiedTools]), and every control on this
 * screen really changes the strip:
 *  - إظهار/إخفاء per tool, تثبيت to the strip head, and ترتيب up/down.
 *  - نطاق الظهور per tool: العادي / التقني / كلاهما.
 *  - إظهار الشريط للوضع العادي (opt-in) and إعادة الضبط to defaults.
 *
 * The advanced technical keys (Tab, braces, arrows…) keep their dedicated
 * editor and ride along in the advanced/dual levels.
 */
@Composable
fun DrsUnifiedToolsScreen() = DrsScreen {
    title = stringRes(R.string.drs__unified__tools_title)
    navigationIconVisible = true
    previewFieldVisible = false

    val navController = LocalNavController.current
    val drsState by DrsStore.state.collectAsState()
    val spec = DrsSystems.specOfName(drsState.userPath)
    val accent = if (isSystemInDarkTheme()) spec.accentNight else spec.accent
    val view = DrsUnifiedTools.viewForSystem(drsState.userPath, drsState.hybridViewMode)
    val resolved = remember(
        view, drsState.unifiedToolOrder, drsState.hiddenUnifiedTools,
        drsState.pinnedUnifiedTools, drsState.unifiedToolViews,
    ) {
        DrsUnifiedTools.resolveFor(
            view, drsState.hiddenUnifiedTools, drsState.pinnedUnifiedTools,
            drsState.unifiedToolOrder, drsState.unifiedToolViews,
        )
    }

    content {
        // DRS fix (v1.0.9): DrsScreen already scrolls — a nested verticalScroll
        // here was measured with infinite height constraints and crashed the
        // screen on open.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // ---------------- intro ----------------
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.6f),
                ),
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = stringRes(R.string.drs__unified__tools_hint),
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    // DRS v1.16.0: the bar is now the FIXED ten-slot strip.
                    Text(
                        text = stringRes(R.string.drs__unified__bar_slots_hint),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = accent,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = stringRes(
                            R.string.drs__unified__tools_live_count,
                            "count" to resolved.size.toString(),
                        ),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = accent,
                    )
                }
            }

            // ---------------- level selector ----------------
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.6f),
                ),
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = stringRes(R.string.drs__unified__level_title),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
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
                }
            }

            // ---------------- tasks-bar master switch ----------------
            // DRS v1.8.0: the bar above the suggestions strip now applies
            // to ALL three systems; this is its single honest switch (also
            // available inside the keyboard from the tools drawer).
            ToolSwitchCard(
                title = stringRes(R.string.drs__tools_drawer__bar_switch),
                summary = stringRes(R.string.drs__tools_drawer__bar_switch_summary),
                checked = drsState.unifiedStripEnabled,
                onChange = { DrsUnified.setStripEnabled(it) },
            )

            // ---------------- catalogue by group ----------------
            DrsToolGroup.entries.forEach { group ->
                val groupTools = DrsUnifiedTools.ALL.filter { it.group == group }
                if (groupTools.isEmpty()) return@forEach
                Text(
                    text = stringRes(
                        when (group) {
                            DrsToolGroup.TOOLS -> R.string.drs__unified__group_tools
                            DrsToolGroup.EDITING -> R.string.drs__unified__group_editing
                            DrsToolGroup.CURSOR -> R.string.drs__unified__group_cursor
                        },
                    ),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 4.dp, top = 4.dp),
                )
                groupTools.forEach { tool ->
                    UnifiedToolRow(
                        tool = tool,
                        hidden = tool.id in drsState.hiddenUnifiedTools,
                        pinned = tool.id in drsState.pinnedUnifiedTools,
                        viewOverride = drsState.unifiedToolViews[tool.id],
                    )
                }
            }

            // ---------------- advanced keys note ----------------
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.6f),
                ),
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = stringRes(R.string.drs__unified__tools_advanced_keys_title),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = stringRes(R.string.drs__unified__tools_advanced_keys_body),
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    TextButton(onClick = { navController.navigate(Routes.Settings.DrsTechToolbar) }) {
                        Text(stringRes(R.string.drs__tech_toolbar__title))
                    }
                }
            }

            // ---------------- reset ----------------
            OutlinedButton(
                modifier = Modifier.fillMaxWidth(),
                onClick = { DrsUnified.resetCustomization() },
            ) {
                Text(stringRes(R.string.drs__unified__tools_reset))
            }

            Spacer(Modifier.height(8.dp))
        }
    }
}

/** One catalogue tool row with live controls. */
@Composable
private fun UnifiedToolRow(
    tool: com.drs.smartkeyboard.drs.DrsUnifiedTool,
    hidden: Boolean,
    pinned: Boolean,
    viewOverride: String?,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.6f),
        ),
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = toolTitle(tool.id),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface.copy(
                                alpha = if (hidden) 0.5f else 1f,
                            ),
                        )
                        Spacer(Modifier.padding(horizontal = 4.dp))
                        Text(
                            text = stringRes(
                                when (tool.scope) {
                                    DrsSettingScope.BASIC -> R.string.drs__unified__scope_basic
                                    DrsSettingScope.ADVANCED -> R.string.drs__unified__scope_advanced
                                    DrsSettingScope.SHARED -> R.string.drs__unified__scope_shared
                                },
                            ),
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.tertiary,
                        )
                    }
                    Text(
                        text = toolDesc(tool.id),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                // pin toggle
                IconButton(onClick = { DrsUnified.setToolPinned(tool.id, !pinned) }) {
                    Icon(
                        imageVector = if (pinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                        contentDescription = stringRes(R.string.drs__unified__tools_pin),
                        tint = if (pinned) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                }
                // reorder
                IconButton(onClick = { DrsUnified.moveTool(tool.id, up = true) }) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowUp,
                        contentDescription = stringRes(R.string.drs__unified__tools_move_up),
                        modifier = Modifier.size(20.dp),
                    )
                }
                IconButton(onClick = { DrsUnified.moveTool(tool.id, up = false) }) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = stringRes(R.string.drs__unified__tools_move_down),
                        modifier = Modifier.size(20.dp),
                    )
                }
                // visibility switch
                Switch(
                    checked = !hidden,
                    onCheckedChange = { checked -> DrsUnified.setToolHidden(tool.id, !checked) },
                )
            }
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                DrsToolView.entries.forEach { option ->
                    FilterChip(
                        selected = viewOverride?.let { it == option.name }
                            ?: (tool.defaultView == option),
                        onClick = { DrsUnified.setToolView(tool.id, option) },
                        label = {
                            Text(
                                when (option) {
                                    DrsToolView.NORMAL -> stringRes(R.string.drs__unified__view_normal)
                                    DrsToolView.TECHNICAL -> stringRes(R.string.drs__unified__view_technical)
                                    DrsToolView.BOTH -> stringRes(R.string.drs__unified__view_both)
                                },
                                fontSize = 11.sp,
                            )
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun ToolSwitchCard(
    title: String,
    summary: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.6f),
        ),
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    text = summary,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = checked, onCheckedChange = onChange)
        }
    }
}

/** Localized title per catalogue tool id (shared with the IME tools drawer). */
@Composable
fun toolTitle(id: String): String = when (id) {
    "emoji" -> stringRes(R.string.drs__unified__tool_emoji)
    "clipboard" -> stringRes(R.string.drs__unified__tool_clipboard)
    "text_tools" -> stringRes(R.string.drs__unified__tool_text_tools)
    "numbers" -> stringRes(R.string.drs__unified__tool_numbers)
    "symbols" -> stringRes(R.string.drs__unified__tool_symbols)
    "language" -> stringRes(R.string.drs__unified__tool_language)
    "settings" -> stringRes(R.string.drs__unified__tool_settings)
    "paste" -> stringRes(R.string.drs__unified__tool_paste)
    "share" -> stringRes(R.string.drs__unified__tool_share)
    "undo" -> stringRes(R.string.drs__unified__tool_undo)
    "redo" -> stringRes(R.string.drs__unified__tool_redo)
    "select_all" -> stringRes(R.string.drs__unified__tool_select_all)
    "copy" -> stringRes(R.string.drs__unified__tool_copy)
    "cut" -> stringRes(R.string.drs__unified__tool_cut)
    "select_word" -> stringRes(R.string.drs__unified__tool_select_word)
    "word_left" -> stringRes(R.string.drs__unified__tool_word_left)
    "word_right" -> stringRes(R.string.drs__unified__tool_word_right)
    "line_start" -> stringRes(R.string.drs__unified__tool_line_start)
    "line_end" -> stringRes(R.string.drs__unified__tool_line_end)
    "delete_word" -> stringRes(R.string.drs__unified__tool_delete_word)
    "hide_keyboard" -> stringRes(R.string.drs__unified__tool_hide_keyboard)
    // DRS v1.0.8
    "theme_cycle" -> stringRes(R.string.drs__unified__tool_theme_cycle)
    "insert_date_time" -> stringRes(R.string.drs__unified__tool_insert_date_time)
    "text_start" -> stringRes(R.string.drs__unified__tool_text_start)
    "text_end" -> stringRes(R.string.drs__unified__tool_text_end)
    // DRS v1.1.0
    "one_handed" -> stringRes(R.string.drs__unified__tool_one_handed)
    "number_row" -> stringRes(R.string.drs__unified__tool_number_row)
    // DRS v1.2.0
    "incognito" -> stringRes(R.string.drs__unified__tool_incognito)
    "autocorrect" -> stringRes(R.string.drs__unified__tool_autocorrect)
    // DRS v1.3.0
    "clipboard_clear" -> stringRes(R.string.drs__unified__tool_clipboard_clear)
    "voice_input" -> stringRes(R.string.drs__unified__tool_voice_input)
    // DRS v1.4.0
    "floating_mode" -> stringRes(R.string.drs__unified__tool_floating_mode)
    "smartbar_toggle" -> stringRes(R.string.drs__unified__tool_smartbar_toggle)
    // DRS v1.5.0
    "clipboard_history_clear" -> stringRes(R.string.drs__unified__tool_clipboard_history_clear)
    "next_language" -> stringRes(R.string.drs__unified__tool_next_language)
    "resize_mode" -> stringRes(R.string.drs__unified__tool_resize_mode)
    // DRS v1.6.0
    "clipboard_full_clear" -> stringRes(R.string.drs__unified__tool_clipboard_full_clear)
    "prev_language" -> stringRes(R.string.drs__unified__tool_prev_language)
    "one_handed_left" -> stringRes(R.string.drs__unified__tool_one_handed_left)
    "one_handed_right" -> stringRes(R.string.drs__unified__tool_one_handed_right)
    "next_keyboard_app" -> stringRes(R.string.drs__unified__tool_next_keyboard_app)
    // DRS v1.7.0
    "clipboard_pin" -> stringRes(R.string.drs__unified__tool_clipboard_pin)
    // DRS v1.8.0
    "quick_actions" -> stringRes(R.string.drs__unified__tool_quick_actions)
    "actions_editor" -> stringRes(R.string.drs__unified__tool_actions_editor)
    // DRS v1.15.0
    "diacritics_panel" -> stringRes(R.string.drs__unified__tool_diacritics_panel)
    "smart_symbols" -> stringRes(R.string.drs__unified__tool_smart_symbols)
    "arabic_letters" -> stringRes(R.string.drs__unified__tool_arabic_letters)
    // DRS v1.19.0
    "split_keyboard" -> stringRes(R.string.drs__unified__tool_split_keyboard)
    "merge_keyboard" -> stringRes(R.string.drs__unified__tool_merge_keyboard)
    // DRS v1.2.0 (الإصدار العام): لوحة الأرقام الذكية
    "smart_number_panel" -> stringRes(R.string.drs__unified__tool_smart_number_panel)
    // DRS v1.3.0 (الإصدار العام): لوحة الحافظة الذكية
    "smart_clipboard_panel" -> stringRes(R.string.drs__unified__tool_smart_clipboard_panel)
    else -> id
}

/** Localized description per catalogue tool id (what it really does). */
@Composable
fun toolDesc(id: String): String = when (id) {
    "emoji" -> stringRes(R.string.drs__unified__tool_emoji_desc)
    "clipboard" -> stringRes(R.string.drs__unified__tool_clipboard_desc)
    "text_tools" -> stringRes(R.string.drs__unified__tool_text_tools_desc)
    "numbers" -> stringRes(R.string.drs__unified__tool_numbers_desc)
    "symbols" -> stringRes(R.string.drs__unified__tool_symbols_desc)
    "language" -> stringRes(R.string.drs__unified__tool_language_desc)
    "settings" -> stringRes(R.string.drs__unified__tool_settings_desc)
    "paste" -> stringRes(R.string.drs__unified__tool_paste_desc)
    "share" -> stringRes(R.string.drs__unified__tool_share_desc)
    "undo" -> stringRes(R.string.drs__unified__tool_undo_desc)
    "redo" -> stringRes(R.string.drs__unified__tool_redo_desc)
    "select_all" -> stringRes(R.string.drs__unified__tool_select_all_desc)
    "copy" -> stringRes(R.string.drs__unified__tool_copy_desc)
    "cut" -> stringRes(R.string.drs__unified__tool_cut_desc)
    "select_word" -> stringRes(R.string.drs__unified__tool_select_word_desc)
    "word_left" -> stringRes(R.string.drs__unified__tool_word_left_desc)
    "word_right" -> stringRes(R.string.drs__unified__tool_word_right_desc)
    "line_start" -> stringRes(R.string.drs__unified__tool_line_start_desc)
    "line_end" -> stringRes(R.string.drs__unified__tool_line_end_desc)
    "delete_word" -> stringRes(R.string.drs__unified__tool_delete_word_desc)
    "hide_keyboard" -> stringRes(R.string.drs__unified__tool_hide_keyboard_desc)
    // DRS v1.0.8
    "theme_cycle" -> stringRes(R.string.drs__unified__tool_theme_cycle_desc)
    "insert_date_time" -> stringRes(R.string.drs__unified__tool_insert_date_time_desc)
    "text_start" -> stringRes(R.string.drs__unified__tool_text_start_desc)
    "text_end" -> stringRes(R.string.drs__unified__tool_text_end_desc)
    // DRS v1.1.0
    "one_handed" -> stringRes(R.string.drs__unified__tool_one_handed_desc)
    "number_row" -> stringRes(R.string.drs__unified__tool_number_row_desc)
    // DRS v1.2.0
    "incognito" -> stringRes(R.string.drs__unified__tool_incognito_desc)
    "autocorrect" -> stringRes(R.string.drs__unified__tool_autocorrect_desc)
    // DRS v1.3.0
    "clipboard_clear" -> stringRes(R.string.drs__unified__tool_clipboard_clear_desc)
    "voice_input" -> stringRes(R.string.drs__unified__tool_voice_input_desc)
    // DRS v1.4.0
    "floating_mode" -> stringRes(R.string.drs__unified__tool_floating_mode_desc)
    "smartbar_toggle" -> stringRes(R.string.drs__unified__tool_smartbar_toggle_desc)
    // DRS v1.5.0
    "clipboard_history_clear" -> stringRes(R.string.drs__unified__tool_clipboard_history_clear_desc)
    "next_language" -> stringRes(R.string.drs__unified__tool_next_language_desc)
    "resize_mode" -> stringRes(R.string.drs__unified__tool_resize_mode_desc)
    // DRS v1.6.0
    "clipboard_full_clear" -> stringRes(R.string.drs__unified__tool_clipboard_full_clear_desc)
    "prev_language" -> stringRes(R.string.drs__unified__tool_prev_language_desc)
    "one_handed_left" -> stringRes(R.string.drs__unified__tool_one_handed_left_desc)
    "one_handed_right" -> stringRes(R.string.drs__unified__tool_one_handed_right_desc)
    "next_keyboard_app" -> stringRes(R.string.drs__unified__tool_next_keyboard_app_desc)
    // DRS v1.7.0
    "clipboard_pin" -> stringRes(R.string.drs__unified__tool_clipboard_pin_desc)
    // DRS v1.8.0
    "quick_actions" -> stringRes(R.string.drs__unified__tool_quick_actions_desc)
    "actions_editor" -> stringRes(R.string.drs__unified__tool_actions_editor_desc)
    // DRS v1.15.0
    "diacritics_panel" -> stringRes(R.string.drs__unified__tool_diacritics_panel_desc)
    "smart_symbols" -> stringRes(R.string.drs__unified__tool_smart_symbols_desc)
    "arabic_letters" -> stringRes(R.string.drs__unified__tool_arabic_letters_desc)
    // DRS v1.19.0
    "split_keyboard" -> stringRes(R.string.drs__unified__tool_split_keyboard_desc)
    "merge_keyboard" -> stringRes(R.string.drs__unified__tool_merge_keyboard_desc)
    // DRS v1.2.0 (الإصدار العام): لوحة الأرقام الذكية
    "smart_number_panel" -> stringRes(R.string.drs__unified__tool_smart_number_panel_desc)
    // DRS v1.3.0 (الإصدار العام): لوحة الحافظة الذكية
    "smart_clipboard_panel" -> stringRes(R.string.drs__unified__tool_smart_clipboard_panel_desc)
    else -> ""
}
