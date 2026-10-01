/*
 * Copyright (C) 2022-2025 The DRS Smart Keyboard Project
 */

package com.drs.smartkeyboard.ime.smartbar.quickaction

import android.content.Context
import androidx.compose.runtime.Composable
import com.drs.smartkeyboard.R
import com.drs.smartkeyboard.editorInstance
import com.drs.smartkeyboard.drs.DrsTextTool
import com.drs.smartkeyboard.drs.ui.textToolDescRes
import com.drs.smartkeyboard.drs.ui.textToolTitleRes
import com.drs.smartkeyboard.ime.keyboard.ComputingEvaluator
import com.drs.smartkeyboard.ime.keyboard.KeyData
import com.drs.smartkeyboard.ime.text.key.KeyCode
import com.drs.smartkeyboard.ime.text.keyboard.TextKeyData
import com.drs.smartkeyboard.keyboardManager
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.drs.lib.compose.stringRes

@Serializable
sealed class QuickAction {
    open fun onPointerDown(context: Context) = Unit

    open fun onPointerUp(context: Context) = Unit

    open fun onPointerCancel(context: Context) = Unit

    @Serializable
    @SerialName("insert_key")
    data class InsertKey(val data: KeyData) : QuickAction() {
        override fun onPointerDown(context: Context) {
            val keyboardManager by context.keyboardManager()
            keyboardManager.inputEventDispatcher.sendDown(data)
        }

        override fun onPointerUp(context: Context) {
            val keyboardManager by context.keyboardManager()
            keyboardManager.inputEventDispatcher.sendUp(data)
            if (!keyboardManager.inputEventDispatcher.isRepeatable(data) &&
                data.code != KeyCode.TOGGLE_ACTIONS_OVERFLOW && data.code != KeyCode.CLIPBOARD_SELECT_ALL) {
                keyboardManager.activeState.isActionsOverflowVisible = false
            }
        }

        override fun onPointerCancel(context: Context) {
            val keyboardManager by context.keyboardManager()
            keyboardManager.inputEventDispatcher.sendCancel(data)
        }
    }

    @Serializable
    @SerialName("insert_text")
    data class InsertText(val data: String) : QuickAction() {
        override fun onPointerUp(context: Context) {
            val editorInstance by context.editorInstance()
            editorInstance.commitText(data)
        }
    }
}

fun QuickAction.keyData(): KeyData {
    return if (this is QuickAction.InsertKey) data else TextKeyData.UNSPECIFIED
}

/**
 * DRS v1.0.5: the KeyCodes that count as "smart tools" for the anonymous
 * most-used-tools statistics (and hence the most-used section in the tools
 * overflow). Deliberately excludes plain editing keys (characters, shift,
 * ctrl modifiers, delete...) so only actual tool button presses count.
 */
val SmartToolCodes = setOf(
    KeyCode.ARROW_LEFT,
    KeyCode.ARROW_RIGHT,
    KeyCode.ARROW_UP,
    KeyCode.ARROW_DOWN,
    KeyCode.MOVE_START_OF_PAGE,
    KeyCode.MOVE_END_OF_PAGE,
    KeyCode.MOVE_START_OF_LINE,
    KeyCode.MOVE_END_OF_LINE,
    KeyCode.MOVE_WORD_LEFT,
    KeyCode.MOVE_WORD_RIGHT,
    KeyCode.CLIPBOARD_COPY,
    KeyCode.CLIPBOARD_CUT,
    KeyCode.CLIPBOARD_PASTE,
    KeyCode.CLIPBOARD_SELECT,
    KeyCode.CLIPBOARD_SELECT_ALL,
    KeyCode.CLIPBOARD_CLEAR_PRIMARY_CLIP,
    KeyCode.CLIPBOARD_SHARE,
    KeyCode.UNDO,
    KeyCode.REDO,
    KeyCode.LANGUAGE_SWITCH,
    KeyCode.IME_UI_MODE_MEDIA,
    KeyCode.IME_UI_MODE_CLIPBOARD,
    KeyCode.IME_UI_MODE_TEXT_TOOLS,
    // DRS v1.0.6: every technical text tool counts as a tool press.
    KeyCode.TEXT_TOOL_UPPERCASE,
    KeyCode.TEXT_TOOL_LOWERCASE,
    KeyCode.TEXT_TOOL_TITLE_CASE,
    KeyCode.TEXT_TOOL_SENTENCE_CASE,
    KeyCode.TEXT_TOOL_TRIM_SPACES,
    KeyCode.TEXT_TOOL_TRIM_LINE_EDGES,
    KeyCode.TEXT_TOOL_REMOVE_EMPTY_LINES,
    KeyCode.TEXT_TOOL_REMOVE_LINE_BREAKS,
    KeyCode.TEXT_TOOL_SORT_LINES,
    KeyCode.TEXT_TOOL_REMOVE_DUPLICATE_LINES,
    KeyCode.TEXT_TOOL_REMOVE_DIACRITICS,
    KeyCode.TEXT_TOOL_NORMALIZE_PUNCTUATION,
    KeyCode.TEXT_TOOL_CLEAN_TEXT,
    KeyCode.TEXT_TOOL_COUNT,
    KeyCode.TEXT_TOOL_DELETE_LINE,
    KeyCode.TEXT_TOOL_DELETE_TO_LINE_START,
    KeyCode.TEXT_TOOL_DELETE_TO_LINE_END,
    KeyCode.VOICE_INPUT,
    KeyCode.TOGGLE_FLOATING_WINDOW,
    KeyCode.TOGGLE_COMPACT_LAYOUT,
    KeyCode.TOGGLE_RESIZE_MODE,
    KeyCode.TOGGLE_INCOGNITO_MODE,
    KeyCode.TOGGLE_AUTOCORRECT,
    KeyCode.SETTINGS,
    KeyCode.TOGGLE_ACTIONS_OVERFLOW,
    KeyCode.IME_HIDE_UI,
    // DRS v1.0.8: the new unified-strip tools count as tool presses too.
    KeyCode.THEME_CYCLE,
    KeyCode.INSERT_DATE_TIME,
    // DRS v1.1.0: new catalogue tools + line text ops count as well.
    KeyCode.TOGGLE_NUMBER_ROW,
    KeyCode.TEXT_TOOL_NUMBER_LINES,
    KeyCode.TEXT_TOOL_REVERSE_LINES,
    // DRS v1.2.0 integration fix: WRAP_QUOTES was missing here, so its
    // usage never surfaced in the most-used tools stats.
    KeyCode.TEXT_TOOL_WRAP_QUOTES,
    // DRS v1.3.0: digit conversion, blank-line collapsing, tatweel removal.
    KeyCode.TEXT_TOOL_TO_ARABIC_DIGITS,
    KeyCode.TEXT_TOOL_TO_WESTERN_DIGITS,
    KeyCode.TEXT_TOOL_COLLAPSE_EMPTY_LINES,
    KeyCode.TEXT_TOOL_REMOVE_TATWEEL,
    // DRS v1.4.0: descending sort + Arabic letter normalization, plus the
    // smartbar-visibility toggle whose usage never surfaced before.
    KeyCode.TEXT_TOOL_SORT_LINES_DESC,
    KeyCode.TEXT_TOOL_NORMALIZE_ARABIC,
    KeyCode.TOGGLE_SMARTBAR_VISIBILITY,
    // DRS v1.5.0: the eight new text tools count as tool presses, and the
    // clipboard-history / next-language catalogue tools join the set.
    KeyCode.TEXT_TOOL_TOGGLE_CASE,
    KeyCode.TEXT_TOOL_TO_ARABIC_PUNCTUATION,
    KeyCode.TEXT_TOOL_REMOVE_ZERO_WIDTH,
    KeyCode.TEXT_TOOL_TABS_TO_SPACES,
    KeyCode.TEXT_TOOL_SORT_LINES_BY_LENGTH,
    KeyCode.TEXT_TOOL_REMOVE_DUPLICATE_WORDS,
    KeyCode.TEXT_TOOL_WRAP_PARENS,
    KeyCode.TEXT_TOOL_SENTENCE_PER_LINE,
    KeyCode.CLIPBOARD_CLEAR_HISTORY,
    KeyCode.IME_NEXT_SUBTYPE,
    // DRS v1.6.0: the five new catalogue tools count as tool presses too.
    KeyCode.CLIPBOARD_CLEAR_FULL_HISTORY,
    KeyCode.IME_PREV_SUBTYPE,
    KeyCode.COMPACT_LAYOUT_TO_LEFT,
    KeyCode.COMPACT_LAYOUT_TO_RIGHT,
    KeyCode.SYSTEM_NEXT_INPUT_METHOD,
    // DRS v1.6.0: the four new text tools count as tool presses.
    KeyCode.TEXT_TOOL_SPACES_TO_TABS,
    KeyCode.TEXT_TOOL_TO_WESTERN_PUNCTUATION,
    KeyCode.TEXT_TOOL_TRIM_BLANK_EDGES,
    KeyCode.TEXT_TOOL_REVERSE_WORDS,
    // DRS v1.7.0: the subtype picker (the LANGUAGE catalogue tool since
    // v1.5.0 never counted in the stats), the actions-editor toggle (its
    // tiles would have rendered the invalid-fatal placeholder) and the
    // active-clip pin toggle all count as tool presses now.
    KeyCode.SHOW_SUBTYPE_PICKER,
    KeyCode.TOGGLE_ACTIONS_EDITOR,
    KeyCode.CLIPBOARD_PIN_ACTIVE,
    // DRS v1.7.0: the seven new text tools count as tool presses too.
    KeyCode.TEXT_TOOL_NORMALIZE_ARABIC_FORMS,
    KeyCode.TEXT_TOOL_SPLIT_TO_LINES,
    KeyCode.TEXT_TOOL_JOIN_LINES,
    KeyCode.TEXT_TOOL_REMOVE_ALL_SPACES,
    KeyCode.TEXT_TOOL_STRIP_EMOJI,
    KeyCode.TEXT_TOOL_URL_ENCODE,
    KeyCode.TEXT_TOOL_URL_DECODE,
    // DRS v1.15.0: the three smart panels count as tool presses too.
    KeyCode.IME_UI_MODE_DIACRITICS,
    KeyCode.IME_UI_MODE_SMART_SYMBOLS,
    KeyCode.IME_UI_MODE_ARABIC_LETTERS,
    // DRS v1.19.0: the split/merge keyboard toggles count as tool presses,
    // and the two v1.8.0 text tools that were missed by every stats register
    // since their introduction (SEPARATE_DIGIT_LETTERS / REMOVE_PUNCTUATION)
    // finally surface in the most-used tiles.
    KeyCode.SPLIT_LAYOUT,
    KeyCode.MERGE_LAYOUT,
    KeyCode.TEXT_TOOL_SEPARATE_DIGIT_LETTERS,
    KeyCode.TEXT_TOOL_REMOVE_PUNCTUATION,
)

@Composable
fun QuickAction.computeDisplayName(evaluator: ComputingEvaluator): String {
    return when (this) {
        is QuickAction.InsertKey -> stringRes(when (data.code) {
            KeyCode.ARROW_UP -> R.string.quick_action__arrow_up
            KeyCode.ARROW_DOWN -> R.string.quick_action__arrow_down
            KeyCode.ARROW_LEFT -> R.string.quick_action__arrow_left
            KeyCode.ARROW_RIGHT -> R.string.quick_action__arrow_right
            KeyCode.CLIPBOARD_CLEAR_PRIMARY_CLIP -> R.string.quick_action__clipboard_clear_primary_clip
            KeyCode.CLIPBOARD_COPY -> R.string.quick_action__clipboard_copy
            KeyCode.CLIPBOARD_CUT -> R.string.quick_action__clipboard_cut
            KeyCode.CLIPBOARD_PASTE -> R.string.quick_action__clipboard_paste
            KeyCode.CLIPBOARD_SELECT_ALL -> R.string.quick_action__clipboard_select_all
            KeyCode.CLIPBOARD_SHARE -> R.string.quick_action__clipboard_share
            KeyCode.MOVE_WORD_LEFT -> R.string.quick_action__move_word_left
            KeyCode.MOVE_WORD_RIGHT -> R.string.quick_action__move_word_right
            KeyCode.FORWARD_DELETE -> R.string.quick_action__forward_delete
            KeyCode.IME_UI_MODE_CLIPBOARD -> R.string.quick_action__ime_ui_mode_clipboard
            KeyCode.IME_UI_MODE_MEDIA -> R.string.quick_action__ime_ui_mode_media
            KeyCode.IME_UI_MODE_TEXT_TOOLS -> R.string.quick_action__ime_ui_mode_text_tools
            KeyCode.LANGUAGE_SWITCH -> R.string.quick_action__language_switch
            KeyCode.SETTINGS -> R.string.quick_action__settings
            KeyCode.UNDO -> R.string.quick_action__undo
            KeyCode.REDO -> R.string.quick_action__redo
            KeyCode.TOGGLE_ACTIONS_OVERFLOW -> R.string.quick_action__toggle_actions_overflow
            KeyCode.TOGGLE_INCOGNITO_MODE -> R.string.quick_action__toggle_incognito_mode
            KeyCode.TOGGLE_AUTOCORRECT -> R.string.quick_action__toggle_autocorrect
            KeyCode.VOICE_INPUT -> R.string.quick_action__voice_input
            KeyCode.IME_HIDE_UI -> R.string.quick_action__ime_hide_ui
            KeyCode.TOGGLE_FLOATING_WINDOW -> R.string.quick_action__floating_window_mode
            // TODO: In the future this will be merged into the resize keyboard panel, for now it is a separate action
            KeyCode.TOGGLE_COMPACT_LAYOUT -> R.string.quick_action__one_handed_mode
            KeyCode.TOGGLE_RESIZE_MODE -> R.string.quick_action__resize_mode
            // DRS v1.1.0: unified-strip tools that can surface as most-used tiles.
            KeyCode.THEME_CYCLE -> R.string.quick_action__theme_cycle
            KeyCode.INSERT_DATE_TIME -> R.string.quick_action__insert_date_time
            KeyCode.TOGGLE_NUMBER_ROW -> R.string.quick_action__toggle_number_row
            // DRS v1.5.0: remaining smart-tool codes that used to fall
            // through to the invalid-fatal placeholder when they surfaced
            // as most-used tiles.
            KeyCode.MOVE_START_OF_PAGE -> R.string.quick_action__move_start_of_page
            KeyCode.MOVE_END_OF_PAGE -> R.string.quick_action__move_end_of_page
            KeyCode.MOVE_START_OF_LINE -> R.string.quick_action__move_start_of_line
            KeyCode.MOVE_END_OF_LINE -> R.string.quick_action__move_end_of_line
            KeyCode.CLIPBOARD_SELECT -> R.string.quick_action__clipboard_select
            KeyCode.TOGGLE_SMARTBAR_VISIBILITY -> R.string.quick_action__toggle_smartbar_visibility
            // DRS v1.6.0: the two remaining smart-tool codes that used to
            // fall through to the invalid-fatal placeholder when they
            // surfaced as most-used tiles, plus the five new catalogue
            // tools (which enter the most-used set via SmartToolCodes).
            KeyCode.CLIPBOARD_CLEAR_HISTORY -> R.string.quick_action__clipboard_clear_history
            KeyCode.IME_NEXT_SUBTYPE -> R.string.quick_action__ime_next_subtype
            KeyCode.CLIPBOARD_CLEAR_FULL_HISTORY -> R.string.quick_action__clipboard_clear_full_history
            KeyCode.IME_PREV_SUBTYPE -> R.string.quick_action__ime_prev_subtype
            KeyCode.COMPACT_LAYOUT_TO_LEFT -> R.string.quick_action__compact_layout_to_left
            KeyCode.COMPACT_LAYOUT_TO_RIGHT -> R.string.quick_action__compact_layout_to_right
            KeyCode.SYSTEM_NEXT_INPUT_METHOD -> R.string.quick_action__system_next_input_method
            // DRS v1.7.0: the subtype picker (LANGUAGE tool), the
            // actions-editor toggle and the active-clip pin toggle.
            KeyCode.SHOW_SUBTYPE_PICKER -> R.string.quick_action__show_subtype_picker
            KeyCode.TOGGLE_ACTIONS_EDITOR -> R.string.quick_action__toggle_actions_editor
            KeyCode.CLIPBOARD_PIN_ACTIVE -> R.string.quick_action__clipboard_pin_active
            // DRS v1.15.0: the three smart panels.
            KeyCode.IME_UI_MODE_DIACRITICS -> R.string.quick_action__ime_ui_mode_diacritics
            KeyCode.IME_UI_MODE_SMART_SYMBOLS -> R.string.quick_action__ime_ui_mode_smart_symbols
            KeyCode.IME_UI_MODE_ARABIC_LETTERS -> R.string.quick_action__ime_ui_mode_arabic_letters
            // DRS v1.19.0: the split/merge keyboard toggles.
            KeyCode.SPLIT_LAYOUT -> R.string.quick_action__split_layout
            KeyCode.MERGE_LAYOUT -> R.string.quick_action__merge_layout
            // DRS v1.5.0: text tools show their real panel titles instead
            // of the invalid-fatal placeholder.
            in DrsTextTool.CODE_RANGE -> DrsTextTool.fromCode(data.code)
                ?.let { textToolTitleRes(it) }
                ?: R.string.general__empty_string
            KeyCode.DRAG_MARKER -> if (evaluator.state.debugShowDragAndDropHelpers) {
                R.string.quick_action__drag_marker
            } else {
                R.string.general__empty_string
            }
            KeyCode.NOOP -> R.string.quick_action__noop
            else -> R.string.general__invalid_fatal
        })
        is QuickAction.InsertText -> data
    }
}

@Composable
fun QuickAction.computeTooltip(evaluator: ComputingEvaluator): String {
    return when (this) {
        is QuickAction.InsertKey -> stringRes(when (data.code) {
            KeyCode.ARROW_UP -> R.string.quick_action__arrow_up__tooltip
            KeyCode.ARROW_DOWN -> R.string.quick_action__arrow_down__tooltip
            KeyCode.ARROW_LEFT -> R.string.quick_action__arrow_left__tooltip
            KeyCode.ARROW_RIGHT -> R.string.quick_action__arrow_right__tooltip
            KeyCode.CLIPBOARD_CLEAR_PRIMARY_CLIP -> R.string.quick_action__clipboard_clear_primary_clip__tooltip
            KeyCode.CLIPBOARD_COPY -> R.string.quick_action__clipboard_copy__tooltip
            KeyCode.CLIPBOARD_CUT -> R.string.quick_action__clipboard_cut__tooltip
            KeyCode.CLIPBOARD_PASTE -> R.string.quick_action__clipboard_paste__tooltip
            KeyCode.CLIPBOARD_SELECT_ALL -> R.string.quick_action__clipboard_select_all__tooltip
            KeyCode.CLIPBOARD_SHARE -> R.string.quick_action__clipboard_share__tooltip
            KeyCode.MOVE_WORD_LEFT -> R.string.quick_action__move_word_left__tooltip
            KeyCode.MOVE_WORD_RIGHT -> R.string.quick_action__move_word_right__tooltip
            KeyCode.IME_UI_MODE_CLIPBOARD -> R.string.quick_action__ime_ui_mode_clipboard__tooltip
            KeyCode.IME_UI_MODE_MEDIA -> R.string.quick_action__ime_ui_mode_media__tooltip
            KeyCode.IME_UI_MODE_TEXT_TOOLS -> R.string.quick_action__ime_ui_mode_text_tools__tooltip
            KeyCode.LANGUAGE_SWITCH -> R.string.quick_action__language_switch__tooltip
            KeyCode.SETTINGS -> R.string.quick_action__settings__tooltip
            KeyCode.UNDO -> R.string.quick_action__undo__tooltip
            KeyCode.REDO -> R.string.quick_action__redo__tooltip
            KeyCode.TOGGLE_ACTIONS_OVERFLOW -> R.string.quick_action__toggle_actions_overflow__tooltip
            KeyCode.TOGGLE_INCOGNITO_MODE -> R.string.quick_action__toggle_incognito_mode__tooltip
            KeyCode.TOGGLE_AUTOCORRECT -> R.string.quick_action__toggle_autocorrect__tooltip
            KeyCode.VOICE_INPUT -> R.string.quick_action__voice_input__tooltip
            KeyCode.IME_HIDE_UI -> R.string.quick_action__ime_hide_ui__tooltip
            KeyCode.TOGGLE_FLOATING_WINDOW -> R.string.quick_action__floating_window_mode__tooltip
            // TODO: In the future this will be merged into the resize keyboard panel, for now it is a separate action
            KeyCode.TOGGLE_COMPACT_LAYOUT -> R.string.quick_action__one_handed_mode__tooltip
            KeyCode.TOGGLE_RESIZE_MODE -> R.string.quick_action__resize_mode__tooltip
            // DRS v1.1.0: unified-strip tools that can surface as most-used tiles.
            KeyCode.THEME_CYCLE -> R.string.quick_action__theme_cycle__tooltip
            KeyCode.INSERT_DATE_TIME -> R.string.quick_action__insert_date_time__tooltip
            KeyCode.TOGGLE_NUMBER_ROW -> R.string.quick_action__toggle_number_row__tooltip
            // DRS v1.5.0: tooltips for the codes that used to render the
            // invalid-fatal placeholder as most-used tiles.
            KeyCode.MOVE_START_OF_PAGE -> R.string.quick_action__move_start_of_page__tooltip
            KeyCode.MOVE_END_OF_PAGE -> R.string.quick_action__move_end_of_page__tooltip
            KeyCode.MOVE_START_OF_LINE -> R.string.quick_action__move_start_of_line__tooltip
            KeyCode.MOVE_END_OF_LINE -> R.string.quick_action__move_end_of_line__tooltip
            KeyCode.CLIPBOARD_SELECT -> R.string.quick_action__clipboard_select__tooltip
            KeyCode.TOGGLE_SMARTBAR_VISIBILITY -> R.string.quick_action__toggle_smartbar_visibility__tooltip
            // DRS v1.6.0: tooltips for the two previously-placeholdered
            // codes and the five new catalogue tools.
            KeyCode.CLIPBOARD_CLEAR_HISTORY -> R.string.quick_action__clipboard_clear_history__tooltip
            KeyCode.IME_NEXT_SUBTYPE -> R.string.quick_action__ime_next_subtype__tooltip
            KeyCode.CLIPBOARD_CLEAR_FULL_HISTORY -> R.string.quick_action__clipboard_clear_full_history__tooltip
            KeyCode.IME_PREV_SUBTYPE -> R.string.quick_action__ime_prev_subtype__tooltip
            KeyCode.COMPACT_LAYOUT_TO_LEFT -> R.string.quick_action__compact_layout_to_left__tooltip
            KeyCode.COMPACT_LAYOUT_TO_RIGHT -> R.string.quick_action__compact_layout_to_right__tooltip
            KeyCode.SYSTEM_NEXT_INPUT_METHOD -> R.string.quick_action__system_next_input_method__tooltip
            // DRS v1.7.0: the subtype picker (LANGUAGE tool), the
            // actions-editor toggle and the active-clip pin toggle.
            KeyCode.SHOW_SUBTYPE_PICKER -> R.string.quick_action__show_subtype_picker__tooltip
            KeyCode.TOGGLE_ACTIONS_EDITOR -> R.string.quick_action__toggle_actions_editor__tooltip
            KeyCode.CLIPBOARD_PIN_ACTIVE -> R.string.quick_action__clipboard_pin_active__tooltip
            // DRS v1.15.0: the three smart panels.
            KeyCode.IME_UI_MODE_DIACRITICS -> R.string.quick_action__ime_ui_mode_diacritics__tooltip
            KeyCode.IME_UI_MODE_SMART_SYMBOLS -> R.string.quick_action__ime_ui_mode_smart_symbols__tooltip
            KeyCode.IME_UI_MODE_ARABIC_LETTERS -> R.string.quick_action__ime_ui_mode_arabic_letters__tooltip
            // DRS v1.19.0: the split/merge keyboard toggles.
            KeyCode.SPLIT_LAYOUT -> R.string.quick_action__split_layout__tooltip
            KeyCode.MERGE_LAYOUT -> R.string.quick_action__merge_layout__tooltip
            // DRS v1.5.0: text tools show their real panel descriptions.
            in DrsTextTool.CODE_RANGE -> DrsTextTool.fromCode(data.code)
                ?.let { textToolDescRes(it) }
                ?: R.string.general__empty_string
            KeyCode.DRAG_MARKER -> if (evaluator.state.debugShowDragAndDropHelpers) {
                R.string.quick_action__drag_marker__tooltip
            } else {
                R.string.general__empty_string
            }
            KeyCode.NOOP -> R.string.quick_action__noop__tooltip
            else -> R.string.general__invalid_fatal
        })
        // DRS v1.19.0: the tooltip is localized now (was a hardcoded
        // English literal leaking into every locale).
        is QuickAction.InsertText -> stringRes(
            R.string.quick_action__insert_text_tooltip,
            "text" to data,
        )
    }
}
