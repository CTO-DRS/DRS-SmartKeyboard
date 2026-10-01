/*
 * Copyright (C) 2025 The DRS Smart Keyboard Project
 */

package com.drs.smartkeyboard.lib.ext

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.util.zip.ZipFile

/**
 * DRS M0.2 — the honest lifecycle of an INSTALLED extension archive,
 * operating on one module directory (e.g. files/ime/keyboard):
 *
 *  - DISABLE: a SIBLING marker file `<id>.flex.drs-disabled` next to the
 *    archive — never inside it, so the extension content and the index
 *    parser stay untouched. The index skips disabled archives but keeps
 *    exposing them through [ExtensionManager.allWithDisabledState].
 *
 *  - ARCHIVE: before every overwrite (import of the same id), the current
 *    archive moves to `archive/<id>/<version>.flex`. THE ARCHIVE NAME IS
 *    ALWAYS THE MANIFEST VERSION INSIDE THE PACKAGE BEING ARCHIVED — the
 *    label can never drift from the content (a caller-supplied version
 *    parameter would allow exactly that, so the API does not have one).
 *
 *  - ROLLBACK: restores the newest archived version (numeric ordering,
 *    part-wise dotted compare), archiving the current file first so the
 *    rollback itself stays reversible (disable → archive → rollback →
 *    enable is a true round trip).
 *
 *  - FAIL-CLOSED PATH SAFETY: a version string that could steer the
 *    archive path outside the module dir ("..", separators, empty) is
 *    rejected — the archive operation fails, nothing is moved.
 *
 * Pure JVM (java.io.File + java.util.zip) — pinned by DrsM02ExtensionLifecycleTest.
 */
class ExtensionLifecycle(private val moduleDir: File) {

    companion object {
        const val DISABLED_SUFFIX = ".drs-disabled"
        const val ARCHIVE_DIR_NAME = "archive"
        const val ARCHIVE_LIMIT = 3

        /** Dotted versions with safe file-name characters only. */
        private val SAFE_VERSION_REGEX = Regex("""^\d+(\.\d+){0,3}([-+][A-Za-z0-9._-]*)?$""")

        /** Numeric, part-wise compare of dotted version strings ("2.10" > "2.9"). */
        fun compareVersions(a: String, b: String): Int {
            val pa = a.split('.', '-', '+').filter { it.toLongOrNull() != null }.map { it.toLong() }
            val pb = b.split('.', '-', '+').filter { it.toLongOrNull() != null }.map { it.toLong() }
            for (i in 0 until maxOf(pa.size, pb.size)) {
                val va = pa.getOrElse(i) { 0L }
                val vb = pb.getOrElse(i) { 0L }
                if (va != vb) return va.compareTo(vb)
            }
            return a.compareTo(b)
        }
    }

    private fun flexFileOf(extId: String) = File(moduleDir, ExtensionDefaults.createFlexName(extId))

    private fun markerFileOf(extId: String) = File(moduleDir, ExtensionDefaults.createFlexName(extId) + DISABLED_SUFFIX)

    private fun archiveDirOf(extId: String) = File(File(moduleDir, ARCHIVE_DIR_NAME), extId)

    fun isDisabled(extId: String): Boolean = markerFileOf(extId).isFile

    /** Marks the installed archive as disabled. Fails when nothing is installed. */
    fun disable(extId: String): Result<Unit> = runCatching {
        val flex = flexFileOf(extId)
        check(flex.isFile) { "extension archive not installed: $extId" }
        markerFileOf(extId).writeText(extId)
    }

    /** Removes the disabled marker (idempotent — enabling twice is fine). */
    fun enable(extId: String): Result<Unit> = runCatching {
        markerFileOf(extId).delete()
    }

    /** Archived versions of the extension, newest first. */
    fun archivedVersions(extId: String): List<String> {
        val dir = archiveDirOf(extId)
        if (!dir.isDirectory) return emptyList()
        return dir.listFiles { f -> f.isFile && f.name.endsWith("." + ExtensionDefaults.FILE_EXTENSION) }
            .orEmpty()
            .map { it.name.removeSuffix("." + ExtensionDefaults.FILE_EXTENSION) }
            .sortedWith { a, b -> compareVersions(b, a) }
    }

    /**
     * Reads the version from the manifest INSIDE the flex archive — the
     * single naming authority for archives.
     */
    internal fun versionInsidePackage(flex: File): String? = runCatching {
        ZipFile(flex).use { zip ->
            val entry = zip.getEntry(ExtensionDefaults.MANIFEST_FILE_NAME) ?: return@use null
            val text = zip.getInputStream(entry).bufferedReader().use { it.readText() }
            Json.parseToJsonElement(text).jsonObject["meta"]?.jsonObject?.get("version")?.jsonPrimitive?.content
        }
    }.getOrNull()

    /**
     * Moves the currently installed archive of [extId] into
     * `archive/<extId>/<version-from-manifest>.flex` and prunes to
     * [ARCHIVE_LIMIT] (oldest deleted, numeric ordering). Fails
     * fail-closed when nothing is installed or the inner manifest has no
     * safe version.
     */
    fun archive(extId: String): Result<String> = runCatching {
        val flex = flexFileOf(extId)
        check(flex.isFile) { "extension archive not installed: $extId" }
        val version = versionInsidePackage(flex)
        check(!version.isNullOrBlank()) { "archive rejected: no version inside package manifest" }
        check(SAFE_VERSION_REGEX.matches(version)) { "archive rejected: unsafe version '$version'" }

        val archiveDir = archiveDirOf(extId)
        archiveDir.mkdirs()
        val dest = File(archiveDir, "$version." + ExtensionDefaults.FILE_EXTENSION)
        if (dest.exists()) dest.delete()
        check(flex.renameTo(dest)) { "archive move failed for $extId" }

        // Prune beyond the limit, oldest (numerically) first.
        val versions = archivedVersions(extId)
        for (old in versions.drop(ARCHIVE_LIMIT)) {
            File(archiveDir, "$old." + ExtensionDefaults.FILE_EXTENSION).delete()
        }
        version
    }

    /**
     * Restores the newest archived version into the module dir. A currently
     * installed archive is archived first (rollback stays reversible).
     * Returns the restored version.
     */
    fun rollback(extId: String): Result<String> = runCatching {
        val candidates = archivedVersions(extId)
        check(candidates.isNotEmpty()) { "no archived version to roll back to: $extId" }
        // The restore target is fixed BEFORE the current file is archived —
        // otherwise the just-archived current version would shadow it.
        val restoreVersion = candidates.first()
        if (flexFileOf(extId).isFile) {
            archive(extId).getOrThrow()
        }
        val source = File(archiveDirOf(extId), "$restoreVersion." + ExtensionDefaults.FILE_EXTENSION)
        val flex = flexFileOf(extId)
        check(source.renameTo(flex)) { "rollback move failed for $extId" }
        restoreVersion
    }
}
