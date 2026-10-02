/*
 * Copyright (C) 2025-2026 The DRS Smart Keyboard Project
 */

package com.drs.smartkeyboard.drs.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.drs.smartkeyboard.R
import com.drs.smartkeyboard.app.DrsPreferenceStore
import com.drs.smartkeyboard.drs.DrsKeyboardHarakat
import com.drs.smartkeyboard.drs.DrsKeyboardHarakatKey
import com.drs.smartkeyboard.drs.DrsNumberFieldClass
import com.drs.smartkeyboard.drs.DrsNumberPanelSmart
import com.drs.smartkeyboard.drs.DrsPanelOrder
import com.drs.smartkeyboard.drs.DrsRuntimeState
import com.drs.smartkeyboard.drs.DrsStore
import com.drs.smartkeyboard.drs.DrsSystems
import com.drs.smartkeyboard.drs.PanelUsageTracker
import com.drs.smartkeyboard.ime.ImeUiMode
import com.drs.smartkeyboard.ime.input.LocalInputFeedbackController
import com.drs.smartkeyboard.ime.keyboard.DrsImeSizing
import com.drs.smartkeyboard.ime.text.key.KeyCode
import com.drs.smartkeyboard.ime.text.key.KeyType
import com.drs.smartkeyboard.ime.text.keyboard.TextKeyData
import com.drs.smartkeyboard.ime.theme.DrsImeUi
import com.drs.smartkeyboard.ime.window.LocalWindowController
import com.drs.smartkeyboard.editorInstance
import com.drs.smartkeyboard.keyboardManager
import java.time.LocalDate
import java.time.chrono.HijrahDate
import org.drs.lib.compose.stringRes
import org.drs.jetpref.datastore.model.collectAsState
import org.drs.lib.snygg.ui.SnyggBox
import org.drs.lib.snygg.ui.SnyggColumn

/**
 * DRS v1.2.0 — لوحة الأرقام الذكية (the smart numbers panel) — the
 * fourth smart panel, built on the exact anatomy of the harakat board:
 * the shared header and switcher chips, a context bar whose chips
 * reorder themselves (the detected field context first, then the local
 * open counts), a ready-formats strip (phone, Gregorian + Hijri dates,
 * seven currencies — all computed by [DrsNumberPanelSmart] on device),
 * a 4-column grid rendered through the SAME themed key element the real
 * keys use ([HarakatKeyboardKey]), and the real bottom row with the
 * hold-to-repeat delete.
 *
 * The doctrine holds: every pick is a deterministic local computation —
 * the panel never invents a digit, never calls out, never records text
 * (only which TILE was pressed, and never in incognito).
 */

/** The persisted panel-usage namespace of the numbers panel (local only). */
private const val USAGE_PANEL_NUMBERS = "numbers"

/** The display order of the seven contexts (the catalogue order). */
private val NUMBER_CONTEXTS = listOf(
    DrsNumberFieldClass.GENERAL,
    DrsNumberFieldClass.PHONE,
    DrsNumberFieldClass.MONEY,
    DrsNumberFieldClass.DATE,
    DrsNumberFieldClass.OTP,
    DrsNumberFieldClass.MATH,
    DrsNumberFieldClass.ID,
)

/** The localized label of a context chip. */
@Composable
private fun contextLabel(context: DrsNumberFieldClass): Int = when (context) {
    DrsNumberFieldClass.GENERAL -> R.string.panel__numbers__ctx_general
    DrsNumberFieldClass.PHONE -> R.string.panel__numbers__ctx_phone
    DrsNumberFieldClass.MONEY -> R.string.panel__numbers__ctx_money
    DrsNumberFieldClass.DATE -> R.string.panel__numbers__ctx_date
    DrsNumberFieldClass.OTP -> R.string.panel__numbers__ctx_otp
    DrsNumberFieldClass.MATH -> R.string.panel__numbers__ctx_math
    DrsNumberFieldClass.ID -> R.string.panel__numbers__ctx_id
}

@Composable
fun DrsSmartNumberPanel(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val keyboardManager by context.keyboardManager()
    val editorInstance by context.editorInstance()
    val prefs by DrsPreferenceStore
    val feedback = LocalInputFeedbackController.current

    val recentsEnabled by prefs.panels.panelRecents.collectAsState()
    var recents by remember { mutableStateOf(DrsPanelUsageStore.load(context, USAGE_PANEL_NUMBERS)) }

    RecordPanelOpen(ImeUiMode.SMART_NUMBER)

    // The detected field context (raw EditorInfo truth set at input
    // start) leads the panel; the user can tap any other chip.
    val detected by DrsRuntimeState.numberFieldClass.collectAsState()
    var selected by remember(detected) { mutableStateOf(detected) }
    var commitStamp by remember { mutableIntStateOf(0) }

    fun commitText(text: String) {
        editorInstance.commitText(text)
        if (!keyboardManager.activeState.isIncognitoMode) {
            DrsPanelUsageStore.record(context, USAGE_PANEL_NUMBERS, text)
            recents = DrsPanelUsageStore.load(context, USAGE_PANEL_NUMBERS)
        }
        commitStamp++
    }

    fun applyKey(key: DrsKeyboardHarakatKey) {
        when (key) {
            is DrsKeyboardHarakatKey.Literal -> commitText(key.text)
            DrsKeyboardHarakatKey.BackToLetters ->
                keyboardManager.activeState.imeUiMode = ImeUiMode.TEXT
            DrsKeyboardHarakatKey.Delete ->
                keyboardManager.inputEventDispatcher.sendDownUp(TextKeyData.DELETE)
            DrsKeyboardHarakatKey.Space ->
                keyboardManager.inputEventDispatcher.sendDownUp(TextKeyData.SPACE)
            DrsKeyboardHarakatKey.Enter ->
                keyboardManager.inputEventDispatcher.sendDownUp(
                    TextKeyData(type = KeyType.ENTER_EDITING, code = KeyCode.ENTER, label = "enter"),
                )
            else -> {}
        }
    }

    // The ready formats of the selected context — recomputed after every
    // panel commit so the feed reads the fresh cursor window.
    val beforeText = remember(selected, commitStamp) {
        if (selected == DrsNumberFieldClass.PHONE || selected == DrsNumberFieldClass.MONEY) {
            editorInstance.run { activeContent.getTextBeforeCursor(48) }
        } else {
            ""
        }
    }
    val formats = remember(selected, commitStamp, beforeText) {
        when (selected) {
            DrsNumberFieldClass.PHONE ->
                DrsNumberPanelSmart.phoneFormats(DrsNumberPanelSmart.trailingDigits(beforeText))
            DrsNumberFieldClass.MONEY ->
                DrsNumberPanelSmart.currencyFormats(DrsNumberPanelSmart.trailingAmount(beforeText))
            DrsNumberFieldClass.DATE ->
                DrsNumberPanelSmart.gregorianFormats(LocalDate.now()) +
                    DrsNumberPanelSmart.hijriFormats(HijrahDate.now())
            else -> emptyList()
        }
    }
    val tiles = remember(selected) { DrsNumberPanelSmart.gridFor(selected) }

    // The context bar order: the selected context first (stable anchor),
    // then the rest by local usage — the same smart ordering the other
    // three panels obey.
    val contextOrder = remember(selected, recents) {
        if (recentsEnabled) {
            DrsPanelOrder.smartSwitcher(selected, recents, NUMBER_CONTEXTS)
        } else {
            NUMBER_CONTEXTS
        }
    }

    val systemSpec = DrsSystems.specOfName(DrsStore.state.value.userPath)
    val accent = if (isSystemInDarkTheme()) systemSpec.accentNight else systemSpec.accent
    val windowController = LocalWindowController.current
    val windowSpec by windowController.activeWindowSpec.collectAsState()
    val rowHeight = DrsImeSizing.keyboardRowBaseHeight

    SnyggColumn(
        modifier = modifier
            .fillMaxWidth()
            .height(DrsImeSizing.imeUiHeight()),
    ) {
        SmartPanelHeader(R.string.panel__numbers__title, keyboardManager)
        PanelSwitcherChips(ImeUiMode.SMART_NUMBER, keyboardManager, accent)

        // شريط السياقات — the context bar (selected first, usage-ordered).
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 2.dp)
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            contextOrder.forEach { candidate ->
                AdviceChip(
                    label = stringRes(contextLabel(candidate)),
                    accent = accent,
                    leading = candidate == selected,
                    onClick = {
                        feedback.keyPress()
                        selected = candidate
                    },
                )
            }
        }

        // شريط التنسيقات الجاهزة — the ready formats strip (hidden when
        // the context has nothing honest to offer).
        if (formats.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 2.dp)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                formats.forEach { format ->
                    AdviceChip(
                        label = format,
                        accent = accent,
                        leading = true,
                        onClick = {
                            feedback.keyPress()
                            commitText(format)
                        },
                    )
                }
            }
        }

        // الشبكة 4×4 — the grid, through the SAME themed key element.
        SnyggBox(
            elementName = DrsImeUi.ClipboardContent.elementName,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        ) {
            LazyVerticalGrid(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = windowSpec.keyMarginH),
                columns = GridCells.Fixed(4),
            ) {
                itemsIndexed(tiles, key = { index, _ -> "num_$index" }) { _, tile ->
                    HarakatKeyboardKey(
                        key = DrsKeyboardHarakatKey.Literal(tile),
                        modifier = Modifier
                            .aspectRatio(1.4f)
                            .padding(2.dp),
                        onPress = {
                            feedback.keyPress()
                            applyKey(DrsKeyboardHarakatKey.Literal(tile))
                        },
                    )
                }
            }
        }

        // الصف السفلي الحقيقي — the real bottom row (hold-to-repeat
        // delete), the anatomy the harakat board established.
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
                        applyKey(key)
                    },
                    holdRepeat = key is DrsKeyboardHarakatKey.Delete,
                )
            }
        }
        Spacer(Modifier.height(4.dp))
    }
}

/** The recents row of the numbers panel (used tiles, most-used first). */
internal fun numberPanelRecents(
    counts: Map<String, Int>,
    enabled: Boolean,
): List<String> {
    if (!enabled) return emptyList()
    val catalogue = DrsNumberFieldClass.entries.flatMap { DrsNumberPanelSmart.gridFor(it) }
    return PanelUsageTracker.topRecents(counts, catalogue)
}
