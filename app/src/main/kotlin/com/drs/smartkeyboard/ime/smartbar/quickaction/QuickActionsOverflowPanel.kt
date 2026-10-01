/*
 * Copyright (C) 2022-2025 The DRS Smart Keyboard Project
 */

package com.drs.smartkeyboard.ime.smartbar.quickaction

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.drs.smartkeyboard.R
import com.drs.smartkeyboard.app.DrsPreferenceStore
import com.drs.smartkeyboard.drs.DrsStore
import com.drs.smartkeyboard.ime.keyboard.DrsImeSizing
import com.drs.smartkeyboard.ime.theme.DrsImeUi
import com.drs.smartkeyboard.ime.text.keyboard.TextKeyData
import com.drs.smartkeyboard.keyboardManager
import org.drs.jetpref.datastore.model.collectAsState
import org.drs.lib.compose.stringRes
import org.drs.lib.snygg.ui.SnyggBox
import org.drs.lib.snygg.ui.SnyggButton
import org.drs.lib.snygg.ui.SnyggText

@Composable
fun QuickActionsOverflowPanel() {
    val prefs by DrsPreferenceStore
    val context = LocalContext.current
    val keyboardManager by context.keyboardManager()

    val actionArrangement by prefs.smartbar.actionArrangement.collectAsState()
    val evaluator by keyboardManager.activeSmartbarEvaluator.collectAsState()

    val dynamicActions = actionArrangement.dynamicActions
    val dynamicActionsCountToShow = when {
        dynamicActions.isEmpty() -> 0
        else -> {
            (dynamicActions.size - keyboardManager.smartbarVisibleDynamicActionsCount).coerceIn(dynamicActions.indices)
        }
    }
    val visibleActions = remember(actionArrangement, dynamicActionsCountToShow) {
        actionArrangement.dynamicActions.takeLast(dynamicActionsCountToShow)
    }

    // DRS v1.0.5: "most used tools" — the three tools this user actually
    // presses the most, straight from the local anonymous usage counters.
    // Only known tool codes count; hidden and sticky actions are excluded
    // so the user's customization is always respected.
    val drsState by DrsStore.state.collectAsState()
    val mostUsedActions = remember(drsState.usage.toolUses, actionArrangement) {
        val stickyCode = actionArrangement.stickyAction?.keyData()?.code
        val hiddenCodes = actionArrangement.hiddenActions.mapTo(mutableSetOf()) { it.keyData().code }
        drsState.usage.toolUses.asSequence()
            .filter { (code, count) -> count > 0 && code in SmartToolCodes }
            .filter { (code, _) -> code != stickyCode && code !in hiddenCodes }
            .sortedByDescending { it.value }
            .take(3)
            .mapNotNull { (code, _) ->
                TextKeyData.getCodeInfoAsTextKeyData(code)?.let { QuickAction.InsertKey(it) }
            }
            .toList()
    }

    SnyggBox(
        elementName = DrsImeUi.SmartbarActionsOverflow.elementName,
        modifier = Modifier
            .fillMaxWidth()
            .height(DrsImeSizing.keyboardUiHeight()),
    ) {
        LazyVerticalGrid(
            modifier = Modifier
                .fillMaxWidth(),
            columns = GridCells.Adaptive(DrsImeSizing.smartbarHeight.coerceAtLeast(1.dp) * 2.2f),
        ) {
            if (mostUsedActions.isNotEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }, key = "most_used_header") {
                    SnyggText(
                        elementName = DrsImeUi.SmartbarActionsOverflow.elementName,
                        text = stringRes(R.string.smart_tools__most_used),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 12.dp, top = 8.dp, bottom = 2.dp),
                    )
                }
                items(mostUsedActions, key = { "most_used_${it.keyData().code}" }) { action ->
                    QuickActionButton(
                        action = action,
                        evaluator = evaluator,
                        type = QuickActionBarType.INTERACTIVE_TILE,
                    )
                }
            }
            items(visibleActions) { action ->
                QuickActionButton(
                    action = action,
                    evaluator = evaluator,
                    type = QuickActionBarType.INTERACTIVE_TILE,
                )
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                SnyggButton(
                    elementName = DrsImeUi.SmartbarActionsOverflowCustomizeButton.elementName,
                    onClick = { keyboardManager.activeState.isActionsEditorVisible = true },
                    modifier = Modifier
                        .wrapContentWidth(),
                ) {
                    SnyggText(
                        text = stringRes(R.string.quick_actions_overflow__customize_actions_button),
                    )
                }
            }
        }
    }
}
