/*
 * Copyright (C) 2021-2025 The DRS Smart Keyboard Project
 */

package com.drs.smartkeyboard.ime.text.key

object KeyCode {
    object Spec {
        const val CHARACTERS_MIN = 1
        const val CHARACTERS_MAX = 65535
        val CHARACTERS = CHARACTERS_MIN..CHARACTERS_MAX

        const val INTERNAL_MIN = -9999
        const val INTERNAL_MAX = -1
        val INTERNAL = INTERNAL_MIN..INTERNAL_MAX
    }

    const val UNSPECIFIED =                    0

    const val PHONE_WAIT =                    59 // ;
    const val PHONE_PAUSE =                   44 // ,

    const val SPACE =                         32
    const val ESCAPE =                        27
    const val ENTER =                         10
    const val TAB =                            9

    const val CTRL =                          -1
    const val CTRL_LOCK =                     -2
    const val ALT =                           -3
    const val ALT_LOCK =                      -4
    const val FN =                            -5
    const val FN_LOCK =                       -6
    const val DELETE =                        -7
    const val DELETE_WORD =                   -8
    const val FORWARD_DELETE =                -9
    const val FORWARD_DELETE_WORD =          -10
    const val SHIFT =                        -11
    const val CAPS_LOCK =                    -13

    const val ARROW_LEFT =                   -21
    const val ARROW_RIGHT =                  -22
    const val ARROW_UP =                     -23
    const val ARROW_DOWN =                   -24
    const val MOVE_START_OF_PAGE =           -25
    const val MOVE_END_OF_PAGE =             -26
    const val MOVE_START_OF_LINE =           -27
    const val MOVE_END_OF_LINE =             -28

    // DRS v1.0.5: word-by-word cursor navigation for the normal user.
    const val MOVE_WORD_LEFT =               -29
    const val MOVE_WORD_RIGHT =              -30

    const val CLIPBOARD_COPY =               -31
    const val CLIPBOARD_CUT =                -32
    const val CLIPBOARD_PASTE =              -33
    const val CLIPBOARD_SELECT =             -34
    const val CLIPBOARD_SELECT_ALL =         -35
    const val CLIPBOARD_CLEAR_HISTORY =      -36
    const val CLIPBOARD_CLEAR_FULL_HISTORY = -37
    const val CLIPBOARD_CLEAR_PRIMARY_CLIP = -38

    // DRS v1.0.5: share the selected text (or the whole field) via the
    // system share sheet — wired to the real editor content.
    const val CLIPBOARD_SHARE =              -39
    // DRS v1.7.0: pin/unpin the ACTIVE clipboard entry — the same real
    // pinClip/unpinClip path the clipboard panel long-press popup uses
    // (pinned entries survive clearHistory by contract).
    const val CLIPBOARD_PIN_ACTIVE =         -40

    const val TOGGLE_FLOATING_WINDOW =      -109
    const val TOGGLE_COMPACT_LAYOUT =       -110
    const val COMPACT_LAYOUT_TO_LEFT =      -111
    const val COMPACT_LAYOUT_TO_RIGHT =     -112
    const val SPLIT_LAYOUT =                -113
    const val MERGE_LAYOUT =                -114
    const val TOGGLE_RESIZE_MODE =          -115

    const val UNDO =                        -131
    const val REDO =                        -132

    const val VIEW_CHARACTERS =             -201
    const val VIEW_SYMBOLS =                -202
    const val VIEW_SYMBOLS2 =               -203
    const val VIEW_NUMERIC =                -204
    const val VIEW_NUMERIC_ADVANCED =       -205
    const val VIEW_PHONE =                  -206
    const val VIEW_PHONE2 =                 -207

    const val IME_UI_MODE_TEXT =            -211
    const val IME_UI_MODE_MEDIA =           -212
    const val IME_UI_MODE_CLIPBOARD =       -213

    // DRS v1.0.6: opens the technical text tools panel.
    const val IME_UI_MODE_TEXT_TOOLS =      -214

    // DRS v1.0.8: unified-strip tools. THEME_CYCLE switches the keyboard
    // theme of the currently effective day/night slot to the next installed
    // theme; INSERT_DATE_TIME commits the current date & time formatted
    // with the active subtype's locale. Both are handled in KeyboardManager.
    const val THEME_CYCLE =                 -215
    const val INSERT_DATE_TIME =            -216

    // DRS v1.1.0: unified-strip tools. TOGGLE_NUMBER_ROW flips the
    // keyboard__number_row pref; the LayoutManager recomputes the layout
    // through the existing pref collector. Handled in KeyboardManager.
    const val TOGGLE_NUMBER_ROW =           -217

    // DRS v1.15.0: the three smart panels (لوحة الحركات / لوحة الرموز
    // الذكية / لوحة الحروف الموسعة). Each is a real ImeUiMode handled in
    // KeyboardManager + hosted in ImeWindow, and each joins the unified
    // tool catalogue so it can be pinned among the capped 10 tasks.
    const val IME_UI_MODE_DIACRITICS =      -218
    const val IME_UI_MODE_SMART_SYMBOLS =   -219
    const val IME_UI_MODE_ARABIC_LETTERS =  -220

    const val SYSTEM_INPUT_METHOD_PICKER =  -221
    const val SYSTEM_PREV_INPUT_METHOD =    -222
    const val SYSTEM_NEXT_INPUT_METHOD =    -223
    const val IME_SUBTYPE_PICKER =          -224
    const val IME_PREV_SUBTYPE =            -225
    const val IME_NEXT_SUBTYPE =            -226
    const val LANGUAGE_SWITCH =             -227
    const val SHOW_SUBTYPE_PICKER =         -228

    const val IME_SHOW_UI =                 -231
    const val IME_HIDE_UI =                 -232
    const val VOICE_INPUT =                 -233

    const val TOGGLE_SMARTBAR_VISIBILITY =  -241
    const val TOGGLE_ACTIONS_OVERFLOW =     -242
    const val TOGGLE_ACTIONS_EDITOR =       -243
    const val TOGGLE_INCOGNITO_MODE =       -244
    const val TOGGLE_AUTOCORRECT =          -245

    const val URI_COMPONENT_TLD =           -255

    const val SETTINGS =                    -301

    // DRS v1.0.6: technical text tool codes (see DrsTextTool for the full
    // list and DrsTextTools for the pure transformations behind them).
    const val TEXT_TOOL_UPPERCASE =             -601
    const val TEXT_TOOL_LOWERCASE =             -602
    const val TEXT_TOOL_TITLE_CASE =            -603
    const val TEXT_TOOL_SENTENCE_CASE =         -604
    const val TEXT_TOOL_TRIM_SPACES =           -611
    const val TEXT_TOOL_TRIM_LINE_EDGES =       -612
    const val TEXT_TOOL_REMOVE_EMPTY_LINES =    -613
    const val TEXT_TOOL_REMOVE_LINE_BREAKS =    -614
    const val TEXT_TOOL_SORT_LINES =            -615
    const val TEXT_TOOL_REMOVE_DUPLICATE_LINES =-616
    const val TEXT_TOOL_NUMBER_LINES =          -617
    const val TEXT_TOOL_REVERSE_LINES =         -618
    // DRS v1.2.0: locale-aware quote wrapping (inside CODE_RANGE, so the
    // existing text-tool dispatch path handles it with no extra wiring).
    const val TEXT_TOOL_WRAP_QUOTES =           -619
    const val TEXT_TOOL_REMOVE_DIACRITICS =     -621
    // DRS v1.3.0: digit conversion, blank-line collapsing and tatweel
    // removal (same pure-transform dispatch path).
    const val TEXT_TOOL_TO_ARABIC_DIGITS =      -605
    const val TEXT_TOOL_TO_WESTERN_DIGITS =     -606
    const val TEXT_TOOL_COLLAPSE_EMPTY_LINES =  -620
    const val TEXT_TOOL_REMOVE_TATWEEL =        -622
    // DRS v1.4.0: descending line sort (the real counterpart of
    // TEXT_TOOL_SORT_LINES) and Arabic letter normalization (same
    // pure-transform dispatch path via DrsTextTool.CODE_RANGE).
    const val TEXT_TOOL_SORT_LINES_DESC =       -624
    const val TEXT_TOOL_NORMALIZE_ARABIC =      -623
    // DRS v1.5.0: case inversion, Arabic punctuation mapping, zero-width
    // cleanup and tab→space conversion (all pure transforms dispatched via
    // DrsTextTool.CODE_RANGE like every other text tool).
    const val TEXT_TOOL_TOGGLE_CASE =           -607
    const val TEXT_TOOL_TO_ARABIC_PUNCTUATION = -608
    const val TEXT_TOOL_REMOVE_ZERO_WIDTH =     -609
    const val TEXT_TOOL_TABS_TO_SPACES =        -610
    // DRS v1.5.0: length sort, word-level dedup, paren wrapping and one
    // sentence per line (same pure-transform dispatch path).
    const val TEXT_TOOL_SORT_LINES_BY_LENGTH =  -625
    const val TEXT_TOOL_REMOVE_DUPLICATE_WORDS =-626
    const val TEXT_TOOL_WRAP_PARENS =           -627
    const val TEXT_TOOL_SENTENCE_PER_LINE =     -628
    const val TEXT_TOOL_NORMALIZE_PUNCTUATION = -631
    const val TEXT_TOOL_CLEAN_TEXT =            -632

    // DRS v1.6.0: inverse tab/space + punctuation mappings, edge-only
    // blank trim and word-order reversal (same pure-transform families).
    const val TEXT_TOOL_SPACES_TO_TABS =        -629
    const val TEXT_TOOL_TO_WESTERN_PUNCTUATION =-630
    const val TEXT_TOOL_TRIM_BLANK_EDGES =      -633
    const val TEXT_TOOL_REVERSE_WORDS =         -634

    // DRS v1.7.0: PDF presentation-form repair, list<->lines, hashtag
    // space removal, emoji stripping and the URL encode/decode pair
    // (same pure-transform families; dispatch flows through CODE_RANGE).
    const val TEXT_TOOL_NORMALIZE_ARABIC_FORMS =-635
    const val TEXT_TOOL_SPLIT_TO_LINES =        -636
    const val TEXT_TOOL_JOIN_LINES =            -637
    const val TEXT_TOOL_REMOVE_ALL_SPACES =     -638
    const val TEXT_TOOL_STRIP_EMOJI =           -639
    const val TEXT_TOOL_URL_ENCODE =            -642
    const val TEXT_TOOL_URL_DECODE =            -643
    // DRS v1.19.0: constants for the two v1.8.0 tools that shipped without
    // KeyCode entries — their presses flow through DrsTextTool.CODE_RANGE as
    // before, but the constants make the SmartToolCodes stats register
    // self-documenting instead of magic numbers.
    const val TEXT_TOOL_SEPARATE_DIGIT_LETTERS = -644
    const val TEXT_TOOL_REMOVE_PUNCTUATION =    -645
    const val TEXT_TOOL_COUNT =                 -641
    const val TEXT_TOOL_DELETE_LINE =           -651
    const val TEXT_TOOL_DELETE_TO_LINE_START =  -652
    const val TEXT_TOOL_DELETE_TO_LINE_END =    -653

    const val CURRENCY_SLOT_1 =             -801
    const val CURRENCY_SLOT_2 =             -802
    const val CURRENCY_SLOT_3 =             -803
    const val CURRENCY_SLOT_4 =             -804
    const val CURRENCY_SLOT_5 =             -805
    const val CURRENCY_SLOT_6 =             -806

    const val MULTIPLE_CODE_POINTS =        -902
    const val DRAG_MARKER =                 -991
    const val NOOP =                        -999

    const val CHAR_WIDTH_SWITCHER =        -9701
    const val CHAR_WIDTH_FULL =            -9702
    const val CHAR_WIDTH_HALF =            -9703

    // DRS v1.23.0: the dead KANA_SMALL constant (12307) is gone — it had
    // zero references in code or assets (the kana-small toggle uses the
    // KeyboardState flag, not a key code).

    const val KANA_SWITCHER =              -9710
    const val KANA_HIRA =                  -9711
    const val KANA_KATA =                  -9712
    const val KANA_HALF_KATA =             -9713

    const val KESHIDA =                     1600
    const val HALF_SPACE =                  8204

    const val CJK_SPACE =                  12288
}
