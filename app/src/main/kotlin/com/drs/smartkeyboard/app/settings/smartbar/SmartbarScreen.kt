/*
 * Copyright (C) 2021-2025 The DRS Smart Keyboard Project
 */

package com.drs.smartkeyboard.app.settings.smartbar

import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import com.drs.smartkeyboard.R
import com.drs.smartkeyboard.app.enumDisplayEntriesOf
import com.drs.smartkeyboard.ime.smartbar.CandidatesDisplayMode
import com.drs.smartkeyboard.ime.smartbar.ExtendedActionsPlacement
import com.drs.smartkeyboard.ime.smartbar.SmartbarLayout
import com.drs.smartkeyboard.lib.compose.DrsScreen
import org.drs.jetpref.datastore.ui.ListPreference
import org.drs.jetpref.datastore.ui.Preference
import org.drs.jetpref.datastore.ui.PreferenceGroup
import org.drs.jetpref.datastore.ui.SwitchPreference
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Tune
import org.drs.lib.compose.stringRes

@Composable
fun SmartbarScreen() = DrsScreen {
    title = stringRes(R.string.settings__smartbar__title)
    previewFieldVisible = true

    content {
        // DRS v1.0.5: actionable pointer to the in-IME tool customization —
        // informational row (not clickable) because the drag-and-drop editor
        // lives inside the keyboard itself.
        Preference(
            icon = Icons.Default.Tune,
            title = stringRes(R.string.pref__smartbar__customize_hint__label),
            summary = stringRes(R.string.pref__smartbar__customize_hint__summary),
        )
        PreferenceGroup(title = stringRes(R.string.pref__smartbar__group_basics__label)) {
            SwitchPreference(
                prefs.smartbar.enabled,
                title = stringRes(R.string.pref__smartbar__enabled__label),
                summary = stringRes(R.string.pref__smartbar__enabled__summary),
            )
            ListPreference(
                listPref = prefs.smartbar.layout,
                title = stringRes(R.string.pref__smartbar__layout__label),
                entries = enumDisplayEntriesOf(SmartbarLayout::class),
                enabledIf = { prefs.smartbar.enabled isEqualTo true },
            )
        }

        PreferenceGroup(title = stringRes(R.string.pref__smartbar__group_layout_specific__label)) {
            ListPreference(
                prefs.suggestion.displayMode,
                title = stringRes(R.string.pref__suggestion__display_mode__label),
                entries = enumDisplayEntriesOf(CandidatesDisplayMode::class),
                enabledIf = { prefs.smartbar.enabled isEqualTo true },
                visibleIf = { prefs.smartbar.layout isNotEqualTo SmartbarLayout.ACTIONS_ONLY },
            )
            SwitchPreference(
                prefs.smartbar.flipToggles,
                title = stringRes(R.string.pref__smartbar__flip_toggles__label),
                summary = stringRes(R.string.pref__smartbar__flip_toggles__summary),
                enabledIf = { prefs.smartbar.enabled isEqualTo true },
                visibleIf = {
                    prefs.smartbar.layout isEqualTo SmartbarLayout.SUGGESTIONS_ACTIONS_SHARED ||
                        prefs.smartbar.layout isEqualTo SmartbarLayout.SUGGESTIONS_ACTIONS_EXTENDED
                },
            )
            // TODO: schedule to remove this preference in the future, but keep it for now so users
            //  know why the setting is not available anymore. Also force enable it for UI display.
            SideEffect {
                // prefs.smartbar.sharedActionsAutoExpandCollapse.set(true)
            }
            SwitchPreference(
                prefs.smartbar.sharedActionsAutoExpandCollapse,
                title = stringRes(R.string.pref__smartbar__shared_actions_auto_expand_collapse__label),
                summary = stringRes(R.string.pref__smartbar__shared_actions_auto_expand_collapse__summary),
                enabledIf = { false },
                visibleIf = { prefs.smartbar.layout isEqualTo SmartbarLayout.SUGGESTIONS_ACTIONS_SHARED },
            )
            ListPreference(
                listPref = prefs.smartbar.extendedActionsPlacement,
                title = stringRes(R.string.pref__smartbar__extended_actions_placement__label),
                entries = enumDisplayEntriesOf(ExtendedActionsPlacement::class),
                enabledIf = { prefs.smartbar.enabled isEqualTo true },
                visibleIf = { prefs.smartbar.layout isEqualTo SmartbarLayout.SUGGESTIONS_ACTIONS_EXTENDED },
            )
        }

        // DRS v1.15.0: the smart panels' behavior switches (لوحة الحركات /
        // لوحة الرموز الذكية / لوحة الحروف الموسعة). Real behavior toggles
        // the panels read live — not decoration.
        PreferenceGroup(title = stringRes(R.string.pref__panels__group__label)) {
            SwitchPreference(
                prefs.panels.harakatSmartReplace,
                title = stringRes(R.string.pref__panels__harakat_smart_replace__label),
                summary = stringRes(R.string.pref__panels__harakat_smart_replace__summary),
            )
            SwitchPreference(
                prefs.panels.symbolSmartSuggestions,
                title = stringRes(R.string.pref__panels__symbol_suggestions__label),
                summary = stringRes(R.string.pref__panels__symbol_suggestions__summary),
            )
            SwitchPreference(
                prefs.panels.panelRecents,
                title = stringRes(R.string.pref__panels__recents__label),
                summary = stringRes(R.string.pref__panels__recents__summary),
            )
            // DRS v1.16.0: the smart panel ordering (أعد ترتيب الوحات بنظام
            // مرتب وذكي) — the switcher reorders from local open counts.
            SwitchPreference(
                prefs.panels.panelSmartOrder,
                title = stringRes(R.string.pref__panels__smart_order__label),
                summary = stringRes(R.string.pref__panels__smart_order__summary),
            )
            // DRS v2.2.0: the tiles' contextual advisory row — the
            // deterministic «مقترحات لهذا النص» of the text tools panel.
            SwitchPreference(
                prefs.panels.textToolsSmartContext,
                title = stringRes(R.string.pref__panels__text_tools_smart_context__label),
                summary = stringRes(R.string.pref__panels__text_tools_smart_context__summary),
            )
        }
    }
}
