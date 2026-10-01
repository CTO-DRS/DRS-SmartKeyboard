/*
 * Copyright (C) 2025-2026 The DRS Smart Keyboard Project
 */

package com.drs.smartkeyboard.ime.input

import com.drs.smartkeyboard.ime.text.key.KeyCode

/**
 * DRS v1.22.0: the latch state of the revived CTRL/ALT modifier keys —
 * «إحياء المفاتيح الميتة». Until this round the CTRL/ALT key codes fell
 * into the unknown-key branch of the input pipeline (drawn if a layout
 * declares them, dead on press, logged as an error). The latching
 * behavior mirrors the shift family users already know:
 *
 * - OFF: the modifier does nothing.
 * - LATCHED (one-shot): armed for the next consuming key — an arrow
 *   becomes word/line/page navigation, DELETE becomes word deletion —
 *   then releases itself.
 * - LOCKED: armed until the key is tapped again (the CTRL_LOCK/ALT_LOCK
 *   codes jump straight here, exactly like caps lock vs. shift).
 *
 * Typing a normal character keeps the character honest (no host-side
 * ctrl+letter emulation, which IMEs cannot deliver reliably) and
 * consumes a latched modifier so it can never surprise the user later.
 */
enum class InputModifierState(val value: Int) {
    OFF(0),
    LATCHED(1),
    LOCKED(2),
    ;

    /** Whether the modifier currently arms the next consuming key. */
    val isArmed: Boolean
        get() = this != OFF

    /**
     * The state after one consuming press: a one-shot latch releases,
     * a lock persists — that is the whole point of locking.
     */
    fun consumed(): InputModifierState = if (this == LATCHED) OFF else this

    companion object {
        fun fromInt(int: Int) = entries.firstOrNull { it.value == int } ?: OFF
    }

    override fun toString() = name.lowercase()

    fun toInt() = value
}

/**
 * The pure latch cycle — DRS v1.22.0. A plain modifier tap walks
 * OFF -> LATCHED -> LOCKED -> OFF; the *_LOCK codes jump straight to
 * [InputModifierState.LOCKED] in one press. Pure and testable: the
 * same function feeds the input pipeline and the unit contracts.
 */
fun cycleModifierLatch(current: InputModifierState, lock: Boolean): InputModifierState = when {
    lock -> InputModifierState.LOCKED
    current == InputModifierState.OFF -> InputModifierState.LATCHED
    current == InputModifierState.LATCHED -> InputModifierState.LOCKED
    else -> InputModifierState.OFF
}

/**
 * DRS v1.23.0: the FN latch's real work — mapping the digit keys onto
 * the function keys F1–F10 of the host (android.view.KeyEvent codes
 * KEYCODE_F1=131 … KEYCODE_F12=142; only F1–F10 have digits). The on-screen
 * digit keys carry the ASCII digit codes ('0'=48 … '9'=57), so the honest
 * contract is: '1'→F1 … '9'→F9, '0'→F10 — exactly what a physical
 * keyboard's Fn row delivers in terminals, remote-desktop clients and
 * console emulators. Returns null for anything that is not a digit key:
 * the caller then keeps the normal digit commit. Pure and JVM-tested.
 *
 * DRS v1.24.0: the row extends to its natural end — on a physical
 * keyboard '-' and '=' sit right after '0' on the top letter row, and
 * F11/F12 sit right after F10, so the Fn-row contract maps them onto
 * each other: '-'(45)→KEYCODE_F11(141), '='(61)→KEYCODE_F12(142).
 * Both keys are reachable on-screen (the numeric and symbols pages
 * ship '-', the symbols2 page ships '='), pinned by asset contracts.
 */
fun fnFunctionKeyCodeOf(digitCode: Int): Int? = when (digitCode) {
    in 49..57 -> 130 + (digitCode - 48) // '1'..'9' → F1..F9
    48 -> 140              // '0' → KEYCODE_F10
    45 -> 141              // '-' → KEYCODE_F11
    61 -> 142              // '=' → KEYCODE_F12
    else -> null
}

/**
 * DRS v1.24.0: which keys keep the FN latch alive instead of consuming
 * it. F11 and F12 live on different pages than the digits do ('-' on
 * numeric/symbols, '=' on symbols2), so a page hop must never eat the
 * latch the user just armed — otherwise the extended Fn row would be
 * unreachable in practice. Page/mode switches (VIEW_* and IME_UI_MODE_*)
 * and the FN keys themselves preserve it; every real consuming key
 * (letters, digits, '-'/'=', space, enter…) releases a one-shot latch
 * exactly as before. Pure and JVM-tested.
 */
fun fnSurvivesKey(code: Int): Boolean = when (code) {
    KeyCode.FN, KeyCode.FN_LOCK -> true
    KeyCode.VIEW_CHARACTERS,
    KeyCode.VIEW_SYMBOLS,
    KeyCode.VIEW_SYMBOLS2,
    KeyCode.VIEW_NUMERIC,
    KeyCode.VIEW_NUMERIC_ADVANCED,
    KeyCode.VIEW_PHONE,
    KeyCode.VIEW_PHONE2,
    KeyCode.IME_UI_MODE_TEXT,
    KeyCode.IME_UI_MODE_MEDIA,
    KeyCode.IME_UI_MODE_CLIPBOARD,
    KeyCode.IME_UI_MODE_TEXT_TOOLS,
    KeyCode.IME_UI_MODE_DIACRITICS,
    KeyCode.IME_UI_MODE_SMART_SYMBOLS,
    KeyCode.IME_UI_MODE_ARABIC_LETTERS,
    -> true
    else -> false
}
