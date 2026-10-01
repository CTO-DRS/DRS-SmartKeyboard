/*
 * Copyright (C) 2021-2025 The DRS Smart Keyboard Project
 */

package org.drs.lib.snygg.value

import org.drs.lib.kotlin.toStringWithoutDotZero

typealias SnyggIdToValueMap = MutableMap<String, String>

fun snyggIdToValueMapOf(vararg pairs: Pair<String, Any>): SnyggIdToValueMap {
    val map = mutableMapOf<String, String>()
    map.add(*pairs)
    return map
}

fun SnyggIdToValueMap.getInt(id: String): Int {
    return getValue(id).toInt()
}

fun SnyggIdToValueMap.getFloat(id: String): Float {
    return getValue(id).toFloat()
}

fun SnyggIdToValueMap.getString(id: String): String {
    return getValue(id)
}

fun SnyggIdToValueMap.add(vararg pairs: Pair<String, Any>) {
    pairs.forEach { (id, value) ->
        if (value is Number) {
            put(id, value.toStringWithoutDotZero())
        } else {
            put(id, value.toString())
        }
    }
}
