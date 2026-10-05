/*
 * Copyright (C) 2025 The DRS Smart Keyboard Project
 */

package com.drs.smartkeyboard.ime.theme

/**
 * DRS v2.8.0 «خلفيتك من ألبومك» — the pure contract of the user background image.
 *
 * The user can pick ONE image from their device; it is stored app-privately under
 * `noBackupFilesDir/drs_background/<name>` and injected into the ACTIVE theme's
 * stylesheet as a `background-image: uri("drsimg:/<name>")` rule on the `window`
 * element. Nothing leaves the device, nothing is uploaded, and no theme file on
 * disk is ever modified — the patch is applied in memory at theme-load time.
 *
 * This object holds the pure, JVM-testable decisions:
 *  - the `drsimg:` URI scheme (resolved by [DrsAssetResolver]),
 *  - the dimness→scrim-alpha mapping,
 *  - the stored-file-name safety check (defense in depth beside the canonical
 *    containment check performed by the resolver at resolve time).
 */
object DrsThemeBackground {
    /** URI scheme addressing the app-private user background image dir. */
    const val URI_SCHEME = "drsimg"

    /** Maximum dimness the user can pick (percent). 0 = no scrim at all. */
    const val MAX_DIMNESS = 80

    /** Default dimness (percent) — a dark-ish scrim so keys stay readable on bright photos. */
    const val DEFAULT_DIMNESS = 30

    /**
     * Maps the user-facing dimness percent (0..[MAX_DIMNESS]) to a scrim alpha.
     * Out-of-range values are clamped, never thrown — a corrupted pref value
     * degrades to a readable keyboard, not a crash.
     */
    fun dimnessToAlpha(dimness: Int): Float {
        return dimness.coerceIn(0, MAX_DIMNESS) / 100f
    }

    /** The stylesheet URI for a stored background image file name. */
    fun uriFor(fileName: String): String {
        return "$URI_SCHEME:/$fileName"
    }

    /**
     * Only file names that our own importer produces are accepted anywhere in the
     * injection pipeline. The importer always writes `<16 lowercase hex chars>.<ext>`,
     * so anything containing separators, dot-segments, whitespace or exotic
     * characters is rejected BEFORE it can ever reach a file path.
     */
    fun isSafeStoredName(name: String?): Boolean {
        if (name.isNullOrEmpty()) return false
        if (name.length > 64) return false
        if (!name.matches(STORED_NAME_REGEX)) return false
        return true
    }

    private val STORED_NAME_REGEX = """[0-9a-f]{16}\.(jpg|jpeg|png|webp|gif)""".toRegex()

    /** Sanitizes a picked file's display name into one of the allowed extensions. */
    fun extensionOf(displayName: String?): String {
        val raw = displayName?.substringAfterLast('.', "")?.lowercase().orEmpty()
        return if (raw in ALLOWED_EXTENSIONS) raw else DEFAULT_EXTENSION
    }

    private val ALLOWED_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp", "gif")
    private const val DEFAULT_EXTENSION = "png"
}
