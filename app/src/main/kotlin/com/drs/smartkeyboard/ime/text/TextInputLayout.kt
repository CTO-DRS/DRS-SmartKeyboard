/*
 * Copyright (C) 2021-2025 The DRS Smart Keyboard Project
 */

package com.drs.smartkeyboard.ime.text

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import com.drs.smartkeyboard.R
import com.drs.smartkeyboard.app.DrsPreferenceStore
import com.drs.smartkeyboard.drs.DrsRuntimeState
import com.drs.smartkeyboard.drs.ui.DrsStripSlotEditorPanel
import com.drs.smartkeyboard.drs.ui.DrsToolsDrawerPanel
import com.drs.smartkeyboard.drs.ui.DrsUnifiedStrip
import com.drs.smartkeyboard.ime.smartbar.IncognitoDisplayMode
import com.drs.smartkeyboard.ime.smartbar.InlineSuggestionsStyleCache
import com.drs.smartkeyboard.ime.smartbar.Smartbar
import com.drs.smartkeyboard.ime.smartbar.quickaction.QuickActionsOverflowPanel
import com.drs.smartkeyboard.ime.text.keyboard.TextKeyboardLayout
import com.drs.smartkeyboard.ime.theme.DrsImeUi
import com.drs.smartkeyboard.ime.voice.DrsVoiceInputBus
import com.drs.smartkeyboard.ime.voice.VoiceInputBar
import com.drs.smartkeyboard.ime.voice.VoiceUiState
import com.drs.smartkeyboard.keyboardManager
import org.drs.jetpref.datastore.model.collectAsState
import org.drs.lib.snygg.ui.SnyggIcon

@Composable
fun TextInputLayout(
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val keyboardManager by context.keyboardManager()

    val prefs by DrsPreferenceStore

    val state by keyboardManager.activeState.collectAsState()
    val evaluator by keyboardManager.activeEvaluator.collectAsState()

    InlineSuggestionsStyleCache()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .wrapContentHeight(),
    ) {
        // DRS v1.0.7: unified strip for all three systems - renders the
        // right tools per active display level (بسيط/تقني/مزدوج) and
        // supersedes the former technical-only toolbar.
        // DRS v1.8.0: the strip is now the TASKS BAR above the suggestions
        // strip for every user system, and its side-pull handle opens the
        // pinned-tools drawer over the keyboard area.
        DrsUnifiedStrip()
        // DRS v1.23.0: while the built-in voice recognizer owns a session
        // the live dictation bar replaces the Smartbar (same slot, same
        // theme elements) — partial transcript + cancel, keyboard alive.
        val voiceUiState by DrsVoiceInputBus.uiState.collectAsState()
        if (voiceUiState is VoiceUiState.Idle) {
            Smartbar()
        } else {
            VoiceInputBar()
        }
        // DRS v1.16.0: the strip slot editor (تغيير مهمة من العشر) swaps
        // the keyboard area the same way the drawer and the overflow do.
        val slotEditorOpen by DrsRuntimeState.stripSlotEditor.collectAsState()
        when {
            // The pinned-tools drawer has priority; the panels never
            // stack (opening one closes the others).
            state.isToolsDrawerVisible -> DrsToolsDrawerPanel()
            slotEditorOpen != null -> DrsStripSlotEditorPanel()
            state.isActionsOverflowVisible -> QuickActionsOverflowPanel()
            else -> Box {
                val incognitoDisplayMode by prefs.keyboard.incognitoDisplayMode.collectAsState()
                val showIncognitoIcon = evaluator.state.isIncognitoMode &&
                    incognitoDisplayMode == IncognitoDisplayMode.DISPLAY_BEHIND_KEYBOARD
                if (showIncognitoIcon) {
                    SnyggIcon(
                        DrsImeUi.IncognitoModeIndicator.elementName,
                        modifier = Modifier
                            .matchParentSize()
                            .align(Alignment.Center),
                        painter = painterResource(R.drawable.ic_incognito),
                    )
                }
                TextKeyboardLayout(evaluator = evaluator)
            }
        }
    }
}
