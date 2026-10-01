/*
 * Copyright (C) 2020-2025 The DRS Smart Keyboard Project
 */

package com.drs.smartkeyboard.ime.media.emoticon

import kotlinx.serialization.Serializable

/**
 * Data class for a single emoticon.
 *
 * @property icon The char sequence of the emoticon.
 * @property meaning List of possible meanings for this emoticon.
 */
@Serializable
data class EmoticonKeyData(
    var icon: String = "",
    var meaning: List<String> = listOf()
)
