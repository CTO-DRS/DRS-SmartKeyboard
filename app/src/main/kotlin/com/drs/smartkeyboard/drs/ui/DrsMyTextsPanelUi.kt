/*
 * Copyright (C) 2026 The DRS Smart Keyboard Project
 */

package com.drs.smartkeyboard.drs.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.drs.smartkeyboard.R
import com.drs.smartkeyboard.clipboardManager
import com.drs.smartkeyboard.drs.DrsKeyboardHarakat
import com.drs.smartkeyboard.drs.DrsKeyboardHarakatKey
import com.drs.smartkeyboard.drs.DrsMyText
import com.drs.smartkeyboard.drs.DrsMyTexts
import com.drs.smartkeyboard.drs.DrsStore
import com.drs.smartkeyboard.drs.DrsSystems
import com.drs.smartkeyboard.editorInstance
import com.drs.smartkeyboard.ime.ImeUiMode
import com.drs.smartkeyboard.ime.input.LocalInputFeedbackController
import com.drs.smartkeyboard.ime.keyboard.DrsImeSizing
import com.drs.smartkeyboard.ime.clipboard.provider.ItemType
import com.drs.smartkeyboard.ime.text.key.KeyVariation
import com.drs.smartkeyboard.ime.text.keyboard.TextKeyData
import com.drs.smartkeyboard.keyboardManager
import com.drs.smartkeyboard.ime.theme.DrsImeUi
import com.drs.smartkeyboard.ime.window.LocalWindowController
import org.drs.lib.compose.stringRes
import org.drs.lib.snygg.ui.SnyggBox
import org.drs.lib.snygg.ui.SnyggColumn
import org.drs.lib.snygg.ui.SnyggText

/**
 * DRS v2.21.0 — لوحة نصوصي المحفوظة (the saved-texts panel) — the SIXTH
 * smart panel, built on the exact anatomy of the smart clipboard panel:
 * the shared header and switcher chips, a category chip bar (الكل + the
 * user's own categories), the saved texts in the deterministic order
 * (pinned first, then recency — [DrsMyTexts.order]), one honest tile per
 * row (tap = insert), and the real bottom row with hold-to-repeat delete.
 *
 * The doctrine holds:
 * - a saved text is USER-CURATED permanent content — the panel never
 *   captures anything implicitly; the only save action is an explicit
 *   tap on «احفظ ما في الحافظة», and the full CRUD lives in the
 *   manager screen (نصوصي in the app);
 * - templates ({date}/{time}/{hijri}/{clipboard}/{newline}/{cursor})
 *   expand at INSERT time through the shortcuts engine's one expansion
 *   contract, so {date} never lies;
 * - in a password field the panel shows NOTHING (الخصوصية المطلقة) —
 *   the clipboard panel's gate, mirrored;
 * - in incognito the panel still works (your own texts are yours to
 *   type) but records NO usage counter, exactly like every other DRS
 *   counter.
 */

@Composable
private fun MyTextsStateBox(
    messageRes: Int,
    accent: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier,
) {
    SnyggBox(
        elementName = DrsImeUi.ClipboardContent.elementName,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AdviceChip(label = stringRes(messageRes), accent = accent, leading = false)
        }
    }
}

@Composable
fun DrsMyTextsPanel(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val keyboardManager by context.keyboardManager()
    val editorInstance by context.editorInstance()
    val clipboardManager by context.clipboardManager()
    val drsState by DrsStore.state.collectAsState()
    val feedback = LocalInputFeedbackController.current

    RecordPanelOpen(ImeUiMode.MY_TEXTS)

    // The privacy gate: in a password field the panel shows NOTHING.
    val passwordField = keyboardManager.activeState.keyVariation == KeyVariation.PASSWORD

    // The current clip: TEXT items only — the honest source of the
    // explicit «احفظ ما في الحافظة» action.
    val primaryClip by clipboardManager.primaryClipFlow.collectAsState()
    val clipText = remember(primaryClip) {
        if (primaryClip?.type == ItemType.TEXT) primaryClip?.text.orEmpty() else ""
    }

    var selectedCategory by remember { mutableStateOf<String?>(null) } // null = الكل

    val systemSpec = DrsSystems.specOfName(DrsStore.state.value.userPath)
    val accent = if (isSystemInDarkTheme()) systemSpec.accentNight else systemSpec.accent
    val windowController = LocalWindowController.current
    val windowSpec by windowController.activeWindowSpec.collectAsState()
    val rowHeight = DrsImeSizing.keyboardRowBaseHeight

    fun insert(item: DrsMyText) {
        val expanded = DrsMyTexts.expand(item.text, clipText.ifBlank { null })
        val placesCursor = DrsMyTexts.hasCursorMarker(expanded)
        // The {cursor} marker contract is the shortcuts engine's own: commit
        // the text, then land the cursor at the marker's offset.
        val (finalText, cursorOffset) =
            if (placesCursor) DrsMyTexts.extractCursorMarker(expanded) else expanded to -1
        editorInstance.commitText(finalText)
        if (placesCursor && cursorOffset >= 0) {
            val selectionEnd = editorInstance.activeContent.selection.end
            val target = (selectionEnd - finalText.length + cursorOffset).coerceAtLeast(0)
            editorInstance.setSelection(target, target)
        }
        if (!keyboardManager.activeState.isIncognitoMode) {
            DrsMyTexts.recordUse()
        }
    }

    val ordered = remember(drsState.myTexts) { DrsMyTexts.order(drsState.myTexts) }
    val categories = remember(drsState.myTexts) { DrsMyTexts.categoriesOf(drsState.myTexts) }
    // A deleted category must never trap the panel in an empty filter.
    val effectiveCategory = if (selectedCategory in categories) selectedCategory else null
    val visible = remember(ordered, effectiveCategory) {
        if (effectiveCategory == null) {
            ordered
        } else {
            ordered.filter { it.category == effectiveCategory }
        }
    }
    val canSaveClip = clipText.isNotBlank() &&
        drsState.myTextsEnabled &&
        drsState.myTexts.size < DrsMyTexts.MAX_ITEMS

    SnyggColumn(
        modifier = modifier
            .fillMaxWidth()
            .height(DrsImeSizing.imeUiHeight()),
    ) {
        SmartPanelHeader(R.string.panel__mytexts__title, keyboardManager)
        PanelSwitcherChips(ImeUiMode.MY_TEXTS, keyboardManager, accent)

        when {
            passwordField -> MyTextsStateBox(R.string.panel__mytexts__hidden, accent, Modifier.weight(1f))
            !drsState.myTextsEnabled -> MyTextsStateBox(R.string.panel__mytexts__disabled, accent, Modifier.weight(1f))
            else -> {
                // شريط الفئات — الكل first, then the user's categories, plus
                // the explicit clipboard-save chip when a savable clip exists.
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    if (canSaveClip) {
                        AdviceChip(
                            label = stringRes(R.string.panel__mytexts__save_clip),
                            accent = accent,
                            leading = false,
                            onClick = { DrsMyTexts.add(clipText, "", "") },
                        )
                    }
                    AdviceChip(
                        label = stringRes(R.string.panel__mytexts__all),
                        accent = accent,
                        leading = effectiveCategory == null,
                        onClick = { selectedCategory = null },
                    )
                    categories.forEach { category ->
                        AdviceChip(
                            label = category,
                            accent = accent,
                            leading = category == effectiveCategory,
                            onClick = { selectedCategory = category },
                        )
                    }
                }

                // النصوص المحفوظة — one honest tile per row, tap = insert.
                if (visible.isEmpty()) {
                    MyTextsStateBox(R.string.panel__mytexts__empty, accent, Modifier.weight(1f))
                } else {
                    SnyggBox(
                        elementName = DrsImeUi.ClipboardContent.elementName,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .verticalScroll(rememberScrollState())
                                .padding(horizontal = windowSpec.keyMarginH),
                        ) {
                            visible.forEachIndexed { itemIndex, item ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 2.dp),
                                    horizontalArrangement = Arrangement.Start,
                                ) {
                                    AdviceChip(
                                        label = DrsMyTexts.previewLabel(item.text, item.label),
                                        accent = accent,
                                        leading = item.pinned,
                                        onClick = {
                                            feedback.keyPress()
                                            insert(item)
                                        },
                                        entranceIndex = itemIndex,
                                        entranceKey = visible,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // الصف السفلي الحقيقي — the real bottom row (hold-to-repeat
        // delete), the anatomy every smart panel shares.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(rowHeight)
                .padding(horizontal = windowSpec.keyMarginH),
            horizontalArrangement = Arrangement.spacedBy(windowSpec.keyMarginH * 2),
        ) {
            DrsKeyboardHarakat.ROWS.last().forEach { key ->
                val weight = if (key is DrsKeyboardHarakatKey.Space) 2.2f else 1f
                HarakatKeyboardKey(
                    key = key,
                    modifier = Modifier
                        .weight(weight)
                        .fillMaxHeight()
                        .padding(vertical = windowSpec.keyMarginV),
                    onPress = {
                        feedback.keyPress()
                        when (key) {
                            is DrsKeyboardHarakatKey.Literal -> editorInstance.commitText(key.text)
                            DrsKeyboardHarakatKey.Delete ->
                                keyboardManager.inputEventDispatcher.sendDownUp(TextKeyData.DELETE)
                            DrsKeyboardHarakatKey.Space ->
                                keyboardManager.inputEventDispatcher.sendDownUp(TextKeyData.SPACE)
                            DrsKeyboardHarakatKey.Enter ->
                                keyboardManager.inputEventDispatcher.sendDownUp(
                                    com.drs.smartkeyboard.ime.text.keyboard.TextKeyData(
                                        type = com.drs.smartkeyboard.ime.text.key.KeyType.ENTER_EDITING,
                                        code = com.drs.smartkeyboard.ime.text.key.KeyCode.ENTER,
                                        label = "enter",
                                    ),
                                )
                            DrsKeyboardHarakatKey.BackToLetters ->
                                keyboardManager.activeState.imeUiMode = ImeUiMode.TEXT
                            else -> {}
                        }
                    },
                    holdRepeat = key is DrsKeyboardHarakatKey.Delete,
                )
            }
        }
        Spacer(Modifier.height(4.dp))
    }
}
