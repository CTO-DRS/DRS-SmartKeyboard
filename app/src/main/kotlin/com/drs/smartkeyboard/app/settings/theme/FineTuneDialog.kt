/*
 * Copyright (C) 2022-2025 The DRS Smart Keyboard Project
 */

package com.drs.smartkeyboard.app.settings.theme

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import com.drs.smartkeyboard.R
import com.drs.smartkeyboard.app.DrsPreferenceStore
import com.drs.smartkeyboard.app.enumDisplayEntriesOf
import org.drs.jetpref.datastore.ui.ListPreference
import org.drs.jetpref.datastore.ui.PreferenceLayout
import org.drs.jetpref.material.ui.ColorRepresentation
import org.drs.jetpref.material.ui.JetPrefAlertDialog
import org.drs.lib.compose.stringRes

private val FineTuneContentPadding = PaddingValues(horizontal = 8.dp)

@Composable
fun FineTuneDialog(onDismiss: () -> Unit) {
    JetPrefAlertDialog(
        title = stringRes(R.string.settings__theme_editor__fine_tune__title),
        onDismiss = onDismiss,
        contentPadding = FineTuneContentPadding,
    ) {
        PreferenceLayout(DrsPreferenceStore, iconSpaceReserved = false) {
            ListPreference(
                listPref = prefs.theme.editorLevel,
                title = stringRes(R.string.settings__theme_editor__fine_tune__level),
                entries = enumDisplayEntriesOf(SnyggLevel::class),
            )
            ListPreference(
                listPref = prefs.theme.editorColorRepresentation,
                title = stringRes(R.string.settings__theme_editor__fine_tune__color_representation),
                entries = enumDisplayEntriesOf(ColorRepresentation::class),
            )
            ListPreference(
                listPref = prefs.theme.editorDisplayKbdAfterDialogs,
                title = stringRes(R.string.settings__theme_editor__fine_tune__display_kbd_after_dialogs),
                entries = enumDisplayEntriesOf(DisplayKbdAfterDialogs::class),
            )
        }
    }
}
