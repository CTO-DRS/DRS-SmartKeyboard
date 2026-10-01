/*
 * Copyright (C) 2021-2025 The DRS Smart Keyboard Project
 */

package com.drs.smartkeyboard.app.settings.clipboard

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.IosShare
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import com.drs.smartkeyboard.R
import com.drs.smartkeyboard.app.enumDisplayEntriesOf
import com.drs.smartkeyboard.clipboardManager
import com.drs.smartkeyboard.ime.clipboard.CLIPBOARD_HISTORY_NUM_GRID_COLUMNS_AUTO
import com.drs.smartkeyboard.ime.clipboard.ClipEditorCharLimit
import com.drs.smartkeyboard.ime.clipboard.ClipEditorPopupSize
import com.drs.smartkeyboard.ime.clipboard.ClipEditorRoute
import com.drs.smartkeyboard.ime.clipboard.ClipEditorScrim
import com.drs.smartkeyboard.ime.clipboard.ClipFontOption
import com.drs.smartkeyboard.ime.clipboard.ClipFontSizeOption
import com.drs.smartkeyboard.ime.clipboard.ClipboardHistoryExport
import com.drs.smartkeyboard.ime.clipboard.ClipHistorySort
import com.drs.smartkeyboard.ime.clipboard.ClipboardSyncBehavior
import com.drs.smartkeyboard.lib.compose.DrsScreen
import org.drs.jetpref.datastore.ui.DialogSliderPreference
import org.drs.jetpref.datastore.ui.ExperimentalJetPrefDatastoreUi
import org.drs.jetpref.datastore.ui.ListPreference
import org.drs.jetpref.datastore.ui.Preference
import org.drs.jetpref.datastore.ui.PreferenceGroup
import org.drs.jetpref.datastore.ui.SwitchPreference
import org.drs.lib.android.AndroidVersion
import org.drs.lib.android.showShortToastSync
import org.drs.lib.compose.pluralsRes
import org.drs.lib.compose.stringRes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalJetPrefDatastoreUi::class)
@Composable
fun ClipboardScreen() = DrsScreen {
    title = stringRes(R.string.settings__clipboard__title)
    previewFieldVisible = true

    val context = LocalContext.current
    val clipboardManager by context.clipboardManager()
    // DRS v1.9.0: the integrated clipboard system exports the whole text
    // history as one portable JSON document through the system file picker
    // (text items only — media bytes cannot round-trip through JSON).
    val exportPayload = remember { mutableStateOf<String?>(null) }
    // DRS p7 (F10): the JSON serialization moved off the main thread — it
    // can walk up to 500k-char items — through this scope.
    val exportScope = rememberCoroutineScope()
    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        val payload = exportPayload.value
        if (uri != null && payload != null) {
            val written = runCatching {
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    out.write(payload.toByteArray(Charsets.UTF_8))
                } != null
            }.getOrDefault(false)
            context.showShortToastSync(
                if (written) R.string.clipboard__export_history__done
                else R.string.clipboard__export_history__failed,
            )
        }
        exportPayload.value = null
    }

    content {
        PreferenceGroup(title = stringRes(R.string.pref__clipboard__group_basics__label)) {
            SwitchPreference(
                prefs.clipboard.useInternalClipboard,
                title = stringRes(R.string.pref__clipboard__use_internal_clipboard__label),
                summary = stringRes(R.string.pref__clipboard__use_internal_clipboard__summary),
            )
            ListPreference(
                prefs.clipboard.syncToDrs,
                title = stringRes(R.string.pref__clipboard__sync_from_system_clipboard__label),
                entries = enumDisplayEntriesOf(ClipboardSyncBehavior::class),
                enabledIf = { prefs.clipboard.useInternalClipboard isEqualTo true },
            )
            ListPreference(
                prefs.clipboard.syncToSystem,
                title = stringRes(R.string.pref__clipboard__sync_to_system_clipboard__label),
                entries = enumDisplayEntriesOf(ClipboardSyncBehavior::class),
                enabledIf = { prefs.clipboard.useInternalClipboard isEqualTo true },
            )
        }

        PreferenceGroup(title = stringRes(R.string.pref__clipboard__group_clipboard_suggestion__label)) {
            SwitchPreference(
                prefs.clipboard.suggestionEnabled,
                title = stringRes(R.string.pref__clipboard__suggestion_enabled__label),
                summary = stringRes(R.string.pref__clipboard__suggestion_enabled__summary),
            )
            DialogSliderPreference(
                prefs.clipboard.suggestionTimeout,
                title = stringRes(R.string.pref__clipboard__suggestion_timeout__label),
                valueLabel = { stringRes(R.string.pref__clipboard__suggestion_timeout__summary, "v" to it) },
                min = 30,
                max = 300,
                stepIncrement = 5,
                enabledIf = { prefs.clipboard.suggestionEnabled isEqualTo true },
            )
        }

        PreferenceGroup(title = stringRes(R.string.pref__clipboard__group_clipboard_history__label)) {
            SwitchPreference(
                prefs.clipboard.historyEnabled,
                title = stringRes(R.string.pref__clipboard__enable_clipboard_history__label),
                summary = stringRes(R.string.pref__clipboard__enable_clipboard_history__summary),
            )
            DialogSliderPreference(
                primaryPref = prefs.clipboard.historyNumGridColumnsPortrait,
                secondaryPref = prefs.clipboard.historyNumGridColumnsLandscape,
                title = stringRes(R.string.pref__clipboard__num_history_grid_columns__label),
                primaryLabel = stringRes(R.string.screen_orientation__portrait),
                secondaryLabel = stringRes(R.string.screen_orientation__landscape),
                valueLabel = { numGridColumns ->
                    if (numGridColumns == CLIPBOARD_HISTORY_NUM_GRID_COLUMNS_AUTO) {
                        stringRes(R.string.general__auto)
                    } else {
                        numGridColumns.toString()
                    }
                },
                min = 0,
                max = 10,
                stepIncrement = 1,
                enabledIf = { prefs.clipboard.historyEnabled isEqualTo true },
            )
            SwitchPreference(
                prefs.clipboard.historyAutoCleanOldEnabled,
                title = stringRes(R.string.pref__clipboard__clean_up_old__label),
                enabledIf = { prefs.clipboard.historyEnabled isEqualTo true },
            )
            DialogSliderPreference(
                prefs.clipboard.historyAutoCleanOldAfter,
                title = stringRes(R.string.pref__clipboard__clean_up_after__label),
                valueLabel = { pluralsRes(R.plurals.unit__minutes__written, it, "v" to it) },
                min = 0,
                max = 120,
                stepIncrement = 5,
                enabledIf = { prefs.clipboard.historyEnabled isEqualTo true && prefs.clipboard.historyAutoCleanOldEnabled isEqualTo true },
            )
            SwitchPreference(
                prefs.clipboard.historyAutoCleanSensitiveEnabled,
                title = stringRes(R.string.pref__clipboard__auto_clean_sensitive__label),
                enabledIf = { prefs.clipboard.historyEnabled isEqualTo true },
                visibleIf = { AndroidVersion.ATLEAST_API33_T },
            )
            DialogSliderPreference(
                prefs.clipboard.historyAutoCleanSensitiveAfter,
                title = stringRes(R.string.pref__clipboard__auto_clean_sensitive_after__label),
                valueLabel = { pluralsRes(R.plurals.unit__seconds__written, it, "v" to it) },
                min = 0,
                max = 300,
                stepIncrement = 10,
                enabledIf = { prefs.clipboard.historyEnabled isEqualTo true && prefs.clipboard.historyAutoCleanSensitiveEnabled isEqualTo true },
                visibleIf = { AndroidVersion.ATLEAST_API33_T },
            )
            SwitchPreference(
                prefs.clipboard.historySizeLimitEnabled,
                title = stringRes(R.string.pref__clipboard__limit_history_size__label),
                enabledIf = { prefs.clipboard.historyEnabled isEqualTo true },
            )
            DialogSliderPreference(
                prefs.clipboard.historySizeLimit,
                title = stringRes(R.string.pref__clipboard__max_history_size__label),
                valueLabel = { pluralsRes(R.plurals.unit__items__written, it, "v" to it) },
                min = 5,
                max = 100,
                stepIncrement = 5,
                enabledIf = { prefs.clipboard.historyEnabled isEqualTo true && prefs.clipboard.historySizeLimitEnabled isEqualTo true },
            )
            // DRS v1.22.0 — «سقف التثبيتات»: pinned items were the one
            // unbounded clipboard store; the cap now gates new pins with
            // an honest toast, and pins above a lowered cap are never
            // auto-destroyed.
            DialogSliderPreference(
                prefs.clipboard.pinnedMaxSize,
                title = stringRes(R.string.pref__clipboard__pinned_max_size__label),
                valueLabel = { pluralsRes(R.plurals.unit__items__written, it, "v" to it) },
                min = 5,
                max = 200,
                stepIncrement = 5,
                enabledIf = { prefs.clipboard.historyEnabled isEqualTo true },
            )

            SwitchPreference(
                prefs.clipboard.historyHideOnPaste,
                title = stringRes(R.string.pref__clipboard__history_hide_on_paste__label),
                enabledIf = { prefs.clipboard.historyEnabled isEqualTo true }
            )
            SwitchPreference(
                prefs.clipboard.historyHideOnNextTextField,
                title = stringRes(R.string.pref__clipboard__history_hide_on_next_text_field__label),
                enabledIf = { prefs.clipboard.historyEnabled isEqualTo true }
            )

            SwitchPreference(
                prefs.clipboard.clearPrimaryClipAffectsHistoryIfUnpinned,
                title = stringRes(R.string.pref__clipboard__clear_primary_clip_affects_history_if_unpinned__label),
                summary = stringRes(R.string.pref__clipboard__clear_primary_clip_affects_history_if_unpinned__summary),
                enabledIf = { prefs.clipboard.historyEnabled isEqualTo true },
            )
        }

        PreferenceGroup(title = stringRes(R.string.pref__clipboard__group_popup_editor__label)) {
            // DRS v1.14.0 — the comprehensive settings list covers the
            // floating edit popup window itself: where the edit opens,
            // how big the window is, and how strongly the app behind it
            // is dimmed — everything v1.11.0 hardcoded becomes a choice.
            ListPreference(
                prefs.clipboard.editRoute,
                title = stringRes(R.string.pref__clipboard__edit_route__label),
                entries = enumDisplayEntriesOf(ClipEditorRoute::class),
            )
            ListPreference(
                prefs.clipboard.popupSize,
                title = stringRes(R.string.pref__clipboard__popup_size__label),
                entries = enumDisplayEntriesOf(ClipEditorPopupSize::class),
            )
            ListPreference(
                prefs.clipboard.popupScrim,
                title = stringRes(R.string.pref__clipboard__popup_scrim__label),
                entries = enumDisplayEntriesOf(ClipEditorScrim::class),
            )
            // DRS v1.21.0 — «نافذة الاشعارات المنبثقه الخاصه بالتعديل»: the
            // heads-up notification that opens the floating editor from
            // anywhere — not only from inside the panel.
            SwitchPreference(
                prefs.clipboard.editNotificationEnabled,
                title = stringRes(R.string.pref__clipboard__edit_notification__label),
                summary = stringRes(R.string.pref__clipboard__edit_notification__summary),
            )
        }

        PreferenceGroup(title = stringRes(R.string.pref__clipboard__group_smart_editor__label)) {
            // DRS v1.12.0 — the complete smart clipboard system: every
            // default the editors honor lives here, in the app, the way
            // the user asked for («اضف لها اعداداتها في التطبيق»).
            ListPreference(
                prefs.clipboard.editorCharLimit,
                title = stringRes(R.string.pref__clipboard__editor_char_limit__label),
                entries = enumDisplayEntriesOf(ClipEditorCharLimit::class),
            )
            SwitchPreference(
                prefs.clipboard.largeTextWarning,
                title = stringRes(R.string.pref__clipboard__large_text_warning__label),
                summary = stringRes(R.string.pref__clipboard__large_text_warning__summary),
            )
        }

        PreferenceGroup(title = stringRes(R.string.pref__clipboard__group_search_results__label)) {
            // DRS v1.12.0 + v1.13.0 + v1.14.0: the colored result cards,
            // their navigation, and the direct line jump — every part of
            // the search experience the user asked for, in one place.
            SwitchPreference(
                prefs.clipboard.searchResultCards,
                title = stringRes(R.string.pref__clipboard__search_result_cards__label),
                summary = stringRes(R.string.pref__clipboard__search_result_cards__summary),
            )
            SwitchPreference(
                prefs.clipboard.autoResultsPanel,
                title = stringRes(R.string.pref__clipboard__auto_results_panel__label),
                summary = stringRes(R.string.pref__clipboard__auto_results_panel__summary),
                enabledIf = { prefs.clipboard.searchResultCards isEqualTo true },
            )
            SwitchPreference(
                prefs.clipboard.matchCaseByDefault,
                title = stringRes(R.string.pref__clipboard__match_case_by_default__label),
                summary = stringRes(R.string.pref__clipboard__match_case_by_default__summary),
            )
            SwitchPreference(
                prefs.clipboard.jumpToLine,
                title = stringRes(R.string.pref__clipboard__jump_to_line__label),
                summary = stringRes(R.string.pref__clipboard__jump_to_line__summary),
                enabledIf = { prefs.clipboard.searchResultCards isEqualTo true },
            )
            SwitchPreference(
                prefs.clipboard.jumpCenter,
                title = stringRes(R.string.pref__clipboard__jump_center__label),
                summary = stringRes(R.string.pref__clipboard__jump_center__summary),
                enabledIf = {
                    prefs.clipboard.searchResultCards isEqualTo true &&
                        prefs.clipboard.jumpToLine isEqualTo true
                },
            )
            SwitchPreference(
                prefs.clipboard.followActiveCard,
                title = stringRes(R.string.pref__clipboard__follow_active_card__label),
                summary = stringRes(R.string.pref__clipboard__follow_active_card__summary),
                enabledIf = { prefs.clipboard.searchResultCards isEqualTo true },
            )
        }

        PreferenceGroup(title = stringRes(R.string.pref__clipboard__group_code_detection__label)) {
            // DRS v1.12.0 + v1.14.0: the code-line detection and its two
            // details — the language badge and the automatic monospace
            // switch — each with its own honest switch.
            SwitchPreference(
                prefs.clipboard.codeDetection,
                title = stringRes(R.string.pref__clipboard__code_detection__label),
                summary = stringRes(R.string.pref__clipboard__code_detection__summary),
            )
            SwitchPreference(
                prefs.clipboard.codeBadge,
                title = stringRes(R.string.pref__clipboard__code_badge__label),
                summary = stringRes(R.string.pref__clipboard__code_badge__summary),
                enabledIf = { prefs.clipboard.codeDetection isEqualTo true },
            )
            SwitchPreference(
                prefs.clipboard.codeAutoMonospace,
                title = stringRes(R.string.pref__clipboard__code_auto_monospace__label),
                summary = stringRes(R.string.pref__clipboard__code_auto_monospace__summary),
                enabledIf = { prefs.clipboard.codeDetection isEqualTo true },
            )
        }

        PreferenceGroup(title = stringRes(R.string.pref__clipboard__group_history_organization__label)) {
            // DRS v1.12.0 + v1.14.0: the panel organization — the sort
            // order, the calendar sections, and the smart category
            // badges — the full comprehensive organization as settings.
            ListPreference(
                prefs.clipboard.historySort,
                title = stringRes(R.string.pref__clipboard__history_sort__label),
                entries = enumDisplayEntriesOf(ClipHistorySort::class),
            )
            SwitchPreference(
                prefs.clipboard.calendarSections,
                title = stringRes(R.string.pref__clipboard__calendar_sections__label),
                summary = stringRes(R.string.pref__clipboard__calendar_sections__summary),
                enabledIf = { prefs.clipboard.historyEnabled isEqualTo true },
            )
            SwitchPreference(
                prefs.clipboard.categoryBadges,
                title = stringRes(R.string.pref__clipboard__category_badges__label),
                summary = stringRes(R.string.pref__clipboard__category_badges__summary),
                enabledIf = { prefs.clipboard.historyEnabled isEqualTo true },
            )
        }

        PreferenceGroup(title = stringRes(R.string.pref__clipboard__group_export__label)) {
            Preference(
                icon = Icons.Outlined.IosShare,
                title = stringRes(R.string.clipboard__export_history__label),
                summary = stringRes(R.string.clipboard__export_history__summary),
                onClick = {
                    // DRS p7 (F10): serialize on Dispatchers.Default, then
                    // hand the payload to the SAF launcher — the picker flow
                    // itself is unchanged.
                    exportScope.launch {
                        val payload = withContext(Dispatchers.Default) {
                            clipboardManager.exportHistoryJson()
                        }
                        exportPayload.value = payload
                        exportLauncher.launch(ClipboardHistoryExport.DEFAULT_FILE_NAME)
                    }
                },
            )
        }
    }
}
