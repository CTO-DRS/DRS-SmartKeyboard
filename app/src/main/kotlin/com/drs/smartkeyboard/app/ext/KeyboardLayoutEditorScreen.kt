/*
 * Copyright (C) 2025 The DRS Smart Keyboard Project
 */

package com.drs.smartkeyboard.app.ext

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.drs.smartkeyboard.R
import com.drs.smartkeyboard.ime.keyboard.KeyboardExtensionEditor
import com.drs.smartkeyboard.ime.keyboard.LayoutArrangementComponent
import com.drs.smartkeyboard.ime.keyboard.LayoutArrangementEditor
import com.drs.smartkeyboard.ime.keyboard.LayoutType
import com.drs.smartkeyboard.lib.cache.CacheManager
import com.drs.smartkeyboard.lib.compose.DrsScreen
import org.drs.lib.compose.DrsButtonBar
import org.drs.lib.compose.DrsIconButton
import org.drs.lib.compose.DrsOutlinedBox
import org.drs.lib.compose.defaultDrsOutlinedBox
import org.drs.lib.compose.stringRes
import org.drs.lib.compose.DrsTextButton

/**
 * DRS M0.1 — the layout index view of a keyboard extension inside the
 * extension editor. Lists every layout type with its arrangements; each
 * arrangement opens in [KeyboardLayoutEditorScreen].
 */
@Composable
internal fun KeyboardExtensionLayoutsList(
    workspace: CacheManager.ExtEditorWorkspace<*>,
    extEditor: KeyboardExtensionEditor,
) {
    Column {
        ListItem(headlineContent = { Text(
            text = stringRes(R.string.ext__meta__components_keyboard),
            color = MaterialTheme.colorScheme.secondary,
            fontWeight = FontWeight.Bold,
        ) })
        for ((typeId, components) in extEditor.layouts) {
            ListItem(headlineContent = { Text(
                text = typeId,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.tertiary,
                fontWeight = FontWeight.Bold,
            ) })
            for (component in components) {
                DrsOutlinedBox(
                    modifier = Modifier
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                        .fillMaxWidth(),
                ) {
                    ListItem(
                        headlineContent = { Text(text = component.label) },
                        supportingContent = { Text(text = component.id) },
                        trailingContent = {
                            DrsTextButton(
                                onClick = {
                                    workspace.currentAction = EditorAction.ManageKeyboardArrangement(typeId, component)
                                },
                                text = stringRes(R.string.action__edit),
                            )
                        },
                    )
                }
            }
        }
    }
}

/**
 * DRS M0.1 — the visual touch grid for ONE arrangement of a keyboard
 * extension. Reuses the package's own arrangement JSON file (loaded from
 * the editor workspace staging dir) through the pure-JVM
 * [LayoutArrangementEditor]; every mutation registers the edited
 * arrangement on the editor so the save flow persists it back into the
 * package. The grid always renders left-to-right (arrangements are LTR
 * data structures — JSON order = visual order), while the screen chrome
 * follows the app direction.
 */
@Composable
internal fun KeyboardLayoutEditorScreen(
    workspace: CacheManager.ExtEditorWorkspace<*>,
    typeId: String,
    component: LayoutArrangementComponent,
) = DrsScreen {
    title = component.label

    val extEditor = workspace.editor as? KeyboardExtensionEditor ?: return@DrsScreen
    val arrangementKey = "$typeId:${component.id}"
    val layoutType = LayoutType.entries.firstOrNull { it.id == typeId }

    val arrangementEditor: LayoutArrangementEditor? = remember(arrangementKey) {
        extEditor.editedArrangements[arrangementKey] ?: run {
            val relPath = component.arrangementFile(layoutType ?: LayoutType.CHARACTERS)
            val file = containedSubFile(workspace.extDir, relPath)
            val parsed = file?.takeIf { it.isFile }?.let { f ->
                runCatching { LayoutArrangementEditor.parse(f.readText()) }.getOrNull()
            }
            if (parsed != null) {
                extEditor.editedArrangements[arrangementKey] = parsed
            }
            parsed
        }
    }

    if (arrangementEditor == null) {
        Text(
            text = stringRes(R.string.ext__editor__keyboard__missing_file),
            modifier = Modifier.padding(16.dp),
        )
        navigationIcon {
            DrsIconButton(
                onClick = { workspace.currentAction = null },
                icon = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringRes(R.string.action__navigate_back),
            )
        }
        return@DrsScreen
    }

    var revision by remember { mutableIntStateOf(0) }
    var selected by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var showKeyDialog by remember { mutableStateOf(false) }

    fun apply(mutation: LayoutArrangementEditor.() -> Boolean): Boolean {
        val changed = arrangementEditor.mutation()
        if (changed) {
            // Reference the outer smart-cast editor only (star-projected
            // receiver of update is unusable inside the lambda).
            workspace.update { extEditor.editedArrangements[arrangementKey] = arrangementEditor }
            revision++
        }
        return changed
    }

    navigationIcon {
        DrsIconButton(
            onClick = { workspace.currentAction = null },
            icon = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = stringRes(R.string.action__navigate_back),
        )
    }

    bottomBar {
        DrsButtonBar {
            ButtonBarSpacer()
            ButtonBarTextButton(text = stringRes(R.string.ext__editor__keyboard__add_row)) {
                apply { addRow(rowCount) }
            }
            ButtonBarTextButton(text = stringRes(R.string.ext__editor__keyboard__add_key)) {
                val lastRow = (arrangementEditor.rowCount - 1).coerceAtLeast(0)
                val rowIdx = (selected?.first ?: lastRow).coerceIn(0, lastRow)
                val colIdx = selected?.let { it.second + 1 } ?: arrangementEditor.keyCount(rowIdx)
                apply { addKey(rowIdx, colIdx.coerceIn(0, keyCount(rowIdx))) }
            }
            ButtonBarButton(text = stringRes(R.string.action__done)) {
                workspace.currentAction = null
            }
        }
    }

    content {
        key(revision) {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    for (rowIndex in 0 until arrangementEditor.rowCount) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            for (keyIndex in 0 until arrangementEditor.keyCount(rowIndex)) {
                                val label = arrangementEditor.labelAt(rowIndex, keyIndex)
                                val isActionKey = label.isNullOrEmpty()
                                KeyCell(
                                    modifier = Modifier.weight(1f),
                                    label = label ?: arrangementEditor.keyAt(rowIndex, keyIndex)
                                        ?.toString()?.take(12) ?: "?",
                                    selected = selected == Pair(rowIndex, keyIndex),
                                    dimmed = isActionKey,
                                    onClick = {
                                        selected = Pair(rowIndex, keyIndex)
                                        showKeyDialog = true
                                    },
                                )
                            }
                        }
                    }
                    if (arrangementEditor.rowCount == 0) {
                        Text(text = stringRes(R.string.ext__editor__keyboard__empty))
                    }
                }
            }
        }

        if (showKeyDialog) {
            val sel = selected
            if (sel == null || sel.first >= arrangementEditor.rowCount ||
                sel.second >= arrangementEditor.keyCount(sel.first)
            ) {
                showKeyDialog = false
            } else {
                KeyEditDialog(
                    arrangementEditor = arrangementEditor,
                    row = sel.first,
                    index = sel.second,
                    onApply = { apply(it) },
                    onDismiss = { showKeyDialog = false },
                )
            }
        }
    }
}

@Composable
private fun KeyCell(
    modifier: Modifier,
    label: String,
    selected: Boolean,
    dimmed: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .height(44.dp)
            .background(
                color = if (dimmed) {
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                },
                shape = RoundedCornerShape(8.dp),
            )
            .then(
                if (selected) {
                    Modifier.border(
                        width = 2.dp,
                        color = MaterialTheme.colorScheme.primary,
                        shape = RoundedCornerShape(8.dp),
                    )
                } else {
                    Modifier
                },
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            fontSize = 14.sp,
            maxLines = 1,
            textAlign = TextAlign.Center,
            color = if (dimmed) {
                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            } else {
                MaterialTheme.colorScheme.onSurface
            },
        )
    }
}

@Composable
private fun KeyEditDialog(
    arrangementEditor: LayoutArrangementEditor,
    row: Int,
    index: Int,
    onApply: (LayoutArrangementEditor.() -> Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    val canEditLabel = arrangementEditor.isLabelEditableAt(row, index)
    val canEditCode = arrangementEditor.isCodeEditableAt(row, index)

    var label by remember(row, index) {
        mutableStateOf(arrangementEditor.labelAt(row, index).orEmpty())
    }
    var code by remember(row, index) {
        mutableStateOf(arrangementEditor.codeAt(row, index)?.toString().orEmpty())
    }

    org.drs.jetpref.material.ui.JetPrefAlertDialog(
        title = stringRes(R.string.ext__editor__keyboard__edit_key),
        confirmLabel = stringRes(R.string.action__ok),
        onConfirm = {
            if (canEditLabel) onApply { setKeyLabel(row, index, label) }
            if (canEditCode) {
                code.toIntOrNull()?.let { value -> onApply { setKeyCode(row, index, value) } }
            }
            onDismiss()
        },
        onDismiss = onDismiss,
        content = {
            Column {
                if (!canEditLabel && !canEditCode) {
                    Text(text = stringRes(R.string.ext__editor__keyboard__key_not_editable))
                }
                if (canEditLabel) {
                    OutlinedTextField(
                        value = label,
                        onValueChange = { label = it },
                        label = { Text(stringRes(R.string.ext__editor__keyboard__label)) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (canEditCode) {
                    OutlinedTextField(
                        value = code,
                        onValueChange = { code = it },
                        label = { Text(stringRes(R.string.ext__editor__keyboard__code)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    DrsTextButton(
                        onClick = {
                            onApply { deleteKey(row, index) }
                            onDismiss()
                        },
                        icon = Icons.Default.Delete,
                        text = stringRes(R.string.ext__editor__keyboard__delete_key),
                    )
                    DrsTextButton(
                        onClick = {
                            onApply { moveKey(row, index, row, index - 1) }
                            onDismiss()
                        },
                        icon = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                        text = stringRes(R.string.ext__editor__keyboard__move_left),
                    )
                    DrsTextButton(
                        onClick = {
                            onApply { moveKey(row, index, row, index + 1) }
                            onDismiss()
                        },
                        icon = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        text = stringRes(R.string.ext__editor__keyboard__move_right),
                    )
                }
            }
        },
    )
}
