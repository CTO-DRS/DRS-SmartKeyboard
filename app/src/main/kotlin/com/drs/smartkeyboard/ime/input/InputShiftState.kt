/*
 * Copyright (C) 2022-2025 The DRS Smart Keyboard Project
 */

package com.drs.smartkeyboard.ime.input

/**
 * Enum for the input shift states of a text keyboard.
 */
enum class InputShiftState(val value: Int) {
    /**
     * The default input mode, no shift modifier is active.
     */
    UNSHIFTED(0),
    /**
     * Shift is active, but resets to [UNSHIFTED] after a single input. Symbol rows are shifted.
     * Indicates that this shift was manually activated, e.g. by pressing the shift key.
     */
    SHIFTED_MANUAL(1),
    /**
     * Shift is active, but resets to [UNSHIFTED] after a single input. Symbol rows are not shifted.
     * Indicates that this shift was automatically activated through the auto-capitalization feature.
     */
    SHIFTED_AUTOMATIC(2),
    /**
     * Caps lock is active and persists after input. Symbol rows are not shifted.
     */
    CAPS_LOCK(3);

    companion object {
        fun fromInt(int: Int) = entries.firstOrNull { it.value == int } ?: UNSHIFTED
    }

    override fun toString() = name.lowercase()

    fun toInt() = value
}
