/*
 * Copyright (C) 2021-2025 The DRS Smart Keyboard Project
 */

package com.drs.smartkeyboard.app.settings.typing

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.LibraryBooks
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.SpaceBar
import androidx.compose.material.icons.filled.Spellcheck
import androidx.compose.material3.Card
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp
import com.drs.smartkeyboard.R
import com.drs.smartkeyboard.app.LocalNavController
import com.drs.smartkeyboard.app.Routes
import com.drs.smartkeyboard.app.enumDisplayEntriesOf
import com.drs.smartkeyboard.ime.keyboard.IncognitoMode
import com.drs.smartkeyboard.ime.voice.VoiceRecognizerMode
import com.drs.smartkeyboard.ime.nlp.SpellingLanguageMode
import com.drs.smartkeyboard.lib.compose.DrsHyperlinkText
import com.drs.smartkeyboard.lib.compose.DrsScreen
import org.drs.jetpref.datastore.model.collectAsState
import org.drs.jetpref.datastore.ui.ExperimentalJetPrefDatastoreUi
import org.drs.jetpref.datastore.ui.ListPreference
import org.drs.jetpref.datastore.ui.Preference
import org.drs.jetpref.datastore.ui.PreferenceGroup
import org.drs.jetpref.datastore.ui.SwitchPreference
import org.drs.lib.android.AndroidVersion
import org.drs.lib.compose.stringRes

@OptIn(ExperimentalJetPrefDatastoreUi::class)
@Composable
fun TypingScreen() = DrsScreen {
    title = stringRes(R.string.settings__typing__title)
    previewFieldVisible = true

    val navController = LocalNavController.current

    content {
        // DRS smart engine status — replaces the stale upstream notice (suggestions
        // have been fully working since DRS V1.5.0, so the old "not available"
        // error card was factually wrong for this build).
        Card(modifier = Modifier.padding(8.dp)) {
            Text(
                modifier = Modifier.padding(8.dp),
                text = stringRes(R.string.settings__typing__drs_smart_card__text),
            )
        }

        PreferenceGroup(title = stringRes(R.string.pref__suggestion__title)) {
            SwitchPreference(
                prefs.suggestion.enabled,
                title = stringRes(R.string.pref__suggestion__enabled__label),
                summary = stringRes(R.string.pref__suggestion__enabled__summary),
            )
            // DRS v1.17.0: the REAL autocorrect — space may silently fix a
            // genuine typo when a very common word sits within edit distance 1.
            SwitchPreference(
                prefs.suggestion.autocorrectEnabled,
                title = stringRes(R.string.pref__suggestion__autocorrect_enabled__label),
                summary = stringRes(R.string.pref__suggestion__autocorrect_enabled__summary),
                enabledIf = { prefs.suggestion.enabled isEqualTo true },
            )
            SwitchPreference(
                prefs.suggestion.nextWordEnabled,
                title = stringRes(R.string.pref__suggestion__next_word_enabled__label),
                summary = stringRes(R.string.pref__suggestion__next_word_enabled__summary),
                enabledIf = { prefs.suggestion.enabled isEqualTo true },
            )
            SwitchPreference(
                prefs.suggestion.blockPossiblyOffensive,
                title = stringRes(R.string.pref__suggestion__block_possibly_offensive__label),
                summary = stringRes(R.string.pref__suggestion__block_possibly_offensive__summary),
                enabledIf = { prefs.suggestion.enabled isEqualTo true },
            )
            SwitchPreference(
                prefs.suggestion.api30InlineSuggestionsEnabled,
                title = stringRes(R.string.pref__suggestion__api30_inline_suggestions_enabled__label),
                summary = stringRes(R.string.pref__suggestion__api30_inline_suggestions_enabled__summary),
                visibleIf = { AndroidVersion.ATLEAST_API30_R },
            )
            ListPreference(
                prefs.suggestion.incognitoMode,
                icon = ImageVector.vectorResource(id = R.drawable.ic_incognito),
                title = stringRes(R.string.pref__suggestion__incognito_mode__label),
                entries = enumDisplayEntriesOf(IncognitoMode::class),
            )
        }

        // DRS v1.24.0: the voice dictation gate — «الميكروفون في قبضة
        // المستخدم». The switch flips the exact pref the mic key consults
        // on every press (decideVoiceInputRoute), so a chosen-off
        // microphone answers with an honest toast on the very next press.
        PreferenceGroup(title = stringRes(R.string.pref__voice__title)) {
            SwitchPreference(
                prefs.voice.enabled,
                title = stringRes(R.string.pref__voice__enabled__label),
                summary = stringRes(R.string.pref__voice__enabled__summary),
            )
            // DRS v1.25.0 — the second voice gate: the user picks where
            // their speech is recognized (auto / strictly on-device /
            // classic standard), and the mic key honors it on the very
            // next press. (jetpref's ListPreference derives its summary
            // from the selected entry, so no summary string is wired.)
            ListPreference(
                prefs.voice.recognizerMode,
                title = stringRes(R.string.pref__voice__recognizer_mode__label),
                entries = enumDisplayEntriesOf(VoiceRecognizerMode::class),
            )
        }

        PreferenceGroup(title = stringRes(R.string.pref__correction__title)) {
            SwitchPreference(
                prefs.correction.autoCapitalization,
                title = stringRes(R.string.pref__correction__auto_capitalization__label),
                summary = stringRes(R.string.pref__correction__auto_capitalization__summary),
            )
            val isAutoSpacePunctuationEnabled by prefs.correction.autoSpacePunctuation.collectAsState()
            SwitchPreference(
                prefs.correction.autoSpacePunctuation,
                icon = Icons.Default.SpaceBar,
                title = stringRes(R.string.pref__correction__auto_space_punctuation__label),
                summary = stringRes(R.string.pref__correction__auto_space_punctuation__summary),
            )
            if (isAutoSpacePunctuationEnabled) {
                Card(modifier = Modifier.padding(8.dp)) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        Text(
                            text = """
                                Auto-space after punctuation is an experimental feature which may break or behave
                                unexpectedly. If you want, please give feedback about it in below linked feedback
                                thread. This helps a lot in improving this feature. Thanks!
                            """.trimIndent().replace('\n', ' '),
                        )
                        DrsHyperlinkText(
                            text = "Feedback thread (GitHub)",
                            url = "https://github.com/CTO-DRS/DRS-SmartKeyboard/discussions",
                        )
                    }
                }
            }
            SwitchPreference(
                prefs.correction.rememberCapsLockState,
                title = stringRes(R.string.pref__correction__remember_caps_lock_state__label),
                summary = stringRes(R.string.pref__correction__remember_caps_lock_state__summary),
            )
            SwitchPreference(
                prefs.correction.doubleSpacePeriod,
                title = stringRes(R.string.pref__correction__double_space_period__label),
                summary = stringRes(R.string.pref__correction__double_space_period__summary),
            )
        }

        PreferenceGroup(title = stringRes(R.string.pref__spelling__title)) {
            val drsSpellCheckerEnabled = remember { mutableStateOf(false) }
            SpellCheckerServiceSelector(drsSpellCheckerEnabled)
            ListPreference(
                prefs.spelling.languageMode,
                icon = Icons.Default.Language,
                title = stringRes(R.string.pref__spelling__language_mode__label),
                entries = enumDisplayEntriesOf(SpellingLanguageMode::class),
                enabledIf = { drsSpellCheckerEnabled.value },
            )
            // DRS v1.19.0: the spell checker finally marks real typos (red
            // underline + tap-to-fix suggestions) behind conservative gates —
            // rich dictionaries (Arabic/English) only, plain words only, and a
            // plausible correction must exist. This switch turns it off.
            SwitchPreference(
                prefs.spelling.typoFlaggingEnabled,
                icon = Icons.Default.Spellcheck,
                title = stringRes(R.string.pref__spelling__typo_flagging_enabled__label),
                summary = stringRes(R.string.pref__spelling__typo_flagging_enabled__summary),
                enabledIf = { drsSpellCheckerEnabled.value },
            )
            // DRS v1.20.0: the visibleIf=false stubs for "use contacts" / "use user
            // dictionary entries" are gone together with their dead prefs — no contacts
            // integration exists in the IME, and hidden switches that promise behavior
            // nothing implements are removed, not shipped disabled.
        }

        PreferenceGroup(title = stringRes(R.string.settings__dictionary__title)) {
            Preference(
                icon = Icons.AutoMirrored.Filled.LibraryBooks,
                title = stringRes(R.string.settings__dictionary__title),
                summary = stringRes(R.string.settings__dictionary__home_summary),
                onClick = { navController.navigate(Routes.Settings.Dictionary) },
            )
        }
    }
}
