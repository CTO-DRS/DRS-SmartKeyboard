/*
 * Copyright (C) 2021-2025 The DRS Smart Keyboard Project
 */

package com.drs.smartkeyboard.ime.keyboard

import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.runtime.mutableStateMapOf
import com.drs.smartkeyboard.ime.core.SubtypePreset
import com.drs.smartkeyboard.ime.nlp.PunctuationRule
import com.drs.smartkeyboard.ime.popup.PopupMappingComponent
import com.drs.smartkeyboard.ime.text.composing.Composer
import com.drs.smartkeyboard.lib.ext.Extension
import com.drs.smartkeyboard.lib.ext.ExtensionComponent
import com.drs.smartkeyboard.lib.ext.ExtensionComponentName
import com.drs.smartkeyboard.lib.ext.ExtensionEditor
import com.drs.smartkeyboard.lib.ext.ExtensionMeta
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@SerialName(KeyboardExtension.SERIAL_TYPE)
@Serializable
data class KeyboardExtension(
    override val meta: ExtensionMeta,
    override val dependencies: List<String>? = null,
    val composers: List<Composer> = listOf(),
    val currencySets: List<CurrencySet> = listOf(),
    val layouts: Map<String, List<LayoutArrangementComponent>> = mapOf(),
    val punctuationRules: List<PunctuationRule> = listOf(),
    val popupMappings: List<PopupMappingComponent> = listOf(),
    val subtypePresets: List<SubtypePreset> = listOf(),
) : Extension() {

    companion object {
        const val SERIAL_TYPE = "ime.extension.keyboard"
    }

    override fun serialType() = SERIAL_TYPE

    override fun components(): List<ExtensionComponent> {
        return emptyList()
    }

    override fun edit() = KeyboardExtensionEditor(
        meta = meta,
        dependencies = dependencies?.toMutableList() ?: mutableListOf(),
        layouts = layouts,
        composers = composers,
        currencySets = currencySets,
        punctuationRules = punctuationRules,
        popupMappings = popupMappings,
        subtypePresets = subtypePresets,
    )
}

/**
 * DRS M0.1 — the real editor of a keyboard extension. The manifest-level
 * fields (meta, dependencies, the layout component INDEX) are editable
 * directly; the arrangement CONTENTS (rows/keys of each layout JSON file)
 * are edited through [LayoutArrangementEditor] instances registered in
 * [editedArrangements] by the visual editor screen, keyed by
 * "<layoutType.id>:<arrangementId>". [build] only ever reproduces the
 * manifest — the edited arrangement files are written by the save flow
 * itself, so the manifest and the files can never disagree about which
 * arrangements exist.
 */
class KeyboardExtensionEditor(
    override var meta: ExtensionMeta,
    override val dependencies: MutableList<String>,
    val layouts: Map<String, List<LayoutArrangementComponent>> = mapOf(),
    val composers: List<Composer> = listOf(),
    val currencySets: List<CurrencySet> = listOf(),
    val punctuationRules: List<PunctuationRule> = listOf(),
    val popupMappings: List<PopupMappingComponent> = listOf(),
    val subtypePresets: List<SubtypePreset> = listOf(),
) : ExtensionEditor {

    /** Runtime-only state: arrangements opened for editing in the visual editor. */
    val editedArrangements: SnapshotStateMap<String, LayoutArrangementEditor> = mutableStateMapOf()

    override fun build() = KeyboardExtension(
        meta = meta,
        dependencies = dependencies.takeUnless { it.isEmpty() }?.toList(),
        composers = composers,
        currencySets = currencySets,
        layouts = layouts,
        punctuationRules = punctuationRules,
        popupMappings = popupMappings,
        subtypePresets = subtypePresets,
    )
}

@Suppress("NOTHING_TO_INLINE")
inline fun extCoreComposer(id: String): ExtensionComponentName {
    return ExtensionComponentName(
        extensionId = "org.drs.composers",
        componentId = id,
    )
}

@Suppress("NOTHING_TO_INLINE")
inline fun extCoreCurrencySet(id: String): ExtensionComponentName {
    return ExtensionComponentName(
        extensionId = "org.drs.currencysets",
        componentId = id,
    )
}

@Suppress("NOTHING_TO_INLINE")
inline fun extCoreLayout(id: String): ExtensionComponentName {
    return ExtensionComponentName(
        extensionId = "org.drs.layouts",
        componentId = id,
    )
}

@Suppress("NOTHING_TO_INLINE")
inline fun extCorePunctuationRule(id: String): ExtensionComponentName {
    return ExtensionComponentName(
        extensionId = "org.drs.localization",
        componentId = id,
    )
}

@Suppress("NOTHING_TO_INLINE")
inline fun extCorePopupMapping(id: String): ExtensionComponentName {
    return ExtensionComponentName(
        extensionId = "org.drs.localization",
        componentId = id,
    )
}
