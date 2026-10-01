/*
 * Copyright (C) 2024-2025 The DRS Smart Keyboard Project
 */

package com.drs.smartkeyboard.app.settings.media

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.EmojiSymbols
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.drs.smartkeyboard.R
import com.drs.smartkeyboard.app.DrsPreferenceStore
import com.drs.smartkeyboard.app.enumDisplayEntriesOf
import com.drs.smartkeyboard.ime.media.emoji.EmojiHistory
import com.drs.smartkeyboard.ime.media.emoji.EmojiHistoryHelper
import com.drs.smartkeyboard.ime.media.emoji.EmojiSkinTone
import com.drs.smartkeyboard.ime.media.emoji.EmojiSuggestionType
import com.drs.smartkeyboard.lib.compose.DrsScreen
import org.drs.jetpref.datastore.ui.DialogSliderPreference
import org.drs.jetpref.datastore.ui.ExperimentalJetPrefDatastoreUi
import org.drs.jetpref.datastore.ui.ListPreference
import org.drs.jetpref.datastore.ui.Preference
import org.drs.jetpref.datastore.ui.PreferenceGroup
import org.drs.jetpref.datastore.ui.SwitchPreference
import org.drs.jetpref.material.ui.JetPrefAlertDialog
import kotlinx.coroutines.launch
import org.drs.lib.compose.pluralsRes
import org.drs.lib.compose.stringRes

@OptIn(ExperimentalJetPrefDatastoreUi::class)
@Composable
fun MediaScreen() = DrsScreen {
    title = stringRes(R.string.settings__media__title)
    previewFieldVisible = true
    iconSpaceReserved = true

    val prefs by DrsPreferenceStore

    var shouldDelete by remember { mutableStateOf<ShouldDelete?>(null) }
    val scope = rememberCoroutineScope()

    content {
        PreferenceGroup(title = stringRes(R.string.pref__media__group_basics__label)) {
            ListPreference(
                prefs.emoji.preferredSkinTone,
                title = stringRes(R.string.prefs__media__emoji_preferred_skin_tone),
                entries = enumDisplayEntriesOf(EmojiSkinTone::class),
            )
        }

        PreferenceGroup(title = stringRes(R.string.prefs__media__emoji_history__title)) {
            SwitchPreference(
                prefs.emoji.historyEnabled,
                icon = Icons.Outlined.Schedule,
                title = stringRes(R.string.prefs__media__emoji_history_enabled),
                summary = stringRes(R.string.prefs__media__emoji_history_enabled__summary),
            )
            ListPreference(
                prefs.emoji.historyPinnedUpdateStrategy,
                title = stringRes(R.string.prefs__media__emoji_history_pinned_update_strategy),
                entries = enumDisplayEntriesOf(EmojiHistory.UpdateStrategy::class),
                enabledIf = { prefs.emoji.historyEnabled.isTrue() },
            )
            ListPreference(
                prefs.emoji.historyRecentUpdateStrategy,
                title = stringRes(R.string.prefs__media__emoji_history_recent_update_strategy),
                entries = enumDisplayEntriesOf(EmojiHistory.UpdateStrategy::class),
                enabledIf = { prefs.emoji.historyEnabled.isTrue() },
            )
            DialogSliderPreference(
                primaryPref = prefs.emoji.historyPinnedMaxSize,
                secondaryPref = prefs.emoji.historyRecentMaxSize,
                title = stringRes(R.string.prefs__media__emoji_history_max_size),
                primaryLabel = stringRes(R.string.emoji__history__pinned),
                secondaryLabel = stringRes(R.string.emoji__history__recent),
                valueLabel = { maxSize ->
                    if (maxSize == EmojiHistory.MaxSizeUnlimited) {
                        stringRes(R.string.general__unlimited)
                    } else {
                        pluralsRes(R.plurals.unit__items__written, maxSize, "v" to maxSize)
                    }
                },
                min = 0,
                max = 120,
                stepIncrement = 1,
                enabledIf = { prefs.emoji.historyEnabled.isTrue() },
            )
            Preference(
                title = stringRes(R.string.prefs__media__emoji_history_pinned_reset),
                onClick = {
                    shouldDelete = ShouldDelete(true)
                },
                enabledIf = { prefs.emoji.historyEnabled.isTrue() },
            )
            Preference(
                title = stringRes(R.string.prefs__media__emoji_history_reset),
                onClick = {
                    shouldDelete = ShouldDelete(false)
                },
                enabledIf = { prefs.emoji.historyEnabled.isTrue() },
            )

        }

        PreferenceGroup(title = stringRes(R.string.prefs__media__emoji_suggestion__title)) {
            SwitchPreference(
                prefs.emoji.suggestionEnabled,
                icon = Icons.Outlined.EmojiSymbols,
                title = stringRes(R.string.prefs__media__emoji_suggestion_enabled),
                summary = stringRes(R.string.prefs__media__emoji_suggestion_enabled__summary),
            )
            ListPreference(
                prefs.emoji.suggestionType,
                title = stringRes(R.string.prefs__media__emoji_suggestion_type),
                entries = enumDisplayEntriesOf(EmojiSuggestionType::class),
                enabledIf = { prefs.emoji.suggestionEnabled.isTrue() },
            )
            SwitchPreference(
                prefs.emoji.suggestionUpdateHistory,
                title = stringRes(R.string.prefs__media__emoji_suggestion_update_history),
                summary = stringRes(R.string.prefs__media__emoji_suggestion_update_history__summary),
                enabledIf = {
                    prefs.emoji.suggestionEnabled.isTrue() && prefs.emoji.historyEnabled.isTrue()
                },
            )
            SwitchPreference(
                prefs.emoji.suggestionCandidateShowName,
                title = stringRes(R.string.prefs__media__emoji_suggestion_candidate_show_name),
                summary = stringRes(R.string.prefs__media__emoji_suggestion_candidate_show_name__summary),
                enabledIf = { prefs.emoji.suggestionEnabled.isTrue() },
            )
            DialogSliderPreference(
                prefs.emoji.suggestionQueryMinLength,
                title = stringRes(R.string.prefs__media__emoji_suggestion_query_min_length),
                valueLabel = { length ->
                    pluralsRes(R.plurals.unit__characters__written, length, "v" to length)
                },
                min = 1,
                max = 5,
                stepIncrement = 1,
                enabledIf = { prefs.emoji.suggestionEnabled.isTrue() },
            )
            DialogSliderPreference(
                prefs.emoji.suggestionCandidateMaxCount,
                title = stringRes(R.string.prefs__media__emoji_suggestion_candidate_max_count),
                valueLabel = { count ->
                    pluralsRes(R.plurals.unit__candidates__written, count, "v" to count)
                },
                min = 1,
                max = 10,
                stepIncrement = 1,
                enabledIf = { prefs.emoji.suggestionEnabled.isTrue() },
            )
        }
    }

    DeleteEmojiHistoryConfirmDialog(
        shouldDelete = shouldDelete,
        onDismiss = {
            shouldDelete = null
        },
        onConfirm = {
            shouldDelete?.let {
                scope.launch {
                    if (it.pinned) {
                        EmojiHistoryHelper.deletePinned(prefs = prefs)
                    } else {
                        EmojiHistoryHelper.deleteHistory(prefs = prefs)
                    }
                }
                shouldDelete = null
            }
        },
    )
}

@Composable
fun DeleteEmojiHistoryConfirmDialog(
    shouldDelete: ShouldDelete?,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    shouldDelete?.let {
        JetPrefAlertDialog(
            title = stringRes(R.string.action__reset_confirm_title),
            confirmLabel = stringRes(R.string.action__yes),
            dismissLabel = stringRes(R.string.action__no),
            onDismiss = onDismiss,
            onConfirm = onConfirm,
        ) {
            if (it.pinned) {
                Text(stringRes(R.string.action__reset_confirm_message, "name" to "pinned emojis"))
            } else {
                Text(stringRes(R.string.action__reset_confirm_message, "name" to "emoji history"))
            }

        }
    }
}

data class ShouldDelete(val pinned: Boolean)
