/*
 * Copyright (C) 2025-2026 The DRS Smart Keyboard Project
 */

package com.drs.smartkeyboard.ime.window

import com.drs.smartkeyboard.lib.devtools.flogError
import org.drs.jetpref.datastore.model.PreferenceSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Alias for mapping form factor guesses to window configs.
 */
typealias ImeWindowConfigByType = Map<ImeFormFactor.Type, ImeWindowConfig>

/**
 * Describes the window configuration, which is used to persist the user-preferred modes and sizes to prefs. For
 * each [ImeFormFactor.Type], a separate window config is present.
 *
 * @property mode The current window mode. Determines if the fixed or floating mode and props should be used for
 *  calculations and logic decisions.
 * @property fixedMode Describes the fixed sub-mode.
 * @property fixedProps Describes the props per fixed sub-mode. May not have a mapping for a given sub-mode, in
 *  which case the window constraints should be queried for default props.
 * @property floatingMode Describes the floating sub-mode.
 * @property floatingProps Describes the props per floating sub-mode. May not have a mapping for a given sub-mode, in
 *  which case the window constraints should be queried for default props.
 */
@Serializable
data class ImeWindowConfig(
    val mode: ImeWindowMode,
    val fixedMode: ImeWindowMode.Fixed = ImeWindowMode.Fixed.NORMAL,
    val fixedProps: Map<ImeWindowMode.Fixed, ImeWindowProps.Fixed> = emptyMap(),
    val floatingMode: ImeWindowMode.Floating = ImeWindowMode.Floating.NORMAL,
    val floatingProps: Map<ImeWindowMode.Floating, ImeWindowProps.Floating> = emptyMap(),
) {
    /**
     * Helper for serializing [ImeWindowConfigByType] to prefs.
     */
    object ByTypeSerializer : PreferenceSerializer<ImeWindowConfigByType> {
        override fun serialize(value: ImeWindowConfigByType): String {
            return Json.encodeToString(value)
        }

        override fun deserialize(value: String): ImeWindowConfigByType {
            return try {
                Json.decodeFromString(value)
            } catch (e: Throwable) {
                // DRS privacy (S-3): the raw exception message can embed fragments of the pref payload,
                // and a corrupted/foreign pref value is user-controlled data — log the exception class
                // and the payload length only.
                flogError { "Failed to deserialize ImeWindowConfig.ByType (payload length ${value.length}): ${e.javaClass.simpleName}" }
                emptyMap()
            }
        }
    }

    companion object {
        /**
         * The default window config, which has empty prop mappings.
         */
        val Default = ImeWindowConfig(
            mode = ImeWindowMode.FIXED,
            fixedMode = ImeWindowMode.Fixed.NORMAL,
            floatingMode = ImeWindowMode.Floating.NORMAL,
        )
    }
}
