/*
 * Copyright (C) 2025-2026 The DRS Smart Keyboard Project
 */

package com.drs.smartkeyboard.ime.window

import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.systemGestureExclusion
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.coerceAtLeast
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.roundToIntRect
import com.drs.smartkeyboard.R
import com.drs.smartkeyboard.app.devtools.DevtoolsOverlay
import com.drs.smartkeyboard.ime.ImeUiMode
import com.drs.smartkeyboard.ime.clipboard.ClipboardInputLayout
import com.drs.smartkeyboard.drs.ui.DrsTextToolsPanel
import com.drs.smartkeyboard.drs.ui.DrsDiacriticsPanel
import com.drs.smartkeyboard.drs.ui.DrsSmartSymbolsPanel
import com.drs.smartkeyboard.drs.ui.DrsArabicLettersPanel
import com.drs.smartkeyboard.drs.ui.DrsSmartNumberPanel
import com.drs.smartkeyboard.drs.ui.DrsSmartClipboardPanel
import com.drs.smartkeyboard.ime.input.LocalInputFeedbackController
import com.drs.smartkeyboard.ime.keyboard.ProvideKeyboardRowBaseHeight
import com.drs.smartkeyboard.ime.media.MediaInputLayout
import com.drs.smartkeyboard.ime.sheet.BottomSheetWindow
import com.drs.smartkeyboard.ime.text.TextInputLayout
import com.drs.smartkeyboard.ime.theme.DrsImeUi
import com.drs.smartkeyboard.ime.theme.DrsThemeBackground
import com.drs.smartkeyboard.app.DrsPreferenceStore
import com.drs.smartkeyboard.keyboardManager
import kotlinx.coroutines.delay
import org.drs.jetpref.datastore.model.collectAsState as jetprefCollectAsState
import org.drs.lib.compose.ProvideActualLayoutDirection
import org.drs.lib.compose.conditional
import org.drs.lib.compose.drawBorder
import org.drs.lib.compose.drawableRes
import org.drs.lib.compose.fold
import org.drs.lib.compose.ifIsInstance
import org.drs.lib.compose.stringRes
import org.drs.lib.snygg.ui.SnyggBox
import org.drs.lib.snygg.ui.SnyggIcon
import org.drs.lib.snygg.ui.SnyggIconButton
import org.drs.lib.snygg.ui.rememberSnyggThemeQuery

/**
 * The main entry point of the IME user interface. This includes the keyboard itself, devtools overlays,
 * bottom sheets, and system bars management.
 *
 * Typically, the root window corresponds to the screen bounds, however this is not guaranteed. For
 * consistency reasons, all sub sizes and positions should be derived from the root window.
 *
 * The size and position of this composable are used to calculate [ImeWindowController.activeRootInsets].
 *
 * @see ImeWindow
 * @see BottomSheetWindow
 * @see DevtoolsOverlay
 */
@Composable
fun ImeRootWindow() {
    val density = LocalDensity.current
    val windowController = LocalWindowController.current

    val editorState by windowController.editor.state.collectAsState()
    val isEditorEnabled by remember {
        derivedStateOf {
            editorState.isEnabled
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .conditional(isEditorEnabled) {
                Modifier.pointerInput(isEditorEnabled) {
                    detectTapGestures {
                        windowController.editor.disableIfNoGestureInProgress()
                    }
                }
            }
            .onGloballyPositioned { coords ->
                val boundsPx = coords.boundsInRoot().roundToIntRect()
                val newInsets = with(density) { ImeInsets.Root.of(boundsPx) }
                windowController.updateRootInsets(newInsets)
            },
    ) {
        DevtoolsOverlay()
        ImeWindow()
        BottomSheetWindow()
        ImeSystemUi()
    }
}

/**
 * The IME window contains all components that users would describe as the keyboard user interface. Its placement
 * and size is dependent on the window config, with the main factor being the window mode.
 *
 * Most of the time all draw operations are contained within the bounds of this composable. Exceptions to
 * this may be resize handles, popups, tooltips, and any other composable positioned absolutely.
 *
 * The size and position of this composable are used to calculate [ImeWindowController.activeWindowInsets].
 *
 * @see ImeWindowMode
 */
@Composable
fun BoxScope.ImeWindow() {
    val density = LocalDensity.current
    val windowController = LocalWindowController.current

    // DRS v2.8.0 «خلفيتك من ألبومك»: readability scrim strength for the user
    // background image. Injected at the composition level exactly like the
    // dynamic accent color — outside the stylesheet contract.
    val prefs by DrsPreferenceStore
    val backgroundDimness by prefs.theme.backgroundDimness.jetprefCollectAsState()

    val windowSpec by windowController.activeWindowSpec.collectAsState()
    val windowConfig by windowController.activeWindowConfig.collectAsState()

    val attributes = remember(windowConfig.mode) {
        mapOf(
            DrsImeUi.Attr.WindowMode to windowConfig.mode.toString(),
        )
    }

    FloatingDockToFixedIndicator()

    SnyggBox(
        elementName = DrsImeUi.Window.elementName,
        attributes = attributes,
        modifier = Modifier
            .align(Alignment.BottomStart)
            .ifIsInstance<ImeWindowProps.Fixed>(windowSpec.props) {
                Modifier
                    .fillMaxWidth()
            }
            .ifIsInstance<ImeWindowProps.Floating>(windowSpec.props) { props ->
                Modifier
                    .offset(props.offsetLeft, -props.offsetBottom)
                    .width(props.keyboardWidth)
            }
            .wrapContentHeight()
            .onGloballyPositioned { coords ->
                val boundsPx = coords.boundsInRoot().roundToIntRect()
                val newInsets = with(density) { ImeInsets.Window.of(boundsPx) }
                windowController.updateWindowInsets(newInsets)
            },
        supportsBackgroundImage = true,
        backgroundImageDim = DrsThemeBackground.dimnessToAlpha(backgroundDimness),
        allowClip = false,
    ) {
        OneHandedPanel()
        ProvideKeyboardRowBaseHeight {
            ImeInnerWindow()
        }
        ImeWindowResizeHandlesFloating()
    }
}

@Composable
private fun ImeInnerWindow() {
    val context = LocalContext.current
    val windowController = LocalWindowController.current

    val keyboardManager by context.keyboardManager()

    val state by keyboardManager.activeState.collectAsState()
    val windowSpec by windowController.activeWindowSpec.collectAsState()

    ProvideActualLayoutDirection {
        val layoutDirection = LocalLayoutDirection.current
        LaunchedEffect(layoutDirection) {
            keyboardManager.activeState.layoutDirection = layoutDirection
        }
    }

    SnyggBox(
        elementName = DrsImeUi.WindowInner.elementName,
        modifier = Modifier
            .fillMaxWidth()
            .wrapContentHeight()
            .ifIsInstance<ImeWindowProps.Fixed>(windowSpec.props) { props ->
                Modifier
                    .safeDrawingPadding()
                    .systemGestureExclusion()
                    .padding(
                        start = props.paddingLeft.coerceAtLeast(0.dp),
                        end = props.paddingRight.coerceAtLeast(0.dp),
                        bottom = props.paddingBottom.coerceAtLeast(0.dp),
                    )
            }
            .ifIsInstance<ImeWindowProps.Floating>(windowSpec.props) {
                Modifier.systemGestureExclusion()
            },
        allowClip = false,
    ) {
        Column {
            when (state.imeUiMode) {
                ImeUiMode.TEXT -> TextInputLayout()
                ImeUiMode.MEDIA -> ProvideActualLayoutDirection { MediaInputLayout() }
                ImeUiMode.CLIPBOARD -> ProvideActualLayoutDirection { ClipboardInputLayout() }
                ImeUiMode.TEXT_TOOLS -> ProvideActualLayoutDirection { DrsTextToolsPanel() }
                // DRS v1.15.0: the three smart panels.
                ImeUiMode.DIACRITICS -> ProvideActualLayoutDirection { DrsDiacriticsPanel() }
                ImeUiMode.SMART_SYMBOLS -> ProvideActualLayoutDirection { DrsSmartSymbolsPanel() }
                ImeUiMode.ARABIC_LETTERS -> ProvideActualLayoutDirection { DrsArabicLettersPanel() }
                // DRS v1.2.0: the fourth smart panel.
                ImeUiMode.SMART_NUMBER -> ProvideActualLayoutDirection { DrsSmartNumberPanel() }
                // DRS v1.3.0: the fifth smart panel.
                ImeUiMode.SMART_CLIPBOARD -> ProvideActualLayoutDirection { DrsSmartClipboardPanel() }
            }
            ImeSystemUiFloating()
        }
        ImeWindowResizeHandlesFixed()
    }
}

@Composable
private fun BoxScope.FloatingDockToFixedIndicator() {
    val inputFeedbackController = LocalInputFeedbackController.current
    val windowController = LocalWindowController.current

    val windowSpec by windowController.activeWindowSpec.collectAsState()
    val editorState by windowController.editor.state.collectAsState()

    val visible by remember {
        derivedStateOf {
            windowSpec.let { spec ->
                editorState.isMoveGesture && spec is ImeWindowSpec.Floating &&
                    spec.props.offsetBottom <= spec.constraints.dockToFixedHeight
            }
        }
    }

    val transition = updateTransition(
        targetState = visible,
        label = "FloatingDockToFixedIndicator_visibility",
    )
    val animatedAlpha by transition.animateFloat(label = "alpha") { visible ->
        if (visible) 1f else 0f
    }
    val animatedHeightRatio by transition.animateFloat(label = "height") { visible ->
        if (visible) 1f else 0f
    }
    val indicatorTheme = rememberSnyggThemeQuery(DrsImeUi.FloatingDockToFixedIndicator.elementName)
    val color = indicatorTheme.background(default = Color.Gray)

    LaunchedEffect(visible) {
        if (visible) {
            delay(150)
            inputFeedbackController.keyPress()
        }
    }

    Box(
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .fillMaxWidth()
            .height(windowSpec.constraints.dockToFixedHeight)
            .navigationBarsPadding()
            .graphicsLayer { alpha = animatedAlpha }
            .drawBehind {
                val animatedTopLeft = Offset(
                    x = 0f,
                    y = size.height * (1f - animatedHeightRatio),
                )
                drawRect(
                    color = color,
                    topLeft = animatedTopLeft,
                    alpha = 0.5f,
                )
                drawBorder(
                    color = color,
                    topLeft = animatedTopLeft,
                    stroke = Stroke(windowSpec.constraints.dockToFixedBorder.toPx()),
                )
            },
    )
}

@Composable
private fun BoxScope.OneHandedPanel() {
    val windowController = LocalWindowController.current
    val windowSpec by windowController.activeWindowSpec.collectAsState()

    when (val spec = windowSpec) {
        is ImeWindowSpec.Fixed if spec.fixedMode == ImeWindowMode.Fixed.COMPACT -> {
            OneHandedPanel(spec)
        }
        else -> { }
    }
}

@Composable
private fun BoxScope.OneHandedPanel(spec: ImeWindowSpec.Fixed) {
    val windowController = LocalWindowController.current
    val editorState by windowController.editor.state.collectAsState()
    val windowConfig by windowController.activeWindowConfig.collectAsState()

    val attributes = remember(windowConfig.mode) {
        mapOf(
            DrsImeUi.Attr.WindowMode to windowConfig.mode.toString()
        )
    }

    if (!editorState.isEnabled) {
        Box(Modifier.matchParentSize()) {
            Column(
                modifier = Modifier
                    .fillMaxHeight()
                    .safeDrawingPadding()
                    .padding(bottom = spec.props.paddingBottom.coerceAtLeast(0.dp))
                    .fold(
                        condition = spec.props.paddingLeft >= spec.props.paddingRight,
                        ifTrue = {
                            Modifier
                                .width(spec.props.paddingLeft)
                                .align(Alignment.CenterStart)
                        },
                        ifFalse = {
                            Modifier
                                .width(spec.props.paddingRight)
                                .align(Alignment.CenterEnd)
                        }
                    ),
                verticalArrangement = Arrangement.SpaceAround,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // DRS a11y/i18n/ux (r0-F): the one-handed panel buttons were unlabeled — TalkBack announced
                // nothing. Labels reuse the legacy one_handed__* keys (grep-verified present in values/ +
                // values-en/) and the quick-action resize label.
                SnyggIconButton(
                    elementName = DrsImeUi.OneHandedPanelButton.elementName,
                    attributes = attributes,
                    contentDescription = stringRes(R.string.one_handed__close_btn_content_description),
                    onClick = {
                        windowController.actions.toggleCompactLayout()
                    },
                ) {
                    SnyggIcon(
                        attributes = attributes,
                        imageVector = drawableRes(R.drawable.ic_zoom_out_map),
                    )
                }
                SnyggIconButton(
                    elementName = DrsImeUi.OneHandedPanelButton.elementName,
                    attributes = attributes,
                    // The chevron points to the side the keyboard will move to — announce it accordingly.
                    contentDescription = stringRes(
                        if (spec.props.paddingLeft > spec.props.paddingRight) {
                            R.string.one_handed__move_start_btn_content_description
                        } else {
                            R.string.one_handed__move_end_btn_content_description
                        },
                    ),
                    onClick = {
                        windowController.actions.compactLayoutFlipSide()
                    },
                ) {
                    if (spec.props.paddingLeft > spec.props.paddingRight) {
                        SnyggIcon(
                            imageVector = drawableRes(R.drawable.ic_chevron_left),
                            attributes = attributes,
                        )
                    } else {
                        SnyggIcon(
                            attributes = attributes,
                            imageVector = drawableRes(R.drawable.ic_chevron_right),
                        )
                    }
                }
                SnyggIconButton(
                    elementName = DrsImeUi.OneHandedPanelButton.elementName,
                    attributes = attributes,
                    contentDescription = stringRes(R.string.quick_action__resize_mode),
                    onClick = {
                        windowController.editor.toggleEnabled()
                    },
                ) {
                    SnyggIcon(
                        imageVector = drawableRes(R.drawable.ic_resize),
                        attributes = attributes,
                    )
                }
            }
        }
    }
}
