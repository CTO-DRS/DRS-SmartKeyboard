/*
 * Copyright (C) 2022-2025 The DRS Smart Keyboard Project
 */

package com.drs.smartkeyboard.ime.smartbar.quickaction

import android.annotation.SuppressLint
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import com.drs.smartkeyboard.app.DrsPreferenceStore
import com.drs.smartkeyboard.clipboardManager
import com.drs.smartkeyboard.drs.DrsRuntimeState
import com.drs.smartkeyboard.ime.smartbar.DrsContextualRank
import com.drs.smartkeyboard.ime.smartbar.DrsFieldKind
import com.drs.smartkeyboard.ime.smartbar.SmartbarLayout
import com.drs.smartkeyboard.ime.text.keyboard.TextKeyData
import com.drs.smartkeyboard.keyboardManager
import org.drs.jetpref.datastore.model.collectAsState
import org.drs.lib.snygg.ui.SnyggRow

internal val ToggleOverflowPanelAction = QuickAction.InsertKey(TextKeyData.TOGGLE_ACTIONS_OVERFLOW)

@SuppressLint("UnusedBoxWithConstraintsScope")
@Composable
fun QuickActionsRow(
    elementName: String,
    modifier: Modifier = Modifier,
) = with(LocalDensity.current) {
    val prefs by DrsPreferenceStore
    val context = LocalContext.current
    val keyboardManager by context.keyboardManager()

    val flipToggles by prefs.smartbar.flipToggles.collectAsState()
    val evaluator by keyboardManager.activeSmartbarEvaluator.collectAsState()
    val smartbarLayout by prefs.smartbar.layout.collectAsState()
    val actionArrangement by prefs.smartbar.actionArrangement.collectAsState()
    val sharedActionsExpanded by prefs.smartbar.sharedActionsExpanded.collectAsState()

    val dynamicActions = remember(smartbarLayout, actionArrangement) {
        if (smartbarLayout == SmartbarLayout.ACTIONS_ONLY && actionArrangement.stickyAction != null) {
            buildList {
                add(actionArrangement.stickyAction!!)
                addAll(actionArrangement.dynamicActions)
            }
        } else {
            actionArrangement.dynamicActions
        }
    }

    // DRS v2.11.0 «المُرتّب السياقي الصادق»: the row is ranked by the
    // focused field's declared kind BEFORE rendering — the user's own
    // actions only, a strict permutation of their manual arrangement
    // (same tiles, same count, zero flicker), every tile still judged by
    // its own evaluateEnabled verdict. GENERAL fields and a switched-off
    // master pref get the identity ranking — the base order IS the
    // context there. Signals are booleans read from the same sources the
    // tiles' own honesty (v2.2.2) uses: the live selection flag and the
    // primary clip's creation timestamp (no content, ever).
    val contextualRanking by prefs.smartbar.contextualRanking.collectAsState()
    val fieldKind by DrsRuntimeState.fieldKind.collectAsState()
    val activeState by keyboardManager.activeState.collectAsState()
    val clipboardManager by context.clipboardManager()
    val primaryClip by clipboardManager.primaryClipFlow.collectAsState()
    val contextualSignals = remember(
        fieldKind,
        contextualRanking,
        activeState.isSelectionMode,
        primaryClip,
    ) {
        val clip = primaryClip
        DrsContextualRank.Signals(
            hasSelection = activeState.isSelectionMode,
            clipboardFresh = clip != null &&
                System.currentTimeMillis() - clip.creationTimestampMs <=
                DrsContextualRank.FRESH_CLIPBOARD_WINDOW_MS,
        )
    }
    val rankedActions = if (!contextualRanking || fieldKind == DrsFieldKind.GENERAL) {
        dynamicActions
    } else {
        DrsContextualRank.rank(dynamicActions, fieldKind, contextualSignals)
    }
    val showOverflowAction = actionArrangement.stickyAction != null ||
        smartbarLayout != SmartbarLayout.SUGGESTIONS_ACTIONS_SHARED || !sharedActionsExpanded

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val width = constraints.maxWidth.toDp()
        val height = constraints.maxHeight.toDp()
        val numActionsToShow = ((width / height).toInt() - (if (showOverflowAction) 1 else 0)).coerceAtLeast(0)
        val visibleActions = rankedActions
            .subList(0, numActionsToShow.coerceAtMost(rankedActions.size))

        SideEffect {
            keyboardManager.smartbarVisibleDynamicActionsCount =
                if (smartbarLayout == SmartbarLayout.ACTIONS_ONLY && actionArrangement.stickyAction != null) {
                    numActionsToShow - 1
                } else {
                    numActionsToShow
                }.coerceAtLeast(0)
        }

        SnyggRow(
            elementName = elementName,
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            if (showOverflowAction && flipToggles) {
                QuickActionButton(ToggleOverflowPanelAction, evaluator)
            }
            for (action in visibleActions) {
                QuickActionButton(action, evaluator)
            }
            if (showOverflowAction && !flipToggles) {
                QuickActionButton(ToggleOverflowPanelAction, evaluator)
            }
        }
    }
}
