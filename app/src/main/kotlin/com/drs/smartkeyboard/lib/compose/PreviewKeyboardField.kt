/*
 * Copyright (C) 2021-2025 The DRS Smart Keyboard Project
 */

package com.drs.smartkeyboard.lib.compose

import android.content.Context
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.exclude
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChangeIgnoreConsumed
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.drs.smartkeyboard.R
import com.drs.smartkeyboard.lib.util.InputMethodUtils
import kotlinx.coroutines.delay
import org.drs.lib.android.showShortToastSync
import org.drs.lib.android.showShortToast
import org.drs.lib.compose.stringRes
import org.drs.lib.compose.verticalTween
import kotlin.math.roundToInt

private const val AnimationDuration = 200

private val PreviewEnterTransition = EnterTransition.verticalTween(AnimationDuration)
private val PreviewExitTransition = ExitTransition.verticalTween(AnimationDuration)

private val PreviewPillShape = RoundedCornerShape(50)
private val PreviewFieldShape = RoundedCornerShape(28.dp)
private val PreviewHintShape = RoundedCornerShape(20.dp)

val LocalPreviewFieldController = staticCompositionLocalOf<PreviewFieldController?> { null }

/**
 * DRS: resolved safe drag bounds of the pill overlay (in px), already taking
 * system bars / display cutout into account so the pill can be moved anywhere
 * on the screen yet can never end up unreachable behind them.
 */
private data class DrsPillDragBounds(
    val minX: Float,
    val minY: Float,
    val maxX: Float,
    val maxY: Float,
)

@Composable
fun rememberPreviewFieldController(): PreviewFieldController {
    return remember { PreviewFieldController() }
}

class PreviewFieldController {
    val focusRequester = FocusRequester()
    var isVisible by mutableStateOf(false)
    var text by mutableStateOf(TextFieldValue(""))

    /**
     * DRS: true while the preview text field is focused or explicitly expanded.
     * Drives the floating pill <-> floating field switch of [PreviewKeyboardField].
     */
    var isEditing by mutableStateOf(false)

    /**
     * DRS: when true, the pill renders inline inside the caller's bottom slot
     * (used by the theme editor screen) instead of the draggable activity-level
     * overlay, so it can never collide with that screen's own FAB.
     */
    var usesInlinePill by mutableStateOf(false)

    /**
     * DRS: expands the preview into the floating field and focuses it so the
     * keyboard opens. Safe to call at any time, even while the field is not
     * composed yet (unlike raw [FocusRequester.requestFocus]).
     *
     * DRS v1.0.2: `isEditing` is now driven ONLY by explicit actions (this
     * method and [collapse]) — never by transient focus callbacks. The old
     * focus-writes-state loop let a single dropped focus event during the
     * AnimatedContent transition cancel the focus-retry coroutine and leave
     * the field dead on some OEM Android 14/15 builds.
     */
    fun expandAndFocus() {
        isEditing = true
    }

    /**
     * DRS v1.0.2: explicit collapse. Called when the preview area hides
     * (navigation), when the user dismisses the field, or when focus is lost
     * after a successful editing session — so a stuck editing flag can never
     * replace the pill with an unfocused, dead-looking field.
     */
    fun collapse() {
        isEditing = false
    }
}

private fun isPreviewFieldMode(controller: PreviewFieldController): Boolean =
    controller.isEditing || controller.text.text.isNotEmpty()

/**
 * DRS bottom slot renderer: shows the floating rounded field while editing,
 * an inline centered pill for screens that opt in ([PreviewFieldController.usesInlinePill]),
 * or nothing (the draggable activity-level overlay hosts the pill instead).
 */
@Composable
fun PreviewKeyboardField(
    controller: PreviewFieldController,
    modifier: Modifier = Modifier,
    hint: String = stringRes(R.string.settings__preview_keyboard),
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current

    // DRS v1.0.2: the bottom slot lives across navigation (activity level).
    // Whenever the current screen hides the preview, tear the editing state
    // down with it — otherwise returning to a preview screen could show a
    // stale, unfocused field instead of the tappable pill.
    LaunchedEffect(controller.isVisible) {
        if (!controller.isVisible) controller.collapse()
    }

    AnimatedVisibility(
        visible = controller.isVisible,
        enter = PreviewEnterTransition,
        exit = PreviewExitTransition,
    ) {
        AnimatedContent(
            targetState = isPreviewFieldMode(controller),
            transitionSpec = {
                (fadeIn(tween(AnimationDuration)) + scaleIn(
                    initialScale = 0.9f,
                    animationSpec = tween(AnimationDuration),
                )).togetherWith(
                    fadeOut(tween(AnimationDuration)) + scaleOut(
                        targetScale = 0.95f,
                        animationSpec = tween(AnimationDuration),
                    ),
                )
            },
            label = "DrsPreviewFieldMode",
        ) { isFieldMode ->
            if (isFieldMode) {
                PreviewFloatingField(
                    controller = controller,
                    hint = hint,
                    focusManager = focusManager,
                    context = context,
                )
            } else if (controller.usesInlinePill) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 14.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    DrsPreviewPillContent(hint = hint, onClick = { controller.expandAndFocus() })
                }
            } else {
                Spacer(modifier = Modifier)
            }
        }
    }
}

/**
 * DRS draggable floating pill overlay: place it above screen content (activity
 * level). Defaults to the visually-left bottom corner, can be dragged anywhere,
 * and shows a first-open coach hint explaining what it does.
 */
@Composable
fun DrsFloatingPreviewPill(
    controller: PreviewFieldController,
    modifier: Modifier = Modifier,
    hint: String = stringRes(R.string.settings__preview_keyboard),
    hintTitle: String = stringRes(R.string.drs__preview_pill__hint__title),
    hintText: String = stringRes(R.string.drs__preview_pill__hint__text),
    showHint: Boolean = false,
    savedAnchorX: Float? = null,
    savedAnchorY: Float? = null,
    onAnchorChanged: (Float, Float) -> Unit = { _, _ -> },
    onHintDismiss: () -> Unit = {},
) {
    val pillVisible = controller.isVisible && !isPreviewFieldMode(controller) && !controller.usesInlinePill

    AnimatedVisibility(
        visible = pillVisible,
        enter = fadeIn(tween(AnimationDuration)) + scaleIn(
            initialScale = 0.85f,
            animationSpec = tween(AnimationDuration),
        ),
        exit = fadeOut(tween(AnimationDuration)) + scaleOut(
            targetScale = 0.9f,
            animationSpec = tween(AnimationDuration),
        ),
        modifier = modifier,
    ) {
        val dismissHint = rememberUpdatedState(onHintDismiss)

        LaunchedEffect(showHint) {
            if (showHint) {
                kotlinx.coroutines.delay(700)
                kotlinx.coroutines.delay(8000)
                dismissHint.value()
            }
        }

        DraggablePillBody(
            controller = controller,
            hint = hint,
            hintTitle = hintTitle,
            hintText = hintText,
            showHint = showHint,
            savedAnchorX = savedAnchorX,
            savedAnchorY = savedAnchorY,
            onAnchorChanged = onAnchorChanged,
            onHintDismiss = { dismissHint.value() },
        )
    }
}

@Composable
private fun DraggablePillBody(
    controller: PreviewFieldController,
    hint: String,
    hintTitle: String,
    hintText: String,
    showHint: Boolean,
    savedAnchorX: Float?,
    savedAnchorY: Float?,
    onAnchorChanged: (Float, Float) -> Unit,
    onHintDismiss: () -> Unit,
) {
    val density = androidx.compose.ui.platform.LocalDensity.current
    val layoutDirection = androidx.compose.ui.platform.LocalLayoutDirection.current
    val haptic = LocalHapticFeedback.current

    var boxSize by remember { mutableStateOf(IntSize.Zero) }
    var pillSize by remember { mutableStateOf(IntSize.Zero) }
    var placed by rememberSaveable { mutableStateOf(false) }
    var px by rememberSaveable { mutableFloatStateOf(0f) }
    var py by rememberSaveable { mutableFloatStateOf(0f) }

    // DRS: free movement over the WHOLE screen, but never behind system
    // bars or the display cutout (ime excluded: the pill hides while editing).
    val safeInsets = WindowInsets.safeDrawing.exclude(WindowInsets.ime)
    val safeLeftPx = safeInsets.getLeft(density, layoutDirection).toFloat()
    val safeTopPx = safeInsets.getTop(density).toFloat()
    val safeRightPx = safeInsets.getRight(density, layoutDirection).toFloat()
    val safeBottomPx = safeInsets.getBottom(density).toFloat()
    val gripMargin = with(density) { 8.dp.toPx() }

    LaunchedEffect(boxSize, pillSize, savedAnchorX, savedAnchorY) {
        if (!placed && boxSize.width > 0 && pillSize.width > 0) {
            val minBoundX = safeLeftPx + gripMargin
            val minBoundY = safeTopPx + gripMargin
            val maxBoundX = (boxSize.width - pillSize.width - safeRightPx - gripMargin).coerceAtLeast(minBoundX)
            val maxBoundY = (boxSize.height - pillSize.height - safeBottomPx - gripMargin).coerceAtLeast(minBoundY)
            val anchorX = savedAnchorX
            val anchorY = savedAnchorY
            if (anchorX != null && anchorY != null) {
                // DRS: restore the last persisted drag position (stored as
                // fractions of the safe area, so it survives restarts and
                // screen size/orientation changes).
                px = (minBoundX + anchorX * (maxBoundX - minBoundX)).coerceIn(minBoundX, maxBoundX)
                py = (minBoundY + anchorY * (maxBoundY - minBoundY)).coerceIn(minBoundY, maxBoundY)
            } else {
                // DRS: default placement — visually left bottom corner.
                px = minBoundX
                py = maxBoundY
            }
            placed = true
        }
    }

    val bounds = rememberUpdatedState(
        DrsPillDragBounds(
            minX = safeLeftPx + gripMargin,
            minY = safeTopPx + gripMargin,
            maxX = (boxSize.width - pillSize.width - safeRightPx - gripMargin).coerceAtLeast(safeLeftPx + gripMargin),
            maxY = (boxSize.height - pillSize.height - safeBottomPx - gripMargin).coerceAtLeast(safeTopPx + gripMargin),
        ),
    )
    val b = bounds.value
    val cx = px.coerceIn(b.minX, b.maxX).roundToInt()
    val cy = py.coerceIn(b.minY, b.maxY).roundToInt()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { boxSize = it },
    ) {
        AnimatedVisibility(
            visible = showHint,
            enter = fadeIn(tween(350)) + slideInVertically(tween(350)) { it / 2 },
            exit = fadeOut(tween(250)),
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 12.dp, end = 12.dp, bottom = 92.dp),
        ) {
            Surface(
                shape = PreviewHintShape,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)),
                shadowElevation = 8.dp,
                modifier = Modifier
                    .widthIn(max = 320.dp)
                    .clickable(onClick = onHintDismiss),
            ) {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = hintTitle,
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = hintText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        Box(
            modifier = Modifier
                .offset { IntOffset(cx, cy) }
                .onSizeChanged { pillSize = it },
        ) {
            DrsPreviewPillContent(
                hint = hint,
                onClick = {
                    onHintDismiss()
                    controller.expandAndFocus()
                },
                onDragStart = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onHintDismiss()
                },
                onDrag = { amount ->
                    val boundsNow = bounds.value
                    px = (px + amount.x).coerceIn(boundsNow.minX, boundsNow.maxX)
                    py = (py + amount.y).coerceIn(boundsNow.minY, boundsNow.maxY)
                },
                onDragEnd = {
                    // DRS: persist the drop position as fractions of the safe
                    // area so the pill stays exactly where the user left it,
                    // even after closing and reopening the app.
                    val boundsNow = bounds.value
                    val spanX = (boundsNow.maxX - boundsNow.minX).coerceAtLeast(1f)
                    val spanY = (boundsNow.maxY - boundsNow.minY).coerceAtLeast(1f)
                    onAnchorChanged(
                        ((px - boundsNow.minX) / spanX).coerceIn(0f, 1f),
                        ((py - boundsNow.minY) / spanY).coerceIn(0f, 1f),
                    )
                },
            )
        }
    }
}

/**
 * The luxurious pill visual: soft gradient, thin border, elevated shadow,
 * keyboard icon in a tinted badge, bold label and a subtle press-scale.
 *
 * DRS fix: a single unified tap-or-drag gesture detector replaces the former
 * [Modifier.clickable] + [androidx.compose.foundation.gestures.detectDragGestures]
 * pair. With two competing detectors, any finger movement past the touch slop
 * during a "tap" was claimed by the drag detector, which consumed the event
 * stream out from under the clickable — so on real devices the pill often
 * refused to open (it only nudged sideways). Gesture ownership now lives in
 * exactly one place: release before the slop = tap, movement beyond the slop
 * = drag. Deterministic on every device and every Compose version.
 */
@Composable
private fun DrsPreviewPillContent(
    hint: String,
    onClick: () -> Unit,
    onDragStart: () -> Unit = {},
    onDrag: (Offset) -> Unit = {},
    onDragEnd: () -> Unit = {},
) {
    val colorScheme = MaterialTheme.colorScheme
    val pillGradient = Brush.linearGradient(
        listOf(
            colorScheme.primary.copy(alpha = 0.16f),
            colorScheme.tertiary.copy(alpha = 0.16f),
        ),
    )
    val borderColor = colorScheme.outlineVariant.copy(alpha = 0.55f)

    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.95f else 1f,
        animationSpec = tween(AnimationDuration),
        label = "DrsPreviewPillScale",
    )
    val haptic = LocalHapticFeedback.current

    // DRS: always dispatch into the freshest callbacks without ever restarting
    // the gesture detector mid-gesture (Unit-keyed pointerInput).
    val currentOnClick by rememberUpdatedState(onClick)
    val currentOnDragStart by rememberUpdatedState(onDragStart)
    val currentOnDrag by rememberUpdatedState(onDrag)
    val currentOnDragEnd by rememberUpdatedState(onDragEnd)

    Row(
        modifier = Modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .shadow(elevation = 8.dp, shape = PreviewPillShape, clip = false)
            .clip(PreviewPillShape)
            .background(pillGradient)
            .border(BorderStroke(1.dp, borderColor), PreviewPillShape)
            // DRS: keep TalkBack parity with the former clickable() — an
            // accessibility click action (never fired by touch, so it cannot
            // collide with the unified gesture detector above).
            .semantics(mergeDescendants = true) {
                onClick(label = hint) { currentOnClick(); true }
            }
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    down.consume()
                    pressed = true
                    var dragStarted = false
                    var totalDelta = Offset.Zero
                    try {
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (change.changedToUpIgnoreConsumed()) {
                                // Released before the slop: a clean tap. After the
                                // slop: the drag already handled the move; do not
                                // fire the click as well.
                                if (!dragStarted) {
                                    // DRS v1.0.2: a light tactile tick that confirms
                                    // the tap registered — on real hardware users must
                                    // FEEL that the pill accepted the touch, even when
                                    // the transition to the field takes a few frames.
                                    haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                                    currentOnClick()
                                }
                                change.consume()
                                break
                            }
                            val delta = change.positionChangeIgnoreConsumed()
                            if (!dragStarted) {
                                // Another gesture took ownership (e.g. a parent
                                // scroll): stand down instead of fighting it.
                                if (change.isConsumed) break
                                totalDelta += delta
                                if (totalDelta.getDistance() > viewConfiguration.touchSlop) {
                                    dragStarted = true
                                    currentOnDragStart()
                                }
                            }
                            if (dragStarted) {
                                change.consume()
                                currentOnDrag(delta)
                            }
                        }
                    } finally {
                        pressed = false
                        if (dragStarted) currentOnDragEnd()
                    }
                }
            }
            .padding(start = 14.dp, end = 22.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(30.dp)
                .clip(CircleShape)
                .background(colorScheme.primary.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Default.Keyboard,
                contentDescription = null,
                tint = colorScheme.primary,
                modifier = Modifier.size(17.dp),
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = hint,
            style = MaterialTheme.typography.titleMedium,
            color = colorScheme.primary,
            fontWeight = FontWeight.Bold,
            overflow = TextOverflow.Ellipsis,
            maxLines = 1,
        )
    }
}

@Composable
private fun PreviewFloatingField(
    controller: PreviewFieldController,
    hint: String,
    focusManager: FocusManager,
    context: Context,
) {
    val colorScheme = MaterialTheme.colorScheme
    val keyboard = LocalSoftwareKeyboardController.current
    var fieldHasFocus by remember { mutableStateOf(false) }
    var everFocused by remember { mutableStateOf(false) }

    // DRS v1.0.2 hardening — the focus bootstrap is now keyed to Unit, so it
    // lives exactly as long as this field composition and can NEVER be
    // cancelled mid-retry by a transient `isEditing` flip (the v1.0.1 loop was
    // keyed on controller.isEditing, which onFocusChanged wrote back to — a
    // single dropped focus event during the AnimatedContent transition then
    // killed the retry wave and left an unfocused, dead-looking field).
    //
    // Wave 1: retry focus frame-by-frame (up to ~20 frames) until the field
    //         really holds it, then raise the IME.
    // Wave 2: some OEM Android 14/15 builds attach the window late and swallow
    //         the first show() — one more focus/show pass shortly after makes
    //         the keyboard appear without any user interaction.
    LaunchedEffect(Unit) {
        var attempts = 0
        while (!fieldHasFocus && attempts < 20) {
            runCatching { controller.focusRequester.requestFocus() }
            withFrameNanos { }
            attempts++
        }
        keyboard?.show()
        delay(150)
        if (!fieldHasFocus && controller.isEditing) {
            runCatching { controller.focusRequester.requestFocus() }
            withFrameNanos { }
            keyboard?.show()
        }
        // DRS v1.0.2 last-resort guarantee: if focus STILL never landed after
        // ~1s, quietly fall back to the pill instead of stranding the user on
        // a dead, unfocused field. An empty preview has nothing to preserve —
        // and on the pill, retrying is always exactly one tap away.
        delay(600)
        if (!fieldHasFocus && controller.isEditing && controller.text.text.isEmpty()) {
            controller.collapse()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 14.dp),
    ) {
        SelectionContainer {
            TextField(
                modifier = Modifier
                    // DRS a11y/i18n/ux (r0-I) FIX 9 — heightIn(min) instead of fixed
                    // height: large user font scales grow the preview field instead of
                    // clipping the text (singleLine already set below).
                    .heightIn(min = 56.dp)
                    .fillMaxWidth()
                    .shadow(elevation = 6.dp, shape = PreviewFieldShape, clip = false)
                    .onPreviewKeyEvent { event ->
                        if (event.key == Key.Back) {
                            focusManager.clearFocus()
                        }
                        false
                    }
                    .focusRequester(controller.focusRequester)
                    .onFocusChanged {
                        fieldHasFocus = it.isFocused
                        // DRS v1.0.2: focus LOSS after a successful editing
                        // session (tap outside, Done action, back key) collapses
                        // back to the pill. Focus events no longer write into
                        // isEditing directly — that feedback loop was the deep
                        // root of stuck/dead field states on real devices.
                        if (it.isFocused) {
                            everFocused = true
                        } else if (everFocused) {
                            controller.collapse()
                        }
                    },
                value = controller.text,
                onValueChange = { controller.text = it },
                textStyle = LocalTextStyle.current.copy(textDirection = TextDirection.ContentOrLtr),
                placeholder = {
                    Text(
                        text = hint,
                        overflow = TextOverflow.Ellipsis,
                        maxLines = 1,
                    )
                },
                trailingIcon = {
                    Row {
                        if (controller.text.text.isNotEmpty()) {
                            IconButton(onClick = {
                                controller.text = TextFieldValue("")
                                focusManager.clearFocus()
                            }) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = stringRes(R.string.action__cancel),
                                )
                            }
                        }
                        IconButton(onClick = {
                            if (!InputMethodUtils.showImePicker(context)) {
                                context.showShortToastSync(
                                    context.getString(R.string.error__imm_service_unavailable),
                                )
                            }
                        }) {
                            Icon(
                                imageVector = Icons.Default.Keyboard,
                                contentDescription = null,
                            )
                        }
                    }
                },
                keyboardActions = KeyboardActions(
                    onDone = { focusManager.clearFocus() },
                ),
                keyboardOptions = KeyboardOptions(autoCorrectEnabled = true),
                singleLine = true,
                shape = PreviewFieldShape,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = colorScheme.surfaceContainerHigh,
                    unfocusedContainerColor = colorScheme.surfaceContainerHigh,
                    disabledContainerColor = colorScheme.surfaceContainerHigh,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    disabledIndicatorColor = Color.Transparent,
                ),
            )
        }
    }
}
