/*
 * Copyright (C) 2025-2026 The DRS Smart Keyboard Project
 */

package com.drs.smartkeyboard.ime.keyboard

import android.view.KeyEvent
import com.drs.smartkeyboard.ime.text.gestures.SwipeAction

/**
 * DRS v2.7.0 «مزايا البحث الخارجي» — volume-key cursor control.
 *
 * Researched from the AOSP/OpenBoard heritage (LatinIME's volume-key
 * cursor affordance): while the keyboard owns key events, VOLUME_UP and
 * VOLUME_DOWN move the text cursor one line up/down instead of changing
 * media volume — an accessibility win for one-handed and motor-impaired
 * typing, and a precision win on long documents.
 *
 * Deliberately minimal: only the two volume keys map, only to the SAME
 * cursor actions the board's swipe language already speaks
 * (MOVE_CURSOR_UP/DOWN), so the dispatch path and its feedback are the
 * existing, tested ones — no new key dialect. Everything else (any other
 * key code) returns null and falls through untouched.
 *
 * Pure mapping; the pref gate and dispatch live in KeyboardManager.
 */
object DrsVolumeCursor {

    fun actionFor(keyCode: Int): SwipeAction? = when (keyCode) {
        KeyEvent.KEYCODE_VOLUME_UP -> SwipeAction.MOVE_CURSOR_UP
        KeyEvent.KEYCODE_VOLUME_DOWN -> SwipeAction.MOVE_CURSOR_DOWN
        else -> null
    }
}
