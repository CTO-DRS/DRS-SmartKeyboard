/*
 * Copyright (C) 2021-2026 The DRS Smart Keyboard Project
 */

package org.drs.jetpref.datastore.model

import org.drs.jetpref.datastore.annotations.PreferenceTypeId

@ConsistentCopyVisibility
data class PreferenceType private constructor(@PreferenceTypeId val id: String) {
    companion object {
        @PreferenceTypeId private const val BOOLEAN: String =       "b"
        @PreferenceTypeId private const val DOUBLE: String =        "d"
        @PreferenceTypeId private const val FLOAT: String =         "f"
        @PreferenceTypeId private const val INTEGER: String =       "i"
        @PreferenceTypeId private const val LONG: String =          "l"
        @PreferenceTypeId private const val STRING: String =        "s"

        fun boolean() = PreferenceType(BOOLEAN)

        fun double() = PreferenceType(DOUBLE)

        fun float() = PreferenceType(FLOAT)

        fun integer() = PreferenceType(INTEGER)

        fun long() = PreferenceType(LONG)

        fun string() = PreferenceType(STRING)

        fun from(@PreferenceTypeId id: String) = PreferenceType(id)
    }

    fun isValid() = isPrimitive()

    fun isInvalid() = !isValid()

    fun isPrimitive() = when (id) {
        BOOLEAN, DOUBLE, FLOAT, INTEGER, LONG, STRING -> true
        else -> false
    }

    fun isBoolean() = id == BOOLEAN

    fun isDouble() = id == DOUBLE

    fun isFloat() = id == FLOAT

    fun isInteger() = id == INTEGER

    fun isLong() = id == LONG

    fun isString() = id == STRING

    override fun toString(): String {
        return id
    }
}
