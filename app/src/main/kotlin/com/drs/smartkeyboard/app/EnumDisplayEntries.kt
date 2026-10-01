/*
 * Copyright (C) 2025 The DRS Smart Keyboard Project
 */

package com.drs.smartkeyboard.app

import androidx.compose.runtime.Composable
import com.drs.smartkeyboard.R
import com.drs.smartkeyboard.app.settings.theme.DisplayKbdAfterDialogs
import com.drs.smartkeyboard.app.settings.theme.SnyggLevel
import com.drs.smartkeyboard.app.drsupdater.UpdateCheckMode
import com.drs.smartkeyboard.ime.clipboard.ClipEditorCharLimit
import com.drs.smartkeyboard.ime.clipboard.ClipEditorPopupSize
import com.drs.smartkeyboard.ime.clipboard.ClipEditorRoute
import com.drs.smartkeyboard.ime.clipboard.ClipEditorScrim
import com.drs.smartkeyboard.ime.voice.VoiceRecognizerMode
import com.drs.smartkeyboard.ime.clipboard.ClipFontOption
import com.drs.smartkeyboard.ime.clipboard.ClipFontSizeOption
import com.drs.smartkeyboard.ime.clipboard.ClipHistorySort
import com.drs.smartkeyboard.ime.clipboard.ClipboardSyncBehavior
import com.drs.smartkeyboard.ime.core.DisplayLanguageNamesIn
import com.drs.smartkeyboard.ime.input.CapitalizationBehavior
import com.drs.smartkeyboard.ime.input.DrsSoundStyle
import com.drs.smartkeyboard.ime.input.HapticVibrationMode
import com.drs.smartkeyboard.ime.input.InputFeedbackActivationMode
import com.drs.smartkeyboard.ime.input.InputShiftState
import com.drs.smartkeyboard.ime.keyboard.IncognitoMode
import com.drs.smartkeyboard.ime.keyboard.KeyboardMode
import com.drs.smartkeyboard.ime.keyboard.SpaceBarMode
import com.drs.smartkeyboard.ime.landscapeinput.LandscapeInputUiMode
import com.drs.smartkeyboard.ime.media.emoji.EmojiHistory
import com.drs.smartkeyboard.ime.media.emoji.EmojiSkinTone
import com.drs.smartkeyboard.ime.media.emoji.EmojiSuggestionType
import com.drs.smartkeyboard.ime.nlp.SpellingLanguageMode
import com.drs.smartkeyboard.ime.smartbar.CandidatesDisplayMode
import com.drs.smartkeyboard.ime.smartbar.ExtendedActionsPlacement
import com.drs.smartkeyboard.ime.smartbar.IncognitoDisplayMode
import com.drs.smartkeyboard.ime.smartbar.SmartbarLayout
import com.drs.smartkeyboard.ime.keyboard.SplitMode
import com.drs.smartkeyboard.ime.text.gestures.SwipeAction
import com.drs.smartkeyboard.ime.text.key.KeyHintMode
import com.drs.smartkeyboard.ime.text.key.UtilityKeyAction
import com.drs.smartkeyboard.ime.theme.ThemeMode
import com.drs.smartkeyboard.ime.window.ImeWindowMode
import org.drs.jetpref.datastore.ui.ListPreferenceEntry
import org.drs.jetpref.datastore.ui.listPrefEntries
import org.drs.jetpref.material.ui.ColorRepresentation
import org.drs.lib.compose.stringRes
import org.drs.lib.kotlin.curlyFormat
import kotlin.reflect.KClass

private const val DEFAULT = ""

private val ENUM_DISPLAY_ENTRIES = mapOf<Pair<KClass<*>, String>, @Composable () -> List<ListPreferenceEntry<*>>>(
    AppTheme::class to DEFAULT to {
        listPrefEntries {
            entry(
                key = AppTheme.AUTO,
                label = stringRes(R.string.settings__system_default),
            )
            entry(
                key = AppTheme.AUTO_AMOLED,
                label = stringRes(R.string.pref__other__settings_theme__auto_amoled),
            )
            entry(
                key = AppTheme.LIGHT,
                label = stringRes(R.string.pref__other__settings_theme__light),
            )
            entry(
                key = AppTheme.DARK,
                label = stringRes(R.string.pref__other__settings_theme__dark),
            )
            entry(
                key = AppTheme.AMOLED_DARK,
                label = stringRes(R.string.pref__other__settings_theme__amoled_dark),
            )
        }
    },
    CandidatesDisplayMode::class to DEFAULT to {
        listPrefEntries {
            entry(
                key = CandidatesDisplayMode.CLASSIC,
                label = stringRes(R.string.enum__candidates_display_mode__classic),
            )
            entry(
                key = CandidatesDisplayMode.DYNAMIC,
                label = stringRes(R.string.enum__candidates_display_mode__dynamic),
            )
            entry(
                key = CandidatesDisplayMode.DYNAMIC_SCROLLABLE,
                label = stringRes(R.string.enum__candidates_display_mode__dynamic_scrollable),
            )
        }
    },
    CapitalizationBehavior::class to DEFAULT to {
        listPrefEntries {
            entry(
                key = CapitalizationBehavior.CAPSLOCK_BY_DOUBLE_TAP,
                label = stringRes(R.string.enum__capitalization_behavior__capslock_by_double_tap),
            )
            entry(
                key = CapitalizationBehavior.CAPSLOCK_BY_CYCLE,
                label = stringRes(R.string.enum__capitalization_behavior__capslock_by_cycle),
            )
        }
    },
    ClipboardSyncBehavior::class to DEFAULT to {
        listPrefEntries {
            entry(
                key = ClipboardSyncBehavior.NO_EVENTS,
                label = stringRes(R.string.enum__clipboard_sync_behavior__no_events),
                description = stringRes(R.string.enum__clipboard_sync_behavior__no_events__description),
            )
            entry(
                key = ClipboardSyncBehavior.ONLY_CLEAR_EVENTS,
                label = stringRes(R.string.enum__clipboard_sync_behavior__only_clear_events),
                description = stringRes(R.string.enum__clipboard_sync_behavior__only_clear_events__description),
            )
            entry(
                key = ClipboardSyncBehavior.ONLY_SET_EVENTS,
                label = stringRes(R.string.enum__clipboard_sync_behavior__only_set_events),
                description = stringRes(R.string.enum__clipboard_sync_behavior__only_set_events__description),
            )
            entry(
                key = ClipboardSyncBehavior.ALL_EVENTS,
                label = stringRes(R.string.enum__clipboard_sync_behavior__all_events),
                description = stringRes(R.string.enum__clipboard_sync_behavior__all_events__description),
            )
        }
    },
    ColorRepresentation::class to DEFAULT to {
        listPrefEntries {
            entry(
                key = ColorRepresentation.HEX,
                label = stringRes(R.string.enum__color_representation__hex),
                description = stringRes(R.string.general__example_given).curlyFormat("example" to "#4caf50ff"),
                showDescriptionOnlyIfSelected = true,
            )
            entry(
                key = ColorRepresentation.RGB,
                label = stringRes(R.string.enum__color_representation__rgb),
                description = stringRes(R.string.general__example_given).curlyFormat("example" to "rgba(76, 175, 80, 1.0)"),
                showDescriptionOnlyIfSelected = true,
            )
            entry(
                key = ColorRepresentation.HSV,
                label = stringRes(R.string.enum__color_representation__hsv),
                description = stringRes(R.string.general__example_given).curlyFormat("example" to "hsva(122, 56, 68, 1.0)"),
                showDescriptionOnlyIfSelected = true,
            )
        }
    },
    DisplayKbdAfterDialogs::class to DEFAULT to {
        listPrefEntries {
            entry(
                key = DisplayKbdAfterDialogs.ALWAYS,
                label = stringRes(R.string.enum__display_kbd_after_dialogs__always),
                description = stringRes(R.string.enum__display_kbd_after_dialogs__always__description),
                showDescriptionOnlyIfSelected = true,
            )
            entry(
                key = DisplayKbdAfterDialogs.NEVER,
                label = stringRes(R.string.enum__display_kbd_after_dialogs__never),
                description = stringRes(R.string.enum__display_kbd_after_dialogs__never__description),
                showDescriptionOnlyIfSelected = true,
            )
            entry(
                key = DisplayKbdAfterDialogs.REMEMBER,
                label = stringRes(R.string.enum__display_kbd_after_dialogs__remember),
                description = stringRes(R.string.enum__display_kbd_after_dialogs__remember__description),
                showDescriptionOnlyIfSelected = true,
            )
        }
    },
    DisplayLanguageNamesIn::class to DEFAULT to {
        listPrefEntries {
            entry(
                key = DisplayLanguageNamesIn.SYSTEM_LOCALE,
                label = stringRes(R.string.enum__display_language_names_in__system_locale),
                description = stringRes(R.string.enum__display_language_names_in__system_locale__description),
                showDescriptionOnlyIfSelected = true,
            )
            entry(
                key = DisplayLanguageNamesIn.NATIVE_LOCALE,
                label = stringRes(R.string.enum__display_language_names_in__native_locale),
                description = stringRes(R.string.enum__display_language_names_in__native_locale__description),
                showDescriptionOnlyIfSelected = true,
            )
        }
    },
    EmojiHistory.UpdateStrategy::class to DEFAULT to {
        listPrefEntries {
            entry(
                key = EmojiHistory.UpdateStrategy.AUTO_SORT_PREPEND,
                label = stringRes(R.string.enum__emoji_history_update_strategy__auto_sort_prepend),
                description = stringRes(R.string.enum__emoji_history_update_strategy__auto_sort_prepend__description),
            )
            entry(
                key = EmojiHistory.UpdateStrategy.AUTO_SORT_APPEND,
                label = stringRes(R.string.enum__emoji_history_update_strategy__auto_sort_append),
                description = stringRes(R.string.enum__emoji_history_update_strategy__auto_sort_append__description),
            )
            entry(
                key = EmojiHistory.UpdateStrategy.MANUAL_SORT_PREPEND,
                label = stringRes(R.string.enum__emoji_history_update_strategy__manual_sort_prepend),
                description = stringRes(R.string.enum__emoji_history_update_strategy__manual_sort_prepend__description),
            )
            entry(
                key = EmojiHistory.UpdateStrategy.MANUAL_SORT_APPEND,
                label = stringRes(R.string.enum__emoji_history_update_strategy__manual_sort_append),
                description = stringRes(R.string.enum__emoji_history_update_strategy__manual_sort_append__description),
            )
        }
    },
    EmojiSkinTone::class to DEFAULT to {
        listPrefEntries {
            entry(
                key = EmojiSkinTone.DEFAULT,
                label = stringRes(
                    R.string.enum__emoji_skin_tone__default,
                    "emoji" to "\uD83D\uDC4B" // 👋
                ),
            )
            entry(
                key = EmojiSkinTone.LIGHT_SKIN_TONE,
                label = stringRes(
                    R.string.enum__emoji_skin_tone__light_skin_tone,
                    "emoji" to "\uD83D\uDC4B\uD83C\uDFFB" // 👋🏻
                ),
            )
            entry(
                key = EmojiSkinTone.MEDIUM_LIGHT_SKIN_TONE,
                label = stringRes(
                    R.string.enum__emoji_skin_tone__medium_light_skin_tone,
                    "emoji" to "\uD83D\uDC4B\uD83C\uDFFC" // 👋🏼
                ),
            )
            entry(
                key = EmojiSkinTone.MEDIUM_SKIN_TONE,
                label = stringRes(
                    R.string.enum__emoji_skin_tone__medium_skin_tone,
                    "emoji" to "\uD83D\uDC4B\uD83C\uDFFD" // 👋🏽
                ),
            )
            entry(
                key = EmojiSkinTone.MEDIUM_DARK_SKIN_TONE,
                label = stringRes(
                    R.string.enum__emoji_skin_tone__medium_dark_skin_tone,
                    "emoji" to "\uD83D\uDC4B\uD83C\uDFFE" // 👋🏾
                ),
            )
            entry(
                key = EmojiSkinTone.DARK_SKIN_TONE,
                label = stringRes(
                    R.string.enum__emoji_skin_tone__dark_skin_tone,
                    "emoji" to "\uD83D\uDC4B\uD83C\uDFFF" // 👋🏿
                ),
            )
        }
    },
    EmojiSuggestionType::class to DEFAULT to {
        listPrefEntries {
            entry(
                key = EmojiSuggestionType.LEADING_COLON,
                label = stringRes(R.string.enum__emoji_suggestion_type__leading_colon),
                description = stringRes(R.string.enum__emoji_suggestion_type__leading_colon__description),
            )
            entry(
                key = EmojiSuggestionType.INLINE_TEXT,
                label = stringRes(R.string.enum__emoji_suggestion_type__inline_text),
                description = stringRes(R.string.enum__emoji_suggestion_type__inline_text__description),
            )
        }
    },
    ExtendedActionsPlacement::class to DEFAULT to {
        listPrefEntries {
            entry(
                key = ExtendedActionsPlacement.ABOVE_CANDIDATES,
                label = stringRes(R.string.enum__extended_actions_placement__above_candidates),
                description = stringRes(R.string.enum__extended_actions_placement__above_candidates__description),
                showDescriptionOnlyIfSelected = true,
            )
            entry(
                key = ExtendedActionsPlacement.BELOW_CANDIDATES,
                label = stringRes(R.string.enum__extended_actions_placement__below_candidates),
                description = stringRes(R.string.enum__extended_actions_placement__below_candidates__description),
                showDescriptionOnlyIfSelected = true,
            )
            entry(
                key = ExtendedActionsPlacement.OVERLAY_APP_UI,
                label = stringRes(R.string.enum__extended_actions_placement__overlay_app_ui),
                description = stringRes(R.string.enum__extended_actions_placement__overlay_app_ui__description),
                showDescriptionOnlyIfSelected = true,
            )
        }
    },
    HapticVibrationMode::class to DEFAULT to {
        listPrefEntries {
            entry(
                key = HapticVibrationMode.USE_VIBRATOR_DIRECTLY,
                label = stringRes(R.string.enum__haptic_vibration_mode__use_vibrator_directly),
                description = stringRes(R.string.enum__haptic_vibration_mode__use_vibrator_directly__description),
                showDescriptionOnlyIfSelected = true,
            )
            entry(
                key = HapticVibrationMode.USE_HAPTIC_FEEDBACK_INTERFACE,
                label = stringRes(R.string.enum__haptic_vibration_mode__use_haptic_feedback_interface),
                description = stringRes(R.string.enum__haptic_vibration_mode__use_haptic_feedback_interface__description),
                showDescriptionOnlyIfSelected = true,
            )
        }
    },
    KeyHintMode::class to DEFAULT to {
        listPrefEntries {
            entry(
                key = KeyHintMode.ACCENT_PRIORITY,
                label = stringRes(R.string.enum__key_hint_mode__accent_priority),
                description = stringRes(R.string.enum__key_hint_mode__accent_priority__description),
                showDescriptionOnlyIfSelected = true,
            )
            entry(
                key = KeyHintMode.HINT_PRIORITY,
                label = stringRes(R.string.enum__key_hint_mode__hint_priority),
                description = stringRes(R.string.enum__key_hint_mode__hint_priority__description),
                showDescriptionOnlyIfSelected = true,
            )
            entry(
                key = KeyHintMode.SMART_PRIORITY,
                label = stringRes(R.string.enum__key_hint_mode__smart_priority),
                description = stringRes(R.string.enum__key_hint_mode__smart_priority__description),
                showDescriptionOnlyIfSelected = true,
            )
        }
    },
    IncognitoDisplayMode::class to DEFAULT to {
        listPrefEntries {
            entry(
                key = IncognitoDisplayMode.REPLACE_SHARED_ACTIONS_TOGGLE,
                label = stringRes(id = R.string.enum__incognito_display_mode__replace_shared_actions_toggle),
            )
            entry(
                key = IncognitoDisplayMode.DISPLAY_BEHIND_KEYBOARD,
                label = stringRes(id = R.string.enum__incognito_display_mode__display_behind_keyboard),
            )
        }
    },
    IncognitoMode::class to DEFAULT to {
        listPrefEntries {
            entry(
                key = IncognitoMode.FORCE_OFF,
                label = stringRes(R.string.enum__incognito_mode__force_off),
                description = stringRes(R.string.enum__incognito_mode__force_off__description),
                showDescriptionOnlyIfSelected = true,
            )
            entry(
                key = IncognitoMode.DYNAMIC_ON_OFF,
                label = stringRes(R.string.enum__incognito_mode__dynamic_on_off),
                description = stringRes(R.string.enum__incognito_mode__dynamic_on_off__description),
                showDescriptionOnlyIfSelected = true,
            )
            entry(
                key = IncognitoMode.FORCE_ON,
                label = stringRes(R.string.enum__incognito_mode__force_on),
                description = stringRes(R.string.enum__incognito_mode__force_on__description),
                showDescriptionOnlyIfSelected = true,
            )
        }
    },
    InputFeedbackActivationMode::class to "audio" to {
        listPrefEntries {
            entry(
                key = InputFeedbackActivationMode.RESPECT_SYSTEM_SETTINGS,
                label = stringRes(R.string.enum__input_feedback_activation_mode__audio_respect_system_settings),
            )
            entry(
                key = InputFeedbackActivationMode.IGNORE_SYSTEM_SETTINGS,
                label = stringRes(R.string.enum__input_feedback_activation_mode__audio_ignore_system_settings),
            )
        }
    },
    InputFeedbackActivationMode::class to "haptic" to {
        listPrefEntries {
            entry(
                key = InputFeedbackActivationMode.RESPECT_SYSTEM_SETTINGS,
                label = stringRes(R.string.enum__input_feedback_activation_mode__haptic_respect_system_settings),
            )
            entry(
                key = InputFeedbackActivationMode.IGNORE_SYSTEM_SETTINGS,
                label = stringRes(R.string.enum__input_feedback_activation_mode__haptic_ignore_system_settings),
            )
        }
    },
    DrsSoundStyle::class to DEFAULT to {
        listPrefEntries {
            entry(
                key = DrsSoundStyle.SYSTEM,
                label = stringRes(R.string.enum__drs_sound_style__system),
                description = stringRes(R.string.enum__drs_sound_style__system__description),
            )
            entry(
                key = DrsSoundStyle.CLASSIC,
                label = stringRes(R.string.enum__drs_sound_style__classic),
                description = stringRes(R.string.enum__drs_sound_style__classic__description),
            )
            entry(
                key = DrsSoundStyle.MODERN,
                label = stringRes(R.string.enum__drs_sound_style__modern),
                description = stringRes(R.string.enum__drs_sound_style__modern__description),
            )
            entry(
                key = DrsSoundStyle.SOFT,
                label = stringRes(R.string.enum__drs_sound_style__soft),
                description = stringRes(R.string.enum__drs_sound_style__soft__description),
            )
            entry(
                key = DrsSoundStyle.CLICK,
                label = stringRes(R.string.enum__drs_sound_style__click),
                description = stringRes(R.string.enum__drs_sound_style__click__description),
            )
            entry(
                key = DrsSoundStyle.TYPER,
                label = stringRes(R.string.enum__drs_sound_style__typer),
                description = stringRes(R.string.enum__drs_sound_style__typer__description),
            )
            entry(
                key = DrsSoundStyle.POP,
                label = stringRes(R.string.enum__drs_sound_style__pop),
                description = stringRes(R.string.enum__drs_sound_style__pop__description),
            )
        }
    },
    InputShiftState::class to DEFAULT to {
        listPrefEntries {
            entry(
                key = InputShiftState.UNSHIFTED,
                label = stringRes(R.string.enum__input_shift_state__unshifted),
            )
            entry(
                key = InputShiftState.SHIFTED_MANUAL,
                label = stringRes(R.string.enum__input_shift_state__shifted_manual),
            )
            entry(
                key = InputShiftState.SHIFTED_AUTOMATIC,
                label = stringRes(R.string.enum__input_shift_state__shifted_automatic),
            )
            entry(
                key = InputShiftState.CAPS_LOCK,
                label = stringRes(R.string.enum__input_shift_state__caps_lock),
            )
        }
    },
    ImeWindowMode::class to DEFAULT to {
        listPrefEntries {
            entry(
                key = ImeWindowMode.FIXED,
                label = stringRes(R.string.enum__ime_window_mode__fixed),
            )
            entry(
                key = ImeWindowMode.FLOATING,
                label = stringRes(R.string.enum__ime_window_mode__floating),
            )
        }
    },
    KeyboardMode::class to DEFAULT to {
        listPrefEntries {
            entry(
                key = KeyboardMode.CHARACTERS,
                label = stringRes(R.string.enum__keyboard_mode__characters),
            )
            entry(
                key = KeyboardMode.SYMBOLS,
                label = stringRes(R.string.enum__keyboard_mode__symbols),
            )
            entry(
                key = KeyboardMode.SYMBOLS2,
                label = stringRes(R.string.enum__keyboard_mode__symbols2),
            )
            entry(
                key = KeyboardMode.NUMERIC,
                label = stringRes(R.string.enum__keyboard_mode__numeric),
            )
            entry(
                key = KeyboardMode.NUMERIC_ADVANCED,
                label = stringRes(R.string.enum__keyboard_mode__numeric_advanced),
            )
            entry(
                key = KeyboardMode.PHONE,
                label = stringRes(R.string.enum__keyboard_mode__phone),
            )
            entry(
                key = KeyboardMode.PHONE2,
                label = stringRes(R.string.enum__keyboard_mode__phone2),
            )
        }
    },
    LandscapeInputUiMode::class to DEFAULT to {
        listPrefEntries {
            entry(
                key = LandscapeInputUiMode.NEVER_SHOW,
                label = stringRes(R.string.enum__landscape_input_ui_mode__never_show),
            )
            entry(
                key = LandscapeInputUiMode.ALWAYS_SHOW,
                label = stringRes(R.string.enum__landscape_input_ui_mode__always_show),
            )
            entry(
                key = LandscapeInputUiMode.DYNAMICALLY_SHOW,
                label = stringRes(R.string.enum__landscape_input_ui_mode__dynamically_show),
            )
        }
    },
    SplitMode::class to DEFAULT to {
        listPrefEntries {
            entry(
                key = SplitMode.AUTO,
                label = stringRes(R.string.enum__split_mode__auto),
                description = stringRes(R.string.enum__split_mode__auto__description),
                showDescriptionOnlyIfSelected = true,
            )
            entry(
                key = SplitMode.ALWAYS,
                label = stringRes(R.string.enum__split_mode__always),
                description = stringRes(R.string.enum__split_mode__always__description),
                showDescriptionOnlyIfSelected = true,
            )
            entry(
                key = SplitMode.NEVER,
                label = stringRes(R.string.enum__split_mode__never),
                description = stringRes(R.string.enum__split_mode__never__description),
                showDescriptionOnlyIfSelected = true,
            )
        }
    },
    SmartbarLayout::class to DEFAULT to {
        listPrefEntries {
            entry(
                key = SmartbarLayout.SUGGESTIONS_ONLY,
                label = stringRes(R.string.enum__smartbar_layout__suggestions_only),
                description = stringRes(R.string.enum__smartbar_layout__suggestions_only__description),
            )
            entry(
                key = SmartbarLayout.ACTIONS_ONLY,
                label = stringRes(R.string.enum__smartbar_layout__actions_only),
                description = stringRes(R.string.enum__smartbar_layout__actions_only__description),
            )
            entry(
                key = SmartbarLayout.SUGGESTIONS_ACTIONS_SHARED,
                label = stringRes(R.string.enum__smartbar_layout__suggestions_action_shared),
                description = stringRes(R.string.enum__smartbar_layout__suggestions_action_shared__description),
            )
            entry(
                key = SmartbarLayout.SUGGESTIONS_ACTIONS_EXTENDED,
                label = stringRes(R.string.enum__smartbar_layout__suggestions_actions_extended),
                description = stringRes(R.string.enum__smartbar_layout__suggestions_actions_extended__description),
            )
        }
    },
    SnyggLevel::class to DEFAULT to {
        listPrefEntries {
            entry(
                key = SnyggLevel.BASIC,
                label = stringRes(R.string.enum__snygg_level__basic),
                description = stringRes(R.string.enum__snygg_level__basic__description),
                showDescriptionOnlyIfSelected = true,
            )
            entry(
                key = SnyggLevel.ADVANCED,
                label = stringRes(R.string.enum__snygg_level__advanced),
                description = stringRes(R.string.enum__snygg_level__advanced__description),
                showDescriptionOnlyIfSelected = true,
            )
            entry(
                key = SnyggLevel.DEVELOPER,
                label = stringRes(R.string.enum__snygg_level__developer),
                description = stringRes(R.string.enum__snygg_level__developer__description),
                showDescriptionOnlyIfSelected = true,
            )
        }
    },
    SpaceBarMode::class to DEFAULT to {
        listPrefEntries {
            entry(
                key = SpaceBarMode.NOTHING,
                label = stringRes(R.string.enum__space_bar_mode__nothing),
            )
            entry(
                key = SpaceBarMode.CURRENT_LANGUAGE,
                label = stringRes(R.string.enum__space_bar_mode__current_language),
            )
            entry(
                key = SpaceBarMode.SPACE_BAR_KEY,
                label = stringRes(R.string.enum__space_bar_mode__space_bar_key),
            )
        }
    },
    SpellingLanguageMode::class to DEFAULT to {
        listPrefEntries {
            entry(
                key = SpellingLanguageMode.USE_SYSTEM_LANGUAGES,
                label = stringRes(R.string.enum__spelling_language_mode__use_system_languages),
            )
            entry(
                key = SpellingLanguageMode.USE_KEYBOARD_SUBTYPES,
                label = stringRes(R.string.enum__spelling_language_mode__use_keyboard_subtypes),
            )
        }
    },
    SwipeAction::class to "general" to {
        listPrefEntries {
            entry(
                key = SwipeAction.NO_ACTION,
                label = stringRes(R.string.enum__swipe_action__no_action),
            )
            entry(
                key = SwipeAction.CYCLE_TO_PREVIOUS_KEYBOARD_MODE,
                label = stringRes(R.string.enum__swipe_action__cycle_to_previous_keyboard_mode),
            )
            entry(
                key = SwipeAction.CYCLE_TO_NEXT_KEYBOARD_MODE,
                label = stringRes(R.string.enum__swipe_action__cycle_to_next_keyboard_mode),
            )
            entry(
                key = SwipeAction.DELETE_WORD,
                label = stringRes(R.string.enum__swipe_action__delete_word),
            )
            entry(
                key = SwipeAction.HIDE_KEYBOARD,
                label = stringRes(R.string.enum__swipe_action__hide_keyboard),
            )
            entry(
                key = SwipeAction.INSERT_SPACE,
                label = stringRes(R.string.enum__swipe_action__insert_space),
            )
            entry(
                key = SwipeAction.MOVE_CURSOR_UP,
                label = stringRes(R.string.enum__swipe_action__move_cursor_up),
            )
            entry(
                key = SwipeAction.MOVE_CURSOR_DOWN,
                label = stringRes(R.string.enum__swipe_action__move_cursor_down),
            )
            entry(
                key = SwipeAction.MOVE_CURSOR_LEFT,
                label = stringRes(R.string.enum__swipe_action__move_cursor_left),
            )
            entry(
                key = SwipeAction.MOVE_CURSOR_RIGHT,
                label = stringRes(R.string.enum__swipe_action__move_cursor_right),
            )
            entry(
                key = SwipeAction.MOVE_CURSOR_START_OF_LINE,
                label = stringRes(R.string.enum__swipe_action__move_cursor_start_of_line),
            )
            entry(
                key = SwipeAction.MOVE_CURSOR_END_OF_LINE,
                label = stringRes(R.string.enum__swipe_action__move_cursor_end_of_line),
            )
            entry(
                key = SwipeAction.MOVE_CURSOR_START_OF_PAGE,
                label = stringRes(R.string.enum__swipe_action__move_cursor_start_of_page),
            )
            entry(
                key = SwipeAction.MOVE_CURSOR_END_OF_PAGE,
                label = stringRes(R.string.enum__swipe_action__move_cursor_end_of_page),
            )
            entry(
                key = SwipeAction.SHIFT,
                label = stringRes(R.string.enum__swipe_action__shift),
            )
            entry(
                key = SwipeAction.REDO,
                label = stringRes(R.string.enum__swipe_action__redo),
            )
            entry(
                key = SwipeAction.UNDO,
                label = stringRes(R.string.enum__swipe_action__undo),
            )
            entry(
                key = SwipeAction.SWITCH_TO_CLIPBOARD_CONTEXT,
                label = stringRes(R.string.enum__swipe_action__switch_to_clipboard_context),
            )
            entry(
                key = SwipeAction.SWITCH_TO_MEDIA_CONTEXT,
                label = stringRes(R.string.enum__swipe_action__switch_to_media_context)
            )
            entry(
                key = SwipeAction.SHOW_INPUT_METHOD_PICKER,
                label = stringRes(R.string.enum__swipe_action__show_input_method_picker),
            )
            entry(
                key = SwipeAction.SHOW_SUBTYPE_PICKER,
                // DRS v1.19.0: was a hardcoded "Show subtype picker"
                // English literal shown to users of every locale.
                label = stringRes(R.string.enum__swipe_action__show_subtype_picker)
            )
            entry(
                key = SwipeAction.SWITCH_TO_PREV_SUBTYPE,
                label = stringRes(R.string.enum__swipe_action__switch_to_prev_subtype),
            )
            entry(
                key = SwipeAction.SWITCH_TO_NEXT_SUBTYPE,
                label = stringRes(R.string.enum__swipe_action__switch_to_next_subtype),
            )
            entry(
                key = SwipeAction.SWITCH_TO_PREV_KEYBOARD,
                label = stringRes(R.string.enum__swipe_action__switch_to_prev_keyboard),
            )
            entry(
                key = SwipeAction.TOGGLE_SMARTBAR_VISIBILITY,
                label = stringRes(R.string.enum__swipe_action__toggle_smartbar_visibility),
            )
            entry(
                key = SwipeAction.TOGGLE_COMPACT_LAYOUT,
                label = stringRes(R.string.enum__swipe_action__toggle_compact_layout),
            )
        }
    },
    SwipeAction::class to "deleteSwipe" to {
        listPrefEntries {
            entry(
                key = SwipeAction.NO_ACTION,
                label = stringRes(R.string.enum__swipe_action__no_action),
            )
            entry(
                key = SwipeAction.DELETE_CHARACTERS_PRECISELY,
                label = stringRes(R.string.enum__swipe_action__delete_characters_precisely),
            )
            entry(
                key = SwipeAction.DELETE_WORD,
                label = stringRes(R.string.enum__swipe_action__delete_word),
            )
            entry(
                key = SwipeAction.DELETE_WORDS_PRECISELY,
                label = stringRes(R.string.enum__swipe_action__delete_words_precisely),
            )
            entry(
                key = SwipeAction.SELECT_CHARACTERS_PRECISELY,
                label = stringRes(R.string.enum__swipe_action__select_characters_precisely),
            )
            entry(
                key = SwipeAction.SELECT_WORDS_PRECISELY,
                label = stringRes(R.string.enum__swipe_action__select_words_precisely),
            )
        }
    },
    SwipeAction::class to "deleteLongPress" to {
        listPrefEntries {
            entry(
                key = SwipeAction.DELETE_CHARACTER,
                label = stringRes(R.string.enum__swipe_action__delete_character),
            )
            entry(
                key = SwipeAction.DELETE_WORD,
                label = stringRes(R.string.enum__swipe_action__delete_word),
            )
        }
    },
    ThemeMode::class to DEFAULT to {
        listPrefEntries {
            entry(
                key = ThemeMode.ALWAYS_DAY,
                label = stringRes(R.string.enum__theme_mode__always_day),
            )
            entry(
                key = ThemeMode.ALWAYS_NIGHT,
                label = stringRes(R.string.enum__theme_mode__always_night),
            )
            entry(
                key = ThemeMode.FOLLOW_SYSTEM,
                label = stringRes(R.string.enum__theme_mode__follow_system),
            )
            entry(
                key = ThemeMode.FOLLOW_TIME,
                label = stringRes(R.string.enum__theme_mode__follow_time),
            )
        }
    },
    UtilityKeyAction::class to DEFAULT to {
        listPrefEntries {
            entry(
                key = UtilityKeyAction.SWITCH_TO_EMOJIS,
                label = stringRes(R.string.enum__utility_key_action__switch_to_emojis),
            )
            entry(
                key = UtilityKeyAction.SWITCH_LANGUAGE,
                label = stringRes(R.string.enum__utility_key_action__switch_language),
            )
            entry(
                key = UtilityKeyAction.SWITCH_KEYBOARD_APP,
                label = stringRes(R.string.enum__utility_key_action__switch_keyboard_app),
            )
            entry(
                key = UtilityKeyAction.DYNAMIC_SWITCH_LANGUAGE_EMOJIS,
                label = stringRes(R.string.enum__utility_key_action__dynamic_switch_language_emojis),
            )
        }
    },
    UpdateCheckMode::class to DEFAULT to {
        listPrefEntries {
            entry(
                key = UpdateCheckMode.DAILY,
                label = stringRes(R.string.enum__update_check_mode__daily),
            )
            entry(
                key = UpdateCheckMode.WEEKLY,
                label = stringRes(R.string.enum__update_check_mode__weekly),
            )
            entry(
                key = UpdateCheckMode.MANUAL,
                label = stringRes(R.string.enum__update_check_mode__manual),
            )
            entry(
                key = UpdateCheckMode.OFF,
                label = stringRes(R.string.enum__update_check_mode__off),
            )
        }
    },
    ClipEditorCharLimit::class to DEFAULT to {
        listPrefEntries {
            entry(
                key = ClipEditorCharLimit.FIFTY_K,
                label = stringRes(R.string.enum__clip_editor_char_limit__fifty_k),
            )
            entry(
                key = ClipEditorCharLimit.HUNDRED_K,
                label = stringRes(R.string.enum__clip_editor_char_limit__hundred_k),
            )
            entry(
                key = ClipEditorCharLimit.TWO_FIFTY_K,
                label = stringRes(R.string.enum__clip_editor_char_limit__two_fifty_k),
            )
            entry(
                key = ClipEditorCharLimit.FIVE_HUNDRED_K,
                label = stringRes(R.string.enum__clip_editor_char_limit__five_hundred_k),
            )
        }
    },
    ClipHistorySort::class to DEFAULT to {
        listPrefEntries {
            entry(
                key = ClipHistorySort.NEWEST,
                label = stringRes(R.string.enum__clip_history_sort__newest),
            )
            entry(
                key = ClipHistorySort.OLDEST,
                label = stringRes(R.string.enum__clip_history_sort__oldest),
            )
            entry(
                key = ClipHistorySort.LONGEST,
                label = stringRes(R.string.enum__clip_history_sort__longest),
            )
            entry(
                key = ClipHistorySort.SHORTEST,
                label = stringRes(R.string.enum__clip_history_sort__shortest),
            )
        }
    },
    ClipFontOption::class to DEFAULT to {
        listPrefEntries {
            entry(
                key = ClipFontOption.DEFAULT,
                label = stringRes(R.string.clip__font_default),
            )
            entry(
                key = ClipFontOption.SANS_SERIF,
                label = stringRes(R.string.clip__font_sans),
            )
            entry(
                key = ClipFontOption.SERIF,
                label = stringRes(R.string.clip__font_serif),
            )
            entry(
                key = ClipFontOption.MONOSPACE,
                label = stringRes(R.string.clip__font_mono),
            )
            entry(
                key = ClipFontOption.CURSIVE,
                label = stringRes(R.string.clip__font_cursive),
            )
        }
    },
    ClipFontSizeOption::class to DEFAULT to {
        listPrefEntries {
            entry(
                key = ClipFontSizeOption.SMALL,
                label = ClipFontSizeOption.SMALL.spValue.toString(),
            )
            entry(
                key = ClipFontSizeOption.NORMAL,
                label = ClipFontSizeOption.NORMAL.spValue.toString(),
            )
            entry(
                key = ClipFontSizeOption.LARGE,
                label = ClipFontSizeOption.LARGE.spValue.toString(),
            )
            entry(
                key = ClipFontSizeOption.EXTRA_LARGE,
                label = ClipFontSizeOption.EXTRA_LARGE.spValue.toString(),
            )
        }
    },
    // DRS v1.14.0 — the comprehensive settings list: the popup edit
    // window surface, size, and dimming enums join the registry.
    ClipEditorRoute::class to DEFAULT to {
        listPrefEntries {
            entry(
                key = ClipEditorRoute.POPUP_WINDOW,
                label = stringRes(R.string.enum__clip_edit_route__popup),
            )
            entry(
                key = ClipEditorRoute.IN_PANEL,
                label = stringRes(R.string.enum__clip_edit_route__panel),
            )
        }
    },
    ClipEditorPopupSize::class to DEFAULT to {
        listPrefEntries {
            entry(
                key = ClipEditorPopupSize.COMPACT,
                label = stringRes(R.string.enum__clip_popup_size__compact),
            )
            entry(
                key = ClipEditorPopupSize.NORMAL,
                label = stringRes(R.string.enum__clip_popup_size__normal),
            )
            entry(
                key = ClipEditorPopupSize.NEARLY_FULL,
                label = stringRes(R.string.enum__clip_popup_size__nearly_full),
            )
        }
    },
    ClipEditorScrim::class to DEFAULT to {
        listPrefEntries {
            entry(
                key = ClipEditorScrim.NONE,
                label = stringRes(R.string.enum__clip_popup_scrim__none),
            )
            entry(
                key = ClipEditorScrim.LIGHT,
                label = stringRes(R.string.enum__clip_popup_scrim__light),
            )
            entry(
                key = ClipEditorScrim.NORMAL,
                label = stringRes(R.string.enum__clip_popup_scrim__normal),
            )
            entry(
                key = ClipEditorScrim.STRONG,
                label = stringRes(R.string.enum__clip_popup_scrim__strong),
            )
        }
    },
    // DRS v1.25.0 — the second voice gate: which recognizer may listen
    // (auto / strictly on-device / classic standard), honest labels so
    // the privacy promise in each choice is visible before it is made.
    VoiceRecognizerMode::class to DEFAULT to {
        listPrefEntries {
            entry(
                key = VoiceRecognizerMode.AUTO,
                label = stringRes(R.string.enum__voice_recognizer_mode__auto),
            )
            entry(
                key = VoiceRecognizerMode.ON_DEVICE_ONLY,
                label = stringRes(R.string.enum__voice_recognizer_mode__on_device_only),
            )
            entry(
                key = VoiceRecognizerMode.STANDARD,
                label = stringRes(R.string.enum__voice_recognizer_mode__standard),
            )
        }
    },
)

@Composable
fun <V : Any> enumDisplayEntriesOf(
    enumClass: KClass<V>,
    variant: String = DEFAULT,
): List<ListPreferenceEntry<V>> {
    @Suppress("UNCHECKED_CAST")
    return ENUM_DISPLAY_ENTRIES[enumClass to variant]?.invoke()
        as List<ListPreferenceEntry<V>>
}
