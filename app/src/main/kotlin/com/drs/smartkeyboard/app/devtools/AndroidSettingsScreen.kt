/*
 * Copyright (C) 2021-2025 The DRS Smart Keyboard Project
 */

package com.drs.smartkeyboard.app.devtools

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.drs.smartkeyboard.R
import com.drs.smartkeyboard.lib.compose.DrsScreen
import org.drs.jetpref.datastore.ui.Preference
import org.drs.jetpref.material.ui.JetPrefAlertDialog
import org.drs.lib.android.AndroidSettings
import org.drs.lib.compose.stringRes

@Composable
fun AndroidSettingsScreen(name: String?) = DrsScreen {
    title = when (name) {
        AndroidSettings.Global.groupId -> stringRes(R.string.devtools__android_settings_global__title)
        AndroidSettings.Secure.groupId -> stringRes(R.string.devtools__android_settings_secure__title)
        AndroidSettings.System.groupId -> stringRes(R.string.devtools__android_settings_system__title)
        else -> "invalid"
    }
    scrollable = false

    val context = LocalContext.current

    val settingsGroup = when (name) {
        AndroidSettings.Global.groupId -> AndroidSettings.Global
        AndroidSettings.Secure.groupId -> AndroidSettings.Secure
        AndroidSettings.System.groupId -> AndroidSettings.System
        else -> AndroidSettings.Global
    }
    val nameValueTable = remember(name) { settingsGroup.getAllKeys().toList() }
    var dialogKey by remember { mutableStateOf<String?>(null) }

    content {
        LazyColumn {
            items(nameValueTable) { (fieldName, key) ->
                Preference(
                    title = fieldName,
                    summary = key,
                    onClick = { dialogKey = key },
                )
            }
        }

        if (dialogKey != null) {
            JetPrefAlertDialog(
                title = dialogKey!!,
                onDismiss = { dialogKey = null },
            ) {
                SelectionContainer {
                    Text(
                        text = remember {
                            (settingsGroup.getString(context, dialogKey!!) ?: "(null)").ifBlank { "(blank)" }
                        },
                    )
                }
            }
        }
    }
}
