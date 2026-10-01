/*
 * Copyright (C) 2021-2025 The DRS Smart Keyboard Project
 */

package com.drs.smartkeyboard.app.settings.theme

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Brightness2
import androidx.compose.material.icons.filled.BrightnessAuto
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.WbTwilight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.drs.smartkeyboard.R
import com.drs.smartkeyboard.app.LocalNavController
import com.drs.smartkeyboard.app.Routes
import com.drs.smartkeyboard.app.enumDisplayEntriesOf
import com.drs.smartkeyboard.app.ext.AddonManagementReferenceBox
import com.drs.smartkeyboard.app.ext.ExtensionListScreenType
import com.drs.smartkeyboard.ime.theme.ThemeManager
import com.drs.smartkeyboard.ime.theme.ThemeMode
import com.drs.smartkeyboard.lib.compose.DrsScreen
import com.drs.smartkeyboard.lib.ext.ExtensionComponentName
import com.drs.smartkeyboard.themeManager
import kotlinx.coroutines.launch
import org.drs.jetpref.datastore.model.collectAsState
import org.drs.jetpref.datastore.ui.ColorPickerPreference
import org.drs.jetpref.datastore.ui.ListPreference
import org.drs.jetpref.datastore.ui.LocalTimePickerPreference
import org.drs.jetpref.datastore.ui.Preference
import org.drs.jetpref.datastore.ui.PreferenceGroup
import org.drs.jetpref.datastore.ui.isMaterialYou
import org.drs.lib.android.showShortToastSync
import org.drs.lib.color.ColorMappings
import org.drs.lib.compose.stringRes

@Composable
fun ThemeScreen() = DrsScreen {
    title = stringRes(R.string.settings__theme__title)
    previewFieldVisible = true

    val context = LocalContext.current
    val navController = LocalNavController.current
    // DRS v1.5.0: cycleTheme was only reachable from the keyboard strip —
    // the theme screen never offered it. One tap switches the effective
    // day/night slot to the next installed theme and confirms with its
    // real label (null = fewer than two themes installed).
    val scope = rememberCoroutineScope()
    val themeManager by context.themeManager()

    @Composable
    fun ThemeManager.getThemeLabel(id: ExtensionComponentName): String {
        val configs by indexedThemeConfigs.collectAsState()
        configs.first[id]?.let { return it.label }
        return id.toString()
    }

    content {
        val dayThemeId by prefs.theme.dayThemeId.collectAsState()
        val nightThemeId by prefs.theme.nightThemeId.collectAsState()

        PreferenceGroup(title = stringRes(R.string.pref__theme__group_mode__label)) {
        ListPreference(
            prefs.theme.mode,
            icon = Icons.Default.BrightnessAuto,
            title = stringRes(R.string.pref__theme__mode__label),
            entries = enumDisplayEntriesOf(ThemeMode::class),
        )
        LocalTimePickerPreference(
            pref = prefs.theme.sunriseTime,
            title = stringRes(R.string.pref__theme__sunrise_time__label),
            icon = Icons.Default.WbTwilight,
            enabledIf = { prefs.theme.mode isEqualTo ThemeMode.FOLLOW_TIME },
        )
        LocalTimePickerPreference(
            pref = prefs.theme.sunsetTime,
            title = stringRes(R.string.pref__theme__sunset_time__label),
            icon = Icons.Default.Brightness2,
            enabledIf = { prefs.theme.mode isEqualTo ThemeMode.FOLLOW_TIME },
        )
        }

        PreferenceGroup(title = stringRes(R.string.pref__theme__group_themes__label)) {
        // DRS v1.5.0: surface the real cycleTheme engine action here.
        Preference(
            icon = Icons.Default.Palette,
            title = stringRes(R.string.pref__theme__cycle_now__label),
            summary = stringRes(R.string.pref__theme__cycle_now__summary),
            onClick = {
                scope.launch {
                    val next = themeManager.cycleTheme()
                    if (next != null) {
                        // Non-composable resolution: the toast runs inside
                        // a coroutine, so the @Composable getThemeLabel
                        // helper cannot be used here.
                        val label = themeManager.indexedThemeConfigs.value.first[next]?.label
                            ?: next.toString()
                        context.showShortToastSync(label)
                    } else {
                        context.showShortToastSync(R.string.pref__theme__cycle_now__single)
                    }
                }
            },
        )
        Preference(
            icon = Icons.Default.LightMode,
            title = stringRes(R.string.pref__theme__day),
            summary = themeManager.getThemeLabel(dayThemeId),
            enabledIf = { prefs.theme.mode isNotEqualTo ThemeMode.ALWAYS_NIGHT },
            onClick = {
                navController.navigate(Routes.Settings.ThemeManager(ThemeManagerScreenAction.SELECT_DAY))
            },
        )
        Preference(
            icon = Icons.Default.DarkMode,
            title = stringRes(R.string.pref__theme__night),
            summary = themeManager.getThemeLabel(nightThemeId),
            enabledIf = { prefs.theme.mode isNotEqualTo ThemeMode.ALWAYS_DAY },
            onClick = {
                navController.navigate(Routes.Settings.ThemeManager(ThemeManagerScreenAction.SELECT_NIGHT))
            },
        )
        AddonManagementReferenceBox(type = ExtensionListScreenType.EXT_THEME)
        }

        PreferenceGroup(title = stringRes(R.string.pref__theme__group_customization__label)) {
        ColorPickerPreference(
            pref = prefs.theme.accentColor,
            title = stringRes(R.string.pref__theme__theme_accent_color__label),
            defaultValueLabel = stringRes(R.string.action__default),
            icon = Icons.Default.ColorLens,
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
        }
    }
}
