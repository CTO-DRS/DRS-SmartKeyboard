/*
 * Copyright (C) 2020-2025 The DRS Smart Keyboard Project
 */

package com.drs.smartkeyboard.ime.theme

/**
 * Enum class which specifies all theme modes available. Used in the Settings
 * to properly manage different use cases when the day or night theme should
 * be active.
 */
enum class ThemeMode {
    ALWAYS_DAY,
    ALWAYS_NIGHT,
    FOLLOW_SYSTEM,
    FOLLOW_TIME;
}
