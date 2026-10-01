/*
 * Copyright (C) 2021-2026 The DRS Smart Keyboard Project
 */

package org.drs.jetpref.datastore.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.res.stringResource

data class DialogPrefStrings(
    val confirmLabel: String,
    val dismissLabel: String,
    val neutralLabel: String,
)

val LocalDefaultDialogPrefStrings = staticCompositionLocalOf {
    DialogPrefStrings(
        confirmLabel = "Ok",
        dismissLabel = "Cancel",
        neutralLabel = "Default",
    )
}

/**
 * Provides the default button strings to use for all preferences
 * which show a dialog.
 *
 * @param confirmLabel The label for the confirm button. Null means
 *  no preferred default value.
 * @param dismissLabel The label for the dismiss button. Null means
 *  no preferred default value.
 * @param neutralLabel The label for the neutral button. Null means
 *  no preferred default value.
 */
@Composable
fun ProvideDefaultDialogPrefStrings(
    confirmLabel: String = stringResource(android.R.string.ok),
    dismissLabel: String = stringResource(android.R.string.cancel),
    neutralLabel: String = "Default",
    content: @Composable () -> Unit,
) {
    val dialogPrefStrings = remember(confirmLabel, dismissLabel, neutralLabel) {
        DialogPrefStrings(confirmLabel, dismissLabel, neutralLabel)
    }
    CompositionLocalProvider(
        LocalDefaultDialogPrefStrings provides dialogPrefStrings,
        content = content,
    )
}
