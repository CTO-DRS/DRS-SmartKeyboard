/*
 * Copyright (C) 2021-2026 The DRS Smart Keyboard Project
 */

package org.drs.jetpref.datastore.model

internal object Validator {
    private val FILE_NAME_REGEX = """^(([a-zA-Z_])|([a-zA-Z_][a-zA-Z0-9_-]*[a-zA-Z0-9_]))${'$'}""".toRegex()
    private val KEY_REGEX = """^(([a-zA-Z_])|([a-zA-Z_][a-zA-Z0-9_-]*[a-zA-Z0-9_]))${'$'}""".toRegex()

    fun validateFileName(fileName: String): String {
        if (!FILE_NAME_REGEX.matches(fileName)) {
            throw IllegalArgumentException(
                "Datastore file name '$fileName' does not conform to the expected format of $FILE_NAME_REGEX"
            )
        }
        return fileName
    }

    fun validateKey(key: String): String {
        if (!KEY_REGEX.matches(key)) {
            throw IllegalArgumentException(
                "Preference key '$key' does not conform to the expected format of $KEY_REGEX"
            )
        }
        return key
    }
}
