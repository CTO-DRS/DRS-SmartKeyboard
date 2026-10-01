/*
 * Copyright (C) 2025 The DRS Smart Keyboard Project
 */

package com.drs.smartkeyboard.ime.theme

import android.content.Context
import com.drs.smartkeyboard.lib.devtools.flogError
import org.drs.lib.kotlin.io.subFile
import org.drs.lib.snygg.value.SnyggAssetResolver
import java.net.URI

class DrsAssetResolver(val context: Context, val themeInfo: ThemeManager.ThemeInfo) : SnyggAssetResolver {
    override fun resolveAbsolutePath(uri: String) = runCatching {
        val uri = URI.create(uri)
        require(uri.scheme == "flex")
        require(uri.authority.isNullOrEmpty())
        val baseDir = checkNotNull(themeInfo.loadedDir) { "Loaded directory was null" }
        val basePath = baseDir.canonicalPath
        val canonicalFile = baseDir.subFile(uri.path).canonicalFile
        val canonicalPath = canonicalFile.path
        check(canonicalPath.startsWith(basePath)) {
            "Calculated path '$canonicalPath' does not start with base path '$basePath'"
        }
        check(canonicalFile.exists()) {
            "Calculated path '$canonicalPath' does not exist"
        }
        check(canonicalFile.isFile()) {
            "Calculated path '$canonicalPath' is not a file"
        }
        canonicalPath
    }.onFailure { exception ->
        flogError { "DrsAssetResolver failed to resolve URI '$uri'\n  error: ${exception.message}\n  with:  $themeInfo" }
    }
}
