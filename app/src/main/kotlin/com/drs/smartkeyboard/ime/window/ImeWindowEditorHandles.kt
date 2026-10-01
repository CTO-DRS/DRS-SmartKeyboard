/*
 * Copyright (C) 2025-2026 The DRS Smart Keyboard Project
 */

package com.drs.smartkeyboard.ime.window

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.systemGestureExclusion
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.drs.smartkeyboard.R
import com.drs.smartkeyboard.app.DrsPreferenceStore
import com.drs.smartkeyboard.ime.keyboard.DrsImeSizing
import com.drs.smartkeyboard.ime.theme.DrsImeUi
import org.drs.jetpref.datastore.model.collectAsState
import kotlinx.coroutines.delay
import org.drs.lib.compose.conditional
import org.drs.lib.compose.drawableRes
import org.drs.lib.compose.stringRes
import org.drs.lib.compose.toDp
import org.drs.lib.snygg.ui.SnyggIcon
import org.drs.lib.snygg.ui.SnyggIconButton
import org.drs.lib.snygg.ui.rememberSnyggThemeQuery
import org.drs.lib.snygg.ui.uriOrNull

enum class ImeWindowResizeHandle(
    val left: Boolean = false,
    val top: Boolean = false,
    val right: Boolean = false,
    val bottom: Boolean = false,
) {
    LEFT(left = true),
    TOP_LEFT(top = true, left = true),
    TOP(top = true),
    TOP_RIGHT(top = true, right = true),
    RIGHT(right = true),
    BOTTOM_RIGHT(bottom = true, right = true),
    BOTTOM(bottom = true),
    BOTTOM_LEFT(bottom = true, left = true);

    fun shape(thickness: Dp, cornerRadius: Dp): Shape {
        return object : Shape {
            override fun createOutline(
                size: Size,
                layoutDirection: LayoutDirection,
                density: Density,
            ): Outline = with(density) {
                val path = Path()
                val radius = CornerRadius(cornerRadius.toPx(), cornerRadius.toPx())

                if (top || bottom) {
                    val topLeftOffset = if (bottom) Offset(0f, size.height - thickness.toPx()) else Offset.Zero
                    path.addRoundRect(
                        RoundRect(
                            left = topLeftOffset.x,
                            top = topLeftOffset.y,
                            right = topLeftOffset.x + size.width,
                            bottom = topLeftOffset.y + thickness.toPx(),
                            cornerRadius = radius,
                        )
                    )
                }

                if (left || right) {
                    val topLeftOffset = if (right) Offset(size.width - thickness.toPx(), 0f) else Offset.Zero
                    path.addRoundRect(
                        RoundRect(
                            left = topLeftOffset.x,
                            top = topLeftOffset.y,
                            right = topLeftOffset.x + thickness.toPx(),
                            bottom = topLeftOffset.y + size.height,
                            cornerRadius = radius,
                        )
                    )
                }

                return Outline.Generic(path)
            }
        }
    }
}

@Composable
private fun ImeWindowResizeHandle(
    handle: ImeWindowResizeHandle,
    modifier: Modifier,
    alphaState: State<Float>,
) {
    val windowController = LocalWindowController.current
    val prefs by DrsPreferenceStore

    val currentAlpha by alphaState

    val windowSpec by windowController.activeWindowSpec.collectAsState()
    val windowConfig by windowController.activeWindowConfig.collectAsState()

    val thickness = windowSpec.constraints.resizeHandleDrawThickness
    val cornerRadius = windowSpec.constraints.resizeHandleDrawCornerRadius

    val showWindowResizeHandleBoundaries by prefs.devtools.showWindowResizeHandleBoundaries.collectAsState()

    val attributes = remember(windowConfig.mode) {
        mapOf(
            DrsImeUi.Attr.WindowMode to windowConfig.mode.toString(),
        )
    }

    val resizeHandleA11yLabel = stringRes(R.string.snygg__rule_element__window_resize_handle)

    val style = rememberSnyggThemeQuery(DrsImeUi.WindowResizeHandle.elementName, attributes)
    // DRS a11y/i18n/ux (r0-I): the fallback handle color was hardcoded black — invisible resize/zoom
    // handles on every dark keyboard theme. The default now follows the same luminance-derived
    // scrim approach as the ImeSystemUi move pill: derive black-vs-white from the window background
    // luminance (background images count as dark, matching the system bar icon logic there).
    val windowQuery = rememberSnyggThemeQuery(DrsImeUi.Window.elementName)
    val windowBackgroundImage = windowQuery.backgroundImage.uriOrNull()
    val windowBackgroundColor = windowQuery.background()
    val useDarkHandleDefault = if (windowBackgroundImage == null) {
        windowBackgroundColor.luminance() >= 0.5f
    } else {
        false
    }
    val defaultHandleColor = if (useDarkHandleDefault) Color.Black else Color.White
    val handleColor by rememberUpdatedState(style.background(default = defaultHandleColor))
    val shadowColor by rememberUpdatedState(style.shadowColor(default = Color.Black))
    val shadowElevation by rememberUpdatedState(style.shadowElevation(default = 8.dp))

    Box(
        modifier = modifier
            .size(windowSpec.constraints.resizeHandleTouchSize)
            .imeWindowResizeHandle(windowController, handle)
            // DRS a11y/i18n/ux (r0-F): the drag handles exposed no semantics at all — at least identify
            // them for accessibility services (drag-to-resize interaction itself stays pointer-only).
            .semantics {
                this.contentDescription = resizeHandleA11yLabel
            }
            .conditional(showWindowResizeHandleBoundaries) {
                Modifier
                    .background(Color.Yellow)
            }
            .systemGestureExclusion()
            .padding(windowSpec.constraints.resizeHandleDrawPadding),
    ) {
        val handleShape = remember(handle, thickness, cornerRadius) {
            handle.shape(thickness, cornerRadius)
        }
        Box(
            modifier = Modifier
                .matchParentSize()
                .graphicsLayer {
                    this.shape = handleShape
                    this.clip = true
                    this.shadowElevation = shadowElevation.toPx()
                    this.ambientShadowColor = shadowColor
                    this.spotShadowColor = shadowColor
                    this.alpha = currentAlpha
                }
                .background(handleColor),
        )
    }
}

@Composable
fun BoxScope.ImeWindowResizeHandlesFixed() {
    val windowController = LocalWindowController.current

    val windowSpec by windowController.activeWindowSpec.collectAsState()
    val windowConfig by windowController.activeWindowConfig.collectAsState()
    val editorState by windowController.editor.state.collectAsState()

    val visible by remember {
        derivedStateOf {
            editorState.isEnabled && windowSpec is ImeWindowSpec.Fixed
        }
    }
    val animatedAlpha = animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(350),
    )

    val attributes = remember(windowConfig.mode) {
        mapOf(
            DrsImeUi.Attr.WindowMode to windowConfig.mode
        )
    }

    val overlayStyle = rememberSnyggThemeQuery(DrsImeUi.WindowResizeOverlayFixed.elementName)
    val overlayColor by rememberUpdatedState(overlayStyle.background(Color.Gray.copy(alpha = 0.5f)))

    if (visible) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .pointerInput(Unit) {
                    while (true) {
                        awaitPointerEventScope {
                            val event = awaitPointerEvent()
                            event.changes.forEach { it.consume() }
                        }
                    }
                }
                .background(overlayColor)
        )

        Row(
            modifier = Modifier
                .matchParentSize()
                .padding(vertical = windowSpec.constraints.resizeHandleTouchSize),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SnyggIconButton(
                elementName = DrsImeUi.WindowResizeAction.elementName,
                attributes = attributes,
                // DRS a11y/i18n/ux (r0-F): label the editor controls for TalkBack.
                contentDescription = stringRes(R.string.action__reset),
                onClick = { windowController.actions.resetFixedSize() },
            ) {
                SnyggIcon(
                    elementName = DrsImeUi.WindowResizeAction.elementName,
                    imageVector = drawableRes(R.drawable.ic_restart_alt),
                )
            }

            SnyggIconButton(
                DrsImeUi.WindowMoveHandle.elementName,
                attributes = attributes,
                contentDescription = stringRes(R.string.snygg__rule_element__window_move_handle),
                onClick = {}
            ) {
                SnyggIcon(
                    elementName = DrsImeUi.WindowMoveHandle.elementName,
                    imageVector = drawableRes(R.drawable.ic_drag_pan),
                    modifier = Modifier.imeWindowMoveHandle(windowController),
                )
            }

            SnyggIconButton(
                elementName = DrsImeUi.WindowResizeAction.elementName,
                attributes = attributes,
                contentDescription = stringRes(R.string.action__done),
                onClick = { windowController.editor.disable() },
            ) {
                SnyggIcon(
                    elementName = DrsImeUi.WindowResizeAction.elementName,
                    imageVector = drawableRes(R.drawable.ic_check),
                )
            }
        }

        ImeWindowResizeHandle(
            handle = ImeWindowResizeHandle.LEFT,
            modifier = Modifier
                .align(Alignment.CenterStart),
            alphaState = animatedAlpha,
        )
        ImeWindowResizeHandle(
            handle = ImeWindowResizeHandle.TOP_LEFT,
            modifier = Modifier
                .align(Alignment.TopStart),
            alphaState = animatedAlpha,
        )
        ImeWindowResizeHandle(
            handle = ImeWindowResizeHandle.TOP,
            modifier = Modifier
                .align(Alignment.TopCenter),
            alphaState = animatedAlpha,
        )
        ImeWindowResizeHandle(
            handle = ImeWindowResizeHandle.TOP_RIGHT,
            modifier = Modifier
                .align(Alignment.TopEnd),
            alphaState = animatedAlpha,
        )
        ImeWindowResizeHandle(
            handle = ImeWindowResizeHandle.RIGHT,
            modifier = Modifier
                .align(Alignment.CenterEnd),
            alphaState = animatedAlpha,
        )
        ImeWindowResizeHandle(
            handle = ImeWindowResizeHandle.BOTTOM_RIGHT,
            modifier = Modifier
                .align(Alignment.BottomEnd),
            alphaState = animatedAlpha,
        )
        ImeWindowResizeHandle(
            handle = ImeWindowResizeHandle.BOTTOM,
            modifier = Modifier
                .align(Alignment.BottomCenter),
            alphaState = animatedAlpha,
        )
        ImeWindowResizeHandle(
            handle = ImeWindowResizeHandle.BOTTOM_LEFT,
            modifier = Modifier
                .align(Alignment.BottomStart),
            alphaState = animatedAlpha,
        )
    }

}

@Composable
fun BoxScope.ImeWindowResizeHandlesFloating() {
    val windowController = LocalWindowController.current

    val windowSpec by windowController.activeWindowSpec.collectAsState()
    val editorState by windowController.editor.state.collectAsState()

    val isFloatingAndEditorAndNoGestureActive by remember {
        derivedStateOf {
            editorState.isEnabled && !editorState.isAnyGesture && windowSpec is ImeWindowSpec.Floating
        }
    }
    LaunchedEffect(isFloatingAndEditorAndNoGestureActive) {
        if (isFloatingAndEditorAndNoGestureActive) {
            delay(3000L)
            windowController.editor.disable()
        }
    }

    val visible by remember {
        derivedStateOf {
            editorState.isEnabled && !editorState.isMoveGesture && windowSpec is ImeWindowSpec.Floating
        }
    }
    val animatedAlpha = animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(350),
    )

    val offset = windowSpec.constraints.resizeHandleTouchOffsetFloating
    if (visible) {
        ImeWindowResizeHandle(
            handle = ImeWindowResizeHandle.TOP_LEFT,
            modifier = Modifier
                .align(Alignment.TopStart)
                .offset(-offset, -offset),
            alphaState = animatedAlpha,
        )
        ImeWindowResizeHandle(
            handle = ImeWindowResizeHandle.TOP_RIGHT,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(offset, -offset),
            alphaState = animatedAlpha,
        )
        ImeWindowResizeHandle(
            handle = ImeWindowResizeHandle.BOTTOM_RIGHT,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .offset(offset, offset),
            alphaState = animatedAlpha,
        )
        ImeWindowResizeHandle(
            handle = ImeWindowResizeHandle.BOTTOM_LEFT,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .offset(-offset, offset),
            alphaState = animatedAlpha,
        )
    }
}

fun Modifier.imeWindowMoveHandle(
    windowController: ImeWindowController,
    onTap: () -> Unit = {},
): Modifier = imeWindowEditorHandle(
    onTap = onTap,
    onBeginGesture = { windowController.editor.beginMoveGesture() },
    onGesture = { initialSpec, offset, rowCount, smartbarRowCount ->
        initialSpec.movedBy(offset, rowCount, smartbarRowCount).also { updatedSpec ->
            windowController.editor.onSpecUpdated(updatedSpec)
        }
    },
    onEndGesture = { windowController.editor.endMoveGesture(it) },
    onCancelGesture = { windowController.editor.cancelGesture() },
)

fun Modifier.imeWindowResizeHandle(
    windowController: ImeWindowController,
    handle: ImeWindowResizeHandle,
    onTap: () -> Unit = {},
): Modifier = imeWindowEditorHandle(
    onTap = onTap,
    onBeginGesture = { windowController.editor.beginResizeGesture() },
    onGesture = { initialSpec, offset, rowCount, smartbarRowCount ->
        initialSpec.resizedBy(offset, handle, rowCount, smartbarRowCount).also { updatedSpec ->
            windowController.editor.onSpecUpdated(updatedSpec)
        }
    },
    onEndGesture = { windowController.editor.endResizeGesture(it) },
    onCancelGesture = { windowController.editor.cancelGesture() },
)

private fun Modifier.imeWindowEditorHandle(
    onTap: () -> Unit,
    onBeginGesture: () -> ImeWindowSpec,
    onGesture: (initialSpec: ImeWindowSpec, offset: DpOffset, rowCount: Int, smartbarRowCount: Int) -> ImeWindowSpec,
    onEndGesture: (finalSpec: ImeWindowSpec) -> Unit,
    onCancelGesture: () -> Unit,
): Modifier = this.composed {
    val rowCount by DrsImeSizing.rowCountAsState()
    val smartbarRowCount by DrsImeSizing.smartbarRowCountAsState()
    Modifier
        .pointerInput(Unit) {
            detectTapGestures {
                onTap()
            }
        }
        .pointerInput(Unit) {
            // DRS v1.27.0 — the v1.25-era evaluation TODO is closed as a
            // documented decision: the current scheme (accumulate raw px
            // deltas during the drag, convert to dp once per event, apply
            // to a captured initialSpec) is the correct one, not a
            // placeholder. The absolute-coordinates alternative would add
            // an onGloballyPositioned listener per handle and still pass
            // through the same dp conversion inside movedBy/resizedBy's
            // constrained() round-trip, so it cannot be more accurate —
            // and applying offsets against a frozen initial spec (instead
            // of re-reading a mutated one) is what keeps slow drags from
            // compounding rounding through repeated baseline/effective
            // conversions. The pipeline is covered by the JVM editor
            // move/resize tests.
            var accumulatedOffset = Offset.Zero
            var initialSpec = ImeWindowSpec.Fallback
            var currentSpec = ImeWindowSpec.Fallback
            detectDragGestures(
                onDragStart = {
                    accumulatedOffset = Offset.Zero
                    initialSpec = onBeginGesture()
                    currentSpec = initialSpec
                },
                onDrag = { change, dragAmount ->
                    change.consume()
                    accumulatedOffset += dragAmount
                    currentSpec = onGesture(initialSpec, accumulatedOffset.toDp(), rowCount, smartbarRowCount)
                },
                onDragEnd = {
                    onEndGesture(currentSpec)
                },
                onDragCancel = {
                    onCancelGesture()
                },
            )
        }
}
