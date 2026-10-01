/*
 * Copyright (C) 2021-2025 The DRS Smart Keyboard Project
 */

package com.drs.smartkeyboard.lib.ext

import androidx.compose.runtime.saveable.Saver
import org.drs.jetpref.datastore.model.PreferenceSerializer
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import org.drs.lib.kotlin.tryOrNull

/**
 * An extension component, typically a layout, theme, file, etc. descriptor.
 */
interface ExtensionComponent {
    val id: String
    val label: String
    val authors: List<String>
}

/**
 * An extension component name is an identifier string which allows the resources manager to load a resource component
 * based on an [extensionId] and an [componentId] for a component type.
 *
 * Example component name:
 *  `org.drs.layouts:qwerty`
 */
@Serializable(with = ExtensionComponentName.Serializer::class)
data class ExtensionComponentName(
    val extensionId: String,
    val componentId: String,
) {
    companion object {
        private const val DELIMITER = ":"

        fun from(str: String): ExtensionComponentName {
            val data = str.split(DELIMITER)
            check(data.size == 2) { "Extension component name must be of format <ext_id>:<comp_id>" }
            return ExtensionComponentName(data[0], data[1])
        }

        val Saver = Saver<ExtensionComponentName?, String>(
            save = { it.toString() },
            restore = { tryOrNull { from(it) } },
        )
    }

    override fun toString(): String {
        return "$extensionId$DELIMITER$componentId"
    }

    object Serializer : PreferenceSerializer<ExtensionComponentName>, KSerializer<ExtensionComponentName> {
        override val descriptor = PrimitiveSerialDescriptor("ExtensionComponentName", PrimitiveKind.STRING)

        override fun serialize(value: ExtensionComponentName): String {
            return value.toString()
        }

        override fun serialize(encoder: Encoder, value: ExtensionComponentName) {
            encoder.encodeString(value.toString())
        }

        override fun deserialize(value: String): ExtensionComponentName? {
            return tryOrNull { from(value) }
        }

        override fun deserialize(decoder: Decoder): ExtensionComponentName {
            return from(decoder.decodeString())
        }
    }
}
