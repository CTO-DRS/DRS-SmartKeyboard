/*
 * Copyright (C) 2021-2025 The DRS Smart Keyboard Project
 */

package org.drs.lib.snygg.value

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private const val Size = "size"
private const val DpUnit = "dp"
private const val SpUnit = "sp"

sealed interface SnyggSizeValue : SnyggValue

data class SnyggDpSizeValue(val dp: Dp) : SnyggSizeValue {
    companion object : SnyggValueEncoder {
        override val spec = SnyggValueSpec {
            float(id = Size, unit = DpUnit, numberPattern = """(?:0|[1-9][0-9]*)(?:[.][0-9]*)?|[.][0-9]+""".toRegex())
        }

        override fun defaultValue() = SnyggDpSizeValue(0.dp)

        override fun serialize(v: SnyggValue) = runCatching<String> {
            require(v is SnyggDpSizeValue)
            val map = snyggIdToValueMapOf(Size to v.dp.value)
            return@runCatching spec.pack(map)
        }

        override fun deserialize(v: String) = runCatching<SnyggValue> {
            val map = snyggIdToValueMapOf()
            spec.parse(v, map)
            val size = map.getFloat(Size)
            return@runCatching SnyggDpSizeValue(size.dp)
        }
    }

    override fun encoder() = Companion
}

data class SnyggSpSizeValue(val sp: TextUnit) : SnyggSizeValue {
    companion object : SnyggValueEncoder {
        override val spec = SnyggValueSpec {
            float(id = Size, unit = SpUnit, numberPattern = """[1-9][0-9]*(?:[.][0-9]*)?""".toRegex())
        }

        override fun defaultValue() = SnyggSpSizeValue(24.sp)

        override fun serialize(v: SnyggValue) = runCatching<String> {
            require(v is SnyggSpSizeValue)
            val map = snyggIdToValueMapOf(Size to v.sp.value)
            return@runCatching spec.pack(map)
        }

        override fun deserialize(v: String) = runCatching<SnyggValue> {
            val map = snyggIdToValueMapOf()
            spec.parse(v, map)
            val size = map.getFloat(Size)
            return@runCatching SnyggSpSizeValue(size.sp)
        }
    }

    override fun encoder() = Companion
}

data class SnyggPercentageSizeValue(val percentage: Float) : SnyggSizeValue {
    companion object : SnyggValueEncoder {
        override val spec = SnyggValueSpec {
            percentageFloat(id = Size)
        }

        override fun defaultValue() = SnyggPercentageSizeValue(0f)

        override fun serialize(v: SnyggValue) = runCatching<String> {
            require(v is SnyggPercentageSizeValue)
            val map = snyggIdToValueMapOf(Size to v.percentage * 100.0f)
            return@runCatching spec.pack(map)
        }

        override fun deserialize(v: String) = runCatching<SnyggValue> {
            val map = snyggIdToValueMapOf()
            spec.parse(v, map)
            val size = map.getFloat(Size) / 100.0f
            return@runCatching SnyggPercentageSizeValue(size)
        }
    }

    override fun encoder() = Companion
}
