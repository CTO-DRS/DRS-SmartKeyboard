/*
 * Copyright (C) 2025 The DRS Smart Keyboard Project
 */

package com.drs.smartkeyboard.ime.clipboard

import com.drs.smartkeyboard.ime.clipboard.provider.ClipboardItem

data class ClipboardHistory(val all: List<ClipboardItem>) {
    companion object {
        private const val RECENT_TIMESPAN_MS = 300_000 // 300 sec = 5 min

        val EMPTY = ClipboardHistory(emptyList())
    }

    private val now = System.currentTimeMillis()

    val pinned = all.filter { it.isPinned }
    val unpinned = all.filter { !it.isPinned }
    val recent = unpinned.filter { (now - it.creationTimestampMs) < RECENT_TIMESPAN_MS }
    val other = unpinned.filter { (now - it.creationTimestampMs) >= RECENT_TIMESPAN_MS }
}
