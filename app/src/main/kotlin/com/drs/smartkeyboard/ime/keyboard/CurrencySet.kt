/*
 * Copyright (C) 2021-2025 The DRS Smart Keyboard Project
 */

package com.drs.smartkeyboard.ime.keyboard

import com.drs.smartkeyboard.ime.text.key.KeyCode
import com.drs.smartkeyboard.ime.text.keyboard.TextKeyData
import kotlinx.serialization.Serializable
import kotlin.math.abs

@Serializable
class CurrencySet(
    val id: String,
    val label: String,
    private val slots: List<TextKeyData>
) {
    companion object {
        val Fallback = CurrencySet(
            id = "fallback",
            label = "Fallback",
            slots = listOf(
                TextKeyData(code = 36, label = "$"),
                TextKeyData(code = 162, label = "¢"),
                TextKeyData(code = 8364, label = "€"),
                TextKeyData(code = 163, label = "£"),
                TextKeyData(code = 165, label = "¥"),
                TextKeyData(code = 8369, label = "₱")
            )
        )

        fun isCurrencySlot(keyCode: Int): Boolean {
            return when (keyCode) {
                KeyCode.CURRENCY_SLOT_1,
                KeyCode.CURRENCY_SLOT_2,
                KeyCode.CURRENCY_SLOT_3,
                KeyCode.CURRENCY_SLOT_4,
                KeyCode.CURRENCY_SLOT_5,
                KeyCode.CURRENCY_SLOT_6 -> true
                else -> false
            }
        }
    }

    fun getSlot(keyCode: Int): TextKeyData? {
        val slot = abs(keyCode) - abs(KeyCode.CURRENCY_SLOT_1)
        return slots.getOrNull(slot)
    }

    override fun toString(): String {
        return "${CurrencySet::class.simpleName} { id=$id, label\"$label\", slots=$slots }"
    }
}
