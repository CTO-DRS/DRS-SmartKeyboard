/*
 * Copyright (C) 2021-2025 The DRS Smart Keyboard Project
 */

package com.drs.smartkeyboard.lib.compose

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.drs.smartkeyboard.R
import org.drs.jetpref.material.ui.JetPrefAlertDialog
import org.drs.lib.compose.stringRes

@Composable
fun DrsConfirmDeleteDialog(
    modifier: Modifier = Modifier,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    what: String,
) {
    JetPrefAlertDialog(
        modifier = modifier,
        title = stringRes(R.string.action__delete_confirm_title),
        confirmLabel = stringRes(R.string.action__delete),
        onConfirm = onConfirm,
        dismissLabel = stringRes(R.string.action__cancel),
        onDismiss = onDismiss,
    ) {
        Text(text = stringRes(R.string.action__delete_confirm_message, "name" to what))
    }
}

@Composable
fun DrsUnsavedChangesDialog(
    modifier: Modifier = Modifier,
    onSave: () -> Unit,
    onDiscard: () -> Unit,
    onDismiss: () -> Unit,
) {
    JetPrefAlertDialog(
        modifier = modifier,
        title = stringRes(R.string.action__discard_confirm_title),
        confirmLabel = stringRes(R.string.action__save),
        onConfirm = onSave,
        dismissLabel = stringRes(R.string.action__discard),
        onDismiss = onDiscard,
        onOutsideDismissal = onDismiss,
        neutralLabel = stringRes(R.string.action__cancel),
        onNeutral = onDismiss,
    ) {
        Text(text = stringRes(R.string.action__discard_confirm_message))
    }
}
