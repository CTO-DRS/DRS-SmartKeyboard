/*
 * Copyright (C) 2021-2025 The DRS Smart Keyboard Project
 */

package com.drs.smartkeyboard.app.settings.dictionary

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import com.drs.smartkeyboard.R
import com.drs.smartkeyboard.app.LocalNavController
import com.drs.smartkeyboard.app.settings.theme.DialogProperty
import com.drs.smartkeyboard.ime.dictionary.DictionaryManager
import com.drs.smartkeyboard.ime.dictionary.FREQUENCY_MAX
import com.drs.smartkeyboard.ime.dictionary.FREQUENCY_MIN
import com.drs.smartkeyboard.ime.dictionary.UserDictionaryDao
import com.drs.smartkeyboard.ime.dictionary.UserDictionaryEntry
import com.drs.smartkeyboard.ime.dictionary.UserDictionaryValidation
import com.drs.smartkeyboard.lib.DrsLocale
import com.drs.smartkeyboard.lib.compose.DrsScreen
import com.drs.smartkeyboard.lib.compose.Validation
import com.drs.smartkeyboard.lib.rememberValidationResult
import com.drs.smartkeyboard.lib.util.launchActivity
import org.drs.jetpref.material.ui.JetPrefAlertDialog
import org.drs.jetpref.material.ui.JetPrefListItem
import org.drs.jetpref.material.ui.JetPrefTextField
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.drs.lib.android.showLongToast
import org.drs.lib.android.showLongToastSync
import org.drs.lib.android.stringRes
import org.drs.lib.compose.DrsIconButton
import org.drs.lib.compose.rippleClickable
import org.drs.lib.compose.stringRes

private val AllLanguagesLocale = DrsLocale.from(language = "zz")
private val UserDictionaryEntryToAdd = UserDictionaryEntry(id = 0, "", 255, null, null)
private const val SystemUserDictionaryUiIntentAction = "android.settings.USER_DICTIONARY_SETTINGS"

enum class UserDictionaryType(val id: String) {
    DRS("drs"),
    SYSTEM("system");
}

@Composable
fun UserDictionaryScreen(type: UserDictionaryType) = DrsScreen {
    title = stringRes(when (type) {
        UserDictionaryType.DRS -> R.string.settings__udm__title_drs
        UserDictionaryType.SYSTEM -> R.string.settings__udm__title_system
    })
    previewFieldVisible = false
    scrollable = false

    val navController = LocalNavController.current
    val context = LocalContext.current
    val dictionaryManager = DictionaryManager.default()
    val scope = rememberCoroutineScope()

    var currentLocale by remember { mutableStateOf<DrsLocale?>(null) }
    var languageList by remember { mutableStateOf(emptyList<DrsLocale>()) }
    var wordList by remember { mutableStateOf(emptyList<UserDictionaryEntry>()) }
    var userDictionaryEntryForDialog by remember { mutableStateOf<UserDictionaryEntry?>(null) }
    // DRS v1.19.0: wipe-all confirmation — reset() is destructive, so it
    // asks before it deletes (the very first honest caller of the
    // previously-TODO reset()).
    var showClearDialog by remember { mutableStateOf(false) }

    fun userDictionaryDao(): UserDictionaryDao? {
        return when (type) {
            UserDictionaryType.DRS -> dictionaryManager.drsUserDictionaryDao()
            UserDictionaryType.SYSTEM -> dictionaryManager.systemUserDictionaryDao()
        }
    }

    fun getDisplayNameForLocale(locale: DrsLocale): String {
        return if (locale == AllLanguagesLocale) {
            context.stringRes(R.string.settings__udm__all_languages)
        } else {
            locale.displayName()
        }
    }

    fun buildUi() {
        if (currentLocale != null) {
            //subtitle = getDisplayNameForLocale(currentLocale)
            val locale = if (currentLocale == AllLanguagesLocale) null else currentLocale
            wordList = userDictionaryDao()?.queryAll(locale) ?: emptyList()
            if (wordList.isEmpty()) {
                currentLocale = null
            }
        }
        if (currentLocale == null) {
            //subtitle = null
            languageList = userDictionaryDao()
                ?.queryLanguageList()
                ?.sortedBy { it?.displayLanguage() }
                ?.map { it ?: AllLanguagesLocale }
                ?: emptyList()
        }
    }

    val importDictionary = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
        onResult = { uri ->
            // If uri is null it indicates that the selection activity was cancelled (mostly
            // by pressing the back button), so we don't display an error message here.
            if (uri == null) return@rememberLauncherForActivityResult
            val db = when (type) {
                UserDictionaryType.DRS -> dictionaryManager.drsUserDictionaryDatabase()
                UserDictionaryType.SYSTEM -> dictionaryManager.systemUserDictionaryDatabase()
            }
            if (db == null) {
                // DRS a11y/i18n/ux (r0-I) FIX 1 — literal toasts → localized strings.
                context.showLongToastSync(
                    context.stringRes(
                        R.string.db__error_null_handle_fmt,
                        "op" to context.stringRes(R.string.db__op_import),
                    )
                )
                return@rememberLauncherForActivityResult
            }
            runCatching {
                db.importCombinedList(context, uri)
            }.onSuccess {
                buildUi()
                context.showLongToastSync(R.string.settings__udm__dictionary_import_success)
            }.onFailure { error ->
                context.showLongToastSync(
                    context.stringRes(R.string.error__localized_fmt, "error" to (error.localizedMessage ?: ""))
                )
            }
        },
    )

    val exportDictionary = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument(),
        onResult = { uri ->
            // If uri is null it indicates that the selection activity was cancelled (mostly
            // by pressing the back button), so we don't display an error message here.
            if (uri == null) return@rememberLauncherForActivityResult
            val db = when (type) {
                UserDictionaryType.DRS -> dictionaryManager.drsUserDictionaryDatabase()
                UserDictionaryType.SYSTEM -> dictionaryManager.systemUserDictionaryDatabase()
            }
            if (db == null) {
                // DRS a11y/i18n/ux (r0-I) FIX 1 — literal toasts → localized strings.
                context.showLongToastSync(
                    context.stringRes(
                        R.string.db__error_null_handle_fmt,
                        "op" to context.stringRes(R.string.db__op_export),
                    )
                )
                return@rememberLauncherForActivityResult
            }
            runCatching {
                db.exportCombinedList(context, uri)
            }.onSuccess {
                context.showLongToastSync(R.string.settings__udm__dictionary_export_success)
            }.onFailure { error ->
                context.showLongToastSync(
                    context.stringRes(R.string.error__localized_fmt, "error" to (error.localizedMessage ?: ""))
                )
            }
        },
    )

    navigationIcon {
        // DRS a11y/i18n/ux (r0-I) FIX 1 — nav up/close button labeled.
        DrsIconButton(
            onClick = {
                if (currentLocale != null) {
                    currentLocale = null
                    buildUi()
                } else {
                    navController.popBackStack()
                }
            },
            icon = if (currentLocale != null) {
                Icons.Default.Close
            } else {
                Icons.AutoMirrored.Filled.ArrowBack
            },
            contentDescription = if (currentLocale != null) {
                stringRes(R.string.action__cancel)
            } else {
                stringRes(R.string.action__navigate_back)
            },
        )
    }

    actions {
        var expanded by remember { mutableStateOf(false) }
        // DRS a11y/i18n/ux (r0-I) FIX 1 — overflow menu button labeled.
        DrsIconButton(
            onClick = { expanded = !expanded },
            icon = Icons.Default.MoreVert,
            contentDescription = stringRes(R.string.action__more_options),
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            DropdownMenuItem(
                onClick = {
                    importDictionary.launch("*/*")
                    expanded = false
                },
                text = { Text(text = stringRes(R.string.action__import)) },
            )
            DropdownMenuItem(
                onClick = {
                    exportDictionary.launch("my-personal-dictionary.clb")
                    expanded = false
                },
                text = { Text(text = stringRes(R.string.action__export)) },
            )
            if (type == UserDictionaryType.SYSTEM) {
                DropdownMenuItem(
                    onClick = {
                        context.launchActivity { it.action = SystemUserDictionaryUiIntentAction }
                        expanded = false
                    },
                    text = { Text(text = stringRes(R.string.settings__udm__open_system_manager_ui)) },
                )
            }
            // DRS v1.19.0: clear the DRS personal dictionary (with a
            // confirmation dialog). Only for the app-private database — the
            // system provider holds words owned by other apps too.
            if (type == UserDictionaryType.DRS) {
                DropdownMenuItem(
                    onClick = {
                        showClearDialog = true
                        expanded = false
                    },
                    text = { Text(text = stringRes(R.string.settings__udm__clear_dictionary)) },
                )
            }
        }
    }

    floatingActionButton {
        ExtendedFloatingActionButton(
            onClick = { userDictionaryEntryForDialog = UserDictionaryEntryToAdd },
            icon = { Icon(imageVector = Icons.Default.Add, contentDescription = null) },
            text = { Text(text = stringRes(R.string.settings__udm__dialog__title_add)) },
        )
    }

    content {
        BackHandler(currentLocale != null) {
            currentLocale = null
            buildUi()
        }

        LaunchedEffect(Unit) {
            dictionaryManager.loadUserDictionariesIfNecessary()
            buildUi()
        }

        LazyColumn {
            if (languageList.isEmpty()) {
                item {
                    Text(
                        modifier = Modifier.padding(vertical = 8.dp, horizontal = 16.dp),
                        text = stringRes(R.string.settings__udm__no_words_in_dictionary),
                        fontStyle = FontStyle.Italic,
                    )
                }
            }
            if (currentLocale == null) {
                items(languageList) { language ->
                    JetPrefListItem(
                        modifier = Modifier.rippleClickable {
                            scope.launch {
                                // Delay makes UI ripple visible and experience better
                                delay(150)
                                currentLocale = language
                                buildUi()
                            }
                        },
                        text = getDisplayNameForLocale(language),
                    )
                }
            } else {
                items(wordList) { wordEntry ->
                    JetPrefListItem(
                        modifier = Modifier.rippleClickable {
                            userDictionaryEntryForDialog = wordEntry
                        },
                        text = wordEntry.word,
                        secondaryText = stringRes(
                            if (wordEntry.shortcut != null) {
                                R.string.settings__udm__word_summary_freq_shortcut
                            } else {
                                R.string.settings__udm__word_summary_freq
                            },
                            "freq" to wordEntry.freq,
                            "shortcut" to wordEntry.shortcut,
                        ),
                    )
                }
            }
        }

        val wordEntry = userDictionaryEntryForDialog
        if (wordEntry != null) {
            var showValidationErrors by rememberSaveable { mutableStateOf(false) }
            val isAddWord = wordEntry === UserDictionaryEntryToAdd
            var word by rememberSaveable { mutableStateOf(wordEntry.word) }
            val wordValidation = rememberValidationResult(UserDictionaryValidation.Word, word)
            var freq by rememberSaveable { mutableStateOf(wordEntry.freq.toString()) }
            val freqValidation = rememberValidationResult(UserDictionaryValidation.Freq, freq)
            var shortcut by rememberSaveable { mutableStateOf(wordEntry.shortcut ?: "") }
            val shortcutValidation = rememberValidationResult(UserDictionaryValidation.Shortcut, shortcut)
            var locale by rememberSaveable { mutableStateOf(wordEntry.locale ?: "") }
            val localeValidation = rememberValidationResult(UserDictionaryValidation.Locale, locale)

            JetPrefAlertDialog(
                title = stringRes(if (isAddWord) {
                    R.string.settings__udm__dialog__title_add
                } else {
                    R.string.settings__udm__dialog__title_edit
                }),
                confirmLabel = stringRes(if (isAddWord) {
                    R.string.action__add
                } else {
                    R.string.action__apply
                }),
                onConfirm = {
                    val isInvalid = wordValidation.isInvalid() ||
                        freqValidation.isInvalid() ||
                        shortcutValidation.isInvalid() ||
                        localeValidation.isInvalid()
                    if (isInvalid) {
                        showValidationErrors = true
                    } else {
                        val entry = UserDictionaryEntry(
                            id = wordEntry.id,
                            word = word.trim(),
                            freq = freq.toInt(10),
                            shortcut = shortcut.trim().takeIf { it.isNotBlank() },
                            locale = locale.trim().takeIf { it.isNotBlank() }?.let {
                                // Normalize tag
                                DrsLocale.fromTag(it).localeTag()
                            },
                        )
                        if (isAddWord) {
                            userDictionaryDao()?.insert(entry)
                        } else {
                            userDictionaryDao()?.update(entry)
                        }
                        userDictionaryEntryForDialog = null
                        buildUi()
                    }
                },
                dismissLabel = stringRes(R.string.action__cancel),
                onDismiss = {
                    userDictionaryEntryForDialog = null
                },
                neutralLabel = if (isAddWord) {
                    null
                } else {
                    stringRes(R.string.action__delete)
                },
                onNeutral = {
                    userDictionaryDao()?.delete(wordEntry)
                    userDictionaryEntryForDialog = null
                    buildUi()
                },
            ) {
                Column {
                    DialogProperty(text = stringRes(R.string.settings__udm__dialog__word_label)) {
                        JetPrefTextField(
                            value = word,
                            onValueChange = { word = it },
                        )
                        Validation(showValidationErrors, wordValidation)
                    }
                    DialogProperty(text = stringRes(
                        R.string.settings__udm__dialog__freq_label,
                        "f_min" to FREQUENCY_MIN, "f_max" to FREQUENCY_MAX,
                    )) {
                        JetPrefTextField(
                            value = freq,
                            onValueChange = { freq = it },
                        )
                        Validation(showValidationErrors, freqValidation)
                    }
                    DialogProperty(text = stringRes(R.string.settings__udm__dialog__shortcut_label)) {
                        JetPrefTextField(
                            value = shortcut,
                            onValueChange = { shortcut = it },
                        )
                        Validation(showValidationErrors, shortcutValidation)
                    }
                    DialogProperty(text = stringRes(R.string.settings__udm__dialog__locale_label)) {
                        JetPrefTextField(
                            value = locale,
                            onValueChange = { locale = it },
                        )
                        Validation(showValidationErrors, localeValidation)
                    }
                }
            }
        }

        // DRS v1.19.0: destructive wipe confirmation — reset() clears every
        // personal/learned word from the app-private Room database. The
        // spell checker and the suggestion engine pick up the empty state
        // within the user-words cache TTL (15s), so the effect is honest.
        if (showClearDialog) {
            JetPrefAlertDialog(
                title = stringRes(R.string.settings__udm__clear_dictionary),
                confirmLabel = stringRes(R.string.action__delete),
                dismissLabel = stringRes(R.string.action__cancel),
                onConfirm = {
                    showClearDialog = false
                    val db = dictionaryManager.drsUserDictionaryDatabase()
                    if (db == null) {
                        // DRS a11y/i18n/ux (r0-I) FIX 1 — literal toast → localized string.
                        context.showLongToastSync(
                            context.stringRes(
                                R.string.db__error_null_handle_fmt,
                                "op" to context.stringRes(R.string.db__op_clear),
                            )
                        )
                    } else {
                        scope.launch {
                            runCatching { db.reset() }
                                .onSuccess {
                                    buildUi()
                                    context.showLongToast(
                                        R.string.settings__udm__clear_dictionary_success,
                                    )
                                }
                                .onFailure { error ->
                                    context.showLongToastSync(
                                        context.stringRes(R.string.error__localized_fmt, "error" to (error.localizedMessage ?: ""))
                                    )
                                }
                        }
                    }
                },
                onDismiss = { showClearDialog = false },
            ) {
                Text(text = stringRes(R.string.settings__udm__clear_dictionary_confirm))
            }
        }
    }
}
