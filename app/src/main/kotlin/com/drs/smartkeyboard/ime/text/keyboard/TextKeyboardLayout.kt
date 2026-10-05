/*
 * Copyright (C) 2021-2025 The DRS Smart Keyboard Project
 */

package com.drs.smartkeyboard.ime.text.keyboard

import com.drs.smartkeyboard.drs.DrsMotion
import com.drs.smartkeyboard.drs.rememberDrsMotionEnabled
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloatAsState
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.provider.Settings
import android.view.MotionEvent
import android.view.animation.AccelerateInterpolator
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Circle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import com.drs.smartkeyboard.DrsImeService
import com.drs.smartkeyboard.app.DrsPreferenceStore
import com.drs.smartkeyboard.editorInstance
import com.drs.smartkeyboard.glideTypingManager
import com.drs.smartkeyboard.ime.editor.OperationScope
import com.drs.smartkeyboard.ime.editor.OperationUnit
import com.drs.smartkeyboard.ime.input.InputEventDispatcher
import com.drs.smartkeyboard.ime.input.InputShiftState
import com.drs.smartkeyboard.ime.input.LocalInputFeedbackController
import com.drs.smartkeyboard.ime.keyboard.ComputingEvaluator
import com.drs.smartkeyboard.ime.keyboard.DrsImeSizing
import com.drs.smartkeyboard.ime.keyboard.KeyboardMode
import com.drs.smartkeyboard.ime.keyboard.SpaceBarMode
import com.drs.smartkeyboard.ime.keyboard.computeLabel
import com.drs.smartkeyboard.ime.keyboard.keyA11yLabelRes
import com.drs.smartkeyboard.ime.keyboard.shiftStateA11yRes
import com.drs.smartkeyboard.ime.popup.ExceptionsForKeyCodes
import com.drs.smartkeyboard.ime.popup.PopupUiController
import com.drs.smartkeyboard.ime.popup.rememberPopupUiController
import com.drs.smartkeyboard.ime.keyboard.SplitLayout
import com.drs.smartkeyboard.ime.window.ImeWindowMode
import com.drs.smartkeyboard.ime.text.gestures.GlideTypingGesture
import com.drs.smartkeyboard.ime.text.gestures.SwipeAction
import com.drs.smartkeyboard.ime.text.gestures.SwipeGesture
import com.drs.smartkeyboard.ime.text.key.KeyCode
import com.drs.smartkeyboard.ime.text.key.KeyType
import com.drs.smartkeyboard.ime.text.key.KeyVariation
import com.drs.smartkeyboard.ime.theme.DrsImeUi
import com.drs.smartkeyboard.ime.window.LocalWindowController
import com.drs.smartkeyboard.keyboardManager
import com.drs.smartkeyboard.lib.DrsRect
import com.drs.smartkeyboard.lib.Pointer
import com.drs.smartkeyboard.lib.PointerMap
import com.drs.smartkeyboard.lib.devtools.LogTopic
import com.drs.smartkeyboard.lib.devtools.flogDebug
import com.drs.smartkeyboard.lib.devtools.flogError
import com.drs.smartkeyboard.lib.toIntOffset
import org.drs.jetpref.datastore.model.collectAsState
import com.drs.smartkeyboard.ime.window.ImeWindowSpec
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.onFailure
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import org.drs.lib.android.isOrientationLandscape
import org.drs.lib.compose.DisposableLifecycleEffect
import org.drs.lib.snygg.SnyggSelector
import org.drs.lib.snygg.ui.SnyggBox
import org.drs.lib.snygg.ui.SnyggIcon
import org.drs.lib.snygg.ui.SnyggText
import org.drs.lib.snygg.ui.rememberSnyggThemeQuery
import kotlin.math.abs
import kotlin.math.sqrt

@SuppressLint("UnusedBoxWithConstraintsScope")
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun TextKeyboardLayout(
    modifier: Modifier = Modifier,
    evaluator: ComputingEvaluator,
): Unit = with(LocalDensity.current) {
    val prefs by DrsPreferenceStore
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val glideTypingManager by context.glideTypingManager()

    val keyboard = evaluator.keyboard as TextKeyboard
    val glideEnabledInternal by prefs.glide.enabled.collectAsState()
    val glideEnabled = glideEnabledInternal && evaluator.editorInfo.isRichInputEditor &&
        evaluator.state.keyVariation != KeyVariation.PASSWORD
    val glideShowTrail by prefs.glide.showTrail.collectAsState()
    val glideTrailStyle = rememberSnyggThemeQuery(DrsImeUi.GlideTrail.elementName)
    val glideTrailColor = glideTrailStyle.foreground(default = Color.Green)

    val controller = remember { TextKeyboardLayoutController(context) }.also {
        it.keyboard = keyboard
        if (glideEnabled && keyboard.mode == KeyboardMode.CHARACTERS) {
            val keys = keyboard.keys().asSequence().toList()
            glideTypingManager.setLayout(keys)
        }
    }
    val touchEventChannel = remember { Channel<MotionEvent>(64) }

    fun resetAllKeys() {
        try {
            val event = MotionEvent.obtain(0L, 0L, MotionEvent.ACTION_CANCEL, 0f, 0f, 0)
            controller.onTouchEventInternal(event)
            controller.popupUiController.hide()
            event.recycle()
        } catch (_: Throwable) {
            // Ignore
        }
    }

    DisposableEffect(Unit) {
        controller.glideTypingDetector.registerListener(controller)
        controller.glideTypingDetector.registerListener(glideTypingManager)
        onDispose {
            controller.glideTypingDetector.unregisterListener(controller)
            controller.glideTypingDetector.unregisterListener(glideTypingManager)
            resetAllKeys()
        }
    }

    DisposableLifecycleEffect(
        onResume = { /* Do nothing */ },
        onPause = { resetAllKeys() },
    )

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(DrsImeSizing.keyboardUiHeight())
            .onGloballyPositioned { coords ->
                controller.size = coords.size.toSize()
            }
            .pointerInteropFilter { event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN,
                    MotionEvent.ACTION_POINTER_DOWN,
                    MotionEvent.ACTION_MOVE,
                    MotionEvent.ACTION_POINTER_UP,
                    MotionEvent.ACTION_UP,
                    MotionEvent.ACTION_CANCEL,
                        -> {
                        val clonedEvent = MotionEvent.obtain(event)
                        touchEventChannel
                            .trySend(clonedEvent)
                            .onFailure {
                                // Make sure to prevent MotionEvent memory leakage
                                // in case the input channel is full
                                clonedEvent.recycle()
                            }
                        return@pointerInteropFilter true
                    }
                }
                return@pointerInteropFilter false
            }
            .drawWithContent {
                drawContent()
                if (glideEnabled && glideShowTrail) {
                    // DRS v1.7.0: the trail geometry is density-correct now
                    // (dp converted through the DrawScope's density — the
                    // radius used to be RAW PIXELS, so the ribbon rendered
                    // ~2x thinner on high-density phones) and follows the
                    // trail-width preference read at draw time.
                    val targetDist = 3.dp.toPx()
                    val radius = 20.dp.toPx() * ImeWindowSpec.sanitizeGlideTrailScale(
                        prefs.glide.trailWidthPercent.get(),
                    )
                    val radiusReductionFactor = 0.99f
                    if (controller.fadingGlideFraction > 0) {
                        controller.drawGlideTrail(
                            this,
                            controller.fadingGlide,
                            targetDist,
                            radius * controller.fadingGlideFraction,
                            radiusReductionFactor,
                            glideTrailColor,
                        )
                    }
                    if (controller.isGliding && controller.glideDataForDrawing.isNotEmpty()) {
                        controller.drawGlideTrail(
                            this, controller.glideDataForDrawing, targetDist, radius,
                            radiusReductionFactor, glideTrailColor,
                        )
                    }
                }
            },
    ) {
        // FIXME (when rewriting TextKeyboardLayout): constrains.maxWidth is not stable!
        val keyboardWidth = constraints.maxWidth.toFloat()
        val keyboardHeight = constraints.maxHeight.toFloat()
        val keyboardRowBaseHeight = DrsImeSizing.keyboardRowBaseHeight

        val windowController = LocalWindowController.current
        val windowSpec by windowController.activeWindowSpec.collectAsState()
        val keyMarginH by remember { derivedStateOf { windowSpec.keyMarginH.toPx() } }
        val keyMarginV by remember { derivedStateOf { windowSpec.keyMarginV.toPx() } }

        // DRS v1.18.0: split keyboard — compute the central gap in pixels
        // for this keyboard from the SplitMode preference and the real
        // window width. Only the normal fixed window mode splits (compact
        // one-handed and floating windows are already narrow), and only the
        // letters page — the numeric/phone pads are small centered grids
        // where a mid-row gap would look broken rather than helpful.
        val splitMode by prefs.keyboard.splitMode.collectAsState()
        val splitGapPx = with(LocalDensity.current) {
            val spec = windowSpec
            val isNormalFixed = spec is ImeWindowSpec.Fixed &&
                spec.fixedMode == ImeWindowMode.Fixed.NORMAL
            if (keyboard.mode == KeyboardMode.CHARACTERS && isNormalFixed) {
                SplitLayout.gapWidthPx(splitMode, keyboardWidth, density)
            } else {
                0f
            }
        }

        val desiredKey = remember(
            keyboard, keyboardWidth, keyboardHeight, keyMarginH, keyMarginV,
            keyboardRowBaseHeight, evaluator, splitGapPx
        ) {
            TextKey(data = TextKeyData.UNSPECIFIED).also { desiredKey ->
                desiredKey.touchBounds.apply {
                    width = keyboardWidth / 10f
                    height = when (keyboard.mode) {
                        KeyboardMode.CHARACTERS,
                        KeyboardMode.NUMERIC_ADVANCED,
                        KeyboardMode.SYMBOLS,
                        KeyboardMode.SYMBOLS2 -> {
                            (keyboardHeight / keyboard.rowCount)
                                .coerceAtMost(keyboardRowBaseHeight.toPx() * 1.12f)
                        }
                        else -> keyboardRowBaseHeight.toPx()
                    }
                }
                desiredKey.visibleBounds.applyFrom(desiredKey.touchBounds).deflateBy(keyMarginH, keyMarginV)
                keyboard.layout(keyboardWidth, keyboardHeight, desiredKey, true, splitGapPx)
            }
        }

        val desiredKeyHack = rememberUpdatedState(desiredKey) // TODO quick'n'dirty hack
        val popupUiController = rememberPopupUiController(
            key1 = keyboard,
            key2 = Unit, // TODO quick'n'dirty hack
            boundsProvider = { key ->
                val keyPopupWidth: Float
                val keyPopupHeight: Float
                // DRS v1.6.0: the key-preview scale preference multiplies
                // the per-orientation popup multipliers. Read at call time
                // (popup show) so a settings change applies on the next
                // preview without any recomposition plumbing.
                val previewScale = ImeWindowSpec.sanitizeKeyPreviewScale(
                    prefs.keyboard.previewScalePercent.get(),
                )
                when {
                    configuration.isOrientationLandscape() -> {
                        keyPopupWidth = desiredKeyHack.value.visibleBounds.width * 1.0f * previewScale
                        keyPopupHeight = desiredKeyHack.value.visibleBounds.height * 3.0f * previewScale
                    }
                    else -> {
                        keyPopupWidth = desiredKeyHack.value.visibleBounds.width * 1.1f * previewScale
                        keyPopupHeight = desiredKeyHack.value.visibleBounds.height * 2.5f * previewScale
                    }
                }
                val keyPopupDiffX = (key.visibleBounds.width - keyPopupWidth) / 2.0f
                DrsRect.new().apply {
                    left = key.visibleBounds.left + keyPopupDiffX
                    top = key.visibleBounds.bottom - keyPopupHeight
                    right = left + keyPopupWidth
                    bottom = top + keyPopupHeight
                }
            },
            isSuitableForBasicPopup = { key ->
                if (key is TextKey) {
                    val keyCode = key.computedData.code
                    val keyType = key.computedData.type
                    val numeric = keyboard.mode == KeyboardMode.NUMERIC ||
                        keyboard.mode == KeyboardMode.PHONE || keyboard.mode == KeyboardMode.PHONE2 ||
                        keyboard.mode == KeyboardMode.NUMERIC_ADVANCED && keyType == KeyType.NUMERIC
                    keyCode > KeyCode.SPACE && keyCode != KeyCode.CJK_SPACE && !numeric
                } else {
                    true
                }
            },
            isSuitableForExtendedPopup = { key ->
                if (key is TextKey) {
                    val keyCode = key.computedData.code
                    keyCode > KeyCode.SPACE && keyCode != KeyCode.CJK_SPACE || ExceptionsForKeyCodes.contains(keyCode)
                } else {
                    true
                }
            },
        )
        popupUiController.evaluator = evaluator
        popupUiController.keyHintConfiguration = prefs.keyboard.keyHintConfiguration()
        // DRS v2.6.0 «المنبثق الحي الصادق»: sliding between extended-popup
        // elements now ticks the existing gestureMovingSwipe feedback
        // contract — the same one the spacebar cursor-slide already uses.
        // The last silent surface of the board finally speaks, reusing
        // preferences that already exist instead of minting new ones. The
        // controller is read from the CompositionLocal (provided at the
        // IME root) because this composable sits outside the touch-loop
        // class that owns the service-locator getter.
        val inputFeedback = LocalInputFeedbackController.current
        popupUiController.onActiveElementChanged = { data ->
            inputFeedback?.gestureMovingSwipe(data)
        }
        controller.popupUiController = popupUiController
        val debugShowTouchBoundaries by prefs.devtools.showKeyTouchBoundaries.collectAsState()
        // DRS v2.4.0: the motion-respect signal is read ONCE for the whole
        // board and handed to every key — the pulse, the caps-lock dot and
        // the row-entrance wave all snap together when the system removes
        // animations (same contract the two strips have honored since
        // v2.2.2 — the board itself is the last surface to join).
        val motionEnabled = rememberDrsMotionEnabled()
        // DRS v2.4.0: rows (not a flat key stream) drive the composition
        // order — each key carries its row index so the entrance wave can
        // stagger deterministically.
        for ((rowIndex, row) in keyboard.arrangement.withIndex()) {
            for (textKey in row) {
                TextKeyButton(
                    textKey, evaluator, desiredKey,
                    debugShowTouchBoundaries,
                    motionEnabled, rowIndex,
                )
            }
        }

        popupUiController.RenderPopups()
    }

    LaunchedEffect(Unit) {
        // DRS (C1): the key-event touch loop is the single dispatcher for
        // ALL keyboard input — one throwing touch event (rotated key,
        // disposed evaluator, exotic popup state…) used to kill the whole
        // LaunchedEffect and leave the keyboard permanently dead to input.
        // Every event is now dispatched inside a Throwable shield; a failed
        // event is flog-reported, the event is ALWAYS recycled (finally),
        // and the loop continues with the next event.
        for (event in touchEventChannel) {
            if (!isActive) break
            try {
                controller.onTouchEventInternal(event)
            } catch (t: Throwable) {
                flogError(LogTopic.KEY_EVENTS) { "Touch event dispatch failed: $t" }
            } finally {
                event.recycle()
            }
        }
    }
}

@Composable
private fun TextKeyButton(
    key: TextKey,
    evaluator: ComputingEvaluator,
    desiredKey: TextKey,
    debugShowTouchBoundaries: Boolean,
    motionEnabled: Boolean,
    rowIndex: Int,
) = with(LocalDensity.current) {
    // DRS (B9): the attributes map was rebuilt on EVERY recomposition of
    // every key (press ripple, trail draw, …) although its content only
    // depends on the key's code, the evaluator state and the keyboard mode.
    // KeyboardState is identity-equal per evaluator snapshot, so the map is
    // now re-derived exactly when one of those inputs changes; the map
    // content is byte-identical to the old inline build.
    val attributes = remember(
        key.computedData.code,
        evaluator.state,
        evaluator.keyboard.mode,
    ) {
        mapOf(
            DrsImeUi.Attr.Code to key.computedData.code,
            DrsImeUi.Attr.Mode to evaluator.keyboard.mode.toString(),
            DrsImeUi.Attr.ShiftState to evaluator.state.inputShiftState.toString(),
            // DRS v1.23.0: the CTRL/ALT/FN latch states reach the theme — the
            // same recompute pipeline that re-renders on shift changes
            // re-renders on latch changes, so the base stylesheet (and any
            // user rule) can highlight the armed keys live.
            DrsImeUi.Attr.CtrlState to evaluator.state.inputCtrlState.toString(),
            DrsImeUi.Attr.AltState to evaluator.state.inputAltState.toString(),
            DrsImeUi.Attr.FnState to evaluator.state.inputFnState.toString(),
        )
    }
    val selector = when {
        !key.isEnabled -> SnyggSelector.DISABLED
        key.isPressed -> SnyggSelector.PRESSED
        else -> SnyggSelector.NONE
    }
    val size = remember(key, desiredKey) {
        key.visibleBounds.size.toDpSize()
    }
    // DRS M2.2 — the Design-2030 key-tap pulse: pressed compresses to
    // 0.94 fast (60ms), release springs back slower (140ms). DRAW-ONLY:
    // the scale rides graphicsLayer so layout is never re-measured.
    // DRS v2.4.0: the pulse finally respects the system rhythm — when
    // «remove animations» zeroes ANIMATOR_DURATION_SCALE the durations
    // snap to 0 and the states still land on identical final targets.
    val pulseScale by animateFloatAsState(
        targetValue = DrsMotion.scaleFor(key.isPressed),
        animationSpec = tween(
            durationMillis = DrsMotion.durationOrSnap(
                DrsMotion.durationFor(key.isPressed),
                motionEnabled,
            ),
        ),
        label = "drsKeyPulse",
    )
    // DRS v2.4.0 — «موجة دخول اللوحة»: whenever the arrangement identity
    // changes (mode switch, subtype switch, number-row toggle, IME reopen)
    // the rows enter with the same capped wave the candidates row uses:
    // alpha 0→1 plus a 5dp settle, delayed per row by the pinned stagger
    // contract. Total wave ≤ stagger cap (112ms) + 130ms. Draw-only and
    // motion-gated: with animations off the Animatable STARTS at 1 so no
    // frame is ever an empty board.
    val entranceDropPx = 5.dp.toPx()
    val entrance = remember(key) { Animatable(if (motionEnabled) 0f else 1f) }
    LaunchedEffect(key, motionEnabled) {
        if (!motionEnabled) {
            entrance.snapTo(1f)
        } else if (entrance.value < 1f) {
            delay(DrsMotion.staggerFor(rowIndex).toLong())
            entrance.animateTo(1f, tween(durationMillis = DrsMotion.ENTRANCE_DURATION_MS))
        }
    }
    // DRS v1.17.0: every key finally speaks to TalkBack. Icon-only keys
    // (shift/backspace/enter/arrows…) get a localized spoken label from
    // the pure [keyA11yLabelRes] mapping; all other keys fall back to the
    // evaluator's computed label or the key's own display string.
    val a11yDescription = remember(key.computedData, key.label) {
        val spoken = keyA11yLabelRes(key.computedData.code)?.let { evaluator.context()?.getString(it) }
        spoken
            ?: evaluator.computeLabel(key.computedData)
            ?: key.computedData.asString(isForDisplay = true)
    }
    // DRS v2.4.0: the shift key speaks its STATE — lowercase / shifted /
    // caps-locked — via the pure shiftStateA11yRes mapping; until now all
    // three states announced identically to TalkBack.
    val shiftStateDescription = if (key.computedData.code == KeyCode.SHIFT) {
        evaluator.context()?.getString(shiftStateA11yRes(evaluator.state.inputShiftState))
    } else {
        null
    }
    SnyggBox(
        DrsImeUi.Key.elementName,
        attributes = attributes,
        selector = selector,
        modifier = Modifier
            .requiredSize(size)
            .graphicsLayer {
                scaleX = pulseScale
                scaleY = pulseScale
            }
            .absoluteOffset { key.visibleBounds.topLeft.toIntOffset() }
            .semantics {
                role = Role.Button
                contentDescription = a11yDescription
                shiftStateDescription?.let { stateDescription = it }
            },
    ) {
        val isTelPadKey = key.computedData.type == KeyType.NUMERIC && evaluator.keyboard.mode == KeyboardMode.PHONE
        key.label?.let { label ->
            var customLabel = label
            if (key.computedData.code == KeyCode.SPACE) {
                val prefs by DrsPreferenceStore
                val spaceBarMode by prefs.keyboard.spaceBarMode.collectAsState()
                when (spaceBarMode) {
                    SpaceBarMode.NOTHING -> return@let
                    SpaceBarMode.CURRENT_LANGUAGE -> {}
                    SpaceBarMode.SPACE_BAR_KEY -> customLabel = "␣"
                }
            }
            SnyggText(
                modifier = Modifier
                    .wrapContentSize()
                    .align(if (isTelPadKey) BiasAlignment(-0.5f, 0f) else Alignment.Center),
                text = customLabel,
            )
        }
        key.hintedLabel?.let { hintedLabel ->
            SnyggText(
                elementName = DrsImeUi.KeyHint.elementName,
                attributes = attributes,
                selector = selector,
                modifier = Modifier
                    .wrapContentSize()
                    .align(if (isTelPadKey) BiasAlignment(0.5f, 0f) else Alignment.TopEnd),
                text = hintedLabel,
            )
        }
        key.foregroundImageVector?.let { imageVector ->
            SnyggIcon(
                modifier = Modifier.align(Alignment.Center),
                imageVector = imageVector,
                contentDescription = null,
            )
        }
        // DRS v2.4.0 — «نقطة قفل الأحرف الصادقة»: caps lock stops riding a
        // silent theme attribute alone. The shift key carries a small dot
        // driven by the EXACT v2.2.1 toggle-dot contract (grow 140ms in,
        // shrink 90ms out), rendered as a theme-styled SnyggIcon so the
        // color follows the stylesheet's foreground for the key — which
        // the base theme already recolors in caps lock. Draw-only: scale
        // on graphicsLayer, zero geometry change, motion-gated.
        if (key.computedData.code == KeyCode.SHIFT) {
            val capsLockActive = evaluator.state.inputShiftState == InputShiftState.CAPS_LOCK
            val dotScale by animateFloatAsState(
                targetValue = DrsMotion.dotScaleFor(capsLockActive),
                animationSpec = tween(
                    durationMillis = DrsMotion.durationOrSnap(
                        DrsMotion.dotDurationFor(capsLockActive),
                        motionEnabled,
                    ),
                ),
                label = "drsCapsLockDot",
            )
            SnyggIcon(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 5.dp)
                    .size(5.dp)
                    .graphicsLayer {
                        scaleX = dotScale
                        scaleY = dotScale
                    },
                imageVector = Icons.Filled.Circle,
                contentDescription = null,
            )
        }
    }
    if (debugShowTouchBoundaries) {
        Box(
            modifier = Modifier
                .requiredSize(key.touchBounds.size.toDpSize())
                .absoluteOffset { key.touchBounds.topLeft.toIntOffset() }
                .border(Dp.Hairline, Color.Red),
        )
    }
}

@Suppress("unused_parameter")
private class TextKeyboardLayoutController(
    // DRS v2.4.0: promoted to a property — the glide-trail fade reads the
    // system animator scale from it at cancel time (one cheap read per
    // gesture end, more accurate than a cached composition value).
    private val context: Context,
) : SwipeGesture.Listener, GlideTypingGesture.Listener {
    private val prefs by DrsPreferenceStore
    private val editorInstance by context.editorInstance()
    private val keyboardManager by context.keyboardManager()

    private val inputEventDispatcher get() = keyboardManager.inputEventDispatcher
    private val inputFeedbackController get() = DrsImeService.inputFeedbackController()
    private val keyHintConfiguration = prefs.keyboard.keyHintConfiguration()
    private val pointerMap: PointerMap<TouchPointer> = PointerMap { TouchPointer() }
    lateinit var popupUiController: PopupUiController

    private var initSelectionStart: Int = 0
    private var initSelectionEnd: Int = 0
    var isGliding by mutableStateOf(false)

    val glideTypingDetector = GlideTypingGesture.Detector(context)
    val glideDataForDrawing = mutableStateListOf<Pair<GlideTypingGesture.Detector.Position, Long>>()
    val fadingGlide = mutableStateListOf<Pair<GlideTypingGesture.Detector.Position, Long>>()
    // DRS v1.7.0: the fade-out now animates a FRACTION (1 -> 0) instead of
    // a raw pixel radius — the px radius is derived at draw time from the
    // density-correct baseline times the trail-width scale, so the fading
    // ribbon matches the live one on every density and setting.
    var fadingGlideFraction by mutableFloatStateOf(0.0f)
    private val swipeGestureDetector = SwipeGesture.Detector(this)

    lateinit var keyboard: TextKeyboard
    var size = Size.Zero

    val isGlideEnabled: Boolean get() = prefs.glide.enabled.get() && editorInstance.activeInfo.isRichInputEditor &&
        keyboardManager.activeState.keyVariation != KeyVariation.PASSWORD

    fun onTouchEventInternal(event: MotionEvent) {
        flogDebug { "event=$event" }
        swipeGestureDetector.onTouchEvent(event)
        if (isGlideEnabled && keyboard.mode == KeyboardMode.CHARACTERS) {
            // DRS v1.20.0: the glide pointer used to be looked up by hardcoded pointer ID 0,
            // so a glide started by a second finger inherited (or missed) the long-press
            // blocking state of the FIRST touch. Blocking must consider every active pointer,
            // and the gesture's initial key comes from the stream's first touch (which is
            // the pointer the detector actually tracks — it latches the DOWN pointer).
            val glideInitialKey = pointerMap.firstOrNull()?.initialKey
            val isNotBlocked = pointerMap.none { it.hasTriggeredLongPress }
            if (isNotBlocked && glideTypingDetector.onTouchEvent(event, glideInitialKey)) {
                for (pointer in pointerMap) {
                    if (pointer.activeKey != null) {
                        onTouchCancelInternal(event, pointer)
                    }
                }
                if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) {
                    pointerMap.clear()
                }
                isGliding = true
                return
            }
        }

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val pointerIndex = event.actionIndex
                val pointerId = event.getPointerId(pointerIndex)
                val pointer = pointerMap.add(pointerId, pointerIndex)
                if (pointer != null) {
                    swipeGestureDetector.onTouchDown(event, pointer)
                    onTouchDownInternal(event, pointer)
                }
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                val pointerIndex = event.actionIndex
                val pointerId = event.getPointerId(pointerIndex)
                val oldPointer = pointerMap.findById(pointerId)
                if (oldPointer != null) {
                    swipeGestureDetector.onTouchCancel(event, oldPointer)
                    onTouchCancelInternal(event, oldPointer)
                    pointerMap.removeById(oldPointer.id)
                }
                // Search for active character keys and cancel them
                for (pointer in pointerMap) {
                    val activeKey = pointer.activeKey
                    if (activeKey != null && popupUiController.isSuitableForPopups(activeKey)) {
                        swipeGestureDetector.onTouchCancel(event, pointer)
                        onTouchUpInternal(event, pointer)
                    }
                }
                val pointer = pointerMap.add(pointerId, pointerIndex)
                if (pointer != null) {
                    swipeGestureDetector.onTouchDown(event, pointer)
                    onTouchDownInternal(event, pointer)
                }
            }
            MotionEvent.ACTION_MOVE -> {
                for (pointerIndex in 0 until event.pointerCount) {
                    val pointerId = event.getPointerId(pointerIndex)
                    val pointer = pointerMap.findById(pointerId)
                    if (pointer != null) {
                        pointer.index = pointerIndex
                        val alwaysTriggerOnMove = (pointer.hasTriggeredGestureMove
                            && (pointer.initialKey?.computedData?.code == KeyCode.DELETE
                            && prefs.gestures.deleteKeySwipeLeft.get().let {
                                it == SwipeAction.DELETE_CHARACTERS_PRECISELY || it == SwipeAction.SELECT_CHARACTERS_PRECISELY
                            }
                            || pointer.initialKey?.computedData?.code == KeyCode.SPACE
                            || pointer.initialKey?.computedData?.code == KeyCode.CJK_SPACE))
                        if (swipeGestureDetector.onTouchMove(event, pointer, alwaysTriggerOnMove) || pointer.hasTriggeredGestureMove) {
                            pointer.hasTriggeredGestureMove = true
                            pointer.activeKey?.let { activeKey ->
                                inputEventDispatcher.sendCancel(activeKey.computedDataOnDown)
                            }
                        } else {
                            onTouchMoveInternal(event, pointer)
                        }
                    }
                }
            }
            MotionEvent.ACTION_POINTER_UP -> {
                val pointerIndex = event.actionIndex
                val pointerId = event.getPointerId(pointerIndex)
                val pointer = pointerMap.findById(pointerId)
                if (pointer != null) {
                    pointer.index = pointerIndex
                    if (swipeGestureDetector.onTouchUp(event, pointer) || pointer.hasTriggeredGestureMove) {
                        if (pointer.hasTriggeredGestureMove && pointer.initialKey?.computedData?.code == KeyCode.DELETE) {
                            val selection = editorInstance.activeContent.selection
                            if (selection.isSelectionMode) {
                                editorInstance.deleteBackwards(OperationUnit.CHARACTERS)
                            }
                        }
                        onTouchCancelInternal(event, pointer)
                    } else {
                        onTouchUpInternal(event, pointer)
                    }
                    pointerMap.removeById(pointer.id)
                }
            }
            MotionEvent.ACTION_UP -> {
                val pointerIndex = event.actionIndex
                val pointerId = event.getPointerId(pointerIndex)
                for (pointer in pointerMap) {
                    if (pointer.id == pointerId) {
                        pointer.index = pointerIndex
                        if (swipeGestureDetector.onTouchUp(event, pointer) || pointer.hasTriggeredGestureMove) {
                            if (pointer.hasTriggeredGestureMove &&
                                pointer.initialKey?.computedData?.code == KeyCode.DELETE &&
                                prefs.gestures.deleteKeySwipeLeft.get() != SwipeAction.SELECT_CHARACTERS_PRECISELY &&
                                prefs.gestures.deleteKeySwipeLeft.get() != SwipeAction.SELECT_WORDS_PRECISELY) {
                                val selection = editorInstance.activeContent.selection
                                if (selection.isSelectionMode) {
                                    editorInstance.deleteBackwards(OperationUnit.CHARACTERS)
                                }
                            }
                            onTouchCancelInternal(event, pointer)
                        } else {
                            onTouchUpInternal(event, pointer)
                        }
                    } else {
                        swipeGestureDetector.onTouchCancel(event, pointer)
                        onTouchCancelInternal(event, pointer)
                    }
                }
                pointerMap.clear()
            }
            MotionEvent.ACTION_CANCEL -> {
                for (pointer in pointerMap) {
                    swipeGestureDetector.onTouchCancel(event, pointer)
                    onTouchCancelInternal(event, pointer)
                }
                pointerMap.clear()
            }
        }
    }

    private fun onTouchDownInternal(event: MotionEvent, pointer: TouchPointer) {
        flogDebug(LogTopic.TEXT_KEYBOARD_VIEW) { "pointer=$pointer" }

        val key = keyboard.getKeyForPos(event.getX(pointer.index), event.getY(pointer.index))
        if (key != null && key.isEnabled) {
            key.computedDataOnDown = key.computedData
            pointer.pressedKeyInfo = inputEventDispatcher.sendDown(
                data = key.computedData,
                onLongPress = onLongPress@ {
                    pointer.hasTriggeredLongPress = true
                    when (key.computedData.code) {
                        KeyCode.SPACE, KeyCode.CJK_SPACE -> {
                            when (prefs.gestures.spaceBarLongPress.get()) {
                                SwipeAction.NO_ACTION,
                                SwipeAction.INSERT_SPACE -> {
                                }
                                else -> {
                                    keyboardManager.executeSwipeAction(prefs.gestures.spaceBarLongPress.get())
                                }
                            }
                            true
                        }
                        KeyCode.SHIFT -> {
                            if (inputEventDispatcher.isUninterruptedEventSequence(key.computedData)) {
                                inputEventDispatcher.sendDownUp(TextKeyData.CAPS_LOCK)
                                inputFeedbackController?.keyLongPress(key.computedData)
                            }
                            // We always return false here to prevent blockade for the up touch event
                            false
                        }
                        KeyCode.LANGUAGE_SWITCH -> {
                            inputEventDispatcher.sendDownUp(TextKeyData.SYSTEM_INPUT_METHOD_PICKER)
                            true
                        }
                        else -> {
                            if (popupUiController.isSuitableForPopups(key) && key.computedPopups.getPopupKeys(
                                    keyHintConfiguration
                                ).isNotEmpty()
                            ) {
                                popupUiController.extend(key, size)
                                inputFeedbackController?.keyLongPress(key.computedData)
                                true
                            } else {
                                false
                            }
                        }
                    }
                },
            )
            if (prefs.keyboard.popupEnabled.get() && popupUiController.isSuitableForPopups(key)) {
                popupUiController.show(key)
            }
            inputFeedbackController?.keyPress(key.computedData)
            key.isPressed = true
            if (pointer.initialKey == null) {
                pointer.initialKey = key
            }
            pointer.activeKey = key
            initSelectionStart = editorInstance.activeContent.selection.start
            initSelectionEnd = editorInstance.activeContent.selection.end
        } else {
            pointer.activeKey = null
        }
    }

    private fun onTouchMoveInternal(event: MotionEvent, pointer: TouchPointer) {
        flogDebug(LogTopic.TEXT_KEYBOARD_VIEW) { "pointer=$pointer" }

        val initialKey = pointer.initialKey
        val activeKey = pointer.activeKey
        if (initialKey != null && activeKey != null) {
            if (popupUiController.isShowingExtendedPopup) {
                val x = event.getX(pointer.index)
                val y = event.getY(pointer.index)
                if (!popupUiController.propagateMotionEvent(activeKey, x, y)) {
                    onTouchCancelInternal(event, pointer)
                    onTouchDownInternal(event, pointer)
                }
            } else {
                if ((event.getX(pointer.index) < activeKey.visibleBounds.left - 0.1f * activeKey.visibleBounds.width)
                    || (event.getX(pointer.index) > activeKey.visibleBounds.right + 0.1f * activeKey.visibleBounds.width)
                    || (event.getY(pointer.index) < activeKey.visibleBounds.top - 0.35f * activeKey.visibleBounds.height)
                    || (event.getY(pointer.index) > activeKey.visibleBounds.bottom + 0.35f * activeKey.visibleBounds.height)
                ) {
                    onTouchCancelInternal(event, pointer)
                    onTouchDownInternal(event, pointer)
                }
            }
        }
    }

    private fun onTouchUpInternal(event: MotionEvent, pointer: TouchPointer) {
        flogDebug(LogTopic.TEXT_KEYBOARD_VIEW) { "pointer=$pointer" }
        pointer.pressedKeyInfo?.cancelJobs()
        pointer.pressedKeyInfo = null

        if (pointer.hasTriggeredMassSelection) {
            pointer.hasTriggeredMassSelection = false
            editorInstance.massSelection.end()
        }

        val initialKey = pointer.initialKey
        val activeKey = pointer.activeKey
        if (initialKey != null && activeKey != null) {
            activeKey.isPressed = false
            if (popupUiController.isSuitableForPopups(activeKey)) {
                val retData = popupUiController.getActiveKeyData(activeKey)
                if (retData != null && !pointer.hasTriggeredGestureMove) {
                    if (retData == activeKey.computedData) {
                        if (activeKey.computedData != activeKey.computedDataOnDown) {
                            inputEventDispatcher.sendCancel(activeKey.computedDataOnDown)
                            inputEventDispatcher.sendDownUp(activeKey.computedData)
                        } else {
                            inputEventDispatcher.sendUp(activeKey.computedDataOnDown)
                        }
                    } else {
                        inputEventDispatcher.sendCancel(activeKey.computedDataOnDown)
                        inputEventDispatcher.sendDownUp(retData)
                    }
                } else {
                    inputEventDispatcher.sendCancel(activeKey.computedDataOnDown)
                }
                popupUiController.hide()
            } else {
                if (pointer.hasTriggeredGestureMove) {
                    inputEventDispatcher.sendCancel(activeKey.computedDataOnDown)
                } else {
                    if (activeKey.computedData != activeKey.computedDataOnDown) {
                        inputEventDispatcher.sendCancel(activeKey.computedDataOnDown)
                        inputEventDispatcher.sendDownUp(activeKey.computedData)
                    } else {
                        inputEventDispatcher.sendUp(activeKey.computedDataOnDown)
                    }
                }
            }
            pointer.activeKey = null
        }
        pointer.hasTriggeredGestureMove = false
    }

    private fun onTouchCancelInternal(event: MotionEvent, pointer: TouchPointer) {
        flogDebug(LogTopic.TEXT_KEYBOARD_VIEW) { "pointer=$pointer" }
        pointer.pressedKeyInfo?.cancelJobs()
        pointer.pressedKeyInfo = null

        if (pointer.hasTriggeredMassSelection) {
            pointer.hasTriggeredMassSelection = false
            editorInstance.massSelection.end()
        }

        val activeKey = pointer.activeKey
        if (activeKey != null) {
            activeKey.isPressed = false
            inputEventDispatcher.sendCancel(activeKey.computedDataOnDown)
            if (popupUiController.isSuitableForPopups(activeKey)) {
                popupUiController.hide()
            }
            pointer.activeKey = null
        }
        pointer.hasTriggeredGestureMove = false
    }

    override fun onSwipe(event: SwipeGesture.Event): Boolean {
        val pointer = pointerMap.findById(event.pointerId) ?: return false
        val initialKey = pointer.initialKey ?: return false
        val activeKey = pointer.activeKey
        flogDebug(LogTopic.TEXT_KEYBOARD_VIEW)

        return when (initialKey.computedData.code) {
            KeyCode.DELETE -> handleDeleteSwipe(event)
            KeyCode.SPACE, KeyCode.CJK_SPACE -> handleSpaceSwipe(event)
            else -> when {
                (initialKey.computedData.code == KeyCode.SHIFT && activeKey?.computedData?.code == KeyCode.SPACE ||
                    initialKey.computedData.code == KeyCode.SHIFT && activeKey?.computedData?.code == KeyCode.CJK_SPACE) &&
                    event.type == SwipeGesture.Type.TOUCH_MOVE -> handleSpaceSwipe(event)
                initialKey.computedData.code == KeyCode.SHIFT && activeKey?.computedData?.code != KeyCode.SHIFT &&
                    event.type == SwipeGesture.Type.TOUCH_UP -> {
                    activeKey?.let {
                        inputEventDispatcher.sendUp(popupUiController.getActiveKeyData(it) ?: it.computedDataOnDown)
                    }
                    inputEventDispatcher.sendCancel(TextKeyData.SHIFT)
                    true
                }
                initialKey.computedData.code > KeyCode.SPACE && !popupUiController.isShowingExtendedPopup -> when {
                    !isGlideEnabled && !pointer.hasTriggeredGestureMove -> when (event.type) {
                        SwipeGesture.Type.TOUCH_UP -> {
                            val swipeAction = when (event.direction) {
                                SwipeGesture.Direction.UP -> prefs.gestures.swipeUp.get()
                                SwipeGesture.Direction.DOWN -> prefs.gestures.swipeDown.get()
                                SwipeGesture.Direction.LEFT -> prefs.gestures.swipeLeft.get()
                                SwipeGesture.Direction.RIGHT -> prefs.gestures.swipeRight.get()
                                else -> SwipeAction.NO_ACTION
                            }
                            if (swipeAction != SwipeAction.NO_ACTION) {
                                keyboardManager.executeSwipeAction(swipeAction)
                                true
                            } else {
                                false
                            }
                        }
                        else -> false
                    }
                    else -> false
                }
                else -> false
            }
        }
    }

    private fun handleDeleteSwipe(event: SwipeGesture.Event): Boolean {
        if (editorInstance.activeInfo.isRawInputEditor) return false

        return when (event.type) {
            SwipeGesture.Type.TOUCH_MOVE -> when (prefs.gestures.deleteKeySwipeLeft.get()) {
                SwipeAction.DELETE_CHARACTERS_PRECISELY, SwipeAction.SELECT_CHARACTERS_PRECISELY -> {
                    if (abs(event.relUnitCountX) > 0) {
                        inputFeedbackController?.gestureMovingSwipe(TextKeyData.DELETE)
                    }
                    val activeSelection = editorInstance.activeContent.selection
                    if (activeSelection.isValid) {
                        if (!inputEventDispatcher.isPressed(KeyCode.SHIFT)) {
                            // Backward select
                            editorInstance.setSelectionSurrounding(
                                n = -event.absUnitCountX - 1,
                                unit = OperationUnit.CHARACTERS,
                                scope = OperationScope.BEFORE_CURSOR,
                            )
                        } else {
                            // Forward select
                            editorInstance.setSelectionSurrounding(
                                n = -event.absUnitCountX - 1,
                                unit = OperationUnit.CHARACTERS,
                                scope = OperationScope.AFTER_CURSOR,
                            )
                        }
                    }
                    true
                }
                SwipeAction.DELETE_WORDS_PRECISELY, SwipeAction.SELECT_WORDS_PRECISELY -> {
                    if (abs(event.relUnitCountX) > 0) {
                        inputFeedbackController?.gestureMovingSwipe(TextKeyData.DELETE)
                    }
                    val activeSelection = editorInstance.activeContent.selection
                    if (activeSelection.isValid) {
                        if (!inputEventDispatcher.isPressed(KeyCode.SHIFT)) {
                            // Backward select
                            editorInstance.setSelectionSurrounding(
                                n = -event.absUnitCountX / 2 - 1,
                                unit = OperationUnit.WORDS,
                                scope = OperationScope.BEFORE_CURSOR,
                            )
                        } else {
                            // Forward select
                            editorInstance.setSelectionSurrounding(
                                n = -event.absUnitCountX / 2 - 1,
                                unit = OperationUnit.WORDS,
                                scope = OperationScope.AFTER_CURSOR,
                            )
                        }
                    }
                    true
                }
                else -> false
            }
            SwipeGesture.Type.TOUCH_UP -> {
                if (event.direction == SwipeGesture.Direction.LEFT &&
                    prefs.gestures.deleteKeySwipeLeft.get() == SwipeAction.DELETE_WORD
                ) {
                    keyboardManager.executeSwipeAction(prefs.gestures.deleteKeySwipeLeft.get())
                    true
                } else {
                    false
                }
            }
        }
    }

    private fun handleSpaceSwipe(event: SwipeGesture.Event): Boolean {
        val pointer = pointerMap.findById(event.pointerId) ?: return false

        return when (event.type) {
            SwipeGesture.Type.TOUCH_MOVE -> when (event.direction) {
                SwipeGesture.Direction.LEFT -> {
                    val action = prefs.gestures.spaceBarSwipeLeft.get()
                    if (action == SwipeAction.MOVE_CURSOR_LEFT) {
                        abs(event.relUnitCountX).let {
                            val count = if (!pointer.hasTriggeredGestureMove) it - 1 else it
                            if (count > 0) {
                                inputFeedbackController?.gestureMovingSwipe(TextKeyData.SPACE)
                                if (!pointer.hasTriggeredMassSelection) {
                                    pointer.hasTriggeredMassSelection = true
                                    editorInstance.massSelection.begin()
                                }
                                keyboardManager.handleArrow(KeyCode.ARROW_LEFT, count)
                            }
                        }
                        true
                    } else {
                        action != SwipeAction.NO_ACTION
                    }
                }
                SwipeGesture.Direction.RIGHT -> {
                    val action = prefs.gestures.spaceBarSwipeRight.get()
                    if (action == SwipeAction.MOVE_CURSOR_RIGHT) {
                        abs(event.relUnitCountX).let {
                            val count = if (!pointer.hasTriggeredGestureMove) it - 1 else it
                            if (count > 0) {
                                inputFeedbackController?.gestureMovingSwipe(TextKeyData.SPACE)
                                if (!pointer.hasTriggeredMassSelection) {
                                    pointer.hasTriggeredMassSelection = true
                                    editorInstance.massSelection.begin()
                                }
                                keyboardManager.handleArrow(KeyCode.ARROW_RIGHT, count)
                            }
                        }
                        true
                    } else {
                        action != SwipeAction.NO_ACTION
                    }
                }
                else -> false
            }
            SwipeGesture.Type.TOUCH_UP -> when (event.direction) {
                SwipeGesture.Direction.LEFT -> {
                    prefs.gestures.spaceBarSwipeLeft.get().let {
                        when {
                            it == SwipeAction.NO_ACTION -> {
                                false
                            }
                            it != SwipeAction.MOVE_CURSOR_LEFT -> {
                                keyboardManager.executeSwipeAction(it)
                                true
                            }
                            else -> {
                                false
                            }
                        }
                    }
                }
                SwipeGesture.Direction.RIGHT -> {
                    prefs.gestures.spaceBarSwipeRight.get().let {
                        when {
                            it == SwipeAction.NO_ACTION -> {
                                false
                            }
                            it != SwipeAction.MOVE_CURSOR_RIGHT -> {
                                keyboardManager.executeSwipeAction(it)
                                true
                            }
                            else -> {
                                false
                            }
                        }
                    }
                }
                else -> {
                    if (event.absUnitCountY < -6) {
                        keyboardManager.executeSwipeAction(prefs.gestures.spaceBarSwipeUp.get())
                        true
                    } else {
                        false
                    }
                }
            }
        }
    }

    override fun onGlideAddPoint(point: GlideTypingGesture.Detector.Position) {
        if (isGlideEnabled) {
            glideDataForDrawing.add(point to System.currentTimeMillis())
        }
    }

    override fun onGlideComplete(data: GlideTypingGesture.Detector.PointerData) {
        onGlideCancelled()
    }

    override fun onGlideCancelled() {
        if (prefs.glide.showTrail.get()) {
            fadingGlide.clear()
            fadingGlide.addAll(glideDataForDrawing)

            val animator = ValueAnimator.ofFloat(1.0f, 0.0f)
            animator.interpolator = AccelerateInterpolator()
            // DRS v2.4.0: the trail fade respects the system rhythm too —
            // when animations are removed system-wide the fade collapses
            // to a 0ms jump to the same final target (fraction 0, trail
            // gone); the state machine below is untouched either way.
            val motionEnabled = try {
                Settings.Global.getFloat(
                    context.contentResolver,
                    Settings.Global.ANIMATOR_DURATION_SCALE,
                    1f,
                ) != 0f
            } catch (_: Throwable) {
                true
            }
            animator.duration = DrsMotion.durationOrSnap(
                prefs.glide.trailDuration.get(),
                motionEnabled,
            ).toLong()
            animator.addUpdateListener {
                fadingGlideFraction = it.animatedValue as Float
            }
            animator.start()
        }
        // DRS v1.20.0: the buffer clear and the state reset previously lived INSIDE the
        // showTrail gate, so with the trail disabled every gesture kept accumulating
        // points in glideDataForDrawing for the whole process lifetime and isGliding
        // stayed true forever after the first glide. The fading animation is cosmetic
        // and stays gated; the state machine must not be.
        glideDataForDrawing.clear()
        isGliding = false
    }

    fun drawGlideTrail(
        drawScope: ContentDrawScope,
        gestureData: MutableList<Pair<GlideTypingGesture.Detector.Position, Long>>,
        targetDist: Float,
        initialRadius: Float,
        radiusReductionFactor: Float,
        color: Color,
    ) {
        var radius = initialRadius
        var drawnPoints = 0
        var prevX = gestureData.lastOrNull()?.first?.x ?: 0.0f
        var prevY = gestureData.lastOrNull()?.first?.y ?: 0.0f
        val time = System.currentTimeMillis()

        outer@ for (i in gestureData.size - 1 downTo 1) {
            if (time - gestureData[i - 1].second > prefs.glide.trailDuration.get()) break

            val dx = prevX - gestureData[i - 1].first.x
            val dy = prevY - gestureData[i - 1].first.y
            val dist = sqrt(dx * dx + dy * dy)

            val numPoints = (dist / targetDist).toInt()
            for (j in 0 until numPoints) {
                radius *= radiusReductionFactor
                val intermediateX =
                    gestureData[i].first.x * (1 - j.toFloat() / numPoints) + gestureData[i - 1].first.x * (j.toFloat() / numPoints)
                val intermediateY =
                    gestureData[i].first.y * (1 - j.toFloat() / numPoints) + gestureData[i - 1].first.y * (j.toFloat() / numPoints)
                drawScope.drawCircle(color, radius, center = Offset(intermediateX, intermediateY))
                drawnPoints += 1
                prevX = intermediateX
                prevY = intermediateY
            }
        }
    }

    private class TouchPointer : Pointer() {
        var initialKey: TextKey? = null
        var activeKey: TextKey? = null
        var hasTriggeredGestureMove: Boolean = false
        var hasTriggeredLongPress: Boolean = false
        var hasTriggeredMassSelection: Boolean = false
        var pressedKeyInfo: InputEventDispatcher.PressedKeyInfo? = null

        override fun reset() {
            super.reset()
            initialKey = null
            activeKey = null
            hasTriggeredGestureMove = false
            hasTriggeredLongPress = false
            hasTriggeredMassSelection = false
            pressedKeyInfo = null
        }

        override fun toString(): String {
            return "${TouchPointer::class.simpleName} { id=$id, index=$index, initialKey=$initialKey, activeKey=$activeKey }"
        }
    }
}
