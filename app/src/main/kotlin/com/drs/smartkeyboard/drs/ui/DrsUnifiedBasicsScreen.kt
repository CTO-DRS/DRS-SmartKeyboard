/*
 * Copyright (C) 2021-2025 The DRS Smart Keyboard Project
 * Copyright (C) 2025 DRS Smart Keyboard contributors
 */

@file:OptIn(ExperimentalJetPrefDatastoreUi::class, ExperimentalMaterial3Api::class)

package com.drs.smartkeyboard.drs.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.drs.smartkeyboard.R
import com.drs.smartkeyboard.app.DrsPreferenceStore
import com.drs.smartkeyboard.app.enumDisplayEntriesOf
import com.drs.smartkeyboard.drs.DrsStore
import com.drs.smartkeyboard.drs.DrsUnified
import com.drs.smartkeyboard.ime.input.CapitalizationBehavior
import com.drs.smartkeyboard.ime.keyboard.SpaceBarMode
import com.drs.smartkeyboard.ime.text.key.KeyHintMode
import com.drs.smartkeyboard.lib.compose.DrsScreen
import org.drs.lib.compose.stringRes
import org.drs.jetpref.datastore.ui.DialogSliderPreference
import org.drs.jetpref.datastore.ui.ExperimentalJetPrefDatastoreUi
import org.drs.jetpref.datastore.ui.ListPreference
import org.drs.jetpref.datastore.ui.PreferenceGroup
import org.drs.jetpref.datastore.ui.SwitchPreference

/**
 * DRS v1.0.7 — الأساسيات الموحدة (Unified Basics).
 *
 * One screen where both levels meet:
 *  - المجموعة الأساسية: the essential switches, always visible.
 *  - المجموعة المتقدمة: expert controls (spacing, delays, repeat rate,
 *    hints, glide) that appear only when Advanced Controls is on - so the
 *    normal user never has to deal with them, while the technical user
 *    keeps full control. Every row is a real preference the live keyboard
 *    obeys immediately.
 */
@Composable
fun DrsUnifiedBasicsScreen() = DrsScreen {
    title = stringRes(R.string.drs__unified__basics_title)
    navigationIconVisible = true
    previewFieldVisible = true

    val prefs by DrsPreferenceStore
    val drsState by DrsStore.state.collectAsState()

    content {
        // ---------------- advanced controls gate ----------------
        AdvancedControlsGateCard()

        // ---------------- basic group (always visible) ----------------
        PreferenceGroup(title = stringRes(R.string.drs__unified__basics_basic_group)) {
            SwitchPreference(
                prefs.keyboard.numberRow,
                icon = null,
                title = stringRes(R.string.pref__keyboard__number_row__label),
                summary = stringRes(R.string.drs__unified__basics_number_row_summary),
            )
            // DRS v1.0.8: keyboard height scale — a real sizing control the
            // live keyboard obeys through ImeWindowSpec.calcRowHeight.
            DialogSliderPreference(
                prefs.keyboard.heightScalePercent,
                title = stringRes(R.string.pref__keyboard__height_scale__label),
                summary = { _ -> stringRes(R.string.drs__unified__basics_height_summary) },
                valueLabel = { stringRes(R.string.unit__percent__symbol, "v" to it) },
                min = com.drs.smartkeyboard.ime.window.ImeWindowSpec.HEIGHT_SCALE_MIN_PERCENT,
                max = com.drs.smartkeyboard.ime.window.ImeWindowSpec.HEIGHT_SCALE_MAX_PERCENT,
                stepIncrement = 5,
            )
            // DRS v1.2.0: Smartbar height scale — the live Smartbar follows
            // it through the sizing layer (ProvideKeyboardRowBaseHeight).
            DialogSliderPreference(
                prefs.keyboard.smartbarHeightScalePercent,
                title = stringRes(R.string.pref__keyboard__smartbar_height_scale__label),
                summary = { _ -> stringRes(R.string.drs__unified__basics_smartbar_summary) },
                valueLabel = { stringRes(R.string.unit__percent__symbol, "v" to it) },
                min = com.drs.smartkeyboard.ime.window.ImeWindowSpec.SMARTBAR_HEIGHT_SCALE_MIN_PERCENT,
                max = com.drs.smartkeyboard.ime.window.ImeWindowSpec.SMARTBAR_HEIGHT_SCALE_MAX_PERCENT,
                stepIncrement = 5,
            )
            SwitchPreference(
                prefs.keyboard.popupEnabled,
                title = stringRes(R.string.pref__keyboard__popup_enabled__label),
                summary = stringRes(R.string.drs__unified__basics_popups_summary),
            )
            // DRS v1.6.0: key-preview scale — the live long-press popup
            // follows it through the popup bounds provider.
            DialogSliderPreference(
                prefs.keyboard.previewScalePercent,
                title = stringRes(R.string.pref__keyboard__preview_scale__label),
                summary = { _ -> stringRes(R.string.drs__unified__basics_preview_summary) },
                valueLabel = { stringRes(R.string.unit__percent__symbol, "v" to it) },
                min = com.drs.smartkeyboard.ime.window.ImeWindowSpec.KEY_PREVIEW_SCALE_MIN_PERCENT,
                max = com.drs.smartkeyboard.ime.window.ImeWindowSpec.KEY_PREVIEW_SCALE_MAX_PERCENT,
                stepIncrement = 10,
            )
            // DRS v1.7.0: glide trail width — the drawn ribbon (and its
            // fade-out) derive from a density-correct baseline times this
            // factor, read at draw time.
            DialogSliderPreference(
                prefs.glide.trailWidthPercent,
                title = stringRes(R.string.pref__glide__trail_width__label),
                summary = { _ -> stringRes(R.string.drs__unified__basics_trail_summary) },
                valueLabel = { stringRes(R.string.unit__percent__symbol, "v" to it) },
                min = com.drs.smartkeyboard.ime.window.ImeWindowSpec.GLIDE_TRAIL_SCALE_MIN_PERCENT,
                max = com.drs.smartkeyboard.ime.window.ImeWindowSpec.GLIDE_TRAIL_SCALE_MAX_PERCENT,
                stepIncrement = 10,
            )
            // DRS v1.7.0: emoji size — the palette grid cell and the glyph
            // font size both multiply by this factor.
            DialogSliderPreference(
                prefs.emoji.sizePercent,
                title = stringRes(R.string.pref__emoji__size__label),
                summary = { _ -> stringRes(R.string.drs__unified__basics_emoji_summary) },
                valueLabel = { stringRes(R.string.unit__percent__symbol, "v" to it) },
                min = com.drs.smartkeyboard.ime.window.ImeWindowSpec.EMOJI_SCALE_MIN_PERCENT,
                max = com.drs.smartkeyboard.ime.window.ImeWindowSpec.EMOJI_SCALE_MAX_PERCENT,
                stepIncrement = 10,
            )
            ListPreference(
                prefs.keyboard.capitalizationBehavior,
                title = stringRes(R.string.pref__keyboard__capitalization_behavior__label),
                entries = enumDisplayEntriesOf(CapitalizationBehavior::class),
            )
            ListPreference(
                prefs.keyboard.spaceBarMode,
                title = stringRes(R.string.pref__keyboard__space_bar_mode__label),
                entries = enumDisplayEntriesOf(SpaceBarMode::class),
            )
            SwitchPreference(
                prefs.suggestion.enabled,
                title = stringRes(R.string.pref__suggestion__enabled__label),
                summary = stringRes(R.string.drs__unified__basics_suggestions_summary),
            )
            SwitchPreference(
                prefs.clipboard.historyEnabled,
                title = stringRes(R.string.pref__clipboard__enable_clipboard_history__label),
                summary = stringRes(R.string.drs__unified__basics_clipboard_summary),
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
        }

        // ---------------- advanced group (gated) ----------------
        if (drsState.advancedControlsEnabled) {
            PreferenceGroup(title = stringRes(R.string.drs__unified__basics_advanced_group)) {
                DialogSliderPreference(
                    primaryPref = prefs.keyboard.fontSizeMultiplierPortrait,
                    secondaryPref = prefs.keyboard.fontSizeMultiplierLandscape,
                    title = stringRes(R.string.pref__keyboard__font_size_multiplier__label),
                    primaryLabel = stringRes(R.string.screen_orientation__portrait),
                    secondaryLabel = stringRes(R.string.screen_orientation__landscape),
                    valueLabel = { stringRes(R.string.unit__percent__symbol, "v" to it) },
                    min = 50,
                    max = 150,
                    stepIncrement = 5,
                )
                DialogSliderPreference(
                    primaryPref = prefs.keyboard.keySpacingVertical,
                    secondaryPref = prefs.keyboard.keySpacingHorizontal,
                    title = stringRes(R.string.pref__keyboard__key_spacing__label),
                    primaryLabel = stringRes(R.string.screen_orientation__vertical),
                    secondaryLabel = stringRes(R.string.screen_orientation__horizontal),
                    valueLabel = { stringRes(R.string.unit__percent__symbol, "v" to it) },
                    min = 50,
                    max = 150,
                    stepIncrement = 5,
                )
                DialogSliderPreference(
                    prefs.keyboard.longPressDelay,
                    title = stringRes(R.string.pref__keyboard__long_press_delay__label),
                    valueLabel = { stringRes(R.string.unit__milliseconds__symbol, "v" to it) },
                    min = 100,
                    max = 700,
                    stepIncrement = 10,
                )
                DialogSliderPreference(
                    prefs.keyboard.keyRepeatRatePercent,
                    title = stringRes(R.string.pref__keyboard__key_repeat_rate__label),
                    valueLabel = { stringRes(R.string.unit__percent__symbol, "v" to it) },
                    min = 50,
                    max = 300,
                    stepIncrement = 10,
                )
                ListPreference(
                    listPref = prefs.keyboard.hintedNumberRowMode,
                    switchPref = prefs.keyboard.hintedNumberRowEnabled,
                    title = stringRes(R.string.pref__keyboard__hinted_number_row_mode__label),
                    summarySwitchDisabled = stringRes(R.string.state__disabled),
                    entries = enumDisplayEntriesOf(KeyHintMode::class),
                    enabledIf = { prefs.keyboard.numberRow.isFalse() },
                )
                ListPreference(
                    listPref = prefs.keyboard.hintedSymbolsMode,
                    switchPref = prefs.keyboard.hintedSymbolsEnabled,
                    title = stringRes(R.string.pref__keyboard__hinted_symbols_mode__label),
                    summarySwitchDisabled = stringRes(R.string.state__disabled),
                    entries = enumDisplayEntriesOf(KeyHintMode::class),
                )
                SwitchPreference(
                    prefs.glide.enabled,
                    title = stringRes(R.string.pref__glide__enabled__label),
                    summary = stringRes(R.string.drs__unified__basics_glide_summary),
                )
                SwitchPreference(
                    prefs.glide.showTrail,
                    title = stringRes(R.string.pref__glide__show_trail__label),
                    summary = stringRes(R.string.drs__unified__basics_glide_trail_summary),
                )
            }
        }
    }
}

/**
 * The Advanced Controls gate: a real, persisted switch stored in DrsState.
 * When off, every advanced group of the unified system stays hidden.
 */
@Composable
fun AdvancedControlsGateCard() {
    val drsState by DrsStore.state.collectAsState()
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
                    text = stringRes(R.string.drs__unified__system_advanced_controls),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = stringRes(R.string.drs__unified__system_advanced_controls_summary),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = drsState.advancedControlsEnabled,
                onCheckedChange = { DrsUnified.setAdvancedControls(it) },
            )
        }
    }
}
