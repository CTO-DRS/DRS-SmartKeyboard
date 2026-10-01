/*
 * Copyright (C) 2025 The DRS Smart Keyboard Project
 */

package com.drs.smartkeyboard.ime.sheet

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import com.drs.smartkeyboard.ime.core.SelectSubtypePanel
import com.drs.smartkeyboard.ime.keyboard.KeyboardState
import com.drs.smartkeyboard.ime.smartbar.quickaction.QuickActionsEditorPanel
import com.drs.smartkeyboard.keyboardManager
import kotlin.getValue

@Composable
fun BottomSheetWindow() {
    val context = LocalContext.current
    val keyboardManager by context.keyboardManager()
    val state by keyboardManager.activeState.collectAsState()

    BottomSheetHostUi(
        isShowing = state.isAnyBottomSheetVisible(),
        onHide = {
            if (state.isActionsEditorVisible) {
                keyboardManager.activeState.isActionsEditorVisible = false
            }
            if (state.isSubtypeSelectionVisible) {
                keyboardManager.activeState.isSubtypeSelectionVisible = false
            }
        },
    ) {
        if (state.isActionsEditorVisible) {
            QuickActionsEditorPanel()
        }
        if (state.isSubtypeSelectionVisible) {
            SelectSubtypePanel()
        }
    }
}

fun KeyboardState.isAnyBottomSheetVisible(): Boolean {
    return isActionsEditorVisible || isSubtypeSelectionVisible
}
