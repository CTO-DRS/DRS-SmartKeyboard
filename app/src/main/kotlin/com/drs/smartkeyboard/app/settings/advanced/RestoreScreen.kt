/*
 * Copyright (C) 2021-2025 The DRS Smart Keyboard Project
 */

package com.drs.smartkeyboard.app.settings.advanced

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import com.drs.smartkeyboard.BuildConfig
import com.drs.smartkeyboard.R
import com.drs.smartkeyboard.app.DrsPreferenceModel
import com.drs.smartkeyboard.app.DrsPreferenceStore
import com.drs.smartkeyboard.app.LocalNavController
import com.drs.smartkeyboard.cacheManager
import com.drs.smartkeyboard.clipboardManager
import com.drs.smartkeyboard.ime.clipboard.ClipboardTextPolicy
import com.drs.smartkeyboard.ime.clipboard.provider.ClipboardFileStorage
import com.drs.smartkeyboard.ime.clipboard.provider.ClipboardItem
import com.drs.smartkeyboard.ime.clipboard.provider.ItemType
import com.drs.smartkeyboard.lib.cache.CacheManager
import com.drs.smartkeyboard.lib.compose.DrsScreen
import com.drs.smartkeyboard.lib.ext.ExtensionManager
import com.drs.smartkeyboard.lib.io.ZipUtils
import org.drs.jetpref.datastore.runtime.AndroidAppDataStorage
import org.drs.jetpref.datastore.runtime.FileBasedStorage
import org.drs.jetpref.datastore.runtime.ImportStrategy
import org.drs.jetpref.datastore.ui.Preference
import java.io.File
import java.io.FileNotFoundException
import java.text.DateFormat
import java.util.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.drs.lib.android.readToFile
import org.drs.lib.android.showLongToast
import org.drs.lib.android.showLongToastSync
import org.drs.lib.compose.DrsButtonBar
import org.drs.lib.compose.DrsCardDefaults
import org.drs.lib.compose.DrsOutlinedBox
import org.drs.lib.compose.DrsOutlinedButton
import org.drs.lib.compose.defaultDrsOutlinedBox
import org.drs.lib.compose.stringRes
import org.drs.lib.kotlin.io.FsDir
import org.drs.lib.kotlin.io.FsFile
import org.drs.lib.kotlin.io.deleteContentsRecursively
import org.drs.lib.kotlin.io.readJson
import org.drs.lib.kotlin.io.subDir
import org.drs.lib.kotlin.io.subFile
import com.drs.smartkeyboard.ime.dictionary.DictionaryManager
import com.drs.smartkeyboard.ime.dictionary.UserDictionaryEntry
import com.drs.smartkeyboard.ime.dictionary.UserDictionaryFormats
import com.drs.smartkeyboard.ime.voice.VoiceRecognizerMode

object Restore {
    const val MIN_VERSION_CODE = 1
    const val PACKAGE_NAME = "com.drs.smartkeyboard"
    const val BACKUP_ARCHIVE_FILE_NAME = "backup.zip"
}

@Composable
fun RestoreScreen() = DrsScreen {
    title = stringRes(R.string.backup_and_restore__restore__title)
    previewFieldVisible = false

    val navController = LocalNavController.current
    val context = LocalContext.current
    val cacheManager by context.cacheManager()
    val prefs by DrsPreferenceStore

    val restoreFilesSelector = remember { Backup.FilesSelector() }
    var importStrategy by remember { mutableStateOf(ImportStrategy.Merge) }
    // DRS v1.20.0: this used to be `remember { CoroutineScope(Dispatchers.Main) }` —
    // a scope that is never cancelled when the screen goes away, leaking every job
    // launched through it. rememberCoroutineScope is the correct compose-scoped
    // cancellation-aware equivalent.
    val restoreScope = rememberCoroutineScope()
    var restoreWorkspace by remember {
        mutableStateOf<CacheManager.BackupAndRestoreWorkspace?>(null)
    }

    val restoreDataFromFileSystemLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
        onResult = { uri ->
            if (uri == null) return@rememberLauncherForActivityResult
            restoreScope.launch {
                // DRS v1.20.0: reading the archive through the SAF and unzipping it
                // (potentially hundreds of MB of media) used to run synchronously on
                // the main thread inside this callback.
                // DRS security-port (p8-E2-2, ported from the audit line): the
                // freshly opened workspace is tracked so a mid-open failure (bad
                // zip, missing metadata) closes the extracted copy instead of
                // leaking it in cache until the next restore or eviction.
                var opened: CacheManager.BackupAndRestoreWorkspace? = null
                runCatching {
                    withContext(Dispatchers.IO) {
                        restoreWorkspace?.close()
                        restoreWorkspace = null
                        val workspace = cacheManager.backupAndRestore.new()
                        workspace.zipFile = workspace.inputDir.subFile(Restore.BACKUP_ARCHIVE_FILE_NAME)
                        context.contentResolver.readToFile(uri, workspace.zipFile)
                        ZipUtils.unzip(workspace.zipFile, workspace.outputDir)
                        workspace.metadata = try {
                            workspace.outputDir.subFile(Backup.METADATA_JSON_NAME).readJson()
                        } catch (e: FileNotFoundException) {
                            error("Invalid archive: either backup_metadata.json is missing or file is not a ZIP archive.")
                        }
                        workspace.restoreWarningId = when {
                            workspace.metadata.versionCode != BuildConfig.VERSION_CODE -> {
                                R.string.backup_and_restore__restore__metadata_warn_different_version
                            }
                            !workspace.metadata.packageName.startsWith(Restore.PACKAGE_NAME) -> {
                                R.string.backup_and_restore__restore__metadata_warn_different_vendor
                            }
                            else -> null
                        }
                        workspace.restoreErrorId = when {
                            workspace.metadata.packageName.isBlank() || workspace.metadata.versionCode < Restore.MIN_VERSION_CODE -> {
                                R.string.backup_and_restore__restore__metadata_error_invalid_metadata
                            }
                            else -> null
                        }
                        restoreWorkspace = workspace
                        opened = workspace
                    }
                }.onFailure { error ->
                    opened?.close()
                    context.showLongToastSync(
                        R.string.backup_and_restore__restore__failure,
                        "error_message" to error.localizedMessage,
                    )
                }
            }
        },
    )

    suspend fun performRestore() {
        // DRS v1.20.0: the whole restore (JSON parsing, recursive copies, database
        // writes) used to run on the main dispatcher — a jank/ANR source on large
        // archives. Pure heavy IO belongs on IO.
        withContext(Dispatchers.IO) {
            val workspace = restoreWorkspace!!
            val shouldReset = importStrategy == ImportStrategy.Erase
            if (restoreFilesSelector.jetprefDatastore) {
                val file = workspace.outputDir
                    .subDir(AndroidAppDataStorage.JETPREF_DIR_NAME)
                    .subFile("${DrsPreferenceModel.NAME}.${AndroidAppDataStorage.JETPREF_FILE_EXT}")
                if (file.exists()) {
                    val fileBasedStorage = FileBasedStorage(file.path)
                    DrsPreferenceStore.import(importStrategy, fileBasedStorage).getOrThrow()
                    // DRS p8 (S-6): the archive's jetpref file is imported
                    // wholesale, so a foreign backup could silently flip the
                    // privacy posture (cloud voice recognition, devtools
                    // overlays rendering surrounding editor text, clipboard
                    // edit notifications mirroring copied text). These three
                    // groups are deliberately NOT restoreable — they are
                    // forced back to their safe privacy defaults after every
                    // import; the user re-enables them by hand. All other
                    // prefs restore untouched.
                    prefs.voice.recognizerMode.set(VoiceRecognizerMode.ON_DEVICE_ONLY).getOrThrow()
                    prefs.devtools.enabled.set(false).getOrThrow()
                    prefs.clipboard.editNotificationEnabled.set(false).getOrThrow()
                }
            }
            val workspaceFilesDir = workspace.outputDir.subDir("files")
            if (restoreFilesSelector.imeKeyboard) {
                val srcDir = workspaceFilesDir.subDir(ExtensionManager.IME_KEYBOARD_PATH)
                val dstDir = context.filesDir.subDir(ExtensionManager.IME_KEYBOARD_PATH)
                if (shouldReset) {
                    dstDir.deleteContentsRecursively()
                }
                if (srcDir.exists()) {
                    srcDir.copyRecursively(dstDir, overwrite = true)
                }
            }
            if (restoreFilesSelector.imeTheme) {
                val srcDir = workspaceFilesDir.subDir(ExtensionManager.IME_THEME_PATH)
                val dstDir = context.filesDir.subDir(ExtensionManager.IME_THEME_PATH)
                if (shouldReset) {
                    dstDir.deleteContentsRecursively()
                }
                if (srcDir.exists()) {
                    srcDir.copyRecursively(dstDir, overwrite = true)
                }
            }
            // DRS v1.21.0: the learned personal words ride the archive —
            // merged back (erase-then-import on the erase strategy), so a
            // factory reset no longer loses the user dictionary.
            if (restoreFilesSelector.userDictionary) {
                val udictFile = workspace.outputDir.subFile(Backup.USER_DICTIONARY_TXT_NAME)
                if (udictFile.exists()) {
                    val dao = DictionaryManager.default().drsUserDictionaryDao()
                    if (dao != null) {
                        if (shouldReset) {
                            dao.deleteAll()
                        }
                        udictFile.bufferedReader().use { reader ->
                            var isFirstLine = true
                            reader.forEachLine { line ->
                                if (isFirstLine) {
                                    isFirstLine = false
                                    return@forEachLine
                                }
                                val entry = UserDictionaryFormats.parseEntryLine(line) ?: return@forEachLine
                                val existing = dao.queryExact(
                                    entry.word,
                                    entry.locale?.let { com.drs.smartkeyboard.lib.DrsLocale.fromTag(it) },
                                )
                                if (existing.isNotEmpty()) {
                                    dao.update(UserDictionaryEntry(existing[0].id, entry.word, entry.freq, entry.locale, entry.shortcut))
                                } else {
                                    dao.insert(UserDictionaryEntry(0, entry.word, entry.freq, entry.locale, entry.shortcut))
                                }
                            }
                        }
                    }
                }
            }
            val clipboardManager = context.clipboardManager().value
            if (shouldReset) {
                clipboardManager.clearFullHistory()
                ClipboardFileStorage.resetClipboardFileStorage(context)
            }

            if (restoreFilesSelector.provideClipboardItems()) {
                val clipboardFilesDir = workspace.outputDir.subDir("clipboard")

                if (restoreFilesSelector.clipboardTextItems) {
                    val clipboardItems = clipboardFilesDir.subFile(Backup.CLIPBOARD_TEXT_ITEMS_JSON_NAME)
                    if (clipboardItems.exists()) {
                        val clipboardItemsList = clipboardItems.readJson<List<ClipboardItem>>()
                        // DRS security-port (p8-E2-4, ported from the audit line):
                        // restored text goes through the SAME storage policy as live
                        // captures — the 50,000-char cap (ClipboardTextPolicy) — so an
                        // oversized JSON cannot bypass the app's own storage invariant.
                        clipboardManager.restoreHistory(
                            items = clipboardItemsList
                                .filter { it.type == ItemType.TEXT }
                                .map { item ->
                                    if (item.text != null) {
                                        item.copy(text = ClipboardTextPolicy.truncateForStorage(item.text))
                                    } else {
                                        item
                                    }
                                },
                        )
                    }
                }
                if (restoreFilesSelector.clipboardImageItems) {
                    val clipboardItems = clipboardFilesDir.subFile(Backup.CLIPBOARD_IMAGES_JSON_NAME)
                    if (clipboardItems.exists()) {
                        val clipboardItemsList = clipboardItems.readJson<List<ClipboardItem>>()
                        val restorableItems = mutableListOf<ClipboardItem>()
                        for (item in clipboardItemsList.filter { it.type == ItemType.IMAGE }) {
                            // DRS v1.20.0: one broken/missing media URI must not abort a
                            // restore midway and leave the user with partial state —
                            // resolve the file name defensively and skip broken items.
                            // DRS p8 (S-4): the name comes from the UNTRUSTED clipboard
                            // JSON inside the archive — sanitize it and require canonical
                            // containment inside the extracted clipboard_files dir before
                            // it can reach the filesystem. The row is only restored when
                            // its backing file really landed — missing file → skip row,
                            // not a broken entry.
                            val sourceFile = containedRestoreMediaFile(
                                extractedClipboardDir = clipboardFilesDir,
                                fileName = item.uri?.path?.substringAfterLast('/'),
                            ) ?: continue
                            runCatching {
                                ClipboardFileStorage.insertFileFromBackupIfNotExisting(context, sourceFile)
                            }
                            if (ClipboardFileStorage.backingFileExists(context, sourceFile.name)) {
                                restorableItems.add(item)
                            }
                        }
                        clipboardManager.restoreHistory(items = restorableItems)
                    }
                }
                if (restoreFilesSelector.clipboardVideoItems) {
                    val clipboardItems = clipboardFilesDir.subFile(Backup.CLIPBOARD_VIDEO_JSON_NAME)
                    if (clipboardItems.exists()) {
                        val clipboardItemsList = clipboardItems.readJson<List<ClipboardItem>>()
                        val restorableItems = mutableListOf<ClipboardItem>()
                        for (item in clipboardItemsList.filter { it.type == ItemType.VIDEO }) {
                            // DRS p8 (S-4): same sanitize + containment + honest
                            // row-only-if-file-landed contract as the image branch.
                            val sourceFile = containedRestoreMediaFile(
                                extractedClipboardDir = clipboardFilesDir,
                                fileName = item.uri?.path?.substringAfterLast('/'),
                            ) ?: continue
                            runCatching {
                                ClipboardFileStorage.insertFileFromBackupIfNotExisting(context, sourceFile)
                            }
                            if (ClipboardFileStorage.backingFileExists(context, sourceFile.name)) {
                                restorableItems.add(item)
                            }
                        }
                        clipboardManager.restoreHistory(items = restorableItems)
                    }
                }
            }
        }
    }

    bottomBar {
        DrsButtonBar {
            ButtonBarSpacer()
            ButtonBarTextButton(
                onClick = {
                    restoreWorkspace?.close()
                    navController.navigateUp()
                },
                text = stringRes(R.string.action__cancel),
            )
            ButtonBarButton(
                onClick = {
                    restoreScope.launch(Dispatchers.Main) {
                        try {
                            performRestore()
                            context.showLongToast(R.string.backup_and_restore__restore__success)
                            navController.navigateUp()
                        } catch (e: Throwable) {
                            e.printStackTrace()
                            context.showLongToast(
                                R.string.backup_and_restore__restore__failure,
                                "error_message" to e.localizedMessage,
                            )
                        } finally {
                            // DRS p8 (S-2): the restore workspace — the fully
                            // unzipped archive (clipboard text JSON,
                            // user_dictionary.txt, prefs file, media) — used
                            // to stay in cacheDir/backup-and-restore/<uuid>/
                            // for the whole session after a restore. Close it
                            // on the success AND the failure continuation
                            // (the cancel/re-pick paths already do).
                            restoreWorkspace?.close()
                            restoreWorkspace = null
                        }
                    }
                },
                text = stringRes(R.string.action__restore),
                enabled = restoreWorkspace != null && restoreWorkspace?.restoreErrorId == null,
            )
        }
    }

    content {
        DrsOutlinedBox(
            modifier = Modifier.defaultDrsOutlinedBox(),
            title = stringRes(R.string.backup_and_restore__restore__mode),
        ) {
            RadioListItem(
                onClick = {
                    importStrategy = ImportStrategy.Merge
                },
                selected = importStrategy == ImportStrategy.Merge,
                text = stringRes(R.string.backup_and_restore__restore__mode_merge),
            )
            RadioListItem(
                onClick = {
                    importStrategy = ImportStrategy.Erase
                },
                selected = importStrategy == ImportStrategy.Erase,
                text = stringRes(R.string.backup_and_restore__restore__mode_erase_and_overwrite),
            )
        }
        DrsOutlinedButton(
            onClick = {
                runCatching {
                    restoreDataFromFileSystemLauncher.launch("*/*")
                }.onFailure { error ->
                    context.showLongToastSync(
                        R.string.backup_and_restore__restore__failure,
                        "error_message" to error.localizedMessage,
                    )
                }
            },
            modifier = Modifier
                .padding(vertical = 16.dp)
                .align(Alignment.CenterHorizontally),
            text = stringRes(R.string.action__select_file),
        )
        val workspace = restoreWorkspace
        if (workspace == null) {
            Text(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(horizontal = 16.dp),
                text = stringRes(R.string.state__no_file_selected),
                fontStyle = FontStyle.Italic,
            )
        } else {
            DrsOutlinedBox(
                modifier = Modifier.defaultDrsOutlinedBox(),
                title = stringRes(R.string.backup_and_restore__restore__metadata),
            ) {
                Preference(
                    icon = Icons.Default.Code,
                    title = workspace.metadata.packageName,
                )
                Preference(
                    icon = Icons.Outlined.Info,
                    title = "${workspace.metadata.versionName} (${workspace.metadata.versionCode})",
                )
                Preference(
                    icon = Icons.Default.Schedule,
                    title = remember(workspace.metadata.timestamp) {
                        val formatter = DateFormat.getDateTimeInstance()
                        val calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
                        calendar.timeInMillis = workspace.metadata.timestamp
                        formatter.format(calendar.time)
                    },
                )
                if (workspace.restoreErrorId != null) {
                    Column(modifier = Modifier.padding(DrsCardDefaults.ContentPadding)) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(9.dp)
                                .padding(bottom = 8.dp)
                                .background(MaterialTheme.colorScheme.error.copy(alpha = 0.56f))
                        )
                        Text(
                            text = stringRes(workspace.restoreErrorId!!),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                            fontStyle = FontStyle.Italic,
                        )
                    }
                } else if (workspace.restoreWarningId != null) {
                    Column(modifier = Modifier.padding(DrsCardDefaults.ContentPadding)) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(9.dp)
                                .padding(bottom = 8.dp)
                                .background(LocalContentColor.current)
                        )
                        Text(
                            text = stringRes(workspace.restoreWarningId!!),
                            style = MaterialTheme.typography.bodyMedium,
                            color = LocalContentColor.current,
                            fontStyle = FontStyle.Italic,
                        )
                    }
                }
            }
            if (workspace.restoreErrorId == null) {
                BackupFilesSelector(
                    filesSelector = restoreFilesSelector,
                    title = stringRes(R.string.backup_and_restore__restore__files),
                )
            }
        }
    }
}

/**
 * DRS p8 (S-4): resolve the archive-side source file of a restored media
 * clipboard item defensively. [fileName] is the last segment of the
 * UNTRUSTED `item.uri.path` from the backup archive's clipboard JSON:
 * reject empty names, path separators and NUL bytes, and leading dots
 * (covers "." and ".." — the segment cannot contain a separator), then
 * require the canonical resolved path to stay inside the extracted
 * clipboard_files dir — the same canonical-containment pattern as
 * ExtensionEditScreen's containedSubFile. Returns null for every
 * rejected shape — the caller skips the item (no row, no crash).
 * DRS p9 (T-4): internal since then so the JVM regression suite can pin
 * the sanitize + containment contract directly.
 */
internal fun containedRestoreMediaFile(extractedClipboardDir: FsDir, fileName: String?): FsFile? {
    if (fileName.isNullOrEmpty()) return null
    if (fileName.contains('/') || fileName.contains('\\') || fileName.contains('\u0000')) return null
    if (fileName.startsWith(".")) return null
    val storageRoot = extractedClipboardDir
        .subFile(ClipboardFileStorage.CLIPBOARD_FILES_PATH)
        .canonicalFile
    val candidate = extractedClipboardDir
        .subFile(relPath = "${ClipboardFileStorage.CLIPBOARD_FILES_PATH}/$fileName")
        .canonicalFile
    return if (candidate.path.startsWith(storageRoot.path + File.separator)) candidate else null
}
