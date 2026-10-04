/*
 * Copyright (C) 2024-2025 The DRS Smart Keyboard Project
 */

package com.drs.smartkeyboard.ime.smartbar

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventTimeoutCancellationException
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.drs.smartkeyboard.app.DrsPreferenceStore
import com.drs.smartkeyboard.drs.DrsMotion
import com.drs.smartkeyboard.ime.nlp.ClipboardSuggestionCandidate
import com.drs.smartkeyboard.ime.nlp.SuggestionCandidate
import com.drs.smartkeyboard.ime.input.LocalInputFeedbackController
import com.drs.smartkeyboard.ime.theme.DrsImeUi
import com.drs.smartkeyboard.keyboardManager
import com.drs.smartkeyboard.nlpManager
import com.drs.smartkeyboard.subtypeManager
import org.drs.jetpref.datastore.model.collectAsState
import org.drs.lib.compose.conditional
import org.drs.lib.compose.drsHorizontalScroll
import org.drs.lib.snygg.SnyggSelector
import org.drs.lib.snygg.ui.SnyggBox
import org.drs.lib.snygg.ui.SnyggColumn
import org.drs.lib.snygg.ui.SnyggIcon
import org.drs.lib.snygg.ui.SnyggRow
import org.drs.lib.snygg.ui.SnyggSpacer
import org.drs.lib.snygg.ui.SnyggText

val CandidatesRowScrollbarHeight = 2.dp

@Composable
fun CandidatesRow(modifier: Modifier = Modifier) {
    val prefs by DrsPreferenceStore
    val context = LocalContext.current
    val keyboardManager by context.keyboardManager()
    val nlpManager by context.nlpManager()
    val subtypeManager by context.subtypeManager()

    val displayMode by prefs.suggestion.displayMode.collectAsState()
    val candidates by nlpManager.activeCandidatesFlow.collectAsState()

    // DRS v2.2.1 «مرشحون أحياء»: haptics at the interaction site — the
    // candidates row was silent on commit AND on long-press removal,
    // while the quick actions beside it always spoke (QuickActionButton).
    val feedback = LocalInputFeedbackController.current
    // DRS v2.2.1: the entrance wave replays ONLY when the candidate SET
    // changes — the signature keys the per-item animation below, so a
    // re-emission of the same suggestions (e.g. a re-rank with identical
    // order) never re-runs the wave.
    val entranceKey = remember(candidates) {
        candidates.joinToString(separator = "|") { it.text.toString() }
    }

    SnyggRow(
        elementName = DrsImeUi.SmartbarCandidatesRow.elementName,
        modifier = modifier
            .fillMaxSize()
            .conditional(displayMode == CandidatesDisplayMode.DYNAMIC_SCROLLABLE && candidates.size > 1) {
                drsHorizontalScroll(scrollbarHeight = CandidatesRowScrollbarHeight)
            },
        horizontalArrangement = if (candidates.size > 1) {
            Arrangement.Start
        } else {
            Arrangement.Center
        },
    ) {
        if (candidates.isNotEmpty()) {
            val candidateModifier = if (candidates.size == 1) {
                Modifier
                    .fillMaxHeight()
                    .weight(1f, fill = false)
            } else {
                Modifier
                    .fillMaxHeight()
                    .conditional(displayMode == CandidatesDisplayMode.CLASSIC) {
                        weight(1f)
                    }
                    .conditional(displayMode != CandidatesDisplayMode.CLASSIC) {
                        wrapContentWidth().widthIn(max = 160.dp)
                    }
            }
            val list = when (displayMode) {
                CandidatesDisplayMode.CLASSIC -> candidates.subList(0, 3.coerceAtMost(candidates.size))
                else -> candidates
            }
            for ((n, candidate) in list.withIndex()) {
                if (n > 0) {
                    SnyggSpacer(
                        elementName = DrsImeUi.SmartbarCandidateSpacer.elementName,
                        modifier = Modifier
                            .width(1.dp)
                            .fillMaxHeight(0.6f)
                            .align(Alignment.CenterVertically),
                    )
                }
                CandidateItem(
                    modifier = candidateModifier,
                    candidate = candidate,
                    displayMode = displayMode,
                    entranceIndex = n,
                    entranceKey = entranceKey,
                    onClick = {
                        // Can't use candidate directly
                        feedback.keyPress()
                        keyboardManager.commitCandidate(candidates[n])
                    },
                    onLongPress = {
                        // Can't use candidate directly
                        val candidateItem = candidates[n]
                        val removed = if (candidateItem.isEligibleForUserRemoval) {
                            nlpManager.removeSuggestion(subtypeManager.activeSubtype, candidateItem)
                        } else {
                            false
                        }
                        if (removed) feedback.keyLongPress()
                        removed
                    },
                    longPressDelay = prefs.keyboard.longPressDelay.get().toLong(),
                )
            }
        }
    }
}

@Composable
private fun CandidateItem(
    candidate: SuggestionCandidate,
    displayMode: CandidatesDisplayMode,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = { },
    onLongPress: () -> Boolean = { false },
    longPressDelay: Long,
    entranceIndex: Int = 0,
    entranceKey: String = "",
) = with(LocalDensity.current) {
    var isPressed by remember { mutableStateOf(false) }

    // DRS v2.2.1: the deterministic entrance wave (مرشحون أحياء). Each
    // candidate fades/slides in with the capped DrsMotion stagger so the
    // row reads as one quick start-to-end wave, never a pop-in. The
    // remember keys on the row's signature: re-renders with the same set
    // keep `entered = true` and stay static; a NEW set re-runs the wave.
    // DRAW-ONLY (graphicsLayer): no re-measurement, no layout shift —
    // the 5dp slide is a draw translate, and the pointer/semantics
    // bounds are untouched.
    var entered by remember(entranceKey) { mutableStateOf(false) }
    val entrance by animateFloatAsState(
        targetValue = if (entered) 1f else 0f,
        animationSpec = tween(
            durationMillis = DrsMotion.ENTRANCE_DURATION_MS,
            delayMillis = DrsMotion.staggerFor(entranceIndex),
        ),
        label = "drsCandidateEntrance",
    )
    LaunchedEffect(entranceKey) { entered = true }

    val elementName = if (candidate is ClipboardSuggestionCandidate) {
        DrsImeUi.SmartbarCandidateClip
    } else {
        DrsImeUi.SmartbarCandidateWord
    }.elementName
    val attributes = mapOf("auto-commit" to if (candidate.isEligibleForAutoCommit) 1 else 0)
    val selector = if (isPressed) SnyggSelector.PRESSED else SnyggSelector.NONE

    SnyggRow(
        elementName = elementName,
        attributes = attributes,
        selector = selector,
        modifier = modifier
            .graphicsLayer {
                alpha = entrance
                translationY = (1f - entrance) * 5.dp.toPx()
            }
            // DRS Phase 2 (roadmap task 14): the candidate row was INVISIBLE
            // to TalkBack — a custom pointerInput gesture chain with no
            // semantics node means screen-reader users heard nothing and
            // could not act. Announced as a button with the candidate text
            // (plus its secondary line), and a custom a11y action exposes
            // the long-press removal path non-visually.
            .semantics {
                role = Role.Button
                contentDescription = buildString {
                    append(candidate.text)
                    candidate.secondaryText?.let { append(", ").append(it) }
                    if (candidate.isEligibleForAutoCommit) {
                        append(" · ").append("↵")
                    }
                }
                if (candidate.isEligibleForUserRemoval) {
                    customActions = listOf(
                        CustomAccessibilityAction("✕") { onLongPress() }
                    )
                }
            }
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    isPressed = true
                    if (down.pressed != down.previousPressed) down.consume()
                    var upOrCancel: PointerInputChange? = null
                    try {
                        upOrCancel = withTimeout(longPressDelay) {
                            waitForUpOrCancellation()
                        }
                        upOrCancel?.let { if (it.pressed != it.previousPressed) it.consume() }
                    } catch (_: PointerEventTimeoutCancellationException) {
                        if (onLongPress()) {
                            upOrCancel = null
                            isPressed = false
                        }
                        waitForUpOrCancellation()?.let { if (it.pressed != it.previousPressed) it.consume() }
                    }
                    if (upOrCancel != null) {
                        onClick()
                    }
                    isPressed = false
                }
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (candidate.icon != null) {
            SnyggBox(
                elementName = "$elementName-icon",
                attributes = attributes,
                selector = selector,
            ) {
                SnyggIcon(imageVector = candidate.icon!!)
            }
        }
        SnyggColumn(
            modifier = if (displayMode == CandidatesDisplayMode.CLASSIC) Modifier.weight(1f) else Modifier,
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            SnyggText(
                elementName = "$elementName-text",
                attributes = attributes,
                selector = selector,
                text = candidate.text.toString(),
            )
            if (candidate.secondaryText != null) {
                SnyggText(
                    elementName = "$elementName-secondary-text",
                    attributes = attributes,
                    selector = selector,
                    text = candidate.secondaryText!!.toString(),
                )
            }
        }
    }
}
