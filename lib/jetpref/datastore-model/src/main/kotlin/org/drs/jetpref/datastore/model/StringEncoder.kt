/*
 * Copyright (C) 2021-2026 The DRS Smart Keyboard Project
 */

package org.drs.jetpref.datastore.model

internal object StringEncoder {
    fun encode(str: String): String {
        val sb = StringBuilder()
        sb.append("\"")
        sb.append(str
            .replace("\\", "\\\\")
            .replace("\r", "\\r")
            .replace("\n", "\\n")
            .replace("\"", "\\\"")
        )
        sb.append("\"")
        return sb.toString()
    }

    fun decode(str: String): String {
        val trimmedStr = str.trim()
        return if (trimmedStr.startsWith("\"") && trimmedStr.endsWith("\"") && trimmedStr.length >= 2) {
            trimmedStr
                .substring(1, trimmedStr.length - 1)
                .replace("\\\"", "\"")
                .replace("\\n", "\n")
                .replace("\\r", "\r")
                .replace("\\\\", "\\")
        } else {
            ""
        }
    }
}
