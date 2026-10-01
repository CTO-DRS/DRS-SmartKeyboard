/*
 * Copyright (C) 2022-2025 The DRS Smart Keyboard Project
 */

package com.drs.smartkeyboard.ime.clipboard.provider

import android.content.Context
import android.net.Uri
import com.drs.smartkeyboard.lib.devtools.LogTopic
import com.drs.smartkeyboard.lib.devtools.flogDebug
import org.drs.lib.android.readToFile
import org.drs.lib.kotlin.io.FsFile
import org.drs.lib.kotlin.io.subFile

/**
 * Backend helper object which is used by [ClipboardMediaProvider] to serve content.
 */
object ClipboardFileStorage {
    const val CLIPBOARD_FILES_PATH = "clipboard_files"

    private val Context.clipboardFilesDir: FsFile
        get() = FsFile(this.noBackupFilesDir, "clipboard_files").also { it.mkdirs() }

    /**
     * Clones a content URI to internal storage.
     *
     * @param uri The URI
     *
     * @return The file's name which is a unique long
     */
    @Synchronized
    fun cloneUri(context: Context, uri: Uri): Long {
        val id = System.nanoTime()
        val file = context.clipboardFilesDir.subFile(id.toString())
        context.contentResolver.readToFile(uri, file)
        return id
    }

    /**
     * Deletes the file corresponding to an id.
     */
    fun deleteById(context: Context, id: Long) {
        flogDebug(LogTopic.CLIPBOARD) { "Cleaning up $id" }
        val file = context.clipboardFilesDir.subFile(id.toString())
        file.delete()
    }

    fun getFileForId(context: Context, id: Long): FsFile {
        return context.clipboardFilesDir.subFile(id.toString())
    }

    /**
     * DRS p8 (S-4): true when a backing file named [fileName] currently
     * exists in the clipboard files storage. The backup-restore flow uses
     * this to keep restored media rows honest — a history row is only
     * restored when its backing file really landed (missing file → skip
     * row, not a broken/crashing entry).
     */
    fun backingFileExists(context: Context, fileName: String): Boolean {
        return context.clipboardFilesDir.subFile(fileName).isFile
    }


    /**
     * Insert file from backup if not existing
     *
     * @param context the application context
     * @param file the file to be inserted
     */
    fun insertFileFromBackupIfNotExisting(context: Context, file: FsFile) {
        val target = context.clipboardFilesDir.subFile(file.name)
        // DRS security-port (p8-E2-4, ported from the audit line): both sides of
        // this copy are canonical-containment-checked — the destination name is
        // backup-supplied (via the caller), so a value that would land OUTSIDE
        // the private clipboard files directory is refused instead of written,
        // and the source must itself exist as a file inside a real directory.
        val container = context.clipboardFilesDir.canonicalPath + FsFile.separator
        if (!target.canonicalPath.startsWith(container)) {
            return
        }
        if (!target.isFile) {
            file.copyTo(target, overwrite = false)
        }
    }

    /**
     * Deletes all files from the clipboard subdirectory
     *
     * @param context the application context
     */
    fun resetClipboardFileStorage(context: Context) {
        context.clipboardFilesDir.listFiles()?.forEach {
            it.delete()
        }
    }

}
