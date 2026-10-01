/*
 * Copyright (C) 2021-2025 The DRS Smart Keyboard Project
 */

package org.drs.lib.snygg.value

private const val VarKey = "varKey"

sealed interface SnyggVarValue : SnyggValue {
    companion object {
        val VariableNameRegex = """--[a-zA-Z0-9-]+""".toRegex()
    }
}

data class SnyggDefinedVarValue(val key: String) : SnyggVarValue {
    companion object : SnyggValueEncoder {
        override val spec = SnyggValueSpec {
            function(name = "var") { string(id = VarKey, regex = SnyggVarValue.VariableNameRegex) }
        }

        override fun defaultValue() = SnyggDefinedVarValue("")

        override fun serialize(v: SnyggValue) = runCatching<String> {
            require(v is SnyggDefinedVarValue)
            val map = snyggIdToValueMapOf(VarKey to v.key)
            return@runCatching spec.pack(map)
        }

        override fun deserialize(v: String) = runCatching<SnyggValue> {
            val map = snyggIdToValueMapOf()
            spec.parse(v, map)
            val key = map.getString(VarKey)
            return@runCatching SnyggDefinedVarValue(key)
        }
    }

    override fun encoder() = Companion
}
