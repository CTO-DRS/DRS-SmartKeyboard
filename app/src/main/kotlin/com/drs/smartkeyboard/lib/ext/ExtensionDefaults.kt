/*
 * Copyright (C) 2021-2025 The DRS Smart Keyboard Project
 */

package com.drs.smartkeyboard.lib.ext

import org.drs.lib.kotlin.curlyFormat

object ExtensionDefaults {
    private const val ID_LOCAL_TEMPLATE = "local.{groupName}.{extensionName}"

    const val FILE_EXTENSION = "flex"
    const val MANIFEST_FILE_NAME = "extension.json"

    fun createLocalId(
        groupName: String,
        extensionName: String = System.currentTimeMillis().toString(),
    ) = ID_LOCAL_TEMPLATE.curlyFormat("groupName" to groupName, "extensionName" to extensionName)

    fun createFlexName(id: String) = "$id.$FILE_EXTENSION"
}
