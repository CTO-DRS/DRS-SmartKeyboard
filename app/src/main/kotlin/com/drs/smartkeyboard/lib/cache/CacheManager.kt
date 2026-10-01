/*
 * Copyright (C) 2021-2025 The DRS Smart Keyboard Project
 */

package com.drs.smartkeyboard.lib.cache

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.drs.smartkeyboard.app.ext.EditorAction
import com.drs.smartkeyboard.app.settings.advanced.Backup
import com.drs.smartkeyboard.appContext
import com.drs.smartkeyboard.ime.keyboard.KeyboardExtensionEditor
import com.drs.smartkeyboard.ime.theme.ThemeExtensionEditor
import com.drs.smartkeyboard.lib.NATIVE_NULLPTR
import com.drs.smartkeyboard.lib.ext.Extension
import com.drs.smartkeyboard.lib.ext.ExtensionDefaults
import com.drs.smartkeyboard.lib.ext.ExtensionEditor
import com.drs.smartkeyboard.lib.ext.ExtensionJsonConfig
import com.drs.smartkeyboard.lib.io.FileRegistry
import com.drs.smartkeyboard.lib.io.ZipUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.drs.lib.android.query
import org.drs.lib.android.readToFile
import org.drs.lib.kotlin.io.FsDir
import org.drs.lib.kotlin.io.FsFile
import org.drs.lib.kotlin.io.readJson
import org.drs.lib.kotlin.io.subDir
import org.drs.lib.kotlin.io.subFile
import java.io.Closeable
import java.io.File
import java.util.UUID

class CacheManager(context: Context) {
    companion object {
        private const val InputDirName = "input"
        private const val OutputDirName = "output"

        private const val ImporterDirName = "importer"
        private const val ExporterDirName = "exporter"
        private const val EditorDirName = "editor"
        // DRS M0.1 — dedicated root for keyboard-extension editor workspaces.
        // Sharing EditorDirName with the theme editor would mix both workspaces
        // under one directory (colliding subDir names like "ext"/"saver" would
        // only stay apart by uuid luck). An explicit, separate root keeps each
        // editor's staging honest.
        private const val KeyExtEditorDirName = "editor-keyboard"
        private const val BackupAndRestoreDirName = "backup-and-restore"

        const val LoadedDirName = "loaded"
    }

    private val appContext by context.appContext()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val importer = WorkspacesContainer(ImporterDirName) { ImporterWorkspace(it) }
    val exporter = WorkspacesContainer(ExporterDirName) { ExporterWorkspace(it) }
    val themeExtEditor = WorkspacesContainer(EditorDirName) { ExtEditorWorkspace<ThemeExtensionEditor>(it, EditorDirName) }
    // DRS M0.1 — the workspace resolves its directory from the EXPLICIT parent
    // dir name passed at construction (companion constants, so no self-reference
    // during container init), instead of hard-wiring the theme editor container
    // into the shared workspace class. Keyboard workspaces now live under their
    // own root: cache/editor-keyboard/<uuid>.
    val keyExtEditor = WorkspacesContainer(KeyExtEditorDirName) { ExtEditorWorkspace<KeyboardExtensionEditor>(it, KeyExtEditorDirName) }
    val backupAndRestore = WorkspacesContainer(BackupAndRestoreDirName) { BackupAndRestoreWorkspace(it) }

    fun readFromUriIntoCache(uri: Uri) = readFromUriIntoCache(listOf(uri))

    fun readFromUriIntoCache(uriList: List<Uri>): ImporterWorkspace {
        val contentResolver = appContext.contentResolver ?: error("Content resolver is null.")
        val workspace = ImporterWorkspace(uuid = UUID.randomUUID().toString()).also { it.mkdirs() }
        workspace.inputFileInfos = buildList {
            for (uri in uriList) {
                val info = contentResolver.query(uri)?.use { cursor ->
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                    cursor.moveToFirst()
                    // DRS v1.28.0 audit fix (HIGH): the provider-supplied
                    // DISPLAY_NAME was used unvalidated as the destination
                    // filename. A malicious app could hand out a name like
                    // "../../database/clipboard_history" and make the
                    // exported SEND/VIEW import flow write an arbitrary file
                    // anywhere inside the app sandbox before the (guarded)
                    // unzip stage even runs. Only the literal file name is
                    // kept — no path separators, no dot segments.
                    val displayName = sanitizeImportFileName(cursor.getString(nameIndex))
                    val file = workspace.inputDir.subFile(displayName)
                    contentResolver.readToFile(uri, file)
                    val ext = runCatching {
                        val extWorkingDir = workspace.outputDir.subDir(file.nameWithoutExtension)
                        ZipUtils.unzip(srcFile = file, dstDir = extWorkingDir)
                        val extJsonFile = extWorkingDir.subFile(ExtensionDefaults.MANIFEST_FILE_NAME)
                        extJsonFile.readJson<Extension>(ExtensionJsonConfig).also { it.workingDir = extWorkingDir }
                    }
                    FileInfo(
                        file = file,
                        mediaType = FileRegistry.guessMediaType(file, contentResolver.getType(uri)),
                        size = cursor.getLong(sizeIndex),
                        ext = ext.getOrNull(),
                    )
                } ?: error("Unable to fetch info about one or more resources to be imported.")
                add(info)
            }
        }
        importer.add(workspace)
        return workspace
    }

    /**
     * DRS v1.28.0 audit fix: reduces a provider-provided display name to a
     * safe single path segment. Falls back to "import.bin" when the name is
     * blank after sanitization so the write target is always deterministic.
     */
    private fun sanitizeImportFileName(rawName: String?): String {
        val name = rawName.orEmpty()
            .substringAfterLast('/')
            .substringAfterLast('\\')
            .trim()
        return if (name.isEmpty() || name == "." || name == "..") "import.bin" else name
    }

    open inner class WorkspacesContainer<T : Workspace> internal constructor(
        val dirName: String,
        val factory: (uuid: String) -> T,
    ) {
        private val workspacesGuard = Mutex(locked = false)
        private val workspaces = mutableListOf<T>()

        val dir: FsDir = appContext.cacheDir.subDir(dirName)

        fun new(uuid: String = UUID.randomUUID().toString()): T {
            return factory(uuid).also { it.mkdirs(); add(it) }
        }

        internal fun add(workspace: T) = scope.launch {
            workspacesGuard.withLock {
                workspaces.add(workspace)
            }
        }

        internal fun remove(workspace: T) = scope.launch {
            workspacesGuard.withLock {
                workspaces.remove(workspace)
            }
        }

        fun getWorkspaceByUuid(uuid: String) = runBlocking { getWorkspaceByUuidAsync(uuid).await() }

        fun getWorkspaceByUuidAsync(uuid: String): Deferred<T?> = scope.async {
            workspacesGuard.withLock {
                workspaces.find { it.uuid == uuid }
            }
        }
    }

    abstract inner class Workspace(val uuid: String) : Closeable {
        abstract val dir: FsDir

        open fun mkdirs() {
            dir.mkdirs()
        }

        fun isOpen() = dir.exists()

        fun isClosed() = !dir.exists()

        override fun close() {
            dir.deleteRecursively()
        }
    }

    inner class ImporterWorkspace(uuid: String) : Workspace(uuid) {
        override val dir: FsDir = importer.dir.subDir(uuid)

        val inputDir: FsDir = dir.subDir(InputDirName)
        val outputDir: FsDir = dir.subDir(OutputDirName)

        var inputFileInfos = emptyList<FileInfo>()

        override fun mkdirs() {
            super.mkdirs()
            inputDir.mkdirs()
            outputDir.mkdirs()
        }

        override fun close() {
            super.close()
            importer.remove(this)
        }
    }

    inner class ExporterWorkspace(uuid: String) : Workspace(uuid) {
        override val dir: FsDir = exporter.dir.subDir(uuid)
    }

    inner class ExtEditorWorkspace<T : ExtensionEditor>(uuid: String, parentDirName: String) : Workspace(uuid) {
        override val dir: FsDir = appContext.cacheDir.subDir(parentDirName).subDir(uuid)

        val extDir: FsDir = dir.subDir("ext")
        val saverDir: FsDir = dir.subDir("saver")

        var currentAction by mutableStateOf<EditorAction?>(null)
        var ext: Extension? = null
        var editor by mutableStateOf<T?>(null)
        var version by mutableIntStateOf(0)

        val isModified get() = version > 0

        override fun mkdirs() {
            super.mkdirs()
            extDir.mkdirs()
            saverDir.mkdirs()
        }

        inline fun <R> update(block: T.() -> R): R {
            // Method is designed to only be called when editor has been previously initialized
            val ret = block(editor!!)
            version++
            return ret
        }
    }

    inner class BackupAndRestoreWorkspace(uuid: String) : Workspace(uuid) {
        override val dir: FsDir = backupAndRestore.dir.subDir(uuid)

        val inputDir: FsDir = dir.subDir(InputDirName)
        val outputDir: FsDir = dir.subDir(OutputDirName)

        lateinit var zipFile: FsFile
        lateinit var metadata: Backup.Metadata
        var restoreWarningId: Int? = null
        var restoreErrorId: Int? = null

        override fun mkdirs() {
            super.mkdirs()
            inputDir.mkdirs()
            outputDir.mkdirs()
        }

        override fun close() {
            super.close()
            backupAndRestore.remove(this)
        }
    }

    data class FileInfo(
        val file: FsFile,
        val mediaType: String?,
        val size: Long,
        val ext: Extension?,
        var skipReason: Int = NATIVE_NULLPTR.toInt(),
    )
}
