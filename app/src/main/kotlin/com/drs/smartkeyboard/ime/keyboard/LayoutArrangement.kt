/*
 * Copyright (C) 2021-2025 The DRS Smart Keyboard Project
 */

package com.drs.smartkeyboard.ime.keyboard

import com.drs.smartkeyboard.lib.ext.ExtensionComponent
import com.drs.smartkeyboard.lib.ext.ExtensionComponentName
import kotlinx.serialization.Serializable

typealias LayoutArrangement = List<List<AbstractKeyData>>

@Serializable
data class LayoutArrangementComponent(
    override val id: String,
    override val label: String,
    override val authors: List<String>,
    val direction: String,
    val modifier: ExtensionComponentName? = null,
    val arrangementFile: String? = null,
) : ExtensionComponent {
    fun arrangementFile(type: LayoutType) = arrangementFile ?: "layouts/${type.id}/$id.json"
}
