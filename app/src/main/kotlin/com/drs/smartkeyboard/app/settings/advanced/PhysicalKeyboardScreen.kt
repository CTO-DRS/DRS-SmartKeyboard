/*
 * Copyright (C) 2025 The DRS Smart Keyboard Project
 */

package com.drs.smartkeyboard.app.settings.advanced

import android.content.Intent
import android.content.res.Configuration
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.drs.smartkeyboard.R
import com.drs.smartkeyboard.lib.compose.DrsScreen
import org.drs.jetpref.datastore.ui.Preference
import org.drs.jetpref.datastore.ui.SwitchPreference
import org.drs.lib.compose.stringRes

@Composable
fun PhysicalKeyboardScreen() = DrsScreen {
    title = stringRes(R.string.physical_keyboard__title)

    val context = LocalContext.current
    val physicalKeyboardAttached by remember {
        mutableStateOf(context.resources.configuration.keyboard != Configuration.KEYBOARD_NOKEYS)
    }

    val activityForResult = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { }

    content {
        if (physicalKeyboardAttached) {
            Preference(
                title = stringRes(R.string.physical_keyboard__system_settings__title),
                summary = stringRes(R.string.physical_keyboard__system_settings__summary),
                onClick = {
                    try {
                        activityForResult.launch(Intent(Settings.ACTION_HARD_KEYBOARD_SETTINGS))
                    } catch (e: android.content.ActivityNotFoundException) {
                        // DRS fix: some devices (TV/Wear/custom ROMs) lack this panel.
                        android.widget.Toast.makeText(
                            context,
                            e.message ?: "Settings panel not available",
                            android.widget.Toast.LENGTH_SHORT,
                        ).show()
                    }
                }
            )
        } else {
            Preference(
                title = stringRes(R.string.physical_keyboard__system_settings__title),
                summary = stringRes(R.string.physical_keyboard__system_settings__summary_not_attached),
            )
        }
        SwitchPreference(
            pref = prefs.physicalKeyboard.showOnScreenKeyboard,
            title = stringRes(R.string.physical_keyboard__show_on_screen_keyboard__title),
            summary = stringRes(R.string.physical_keyboard__show_on_screen_keyboard__summary),
        )
    }
}
