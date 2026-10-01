/*
 * Copyright (C) 2021-2025 The DRS Smart Keyboard Project
 */

package com.drs.smartkeyboard.ime.text.key

/**
 * Helper class for summarizing all hint preferences in one single object.
 */
data class KeyHintConfiguration(
    val symbolHintMode: KeyHintMode,
    val numberHintMode: KeyHintMode,
    val mergeHintPopups: Boolean
) {
    companion object {
        val HINTS_DISABLED = KeyHintConfiguration(KeyHintMode.DISABLED, KeyHintMode.DISABLED, false)
    }
}
