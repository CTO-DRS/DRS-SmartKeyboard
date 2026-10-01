/*
 * Copyright (C) 2021-2025 The DRS Smart Keyboard Project
 */

package com.drs.smartkeyboard.ime.theme

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import com.drs.smartkeyboard.lib.ext.Extension
import com.drs.smartkeyboard.lib.ext.ExtensionEditor
import com.drs.smartkeyboard.lib.ext.ExtensionMeta
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@SerialName(ThemeExtension.SERIAL_TYPE)
@Serializable
class ThemeExtension(
    override val meta: ExtensionMeta,
    override val dependencies: List<String>? = null,
    val themes: List<ThemeExtensionComponentImpl>,
) : Extension() {

    companion object {
        const val SERIAL_TYPE = "ime.extension.theme"
    }

    override fun serialType() = SERIAL_TYPE

    override fun components() = themes

    override fun edit() = ThemeExtensionEditor(
        meta = meta,
        dependencies = dependencies?.toMutableList() ?: mutableListOf(),
        themes = mutableStateListOf(*themes.map { it.edit() }.toTypedArray()),
    )
}

class ThemeExtensionEditor(
    override var meta: ExtensionMeta,
    override val dependencies: MutableList<String>,
    val themes: SnapshotStateList<ThemeExtensionComponentEditor>,
) : ExtensionEditor {

    override fun build() = ThemeExtension(
        meta = meta,
        dependencies = dependencies.takeUnless { it.isEmpty() }?.toList(),
        themes = themes.map { it.build() },
    )
}
