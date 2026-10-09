/*
 * Copyright (C) 2026 The DRS Smart Keyboard Project
 */

@file:OptIn(ExperimentalMaterial3Api::class)

package com.drs.smartkeyboard.drs.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.drs.smartkeyboard.R
import com.drs.smartkeyboard.drs.DrsMyText
import com.drs.smartkeyboard.drs.DrsMyTexts
import com.drs.smartkeyboard.drs.DrsStore
import com.drs.smartkeyboard.lib.compose.DrsScreen
import org.drs.lib.compose.stringRes

private data class DrsMyTextEditorState(
    val id: Long? = null,
    val text: String = "",
    val label: String = "",
    val category: String = "",
)

/** The localized label of a pure-engine validation error. */
@Composable
private fun myTextErrorLabel(error: DrsMyTexts.Error): Int = when (error) {
    DrsMyTexts.Error.EMPTY_TEXT -> R.string.drs__mytexts__err_empty
    DrsMyTexts.Error.TEXT_TOO_LONG -> R.string.drs__mytexts__err_text_long
    DrsMyTexts.Error.LABEL_TOO_LONG -> R.string.drs__mytexts__err_label_long
    DrsMyTexts.Error.CATEGORY_TOO_LONG -> R.string.drs__mytexts__err_category_long
    DrsMyTexts.Error.FULL -> R.string.drs__mytexts__err_full
}

/**
 * DRS v2.21.0 — نصوصي المحفوظة (the saved-texts manager): the full CRUD
 * home of the user's curated texts behind the sixth smart panel. The
 * doctrine mirrors the shortcuts manager: a master switch (gates the
 * PANEL and insertion, never management), confirmed destructive actions
 * (the v1.28.0 audit doctrine — deletion never fires from one slip), an
 * honest capacity counter (N/100), template badges, and a search that
 * matches exactly what was saved.
 */
@Composable
fun DrsMyTextsScreen() = DrsScreen {
    title = stringRes(R.string.drs__mytexts__title)
    navigationIconVisible = true
    previewFieldVisible = false

    val drsState by DrsStore.state.collectAsState()

    var editorState by remember { mutableStateOf<DrsMyTextEditorState?>(null) }
    var editorError by remember { mutableStateOf<DrsMyTexts.Error?>(null) }
    // DRS v1.28.0 audit doctrine: deletion confirms first, and the pending
    // id survives configuration changes via rememberSaveable.
    var pendingDeleteId by rememberSaveable { mutableStateOf<Long?>(null) }
    var showRemoveAllDialog by remember { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }

    val ordered = remember(drsState.myTexts) { DrsMyTexts.order(drsState.myTexts) }
    val visible = remember(ordered, query) { DrsMyTexts.search(ordered, query) }

    content {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // ---------------- Master switch ----------------
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.6f),
                ),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringRes(R.string.drs__mytexts__enable),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium,
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = stringRes(R.string.drs__mytexts__enable_summary),
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = drsState.myTextsEnabled,
                        onCheckedChange = { checked -> DrsMyTexts.setEnabled(checked) },
                    )
                }
            }

            // ---------------- Add + count ----------------
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedButton(
                    modifier = Modifier.weight(1f),
                    enabled = drsState.myTexts.size < DrsMyTexts.MAX_ITEMS,
                    onClick = {
                        editorError = null
                        editorState = DrsMyTextEditorState()
                    },
                ) {
                    Text(text = stringRes(R.string.drs__mytexts__add_new))
                }
                Spacer(Modifier.padding(horizontal = 4.dp))
                Text(
                    text = "${drsState.myTexts.size}/${DrsMyTexts.MAX_ITEMS}",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // ---------------- Existing texts ----------------
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.6f),
                ),
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            modifier = Modifier.weight(1f),
                            text = stringRes(R.string.drs__mytexts__list_section),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium,
                        )
                        if (drsState.myTexts.isNotEmpty()) {
                            TextButton(onClick = { showRemoveAllDialog = true }) {
                                Text(text = stringRes(R.string.drs__mytexts__remove_all))
                            }
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    OutlinedTextField(
                        modifier = Modifier.fillMaxWidth(),
                        value = query,
                        onValueChange = { query = it },
                        singleLine = true,
                        label = { Text(stringRes(R.string.drs__mytexts__search_hint)) },
                    )
                    Spacer(Modifier.height(6.dp))
                    if (drsState.myTexts.isEmpty()) {
                        Text(
                            text = stringRes(R.string.drs__mytexts__empty),
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else if (visible.isEmpty()) {
                        Text(
                            text = stringRes(R.string.drs__mytexts__search_empty),
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        visible.forEach { item ->
                            MyTextRow(
                                item = item,
                                onTogglePin = { DrsMyTexts.togglePin(item.id) },
                                onEdit = {
                                    editorError = null
                                    editorState = DrsMyTextEditorState(
                                        item.id, item.text, item.label, item.category,
                                    )
                                },
                                onDelete = { pendingDeleteId = item.id },
                            )
                        }
                    }
                }
            }
        }

        // ---------------- Editor dialog ----------------
        editorState?.let { editor ->
            AlertDialog(
                onDismissRequest = { editorState = null },
                title = {
                    Text(
                        stringRes(
                            if (editor.id == null) R.string.drs__mytexts__add_new
                            else R.string.drs__mytexts__edit,
                        ),
                    )
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            modifier = Modifier.fillMaxWidth(),
                            value = editor.text,
                            onValueChange = {
                                editorError = null
                                editorState = editor.copy(text = it)
                            },
                            label = { Text(stringRes(R.string.drs__mytexts__text_label)) },
                            minLines = 2,
                        )
                        OutlinedTextField(
                            modifier = Modifier.fillMaxWidth(),
                            value = editor.label,
                            onValueChange = {
                                editorError = null
                                editorState = editor.copy(label = it)
                            },
                            singleLine = true,
                            label = { Text(stringRes(R.string.drs__mytexts__label_label)) },
                        )
                        OutlinedTextField(
                            modifier = Modifier.fillMaxWidth(),
                            value = editor.category,
                            onValueChange = {
                                editorError = null
                                editorState = editor.copy(category = it)
                            },
                            singleLine = true,
                            label = { Text(stringRes(R.string.drs__mytexts__category_label)) },
                        )
                        if (DrsMyTexts.isTemplate(editor.text)) {
                            Text(
                                text = stringRes(R.string.drs__mytexts__template_badge),
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.tertiary,
                            )
                        }
                        editorError?.let { error ->
                            Text(
                                text = stringRes(myTextErrorLabel(error)),
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        val target = editor
                        val error = DrsMyTexts.validate(target.text, target.label, target.category)
                        if (error != null) {
                            editorError = error
                        } else {
                            val ok = if (target.id == null) {
                                DrsMyTexts.add(target.text, target.label, target.category)
                            } else {
                                DrsMyTexts.edit(target.id, target.text, target.label, target.category)
                            }
                            if (ok) editorState = null else editorError = DrsMyTexts.Error.FULL
                        }
                    }) {
                        Text(stringRes(R.string.action__save))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { editorState = null }) {
                        Text(stringRes(R.string.action__cancel))
                    }
                },
            )
        }

        // ---------------- Delete confirmations ----------------
        pendingDeleteId?.let { id ->
            AlertDialog(
                onDismissRequest = { pendingDeleteId = null },
                title = { Text(stringRes(R.string.drs__mytexts__delete)) },
                text = { Text(stringRes(R.string.drs__mytexts__delete_confirm)) },
                confirmButton = {
                    TextButton(onClick = {
                        DrsMyTexts.remove(id)
                        pendingDeleteId = null
                    }) {
                        Text(stringRes(R.string.action__delete))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { pendingDeleteId = null }) {
                        Text(stringRes(R.string.action__cancel))
                    }
                },
            )
        }
        if (showRemoveAllDialog) {
            AlertDialog(
                onDismissRequest = { showRemoveAllDialog = false },
                title = { Text(stringRes(R.string.drs__mytexts__remove_all)) },
                text = { Text(stringRes(R.string.drs__mytexts__remove_all_confirm)) },
                confirmButton = {
                    TextButton(onClick = {
                        DrsMyTexts.removeAll()
                        showRemoveAllDialog = false
                    }) {
                        Text(stringRes(R.string.action__delete))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showRemoveAllDialog = false }) {
                        Text(stringRes(R.string.action__cancel))
                    }
                },
            )
        }
    }
}

@Composable
private fun MyTextRow(
    item: DrsMyText,
    onTogglePin: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onTogglePin) {
            Icon(
                imageVector = Icons.Default.PushPin,
                contentDescription = stringRes(R.string.drs__mytexts__pin_toggle),
                tint = if (item.pinned) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = DrsMyTexts.previewLabel(item.text, item.label)
                    .ifBlank { stringRes(R.string.drs__mytexts__empty) },
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (item.category.isNotBlank()) {
                    Text(
                        text = item.category,
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.padding(horizontal = 4.dp))
                }
                if (DrsMyTexts.isTemplate(item.text)) {
                    Text(
                        text = stringRes(R.string.drs__mytexts__template_badge),
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                }
            }
            Text(
                text = item.text.replace("\n", " ⏎ "),
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        TextButton(onClick = onEdit) {
            Text(text = stringRes(R.string.drs__mytexts__edit))
        }
        IconButton(onClick = onDelete) {
            Icon(
                imageVector = Icons.Default.Delete,
                contentDescription = stringRes(R.string.drs__mytexts__delete),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
