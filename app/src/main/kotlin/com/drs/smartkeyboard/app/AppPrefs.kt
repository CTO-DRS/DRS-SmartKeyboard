/*
 * Copyright (C) 2021-2025 The DRS Smart Keyboard Project
 */

package com.drs.smartkeyboard.app

import com.drs.smartkeyboard.drs.DrsSmartbarShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import com.drs.smartkeyboard.app.settings.theme.ColorPreferenceSerializer
import com.drs.smartkeyboard.app.settings.theme.DisplayKbdAfterDialogs
import com.drs.smartkeyboard.app.settings.theme.SnyggLevel
import com.drs.smartkeyboard.app.setup.NotificationPermissionState
import com.drs.smartkeyboard.app.drsupdater.UpdateCheckMode
import com.drs.smartkeyboard.ime.clipboard.CLIPBOARD_HISTORY_NUM_GRID_COLUMNS_AUTO
import com.drs.smartkeyboard.ime.clipboard.ClipEditorCharLimit
import com.drs.smartkeyboard.ime.clipboard.ClipEditorPopupSize
import com.drs.smartkeyboard.ime.clipboard.ClipEditorRoute
import com.drs.smartkeyboard.ime.voice.VoiceRecognizerMode
import com.drs.smartkeyboard.ime.clipboard.ClipEditorScrim
import com.drs.smartkeyboard.ime.clipboard.ClipFontOption
import com.drs.smartkeyboard.ime.clipboard.ClipFontSizeOption
import com.drs.smartkeyboard.ime.clipboard.ClipHistorySort
import com.drs.smartkeyboard.ime.clipboard.ClipboardSyncBehavior
import com.drs.smartkeyboard.ime.core.DisplayLanguageNamesIn
import com.drs.smartkeyboard.ime.core.Subtype
import com.drs.smartkeyboard.ime.input.CapitalizationBehavior
import com.drs.smartkeyboard.ime.input.DrsSoundStyle
import com.drs.smartkeyboard.ime.input.HapticVibrationMode
import com.drs.smartkeyboard.ime.input.InputFeedbackActivationMode
import com.drs.smartkeyboard.ime.keyboard.IncognitoMode
import com.drs.smartkeyboard.ime.keyboard.SpaceBarMode
import com.drs.smartkeyboard.ime.keyboard.SplitMode
import com.drs.smartkeyboard.ime.landscapeinput.LandscapeInputUiMode
import com.drs.smartkeyboard.app.settings.DrsSearchHistory
// DRS v1.20.0: EmojiHairStyle import removed with the dead emoji__preferred_hair_style pref.
import com.drs.smartkeyboard.ime.media.emoji.EmojiHistory
import com.drs.smartkeyboard.ime.media.emoji.EmojiSkinTone
import com.drs.smartkeyboard.ime.media.emoji.EmojiSuggestionType
import com.drs.smartkeyboard.ime.nlp.SpellingLanguageMode
import com.drs.smartkeyboard.ime.smartbar.CandidatesDisplayMode
import com.drs.smartkeyboard.ime.smartbar.ExtendedActionsPlacement
import com.drs.smartkeyboard.ime.smartbar.IncognitoDisplayMode
import com.drs.smartkeyboard.ime.smartbar.SmartbarLayout
import com.drs.smartkeyboard.ime.smartbar.quickaction.QuickAction
import com.drs.smartkeyboard.ime.smartbar.quickaction.QuickActionArrangement
import com.drs.smartkeyboard.ime.smartbar.quickaction.QuickActionJsonConfig
import com.drs.smartkeyboard.ime.text.gestures.SwipeAction
import com.drs.smartkeyboard.ime.text.key.KeyCode
import com.drs.smartkeyboard.ime.text.key.KeyHintConfiguration
import com.drs.smartkeyboard.ime.text.key.KeyHintMode
import com.drs.smartkeyboard.ime.text.key.UtilityKeyAction
import com.drs.smartkeyboard.ime.text.keyboard.TextKeyData
import com.drs.smartkeyboard.ime.theme.ThemeMode
import com.drs.smartkeyboard.ime.theme.extCoreTheme
import com.drs.smartkeyboard.ime.window.ImeWindowConfig
import com.drs.smartkeyboard.ime.window.ImeWindowSpec
import com.drs.smartkeyboard.lib.ext.ExtensionComponentName
import com.drs.smartkeyboard.lib.util.VersionName
import org.drs.jetpref.datastore.annotations.Preferences
import org.drs.jetpref.datastore.jetprefDataStoreOf
import org.drs.jetpref.datastore.model.LocalTime
import org.drs.jetpref.datastore.model.PreferenceData
import org.drs.jetpref.datastore.model.PreferenceMigrationEntry
import org.drs.jetpref.datastore.model.PreferenceModel
import org.drs.jetpref.datastore.model.PreferenceType
import org.drs.jetpref.material.ui.ColorRepresentation
import kotlinx.serialization.json.Json
import org.drs.lib.android.isOrientationPortrait

val DrsPreferenceStore = jetprefDataStoreOf(DrsPreferenceModel::class)

@Preferences
abstract class DrsPreferenceModel : PreferenceModel() {
    companion object {
        const val NAME = "drs-smartkeyboard-prefs"
    }

    val clipboard = Clipboard()
    inner class Clipboard {
        val useInternalClipboard = boolean(
            key = "clipboard__use_internal_clipboard",
            default = false,
        )
        val syncToDrs = enum(
            key = "clipboard__sync_to_drs",
            default = ClipboardSyncBehavior.ALL_EVENTS,
        )
        val syncToSystem = enum(
            key = "clipboard__sync_to_system",
            default = ClipboardSyncBehavior.NO_EVENTS,
        )
        val suggestionEnabled = boolean(
            key = "clipboard__suggestion_enabled",
            default = true,
        )
        val suggestionTimeout = int(
            key = "clipboard__suggestion_timeout",
            default = 60,
        )
        val historyEnabled = boolean(
            key = "clipboard__history_enabled",
            // DRS v1.0.5: default ON — every user system package enables it
            // anyway, and a fresh install (or a user who skipped choosing a
            // system) now gets a working clipboard panel out of the box.
            // Users can still turn it off from the clipboard panel or settings.
            default = true,
        )
        val historyNumGridColumnsPortrait = int(
            key = "clipboard__history_num_grid_columns_portrait",
            default = CLIPBOARD_HISTORY_NUM_GRID_COLUMNS_AUTO,
        )
        val historyNumGridColumnsLandscape = int(
            key = "clipboard__history_num_grid_columns_landscape",
            default = CLIPBOARD_HISTORY_NUM_GRID_COLUMNS_AUTO,
        )
        @Composable
        fun historyNumGridColumns(): PreferenceData<Int> {
            val configuration = LocalConfiguration.current
            return if (configuration.isOrientationPortrait()) {
                historyNumGridColumnsPortrait
            } else {
                historyNumGridColumnsLandscape
            }
        }
        val historyAutoCleanOldEnabled = boolean(
            key = "clipboard__history_auto_clean_old_enabled",
            default = false,
        )
        val historyAutoCleanOldAfter = int(
            key = "clipboard__history_auto_clean_old_after",
            default = 20,
        )
        val historyAutoCleanSensitiveEnabled = boolean(
            key = "clipboard__history_auto_clean_sensitive_enabled",
            default = false,
        )
        val historyAutoCleanSensitiveAfter = int(
            key = "clipboard__history_auto_clean_sensitive_after",
            default = 20,
        )
        val historySizeLimitEnabled = boolean(
            key = "clipboard__history_size_limit_enabled",
            default = true,
        )
        val historySizeLimit = int(
            key = "clipboard__history_size_limit",
            default = 20,
        )
        // DRS v1.22.0: the honest ceiling of the PINNED clipboard items —
        // «سقف التثبيتات». Pinned items were the one unbounded store in
        // the clipboard (history limits explicitly exempt them), so the
        // cap refuses a new pin with an honest toast instead of silently
        // growing the Room table forever. Existing pins above a lowered
        // cap are never auto-destroyed.
        val pinnedMaxSize = int(
            key = "clipboard__pinned_max_size",
            default = 50,
        )
        val historyHideOnPaste = boolean(
            key = "clipboard__history_hide_on_paste",
            default = false,
        )
        val historyHideOnNextTextField = boolean(
            key = "clipboard__history_hide_on_next_text_field",
            default = true,
        )
        val clearPrimaryClipAffectsHistoryIfUnpinned = boolean(
            key = "clipboard__clear_primary_clip_affects_history_if_unpinned",
            default = true,
        )

        // DRS v1.12.0 — the complete smart clipboard system: the editor
        // character limit (up to the 500,000 policy cap), the colored
        // search-result cards with navigation, the code-line detection,
        // the editor defaults, and the panel sort order.
        val editorCharLimit = enum(
            key = "clipboard__editor_char_limit",
            default = ClipEditorCharLimit.FIVE_HUNDRED_K,
        )
        val searchResultCards = boolean(
            key = "clipboard__search_result_cards",
            default = true,
        )
        val autoResultsPanel = boolean(
            key = "clipboard__auto_results_panel",
            default = true,
        )
        val codeDetection = boolean(
            key = "clipboard__code_detection",
            default = true,
        )
        val editorFont = enum(
            key = "clipboard__editor_font",
            default = ClipFontOption.DEFAULT,
        )
        val editorFontSize = enum(
            key = "clipboard__editor_font_size",
            default = ClipFontSizeOption.NORMAL,
        )
        val matchCaseByDefault = boolean(
            key = "clipboard__match_case_by_default",
            default = false,
        )
        val historySort = enum(
            key = "clipboard__history_sort",
            default = ClipHistorySort.NEWEST,
        )

        // DRS v1.14.0 — the comprehensive settings list in the app covers
        // everything the recent rounds built: the floating edit popup
        // window (surface, size, dimming), the direct line jump of the
        // result cards, the code-line detection details, and the panel
        // organization (calendar sections and category badges).
        val editRoute = enum(
            key = "clipboard__edit_route",
            default = ClipEditorRoute.POPUP_WINDOW,
        )
        val popupSize = enum(
            key = "clipboard__popup_size",
            default = ClipEditorPopupSize.NORMAL,
        )
        val popupScrim = enum(
            key = "clipboard__popup_scrim",
            default = ClipEditorScrim.NORMAL,
        )
        // DRS v1.21.0 — «نافذة الاشعارات المنبثقه الخاصه بالتعديل»: a new
        // text capture may surface a heads-up notification whose «تعديل»
        // action opens the floating editor outside the panel. Sensitive
        // and non-text clips never notify (lock-screen privacy).
        // DRS (S-8) — privacy-first default: the edit heads-up notification
        // mirrors clipboard content into a possibly-on-lock-screen
        // notification; OFF by default, the user opts in explicitly.
        val editNotificationEnabled = boolean(
            key = "clipboard__edit_notification_enabled",
            default = false,
        )
        val jumpToLine = boolean(
            key = "clipboard__jump_to_line",
            default = true,
        )
        val jumpCenter = boolean(
            key = "clipboard__jump_center",
            default = true,
        )
        val followActiveCard = boolean(
            key = "clipboard__follow_active_card",
            default = true,
        )
        val largeTextWarning = boolean(
            key = "clipboard__large_text_warning",
            default = true,
        )
        val codeBadge = boolean(
            key = "clipboard__code_badge",
            default = true,
        )
        val codeAutoMonospace = boolean(
            key = "clipboard__code_auto_monospace",
            default = true,
        )
        val calendarSections = boolean(
            key = "clipboard__calendar_sections",
            default = true,
        )
        val categoryBadges = boolean(
            key = "clipboard__category_badges",
            default = true,
        )
    }

    val correction = Correction()
    inner class Correction {
        val autoCapitalization = boolean(
            key = "correction__auto_capitalization",
            default = true,
        )
        val autoSpacePunctuation = boolean(
            key = "correction__auto_space_punctuation",
            default = false,
        )
        val doubleSpacePeriod = boolean(
            key = "correction__double_space_period",
            default = true,
        )
        val rememberCapsLockState = boolean(
            key = "correction__remember_caps_lock_state",
            default = false,
        )
    }

    val devtools = Devtools()
    inner class Devtools {
        val enabled = boolean(
            key = "devtools__enabled",
            default = false,
        )
        val showPrimaryClip = boolean(
            key = "devtools__show_primary_clip",
            default = false,
        )
        val showInputStateOverlay = boolean(
            key = "devtools__show_input_state_overlay",
            default = false,
        )
        val showSpellingOverlay = boolean(
            key = "devtools__show_spelling_overlay",
            default = false,
        )
        val showInlineAutofillOverlay = boolean(
            key = "devtools__show_inline_autofill_overlay",
            default = false,
        )
        val showKeyTouchBoundaries = boolean(
            key = "devtools__show_touch_boundaries",
            default = false,
        )
        val showDragAndDropHelpers = boolean(
            key = "devtools__show_drag_and_drop_helpers",
            default = false,
        )
        val showWindowResizeHandleBoundaries = boolean(
            key = "devtools__show_window_resize_handle_boundaries",
            default = false,
        )
    }

    val dictionary = Dictionary()
    inner class Dictionary {
        val enableSystemUserDictionary = boolean(
            key = "suggestion__enable_system_user_dictionary",
            default = true,
        )
        val enableDrsUserDictionary = boolean(
            key = "suggestion__enable_drs_user_dictionary",
            default = true,
        )

        // DRS: personal word learning — the keyboard grows a local user dictionary
        // from what you accept and type. Data never leaves the device.
        val learnFromSuggestions = boolean(
            key = "suggestion__learn_from_suggestions",
            default = true,
        )
        val learnTypedWords = boolean(
            key = "suggestion__learn_typed_words",
            default = true,
        )
    }

    val emoji = Emoji()
    inner class Emoji {
        val preferredSkinTone = enum(
            key = "emoji__preferred_skin_tone",
            default = EmojiSkinTone.DEFAULT,
        )
        // DRS v1.7.0: emoji size scale as a percentage of the default
        // grid cell (42dp) and glyph (22sp). 100 = default. Real behavior
        // change: the emoji palette multiplies both its adaptive grid cell
        // and the glyph font size by this factor — density of the grid and
        // legibility of the glyphs really follow the slider.
        val sizePercent = int(
            key = "emoji__size_percent",
            default = ImeWindowSpec.EMOJI_SCALE_DEFAULT_PERCENT,
        )
        // DRS v1.20.0: `emoji__preferred_hair_style` removed — hair style is derived
        // from the emoji's own code points at parse time (Emoji.kt), so the pref was
        // declared but never read by anything, a dead setting.
        val historyEnabled = boolean(
            key = "emoji__history_enabled",
            default = true,
        )
        val historyData = custom(
            key = "emoji__history_data",
            default = EmojiHistory.Empty,
            serializer = EmojiHistory.Serializer,
        )
        val historyPinnedUpdateStrategy = enum(
            key = "emoji__history_pinned_update_strategy",
            default = EmojiHistory.UpdateStrategy.MANUAL_SORT_PREPEND,
        )
        val historyPinnedMaxSize = int(
            key = "emoji__history_pinned_max_size",
            default = EmojiHistory.MaxSizeUnlimited,
        )
        val historyRecentUpdateStrategy = enum(
            key = "emoji__history_recent_update_strategy",
            default = EmojiHistory.UpdateStrategy.AUTO_SORT_PREPEND,
        )
        val historyRecentMaxSize = int(
            key = "emoji__history_recent_max_size",
            default = 90,
        )
        val suggestionEnabled = boolean(
            key = "emoji__suggestion_enabled",
            default = true,
        )
        val suggestionType = enum(
            key = "emoji__suggestion_type",
            default = EmojiSuggestionType.LEADING_COLON,
        )
        val suggestionUpdateHistory = boolean(
            key = "emoji__suggestion_update_history",
            default = true,
        )
        val suggestionCandidateShowName = boolean(
            key = "emoji__suggestion_candidate_show_name",
            default = false,
        )
        val suggestionQueryMinLength = int(
            key = "emoji__suggestion_query_min_length",
            default = 3,
        )
        val suggestionCandidateMaxCount = int(
            key = "emoji__suggestion_candidate_max_count",
            default = 5,
        )
    }

    // DRS v1.0.4: persistent smart-search history — recent queries are stored
    // here (most recent first, deduplicated, capped) and surfaced as one-tap
    // chips inside the search dialog.
    val search = Search()
    inner class Search {
        val historyEnabled = boolean(
            key = "search__history_enabled",
            default = true,
        )
        val historyData = custom(
            key = "search__history_data",
            default = DrsSearchHistory.Empty,
            serializer = DrsSearchHistory.Serializer,
        )
    }

    val gestures = Gestures()
    inner class Gestures {
        val swipeUp = enum(
            key = "gestures__swipe_up",
            default = SwipeAction.SHIFT,
        )
        val swipeDown = enum(
            key = "gestures__swipe_down",
            default = SwipeAction.HIDE_KEYBOARD,
        )
        val swipeLeft = enum(
            key = "gestures__swipe_left",
            default = SwipeAction.SWITCH_TO_NEXT_SUBTYPE,
        )
        val swipeRight = enum(
            key = "gestures__swipe_right",
            default = SwipeAction.SWITCH_TO_PREV_SUBTYPE,
        )
        val spaceBarSwipeUp = enum(
            key = "gestures__space_bar_swipe_up",
            default = SwipeAction.NO_ACTION,
        )
        val spaceBarSwipeLeft = enum(
            key = "gestures__space_bar_swipe_left",
            default = SwipeAction.MOVE_CURSOR_LEFT,
        )
        val spaceBarSwipeRight = enum(
            key = "gestures__space_bar_swipe_right",
            default = SwipeAction.MOVE_CURSOR_RIGHT,
        )
        val spaceBarLongPress = enum(
            key = "gestures__space_bar_long_press",
            default = SwipeAction.SHOW_INPUT_METHOD_PICKER,
        )
        val deleteKeySwipeLeft = enum(
            key = "gestures__delete_key_swipe_left",
            default = SwipeAction.DELETE_CHARACTERS_PRECISELY,
        )
        val deleteKeyLongPress = enum(
            key = "gestures__delete_key_long_press",
            default = SwipeAction.DELETE_CHARACTER,
        )
        val swipeDistanceThreshold = int(
            key = "gestures__swipe_distance_threshold",
            default = 32,
        )
        val swipeVelocityThreshold = int(
            key = "gestures__swipe_velocity_threshold",
            default = 1900,
        )
    }

    val glide = Glide()
    inner class Glide {
        val enabled = boolean(
            key = "glide__enabled",
            default = false,
        )
        val showTrail = boolean(
            key = "glide__show_trail",
            default = true,
        )
        val trailDuration = int(
            key = "glide__trail_fade_duration",
            default = 200,
        )
        // DRS v1.7.0: glide trail WIDTH scale as a percentage of the
        // density-correct baseline (20dp). 100 = default. Real behavior
        // change: the trail draw call and the fade-out animator both
        // derive from this factor, so the ribbon really follows the
        // slider — on every density (the radius used to be raw px).
        val trailWidthPercent = int(
            key = "glide__trail_width_percent",
            default = ImeWindowSpec.GLIDE_TRAIL_SCALE_DEFAULT_PERCENT,
        )
        val showPreview = boolean(
            key = "glide__show_preview",
            default = true,
        )
        val previewRefreshDelay = int(
            key = "glide__preview_refresh_delay",
            default = 150,
        )
        val immediateBackspaceDeletesWord = boolean(
            key = "glide__immediate_backspace_deletes_word",
            default = true,
        )
    }

    val inputFeedback = InputFeedback()
    inner class InputFeedback {
        val audioEnabled = boolean(
            key = "input_feedback__audio_enabled",
            default = true,
        )
        val audioActivationMode = enum(
            key = "input_feedback__audio_activation_mode",
            default = InputFeedbackActivationMode.RESPECT_SYSTEM_SETTINGS,
        )
        val audioVolume = int(
            key = "input_feedback__audio_volume",
            default = 50,
        )
        val audioFeatKeyPress = boolean(
            key = "input_feedback__audio_feat_key_press",
            default = true,
        )
        val audioFeatKeyLongPress = boolean(
            key = "input_feedback__audio_feat_key_long_press",
            default = false,
        )
        val audioFeatKeyRepeatedAction = boolean(
            key = "input_feedback__audio_feat_key_repeated_action",
            default = false,
        )
        val audioFeatGestureSwipe = boolean(
            key = "input_feedback__audio_feat_gesture_swipe",
            default = false,
        )
        val audioFeatGestureMovingSwipe = boolean(
            key = "input_feedback__audio_feat_gesture_moving_swipe",
            default = false,
        )
        val drsSoundStyle = enum(
            key = "input_feedback__drs_sound_style",
            default = DrsSoundStyle.SYSTEM,
        )

        val hapticEnabled = boolean(
            key = "input_feedback__haptic_enabled",
            default = true,
        )
        val hapticActivationMode = enum(
            key = "input_feedback__haptic_activation_mode",
            default = InputFeedbackActivationMode.RESPECT_SYSTEM_SETTINGS,
        )
        val hapticVibrationMode = enum(
            key = "input_feedback__haptic_vibration_mode",
            default = HapticVibrationMode.USE_VIBRATOR_DIRECTLY,
        )
        val hapticVibrationDuration = int(
            key = "input_feedback__haptic_vibration_duration",
            default = 50,
        )
        val hapticVibrationStrength = int(
            key = "input_feedback__haptic_vibration_strength",
            default = 50,
        )
        val hapticFeatKeyPress = boolean(
            key = "input_feedback__haptic_feat_key_press",
            default = true,
        )
        val hapticFeatKeyLongPress = boolean(
            key = "input_feedback__haptic_feat_key_long_press",
            default = false,
        )
        val hapticFeatKeyRepeatedAction = boolean(
            key = "input_feedback__haptic_feat_key_repeated_action",
            default = true,
        )
        val hapticFeatGestureSwipe = boolean(
            key = "input_feedback__haptic_feat_gesture_swipe",
            default = false,
        )
        val hapticFeatGestureMovingSwipe = boolean(
            key = "input_feedback__haptic_feat_gesture_moving_swipe",
            default = true,
        )
    }

    val internal = Internal()
    inner class Internal {
        // DRS v1.20.0: `internal__home_is_beta_toolbox_collapsed_040a01` removed —
        // declared but never read anywhere, a leftover migration marker.
        val isImeSetUp = boolean(
            key = "internal__is_ime_set_up",
            default = false,
        )
        val previewPillHintShown = boolean(
            key = "internal__preview_pill_hint_shown",
            default = false,
        )
        // DRS: last persisted drag position of the floating preview pill, stored
        // as fractions (0..1) of the available overlay area so it survives both
        // app restarts and screen size/orientation changes. -1 means "not set yet".
        val previewPillAnchorX = float(
            key = "internal__preview_pill_anchor_x",
            default = -1f,
        )
        val previewPillAnchorY = float(
            key = "internal__preview_pill_anchor_y",
            default = -1f,
        )
        val versionOnInstall = string(
            key = "internal__version_on_install",
            default = VersionName.DEFAULT_RAW,
        )
        val versionLastUse = string(
            key = "internal__version_last_use",
            default = VersionName.DEFAULT_RAW,
        )
        val versionLastChangelog = string(
            key = "internal__version_last_changelog",
            default = VersionName.DEFAULT_RAW,
        )
        val notificationPermissionState = enum(
            key = "internal__notification_permission_state",
            default = NotificationPermissionState.NOT_SET,
        )
    }

    val keyboard = Keyboard()
    inner class Keyboard {
        val windowConfig = custom(
            key = "keyboard__window_config",
            default = emptyMap(),
            serializer = ImeWindowConfig.ByTypeSerializer,
        )
        val numberRow = boolean(
            key = "keyboard__number_row",
            default = false,
        )
        val hintedNumberRowEnabled = boolean(
            key = "keyboard__hinted_number_row_enabled",
            default = true,
        )
        val hintedNumberRowMode = enum(
            key = "keyboard__hinted_number_row_mode",
            default = KeyHintMode.SMART_PRIORITY,
        )
        val hintedSymbolsEnabled = boolean(
            key = "keyboard__hinted_symbols_enabled",
            default = true,
        )
        val hintedSymbolsMode = enum(
            key = "keyboard__hinted_symbols_mode",
            default = KeyHintMode.SMART_PRIORITY,
        )
        val utilityKeyEnabled = boolean(
            key = "keyboard__utility_key_enabled",
            default = true,
        )
        val utilityKeyAction = enum(
            key = "keyboard__utility_key_action",
            default = UtilityKeyAction.DYNAMIC_SWITCH_LANGUAGE_EMOJIS,
        )
        val spaceBarMode = enum(
            key = "keyboard__space_bar_display_mode",
            default = SpaceBarMode.CURRENT_LANGUAGE,
        )
        val capitalizationBehavior = enum(
            key = "keyboard__capitalization_behavior",
            default = CapitalizationBehavior.CAPSLOCK_BY_DOUBLE_TAP,
        )
        val fontSizeMultiplierPortrait = int(
            key = "keyboard__font_size_multiplier_portrait",
            default = 100,
        )
        val fontSizeMultiplierLandscape = int(
            key = "keyboard__font_size_multiplier_landscape",
            default = 100,
        )
        val landscapeInputUiMode = enum(
            key = "keyboard__landscape_input_ui_mode",
            default = LandscapeInputUiMode.DYNAMICALLY_SHOW,
        )
        val keySpacingVertical = int(
            key = "keyboard__key_spacing_vertical",
            default = 100,
        )
        val keySpacingHorizontal = int(
            key = "keyboard__key_spacing_horizontal",
            default = 100,
        )
        val popupEnabled = boolean(
            key = "keyboard__popup_enabled",
            default = true,
        )
        val mergeHintPopupsEnabled = boolean(
            key = "keyboard__merge_hint_popups_enabled",
            default = false,
        )
        val longPressDelay = int(
            key = "keyboard__long_press_delay",
            default = 300,
        )
        // DRS v1.0.6: key repeat RATE as a percentage of the platform rate.
        // 100 = system default, higher = faster repeating (shorter delay).
        // Real behavior change: InputEventDispatcher divides the platform
        // repeat delay by this factor.
        val keyRepeatRatePercent = int(
            key = "keyboard__key_repeat_rate_percent",
            default = 100,
        )
        // DRS v1.0.8: keyboard HEIGHT scale as a percentage of the baseline
        // height. 100 = default. Real behavior change: ImeWindowController
        // feeds it into ImeWindowSpec.UserPreferredOptions.heightScale and
        // ImeWindowSpec.calcRowHeight() scales every rendered row/key.
        val heightScalePercent = int(
            key = "keyboard__height_scale_percent",
            default = ImeWindowSpec.HEIGHT_SCALE_DEFAULT_PERCENT,
        )
        // DRS v1.2.0: Smartbar HEIGHT scale as a percentage of the baseline
        // Smartbar row height. 100 = default. Real behavior change: the
        // sizing layer multiplies the provided Smartbar row height by this
        // factor, so the live Smartbar (and every consumer of
        // DrsImeSizing.smartbarHeight) really follows the slider.
        val smartbarHeightScalePercent = int(
            key = "keyboard__smartbar_height_scale_percent",
            default = ImeWindowSpec.SMARTBAR_HEIGHT_SCALE_DEFAULT_PERCENT,
        )
        // DRS v1.6.0: key-popup (long-press preview) scale as a percentage
        // of the default popup box size. 100 = default. Real behavior
        // change: the popup bounds provider in TextKeyboardLayout
        // multiplies the per-orientation popup multipliers by this factor,
        // so the live key preview really follows the slider.
        val previewScalePercent = int(
            key = "keyboard__preview_scale_percent",
            default = ImeWindowSpec.KEY_PREVIEW_SCALE_DEFAULT_PERCENT,
        )
        val spaceBarSwitchesToCharacters = boolean(
            key = "keyboard__space_bar_switches_to_characters",
            default = true,
        )
        /**
         * DRS v1.17.0: haraka-first backspace — deleting peels the diacritic
         * off the letter first (one mark per tap) instead of the ICU
         * grapheme cluster (letter + all its marks in one tap).
         */
        val backspaceStripsHarakat = boolean(
            key = "keyboard__backspace_strips_harakat",
            default = true,
        )
        // DRS v1.18.0: split keyboard — two halves with a central gap so
        // thumbs reach on wide screens. AUTO splits only on wide keyboards
        // (>= 560dp, i.e. landscape phones / foldables / tablets); the gap
        // renders on the letters page in the normal fixed window mode only.
        val splitMode = enum(
            key = "keyboard__split_mode",
            default = SplitMode.AUTO,
        )
        val incognitoDisplayMode = enum(
            key = "keyboard__incognito_indicator",
            default = IncognitoDisplayMode.DISPLAY_BEHIND_KEYBOARD,
        )

        fun keyHintConfiguration(): KeyHintConfiguration {
            return KeyHintConfiguration(
                numberHintMode = when {
                    hintedNumberRowEnabled.get() -> hintedNumberRowMode.get()
                    else -> KeyHintMode.DISABLED
                },
                symbolHintMode = when {
                    hintedSymbolsEnabled.get() -> hintedSymbolsMode.get()
                    else -> KeyHintMode.DISABLED
                },
                mergeHintPopups = mergeHintPopupsEnabled.get(),
            )
        }
    }

    val localization = Localization()
    inner class Localization {
        val displayLanguageNamesIn = enum(
            key = "localization__display_language_names_in",
            default = DisplayLanguageNamesIn.SYSTEM_LOCALE,
        )
        val displayKeyboardLabelsInSubtypeLanguage = boolean(
            key = "localization__display_keyboard_labels_in_subtype_language",
            default = false,
        )
        val activeSubtypeId = long(
            key = "localization__active_subtype_id",
            default = Subtype.DEFAULT.id,
        )
        val subtypes = string(
            key = "localization__subtypes",
            default = "[]",
        )
    }

    val other = Other()
    inner class Other {
        val settingsTheme = enum(
            key = "other__settings_theme",
            default = AppTheme.AUTO,
        )
        val accentColor = custom(
            key = "other__accent_color",
            default = Color.Unspecified,
            serializer = ColorPreferenceSerializer,
        )
        val settingsLanguage = string(
            key = "other__settings_language",
            // DRS: Arabic is the native language of the app. Users can still switch
            // the in-app language at any time (Settings > Advanced > Other), and
            // "auto" keeps following the system language.
            default = "ar",
        )
        val showAppIcon = boolean(
            key = "other__show_app_icon",
            default = true,
        )
    }

    val updates = Updates()
    inner class Updates {
        val checkMode = enum(
            key = "updates__check_mode",
            // DRS v1.28.0 audit fix (Medium): the default now matches the
            // documented opt-in contract in DrsUpdateCenter ("MANUAL is the
            // honest default of an opt-in system"). No network request is
            // made unless the user opens the update center or opts into a
            // schedule themselves. Existing users who already have a stored
            // value keep it.
            default = UpdateCheckMode.MANUAL,
        )
        val notifyOnUpdate = boolean(
            key = "updates__notify_on_update",
            default = true,
        )
        val lastCheckTimestamp = long(
            key = "updates__last_check_timestamp",
            default = 0L,
        )
    }

    val physicalKeyboard = PhysicalKeyboard()
    inner class PhysicalKeyboard {
        val showOnScreenKeyboard = boolean(
            key = "physical_keyboard__show_on_screen_keyboard",
            default = false,
        )
    }

    val smartbar = Smartbar()
    inner class Smartbar {
        val enabled = boolean(
            key = "smartbar__enabled",
            default = true,
        )
        // DRS M2.3 — the strip's corner radius (Design 2030 shape control):
        // 0 = the classic square strip, 28 = the pill look. Sanitized by
        // DrsSmartbarShape on both the slider and the drawing path.
        val drsCornerRadius = int(
            key = "smartbar__drs_corner_radius",
            default = DrsSmartbarShape.DEFAULT_RADIUS_DP,
        )
        val layout = enum(
            key = "smartbar__layout",
            default = SmartbarLayout.SUGGESTIONS_ACTIONS_SHARED,
        )
        val actionArrangement = custom(
            key = "smartbar__action_arrangement",
            default = QuickActionArrangement.Default,
            serializer = QuickActionArrangement.Serializer,
        )
        val flipToggles = boolean(
            key = "smartbar__flip_toggles",
            default = false,
        )
        val sharedActionsExpanded = boolean(
            key = "smartbar__shared_actions_expanded",
            default = false,
        )
        @Deprecated("Always enabled due to UX issues")
        val sharedActionsAutoExpandCollapse = boolean(
            key = "smartbar__shared_actions_auto_expand_collapse",
            default = true,
        )
        val sharedActionsExpandWithAnimation = boolean(
            key = "smartbar__shared_actions_expand_with_animation",
            default = true,
        )
        val extendedActionsExpanded = boolean(
            key = "smartbar__extended_actions_expanded",
            default = false,
        )
        val extendedActionsPlacement = enum(
            key = "smartbar__extended_actions_placement",
            default = ExtendedActionsPlacement.ABOVE_CANDIDATES,
        )
    }

    /**
     * DRS v1.15.0: the smart panels' behavior switches (لوحة الحركات /
     * لوحة الرموز الذكية / لوحة الحروف الموسعة). Every default preserves
     * the smart behavior, and every switch is a real behavior toggle.
     */
    val panels = Panels()
    inner class Panels {
        /** Smart harakat stacking: a mark over a mark replaces it. */
        val harakatSmartReplace = boolean(
            key = "panels__harakat_smart_replace",
            default = true,
        )
        /** Context-aware symbol suggestions row in the smart symbols panel. */
        val symbolSmartSuggestions = boolean(
            key = "panels__symbol_smart_suggestions",
            default = true,
        )
        /** The shared most-used recents rows of the three smart panels. */
        val panelRecents = boolean(
            key = "panels__recents_enabled",
            default = true,
        )
        /**
         * DRS v1.16.0: the smart ordering of the panels' switcher chips —
         * the current panel leads, the rest follow the local open counts.
         * Off = the fixed catalogue order.
         */
        val panelSmartOrder = boolean(
            key = "panels__smart_order",
            default = true,
        )
        /**
         * DRS v1.1.0: the smart harakat board's contextual advice strip —
         * the on-device advisor ranks the harakat that make sense at the
         * cursor (tanween completion, shadda's vowel, the definite-article
         * lam) plus the lexicon word-tashkeel action. Off = a plain board.
         */
        val harakatSmartAdvice = boolean(
            key = "panels__harakat_smart_advice",
            default = true,
        )
        /**
         * DRS v2.2.0: the text tools panel's contextual advisory row —
         * the deterministic tiles' brain (DrsTileContextAdvisor) ranks
         * the tools that make sense for the text before the cursor.
         * Off = a plain panel without the advisory row.
         */
        val textToolsSmartContext = boolean(
            key = "panels__text_tools_smart_context",
            default = true,
        )
    }

    val spelling = Spelling()
    inner class Spelling {
        val languageMode = enum(
            key = "spelling__language_mode",
            default = SpellingLanguageMode.USE_KEYBOARD_SUBTYPES,
        )
        // DRS v1.19.0: real typo marking behind the conservative SpellingDecider
        // gates (rich dictionaries only, plausible correction required). Default
        // on — the spell-checker channel finally does what the system expects.
        val typoFlaggingEnabled = boolean(
            key = "spelling__typo_flagging_enabled",
            default = true,
        )
        // DRS v1.20.0: `spelling__use_contacts` / `spelling__use_udm_entries` removed —
        // they were declared and UI-stubbed with visibleIf=false but no contacts
        // integration exists anywhere in the IME; a setting that promises a behavior
        // nothing implements is a false promise. (Contacts lookups would also need
        // READ_CONTACTS; if that feature ever lands, the prefs come back wired.)
    }

    val suggestion = Suggestion()
    inner class Suggestion {
        val api30InlineSuggestionsEnabled = boolean(
            key = "suggestion__api30_inline_suggestions_enabled",
            default = true,
        )
        val enabled = boolean(
            key = "suggestion__enabled",
            default = true,
        )
        val displayMode = enum(
            key = "suggestion__display_mode",
            default = CandidatesDisplayMode.DYNAMIC_SCROLLABLE,
        )
        val blockPossiblyOffensive = boolean(
            key = "suggestion__block_possibly_offensive",
            default = true,
        )
        // DRS: gate the next-word prediction path (empty composing state) independently
        // of the prefix suggestions themselves.
        val nextWordEnabled = boolean(
            key = "suggestion__next_word_enabled",
            default = true,
        )
        /**
         * DRS v1.17.0: TRUE autocorrect — when the typed word matches NO
         * dictionary prefix and a very common word sits within edit
         * distance 1, space silently commits the fix (backspace reverts).
         * Gated by [com.drs.smartkeyboard.ime.nlp.AutocorrectDecider].
         */
        val autocorrectEnabled = boolean(
            key = "suggestion__autocorrect_enabled",
            default = true,
        )
        val incognitoMode = enum(
            key = "suggestion__incognito_mode",
            default = IncognitoMode.DYNAMIC_ON_OFF,
        )
        // Internal pref
        val forceIncognitoModeFromDynamic = boolean(
            key = "suggestion__force_incognito_mode_from_dynamic",
            default = false,
        )
    }

    /**
     * DRS v1.24.0: the built-in voice dictation gate — «الميكروفون في
     * قبضة المستخدم». v1.23.0 woke the microphone up with no explicit
     * user-facing switch; the settings gate below is the real gate the
     * pure [com.drs.smartkeyboard.ime.voice.decideVoiceInputRoute]
     * consults on every mic-key press, so switching it off silences the
     * built-in recognizer with an honest toast on the very next press.
     */
    val voice = Voice()
    inner class Voice {
        val enabled = boolean(
            key = "voice__enabled",
            default = true,
        )
        /**
         * DRS v1.25.0: which recognizer may listen — the second voice
         * gate. AUTO keeps the v1.23 behavior; ON_DEVICE_ONLY refuses
         * any network-backed service (and answers with an honest toast
         * when the ROM cannot honor the demand); STANDARD pins the
         * classic recognizer. Consulted by
         * [com.drs.smartkeyboard.ime.voice.decideVoiceInputRoute] on
         * every mic press and by the controller at every start.
         *
         * DRS (S-1) — privacy-first default: ON_DEVICE_ONLY. A keyboard
         * must never ship a default that can stream typed/spoken text to
         * a network recognizer; users may still opt into AUTO/STANDARD
         * explicitly (the settings disclosure explains the difference).
         */
        val recognizerMode = enum(
            key = "voice__recognizer_mode",
            default = VoiceRecognizerMode.ON_DEVICE_ONLY,
        )

        /**
         * DRS Phase 2 (roadmap task 12): continuous dictation — after each
         * recognized result the microphone re-arms automatically until the
         * user taps the mic again or speaks a terminating command
         * («إيقاف الإملاء» / «امسح الكل» / «stop dictation» / «clear all»).
         * Off by default: every session still starts from an explicit mic
         * press (the same privacy gate v1.24.0 established).
         */
        val continuousDictation = boolean(
            key = "voice__continuous_dictation",
            default = false,
        )
    }

    /**
     * DRS roadmap phase 3 (privacy & trust): the absolute privacy mode.
     * One switch, default OFF, consulted by [com.drs.smartkeyboard.drs.privacy.DrsPrivacyLock]
     * at every network-capable surface (update check/download, package
     * manifest/download) and by the voice route (coerces the recognizer
     * to ON_DEVICE_ONLY). The privacy dashboard renders the live
     * network-sentinel attestation as the user-verifiable proof.
     */
    val privacy = Privacy()
    inner class Privacy {
        val absoluteMode = boolean(
            key = "privacy__absolute_mode",
            default = false,
        )
    }

    val theme = Theme()
    inner class Theme {
        val mode = enum(
            key = "theme__mode",
            default = ThemeMode.FOLLOW_SYSTEM,
        )
        val dayThemeId = custom(
            key = "theme__day_theme_id",
            default = extCoreTheme("drs_day"),
            serializer = ExtensionComponentName.Serializer,
        )
        val nightThemeId = custom(
            key = "theme__night_theme_id",
            default = extCoreTheme("drs_night"),
            serializer = ExtensionComponentName.Serializer,
        )
        val accentColor = custom(
            key = "theme__accent_color",
            default = Color.Unspecified,
            serializer = ColorPreferenceSerializer,
        )
        val sunriseTime = localTime(
            key = "theme__sunrise_time",
            default = LocalTime(6, 0),
        )
        val sunsetTime = localTime(
            key = "theme__sunset_time",
            default = LocalTime(18, 0),
        )
        val editorColorRepresentation = enum(
            key = "theme__editor_color_representation",
            default = ColorRepresentation.HEX,
        )
        val editorDisplayKbdAfterDialogs = enum(
            key = "theme__editor_display_kbd_after_dialogs",
            default = DisplayKbdAfterDialogs.REMEMBER,
        )
        val editorLevel = enum(
            key = "theme__editor_level",
            default = SnyggLevel.ADVANCED,
        )
    }

    override fun migrate(entry: PreferenceMigrationEntry): PreferenceMigrationEntry {
        return when (entry.key) {

            // Migrate media prefs to emoji prefs
            // Keep migration rule until: 0.6 dev cycle
            "media__emoji_recently_used" -> {
                val emojiValues = entry.rawValue.split(";")
                val recent = emojiValues.map {
                    com.drs.smartkeyboard.ime.media.emoji.Emoji(it, "", emptyList())
                }
                val data = EmojiHistory(emptyList(), recent)
                entry.transform(key = "emoji__history_data", rawValue = Json.encodeToString(data))
            }
            "media__emoji_recently_used_max_size" -> {
                entry.transform(key = "emoji__history_recent_max_size")
            }

            // Migrate advanced prefs to other prefs
            // Keep migration rules until: 0.7 dev cycle
            "advanced__settings_theme" -> {
                entry.transform(key = "other__settings_theme")
            }
            "advanced__accent_color" -> {
                entry.transform(key = "other__accent_color")
            }
            "advanced__settings_language" -> {
                entry.transform(key = "other__settings_language")
            }
            "advanced__show_app_icon" -> {
                entry.transform(key = "other__show_app_icon")
            }
            "advanced__incognito_mode" -> {
                entry.transform(key = "suggestion__incognito_mode")
            }
            "advanced__force_incognito_mode_from_dynamic" -> {
                entry.transform(key = "suggestion__force_incognito_mode_from_dynamic")
            }
            // Migrate clipboard suggestion prefs to clipboard
            // Keep migration rules until: 0.7 dev cycle
            "suggestion__clipboard_content_enabled" -> {
                entry.transform(key = "clipboard__suggestion_enabled")
            }
            "suggestion__clipboard_content_timeout" -> {
                entry.transform(key = "clipboard__suggestion_timeout")
            }

            //Migrate one hand mode prefs keep until: 0.7 dev cycle
            "keyboard__one_handed_mode" -> {
                if (entry.rawValue == "OFF") {
                    entry.reset()
                } else {
                    entry.keepAsIs()
                }
            }
            "smartbar__action_arrangement" -> {
                fun migrateAction(action: QuickAction): QuickAction {
                    return if (action is QuickAction.InsertKey && action.data.code == KeyCode.COMPACT_LAYOUT_TO_RIGHT) {
                        action.copy(data = TextKeyData.TOGGLE_COMPACT_LAYOUT)
                    } else {
                        action
                    }
                }

                val arrangement = QuickActionJsonConfig.decodeFromString<QuickActionArrangement>(entry.rawValue)
                var newArrangement = arrangement.copy(
                    stickyAction = arrangement.stickyAction?.let{ migrateAction(it) },
                    dynamicActions = arrangement.dynamicActions.map { migrateAction(it) },
                    hiddenActions = arrangement.hiddenActions.map { migrateAction(it) },
                )
                if (QuickAction.InsertKey(TextKeyData.LANGUAGE_SWITCH) !in newArrangement) {
                    newArrangement = newArrangement.copy(
                        dynamicActions = newArrangement.dynamicActions.plus(QuickAction.InsertKey(TextKeyData.LANGUAGE_SWITCH))
                    )
                }
                if (QuickAction.InsertKey(TextKeyData.FORWARD_DELETE) !in newArrangement) {
                    newArrangement = newArrangement.copy(
                        dynamicActions = newArrangement.dynamicActions.plus(QuickAction.InsertKey(TextKeyData.FORWARD_DELETE))
                    )
                }
                if (QuickAction.InsertKey(TextKeyData.IME_HIDE_UI) !in newArrangement) {
                    newArrangement = newArrangement.copy(
                        dynamicActions = newArrangement.dynamicActions.plus(QuickAction.InsertKey(TextKeyData.IME_HIDE_UI))
                    )
                }
                if (QuickAction.InsertKey(TextKeyData.TOGGLE_FLOATING_WINDOW) !in newArrangement) {
                    newArrangement = newArrangement.copy(
                        dynamicActions = newArrangement.dynamicActions.plus(QuickAction.InsertKey(TextKeyData.TOGGLE_FLOATING_WINDOW))
                    )
                }
                if (QuickAction.InsertKey(TextKeyData.TOGGLE_RESIZE_MODE) !in newArrangement) {
                    newArrangement = newArrangement.copy(
                        dynamicActions = newArrangement.dynamicActions.plus(QuickAction.InsertKey(TextKeyData.TOGGLE_RESIZE_MODE))
                    )
                }
                val json = QuickActionJsonConfig.encodeToString(newArrangement.distinct())
                entry.transform(rawValue = json)
            }

            // Migrate theme editor fine-tuning
            // Keep migration rule until: 0.6 dev cycle
            "theme__editor_display_colors_as" -> {
                val colorRepresentation = when (entry.rawValue) {
                    "RGBA" -> ColorRepresentation.RGB
                    else -> ColorRepresentation.HEX
                }
                entry.transform(
                    key = "theme__editor_color_representation",
                    rawValue = colorRepresentation.name,
                )
            }

            // Migrate clipboard history pref names
            // Keep migration rules until: 0.7 dev cycle
            "clipboard__sync_to_drs", "clipboard__sync_to_system" -> {
                entry.transform(
                    type = PreferenceType.string(),
                    rawValue = when (entry.rawValue) {
                        "true" -> ClipboardSyncBehavior.ALL_EVENTS.name
                        "false" -> ClipboardSyncBehavior.NO_EVENTS.name
                        else -> entry.rawValue
                    },
                )
            }
            "clipboard__num_history_grid_columns_portrait" -> {
                entry.transform(key = "clipboard__history_num_grid_columns_portrait")
            }
            "clipboard__num_history_grid_columns_landscape" -> {
                entry.transform(key = "clipboard__history_num_grid_columns_landscape")
            }
            "clipboard__clean_up_old" -> {
                entry.transform(key = "clipboard__history_auto_clean_old_enabled")
            }
            "clipboard__clean_up_after" -> {
                entry.transform(key = "clipboard__history_auto_clean_old_after")
            }
            "clipboard__auto_clean_sensitive" -> {
                entry.transform(key = "clipboard__history_auto_clean_sensitive_enabled")
            }
            "clipboard__auto_clean_sensitive_after" -> {
                entry.transform(key = "clipboard__history_auto_clean_sensitive_after")
            }
            "clipboard__limit_history_size" -> {
                entry.transform(key = "clipboard__history_size_limit_enabled")
            }
            "clipboard__max_history_size" -> {
                entry.transform(key = "clipboard__history_size_limit")
            }
            "clipboard__clear_primary_clip_deletes_last_item" -> {
                entry.transform(key = "clipboard__clear_primary_clip_affects_history_if_unpinned")
            }

            // Migrate key spacing rules
            // Keep migration rules until: 0.8 dev cycle
            "keyboard__key_spacing_horizontal" -> {
                if (entry.type.isFloat()) {
                    entry.reset()
                } else {
                    entry.keepAsIs()
                }
            }
            "keyboard__key_spacing_vertical" -> {
                if (entry.type.isFloat()) {
                    entry.reset()
                } else {
                    entry.keepAsIs()
                }
            }

            // Default: keep entry
            else -> entry.keepAsIs()
        }
    }
}
