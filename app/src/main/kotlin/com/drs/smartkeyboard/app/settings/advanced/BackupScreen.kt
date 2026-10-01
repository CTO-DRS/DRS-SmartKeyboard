/*
 * Copyright (C) 2021-2025 The DRS Smart Keyboard Project
 */

package com.drs.smartkeyboard.app.settings.advanced

import android.content.ContentUris
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Checkbox
import androidx.compose.material3.RadioButton
import androidx.compose.material3.TriStateCheckbox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.dp
import androidx.core.app.ShareCompat
import androidx.core.content.FileProvider
import com.drs.smartkeyboard.BuildConfig
import com.drs.smartkeyboard.R
import com.drs.smartkeyboard.app.DrsPreferenceModel
import com.drs.smartkeyboard.app.DrsPreferenceStore
import com.drs.smartkeyboard.app.LocalNavController
import com.drs.smartkeyboard.cacheManager
import com.drs.smartkeyboard.clipboardManager
import com.drs.smartkeyboard.ime.clipboard.provider.ClipboardFileStorage
import com.drs.smartkeyboard.ime.clipboard.provider.ItemType
import com.drs.smartkeyboard.lib.cache.CacheManager
import com.drs.smartkeyboard.lib.compose.DrsScreen
import com.drs.smartkeyboard.lib.devtools.flogError
import com.drs.smartkeyboard.lib.ext.ExtensionManager
import com.drs.smartkeyboard.lib.io.FileRegistry
import com.drs.smartkeyboard.lib.io.ZipUtils
import org.drs.jetpref.datastore.runtime.AndroidAppDataStorage
import org.drs.jetpref.datastore.runtime.FileBasedStorage
import org.drs.jetpref.material.ui.JetPrefListItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.drs.lib.android.showLongToast
import org.drs.lib.android.showLongToastSync
import org.drs.lib.android.writeFromFile
import org.drs.lib.compose.DrsButtonBar
import org.drs.lib.compose.DrsOutlinedBox
import org.drs.lib.compose.defaultDrsOutlinedBox
import org.drs.lib.compose.rippleClickable
import org.drs.lib.compose.stringRes
import org.drs.lib.kotlin.io.subDir
import org.drs.lib.kotlin.io.subFile
import org.drs.lib.kotlin.io.writeJson
import com.drs.smartkeyboard.ime.dictionary.DictionaryManager
import com.drs.smartkeyboard.ime.dictionary.UserDictionaryFormats

object Backup {
    const val FILE_PROVIDER_AUTHORITY = "${BuildConfig.APPLICATION_ID}.provider.file"
    const val METADATA_JSON_NAME = "backup_metadata.json"
    const val CLIPBOARD_TEXT_ITEMS_JSON_NAME = "clipboard_text_items.json"
    const val CLIPBOARD_IMAGES_JSON_NAME = "clipboard_images.json"
    const val CLIPBOARD_VIDEO_JSON_NAME = "clipboard_video.json"
    // DRS v1.21.0: the personal user dictionary travels in the archive —
    // a factory reset used to silently lose every learned word.
    const val USER_DICTIONARY_TXT_NAME = "user_dictionary.txt"

    fun defaultFileName(metadata: Metadata): String {
        return "backup_${metadata.packageName}_${metadata.versionCode}_${metadata.timestamp}.zip"
    }

    enum class Destination {
        FILE_SYS,
        SHARE_INTENT;
    }

    class FilesSelector {
        var jetprefDatastore by mutableStateOf(true)
        var imeKeyboard by mutableStateOf(true)
        var imeTheme by mutableStateOf(true)
        var userDictionary by mutableStateOf(true)
        var clipboardTextItems by mutableStateOf(false)
        var clipboardImageItems by mutableStateOf(false)
        var clipboardVideoItems by mutableStateOf(false)

        private var _clipboardData: MutableState<ToggleableState> = mutableStateOf(ToggleableState.Off)
        val clipboardData: State<ToggleableState> = _clipboardData

        fun updateCheckboxState() {
            val newValue = if (
                !clipboardVideoItems && !clipboardImageItems && !clipboardTextItems
            ) {
                ToggleableState.Off
            } else if (
                clipboardVideoItems && clipboardImageItems && clipboardTextItems
            ) {
                ToggleableState.On
            } else {
                ToggleableState.Indeterminate
            }
            _clipboardData.value = newValue
        }

        fun provideClipboardItems(): Boolean {
            return clipboardTextItems || clipboardImageItems || clipboardVideoItems
        }

        fun atLeastOneSelected(): Boolean {
            return jetprefDatastore || imeKeyboard || imeTheme || userDictionary ||
                clipboardTextItems || clipboardImageItems || clipboardVideoItems
        }
    }

    @Serializable
    data class Metadata(
        @SerialName("package")
        val packageName: String,
        val versionCode: Int,
        val versionName: String,
        val timestamp: Long,
    )
}

@Composable
fun BackupScreen() = DrsScreen {
    title = stringRes(R.string.backup_and_restore__back_up__title)
    previewFieldVisible = false

    val navController = LocalNavController.current
    val context = LocalContext.current
    val cacheManager by context.cacheManager()
    val scope = rememberCoroutineScope()

    var backupDestination by remember { mutableStateOf(Backup.Destination.FILE_SYS) }
    val backupFilesSelector = remember { Backup.FilesSelector() }
    // DRS v1.20.0: this workspace handle used to be a plain local `var`, so a config
    // change or process death while the SAF picker was open lost it and the launcher
    // callbacks crashed on `backupWorkspace!!` — reported to the user as a bogus
    // "backup failed". It must survive recomposition like RestoreScreen's does.
    var backupWorkspace by remember { mutableStateOf<CacheManager.BackupAndRestoreWorkspace?>(null) }

    val backUpToFileSystemLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/zip"),
        onResult = { uri ->
            if (uri == null) {
                // User can modify checkboxes between cancellation and second
                // trigger, so we make sure to clear out the previous workspace
                backupWorkspace?.close()
                backupWorkspace = null
                return@rememberLauncherForActivityResult
            }
            val workspace = backupWorkspace
            if (workspace == null) {
                context.showLongToastSync(R.string.backup_and_restore__back_up__failure, "error_message" to "")
                return@rememberLauncherForActivityResult
            }
            scope.launch {
                // DRS v1.20.0: writing the archive through the SAF and closing the
                // workspace are file IO — they used to run on the main thread here.
                runCatching {
                    withContext(Dispatchers.IO) {
                        context.contentResolver.writeFromFile(uri, workspace.zipFile)
                        workspace.close()
                    }
                }.onSuccess {
                    context.showLongToastSync(R.string.backup_and_restore__back_up__success)
                    navController.popBackStack()
                }.onFailure { error ->
                    flogError { error.stackTraceToString() }
                    context.showLongToastSync(R.string.backup_and_restore__back_up__failure, "error_message" to error.message)
                    backupWorkspace = null
                }
            }
        },
    )

    suspend fun prepareBackupWorkspace() {
        // DRS v1.20.0: zipping and copying the whole backup workspace (potentially
        // hundreds of MB of clipboard media) used to run on the main dispatcher —
        // a guaranteed jank/ANR source. Pure file IO belongs on IO.
        withContext(Dispatchers.IO) {
            val workspace = cacheManager.backupAndRestore.new()
            if (backupFilesSelector.jetprefDatastore) {
                val fileBasedStorage = workspace.inputDir
                    .subDir(AndroidAppDataStorage.JETPREF_DIR_NAME)
                    .subFile("${DrsPreferenceModel.NAME}.${AndroidAppDataStorage.JETPREF_FILE_EXT}")
                    .let { FileBasedStorage(it.path) }
                DrsPreferenceStore.export(fileBasedStorage).getOrThrow()
            }
            val workspaceFilesDir = workspace.inputDir.subDir("files")
            if (backupFilesSelector.imeKeyboard) {
                context.filesDir.subDir(ExtensionManager.IME_KEYBOARD_PATH).let { dir ->
                    dir.copyRecursively(workspaceFilesDir.subDir(ExtensionManager.IME_KEYBOARD_PATH))
                }
            }
            if (backupFilesSelector.imeTheme) {
                context.filesDir.subDir(ExtensionManager.IME_THEME_PATH).let { dir ->
                    dir.copyRecursively(workspaceFilesDir.subDir(ExtensionManager.IME_THEME_PATH))
                }
            }

            // DRS v1.21.0: the personal user dictionary travels in the
            // archive (combined-list text format) — a restore/factory
            // reset used to silently lose every learned word.
            if (backupFilesSelector.userDictionary) {
                workspace.inputDir
                    .subFile(Backup.USER_DICTIONARY_TXT_NAME)
                    .bufferedWriter()
                    .use { writer ->
                        DictionaryManager.default().drsUserDictionaryDao()
                            ?.let { dao ->
                                // The DAO lives behind the DB interface; write through a tiny adapter.
                                writer.append("dictionary=user_dictionary;version=1\n")
                                for (entry in dao.queryAll()) {
                                    writer.append(UserDictionaryFormats.entryLine(entry)).append('\n')
                                }
                            }
                    }
            }

            if (backupFilesSelector.provideClipboardItems()) {
                val clipboardManager by context.clipboardManager()
                val clipboardHistory = clipboardManager.currentHistory.all
                val clipboardFilesDir = workspace.inputDir.subDir("clipboard")
                clipboardFilesDir.mkdir()
                if (backupFilesSelector.clipboardTextItems) {
                    clipboardFilesDir.subFile(Backup.CLIPBOARD_TEXT_ITEMS_JSON_NAME)
                        .writeJson(clipboardHistory.filter { it.type == ItemType.TEXT })
                }
                if (backupFilesSelector.clipboardImageItems) {
                    clipboardFilesDir.subFile(Backup.CLIPBOARD_IMAGES_JSON_NAME)
                        .writeJson(clipboardHistory.filter { it.type == ItemType.IMAGE })
                    for (item in clipboardHistory.filter { it.type == ItemType.IMAGE }) {
                        // DRS v1.20.0: one broken/missing media URI must not abort the
                        // whole backup — skip the broken item and keep the archive.
                        val id = item.uri?.let { runCatching { ContentUris.parseId(it) }.getOrNull() } ?: continue
                        runCatching {
                            ClipboardFileStorage.getFileForId(context, id).copyTo(
                                clipboardFilesDir.subFile("${ClipboardFileStorage.CLIPBOARD_FILES_PATH}/$id")
                            )
                        }
                    }
                }
                if (backupFilesSelector.clipboardVideoItems) {
                    clipboardFilesDir.subFile(Backup.CLIPBOARD_VIDEO_JSON_NAME)
                        .writeJson(clipboardHistory.filter { it.type == ItemType.VIDEO })
                    for (item in clipboardHistory.filter { it.type == ItemType.VIDEO }) {
                        val id = item.uri?.let { runCatching { ContentUris.parseId(it) }.getOrNull() } ?: continue
                        runCatching {
                            ClipboardFileStorage.getFileForId(context, id).copyTo(
                                clipboardFilesDir.subFile("${ClipboardFileStorage.CLIPBOARD_FILES_PATH}/$id")
                            )
                        }
                    }
                }
            }
            workspace.metadata = Backup.Metadata(
                packageName = BuildConfig.APPLICATION_ID,
                versionCode = BuildConfig.VERSION_CODE,
                versionName = BuildConfig.VERSION_NAME,
                timestamp = System.currentTimeMillis(),
            )
            workspace.inputDir.subFile(Backup.METADATA_JSON_NAME).writeJson(workspace.metadata)
            workspace.zipFile = workspace.outputDir.subFile(Backup.defaultFileName(workspace.metadata))
            ZipUtils.zip(workspace.inputDir, workspace.zipFile)
            backupWorkspace = workspace
        }
    }

    suspend fun prepareAndPerformBackup() {
        runCatching {
            if (backupWorkspace == null || backupWorkspace!!.isClosed()) {
                prepareBackupWorkspace()
            }
            when (backupDestination) {
                Backup.Destination.FILE_SYS -> {
                    backUpToFileSystemLauncher.launch(backupWorkspace!!.zipFile.name)
                }

                Backup.Destination.SHARE_INTENT -> {
                    val uri =
                        FileProvider.getUriForFile(context, Backup.FILE_PROVIDER_AUTHORITY, backupWorkspace!!.zipFile)
                    val shareIntent = ShareCompat.IntentBuilder(context)
                        .setStream(uri)
                        .setType(FileRegistry.BackupArchive.mediaType)
                        .createChooserIntent()
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    context.startActivity(shareIntent)
                }
            }
        }.onFailure { error ->
            flogError { error.stackTraceToString() }
            context.showLongToast(R.string.backup_and_restore__back_up__failure, "error_message" to error.message)
            backupWorkspace = null
        }
    }

    bottomBar {
        DrsButtonBar {
            ButtonBarSpacer()
            ButtonBarTextButton(
                onClick = {
                    backupWorkspace?.close()
                    navController.popBackStack()
                },
                text = stringRes(R.string.action__cancel),
            )
            ButtonBarButton(
                onClick = {
                    scope.launch { prepareAndPerformBackup() }
                },
                text = stringRes(R.string.action__back_up),
                enabled = backupFilesSelector.atLeastOneSelected(),
            )
        }
    }

    content {
        DrsOutlinedBox(
            modifier = Modifier.defaultDrsOutlinedBox(),
            title = stringRes(R.string.backup_and_restore__back_up__destination),
        ) {
            RadioListItem(
                onClick = {
                    backupDestination = Backup.Destination.FILE_SYS
                },
                selected = backupDestination == Backup.Destination.FILE_SYS,
                text = stringRes(R.string.backup_and_restore__back_up__destination_file_sys),
            )
            RadioListItem(
                onClick = {
                    backupDestination = Backup.Destination.SHARE_INTENT
                },
                selected = backupDestination == Backup.Destination.SHARE_INTENT,
                text = stringRes(R.string.backup_and_restore__back_up__destination_share_intent),
            )
        }
        BackupFilesSelector(
            filesSelector = backupFilesSelector,
            title = stringRes(R.string.backup_and_restore__back_up__files),
        )
    }
}

@Composable
internal fun BackupFilesSelector(
    modifier: Modifier = Modifier,
    filesSelector: Backup.FilesSelector,
    title: String,
) {
    DrsOutlinedBox(
        modifier = modifier.defaultDrsOutlinedBox(),
        title = title,
    ) {
        CheckboxListItem(
            onClick = { filesSelector.jetprefDatastore = !filesSelector.jetprefDatastore },
            checked = filesSelector.jetprefDatastore,
            text = stringRes(R.string.backup_and_restore__back_up__files_jetpref_datastore),
        )
        CheckboxListItem(
            onClick = { filesSelector.imeKeyboard = !filesSelector.imeKeyboard },
            checked = filesSelector.imeKeyboard,
            text = stringRes(R.string.backup_and_restore__back_up__files_ime_keyboard),
        )
        CheckboxListItem(
            onClick = { filesSelector.imeTheme = !filesSelector.imeTheme },
            checked = filesSelector.imeTheme,
            text = stringRes(R.string.backup_and_restore__back_up__files_ime_theme),
        )
        // DRS v1.21.0: the learned personal words ride the archive too.
        CheckboxListItem(
            onClick = { filesSelector.userDictionary = !filesSelector.userDictionary },
            checked = filesSelector.userDictionary,
            text = stringRes(R.string.backup_and_restore__back_up__files_user_dictionary),
        )

        TriStateCheckboxListItem(
            onClick = {
                if (
                    filesSelector.clipboardData.value == ToggleableState.Off ||
                    filesSelector.clipboardData.value == ToggleableState.Indeterminate
                ) {
                    filesSelector.clipboardImageItems = true
                    filesSelector.clipboardVideoItems = true
                    filesSelector.clipboardTextItems = true
                } else {
                    filesSelector.clipboardImageItems = false
                    filesSelector.clipboardVideoItems = false
                    filesSelector.clipboardTextItems = false
                }
                filesSelector.updateCheckboxState()
            },
            state = filesSelector.clipboardData.value,
            text = stringRes(R.string.backup_and_restore__back_up__files_clipboard_history),
        )


        CheckboxListItem(
            onClick = {
                filesSelector.clipboardTextItems = !filesSelector.clipboardTextItems
                filesSelector.updateCheckboxState()
            },
            checked = filesSelector.clipboardTextItems,
            text = stringRes(R.string.backup_and_restore__back_up__files_clipboard_history__clipboard_text_items),
            isSecondaryListItem = true,
        )
        CheckboxListItem(
            onClick = {
                filesSelector.clipboardImageItems = !filesSelector.clipboardImageItems
                filesSelector.updateCheckboxState()
            },
            checked = filesSelector.clipboardImageItems,
            text = stringRes(R.string.backup_and_restore__back_up__files_clipboard_history__clipboard_image_items),
            isSecondaryListItem = true,
        )
        CheckboxListItem(
            onClick = {
                filesSelector.clipboardVideoItems = !filesSelector.clipboardVideoItems
                filesSelector.updateCheckboxState()
            },
            checked = filesSelector.clipboardVideoItems,
            text = stringRes(R.string.backup_and_restore__back_up__files_clipboard_history__clipboard_video_items),
            isSecondaryListItem = true,
        )

    }
}

@Composable
internal fun CheckboxListItem(
    onClick: () -> Unit,
    checked: Boolean,
    text: String,
    isSecondaryListItem: Boolean = false
) {
    JetPrefListItem(
        modifier = Modifier.rippleClickable(onClick = onClick),
        icon = {
            Row {
                if (isSecondaryListItem) {
                    Spacer(modifier = Modifier.width(40.dp))
                }
                Checkbox(
                    checked = checked,
                    onCheckedChange = null,
                )
            }
        },
        text = text,
    )
}

@Composable
internal fun TriStateCheckboxListItem(
    onClick: () -> Unit,
    state: ToggleableState,
    text: String,
    isSecondaryListItem: Boolean = false,
) {
    JetPrefListItem(
        modifier = Modifier.rippleClickable(onClick = onClick),
        icon = {
            Row {
                if (isSecondaryListItem) {
                    Spacer(modifier = Modifier.width(40.dp))
                }
                TriStateCheckbox(
                    state = state,
                    onClick = null,
                )
            }
        },
        text = text,
    )
}

@Composable
internal fun RadioListItem(
    onClick: () -> Unit,
    selected: Boolean,
    text: String,
    secondaryText: String? = null,
) {
    JetPrefListItem(
        modifier = Modifier.rippleClickable(onClick = onClick),
        icon = {
            RadioButton(
                selected = selected,
                onClick = null,
            )
        },
        text = text,
        secondaryText = secondaryText,
    )
}
