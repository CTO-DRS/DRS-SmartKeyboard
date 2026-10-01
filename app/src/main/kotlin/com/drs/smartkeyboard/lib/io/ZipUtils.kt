/*
 * Copyright (C) 2021-2025 The DRS Smart Keyboard Project
 */

package com.drs.smartkeyboard.lib.io

import android.content.Context
import android.net.Uri
import com.drs.smartkeyboard.lib.devtools.flogWarning
import org.drs.lib.android.copyRecursively
import org.drs.lib.android.write
import org.drs.lib.kotlin.io.FsDir
import org.drs.lib.kotlin.io.FsFile
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

object ZipUtils {
    // DRS p8 (S-3): zip-bomb caps, enforced on the bytes ACTUALLY written.
    // The old guard trusted only the zip header's DECLARED uncompressed size
    // (a lying entry could declare 10 bytes and stream gigabytes) and nothing
    // bounded the archive total. MAX_ENTRY_BYTES keeps the historical
    // per-entry limit; MAX_TOTAL_BYTES adds the missing aggregate cap — same
    // order of magnitude, with headroom so legitimate multi-media backups
    // still extract.
    private const val MAX_ENTRY_BYTES = 100_000_000L
    private const val MAX_TOTAL_BYTES = 300_000_000L
    private const val UNZIP_COPY_BUFFER_SIZE = 64 * 1024

    fun readFileFromArchive(context: Context, zipRef: DrsRef, relPath: String) = runCatching<String> {
        when {
            zipRef.isAssets -> {
                zipRef.subRef(relPath).loadTextAsset(context).getOrThrow()
            }
            zipRef.isCache || zipRef.isInternal -> {
                val flexHandle = FsFile(zipRef.absolutePath(context))
                check(flexHandle.isFile) { "Given ref $zipRef is not a file!" }
                var fileContents: String? = null
                ZipFile(flexHandle).use { flexFile ->
                    flexFile.getEntry(relPath)?.let { flexEntry ->
                        fileContents = flexFile.getInputStream(flexEntry).bufferedReader().use { it.readText() }
                    }
                }
                fileContents ?: error("Failed to load requested file $relPath")
            }
            else -> error("Unsupported source!")
        }
    }

    fun zip(context: Context, srcRef: DrsRef, dstRef: DrsRef) =
        zip(context, FsDir(srcRef.absolutePath(context)), dstRef)

    fun zip(context: Context, srcDir: FsDir, dstRef: DrsRef) = runCatching {
        check(srcDir.exists() && srcDir.isDirectory) { "Cannot zip standalone file." }
        when {
            dstRef.isCache || dstRef.isInternal -> {
                val flexFile = FsFile(dstRef.absolutePath(context))
                flexFile.parentFile?.mkdirs()
                // DRS p7 (E3-2) — atomic publish: the installed .flex is the only on-disk
                // copy of an extension; the old delete+direct-write window left it briefly
                // missing/truncated (the FileObserver DELETE event fired mid-window and the
                // index dropped the extension; a crash mid-write corrupted it with no
                // backup). Zip into a sibling <name>.flex.tmp and atomically rename; on
                // rename failure fall back to the legacy direct write (flogWarning). The
                // tmp name never matches the index' `.flex` filter.
                val tmpFile = FsFile(flexFile.parentFile, "${flexFile.name}.tmp")
                runCatching {
                    FileOutputStream(tmpFile).use { fileOut ->
                        ZipOutputStream(fileOut).use { zipOut ->
                            zip(srcDir, zipOut, "")
                        }
                    }
                    check(tmpFile.renameTo(flexFile)) { "renameTo(${flexFile.name}) failed" }
                }.onFailure { tmpError ->
                    flogWarning { "Atomic .flex publish failed (${tmpError.message}); falling back to direct write" }
                    tmpFile.delete()
                    flexFile.delete()
                    FileOutputStream(flexFile).use { fileOut ->
                        ZipOutputStream(fileOut).use { zipOut ->
                            zip(srcDir, zipOut, "")
                        }
                    }
                }
            }
            else -> error("Unsupported destination!")
        }
    }

    fun zip(srcDir: FsDir, dstFile: FsFile) {
        check(srcDir.exists() && srcDir.isDirectory) { "Cannot zip standalone file." }
        dstFile.parentFile?.mkdirs()
        dstFile.delete()
        FileOutputStream(dstFile).use { outStream ->
            ZipOutputStream(outStream).use { zipOut ->
                zip(srcDir, zipOut, "")
            }
        }
    }

    /**
     * DRS p7 (E3-2) — atomically publishes [src] onto [dst]: copies to a sibling
     * `<dst.name>.tmp`, then renames onto the target. If the rename fails, the legacy
     * direct copy runs as a fallback (logged via [flogWarning]); the tmp file is
     * always cleaned up. Publication sites for installed extension archives must use
     * this instead of `copyTo(overwrite = true)` so the only on-disk copy of an
     * extension is never briefly missing or truncated during the publish.
     */
    fun publishAtomic(src: FsFile, dst: FsFile) {
        dst.parentFile?.mkdirs()
        val tmpFile = FsFile(dst.parentFile, "${dst.name}.tmp")
        try {
            src.copyTo(tmpFile, overwrite = true)
            if (tmpFile.renameTo(dst)) {
                return
            }
            flogWarning { "Atomic rename onto $dst failed; falling back to direct copy" }
        } finally {
            tmpFile.delete()
        }
        src.copyTo(dst, overwrite = true)
    }

    fun zip(context: Context, srcDir: FsDir, uri: Uri) = runCatching {
        check(srcDir.exists() && srcDir.isDirectory) { "Cannot zip standalone file." }
        context.contentResolver.write(uri) { fileOut ->
            ZipOutputStream(fileOut).use { zipOut ->
                zip(srcDir, zipOut, "")
            }
        }
    }

    internal fun zip(srcDir: FsDir, zipOut: ZipOutputStream, base: String) {
        val dir = FsDir(srcDir, base)
        for (file in dir.listFiles() ?: arrayOf()) {
            val path = if (base.isBlank()) file.name else "$base/${file.name}"
            if (file.isDirectory) {
                zipOut.putNextEntry(ZipEntry("$path/"))
                zipOut.closeEntry()
                zip(srcDir, zipOut, path)
            } else {
                zipOut.putNextEntry(ZipEntry(path))
                file.inputStream().use { it.copyTo(zipOut) }
                zipOut.closeEntry()
            }
        }
    }

    fun unzip(context: Context, srcRef: DrsRef, dstRef: DrsRef) =
        unzip(context, srcRef, FsDir(dstRef.absolutePath(context)))

    fun unzip(context: Context, srcRef: DrsRef, dstDir: FsFile) = runCatching {
        check(dstDir.exists() && dstDir.isDirectory) { "Cannot unzip into file." }
        dstDir.mkdirs()
        when {
            srcRef.isAssets -> {
                context.assets.copyRecursively(srcRef.relativePath.removeSuffix("/"), dstDir)
            }
            srcRef.isCache || srcRef.isInternal -> {
                val flexHandle = FsFile(srcRef.absolutePath(context))
                unzip(srcFile = flexHandle, dstDir = dstDir)
            }
            else -> error("Unsupported source!")
        }
    }

    /**
     * Unzips a given Zip file to the destination directory.
     *
     * DRS p9 (T-2): the p8 (S-3) zip-bomb caps became defaulted parameters so
     * the JVM regression suite can exercise the skip/abort boundaries with tiny
     * budgets; every existing caller keeps the production defaults unchanged.
     *
     * @param srcFile The source Zip file handle.
     * @param dstDir The destination directory where the [srcFile] contents should be unzipped to.
     * @param maxEntryBytes The per-entry extraction cap in bytes (default [MAX_ENTRY_BYTES]).
     * @param maxTotalBytes The archive-wide extraction cap in bytes (default [MAX_TOTAL_BYTES]).
     *
     * @throws IllegalArgumentException If the given [srcFile] is not existing on the file system or if it points to
     *  a directory instead.
     * @throws java.lang.SecurityException If the current file system does not permit an action.
     * @throws java.util.zip.ZipException If a Zip format error has occurred.
     * @throws java.io.IOException If an I/O error has occurred.
     */
    fun unzip(
        srcFile: FsFile,
        dstDir: FsDir,
        maxEntryBytes: Long = MAX_ENTRY_BYTES,
        maxTotalBytes: Long = MAX_TOTAL_BYTES,
    ) {
        require(srcFile.exists() && srcFile.isFile) { "Given src file `$srcFile` is not valid or a directory." }
        dstDir.mkdirs()
        // DRS p8 (S-3): aggregate extracted-bytes budget for the WHOLE archive.
        var totalExtractedBytes = 0L
        ZipFile(srcFile).use { flexFile ->
            val flexEntries = flexFile.entries()
            while (flexEntries.hasMoreElements()) {
                val flexEntry = flexEntries.nextElement()
                if (flexEntry.name.length > 255) {
                    continue
                }
                val flexEntryFile = FsFile(dstDir, flexEntry.name)
                val canonicalDestinationDirPath = dstDir.canonicalPath
                val canonicalDestinationFilePath = flexEntryFile.canonicalPath
                if (canonicalDestinationFilePath.length > 1023) {
                    continue
                }
                if (!canonicalDestinationFilePath.startsWith(canonicalDestinationDirPath + FsFile.separator)) {
                    continue
                }
                if (flexEntry.isDirectory) {
                    flexEntryFile.mkdir()
                } else {
                    totalExtractedBytes += flexFile.copy(
                        srcEntry = flexEntry,
                        dstFile = flexEntryFile,
                        totalByteBudget = maxTotalBytes - totalExtractedBytes,
                        maxEntryBytes = maxEntryBytes,
                    )
                }
            }
        }
    }

    /**
     * DRS p8 (S-3): bounded entry copy. The old guard checked only the
     * DECLARED `srcEntry.size` while `copyTo` streamed unbounded bytes — a
     * lying header (declares 10 bytes, streams gigabytes) could fill the
     * disk, and no aggregate cap existed. Now: an honestly-declared
     * oversized entry is skipped (legacy behavior); the bytes actually
     * written are counted in the copy loop and abort mid-stream against
     * [maxEntryBytes] and the archive-wide [totalByteBudget]; the
     * partially written file is removed on abort. Violations throw — every
     * caller already surfaces this through its existing failure path
     * (runCatching / try-catch).
     * DRS p9 (T-2): the per-entry cap is a parameter (threaded from
     * [unzip]) instead of the [MAX_ENTRY_BYTES] const.
     *
     * @return the number of bytes actually written for this entry
     */
    private fun ZipFile.copy(
        srcEntry: ZipEntry,
        dstFile: FsFile,
        totalByteBudget: Long,
        maxEntryBytes: Long,
    ): Long {
        if (srcEntry.size > maxEntryBytes) {
            return 0
        }
        if (totalByteBudget <= 0L) {
            error("Zip extraction aborted: total extraction cap of $MAX_TOTAL_BYTES bytes exceeded")
        }
        var written = 0L
        try {
            dstFile.outputStream().use { outStream ->
                this.getInputStream(srcEntry).use { inStream ->
                    val buffer = ByteArray(UNZIP_COPY_BUFFER_SIZE)
                    while (true) {
                        val read = inStream.read(buffer)
                        if (read < 0) break
                        written += read
                        if (written > maxEntryBytes) {
                            error("Zip entry `${srcEntry.name}` exceeds the $maxEntryBytes-byte per-entry extraction cap")
                        }
                        if (written > totalByteBudget) {
                            error("Zip extraction aborted: total extraction cap of $MAX_TOTAL_BYTES bytes exceeded")
                        }
                        outStream.write(buffer, 0, read)
                    }
                }
            }
        } catch (t: Throwable) {
            dstFile.delete()
            throw t
        }
        return written
    }
}
