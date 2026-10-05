/*
 * Copyright (C) 2025 The DRS Smart Keyboard Project
 */

package com.drs.smartkeyboard.ime.theme

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import com.drs.smartkeyboard.lib.devtools.flogError
import org.drs.lib.android.query
import org.drs.lib.android.read
import java.io.File
import java.security.MessageDigest

/**
 * DRS v2.8.0 «خلفيتك من ألبومك» — app-private storage of the single user
 * background image.
 *
 * Deterministic by construction:
 *  - the picked image is copied ONCE into `noBackupFilesDir/drs_background/`
 *    (never cloud, never shared storage, never backed up),
 *  - the stored name is the SHA-256 prefix of the CONTENT + extension, so
 *    re-picking the same picture is idempotent,
 *  - exactly one image is kept: importing a new one removes the previous file,
 *  - a 10 MB cap and a real decodability check (BitmapFactory bounds) run
 *    BEFORE anything is persisted — a corrupt or oversized file is rejected
 *    with an honest error instead of silently breaking the keyboard render.
 */
class DrsBackgroundImageStore(private val context: Context) {

    fun dir(): File = File(context.noBackupFilesDir, DIR_NAME)

    /**
     * Copies the picked image in a SINGLE bounded pass (hash + write together);
     * returns the stored file name on success. Errors carry a stable message key
     * (`TOO_LARGE`, `NOT_AN_IMAGE`) the UI maps to localized strings.
     */
    fun importFromUri(uri: Uri): Result<String> = runCatching {
        val baseDir = dir()
        baseDir.mkdirs()
        val tempFile = File.createTempFile("drs_bg_", ".tmp", baseDir)
        try {
            val digest = MessageDigest.getInstance("SHA-256")
            var byteCount = 0L
            // Pre-check via the asset descriptor, then the streaming cap as the
            // belt-and-braces for descriptors that report UNKNOWN_LENGTH.
            context.contentResolver.read(uri, maxSize = MAX_BYTES) { input ->
                tempFile.outputStream().use { out ->
                    val buffer = ByteArray(64 * 1024)
                    var r: Int
                    while (input.read(buffer).also { r = it } > 0) {
                        byteCount += r
                        check(byteCount <= MAX_BYTES) { "TOO_LARGE" }
                        digest.update(buffer, 0, r)
                        out.write(buffer, 0, r)
                    }
                }
            }
            check(byteCount > 0) { "NOT_AN_IMAGE" }
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(tempFile.absolutePath, options)
            check(options.outWidth > 0 && options.outHeight > 0) { "NOT_AN_IMAGE" }
            val digestHex = digest.digest().joinToString("") { "%02x".format(it) }
            val ext = DrsThemeBackground.extensionOf(queryDisplayName(uri))
            val storedName = "$digestHex.$ext"
            val target = File(baseDir, storedName)
            if (target.exists()) {
                tempFile.delete()
            } else if (!tempFile.renameTo(target)) {
                tempFile.copyTo(target, overwrite = true)
                tempFile.delete()
            }
            keepOnly(baseDir, target)
            storedName
        } catch (t: Throwable) {
            runCatching { tempFile.delete() }
            throw t
        }
    }.onFailure { t ->
        flogError { "Background image import failed: ${t.message}" }
    }

    /** Returns the currently stored image file name, or null when none. */
    fun currentName(): String? {
        val files = dir().listFiles { f -> f.isFile } ?: return null
        return files.mapNotNull { it.name.takeIf { n -> DrsThemeBackground.isSafeStoredName(n) } }
            .firstOrNull()
    }

    /** Deletes every stored image (used by the "remove image" action). */
    fun clear() {
        val files = dir().listFiles { f -> f.isFile } ?: return
        files.forEach { runCatching { it.delete() } }
    }

    /** Deletes previous image files, keeping only the freshly imported one. */
    private fun keepOnly(baseDir: File, keep: File) {
        baseDir.listFiles { f -> f.isFile }?.forEach { f ->
            if (f.canonicalPath != keep.canonicalPath) {
                runCatching { f.delete() }
            }
        }
    }

    private fun queryDisplayName(uri: Uri): String? = runCatching<String?> {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME))
            ?.use { cursor ->
                if (!cursor.moveToFirst()) return@use null
                val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx < 0) null else cursor.getString(idx)
            }
    }.getOrNull()

    companion object {
        const val DIR_NAME = "drs_background"
        const val MAX_BYTES: Long = 10_000_000L
    }
}
