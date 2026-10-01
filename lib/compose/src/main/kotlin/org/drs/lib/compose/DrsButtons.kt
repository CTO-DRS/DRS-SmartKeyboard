/*
 * Copyright (C) 2021-2025 The DRS Smart Keyboard Project
 */

package org.drs.lib.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonColors
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp

@Composable
fun DrsButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    text: String,
    enabled: Boolean = true,
    shape: Shape = MaterialTheme.shapes.small,
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    colors: ButtonColors = ButtonDefaults.buttonColors(),
) {
    Button(
        modifier = modifier,
        enabled = enabled,
        shape = shape,
        colors = colors,
        contentPadding = contentPadding,
        onClick = onClick,
    ) {
        if (icon != null) {
            Icon(
                modifier = Modifier
                    .padding(end = ButtonDefaults.IconSpacing)
                    .size(ButtonDefaults.IconSize),
                imageVector = icon,
                contentDescription = null,
            )
        }
        Text(text = text)
    }
}

@Composable
fun DrsOutlinedButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    text: String,
    enabled: Boolean = true,
    shape: Shape = MaterialTheme.shapes.small,
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    colors: ButtonColors = ButtonDefaults.outlinedButtonColors(),
) {
    OutlinedButton(
        modifier = modifier,
        enabled = enabled,
        shape = shape,
        colors = colors,
        contentPadding = contentPadding,
        onClick = onClick,
    ) {
        if (icon != null) {
            Icon(
                modifier = Modifier
                    .padding(end = ButtonDefaults.IconSpacing)
                    .size(ButtonDefaults.IconSize),
                imageVector = icon,
                contentDescription = null,
            )
        }
        Text(text = text)
    }
}

@Composable
fun DrsTextButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    text: String,
    enabled: Boolean = true,
    shape: Shape = ButtonDefaults.shape,
    contentPadding: PaddingValues = ButtonDefaults.TextButtonContentPadding,
    colors: ButtonColors = ButtonDefaults.textButtonColors(),
) {
    TextButton(
        modifier = modifier,
        enabled = enabled,
        shape = shape,
        colors = colors,
        contentPadding = contentPadding,
        onClick = onClick,
    ) {
        if (icon != null) {
            Icon(
                modifier = Modifier
                    .padding(end = ButtonDefaults.IconSpacing)
                    .size(ButtonDefaults.IconSize),
                imageVector = icon,
                contentDescription = null,
            )
        }
        Text(text = text)
    }
}

@Composable
fun DrsIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector,
    enabled: Boolean = true,
    iconModifier: Modifier = Modifier,
    iconColor: Color = Color.Unspecified,
    // DRS v1.28.0 audit fix (High, a11y): the implicit contentDescription = null
    // made the settings back button (and every other DrsIconButton) invisible
    // to TalkBack — it announced nothing at all. Callers can now supply a
    // localized label; null stays valid for genuinely decorative icon buttons.
    contentDescription: String? = null,
) {
    IconButton(
        modifier = modifier,
        enabled = enabled,
        onClick = onClick,
    ) {
        // DRS a11y/i18n/ux (r0-I): 0.14 was nearly invisible (an unclear
        // state at best); 0.38 is the Material disabled-content alpha spec,
        // keeping the icon legible while still reading as "off".
        val contentAlpha = if (enabled) 1f else 0.38f
        val contentColor = iconColor.takeOrElse { LocalContentColor.current }.copy(alpha = contentAlpha)
        CompositionLocalProvider(
            LocalContentColor provides contentColor,
        ) {
            Icon(
                modifier = iconModifier,
                imageVector = icon,
                contentDescription = contentDescription,
            )
        }
    }
}

@Composable
fun DrsIconButton(
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: IconButtonColors = IconButtonDefaults.iconButtonColors(),
    interactionSource: MutableInteractionSource? = null,
    shape: Shape = IconButtonDefaults.standardShape,
    size: DpSize = DpSize(52.dp, 30.dp),
    // DRS a11y/i18n/ux (r0-F): the content-box overload could not express an accessibility label at
    // all (the Icon overload already takes one). The optional label is set as semantics on the
    // clickable Box so it merges with the child content semantics.
    contentDescription: String? = null,
    content: @Composable () -> Unit,
) {
    // This is a modified version of https://cs.android.com/androidx/platform/frameworks/support/+/androidx-main:compose/material3/material3/src/commonMain/kotlin/androidx/compose/material3/IconButton.kt;l=235-266;drc=e6d33dd5d0a60001a5784d84123b05308d35f410
    // adding longClick functionality
    val interactionSource = interactionSource ?: remember { MutableInteractionSource() }
    Box(
        modifier =
            modifier
                .size(size)
                .clip(shape)
                .background(
                    color = if (enabled) {
                        colors.containerColor
                    } else {
                        colors.disabledContainerColor
                    },
                    shape = shape
                )
                .semantics {
                    if (contentDescription != null) {
                        this.contentDescription = contentDescription
                    }
                }
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = onLongClick,
                    enabled = enabled,
                    role = Role.Button,
                    interactionSource = interactionSource,
                    indication = ripple(),
                ),
                //TODO: I don't know how we could emulate something like this
                //.childSemantics(),
        contentAlignment = Alignment.Center,
    ) {
        val contentColor = if (enabled) {
            colors.contentColor
        } else {
            colors.disabledContentColor
        }
        CompositionLocalProvider(LocalContentColor provides contentColor, content = content)
    }
}
