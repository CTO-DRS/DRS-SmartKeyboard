/*
 * Copyright (C) 2021-2025 The DRS Smart Keyboard Project
 */

package com.drs.smartkeyboard.app.settings.advanced

import android.app.Activity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Adb
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.FormatColorFill
import androidx.compose.material.icons.filled.Healing
import androidx.compose.material.icons.filled.Help
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Redeem
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Preview
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.SettingsBackupRestore
import androidx.compose.material.icons.filled.SystemUpdateAlt
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.vectorResource
import com.drs.smartkeyboard.R
import com.drs.smartkeyboard.app.AppTheme
import com.drs.smartkeyboard.app.DrsPreferenceStore
import com.drs.smartkeyboard.app.LocalNavController
import com.drs.smartkeyboard.app.Routes
import com.drs.smartkeyboard.app.enumDisplayEntriesOf
import com.drs.smartkeyboard.drs.DrsStore
import com.drs.smartkeyboard.ime.core.DisplayLanguageNamesIn
import com.drs.smartkeyboard.lib.DrsLocale
import com.drs.smartkeyboard.lib.compose.DrsScreen
import kotlinx.coroutines.launch
import org.drs.jetpref.datastore.runtime.DataStoreReader
import org.drs.jetpref.datastore.runtime.ImportStrategy
import org.drs.jetpref.datastore.model.collectAsState
import org.drs.jetpref.datastore.ui.ColorPickerPreference
import org.drs.jetpref.datastore.ui.ListPreference
import org.drs.jetpref.datastore.ui.Preference
import org.drs.jetpref.datastore.ui.PreferenceGroup
import org.drs.jetpref.datastore.ui.SwitchPreference
import org.drs.jetpref.datastore.ui.isMaterialYou
import org.drs.jetpref.datastore.ui.listPrefEntries
import org.drs.lib.android.AndroidVersion
import org.drs.lib.color.ColorMappings
import org.drs.lib.compose.stringRes


@Composable
fun OtherScreen() = DrsScreen {
    title = stringRes(R.string.settings__other__title)
    previewFieldVisible = false

    val navController = LocalNavController.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showFactoryResetDialog by remember { mutableStateOf(false) }

    content {
        // DRS: one-tap access to the adaptive layer from the system group.
        PreferenceGroup(title = stringRes(R.string.drs__control_center__drs_title)) {
            Preference(
                icon = Icons.Default.Dashboard,
                title = stringRes(R.string.drs__control_center__title),
                summary = stringRes(R.string.drs__control_center__home_summary),
                onClick = { navController.navigate(Routes.Settings.DrsControlCenter) },
            )
            Preference(
                icon = Icons.Default.Healing,
                title = stringRes(R.string.drs__diagnostics__title),
                summary = stringRes(R.string.drs__diagnostics__home_summary),
                onClick = { navController.navigate(Routes.Settings.DrsDiagnostics) },
            )
            Preference(
                icon = Icons.Default.Redeem,
                title = stringRes(R.string.drs__rewards__title),
                summary = stringRes(R.string.drs__rewards__summary),
                onClick = { navController.navigate(Routes.Settings.DrsRewards) },
            )
            Preference(
                icon = Icons.Outlined.Inventory2,
                title = stringRes(R.string.packages__center__title),
                summary = stringRes(R.string.packages__center__summary),
                onClick = { navController.navigate(Routes.Ext.Packages) },
            )
            Preference(
                icon = Icons.Default.SystemUpdateAlt,
                title = stringRes(R.string.updates__center__title),
                summary = stringRes(R.string.updates__center__summary),
                onClick = { navController.navigate(Routes.Ext.CheckUpdates) },
            )
        }
        ListPreference(
            prefs.other.settingsTheme,
            icon = Icons.Default.Palette,
            title = stringRes(R.string.pref__other__settings_theme__label),
            entries = enumDisplayEntriesOf(AppTheme::class),
        )
        ColorPickerPreference(
            pref = prefs.other.accentColor,
            title = stringRes(R.string.pref__other__settings_accent_color__label),
            defaultValueLabel = stringRes(R.string.action__default),
            icon = Icons.Default.FormatColorFill,
            defaultColors = ColorMappings.colors,
            showAlphaSlider = false,
            enableAdvancedLayout = true,
            colorOverride = {
                if (it.isMaterialYou(context)) {
                    Color.Unspecified
                } else {
                    it
                }
            }
        )
        ListPreference(
            prefs.other.settingsLanguage,
            icon = Icons.Default.Language,
            title = stringRes(R.string.pref__other__settings_language__label),
            entries = listPrefEntries {
                listOf(
                    "auto",
                    "ar",
                    "bg",
                    "bs",
                    "ca",
                    "ckb",
                    "cs",
                    "da",
                    "de",
                    "el",
                    "en",
                    "eo",
                    "es",
                    "fa",
                    "fi",
                    "fr",
                    "hr",
                    "hu",
                    "in",
                    "it",
                    "iw",
                    "ja",
                    "ko-KR",
                    "ku",
                    "lv-LV",
                    "mk",
                    "nds-DE",
                    "nl",
                    "no",
                    "pl",
                    "pt",
                    "pt-BR",
                    "ru",
                    "sk",
                    "sl",
                    "sr",
                    "sv",
                    "tr",
                    "uk",
                    "zgh",
                    "zh-CN",
                ).map { languageTag ->
                    if (languageTag == "auto") {
                        entry(
                            key = "auto",
                            label = stringRes(R.string.settings__system_default),
                        )
                    } else {
                        val displayLanguageNamesIn by prefs.localization.displayLanguageNamesIn.collectAsState()
                        val locale = DrsLocale.fromTag(languageTag)
                        entry(locale.languageTag(), when (displayLanguageNamesIn) {
                            DisplayLanguageNamesIn.SYSTEM_LOCALE -> locale.displayName()
                            DisplayLanguageNamesIn.NATIVE_LOCALE -> locale.displayName(locale)
                        })
                    }
                }
            }
        )
        SwitchPreference(
            prefs.search.historyEnabled,
            icon = Icons.Default.History,
            title = stringRes(R.string.pref__search__history_enabled__label),
            summary = stringRes(R.string.pref__search__history_enabled__summary),
        )
        SwitchPreference(
            prefs.other.showAppIcon,
            icon = Icons.Default.Preview,
            title = stringRes(R.string.pref__other__show_app_icon__label),
            summary = when {
                AndroidVersion.ATLEAST_API29_Q -> stringRes(R.string.pref__other__show_app_icon__summary_atleast_q)
                else -> null
            },
            enabledIf = { AndroidVersion.ATMOST_API28_P },
        )
        Preference(
            icon = ImageVector.vectorResource(R.drawable.ic_keyboard_keys),
            title = stringRes(R.string.physical_keyboard__title),
            onClick = { navController.navigate(Routes.Settings.PhysicalKeyboard) },
        )
        Preference(
            icon = Icons.Default.Adb,
            title = stringRes(R.string.devtools__title),
            onClick = { navController.navigate(Routes.Devtools.Home) },
        )

        PreferenceGroup(title = stringRes(R.string.backup_and_restore__title)) {
            Preference(
                onClick = { navController.navigate(Routes.Settings.Backup) },
                icon = Icons.Default.Archive,
                title = stringRes(R.string.backup_and_restore__back_up__title),
                summary = stringRes(R.string.backup_and_restore__back_up__summary),
            )
            Preference(
                onClick = { navController.navigate(Routes.Settings.Restore) },
                icon = Icons.Default.SettingsBackupRestore,
                title = stringRes(R.string.backup_and_restore__restore__title),
                summary = stringRes(R.string.backup_and_restore__restore__summary),
            )
        }

        // DRS v1.0.5: maintenance — plain-language help for the normal user
        // and a guarded factory reset for all keyboard settings.
        PreferenceGroup(title = stringRes(R.string.help__maintenance_group)) {
            Preference(
                onClick = { navController.navigate(Routes.Settings.Help) },
                icon = Icons.Default.Help,
                title = stringRes(R.string.help__title),
                summary = stringRes(R.string.help__entry_summary),
            )
            Preference(
                onClick = { showFactoryResetDialog = true },
                icon = Icons.Default.RestartAlt,
                title = stringRes(R.string.factory_reset__title),
                summary = stringRes(R.string.factory_reset__summary),
            )
        }
    }

    if (showFactoryResetDialog) {
        AlertDialog(
            onDismissRequest = { showFactoryResetDialog = false },
            title = { Text(text = stringRes(R.string.factory_reset__confirm_title)) },
            text = { Text(text = stringRes(R.string.factory_reset__confirm_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showFactoryResetDialog = false
                        scope.launch {
                            // Real factory reset: erase every keyboard
                            // preference back to defaults, reset the DRS
                            // adaptive layer, then recreate the app.
                            runCatching {
                                DrsPreferenceStore.import(
                                    ImportStrategy.Erase,
                                    DataStoreReader { "" },
                                )
                            }
                            runCatching { DrsStore.resetAll() }
                            (context as? Activity)?.recreate()
                        }
                    },
                ) {
                    Text(
                        text = stringRes(R.string.factory_reset__confirm_yes),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showFactoryResetDialog = false }) {
                    Text(text = stringRes(R.string.action__cancel))
                }
            },
        )
    }
}
