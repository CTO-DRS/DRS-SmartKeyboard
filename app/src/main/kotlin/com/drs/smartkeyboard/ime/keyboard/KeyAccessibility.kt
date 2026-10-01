/*
 * Copyright (C) 2020-2025 The DRS Smart Keyboard Project
 */

package com.drs.smartkeyboard.ime.keyboard

import androidx.annotation.StringRes
import com.drs.smartkeyboard.R
import com.drs.smartkeyboard.ime.text.key.KeyCode

/**
 * DRS v1.17.0: accessibility support for the keyboard. Until this file
 * existed the keys were unlabeled boxes as far as TalkBack was concerned —
 * explore-by-touch announced nothing, because icon keys render no text and
 * the renderer passed `contentDescription = null`.
 *
 * [keyA11yLabelRes] maps the icon-only key codes (which carry no visible
 * label) to localized spoken labels. Character keys do NOT need this —
 * their visible label IS their description. Pure function, JVM-tested.
 */
fun keyA11yLabelRes(code: Int): Int? = when (code) {
    KeyCode.SHIFT -> R.string.key__a11y_shift
    KeyCode.CAPS_LOCK -> R.string.key__a11y_caps_lock
    KeyCode.DELETE -> R.string.key__a11y_delete
    KeyCode.FORWARD_DELETE -> R.string.key__a11y_forward_delete
    KeyCode.DELETE_WORD -> R.string.key__a11y_delete_word
    KeyCode.ENTER -> R.string.key__a11y_enter
    KeyCode.ARROW_LEFT -> R.string.key__a11y_arrow_left
    KeyCode.ARROW_RIGHT -> R.string.key__a11y_arrow_right
    KeyCode.ARROW_UP -> R.string.key__a11y_arrow_up
    KeyCode.ARROW_DOWN -> R.string.key__a11y_arrow_down
    KeyCode.MOVE_START_OF_LINE -> R.string.key__a11y_line_start
    KeyCode.MOVE_END_OF_LINE -> R.string.key__a11y_line_end
    KeyCode.VIEW_CHARACTERS -> R.string.key__a11y_view_characters
    KeyCode.VIEW_SYMBOLS -> R.string.key__a11y_view_symbols
    KeyCode.VIEW_SYMBOLS2 -> R.string.key__a11y_view_symbols2
    KeyCode.VIEW_NUMERIC_ADVANCED -> R.string.key__a11y_view_numeric
    // DRS v1.15.0 panels: the diacritics panel key speaks as its page name.
    KeyCode.IME_UI_MODE_DIACRITICS -> R.string.key__a11y_view_harakat
    else -> null
}
