/*
 * Copyright (C) 2025-2026 The DRS Smart Keyboard Project
 */

package com.drs.smartkeyboard.ime.clipboard

import android.content.ContentValues
import android.content.Context
import android.os.Environment
import android.provider.MediaStore
import java.io.File

/**
 * DRS v1.9.0 — the "save clip as file" engine behind the clipboard panel
 * dialog. Text is written with UTF-8, no storage permission is ever
 * requested:
 *
 * - Android 10+ (API 29): a MediaStore contribution under
 *   `Downloads/DRS Keyboard`, visible to the user and other apps.
 * - Android 8-9 (API 26-28): the app's private `files/clips` directory
 *   (scoped-storage-free), with an automatic ` (n)` collision suffix.
 *
 * The file name must be normalized through [ClipFileNamer.sanitize] first —
 * the saver trusts its caller for the name and owns only the writing.
 */
object ClipFileSaver {
    const val DOWNLOAD_SUBDIR: String = "DRS Keyboard"
    private const val PRIVATE_DIR: String = "clips"

    sealed interface Result {
        /** Saved into the public Downloads tree (MediaStore, Android 10+). */
        data class PublicDownloads(val displayName: String) : Result

        /** Saved into the app's private files dir (Android 8-9 fallback). */
        data class PrivateFiles(val file: File) : Result

        /** The write failed — nothing was persisted. */
        data object Failed : Result
    }

    fun save(context: Context, text: String, fileName: String): Result {
        return try {
            if (org.drs.lib.android.AndroidVersion.ATLEAST_API29_Q) {
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    put(MediaStore.MediaColumns.MIME_TYPE, "text/plain")
                    put(
                        MediaStore.MediaColumns.RELATIVE_PATH,
                        Environment.DIRECTORY_DOWNLOADS + "/" + DOWNLOAD_SUBDIR,
                    )
                }
                val uri = context.contentResolver
                    .insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                    ?: return Result.Failed
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    out.write(text.toByteArray(Charsets.UTF_8))
                    out.flush()
                } ?: return Result.Failed
                Result.PublicDownloads(fileName)
            } else {
                val dir = File(context.filesDir, PRIVATE_DIR).apply { mkdirs() }
                var candidate = File(dir, fileName)
                var n = 1
                val bare = fileName.removeSuffix(ClipFileNamer.EXTENSION)
                while (candidate.exists()) {
                    candidate = File(dir, "$bare ($n)${ClipFileNamer.EXTENSION}")
                    n += 1
                }
                candidate.writeText(text, Charsets.UTF_8)
                Result.PrivateFiles(candidate)
            }
        } catch (_: Throwable) {
            Result.Failed
        }
    }
}
