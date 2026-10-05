/*
 * Copyright (C) 2025 The DRS Smart Keyboard Project
 */

package com.drs.smartkeyboard.ime.theme

import android.content.Context
import com.drs.smartkeyboard.lib.devtools.flogError
import org.drs.lib.kotlin.io.subFile
import org.drs.lib.snygg.value.SnyggAssetResolver
import java.io.File
import java.net.URI

class DrsAssetResolver(val context: Context, val themeInfo: ThemeManager.ThemeInfo) : SnyggAssetResolver {
    override fun resolveAbsolutePath(uri: String) = runCatching {
        val uri = URI.create(uri)
        require(uri.authority.isNullOrEmpty())
        when (uri.scheme) {
            // Theme-package assets: resolved inside the unzipped theme dir.
            "flex" -> resolveWithin(
                baseDir = checkNotNull(themeInfo.loadedDir) { "Loaded directory was null" },
                relative = uri.path.orEmpty(),
            )
            // DRS v2.8.0 «خلفيتك من ألبومك»: the single user background image,
            // stored app-privately. The name must pass the pure safety contract
            // BEFORE any path math, then the canonical containment check runs
            // exactly like the flex branch.
            DrsThemeBackground.URI_SCHEME -> {
                val name = uri.path?.removePrefix("/")
                require(DrsThemeBackground.isSafeStoredName(name)) {
                    "Rejected unsafe drsimg name '$name'"
                }
                resolveWithin(
                    baseDir = File(context.noBackupFilesDir, DrsBackgroundImageStore.DIR_NAME),
                    relative = name!!,
                )
            }
            else -> error("Unsupported asset URI scheme '${uri.scheme}'")
        }
    }.onFailure { exception ->
        flogError { "DrsAssetResolver failed to resolve URI '$uri'\n  error: ${exception.message}\n  with:  $themeInfo" }
    }

    private fun resolveWithin(baseDir: File, relative: String): String {
        val basePath = baseDir.canonicalPath
        val canonicalFile = baseDir.subFile(relative).canonicalFile
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
        return canonicalPath
    }
}
