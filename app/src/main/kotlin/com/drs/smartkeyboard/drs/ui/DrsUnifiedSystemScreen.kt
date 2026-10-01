/*
 * Copyright (C) 2021-2025 The DRS Smart Keyboard Project
 * Copyright (C) 2025 DRS Smart Keyboard contributors
 */

@file:OptIn(ExperimentalJetPrefDatastoreUi::class, ExperimentalMaterial3Api::class)

package com.drs.smartkeyboard.drs.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.MaterialTheme
import com.drs.smartkeyboard.R
import com.drs.smartkeyboard.app.DrsPreferenceStore
import com.drs.smartkeyboard.app.Routes
import com.drs.smartkeyboard.app.LocalNavController
import com.drs.smartkeyboard.app.enumDisplayEntriesOf
import com.drs.smartkeyboard.drs.DrsProfileManager
import com.drs.smartkeyboard.drs.DrsSmartbarShape
import com.drs.smartkeyboard.drs.DrsStore
import com.drs.smartkeyboard.ime.smartbar.IncognitoDisplayMode
import com.drs.smartkeyboard.ime.landscapeinput.LandscapeInputUiMode
import com.drs.smartkeyboard.lib.compose.DrsScreen
import org.drs.lib.compose.stringRes
import org.drs.jetpref.datastore.ui.DialogSliderPreference
import org.drs.jetpref.datastore.ui.ExperimentalJetPrefDatastoreUi
import org.drs.jetpref.datastore.ui.ListPreference
import org.drs.jetpref.datastore.ui.Preference
import org.drs.jetpref.datastore.ui.PreferenceGroup
import org.drs.jetpref.datastore.ui.SwitchPreference

/**
 * DRS v1.0.7 — النظام والتطبيق الموحد (Unified System & App).
 *
 * The unified control center section: basic system settings first (the
 * ones every level needs), then the advanced engine controls (haptic
 * strength, sound level, swipe thresholds, incognito indicator, landscape
 * mode) that only appear when Advanced Controls is on. The technical
 * strip toggle writes through the active profile, exactly like the
 * control center does, so the change is real and persisted.
 */
@Composable
fun DrsUnifiedSystemScreen() = DrsScreen {
    title = stringRes(R.string.drs__unified__system_title)
    navigationIconVisible = true
    previewFieldVisible = true

    val navController = LocalNavController.current
    val prefs by DrsPreferenceStore
    val drsState by DrsStore.state.collectAsState()

    content {
        // ---------------- advanced controls gate ----------------
        AdvancedControlsGateCard()

        // ---------------- basic system settings ----------------
        PreferenceGroup(title = stringRes(R.string.drs__unified__system_basic_group)) {
            Preference(
                title = stringRes(R.string.settings__localization__title),
                summary = stringRes(R.string.drs__unified__system_language_summary),
                onClick = { navController.navigate(Routes.Settings.Localization) },
            )
            Preference(
                title = stringRes(R.string.settings__theme__title),
                summary = stringRes(R.string.drs__unified__system_theme_summary),
                onClick = { navController.navigate(Routes.Settings.Theme) },
            )
            SwitchPreference(
                prefs.inputFeedback.audioEnabled,
                title = stringRes(R.string.pref__input_feedback__audio_enabled__label),
                summary = stringRes(R.string.drs__unified__basics_sound_summary),
            )
            SwitchPreference(
                prefs.inputFeedback.hapticEnabled,
                title = stringRes(R.string.pref__input_feedback__haptic_enabled__label),
                summary = stringRes(R.string.drs__unified__basics_haptic_summary),
            )
            Preference(
                title = stringRes(R.string.drs__control_center__tech_strip),
                summary = stringRes(R.string.drs__unified__system_tech_strip_summary),
                onClick = {
                    // Real write-through: toggles the strip on the ACTIVE
                    // profile, the same path the control center uses.
                    DrsProfileManager.activeProfile(drsState)?.let { profile ->
                        DrsProfileManager.applyProfile(
                            profile.copy(techStripEnabled = !profile.techStripEnabled),
                        )
                    }
                },
            )
            SwitchPreference(
                prefs.clipboard.historyEnabled,
                title = stringRes(R.string.pref__clipboard__enable_clipboard_history__label),
                summary = stringRes(R.string.drs__unified__basics_clipboard_summary),
            )
            SwitchPreference(
                prefs.emoji.historyEnabled,
                title = stringRes(R.string.prefs__media__emoji_history_enabled),
                summary = stringRes(R.string.drs__unified__system_emoji_history_summary),
            )
            Preference(
                title = stringRes(R.string.drs__gestures__title),
                summary = stringRes(R.string.drs__unified__system_gestures_summary),
                onClick = { navController.navigate(Routes.Settings.DrsGestures) },
            )
            Preference(
                title = stringRes(R.string.drs__unified__dashboard_manage_tools),
                summary = stringRes(R.string.drs__unified__tools_hint),
                onClick = { navController.navigate(Routes.Settings.DrsUnifiedTools) },
            )
        }

        // ---------------- advanced engine controls (gated) ----------------
        if (drsState.advancedControlsEnabled) {
            PreferenceGroup(title = stringRes(R.string.drs__unified__system_advanced_group)) {
                Text(
                    text = stringRes(R.string.drs__unified__system_advanced_hint),
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp),
                )
                DialogSliderPreference(
                    prefs.inputFeedback.audioVolume,
                    title = stringRes(R.string.pref__input_feedback__audio_volume__label),
                    valueLabel = { stringRes(R.string.unit__percent__symbol, "v" to it) },
                    min = 1,
                    max = 100,
                    stepIncrement = 1,
                    enabledIf = { prefs.inputFeedback.audioEnabled isEqualTo true },
                )
                DialogSliderPreference(
                    prefs.inputFeedback.hapticVibrationDuration,
                    title = stringRes(R.string.pref__input_feedback__haptic_vibration_duration__label),
                    valueLabel = { stringRes(R.string.unit__milliseconds__symbol, "v" to it) },
                    min = 1,
                    max = 100,
                    stepIncrement = 1,
                    enabledIf = { prefs.inputFeedback.hapticEnabled isEqualTo true },
                )
                DialogSliderPreference(
                    prefs.inputFeedback.hapticVibrationStrength,
                    title = stringRes(R.string.pref__input_feedback__haptic_vibration_strength__label),
                    valueLabel = { stringRes(R.string.unit__percent__symbol, "v" to it) },
                    min = 1,
                    max = 100,
                    stepIncrement = 1,
                    enabledIf = { prefs.inputFeedback.hapticEnabled isEqualTo true },
                )
                DialogSliderPreference(
                    prefs.gestures.swipeDistanceThreshold,
                    title = stringRes(R.string.pref__gestures__swipe_distance_threshold__label),
                    valueLabel = { stringRes(R.string.unit__display_pixel__symbol, "v" to it) },
                    min = 12,
                    max = 72,
                    stepIncrement = 1,
                )
                DialogSliderPreference(
                    prefs.gestures.swipeVelocityThreshold,
                    title = stringRes(R.string.pref__gestures__swipe_velocity_threshold__label),
                    valueLabel = { stringRes(R.string.unit__display_pixel_per_seconds__symbol, "v" to it) },
                    min = 400,
                    max = 4000,
                    stepIncrement = 100,
                )
                // DRS M2.3 — the Design-2030 strip shape: the corner radius
                // slider writes the SAME preference the IME clip reads, so
                // moving it re-shapes the live strip (0 square → 28 pill).
                DialogSliderPreference(
                    prefs.smartbar.drsCornerRadius,
                    title = stringRes(R.string.drs__smartbar__corner_radius__label),
                    valueLabel = { stringRes(R.string.unit__dp__symbol, "v" to it) },
                    min = DrsSmartbarShape.MIN_RADIUS_DP,
                    max = DrsSmartbarShape.MAX_RADIUS_DP,
                    stepIncrement = 1,
                )
                ListPreference(
                    prefs.keyboard.incognitoDisplayMode,
                    title = stringRes(R.string.pref__keyboard__incognito_indicator__label),
                    entries = enumDisplayEntriesOf(IncognitoDisplayMode::class),
                )
                ListPreference(
                    prefs.keyboard.landscapeInputUiMode,
                    title = stringRes(R.string.pref__keyboard__landscape_input_ui_mode__label),
                    entries = enumDisplayEntriesOf(LandscapeInputUiMode::class),
                )
            }
        }

        // ---------------- performance & diagnostics ----------------
        PreferenceGroup(title = stringRes(R.string.drs__unified__system_maintenance_group)) {
            Preference(
                title = stringRes(R.string.drs__unified__stats_title),
                summary = stringRes(R.string.drs__unified__stats_summary),
                onClick = { navController.navigate(Routes.Settings.DrsUnifiedStats) },
            )
            Preference(
                title = stringRes(R.string.drs__performance__title),
                summary = stringRes(R.string.drs__unified__system_performance_summary),
                onClick = { navController.navigate(Routes.Settings.DrsPerformance) },
            )
            Preference(
                title = stringRes(R.string.drs__diagnostics__title),
                summary = stringRes(R.string.drs__unified__system_diagnostics_summary),
                onClick = { navController.navigate(Routes.Settings.DrsDiagnostics) },
            )
            Preference(
                title = stringRes(R.string.backup_and_restore__back_up__title),
                summary = stringRes(R.string.drs__unified__system_backup_summary),
                onClick = { navController.navigate(Routes.Settings.Backup) },
            )
        }
    }
}
