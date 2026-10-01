/*
 * Copyright (C) 2025 The DRS Smart Keyboard Project
 */

package com.drs.smartkeyboard.ime.clipboard

enum class ClipboardSyncBehavior(val shouldSyncSet: Boolean, val shouldSyncClear: Boolean) {
    NO_EVENTS(shouldSyncSet = false, shouldSyncClear = false),
    ONLY_CLEAR_EVENTS(shouldSyncSet = false, shouldSyncClear = true),
    ONLY_SET_EVENTS(shouldSyncSet = true, shouldSyncClear = false),
    ALL_EVENTS(shouldSyncSet = true, shouldSyncClear = true);
}
