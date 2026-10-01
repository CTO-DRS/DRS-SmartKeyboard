/*
 * Copyright (C) 2025-2026 The DRS Smart Keyboard Project
 */

package com.drs.smartkeyboard.ime.window

import android.annotation.SuppressLint
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.AbstractComposeView
import androidx.compose.ui.unit.LayoutDirection
import com.drs.smartkeyboard.DrsImeService
import com.drs.smartkeyboard.R
import com.drs.smartkeyboard.ime.input.LocalInputFeedbackController
import com.drs.smartkeyboard.ime.theme.DrsImeTheme
import org.drs.lib.compose.ProvideLocalizedResources

/**
 * Provides the [ImeWindowController] instance this composition tree is associated with.
 */
val LocalWindowController = staticCompositionLocalOf<ImeWindowController> {
    error("This composition local provider is only available within an IME view")
}

/**
 * The main entry point and bridge between the IME dialog view and the composables. It will fill the maximum area
 * available within the accompanying dialog view, and also draw under system bars.
 *
 * The layout direction will be forced to [LayoutDirection.Ltr], to ensure the window positioning logic's left/right
 * corresponds to the physical left/right. For UI components that need to conform to the actual system layout
 * direction, the UI components should be wrapped with [org.drs.lib.compose.ProvideActualLayoutDirection].
 *
 * @see ImeRootWindow
 */
@SuppressLint("ViewConstructor")
class ImeRootView(val ims: DrsImeService) : AbstractComposeView(ims) {
    init {
        isHapticFeedbackEnabled = true
        layoutParams = LayoutParams(
            /* width = */ LayoutParams.MATCH_PARENT,
            /* height = */ LayoutParams.MATCH_PARENT,
        )
    }

    @Composable
    override fun Content() {
        CompositionLocalProvider(
            LocalInputFeedbackController provides ims.inputFeedbackController,
            LocalWindowController provides ims.windowController,
        ) {
            ProvideLocalizedResources(
                resourcesContext = ims.resourcesContext,
                appName = R.string.app_name,
                forceLayoutDirection = LayoutDirection.Ltr,
            ) {
                DrsImeTheme {
                    ImeRootWindow()
                }
            }
        }
    }

    override fun getAccessibilityClassName(): CharSequence? {
        return this::class.simpleName
    }
}
