/*
 * Copyright (C) 2021-2025 The DRS Smart Keyboard Project
 */

package com.drs.smartkeyboard.app.devtools

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import com.drs.smartkeyboard.R
import com.drs.smartkeyboard.ime.core.DisplayLanguageNamesIn
import com.drs.smartkeyboard.lib.compose.DrsScreen
import org.drs.jetpref.datastore.model.collectAsState
import java.util.*
import org.drs.lib.android.showLongToastSync
import org.drs.lib.compose.DrsIconButton
import org.drs.lib.compose.stringRes
import org.drs.lib.kotlin.io.subDir
import org.drs.lib.kotlin.io.subFile

@Composable
fun AndroidLocalesScreen() = DrsScreen {
    title = stringRes(R.string.devtools__android_locales__title)
    scrollable = false

    val context = LocalContext.current
    val availableLocales = remember { Locale.getAvailableLocales().sortedBy { it.toLanguageTag() } }

    actions {
        DrsIconButton(
            onClick = {
                try {
                    val devtoolsDir = context.noBackupFilesDir.subDir("devtools")
                    devtoolsDir.mkdirs()
                    val txtFile = devtoolsDir.subFile("system_locales.tsv")
                    txtFile.bufferedWriter().use { out ->
                        for (locale in availableLocales) {
                            out.append(locale.toLanguageTag())
                            out.append('\t')
                            out.append(locale.getDisplayName(Locale.ENGLISH))
                            out.append('\t')
                            out.append(locale.getDisplayName(locale))
                            out.appendLine()
                        }
                    }
                    context.showLongToastSync("Exported available system locales to \"${txtFile.path}\"")
                } catch (e: Exception) {
                    context.showLongToastSync(
                        R.string.error__snackbar_message_template,
                        "error_message" to e.message.toString(),
                    )
                }
            },
            icon = Icons.Default.Save,
        )
    }

    content {
        val displayLanguageNamesIn by prefs.localization.displayLanguageNamesIn.collectAsState()

        SelectionContainer(modifier = Modifier.fillMaxWidth()) {
            LazyColumn {
                items(availableLocales) { locale ->
                    Row {
                        Text(
                            text = locale.toLanguageTag().padEnd(12),
                            fontFamily = FontFamily.Monospace,
                        )
                        Text(
                            modifier = Modifier.weight(1.0f),
                            text = when (displayLanguageNamesIn) {
                                DisplayLanguageNamesIn.SYSTEM_LOCALE -> locale.displayName
                                DisplayLanguageNamesIn.NATIVE_LOCALE -> locale.getDisplayName(locale)
                            },
                            fontFamily = FontFamily.Monospace,
                        )
                    }
                }
            }
        }
    }
}
