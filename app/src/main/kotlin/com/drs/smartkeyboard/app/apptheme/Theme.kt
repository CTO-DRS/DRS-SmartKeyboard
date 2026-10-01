/*
 * Copyright (C) 2021-2025 The DRS Smart Keyboard Project
 */

package com.drs.smartkeyboard.app.apptheme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.drs.smartkeyboard.app.AppTheme
import com.drs.smartkeyboard.app.DrsPreferenceStore
import org.drs.jetpref.datastore.model.collectAsState
import org.drs.lib.color.neutralDynamicColorScheme
import org.drs.lib.color.systemAccentOrDefault


@Composable
fun getColorScheme(
    theme: AppTheme,
): ColorScheme {
    val prefs by DrsPreferenceStore
    val accentColor by prefs.other.accentColor.collectAsState()

    val seedColor = systemAccentOrDefault(accentColor)

    return when (theme) {
        AppTheme.AUTO, AppTheme.AUTO_AMOLED -> {
            neutralDynamicColorScheme(
                primary = seedColor,
                isDark = isSystemInDarkTheme(),
                isAmoled = theme == AppTheme.AUTO_AMOLED,
            )
        }

        AppTheme.DARK, AppTheme.LIGHT -> {
            neutralDynamicColorScheme(primary = seedColor, isDark = theme == AppTheme.DARK)
        }

        AppTheme.AMOLED_DARK -> {
            neutralDynamicColorScheme(primary = seedColor, isDark = true, isAmoled = true)
        }
    }
}

fun ColorScheme.amoled(): ColorScheme {
    return this.copy(background = Color.Black, surface = Color.Black)
}

@Composable
fun DrsAppTheme(
    theme: AppTheme,
    content: @Composable () -> Unit,
) {
    val colors = getColorScheme(theme = theme)

    val darkTheme =
        theme == AppTheme.DARK
            || theme == AppTheme.AMOLED_DARK
            || (theme == AppTheme.AUTO && isSystemInDarkTheme())
            || (theme == AppTheme.AUTO_AMOLED && isSystemInDarkTheme())

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            // DRS fix: unwrap + safe cast so non-activity contexts never crash.
            var unwrappedContext = view.context
            while (unwrappedContext is android.content.ContextWrapper && unwrappedContext !is Activity) {
                unwrappedContext = unwrappedContext.baseContext
            }
            val window = (unwrappedContext as? Activity)?.window ?: return@SideEffect
            WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colors,
        typography = Typography,
        content = content,
    )
}
