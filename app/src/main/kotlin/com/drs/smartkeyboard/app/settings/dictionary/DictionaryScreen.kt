/*
 * Copyright (C) 2021-2025 The DRS Smart Keyboard Project
 */

package com.drs.smartkeyboard.app.settings.dictionary

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.NoteAdd
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.drs.smartkeyboard.R
import com.drs.smartkeyboard.app.LocalNavController
import com.drs.smartkeyboard.app.Routes
import com.drs.smartkeyboard.externalDictStore
import com.drs.smartkeyboard.ime.nlp.DrsExternalDictFormats
import com.drs.smartkeyboard.ime.nlp.DrsExternalDictStore
import com.drs.smartkeyboard.lib.compose.DrsScreen
import com.drs.smartkeyboard.nlpManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.drs.jetpref.datastore.ui.Preference
import org.drs.jetpref.datastore.ui.PreferenceGroup
import org.drs.jetpref.datastore.ui.SwitchPreference
import org.drs.jetpref.material.ui.JetPrefAlertDialog
import org.drs.lib.android.read
import org.drs.lib.android.query
import org.drs.lib.android.showLongToastSync
import org.drs.lib.android.stringRes
import org.drs.lib.compose.stringRes
import java.util.Locale

private const val IMPORT_MAX_BYTES: Long = 20_000_000L

@Composable
fun DictionaryScreen() = DrsScreen {
    title = stringRes(R.string.settings__dictionary__title)
    previewFieldVisible = true

    val navController = LocalNavController.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val store = remember { context.externalDictStore().value }

    // DRS v2.8.0 «قاموسك من ملفك»: user-imported external dictionary indexes.
    // Picked from the device, parsed deterministically, stored app-privately,
    // and merged INTO the bundled dictionaries (never replacing them). The
    // same-process NlpManager cache is invalidated right here so the live
    // keyboard picks the words up without any restart.
    var dictsVersion by remember { mutableIntStateOf(0) }
    val importedDicts = remember(dictsVersion) { store.list() }

    var pendingImportText by remember { mutableStateOf<String?>(null) }
    var pendingImportSource by remember { mutableStateOf<String?>(null) }
    var selectedLang by remember { mutableStateOf(DrsExternalDictStore.SUPPORTED_LANGS.first()) }

    val showLangDialog = pendingImportText != null

    fun refreshAndInvalidate(lang: String) {
        dictsVersion++
        context.nlpManager().value.invalidateDictCaches(lang)
    }

    val importExternalDict = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
        onResult = { uri ->
            // Null uri = the picker was cancelled — no honest signal to fake.
            if (uri == null) return@rememberLauncherForActivityResult
            runCatching<Pair<String, String>> {
                val sourceName = context.contentResolver
                    .query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME))
                    ?.use { cursor ->
                        if (!cursor.moveToFirst()) null
                        else {
                            val idx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                            if (idx < 0) null else cursor.getString(idx)
                        }
                    } ?: "dictionary.txt"
                // read() is block-based and returns Unit (with an
                // EXACTLY_ONCE contract), so the text is captured by definite
                // assignment — the same pattern the lib's own readAllText uses.
                val text: String
                context.contentResolver.read(uri, maxSize = IMPORT_MAX_BYTES) { input ->
                    text = input.bufferedReader().use { it.readText() }
                }
                text to sourceName
            }.onSuccess { (text, sourceName) ->
                pendingImportText = text
                pendingImportSource = sourceName
            }.onFailure { error ->
                context.showLongToastSync(
                    context.stringRes(R.string.pref__external_dict__toast_read_failed_fmt,
                        "error" to (error.message ?: "").take(120))
                )
            }
        },
    )

    if (showLangDialog) {
        JetPrefAlertDialog(
            title = stringRes(R.string.pref__external_dict__choose_language__title),
            confirmLabel = stringRes(R.string.action__ok),
            dismissLabel = stringRes(R.string.action__cancel),
            onDismiss = {
                pendingImportText = null
                pendingImportSource = null
            },
            onConfirm = {
                val text = pendingImportText
                val source = pendingImportSource
                pendingImportText = null
                pendingImportSource = null
                if (text != null && source != null) {
                    val lang = selectedLang
                    scope.launch(Dispatchers.IO) {
                        store.import(lang, source, text)
                            .onSuccess { entry ->
                                refreshAndInvalidate(lang)
                                context.showLongToastSync(
                                    context.stringRes(R.string.pref__external_dict__toast_imported_fmt,
                                        "count" to entry.wordCount.toString(),
                                        "lang" to Locale(lang).displayName)
                                )
                            }
                            .onFailure { error ->
                                context.showLongToastSync(
                                    when (error.message) {
                                        DrsExternalDictFormats.REASON_MALFORMED_JSON ->
                                            context.stringRes(R.string.pref__external_dict__toast_malformed_json)
                                        DrsExternalDictFormats.REASON_NO_WORDS ->
                                            context.stringRes(R.string.pref__external_dict__toast_no_words)
                                        else ->
                                            context.stringRes(R.string.pref__external_dict__toast_failed)
                                    }
                                )
                            }
                    }
                }
            },
        ) {
            val langs = remember { DrsExternalDictStore.SUPPORTED_LANGS.sorted() }
            Column {
                langs.forEach { lang ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(selected = lang == selectedLang, onClick = { selectedLang = lang })
                            .padding(horizontal = 8.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = lang == selectedLang, onClick = { selectedLang = lang })
                        Text(
                            text = Locale(lang).displayName.replaceFirstChar { it.uppercase() },
                            modifier = Modifier.padding(start = 4.dp),
                        )
                    }
                }
            }
        }
    }

    content {
        SwitchPreference(
            prefs.dictionary.enableSystemUserDictionary,
            title = stringRes(R.string.pref__dictionary__enable_system_user_dictionary__label),
            summary = stringRes(R.string.pref__dictionary__enable_system_user_dictionary__summary),
        )
        Preference(
            title = stringRes(R.string.pref__dictionary__manage_system_user_dictionary__label),
            summary = stringRes(R.string.pref__dictionary__manage_system_user_dictionary__summary),
            onClick = { navController.navigate(Routes.Settings.UserDictionary(UserDictionaryType.SYSTEM)) },
            enabledIf = { prefs.dictionary.enableSystemUserDictionary isEqualTo true },
        )
        SwitchPreference(
            prefs.dictionary.enableDrsUserDictionary,
            title = stringRes(R.string.pref__dictionary__enable_internal_user_dictionary__label),
            summary = stringRes(R.string.pref__dictionary__enable_internal_user_dictionary__summary),
        )
        Preference(
            title = stringRes(R.string.pref__dictionary__manage_drs_user_dictionary__label),
            summary = stringRes(R.string.pref__dictionary__manage_drs_user_dictionary__summary),
            onClick = { navController.navigate(Routes.Settings.UserDictionary(UserDictionaryType.DRS)) },
            enabledIf = { prefs.dictionary.enableDrsUserDictionary isEqualTo true },
        )
        SwitchPreference(
            prefs.dictionary.learnFromSuggestions,
            title = stringRes(R.string.pref__dictionary__learn_from_suggestions__label),
            summary = stringRes(R.string.pref__dictionary__learn_from_suggestions__summary),
        )
        SwitchPreference(
            prefs.dictionary.learnTypedWords,
            title = stringRes(R.string.pref__dictionary__learn_typed_words__label),
            summary = stringRes(R.string.pref__dictionary__learn_typed_words__summary),
        )

        PreferenceGroup(title = stringRes(R.string.pref__external_dict__group__label)) {
            Preference(
                icon = Icons.Default.NoteAdd,
                title = stringRes(R.string.pref__external_dict__import__label),
                summary = stringRes(R.string.pref__external_dict__import__summary),
                onClick = { importExternalDict.launch("*/*") },
            )
            for (entry in importedDicts) {
                Preference(
                    icon = Icons.Default.Delete,
                    title = entry.sourceName,
                    summary = stringRes(R.string.pref__external_dict__entry__summary_fmt,
                        "lang" to Locale(entry.lang).displayName.replaceFirstChar { it.uppercase() },
                        "count" to entry.wordCount.toString()),
                    onClick = {
                        scope.launch(Dispatchers.IO) {
                            if (store.remove(entry.id)) {
                                refreshAndInvalidate(entry.lang)
                                context.showLongToastSync(
                                    context.stringRes(R.string.pref__external_dict__toast_removed)
                                )
                            } else {
                                context.showLongToastSync(
                                    context.stringRes(R.string.pref__external_dict__toast_failed)
                                )
                            }
                        }
                    },
                )
            }
            Preference(
                title = stringRes(R.string.pref__external_dict__note__label),
                summary = stringRes(R.string.pref__external_dict__note__summary),
            )
        }
    }
}
