/*
 * Copyright (C) 2021-2025 The DRS Smart Keyboard Project
 * Copyright (C) 2025 DRS Smart Keyboard contributors
 */

package com.drs.smartkeyboard.drs

import com.drs.smartkeyboard.ime.text.key.KeyCode
import com.drs.smartkeyboard.ime.text.key.KeyType

/**
 * DRS v1.0.6: catalogue of every key the technical toolbar can host, plus
 * the shared resolution logic used by both the IME strip and the editor
 * screen. A key is identified by a stable string id so the persisted
 * arrangement survives code changes.
 *
 * The strip always shows its fixed leading "text tools" toggle first; this
 * catalogue covers the reorderable/removable keys that follow it.
 */
data class DrsTechToolbarKey(
    val id: String,
    val label: String,
    val code: Int,
    val type: KeyType,
)

object DrsTechToolbarKeys {

    /** Every available key, in the default display order. */
    val ALL: List<DrsTechToolbarKey> = listOf(
        DrsTechToolbarKey("arrow_left", "←", KeyCode.ARROW_LEFT, KeyType.NAVIGATION),
        DrsTechToolbarKey("arrow_right", "→", KeyCode.ARROW_RIGHT, KeyType.NAVIGATION),
        DrsTechToolbarKey("arrow_up", "↑", KeyCode.ARROW_UP, KeyType.NAVIGATION),
        DrsTechToolbarKey("arrow_down", "↓", KeyCode.ARROW_DOWN, KeyType.NAVIGATION),
        DrsTechToolbarKey("tab", "Tab", KeyCode.TAB, KeyType.FUNCTION),
        DrsTechToolbarKey("esc", "Esc", KeyCode.ESCAPE, KeyType.FUNCTION),
        DrsTechToolbarKey("brace_open", "{", '{'.code, KeyType.CHARACTER),
        DrsTechToolbarKey("brace_close", "}", '}'.code, KeyType.CHARACTER),
        DrsTechToolbarKey("bracket_open", "[", '['.code, KeyType.CHARACTER),
        DrsTechToolbarKey("bracket_close", "]", ']'.code, KeyType.CHARACTER),
        DrsTechToolbarKey("paren_open", "(", '('.code, KeyType.CHARACTER),
        DrsTechToolbarKey("paren_close", ")", ')'.code, KeyType.CHARACTER),
        DrsTechToolbarKey("less", "<", '<'.code, KeyType.CHARACTER),
        DrsTechToolbarKey("greater", ">", '>'.code, KeyType.CHARACTER),
        DrsTechToolbarKey("slash", "/", '/'.code, KeyType.CHARACTER),
        DrsTechToolbarKey("backslash", "\\", '\\'.code, KeyType.CHARACTER),
        DrsTechToolbarKey("pipe", "|", '|'.code, KeyType.CHARACTER),
        DrsTechToolbarKey("at", "@", '@'.code, KeyType.CHARACTER),
        DrsTechToolbarKey("hash", "#", '#'.code, KeyType.CHARACTER),
        DrsTechToolbarKey("dollar", "$", '$'.code, KeyType.CHARACTER),
        DrsTechToolbarKey("percent", "%", '%'.code, KeyType.CHARACTER),
        DrsTechToolbarKey("caret", "^", '^'.code, KeyType.CHARACTER),
        DrsTechToolbarKey("amp", "&", '&'.code, KeyType.CHARACTER),
        DrsTechToolbarKey("asterisk", "*", '*'.code, KeyType.CHARACTER),
        // DRS v1.22.0: the revived modifier latches — until this round the
        // CTRL/ALT codes were drawn-but-dead (they fell into the unknown-key
        // branch). They now latch like shift (tap = one-shot, tap again =
        // lock, third tap = release) and arm word/line/page navigation and
        // word deletion. The live active dot comes from the same
        // ToggleStates the other toggle tools use.
        DrsTechToolbarKey("ctrl", "Ctrl", KeyCode.CTRL, KeyType.MODIFIER),
        DrsTechToolbarKey("alt", "Alt", KeyCode.ALT, KeyType.MODIFIER),
        // DRS v1.23.0: the modifier family is complete — FN latches like
        // CTRL/ALT and turns the digit keys into real F1–F10 hardware
        // events while armed. The live active dot comes from the same
        // ToggleStates the other toggle tools use.
        DrsTechToolbarKey("fn", "Fn", KeyCode.FN, KeyType.MODIFIER),
        // DRS v1.26.0: the microphone rides the technical strip — one tap
        // reaches the same built-in dictation route the mic key on the
        // letters board drives (privacy gates included). The label follows
        // the Tab/Esc/Fn Latin convention; the key is momentary, so it has
        // no toggle dot.
        DrsTechToolbarKey("mic", "Mic", KeyCode.VOICE_INPUT, KeyType.FUNCTION),
    )

    private val BY_ID = ALL.associateBy { it.id }

    /** Default arrangement = the full catalogue order. */
    val DEFAULT_IDS: List<String> = ALL.map { it.id }

    /**
     * Resolves the persisted id list into displayable keys, keeping unknown
     * ids out and appending any catalogue key the user removed from the list
     * is NOT done - the user's choice to hide keys is respected. A persisted
     * empty/invalid arrangement falls back to the full default so the strip
     * is never accidentally empty.
     */
    fun resolve(persisted: List<String>): List<DrsTechToolbarKey> {
        if (persisted.isEmpty()) return ALL
        val resolved = persisted.mapNotNull { BY_ID[it] }
        return if (resolved.isEmpty()) ALL else resolved
    }
}
