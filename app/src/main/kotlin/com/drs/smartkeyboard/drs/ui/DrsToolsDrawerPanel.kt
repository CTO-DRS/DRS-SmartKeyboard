/*
 * Copyright (C) 2021-2025 The DRS Smart Keyboard Project
 * Copyright (C) 2025 DRS Smart Keyboard contributors
 */

package com.drs.smartkeyboard.drs.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.drs.smartkeyboard.R
import com.drs.smartkeyboard.drs.DrsHybridViewMode
import com.drs.smartkeyboard.drs.DrsSystems
import com.drs.smartkeyboard.drs.DrsUserPath
import com.drs.smartkeyboard.drs.DrsStore
import com.drs.smartkeyboard.drs.DrsToolGroup
import com.drs.smartkeyboard.drs.DrsUnified
import com.drs.smartkeyboard.drs.DrsUnifiedTools
import com.drs.smartkeyboard.ime.keyboard.DrsImeSizing
import com.drs.smartkeyboard.ime.theme.DrsImeUi
import com.drs.smartkeyboard.keyboardManager
import org.drs.lib.android.showShortToastSync
import org.drs.lib.compose.rippleClickable
import org.drs.lib.compose.stringRes
import org.drs.lib.snygg.ui.SnyggBox
import org.drs.lib.snygg.ui.SnyggColumn
import org.drs.lib.snygg.ui.SnyggIcon
import org.drs.lib.snygg.ui.SnyggIconButton
import org.drs.lib.snygg.ui.SnyggRow
import org.drs.lib.snygg.ui.SnyggText

/**
 * DRS v1.8.0 — درج المهام المثبتة (the pinned-tools drawer).
 *
 * Opened by the side-pull handle (زر السحب الجانبي) that now leads the
 * tasks bar above the suggestions strip. It replaces the keyboard area
 * for a moment (the same swap the quick-actions overflow uses) and gives
 * EVERY user system — العادي والتقني وكلاهما — direct, in-keyboard
 * control over the bar:
 *  - «المثبتة الآن»: the pinned tools in their strip order, with real
 *    reorder (up/down) and unpin controls.
 *  - «جميع المهام»: the whole catalogue filtered by group, where pinning
 *    a tool also guarantees it is visible on the CURRENT level (a
 *    technical-only tool pinned from the simple level widens its view
 *    override via [DrsUnifiedTools.ensureVisibleOverride]), plus
 *    show/hide per tool.
 *  - the tasks-bar master switch and the reset-to-defaults action.
 *
 * Every control mutates DrsState through the same [DrsUnified] helpers
 * the settings manager uses — nothing is simulated, everything survives
 * restarts, and the strip itself re-resolves on the very next frame.
 */

/** Filter of the all-tools list (mirrors the catalogue groups + all). */
private enum class DrawerFilter(val labelRes: Int) {
    ALL(R.string.drs__tools_drawer__filter_all),
    TOOLS(R.string.drs__unified__group_tools),
    EDITING(R.string.drs__unified__group_editing),
    CURSOR(R.string.drs__unified__group_cursor),
}

@Composable
fun DrsToolsDrawerPanel(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val keyboardManager by context.keyboardManager()

    val drsState by DrsStore.state.collectAsState()
    var filter by remember { mutableStateOf(DrawerFilter.ALL) }
    val isHybrid = drsState.userPath == DrsUserPath.HYBRID.name

    val view = DrsUnifiedTools.viewForSystem(drsState.userPath, drsState.hybridViewMode)
    // DRS v1.15.0: «المهام المثبتة 10 فقط» — the pinned section mirrors
    // the CAPPED effective set (explicit pins first in pin order, then the
    // default pins), so the drawer never shows or creates an 11th pin.
    val defaultPinnedIds = DrsUnifiedTools.ALL.filter { it.defaultPinned }.map { it.id }
    val effectivePinnedIds = DrsUnifiedTools
        .capPinned(drsState.pinnedUnifiedTools, defaultPinnedIds)
        .toHashSet()
    val hiddenIds = drsState.hiddenUnifiedTools.toHashSet()

    // The pinned section mirrors the strip head: explicit pins first, then
    // default pins, in resolve order for the CURRENT level.
    val pinnedTools = remember(
        view, drsState.unifiedToolOrder, drsState.hiddenUnifiedTools,
        drsState.pinnedUnifiedTools, drsState.unifiedToolViews,
    ) {
        DrsUnifiedTools.resolveFor(
            view = view,
            hidden = drsState.hiddenUnifiedTools,
            pinned = drsState.pinnedUnifiedTools,
            order = drsState.unifiedToolOrder,
            viewOverrides = drsState.unifiedToolViews,
        ).filter { it.id in effectivePinnedIds }
    }

    val catalogTools = DrsUnifiedTools.ALL.filter { tool ->
        when (filter) {
            DrawerFilter.ALL -> true
            DrawerFilter.TOOLS -> tool.group == DrsToolGroup.TOOLS
            DrawerFilter.EDITING -> tool.group == DrsToolGroup.EDITING
            DrawerFilter.CURSOR -> tool.group == DrsToolGroup.CURSOR
        }
    }

    val systemSpec = DrsSystems.specOfName(drsState.userPath)
    val accent = if (isSystemInDarkTheme()) systemSpec.accentNight else systemSpec.accent

    fun close() {
        keyboardManager.activeState.isToolsDrawerVisible = false
    }

    // ---------------------------------------------------------
    // DRS v1.22.0: «إعادة الترتيب بالسحب» — long-press on a pinned
    // row then drag; crossing another pinned row swaps them through
    // DrsUnified.reorderPinnedTool, the same persisted store every
    // other control writes, so the dragged order survives restarts.
    // The up/down buttons stay for TalkBack and precise nudging.
    // The gesture detector is keyed on the display level only — the
    // live pinned ORDER flows through a stable ref so an ongoing
    // drag is never cancelled by a mid-drag recomposition.
    // ---------------------------------------------------------
    var draggingId by remember { mutableStateOf<String?>(null) }
    var dragFingerRootY by remember { mutableStateOf(0f) }
    val pinnedRowBounds = remember { mutableStateMapOf<String, LayoutCoordinates>() }
    var dragHostRootY by remember { mutableStateOf(0f) }
    val pinnedOrderRef = remember {
        java.util.concurrent.atomic.AtomicReference<List<String>>(emptyList())
    }
    androidx.compose.runtime.SideEffect { pinnedOrderRef.set(pinnedTools.map { it.id }) }

    fun pinnedCenterY(id: String): Float? = pinnedRowBounds[id]?.let {
        it.positionInRoot().y + it.size.height / 2f
    }

    fun pinnedIndexUnderRootY(rootY: Float): Int {
        var best = -1
        var bestDist = Float.MAX_VALUE
        pinnedOrderRef.get().forEachIndexed { index, id ->
            val cy = pinnedCenterY(id) ?: return@forEachIndexed
            val dist = kotlin.math.abs(cy - rootY)
            if (dist < bestDist) {
                bestDist = dist
                best = index
            }
        }
        return best
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
                text = stringRes(R.string.drs__tools_drawer__title),
            )
            SnyggText(
                elementName = DrsImeUi.ClipboardHeaderText.elementName,
                modifier = Modifier.padding(end = 12.dp),
                text = stringRes(
                    R.string.drs__tools_drawer__count,
                    "count" to pinnedTools.size.toString(),
                    "total" to DrsUnifiedTools.ALL.size.toString(),
                ),
            )
        }

        // ---------------- group filters ----------------
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp),
        ) {
            DrawerFilter.entries.forEach { option ->
                val selected = filter == option
                SnyggText(
                    elementName = DrsImeUi.ClipboardSubheader.elementName,
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(
                            if (selected) accent.copy(alpha = 0.22f) else accent.copy(alpha = 0.06f),
                        )
                        .rippleClickable { filter = option }
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    text = stringRes(option.labelRes),
                )
            }
        }

        // DRS v1.16.0: the hybrid display-level selector moved here from
        // the tasks bar (the bar now shows the ten fixed task slots only,
        // «يعرض 10 مهام فقط») — the level stays switchable in-IME.
        if (isHybrid) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SnyggText(
                    elementName = DrsImeUi.ClipboardSubheader.elementName,
                    modifier = Modifier.padding(end = 4.dp),
                    text = stringRes(R.string.drs__tools_drawer__level_section),
                )
                DrsHybridViewMode.entries.forEach { mode ->
                    val selected = view == mode
                    SnyggText(
                        elementName = DrsImeUi.ClipboardSubheader.elementName,
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(
                                if (selected) accent.copy(alpha = 0.22f) else accent.copy(alpha = 0.06f),
                            )
                            .rippleClickable { DrsUnified.setViewMode(mode) }
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        text = stringRes(
                            when (mode) {
                                DrsHybridViewMode.SIMPLE -> R.string.drs__unified__level_simple
                                DrsHybridViewMode.ADVANCED -> R.string.drs__unified__level_advanced
                                DrsHybridViewMode.DUAL -> R.string.drs__unified__level_dual
                            },
                        ),
                    )
                }
            }
        }

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
                // ----- pinned now (strip head order, capped at 10) -----
                SnyggRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, top = 6.dp, bottom = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SnyggText(
                        elementName = DrsImeUi.ClipboardSubheader.elementName,
                        text = stringRes(R.string.drs__tools_drawer__pinned_section),
                    )
                    Spacer(Modifier.width(8.dp))
                    SnyggText(
                        elementName = DrsImeUi.ClipboardItemDescription.elementName,
                        text = stringRes(
                            R.string.drs__tools_drawer__pinned_counter,
                            "count" to pinnedTools.size.toString(),
                            "max" to DrsUnifiedTools.MAX_PINNED_TOOLS.toString(),
                        ),
                    )
                }
                if (pinnedTools.isEmpty()) {
                    SnyggText(
                        elementName = DrsImeUi.ClipboardItemDescription.elementName,
                        modifier = Modifier.padding(start = 20.dp, bottom = 4.dp),
                        text = stringRes(R.string.drs__tools_drawer__pinned_empty),
                    )
                }
                // DRS v1.22.0: the drag hint appears only where the drag
                // actually works — two or more pinned rows.
                if (pinnedTools.size >= 2) {
                    SnyggText(
                        elementName = DrsImeUi.ClipboardItemDescription.elementName,
                        modifier = Modifier.padding(start = 20.dp, bottom = 2.dp),
                        text = stringRes(R.string.drs__tools_drawer__drag_hint),
                    )
                }
                if (pinnedTools.isNotEmpty()) {
                    // The drag host: a long-press anywhere on a pinned row
                    // starts the reorder; the finger's root-Y decides which
                    // row it is over, and every crossing is a real swap.
                    androidx.compose.foundation.layout.Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .onGloballyPositioned { dragHostRootY = it.positionInRoot().y }
                            .pointerInput(view) {
                                detectDragGesturesAfterLongPress(
                                    onDragStart = { start ->
                                        val ids = pinnedOrderRef.get()
                                        val idx = pinnedIndexUnderRootY(
                                            dragHostRootY + start.y,
                                        )
                                        if (idx >= 0 && idx < ids.size) {
                                            draggingId = ids[idx]
                                            dragFingerRootY = dragHostRootY + start.y
                                        }
                                    },
                                    onDrag = { change, _ ->
                                        val id = draggingId ?: return@detectDragGesturesAfterLongPress
                                        dragFingerRootY = dragHostRootY + change.position.y
                                        val fromIdx = pinnedOrderRef.get().indexOf(id)
                                        val targetIdx = pinnedIndexUnderRootY(dragFingerRootY)
                                        if (fromIdx >= 0 && targetIdx >= 0 && targetIdx != fromIdx) {
                                            DrsUnified.reorderPinnedTool(fromIdx, targetIdx, view)
                                        }
                                    },
                                    onDragEnd = { draggingId = null },
                                    onDragCancel = { draggingId = null },
                                )
                            },
                    ) {
                        Column {
                            pinnedTools.forEach { tool ->
                                val isDragged = draggingId == tool.id
                                androidx.compose.foundation.layout.Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .onGloballyPositioned { pinnedRowBounds[tool.id] = it }
                                        .graphicsLayer {
                                            translationY = if (isDragged) {
                                                val center = pinnedRowBounds[tool.id]
                                                if (center != null) {
                                                    dragFingerRootY -
                                                        (center.positionInRoot().y + center.size.height / 2f)
                                                } else {
                                                    0f
                                                }
                                            } else {
                                                0f
                                            }
                                            alpha = if (isDragged) 0.85f else 1f
                                        },
                                ) {
                                    DrawerRow(
                                        id = tool.id,
                                        hidden = tool.id in hiddenIds,
                                        pinned = true,
                                        trailing = {
                                            // DRS a11y/i18n/ux (r0-I): the five 36.dp drawer
                                            // chips are icon-only — labeled here, and their
                                            // fixed height became heightIn(min=36.dp) so the
                                            // touch target can never clip the label/content.
                                            SnyggIconButton(
                                                elementName = DrsImeUi.ClipboardHeaderButton.elementName,
                                                onClick = { DrsUnified.moveTool(tool.id, up = true) },
                                                modifier = Modifier.sizeIn(minWidth = 38.dp).heightIn(min = 36.dp),
                                            ) {
                                                SnyggIcon(
                                                    imageVector = Icons.Default.KeyboardArrowUp,
                                                    contentDescription = stringRes(R.string.drs__unified__tools_move_up),
                                                )
                                            }
                                            SnyggIconButton(
                                                elementName = DrsImeUi.ClipboardHeaderButton.elementName,
                                                onClick = { DrsUnified.moveTool(tool.id, up = false) },
                                                modifier = Modifier.sizeIn(minWidth = 38.dp).heightIn(min = 36.dp),
                                            ) {
                                                SnyggIcon(
                                                    imageVector = Icons.Default.KeyboardArrowDown,
                                                    contentDescription = stringRes(R.string.drs__unified__tools_move_down),
                                                )
                                            }
                                            SnyggIconButton(
                                                elementName = DrsImeUi.ClipboardHeaderButton.elementName,
                                                onClick = { DrsUnified.setToolPinned(tool.id, false) },
                                                modifier = Modifier.sizeIn(minWidth = 38.dp).heightIn(min = 36.dp),
                                            ) {
                                                SnyggIcon(
                                                    imageVector = Icons.Filled.PushPin,
                                                    contentDescription = stringRes(R.string.drs__unified__tools_pin),
                                                )
                                            }
                                        },
                                    )
                                }
                            }
                        }
                    }
                }

                // ----- the whole catalogue -----
                SnyggText(
                    elementName = DrsImeUi.ClipboardSubheader.elementName,
                    modifier = Modifier.padding(start = 16.dp, top = 10.dp, bottom = 2.dp),
                    text = stringRes(R.string.drs__tools_drawer__all_section),
                )
                catalogTools.forEach { tool ->
                    val isPinned = tool.id in effectivePinnedIds
                    DrawerRow(
                        id = tool.id,
                        hidden = tool.id in hiddenIds,
                        pinned = isPinned,
                        trailing = {
                            SnyggIconButton(
                                elementName = DrsImeUi.ClipboardHeaderButton.elementName,
                                onClick = {
                                    if (isPinned) {
                                        DrsUnified.setToolPinned(tool.id, false)
                                    } else {
                                        // Pin + guarantee visibility on the CURRENT
                                        // level (the v1.8.0 contract); the v1.15.0 cap
                                        // refuses the 11th pin with an honest toast.
                                        val applied = DrsUnified.setToolPinnedEnsureVisible(tool.id, view)
                                        if (!applied) {
                                            context.showShortToastSync(R.string.drs__tools_drawer__pin_cap_toast)
                                        }
                                    }
                                },
                                modifier = Modifier.sizeIn(minWidth = 38.dp).heightIn(min = 36.dp),
                            ) {
                                SnyggIcon(
                                    imageVector = if (isPinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                                    contentDescription = stringRes(R.string.drs__unified__tools_pin),
                                )
                            }
                            SnyggIconButton(
                                elementName = DrsImeUi.ClipboardHeaderButton.elementName,
                                onClick = {
                                    DrsUnified.setToolHidden(tool.id, tool.id !in hiddenIds)
                                },
                                modifier = Modifier.sizeIn(minWidth = 38.dp).heightIn(min = 36.dp),
                            ) {
                                SnyggIcon(
                                    imageVector = if (tool.id in hiddenIds) {
                                        Icons.Default.VisibilityOff
                                    } else {
                                        Icons.Default.Visibility
                                    },
                                    contentDescription = stringRes(
                                        if (tool.id in hiddenIds) {
                                            R.string.drs__tech_toolbar__show_action
                                        } else {
                                            R.string.drs__tech_toolbar__hide_action
                                        },
                                    ),
                                )
                            }
                        },
                    )
                }

                // ----- bar master switch + reset -----
                SnyggBox(
                    elementName = DrsImeUi.ClipboardItem.elementName,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                        .rippleClickable { DrsUnified.setStripEnabled(!drsState.unifiedStripEnabled) },
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        SnyggText(
                            modifier = Modifier.weight(1f),
                            text = stringRes(R.string.drs__tools_drawer__bar_switch),
                        )
                        SwitchInline(checked = drsState.unifiedStripEnabled, accent = accent)
                    }
                }
                SnyggBox(
                    elementName = DrsImeUi.ClipboardItem.elementName,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                        .rippleClickable { DrsUnified.resetCustomization() },
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        SnyggIcon(imageVector = Icons.Default.RestartAlt)
                        Spacer(Modifier.width(10.dp))
                        SnyggText(text = stringRes(R.string.drs__tools_drawer__reset))
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
            }
        }
    }
}

/** One catalogue row: identity on the left, real controls on the right. */
@Composable
private fun DrawerRow(
    id: String,
    hidden: Boolean,
    pinned: Boolean,
    trailing: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit,
) {
    SnyggBox(
        elementName = DrsImeUi.ClipboardItem.elementName,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 2.dp),
    ) {
        SnyggRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
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
            trailing()
        }
    }
}

/** Tiny theme-safe state pill (avoids Material switches inside the IME). */
@Composable
private fun SwitchInline(checked: Boolean, accent: androidx.compose.ui.graphics.Color) {
    SnyggBox(
        modifier = Modifier
            .clip(CircleShape)
            .background(accent.copy(alpha = if (checked) 0.3f else 0.12f))
            .padding(horizontal = 14.dp, vertical = 4.dp),
    ) {
        SnyggText(
            text = stringRes(
                if (checked) R.string.drs__tools_drawer__on else R.string.drs__tools_drawer__off,
            ),
        )
    }
}
