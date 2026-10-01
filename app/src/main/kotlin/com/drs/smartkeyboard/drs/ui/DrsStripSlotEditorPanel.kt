/*
 * Copyright (C) 2025-2026 The DRS Smart Keyboard Project
 */

package com.drs.smartkeyboard.drs.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.drs.smartkeyboard.R
import com.drs.smartkeyboard.drs.DrsHybridViewMode
import com.drs.smartkeyboard.drs.DrsRuntimeState
import com.drs.smartkeyboard.drs.DrsStore
import com.drs.smartkeyboard.drs.DrsSystems
import com.drs.smartkeyboard.drs.DrsToolGroup
import com.drs.smartkeyboard.drs.DrsUnified
import com.drs.smartkeyboard.drs.DrsUnifiedTools
import com.drs.smartkeyboard.ime.keyboard.DrsImeSizing
import com.drs.smartkeyboard.ime.theme.DrsImeUi
import com.drs.smartkeyboard.keyboardManager
import org.drs.lib.compose.rippleClickable
import org.drs.lib.compose.stringRes
import org.drs.lib.snygg.ui.SnyggBox
import org.drs.lib.snygg.ui.SnyggColumn
import org.drs.lib.snygg.ui.SnyggIcon
import org.drs.lib.snygg.ui.SnyggIconButton
import org.drs.lib.snygg.ui.SnyggRow
import org.drs.lib.snygg.ui.SnyggText

/**
 * DRS v1.16.0 — محرّر خانة شريط المهام (the strip slot editor).
 *
 * Opened by LONG-PRESSING one of the ten fixed tasks bar slots
 * («إمكانية تغيير المهام»). It replaces the keyboard area for a moment
 * (the same swap the tools drawer and the quick-actions overflow use)
 * and lists the whole catalogue by group: tapping any tool replaces the
 * task occupying that slot, materialized into the pinned list so the
 * swap stays exactly at that slot. The bar re-resolves on the very next
 * frame; closing is always possible from the header arrow.
 */
@Composable
fun DrsStripSlotEditorPanel(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val keyboardManager by context.keyboardManager()

    val drsState by DrsStore.state.collectAsState()
    val slotIndex by DrsRuntimeState.stripSlotEditor.collectAsState()
    val index = slotIndex ?: return

    val view = DrsUnifiedTools.viewForSystem(drsState.userPath, drsState.hybridViewMode)

    val systemSpec = DrsSystems.specOfName(drsState.userPath)
    val accent = if (isSystemInDarkTheme()) systemSpec.accentNight else systemSpec.accent

    fun close() {
        DrsRuntimeState.closeStripSlotEditor()
    }

    SnyggColumn(
        modifier = modifier
            .fillMaxWidth()
            .height(DrsImeSizing.imeUiHeight()),
    ) {
        // ---------------- header ----------------
        SnyggRow(
            DrsImeUi.ClipboardHeader.elementName,
            modifier = Modifier
                .fillMaxWidth()
                .height(DrsImeSizing.smartbarHeight),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val sizeModifier = Modifier
                .sizeIn(maxHeight = DrsImeSizing.smartbarHeight)
                .aspectRatio(1f)
            SnyggIconButton(
                elementName = DrsImeUi.ClipboardHeaderButton.elementName,
                onClick = { close() },
                modifier = sizeModifier,
            ) {
                SnyggIcon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    // DRS a11y/i18n/ux (r0-I): the header back button is icon-only.
                    contentDescription = stringRes(R.string.action__navigate_back),
                )
            }
            SnyggText(
                elementName = DrsImeUi.ClipboardHeaderText.elementName,
                modifier = Modifier.weight(1f),
                text = stringRes(
                    R.string.drs__strip_slot__title,
                    "slot" to (index + 1).toString(),
                ),
            )
            SnyggText(
                elementName = DrsImeUi.ClipboardHeaderText.elementName,
                modifier = Modifier.padding(end = 12.dp),
                text = stringRes(
                    R.string.drs__tools_drawer__count,
                    "count" to (index + 1).toString(),
                    "total" to DrsUnifiedTools.MAX_PINNED_TOOLS.toString(),
                ),
            )
        }

        // ---------------- hint ----------------
        SnyggText(
            elementName = DrsImeUi.ClipboardItemDescription.elementName,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
            text = stringRes(R.string.drs__strip_slot__hint),
        )

        // ---------------- body ----------------
        SnyggBox(
            DrsImeUi.ClipboardContent.elementName,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
            ) {
                DrsToolGroup.entries.forEach { group ->
                    val groupTools = DrsUnifiedTools.ALL.filter { it.group == group }
                    if (groupTools.isEmpty()) return@forEach
                    SnyggText(
                        elementName = DrsImeUi.ClipboardSubheader.elementName,
                        modifier = Modifier.padding(start = 16.dp, top = 10.dp, bottom = 2.dp),
                        text = stringRes(
                            when (group) {
                                DrsToolGroup.TOOLS -> R.string.drs__unified__group_tools
                                DrsToolGroup.EDITING -> R.string.drs__unified__group_editing
                                DrsToolGroup.CURSOR -> R.string.drs__unified__group_cursor
                            },
                        ),
                    )
                    groupTools.forEach { tool ->
                        SlotCandidateRow(
                            id = tool.id,
                            onPick = {
                                if (DrsUnified.replaceStripSlot(index, tool.id, view)) {
                                    close()
                                }
                            },
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
            }
        }
    }
}

/** One catalogue row of the slot editor: tap replaces the slot's task. */
@Composable
private fun SlotCandidateRow(
    id: String,
    onPick: () -> Unit,
) {
    SnyggBox(
        elementName = DrsImeUi.ClipboardItem.elementName,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 2.dp),
        clickAndSemanticsModifier = Modifier.rippleClickable { onPick() },
    ) {
        SnyggRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SnyggIcon(imageVector = iconForTool(id))
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                SnyggText(text = toolTitle(id))
                val desc = toolDesc(id)
                if (desc.isNotBlank()) {
                    SnyggText(
                        elementName = DrsImeUi.ClipboardItemDescription.elementName,
                        text = desc,
                    )
                }
            }
            SnyggText(
                elementName = DrsImeUi.ClipboardSubheader.elementName,
                modifier = Modifier.padding(start = 8.dp),
                text = stringRes(R.string.drs__strip_slot__pick),
            )
        }
    }
}
