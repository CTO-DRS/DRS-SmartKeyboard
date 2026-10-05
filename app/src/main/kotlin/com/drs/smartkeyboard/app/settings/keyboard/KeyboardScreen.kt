/*
 * Copyright (C) 2021-2025 The DRS Smart Keyboard Project
 */

package com.drs.smartkeyboard.app.settings.keyboard

import androidx.compose.runtime.Composable
import com.drs.smartkeyboard.R
import com.drs.smartkeyboard.app.LocalNavController
import com.drs.smartkeyboard.app.Routes
import com.drs.smartkeyboard.app.enumDisplayEntriesOf
import com.drs.smartkeyboard.ime.input.CapitalizationBehavior
import com.drs.smartkeyboard.ime.keyboard.SpaceBarMode
import com.drs.smartkeyboard.ime.keyboard.SplitMode
import com.drs.smartkeyboard.ime.landscapeinput.LandscapeInputUiMode
import com.drs.smartkeyboard.ime.smartbar.IncognitoDisplayMode
import com.drs.smartkeyboard.ime.text.key.KeyHintMode
import com.drs.smartkeyboard.ime.text.key.UtilityKeyAction
import com.drs.smartkeyboard.lib.compose.DrsScreen
import org.drs.jetpref.datastore.ui.DialogSliderPreference
import org.drs.jetpref.datastore.ui.ExperimentalJetPrefDatastoreUi
import org.drs.jetpref.datastore.ui.ListPreference
import org.drs.jetpref.datastore.ui.Preference
import org.drs.jetpref.datastore.ui.PreferenceGroup
import org.drs.jetpref.datastore.ui.SwitchPreference
import org.drs.lib.compose.stringRes

@OptIn(ExperimentalJetPrefDatastoreUi::class)
@Composable
fun KeyboardScreen() = DrsScreen {
    title = stringRes(R.string.settings__keyboard__title)
    previewFieldVisible = true

    val navController = LocalNavController.current

    content {
        PreferenceGroup(title = stringRes(R.string.pref__keyboard__group_rows_hints__label)) {
        SwitchPreference(
            prefs.keyboard.numberRow,
            title = stringRes(R.string.pref__keyboard__number_row__label),
            summary = stringRes(R.string.pref__keyboard__number_row__summary),
        )
        ListPreference(
            listPref = prefs.keyboard.hintedNumberRowMode,
            switchPref = prefs.keyboard.hintedNumberRowEnabled,
            title = stringRes(R.string.pref__keyboard__hinted_number_row_mode__label),
            summarySwitchDisabled = stringRes(R.string.state__disabled),
            entries = enumDisplayEntriesOf(KeyHintMode::class),
            enabledIf = { prefs.keyboard.numberRow.isFalse() }
        )
        ListPreference(
            listPref = prefs.keyboard.hintedSymbolsMode,
            switchPref = prefs.keyboard.hintedSymbolsEnabled,
            title = stringRes(R.string.pref__keyboard__hinted_symbols_mode__label),
            summarySwitchDisabled = stringRes(R.string.state__disabled),
            entries = enumDisplayEntriesOf(KeyHintMode::class),
        )
        }

        PreferenceGroup(title = stringRes(R.string.pref__keyboard__group_smart_keys__label)) {
        SwitchPreference(
            prefs.keyboard.utilityKeyEnabled,
            title = stringRes(R.string.pref__keyboard__utility_key_enabled__label),
            summary = stringRes(R.string.pref__keyboard__utility_key_enabled__summary),
        )
        ListPreference(
            prefs.keyboard.utilityKeyAction,
            title = stringRes(R.string.pref__keyboard__utility_key_action__label),
            entries = enumDisplayEntriesOf(UtilityKeyAction::class),
            visibleIf = { prefs.keyboard.utilityKeyEnabled isEqualTo true },
        )
        ListPreference(
            prefs.keyboard.spaceBarMode,
            title = stringRes(R.string.pref__keyboard__space_bar_mode__label),
            entries = enumDisplayEntriesOf(SpaceBarMode::class),
        )
        ListPreference(
            prefs.keyboard.capitalizationBehavior,
            title = stringRes(R.string.pref__keyboard__capitalization_behavior__label),
            entries = enumDisplayEntriesOf(CapitalizationBehavior::class),
        )
        }

        PreferenceGroup(title = stringRes(R.string.pref__keyboard__group_look__label)) {
        // DRS v1.28.0 audit fix (Medium, exposure): keyboard height was wired
        // end-to-end into ImeWindowSpec but only reachable through the unified
        // basics screen — classic settings users never saw it. The same
        // slider now lives in the classic Look group as well.
        DialogSliderPreference(
            prefs.keyboard.heightScalePercent,
            title = stringRes(R.string.pref__keyboard__height_scale__label),
            valueLabel = { stringRes(R.string.unit__percent__symbol, "v" to it) },
            min = com.drs.smartkeyboard.ime.window.ImeWindowSpec.HEIGHT_SCALE_MIN_PERCENT,
            max = com.drs.smartkeyboard.ime.window.ImeWindowSpec.HEIGHT_SCALE_MAX_PERCENT,
            stepIncrement = 5,
        )
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
        ListPreference(
            listPref = prefs.keyboard.incognitoDisplayMode,
            title = stringRes(R.string.pref__keyboard__incognito_indicator__label),
            entries = enumDisplayEntriesOf(IncognitoDisplayMode::class),
        )
        }

        PreferenceGroup(title = stringRes(R.string.pref__keyboard__group_layout__label)) {
            ListPreference(
                prefs.keyboard.landscapeInputUiMode,
                title = stringRes(R.string.pref__keyboard__landscape_input_ui_mode__label),
                entries = enumDisplayEntriesOf(LandscapeInputUiMode::class),
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
        }

        PreferenceGroup(title = stringRes(R.string.pref__keyboard__group_keypress__label)) {
            Preference(
                title = stringRes(R.string.settings__input_feedback__title),
                onClick = { navController.navigate(Routes.Settings.InputFeedback) },
            )
            SwitchPreference(
                prefs.keyboard.popupEnabled,
                title = stringRes(R.string.pref__keyboard__popup_enabled__label),
                summary = stringRes(R.string.pref__keyboard__popup_enabled__summary),
            )
            SwitchPreference(
                prefs.keyboard.mergeHintPopupsEnabled,
                title = stringRes(R.string.pref__keyboard__merge_hint_popups_enabled__label),
                summary = stringRes(R.string.pref__keyboard__merge_hint_popups_enabled__summary),
            )
            DialogSliderPreference(
                prefs.keyboard.longPressDelay,
                title = stringRes(R.string.pref__keyboard__long_press_delay__label),
                valueLabel = { stringRes(R.string.unit__milliseconds__symbol, "v" to it) },
                min = 100,
                max = 700,
                stepIncrement = 10,
            )
            // DRS v1.0.6: real repeat-rate control - wired into the input
            // event dispatcher, so holding a key actually repeats faster.
            DialogSliderPreference(
                prefs.keyboard.keyRepeatRatePercent,
                title = stringRes(R.string.pref__keyboard__key_repeat_rate__label),
                valueLabel = { stringRes(R.string.unit__percent__symbol, "v" to it) },
                min = 50,
                max = 300,
                stepIncrement = 10,
            )
            // DRS v2.4.0: the accelerating delete ladder — the repeat rate
            // above stays the base speed; this toggle lets the delay descend
            // a fixed three-gear ladder while delete is held. Delete-family
            // keys only, pure deterministic contract, no guessing.
            SwitchPreference(
                prefs.keyboard.acceleratedDelete,
                title = stringRes(R.string.pref__keyboard__accelerated_delete__label),
                summary = stringRes(R.string.pref__keyboard__accelerated_delete__summary),
            )
            SwitchPreference(
                prefs.keyboard.spaceBarSwitchesToCharacters,
                title = stringRes(R.string.pref__keyboard__space_bar_switches_to_characters__label),
                summary = stringRes(R.string.pref__keyboard__space_bar_switches_to_characters__summary),
            )
            // DRS v1.17.0: haraka-first backspace — the delete key peels
            // diacritics off letter by letter instead of one cluster tap.
            SwitchPreference(
                prefs.keyboard.backspaceStripsHarakat,
                title = stringRes(R.string.pref__keyboard__backspace_strips_harakat__label),
                summary = stringRes(R.string.pref__keyboard__backspace_strips_harakat__summary),
            )
            // DRS v1.18.0: split keyboard — the letters page renders in two
            // halves with a central gap so thumbs reach both edges on wide
            // screens (landscape phones, foldables, tablets).
            ListPreference(
                listPref = prefs.keyboard.splitMode,
                title = stringRes(R.string.pref__keyboard__split_mode__label),
                entries = enumDisplayEntriesOf(SplitMode::class),
            )
        }
    }
}
