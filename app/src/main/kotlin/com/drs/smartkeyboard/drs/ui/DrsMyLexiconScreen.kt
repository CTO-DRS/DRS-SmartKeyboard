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
import com.drs.smartkeyboard.drs.DrsMyLexicon
import com.drs.smartkeyboard.drs.DrsMyLexiconEntry
import com.drs.smartkeyboard.drs.DrsStore
import com.drs.smartkeyboard.lib.compose.DrsScreen
import org.drs.lib.compose.stringRes

private data class DrsMyLexiconEditorState(
    val id: Long? = null,
    val word: String = "",
    val vocalized: String = "",
)

/** The localized label of a pure-engine validation error (shared with the panel's teach dialog). */
@Composable
internal fun myLexiconErrorLabel(error: DrsMyLexicon.Error): Int = when (error) {
    DrsMyLexicon.Error.EMPTY_WORD -> R.string.drs__mylexicon__err_empty_word
    DrsMyLexicon.Error.EMPTY_VOCALIZED -> R.string.drs__mylexicon__err_empty_vocalized
    DrsMyLexicon.Error.WORD_TOO_LONG -> R.string.drs__mylexicon__err_word_long
    DrsMyLexicon.Error.VOCALIZED_TOO_LONG -> R.string.drs__mylexicon__err_vocalized_long
    DrsMyLexicon.Error.WORD_HAS_MARKS -> R.string.drs__mylexicon__err_word_marks
    DrsMyLexicon.Error.VOCALIZED_MISMATCH -> R.string.drs__mylexicon__err_mismatch
    DrsMyLexicon.Error.DUPLICATE_WORD -> R.string.drs__mylexicon__err_duplicate
    DrsMyLexicon.Error.FULL -> R.string.drs__mylexicon__err_full
}

/**
 * DRS v2.22.0 — قاموسي التشكيلي (the personal tashkeel lexicon manager):
 * the full CRUD home of the words the user taught the harakat system,
 * behind the smart board's teach chip. The doctrine mirrors the
 * saved-texts manager: a master switch (gates the OVERRIDE LOOKUP, never
 * management), confirmed destructive actions (deletion never fires from
 * one slip), an honest capacity counter (N/200), and a search that
 * matches exactly what was taught.
 *
 * The lookup doctrine is «المستخدم تفوز»: a taught word answers BEFORE
 * the seed and the 3000-word asset — an explicit override by the user's
 * own hand, never a guess.
 */
@Composable
fun DrsMyLexiconScreen() = DrsScreen {
    title = stringRes(R.string.drs__mylexicon__title)
    navigationIconVisible = true
    previewFieldVisible = false

    val drsState by DrsStore.state.collectAsState()

    var editorState by remember { mutableStateOf<DrsMyLexiconEditorState?>(null) }
    var editorError by remember { mutableStateOf<DrsMyLexicon.Error?>(null) }
    // DRS v1.28.0 audit doctrine: deletion confirms first, and the pending
    // id survives configuration changes via rememberSaveable.
    var pendingDeleteId by rememberSaveable { mutableStateOf<Long?>(null) }
    var showRemoveAllDialog by remember { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }

    val ordered = remember(drsState.myLexicon) { DrsMyLexicon.order(drsState.myLexicon) }
    val visible = remember(ordered, query) { DrsMyLexicon.search(ordered, query) }

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
                            text = stringRes(R.string.drs__mylexicon__enable),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium,
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = stringRes(R.string.drs__mylexicon__enable_summary),
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = drsState.myLexiconEnabled,
                        onCheckedChange = { checked -> DrsMyLexicon.setEnabled(checked) },
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
                    enabled = drsState.myLexicon.size < DrsMyLexicon.MAX_ITEMS,
                    onClick = {
                        editorError = null
                        editorState = DrsMyLexiconEditorState()
                    },
                ) {
                    Text(text = stringRes(R.string.drs__mylexicon__add_new))
                }
                Spacer(Modifier.padding(horizontal = 4.dp))
                Text(
                    text = "${drsState.myLexicon.size}/${DrsMyLexicon.MAX_ITEMS}",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // ---------------- Taught words ----------------
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
                            text = stringRes(R.string.drs__mylexicon__list_section),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium,
                        )
                        if (drsState.myLexicon.isNotEmpty()) {
                            TextButton(onClick = { showRemoveAllDialog = true }) {
                                Text(text = stringRes(R.string.drs__mylexicon__remove_all))
                            }
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    OutlinedTextField(
                        modifier = Modifier.fillMaxWidth(),
                        value = query,
                        onValueChange = { query = it },
                        singleLine = true,
                        label = { Text(stringRes(R.string.drs__mylexicon__search_hint)) },
                    )
                    Spacer(Modifier.height(6.dp))
                    if (drsState.myLexicon.isEmpty()) {
                        Text(
                            text = stringRes(R.string.drs__mylexicon__empty),
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else if (visible.isEmpty()) {
                        Text(
                            text = stringRes(R.string.drs__mylexicon__search_empty),
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        visible.forEach { item ->
                            LexiconEntryRow(
                                item = item,
                                onTogglePin = { DrsMyLexicon.togglePin(item.id) },
                                onEdit = {
                                    editorError = null
                                    editorState = DrsMyLexiconEditorState(
                                        item.id, item.word, item.vocalized,
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
                            if (editor.id == null) R.string.drs__mylexicon__add_new
                            else R.string.drs__mylexicon__edit,
                        ),
                    )
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            modifier = Modifier.fillMaxWidth(),
                            value = editor.word,
                            onValueChange = {
                                editorError = null
                                editorState = editor.copy(word = it)
                            },
                            singleLine = true,
                            label = { Text(stringRes(R.string.drs__mylexicon__word_label)) },
                        )
                        OutlinedTextField(
                            modifier = Modifier.fillMaxWidth(),
                            value = editor.vocalized,
                            onValueChange = {
                                editorError = null
                                editorState = editor.copy(vocalized = it)
                            },
                            singleLine = true,
                            label = { Text(stringRes(R.string.drs__mylexicon__vocalized_label)) },
                        )
                        editorError?.let { error ->
                            Text(
                                text = stringRes(myLexiconErrorLabel(error)),
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        val target = editor
                        val error = DrsMyLexicon.validate(target.word, target.vocalized)
                        if (error != null) {
                            editorError = error
                        } else {
                            val ok = if (target.id == null) {
                                DrsMyLexicon.add(target.word, target.vocalized)
                            } else {
                                DrsMyLexicon.edit(target.id, target.word, target.vocalized)
                            }
                            if (ok) {
                                editorState = null
                            } else {
                                editorError = DrsMyLexicon.validate(target.word, target.vocalized)
                                    ?: DrsMyLexicon.Error.FULL
                            }
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
                title = { Text(stringRes(R.string.drs__mylexicon__delete)) },
                text = { Text(stringRes(R.string.drs__mylexicon__delete_confirm)) },
                confirmButton = {
                    TextButton(onClick = {
                        DrsMyLexicon.remove(id)
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
                title = { Text(stringRes(R.string.drs__mylexicon__remove_all)) },
                text = { Text(stringRes(R.string.drs__mylexicon__remove_all_confirm)) },
                confirmButton = {
                    TextButton(onClick = {
                        DrsMyLexicon.removeAll()
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
private fun LexiconEntryRow(
    item: DrsMyLexiconEntry,
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
                contentDescription = stringRes(R.string.drs__mylexicon__pin_toggle),
                tint = if (item.pinned) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.vocalized,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = item.word,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        TextButton(onClick = onEdit) {
            Text(text = stringRes(R.string.drs__mylexicon__edit))
        }
        IconButton(onClick = onDelete) {
            Icon(
                imageVector = Icons.Default.Delete,
                contentDescription = stringRes(R.string.drs__mylexicon__delete),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
