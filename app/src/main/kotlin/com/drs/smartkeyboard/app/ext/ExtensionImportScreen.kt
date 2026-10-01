/*
 * Copyright (C) 2021-2025 The DRS Smart Keyboard Project
 */

package com.drs.smartkeyboard.app.ext

import android.text.format.Formatter
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import com.drs.smartkeyboard.R
import com.drs.smartkeyboard.app.LocalNavController
import com.drs.smartkeyboard.cacheManager
import com.drs.smartkeyboard.extensionManager
import com.drs.smartkeyboard.ime.keyboard.KeyboardExtension
import com.drs.smartkeyboard.ime.nlp.LanguagePackExtension
import com.drs.smartkeyboard.ime.theme.ThemeExtension
import com.drs.smartkeyboard.lib.NATIVE_NULLPTR
import com.drs.smartkeyboard.lib.cache.CacheManager
import com.drs.smartkeyboard.lib.compose.DrsScreen
import com.drs.smartkeyboard.lib.io.FileRegistry
import org.drs.lib.compose.DrsBulletSpacer
import org.drs.lib.compose.DrsButtonBar
import org.drs.lib.compose.DrsOutlinedBox
import org.drs.lib.compose.DrsOutlinedButton
import org.drs.lib.compose.defaultDrsOutlinedBox
import org.drs.lib.compose.drsHorizontalScroll
import org.drs.lib.compose.stringRes
import org.drs.lib.android.showLongToastSync
import org.drs.lib.kotlin.resultOk

enum class ExtensionImportScreenType(
    val id: String,
    @StringRes val titleResId: Int,
    val supportedFiles: List<FileRegistry.Entry>,
) {
    EXT_ANY(
        id = "ext-any",
        titleResId = R.string.ext__import__ext_any,
        supportedFiles = listOf(FileRegistry.FlexExtension),
    ),
    EXT_KEYBOARD(
        id = "ext-keyboard",
        titleResId = R.string.ext__import__ext_keyboard,
        supportedFiles = listOf(FileRegistry.FlexExtension),
    ),
    EXT_THEME(
        id = "ext-theme",
        titleResId = R.string.ext__import__ext_theme,
        supportedFiles = listOf(FileRegistry.FlexExtension),
    ),
    EXT_LANGUAGEPACK(
        id = "ext-languagepack",
        titleResId = R.string.ext__import__ext_languagepack,
        supportedFiles = listOf(FileRegistry.FlexExtension),
    );
}

@Composable
fun ExtensionImportScreen(type: ExtensionImportScreenType, initUuid: String?) = DrsScreen {
    title = stringRes(type.titleResId)

    val navController = LocalNavController.current
    val context = LocalContext.current
    val cacheManager by context.cacheManager()
    val extensionManager by context.extensionManager()

    fun getSkipReason(fileInfo: CacheManager.FileInfo): Int {
        return when {
            !FileRegistry.matchesFileFilter(fileInfo, type.supportedFiles) -> {
                R.string.ext__import__file_skip_unsupported
            }
            fileInfo.ext != null -> {
                val ext = fileInfo.ext
                if (extensionManager.getExtensionById(ext.meta.id)?.sourceRef?.isAssets == true) {
                    R.string.ext__import__file_skip_ext_core
                } else {
                    NATIVE_NULLPTR.toInt()
                }
            }
            else -> { // ext == null
                R.string.ext__import__file_skip_ext_corrupted
            }
        }
    }

    fun Result<CacheManager.ImporterWorkspace>.mapSkipReasons(): Result<CacheManager.ImporterWorkspace> {
        return this.map { workspace ->
            workspace.inputFileInfos.forEach { fileInfo ->
                fileInfo.skipReason = getSkipReason(fileInfo)
            }
            workspace
        }
    }

    var importResult by remember(initUuid) {
        val workspace = initUuid?.let { cacheManager.importer.getWorkspaceByUuid(it) }
            ?.let { resultOk(it) }
            ?.mapSkipReasons()
        mutableStateOf(workspace)
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents(),
        onResult = { uriList ->
            // If uri is null it indicates that the selection activity
            //  was cancelled (mostly by pressing the back button), so
            //  we don't display an error message here.
            if (uriList.isEmpty()) return@rememberLauncherForActivityResult
            importResult?.getOrNull()?.close()
            importResult = runCatching { cacheManager.readFromUriIntoCache(uriList) }.mapSkipReasons()
        },
    )

    bottomBar {
        DrsButtonBar {
            ButtonBarSpacer()
            ButtonBarTextButton(
                text = stringRes(R.string.action__cancel),
            ) {
                importResult?.getOrNull()?.close()
                navController.popBackStack()
            }
            val enabled = remember(importResult) {
                importResult?.getOrNull()?.takeIf { workspace ->
                    workspace.inputFileInfos.any { it.skipReason == NATIVE_NULLPTR.toInt() }
                } != null
            }
            ButtonBarButton(
                text = stringRes(R.string.action__import),
                enabled = enabled,
            ) {
                val workspace = importResult!!.getOrThrow()
                // DRS p7 (E3-8) — per-file runCatching: one throwing file (e.g. an
                // invalid id refused by the import gate) used to abort the whole loop
                // AFTER earlier files were already installed — a silent partial install
                // with a generic "import failed" toast. Every file is now imported
                // independently, failures are collected, and the loop continues past
                // them. The report is honest: success toast + close + pop ONLY when ALL
                // files imported; ≥1 failure → the existing ext__import__failure toast
                // carries the FIRST failure's message and the workspace stays open (the
                // file list remains inspectable, retry possible). Known string gap (as
                // flagged by p7-C): no pluralized "N succeeded, M failed" key exists.
                val failures = mutableListOf<Throwable>()
                for (fileInfo in workspace.inputFileInfos) {
                    if (fileInfo.skipReason != NATIVE_NULLPTR.toInt()) {
                        continue
                    }
                    val ext = fileInfo.ext
                    runCatching {
                        when (type) {
                            ExtensionImportScreenType.EXT_ANY -> {
                                ext?.let { extensionManager.import(it) }
                            }
                            ExtensionImportScreenType.EXT_KEYBOARD -> {
                                ext.takeIf { it is KeyboardExtension }?.let { extensionManager.import(it) }
                            }
                            ExtensionImportScreenType.EXT_THEME -> {
                                ext.takeIf { it is ThemeExtension }?.let { extensionManager.import(it) }
                            }
                            ExtensionImportScreenType.EXT_LANGUAGEPACK -> {
                                ext.takeIf { it is LanguagePackExtension }?.let { extensionManager.import(it) }
                            }
                        }
                    }.onFailure { error ->
                        failures.add(error)
                    }
                }
                if (failures.isEmpty()) {
                    workspace.close()
                    context.showLongToastSync(R.string.ext__import__success)
                    navController.popBackStack()
                } else {
                    context.showLongToastSync(
                        R.string.ext__import__failure,
                        "error_message" to failures.first().localizedMessage,
                    )
                }
            }
        }
    }

    content {
        if (initUuid == null) {
            DrsOutlinedButton(
                onClick = {
                    importLauncher.launch("*/*")
                },
                modifier = Modifier
                    .padding(vertical = 16.dp)
                    .align(Alignment.CenterHorizontally),
                text = stringRes(R.string.action__select_files),
            )
        }

        val result = importResult
        when {
            result == null -> {
                Text(
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .padding(horizontal = 16.dp),
                    text = stringRes(R.string.state__no_files_selected),
                    fontStyle = FontStyle.Italic,
                )
            }
            result.isSuccess -> {
                val workspace = result.getOrThrow()
                for (fileInfo in workspace.inputFileInfos) {
                    FileInfoView(fileInfo)
                }
            }
            result.isFailure -> {
                Text(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    text = stringRes(R.string.ext__import__error_unexpected_exception),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
                SelectionContainer {
                    Text(
                        modifier = Modifier
                            .drsHorizontalScroll()
                            .padding(horizontal = 16.dp),
                        text = result.exceptionOrNull()?.stackTraceToString() ?: "null",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        fontStyle = FontStyle.Italic,
                    )
                }
            }
        }
    }
}

@Composable
private fun FileInfoView(
    fileInfo: CacheManager.FileInfo,
) {
    DrsOutlinedBox(
        modifier = Modifier.defaultDrsOutlinedBox(),
        title = fileInfo.file.name,
        subtitle = fileInfo.mediaType ?: "application/unknown",
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            val grayColor = LocalContentColor.current.copy(alpha = 0.56f)
            val ext = fileInfo.ext
            Row {
                Text(
                    text = Formatter.formatShortFileSize(LocalContext.current, fileInfo.size),
                    style = MaterialTheme.typography.bodyMedium,
                    color = grayColor,
                )
                if (ext != null) {
                    DrsBulletSpacer()
                    Text(
                        text = ext.meta.id,
                        style = MaterialTheme.typography.bodyMedium,
                        color = grayColor,
                    )
                    DrsBulletSpacer()
                    Text(
                        text = ext.meta.version,
                        style = MaterialTheme.typography.bodyMedium,
                        color = grayColor,
                    )
                }
            }
            if (ext != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = ext.meta.title,
                    style = MaterialTheme.typography.bodyMedium,
                )
                ext.meta.description?.let { description ->
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodyMedium,
                        fontStyle = FontStyle.Italic,
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                val maintainers = remember(ext) {
                    ext.meta.maintainers.joinToString { it.name }
                }
                Text(
                    text = stringRes(R.string.ext__meta__maintainers_by, "maintainers" to maintainers),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(modifier = Modifier.height(8.dp))
                for (component in ext.components()) {
                    Text(
                        text = component.id,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            if (fileInfo.skipReason != NATIVE_NULLPTR.toInt()) {
                Box(modifier = Modifier
                    .fillMaxWidth()
                    .height(19.dp)
                    .padding(top = 10.dp, bottom = 8.dp)
                    .background(MaterialTheme.colorScheme.error.copy(alpha = 0.56f)))
                Text(
                    text = stringRes(R.string.ext__import__file_skip),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
                Text(
                    text = stringRes(fileInfo.skipReason),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    fontStyle = FontStyle.Italic,
                )
            }
        }
    }
}
