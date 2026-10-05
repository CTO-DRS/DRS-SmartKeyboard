/*
 * Copyright (C) 2021-2025 The DRS Smart Keyboard Project
 */

package com.drs.smartkeyboard.ime.popup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.size
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import com.drs.smartkeyboard.drs.DrsMotion
import com.drs.smartkeyboard.drs.DrsPopupMotion
import com.drs.smartkeyboard.ime.keyboard.Key
import com.drs.smartkeyboard.ime.theme.DrsImeUi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import org.drs.lib.snygg.SnyggQueryAttributes
import org.drs.lib.snygg.SnyggSelector
import org.drs.lib.snygg.ui.SnyggBox
import org.drs.lib.snygg.ui.SnyggColumn
import org.drs.lib.snygg.ui.SnyggIcon
import org.drs.lib.snygg.ui.SnyggRow
import org.drs.lib.snygg.ui.SnyggText

val GlobalStateNumPopupsShowing = MutableStateFlow(0)

@Composable
fun PopupBaseBox(
    modifier: Modifier = Modifier,
    attributes: SnyggQueryAttributes,
    key: Key,
    shouldIndicateExtendedPopups: Boolean,
): Unit = with(LocalDensity.current) {
    DisposableEffect(key) {
        GlobalStateNumPopupsShowing.update { it + 1 }
        onDispose {
            GlobalStateNumPopupsShowing.update { it - 1 }
        }
    }

    // DRS v2.6.0 «المنبثق الحي الصادق»: the preview bubble GROWS into
    // place instead of popping — attached to the finger, quicker than a
    // panel (90ms vs the candidates' 130ms). The `entered` flag is the
    // project's entrance-wave pattern (CandidatesRow, v2.2.1): the first
    // composition snaps to the from-scale, then the flag flips and the
    // same frame animates to 1. DRAW-ONLY (graphicsLayer): layout is
    // never re-measured, pointer and semantics bounds are untouched.
    var entered by remember(key) { mutableStateOf(false) }
    val entranceScale by animateFloatAsState(
        targetValue = if (entered) 1f else DrsPopupMotion.ENTRANCE_SCALE_FROM,
        animationSpec = tween(durationMillis = DrsPopupMotion.ENTRANCE_DURATION_MS),
        label = "drsPopupBaseEntrance",
    )
    LaunchedEffect(key) { entered = true }

    SnyggBox(
        elementName = DrsImeUi.KeyPopupBox.elementName,
        attributes = attributes,
        modifier = modifier.graphicsLayer {
            scaleX = entranceScale
            scaleY = entranceScale
        },
    ) {
        key.label?.let { label ->
            SnyggBox(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(key.visibleBounds.height.toDp())
                    .align(Alignment.TopCenter),
            ) {
                SnyggText(
                    modifier = Modifier.align(Alignment.Center),
                    text = label,
                )
            }
        }
        if (shouldIndicateExtendedPopups) {
            SnyggIcon(
                elementName = DrsImeUi.KeyPopupExtendedIndicator.elementName,
                attributes = attributes,
                modifier = Modifier.align(Alignment.CenterEnd),
                imageVector = Icons.Default.MoreHoriz,
            )
        }
    }
}

@Composable
fun PopupExtBox(
    modifier: Modifier = Modifier,
    attributes: SnyggQueryAttributes,
    elements: List<List<PopupUiController.Element>>,
    elemArrangement: Arrangement.Horizontal,
    elemWidth: Dp,
    elemHeight: Dp,
    activeElementIndex: Int,
): Unit = with(LocalDensity.current) {
    SnyggColumn(DrsImeUi.KeyPopupBox.elementName, attributes, modifier = modifier) {
        for (row in elements.asReversed()) {
            SnyggRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .requiredHeight(elemHeight),
                horizontalArrangement = elemArrangement,
            ) {
                for (element in row) {
                    val selector = if (activeElementIndex == element.orderedIndex) {
                        SnyggSelector.FOCUS
                    } else {
                        null
                    }
                    val localAttrs = attributes.plus(DrsImeUi.Attr.Code to element.data.code)
                    // DRS v2.6.0 «المنبثق الحي الصادق» — the active element
                    // PULSES with the pressed-key contract itself
                    // (DrsMotion.scaleFor/durationFor: 0.94 in 60ms, back in
                    // 140ms) — the popup speaks the exact language of the
                    // keys, no new constants, no new scale. The entrance
                    // rides the same draw pass: elements grow in with the
                    // capped DrsPopupMotion stagger (tighter than the
                    // candidates — a popup is at most two rows). Both are
                    // DRAW-ONLY via one graphicsLayer block.
                    val isActive = activeElementIndex == element.orderedIndex
                    val pulseScale by animateFloatAsState(
                        targetValue = DrsMotion.scaleFor(isActive),
                        animationSpec = tween(durationMillis = DrsMotion.durationFor(isActive)),
                        label = "drsPopupElementPulse",
                    )
                    var entered by remember(elements, element.orderedIndex) { mutableStateOf(false) }
                    val entranceScale by animateFloatAsState(
                        targetValue = if (entered) 1f else DrsPopupMotion.ENTRANCE_SCALE_FROM,
                        animationSpec = tween(
                            durationMillis = DrsPopupMotion.ENTRANCE_DURATION_MS,
                            delayMillis = DrsPopupMotion.elementStaggerFor(element.orderedIndex),
                        ),
                        label = "drsPopupElementEntrance",
                    )
                    LaunchedEffect(elements, element.orderedIndex) { entered = true }
                    SnyggBox(
                        elementName = DrsImeUi.KeyPopupElement.elementName,
                        attributes = localAttrs,
                        selector = selector,
                        modifier = Modifier
                            .size(elemWidth, elemHeight)
                            .graphicsLayer {
                                scaleX = entranceScale * pulseScale
                                scaleY = entranceScale * pulseScale
                            },
                    ) {
                        element.label?.let { label ->
                            SnyggText(
                                modifier = Modifier.align(Alignment.Center),
                                text = label,
                            )
                        }
                        element.icon?.let { icon ->
                            SnyggIcon(
                                modifier = Modifier.align(Alignment.Center),
                                imageVector = icon,
                            )
                        }
                    }
                }
            }
        }
    }
}
