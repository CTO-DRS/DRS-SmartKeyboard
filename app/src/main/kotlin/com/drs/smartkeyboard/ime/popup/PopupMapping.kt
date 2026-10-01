/*
 * Copyright (C) 2021-2025 The DRS Smart Keyboard Project
 */

package com.drs.smartkeyboard.ime.popup

import com.drs.smartkeyboard.ime.keyboard.AbstractKeyData
import com.drs.smartkeyboard.ime.text.key.KeyVariation
import com.drs.smartkeyboard.lib.ext.ExtensionComponent
import kotlinx.serialization.Serializable

/**
 * An object which maps each base key to its extended popups. This can be done for each
 * key variation. [KeyVariation.ALL] is always the fallback for each key.
 */
typealias PopupMapping = Map<KeyVariation, Map<String, PopupSet<AbstractKeyData>>>

@Serializable
data class PopupMappingComponent(
    override val id: String,
    override val label: String = id,
    override val authors: List<String>,
    val mappingFile: String? = null,
) : ExtensionComponent {
    fun mappingFile() = "popupMappings/$id.json"
}
