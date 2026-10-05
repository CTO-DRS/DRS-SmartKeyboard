/*
 * Copyright (C) 2025-2026 The DRS Smart Keyboard Project
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.drs.smartkeyboard.R
import com.drs.smartkeyboard.app.DrsPreferenceStore
import com.drs.smartkeyboard.clipboardManager
import com.drs.smartkeyboard.drs.DrsClipContentClass
import com.drs.smartkeyboard.drs.DrsClipContentSmart
import com.drs.smartkeyboard.drs.DrsKeyboardHarakat
import com.drs.smartkeyboard.drs.DrsKeyboardHarakatKey
import com.drs.smartkeyboard.drs.DrsPanelOrder
import com.drs.smartkeyboard.drs.DrsStore
import com.drs.smartkeyboard.drs.DrsSystems
import com.drs.smartkeyboard.drs.PanelUsageTracker
import com.drs.smartkeyboard.editorInstance
import com.drs.smartkeyboard.ime.ImeUiMode
import com.drs.smartkeyboard.ime.input.LocalInputFeedbackController
import com.drs.smartkeyboard.ime.keyboard.DrsImeSizing
import com.drs.smartkeyboard.ime.clipboard.provider.ItemType
import com.drs.smartkeyboard.ime.text.key.KeyCode
import com.drs.smartkeyboard.ime.text.key.KeyType
import com.drs.smartkeyboard.ime.text.key.KeyVariation
import com.drs.smartkeyboard.ime.text.keyboard.TextKeyData
import com.drs.smartkeyboard.keyboardManager
import com.drs.smartkeyboard.ime.theme.DrsImeUi
import com.drs.smartkeyboard.ime.window.LocalWindowController
import org.drs.lib.compose.stringRes
import org.drs.jetpref.datastore.model.collectAsState
import org.drs.lib.snygg.ui.SnyggBox
import org.drs.lib.snygg.ui.SnyggColumn
import org.drs.lib.snygg.ui.SnyggText

/**
 * DRS v1.3.0 — لوحة الحافظة الذكية (the smart clipboard panel) — the
 * FIFTH smart panel, built on the exact anatomy of the harakat/numbers
 * boards: the shared header and switcher chips, an honest one-line
 * preview of the copied content, a class bar whose chips reorder
 * themselves (the detected class first, then the local usage counts),
 * the ready variants computed by [DrsClipContentSmart] on device (the
 * clean link, the Saudi phone forms, the extracted code, the seven
 * currencies, the digit flip), and the real bottom row with the
 * hold-to-repeat delete.
 *
 * The doctrine holds: every tile is a deterministic local computation —
 * the panel never invents content, never calls out, never records text
 * (only which TILE was pressed, and never in incognito) — and in a
 * password field the panel refuses to read the clipboard at all.
 */

/** The persisted panel-usage namespace of the clipboard panel (local only). */
private const val USAGE_PANEL_CLIPBOARD = "clipboard"

/** The display order of the seven content classes (the catalogue order). */
private val CLIP_CLASSES = listOf(
    DrsClipContentClass.URL,
    DrsClipContentClass.EMAIL,
    DrsClipContentClass.PHONE,
    DrsClipContentClass.OTP,
    DrsClipContentClass.AMOUNT,
    DrsClipContentClass.NUMBER,
    DrsClipContentClass.TEXT,
)

/** The localized label of a class chip. */
@Composable
private fun classLabel(contentClass: DrsClipContentClass): Int = when (contentClass) {
    DrsClipContentClass.URL -> R.string.panel__clipboard__cls_url
    DrsClipContentClass.EMAIL -> R.string.panel__clipboard__cls_email
    DrsClipContentClass.PHONE -> R.string.panel__clipboard__cls_phone
    DrsClipContentClass.OTP -> R.string.panel__clipboard__cls_otp
    DrsClipContentClass.AMOUNT -> R.string.panel__clipboard__cls_amount
    DrsClipContentClass.NUMBER -> R.string.panel__clipboard__cls_number
    DrsClipContentClass.TEXT -> R.string.panel__clipboard__cls_text
}

@Composable
private fun HonestStateBox(
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
fun DrsSmartClipboardPanel(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val keyboardManager by context.keyboardManager()
    val editorInstance by context.editorInstance()
    val clipboardManager by context.clipboardManager()
    val prefs by DrsPreferenceStore
    val feedback = LocalInputFeedbackController.current

    val recentsEnabled by prefs.panels.panelRecents.collectAsState()
    var recents by remember { mutableStateOf(DrsPanelUsageStore.load(context, USAGE_PANEL_CLIPBOARD)) }

    RecordPanelOpen(ImeUiMode.SMART_CLIPBOARD)

    // The current clip: TEXT items only (media clips have no honest text).
    val primaryClip by clipboardManager.primaryClipFlow.collectAsState()
    val clipText = remember(primaryClip) {
        if (primaryClip?.type == ItemType.TEXT) primaryClip?.text.orEmpty() else ""
    }

    // The privacy gate: in a password field the clipboard is NEVER read.
    val passwordField = keyboardManager.activeState.keyVariation == KeyVariation.PASSWORD

    var commitStamp by remember { mutableIntStateOf(0) }
    val analysis = remember(clipText, commitStamp) {
        if (clipText.isBlank()) null else DrsClipContentSmart.analyze(clipText)
    }
    var selected by remember(analysis?.contentClass) {
        mutableStateOf(analysis?.contentClass ?: DrsClipContentClass.TEXT)
    }
    val variants = remember(selected, analysis, commitStamp) {
        if (analysis == null) emptyList() else DrsClipContentSmart.variantsFor(selected, clipText)
    }
    val preview = remember(clipText) { DrsClipContentSmart.previewLabel(clipText) }

    fun commitText(text: String) {
        editorInstance.commitText(text)
        if (!keyboardManager.activeState.isIncognitoMode) {
            DrsPanelUsageStore.record(context, USAGE_PANEL_CLIPBOARD, text)
            recents = DrsPanelUsageStore.load(context, USAGE_PANEL_CLIPBOARD)
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

    // The class bar order: the selected class first (stable anchor), then
    // the rest by local usage — the same smart ordering the other four
    // panels obey.
    val classOrder = remember(selected, recents, recentsEnabled) {
        if (recentsEnabled) {
            DrsPanelOrder.smartSwitcher(selected, recents, CLIP_CLASSES)
        } else {
            CLIP_CLASSES
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
        SmartPanelHeader(R.string.panel__clipboard__title, keyboardManager)
        PanelSwitcherChips(ImeUiMode.SMART_CLIPBOARD, keyboardManager, accent)

        when {
            passwordField -> HonestStateBox(R.string.panel__clipboard__hidden, accent, Modifier.weight(1f))
            analysis == null -> HonestStateBox(R.string.panel__clipboard__empty, accent, Modifier.weight(1f))
            else -> {
                // سطر المعاينة — the honest one-line preview of the clip.
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    AdviceChip(label = preview, accent = accent, leading = true)
                }

                // شريط الفئات — the class bar (selected first, usage-ordered).
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    classOrder.forEachIndexed { classIndex, candidate ->
                        AdviceChip(
                            label = stringRes(classLabel(candidate)),
                            accent = accent,
                            leading = candidate == selected,
                            onClick = {
                                feedback.keyPress()
                                selected = candidate
                            },
                            // DRS v2.3.0: the class bar reads as one capped
                            // wave whenever the usage-ordered set changes.
                            entranceIndex = classIndex,
                            entranceKey = classOrder,
                        )
                    }
                }

                // التنويعات الجاهزة — the ready variants, one honest tile
                // per row (long links stay readable), tap = insert.
                SnyggBox(
                    elementName = DrsImeUi.ClipboardContent.elementName,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                ) {
                    if (variants.isEmpty()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.Center,
                        ) {
                            AdviceChip(
                                label = stringRes(R.string.panel__clipboard__no_variants),
                                accent = accent,
                                leading = false,
                            )
                        }
                    } else {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .verticalScroll(rememberScrollState())
                                .padding(horizontal = windowSpec.keyMarginH),
                        ) {
                            variants.forEachIndexed { variantIndex, variant ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 2.dp),
                                    horizontalArrangement = Arrangement.Start,
                                ) {
                                    AdviceChip(
                                        label = variant,
                                        accent = accent,
                                        leading = true,
                                        onClick = {
                                            feedback.keyPress()
                                            commitText(variant)
                                        },
                                        // DRS v2.3.0: one capped wave per new
                                        // variants set — never a pop-in.
                                        entranceIndex = variantIndex,
                                        entranceKey = variants,
                                    )
                                }
                            }
                        }
                    }
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

/** The recents feed of the clipboard panel (used tiles, most-used first). */
internal fun clipboardPanelRecents(
    counts: Map<String, Int>,
    enabled: Boolean,
): List<String> {
    if (!enabled) return emptyList()
    val catalogue = CLIP_CLASSES.map { it.name }
    return PanelUsageTracker.topRecents(counts, catalogue)
}
