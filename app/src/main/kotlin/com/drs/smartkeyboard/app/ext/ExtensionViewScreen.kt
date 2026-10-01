/*
 * Copyright (C) 2021-2025 The DRS Smart Keyboard Project
 */

package com.drs.smartkeyboard.app.ext

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.drs.smartkeyboard.R
import com.drs.smartkeyboard.app.LocalNavController
import com.drs.smartkeyboard.app.Routes
import com.drs.smartkeyboard.extensionManager
import com.drs.smartkeyboard.ime.nlp.LanguagePackExtension
import com.drs.smartkeyboard.ime.theme.ThemeExtension
import com.drs.smartkeyboard.ime.theme.ThemeExtensionComponentImpl
import com.drs.smartkeyboard.lib.compose.DrsConfirmDeleteDialog
import com.drs.smartkeyboard.lib.compose.DrsHyperlinkText
import com.drs.smartkeyboard.lib.compose.DrsScreen
import com.drs.smartkeyboard.lib.ext.Extension
import com.drs.smartkeyboard.lib.ext.ExtensionMaintainer
import com.drs.smartkeyboard.lib.ext.ExtensionMeta
import com.drs.smartkeyboard.lib.ext.extensionLicenseDisplayName
import com.drs.smartkeyboard.lib.io.DrsRef
import kotlinx.coroutines.flow.StateFlow
import org.drs.lib.android.showLongToastSync
import org.drs.lib.compose.DrsOutlinedButton
import org.drs.lib.compose.defaultDrsOutlinedBox
import org.drs.lib.compose.stringRes

@Composable
fun ExtensionViewScreen(id: String) {
    val context = LocalContext.current
    val extensionManager by context.extensionManager()

    // DRS M0.2 — resolve from the state-inclusive listing so a DISABLED
    // extension is still viewable (with enable/rollback), not a dead end.
    @Suppress("UNCHECKED_CAST")
    val allWithState by (extensionManager.extensionsWithState
        as StateFlow<List<Pair<com.drs.smartkeyboard.lib.ext.Extension, Boolean>>>).collectAsState()
    val pair = allWithState.find { it.first.meta.id == id }
    val ext = pair?.first
    if (ext != null) {
        ViewScreen(ext, pair.second)
    } else {
        ExtensionNotFoundScreen(id)
    }
}

@Composable
private fun ViewScreen(ext: com.drs.smartkeyboard.lib.ext.Extension, isEnabled: Boolean) = DrsScreen {
    title = ext.meta.title

    val navController = LocalNavController.current
    val context = LocalContext.current
    val extensionManager by context.extensionManager()

    var extToDelete by remember { mutableStateOf<Extension?>(null) }

    content {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp),
        ) {
            ext.meta.description?.let { Text(it) }
            Spacer(modifier = Modifier.height(16.dp))
            ExtensionMetaRowScrollableChips(
                label = stringRes(R.string.ext__meta__maintainers),
                showDividerAbove = false,
            ) {
                for ((n, maintainer) in ext.meta.maintainers.withIndex()) {
                    if (n > 0) {
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    ExtensionMaintainerChip(maintainer)
                }
            }
            ExtensionMetaRowSimpleText(label = stringRes(R.string.ext__meta__id)) {
                Text(text = ext.meta.id)
            }
            ExtensionMetaRowSimpleText(label = stringRes(R.string.ext__meta__version)) {
                Text(text = ext.meta.version)
            }
            if (ext.meta.keywords != null && ext.meta.keywords!!.isNotEmpty()) {
                ExtensionMetaRowScrollableChips(label = stringRes(R.string.ext__meta__keywords)) {
                    for ((n, keyword) in ext.meta.keywords!!.withIndex()) {
                        if (n > 0) {
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        ExtensionKeywordChip(keyword)
                    }
                }
            }
            if (!ext.meta.homepage.isNullOrBlank()) {
                ExtensionMetaRowSimpleText(label = stringRes(R.string.ext__meta__homepage)) {
                    DrsHyperlinkText(
                        text = DrsRef.fromUrl(ext.meta.homepage!!).authority,
                        url = ext.meta.homepage!!,
                    )
                }
            }
            if (!ext.meta.issueTracker.isNullOrBlank()) {
                ExtensionMetaRowSimpleText(label = stringRes(R.string.ext__meta__issue_tracker)) {
                    DrsHyperlinkText(
                        text = DrsRef.fromUrl(ext.meta.issueTracker!!).authority,
                        url = ext.meta.issueTracker!!,
                    )
                }
            }
            ExtensionMetaRowSimpleText(label = stringRes(R.string.ext__meta__license)) {
                // DRS v1.27.0: the human-readable license title — the
                // raw SPDX identifier/expression stays the machine truth
                // inside the manifest and the edit screen; the view row
                // speaks the world's language for every known id and
                // falls back to the raw id honestly when unknown.
                Text(text = extensionLicenseDisplayName(ext.meta.license))
            }
            Row(modifier = Modifier.fillMaxWidth()) {
                if (extensionManager.canDelete(ext)) {
                    DrsOutlinedButton(
                        onClick = {
                            extToDelete = ext
                        },
                        icon = Icons.Default.Delete,
                        text = stringRes(R.string.action__delete),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error,
                        ),
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                DrsOutlinedButton(
                    onClick = {
                        navController.navigate(Routes.Ext.Export(ext.meta.id))
                    },
                    icon = Icons.Default.Share,
                    text = stringRes(R.string.action__export),
                )
            }
            // DRS M0.2 — the honest lifecycle row: disable/enable + rollback,
            // only for internal (user-writable) archives. Assets-based
            // extensions have no lifecycle and render nothing here.
            if (extensionManager.canChangeLifecycle(ext)) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth()) {
                    if (isEnabled) {
                        DrsOutlinedButton(
                            onClick = {
                                runCatching { extensionManager.disable(ext) }
                                    .onFailure { error ->
                                        context.showLongToastSync(
                                            R.string.error__snackbar_message,
                                            "error_message" to error.localizedMessage,
                                        )
                                    }
                            },
                            icon = Icons.Default.Close,
                            text = stringRes(R.string.ext__lifecycle__disable),
                        )
                    } else {
                        DrsOutlinedButton(
                            onClick = {
                                runCatching { extensionManager.enable(ext) }
                                    .onFailure { error ->
                                        context.showLongToastSync(
                                            R.string.error__snackbar_message,
                                            "error_message" to error.localizedMessage,
                                        )
                                    }
                            },
                            icon = Icons.Default.CheckCircle,
                            text = stringRes(R.string.ext__lifecycle__enable),
                        )
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    if (extensionManager.archivedVersions(ext).isNotEmpty()) {
                        DrsOutlinedButton(
                            onClick = {
                                runCatching { extensionManager.rollback(ext) }
                                    .onSuccess { version ->
                                        context.showLongToastSync(
                                            R.string.ext__lifecycle__rolled_back,
                                            "version" to version,
                                        )
                                    }
                                    .onFailure { error ->
                                        context.showLongToastSync(
                                            R.string.error__snackbar_message,
                                            "error_message" to error.localizedMessage,
                                        )
                                    }
                            },
                            icon = Icons.Default.Refresh,
                            text = stringRes(R.string.ext__lifecycle__rollback),
                        )
                    }
                }
            }
        }

        when (ext) {
            is ThemeExtension -> {
                ExtensionComponentListView(
                    title = stringRes(R.string.ext__meta__components_theme),
                    components = ext.themes,
                ) { component ->
                    ExtensionComponentView(
                        modifier = Modifier.defaultDrsOutlinedBox(),
                        meta = ext.meta,
                        component = component,
                    )
                }
            }
            is LanguagePackExtension -> {
                ExtensionComponentListView(
                    title = stringRes(R.string.ext__meta__components_language_pack),
                    components = ext.items,
                ) { component ->
                    ExtensionComponentView(
                        modifier = Modifier.defaultDrsOutlinedBox(),
                        meta = ext.meta,
                        component = component,
                    )
                }
            }
            else -> {
                // Render nothing
            }
        }

        if (extToDelete != null) {
            DrsConfirmDeleteDialog(
                onConfirm = {
                    runCatching {
                        extensionManager.delete(extToDelete!!)
                    }.onSuccess {
                        navController.popBackStack()
                    }.onFailure { error ->
                        context.showLongToastSync(
                            R.string.error__snackbar_message,
                            "error_message" to error.localizedMessage,
                        )
                    }
                    extToDelete = null
                },
                onDismiss = { extToDelete = null },
                what = extToDelete!!.meta.title,
            )
        }
    }
}

@Composable
private fun ExtensionMetaRowSimpleText(
    label: String,
    modifier: Modifier = Modifier,
    showDividerAbove: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    if (showDividerAbove) {
        HorizontalDivider()
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(modifier = Modifier.padding(end = 24.dp), text = label)
        content()
    }
}

@Composable
private fun ExtensionMetaRowScrollableChips(
    label: String,
    modifier: Modifier = Modifier,
    showDividerAbove: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    if (showDividerAbove) {
        HorizontalDivider()
    }
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(modifier = Modifier.padding(end = 24.dp), text = label)
        Row(
            modifier = Modifier
                .weight(1.0f, fill = false)
                .horizontalScroll(rememberScrollState()),
        ) {
            content()
        }
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun PreviewExtensionViewerScreen() {
    val testExtension = ThemeExtension(
        meta = ExtensionMeta(
            id = "com.example.theme.test",
            version = "2.4.3",
            title = "Test theme",
            description = "This is a test theme to preview the extension viewer screen UI.",
            keywords = listOf("Beach", "Sea", "Sun"),
            homepage = "https://example.com",
            issueTracker = "https://git.example.com/issues",
            maintainers = listOf(
                "Max Mustermann <max.mustermann@example.com> (maxmustermann.example.com)",
            ).map { ExtensionMaintainer.fromOrTakeRaw(it) },
            license = "proprietary",
        ),
        dependencies = null,
        themes = listOf(
            ThemeExtensionComponentImpl(id = "test", label = "Test", authors = listOf(), stylesheetPath = "test.json"),
        ),
    )
    ViewScreen(ext = testExtension, isEnabled = true)
}
