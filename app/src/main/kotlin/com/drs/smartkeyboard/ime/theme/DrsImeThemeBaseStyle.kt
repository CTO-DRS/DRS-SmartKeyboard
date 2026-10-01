/*
 * Copyright (C) 2022-2025 The DRS Smart Keyboard Project
 */

package com.drs.smartkeyboard.ime.theme

import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.drs.smartkeyboard.ime.input.InputModifierState
import com.drs.smartkeyboard.ime.input.InputShiftState
import com.drs.smartkeyboard.ime.text.key.KeyCode
import org.drs.lib.snygg.SnyggSelector
import org.drs.lib.snygg.SnyggStylesheet

val DrsImeThemeBaseStyle = SnyggStylesheet.v2 {
    defines {
        "--primary" to rgbaColor(76, 175, 80)
        "--primary-variant" to rgbaColor(56, 142, 60)
        "--secondary" to rgbaColor(245, 124, 0)
        "--secondary-variant" to rgbaColor(230, 81, 0)
        "--background" to rgbaColor(33, 33, 33)
        "--background-variant" to rgbaColor(44, 44, 44)
        "--surface" to rgbaColor(66, 66, 66)
        "--surface-variant" to rgbaColor(97, 97, 97)

        "--on-primary" to rgbaColor(240, 240, 240)
        "--on-background" to rgbaColor(255, 255, 255)
        // DRS a11y/i18n/ux (r0-I): contrast on the dark base background raised
        // from ~1.9:1 to ~3.5:1 (rgb(128,128,128) against both rgb(33,33,33)
        // and the rgb(44,44,44) background-variant tile surface).
        "--on-background-disabled" to rgbaColor(128, 128, 128)
        "--on-surface" to rgbaColor(255, 255, 255)
        // DRS a11y/i18n/ux (r0-I): referenced by the KeyHint style but never
        // defined — the var() silently resolved to the full-strength on-surface
        // foreground, so key hints rendered at full contrast. Defined now as the
        // intended subdued hint tone.
        "--on-surface-variant" to rgbaColor(200, 200, 200)

        "--shape" to roundedCornerShape(8.dp)
        "--shape-variant" to roundedCornerShape(12.dp)
    }

    DrsImeUi.Window.elementName {
        background = `var`("--background")
        foreground = `var`("--on-background")
    }

    DrsImeUi.Key.elementName {
        background = `var`("--surface")
        foreground = `var`("--on-surface")
        fontSize = fontSize(22.sp)
        shadowElevation = size(2.dp)
        shape = `var`("--shape")
        textMaxLines = textMaxLines(1)
    }
    DrsImeUi.Key.elementName(selector = SnyggSelector.PRESSED) {
        background = `var`("--surface-variant")
        foreground = `var`("--on-surface")
    }
    DrsImeUi.Key.elementName(DrsImeUi.Attr.Code to listOf(KeyCode.ENTER)) {
        background = `var`("--primary")
        foreground = `var`("--on-surface")
    }
    DrsImeUi.Key.elementName(DrsImeUi.Attr.Code to listOf(KeyCode.ENTER), selector = SnyggSelector.PRESSED) {
        background = `var`("--primary-variant")
        foreground = `var`("--on-surface")
    }
    DrsImeUi.Key.elementName(DrsImeUi.Attr.Code to listOf(KeyCode.SPACE)) {
        background = `var`("--surface")
        foreground = `var`("--on-surface")
        fontSize = fontSize(12.sp)
        textOverflow = textOverflow(TextOverflow.Ellipsis)
    }
    DrsImeUi.Key.elementName(DrsImeUi.Attr.Code to listOf(
        KeyCode.VIEW_CHARACTERS,
        KeyCode.VIEW_SYMBOLS,
        KeyCode.VIEW_SYMBOLS2,
    )) {
        fontSize = fontSize(18.sp)
    }
    DrsImeUi.Key.elementName(DrsImeUi.Attr.Code to listOf(
        KeyCode.VIEW_NUMERIC,
        KeyCode.VIEW_NUMERIC_ADVANCED,
    )) {
        fontSize = fontSize(12.sp)
    }
    DrsImeUi.Key.elementName(DrsImeUi.Attr.Code to listOf(KeyCode.VIEW_NUMERIC_ADVANCED)) {
        textMaxLines = textMaxLines(2)
    }
    DrsImeUi.Key.elementName(
        DrsImeUi.Attr.Code to listOf(KeyCode.SHIFT),
        DrsImeUi.Attr.ShiftState to listOf(InputShiftState.CAPS_LOCK.toString()),
    ) {
        foreground = rgbaColor(255, 152, 0)
    }
    // DRS v1.23.0: the modifier family gets its face. A CTRL/ALT/FN key on
    // any layout lights up while its latch is armed — light blue for a
    // one-shot latch (same semantics as a mechanical LED), the shift
    // caps-lock orange for a lock. The latch state arrives through the
    // CtrlState/AltState/FnState attributes (TextKeyButton → theme query).
    DrsImeUi.Key.elementName(
        DrsImeUi.Attr.Code to listOf(KeyCode.CTRL, KeyCode.CTRL_LOCK),
        DrsImeUi.Attr.CtrlState to listOf(InputModifierState.LATCHED.toString()),
    ) {
        foreground = rgbaColor(79, 195, 247)
    }
    DrsImeUi.Key.elementName(
        DrsImeUi.Attr.Code to listOf(KeyCode.CTRL, KeyCode.CTRL_LOCK),
        DrsImeUi.Attr.CtrlState to listOf(InputModifierState.LOCKED.toString()),
    ) {
        foreground = rgbaColor(255, 152, 0)
    }
    DrsImeUi.Key.elementName(
        DrsImeUi.Attr.Code to listOf(KeyCode.ALT, KeyCode.ALT_LOCK),
        DrsImeUi.Attr.AltState to listOf(InputModifierState.LATCHED.toString()),
    ) {
        foreground = rgbaColor(79, 195, 247)
    }
    DrsImeUi.Key.elementName(
        DrsImeUi.Attr.Code to listOf(KeyCode.ALT, KeyCode.ALT_LOCK),
        DrsImeUi.Attr.AltState to listOf(InputModifierState.LOCKED.toString()),
    ) {
        foreground = rgbaColor(255, 152, 0)
    }
    DrsImeUi.Key.elementName(
        DrsImeUi.Attr.Code to listOf(KeyCode.FN, KeyCode.FN_LOCK),
        DrsImeUi.Attr.FnState to listOf(InputModifierState.LATCHED.toString()),
    ) {
        foreground = rgbaColor(79, 195, 247)
    }
    DrsImeUi.Key.elementName(
        DrsImeUi.Attr.Code to listOf(KeyCode.FN, KeyCode.FN_LOCK),
        DrsImeUi.Attr.FnState to listOf(InputModifierState.LOCKED.toString()),
    ) {
        foreground = rgbaColor(255, 152, 0)
    }
    DrsImeUi.KeyHint.elementName {
        background = rgbaColor(0, 0, 0, 0f)
        foreground = `var`("--on-surface-variant")
        fontFamily = genericFontFamily(FontFamily.Monospace)
        fontSize = fontSize(12.sp)
        padding = padding(0.dp, 1.dp, 1.dp, 0.dp)
        textMaxLines = textMaxLines(1)
    }
    DrsImeUi.KeyPopupBox.elementName {
        background = rgbaColor(117, 117, 117)
        foreground = `var`("--on-surface")
        fontSize = fontSize(22.sp)
        shape = `var`("--shape")
        shadowElevation = size(2.dp)
    }
    DrsImeUi.KeyPopupElement.elementName(selector = SnyggSelector.FOCUS) {
        background = rgbaColor(189, 189, 189)
        shape = `var`("--shape")
    }
    DrsImeUi.KeyPopupExtendedIndicator.elementName {
        fontSize = fontSize(16.sp)
    }

    DrsImeUi.Smartbar.elementName {
        fontSize = fontSize(18.sp)
    }
    DrsImeUi.SmartbarSharedActionsToggle.elementName {
        background = `var`("--surface")
        foreground = `var`("--on-surface")
        margin = padding(6.dp)
        shape = circleShape()
        shadowElevation = size(2.dp)
    }
    DrsImeUi.SmartbarExtendedActionsToggle.elementName {
        background = rgbaColor(0, 0, 0, 0f)
        foreground = rgbaColor(144, 144, 144)
        margin = padding(6.dp)
        shape = circleShape()
    }
    DrsImeUi.SmartbarActionKey.elementName {
        background = rgbaColor(0, 0, 0, 0f)
        foreground = rgbaColor(220, 220, 220)
        shape = `var`("--shape")
    }
    DrsImeUi.SmartbarActionKey.elementName(selector = SnyggSelector.DISABLED) {
        foreground = `var`("--on-background-disabled")
    }

    DrsImeUi.SmartbarActionsOverflow.elementName {
        margin = padding(4.dp)
    }
    DrsImeUi.SmartbarActionsOverflowCustomizeButton.elementName {
        background = `var`("--primary")
        foreground = `var`("--on-primary")
        fontSize = fontSize(14.sp)
        margin = padding(0.dp, 8.dp, 0.dp, 0.dp)
        shape = roundedCornerShape(24.dp)
    }
    DrsImeUi.SmartbarActionTile.elementName {
        background = `var`("--background-variant")
        foreground = `var`("--on-background")
        fontSize = fontSize(14.sp)
        margin = padding(4.dp)
        padding = padding(4.dp)
        shape = roundedCornerShape(20)
        textAlign = textAlign(TextAlign.Center)
        textMaxLines = textMaxLines(2)
        textOverflow = textOverflow(TextOverflow.Ellipsis)
    }
    DrsImeUi.SmartbarActionTile.elementName(selector = SnyggSelector.DISABLED) {
        foreground = `var`("--on-background-disabled")
    }
    DrsImeUi.SmartbarActionTileIcon.elementName {
        fontSize = fontSize(24.sp)
        margin = padding(0.dp, 0.dp, 0.dp, 8.dp)
    }

    DrsImeUi.SmartbarActionsEditor.elementName {
        background = `var`("--background")
        foreground = `var`("--on-background")
        shape = roundedCornerShape(24.dp, 24.dp, 0.dp, 0.dp)
    }
    DrsImeUi.SmartbarActionsEditorHeader.elementName {
        background = `var`("--surface")
        foreground = `var`("--on-surface")
        fontSize = fontSize(16.sp)
        textMaxLines = textMaxLines(1)
        textOverflow = textOverflow(TextOverflow.Ellipsis)
    }
    DrsImeUi.SmartbarActionsEditorHeaderButton.elementName {
        margin = padding(4.dp)
        shape = circleShape()
    }
    DrsImeUi.SmartbarActionsEditorSubheader.elementName {
        foreground = `var`("--secondary")
        fontSize = fontSize(16.sp)
        fontWeight = fontWeight(FontWeight.Bold)
        padding = padding(12.dp, 16.dp, 12.dp, 8.dp)
        textMaxLines = textMaxLines(1)
        textOverflow = textOverflow(TextOverflow.Ellipsis)
    }
    DrsImeUi.SmartbarActionsEditorTileGrid.elementName {
        margin = padding(4.dp, 0.dp)
    }
    DrsImeUi.SmartbarActionsEditorTile.elementName {
        margin = padding(4.dp)
        padding = padding(8.dp)
        textAlign = textAlign(TextAlign.Center)
        textMaxLines = textMaxLines(2)
        textOverflow = textOverflow(TextOverflow.Ellipsis)
    }
    DrsImeUi.SmartbarActionsEditorTile.elementName(DrsImeUi.Attr.Code to listOf(KeyCode.NOOP)) {
        foreground = `var`("--on-background-disabled")
    }
    DrsImeUi.SmartbarActionsEditorTile.elementName(DrsImeUi.Attr.Code to listOf(KeyCode.DRAG_MARKER)) {
        foreground = rgbaColor(255, 0, 0)
    }

    DrsImeUi.SmartbarCandidateWord.elementName {
        background = rgbaColor(0, 0, 0, 0f)
        foreground = `var`("--on-background")
        fontSize = fontSize(14.sp)
        margin = padding(4.dp)
        padding = padding(8.dp, 0.dp)
        shape = rectangleShape()
        textMaxLines = textMaxLines(1)
        textOverflow = textOverflow(TextOverflow.Ellipsis)
    }
    DrsImeUi.SmartbarCandidateWord.elementName(selector = SnyggSelector.PRESSED) {
        background = `var`("--surface")
        foreground = `var`("--on-surface")
    }
    DrsImeUi.SmartbarCandidateWordSecondaryText.elementName {
        fontSize = fontSize(8.sp)
        margin = padding(0.dp, 2.dp, 0.dp, 0.dp)
    }
    DrsImeUi.SmartbarCandidateClip.elementName {
        background = rgbaColor(0, 0, 0, 0f)
        foreground = rgbaColor(220, 220, 220)
        fontSize = fontSize(14.sp)
        margin = padding(4.dp)
        padding = padding(8.dp, 0.dp)
        shape = roundedCornerShape(8)
        textMaxLines = textMaxLines(1)
        textOverflow = textOverflow(TextOverflow.Ellipsis)
    }
    DrsImeUi.SmartbarCandidateClip.elementName(selector = SnyggSelector.PRESSED) {
        background = `var`("--surface")
        foreground = `var`("--on-surface")
    }
    DrsImeUi.SmartbarCandidateClipIcon.elementName {
        margin = padding(0.dp, 0.dp, 4.dp, 0.dp)
    }
    DrsImeUi.SmartbarCandidateSpacer.elementName {
        foreground = rgbaColor(255, 255, 255, 0.25f)
    }

    DrsImeUi.ClipboardHeader.elementName {
        foreground = `var`("--on-background")
        fontSize = fontSize(16.sp)
    }
    DrsImeUi.ClipboardSubheader.elementName {
        fontSize = fontSize(14.sp)
        margin = padding(6.dp)
    }
    DrsImeUi.ClipboardContent.elementName {
        padding = padding(10.dp)
    }
    DrsImeUi.ClipboardItem.elementName {
        background = `var`("--surface")
        foreground = `var`("--on-surface")
        fontSize = fontSize(14.sp)
        margin = padding(4.dp)
        padding = padding(12.dp, 8.dp)
        shape = `var`("--shape-variant")
        shadowElevation = size(2.dp)
        textMaxLines = textMaxLines(10)
        textOverflow = textOverflow(TextOverflow.Ellipsis)
    }
    DrsImeUi.ClipboardItemPopup.elementName {
        background = `var`("--surface")
        foreground = `var`("--on-surface")
        fontSize = fontSize(14.sp)
        margin = padding(4.dp)
        padding = padding(12.dp, 8.dp)
        shape = `var`("--shape-variant")
        shadowElevation = size(2.dp)
    }
    DrsImeUi.ClipboardItemActions.elementName {
        background = `var`("--surface")
        foreground = `var`("--on-surface")
        margin = padding(4.dp)
        shape = `var`("--shape-variant")
        shadowElevation = size(2.dp)
    }
    DrsImeUi.ClipboardItemAction.elementName {
        fontSize = fontSize(16.sp)
        padding = padding(12.dp)
    }
    DrsImeUi.ClipboardItemActionText.elementName {
        margin = padding(8.dp, 0.dp, 0.dp, 0.dp)
    }
    DrsImeUi.ClipboardHistoryDisabledButton.elementName {
        background = `var`("--primary")
        foreground = `var`("--on-primary")
        shape = roundedCornerShape(24.dp)
    }

    DrsImeUi.MediaEmojiKey.elementName {
        background = rgbaColor(0, 0, 0, 0f)
        foreground = `var`("--on-background")
        fontSize = fontSize(22.sp)
        shape = `var`("--shape")
    }
    DrsImeUi.MediaEmojiKey.elementName(selector = SnyggSelector.PRESSED) {
        background = `var`("--surface")
        foreground = `var`("--on-surface")
    }

    DrsImeUi.GlideTrail.elementName {
        foreground = `var`("--primary")
    }

    DrsImeUi.InlineAutofillChip.elementName {
        background = `var`("--surface")
        foreground = `var`("--on-surface")
    }

    DrsImeUi.IncognitoModeIndicator.elementName {
        foreground = rgbaColor(255, 255, 255, 0.067f)
    }

    DrsImeUi.OneHandedPanel.elementName {
        background = rgbaColor(27, 94, 32)
        foreground = rgbaColor(238, 238, 238)
    }

    DrsImeUi.SubtypePanel.elementName {
        background = `var`("--background")
        foreground = `var`("--on-background")
        shape = roundedCornerShape(24.dp, 24.dp, 0.dp, 0.dp)
    }
    DrsImeUi.SubtypePanelHeader.elementName {
        background = `var`("--surface")
        foreground = `var`("--on-surface")
        fontSize = fontSize(18.sp)
        padding = padding(12.dp)
        textAlign = textAlign(TextAlign.Center)
        textMaxLines = textMaxLines(1)
        textOverflow = textOverflow(TextOverflow.Ellipsis)
    }
    DrsImeUi.SubtypePanelListItem.elementName {
        fontSize = fontSize(16.sp)
        padding = padding(16.dp)
    }
    DrsImeUi.SubtypePanelListItemIconLeading.elementName {
        fontSize = fontSize(24.sp)
        padding = padding(0.dp, 0.dp, 16.dp, 0.dp)
    }
    DrsImeUi.SubtypePanelListItemText.elementName {
        textMaxLines = textMaxLines(1)
        textOverflow = textOverflow(TextOverflow.Ellipsis)
    }
}
