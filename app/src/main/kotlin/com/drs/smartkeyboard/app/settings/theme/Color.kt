/*
 * Copyright (C) 2025 The DRS Smart Keyboard Project
 */

package com.drs.smartkeyboard.app.settings.theme

import androidx.compose.ui.graphics.Color
import org.drs.jetpref.datastore.model.PreferenceSerializer

object ColorPreferenceSerializer : PreferenceSerializer<Color> {
    @OptIn(ExperimentalStdlibApi::class)
    override fun deserialize(value: String): Color {
        return Color(value.hexToULong())
    }

    @OptIn(ExperimentalStdlibApi::class)
    override fun serialize(value: Color): String = value.value.toHexString()
}
