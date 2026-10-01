/*
 * Copyright (C) 2021-2025 The DRS Smart Keyboard Project
 */

package com.drs.smartkeyboard.ime.keyboard

import com.drs.smartkeyboard.drs.DrsAdaptiveLayout
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.drs.smartkeyboard.app.DrsPreferenceStore
import com.drs.smartkeyboard.ime.nlp.NlpInlineAutofill
import com.drs.smartkeyboard.ime.smartbar.ExtendedActionsPlacement
import com.drs.smartkeyboard.ime.smartbar.InlineSuggestionsChipMargin
import com.drs.smartkeyboard.ime.smartbar.SmartbarLayout
import com.drs.smartkeyboard.ime.text.keyboard.TextKeyboard
import com.drs.smartkeyboard.ime.window.ImeWindowSpec
import com.drs.smartkeyboard.ime.window.LocalWindowController
import com.drs.smartkeyboard.keyboardManager
import org.drs.jetpref.datastore.model.collectAsState

private val LocalKeyboardRowBaseHeight = compositionLocalOf { 65.dp }
private val LocalSmartbarHeight = compositionLocalOf { 40.dp }

object DrsImeSizing {
    val keyboardRowBaseHeight: Dp
        @Composable
        @ReadOnlyComposable
        get() = LocalKeyboardRowBaseHeight.current

    val smartbarHeight: Dp
        @Composable
        @ReadOnlyComposable
        get() = LocalSmartbarHeight.current

    @Composable
    fun keyboardUiHeight(): Dp {
        val context = LocalContext.current
        val keyboardManager by context.keyboardManager()
        val evaluator by keyboardManager.activeEvaluator.collectAsState()
        val lastCharactersEvaluator by keyboardManager.lastCharactersEvaluator.collectAsState()
        val rowCount = when (evaluator.keyboard.mode) {
            KeyboardMode.CHARACTERS,
            KeyboardMode.NUMERIC_ADVANCED,
            KeyboardMode.SYMBOLS,
            KeyboardMode.SYMBOLS2 -> lastCharactersEvaluator.keyboard as TextKeyboard
            else -> evaluator.keyboard as TextKeyboard
        }.rowCount.coerceAtLeast(4)
        return (keyboardRowBaseHeight * rowCount)
    }

    @Composable
    fun rowCountAsState(): State<Int> {
        val context = LocalContext.current
        val keyboardManager by context.keyboardManager()
        val lastCharactersEvaluator by keyboardManager.lastCharactersEvaluator.collectAsState()
        // DRS p7 (E2-4): the raw layout rowCount was returned uncoerced, but every window-editor
        // consumer feeds it into ImeWindowSpec's toEffective/toBaseline which `require(4..6)` — a
        // layout with <4 or >6 rows threw during move/resize gestures. Coerce here, at the source
        // (same .coerce style as keyboardUiHeight above); the ImeWindowSpec requires stay as
        // defense in depth.
        return remember { derivedStateOf { (lastCharactersEvaluator.keyboard as TextKeyboard).rowCount.coerceIn(4, 6) } }
    }

    @Composable
    fun smartbarRowCountAsState(): State<Int> {
        val prefs by DrsPreferenceStore
        val smartbarEnabled by prefs.smartbar.enabled.collectAsState()
        val smartbarLayout by prefs.smartbar.layout.collectAsState()
        val extendedActionsExpanded by prefs.smartbar.extendedActionsExpanded.collectAsState()
        val extendedActionsPlacement by prefs.smartbar.extendedActionsPlacement.collectAsState()
        return remember {
            derivedStateOf {
                if (smartbarEnabled) {
                    if (smartbarLayout == SmartbarLayout.SUGGESTIONS_ACTIONS_EXTENDED && extendedActionsExpanded &&
                        extendedActionsPlacement != ExtendedActionsPlacement.OVERLAY_APP_UI) {
                        2
                    } else {
                        1
                    }
                } else {
                    0
                }
            }
        }
    }

    @Composable
    fun smartbarUiHeight(): Dp {
        val smartbarRowCount by smartbarRowCountAsState()
        return smartbarHeight * smartbarRowCount
    }

    @Composable
    fun imeUiHeight(): Dp {
        return keyboardUiHeight() + smartbarUiHeight()
    }
}

@Deprecated("TODO: move logic fully into ImeWindow impl")
@Composable
fun ProvideKeyboardRowBaseHeight(content: @Composable () -> Unit) {
    val windowController = LocalWindowController.current
    val density = LocalDensity.current
    val prefs by DrsPreferenceStore

    val windowSpec by windowController.activeWindowSpec.collectAsState()
    // DRS v1.2.0: the Smartbar height scale multiplies the provided
    // Smartbar row height, so the live Smartbar — and every consumer of
    // DrsImeSizing.smartbarHeight (Smartbar rows, clipboard/emoji headers,
    // autofill chip height) — really follows the preference. The window
    // resize math (ImeWindowSpec) stays untouched by design.
    val smartbarScalePercent by prefs.keyboard.smartbarHeightScalePercent.collectAsState()
    val smartbarScale = ImeWindowSpec.sanitizeSmartbarHeightScale(smartbarScalePercent)

    val heights by remember {
        derivedStateOf {
            val rowHeight = windowSpec.calcRowHeight(windowSpec.props.keyboardHeight)
            val smartbarRowHeight = windowSpec.calcSmartbarRowHeight(windowSpec.props.keyboardHeight)
            rowHeight to smartbarRowHeight
        }
    }
    val (rowHeightBase, smartbarRowHeight) = heights
    // DRS M2.5 — the adaptive row height: rows GROW with the system font
    // scale (half-strength, capped at 1.15) and NEVER shrink below the
    // design height (touch targets are an accessibility contract). The
    // smartbar keeps the static scale contract.
    val adaptiveScale = DrsAdaptiveLayout.rowScaleForFont(density.fontScale)
    val rowHeight = rowHeightBase * adaptiveScale
    val effectiveSmartbarRowHeight = smartbarRowHeight * smartbarScale

    SideEffect {
        val marginV = InlineSuggestionsChipMargin.calculateTopPadding() +
            InlineSuggestionsChipMargin.calculateBottomPadding()
        NlpInlineAutofill.suggestionsChipHeightPx = with(density) {
            (effectiveSmartbarRowHeight - marginV).roundToPx()
        }
    }

    CompositionLocalProvider(
        LocalKeyboardRowBaseHeight provides rowHeight,
        LocalSmartbarHeight provides effectiveSmartbarRowHeight,
    ) {
        content()
    }
}
