/*
 * Copyright (C) 2021-2025 The DRS Smart Keyboard Project
 */

package org.drs.lib.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp


object DrsCardDefaults {
    val IconRequiredSize = 24.dp
    val IconSpacing = 12.dp

    val ContentPadding = PaddingValues(start = 0.dp, end = 16.dp, top = 8.dp, bottom = 8.dp)
}

object BoxDefaults {
    val OutlinedBoxShape = RoundedCornerShape(16.dp)

    val ContentPadding = PaddingValues(all = 0.dp)
}

@Composable
fun DrsSimpleCard(
    modifier: Modifier = Modifier,
    text: String,
    secondaryText: String? = null,
    backgroundColor: Color = MaterialTheme.colorScheme.surfaceContainer,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    contentPadding: PaddingValues = DrsCardDefaults.ContentPadding,
    icon: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    Card(
        onClick = onClick ?: { },
        enabled = onClick != null,
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            contentColor = contentColor,
            containerColor = backgroundColor,
            disabledContainerColor = backgroundColor,
            disabledContentColor = contentColor,
        )
        //backgroundColor = backgroundColor,
        //contentColor = contentColor,
    ) {
        Row(
            modifier = Modifier.padding(contentPadding),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) {
                icon()
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = if (icon == null) 16.dp else 0.dp),
            ) {
                Text(
                    modifier = Modifier.fillMaxWidth(),
                    text = text,
                    style = MaterialTheme.typography.titleMedium,
                )
                if (secondaryText != null) {
                    Text(
                        modifier = Modifier.fillMaxWidth(),
                        text = secondaryText,
                        style = MaterialTheme.typography.titleSmall,
                    )
                }
            }
        }
    }
}

@Composable
fun DrsErrorCard(
    text: String,
    modifier: Modifier = Modifier,
    showIcon: Boolean = true,
    contentPadding: PaddingValues = DrsCardDefaults.ContentPadding,
    onClick: (() -> Unit)? = null,
) {
    // DRS: tonal error styling straight from the theme instead of a raw red
    // block — a calm, accessible error surface that harmonizes with the
    // dynamic palette in both light and dark modes.
    DrsSimpleCard(
        modifier = modifier,
        backgroundColor = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        onClick = onClick,
        icon = if (showIcon) ({ Icon(
            modifier = Modifier
                .padding(all = DrsCardDefaults.IconSpacing)
                .requiredSize(DrsCardDefaults.IconRequiredSize),
            imageVector = Icons.Default.ErrorOutline,
            contentDescription = null,
        ) }) else null,
        text = text,
        contentPadding = contentPadding,
    )
}

@Composable
fun DrsWarningCard(
    text: String,
    modifier: Modifier = Modifier,
    showIcon: Boolean = true,
    contentPadding: PaddingValues = DrsCardDefaults.ContentPadding,
    onClick: (() -> Unit)? = null,
) {
    // DRS: adaptive tonal amber warning — soft in light mode, deep honey in
    // dark mode, replacing the harsh raw yellow block.
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val amberContainer = if (isDark) Color(0xFF43380A) else Color(0xFFFFF0B3)
    val amberOnContainer = if (isDark) Color(0xFFFFE082) else Color(0xFF43310A)
    DrsSimpleCard(
        modifier = modifier,
        backgroundColor = amberContainer,
        contentColor = amberOnContainer,
        onClick = onClick,
        icon = if (showIcon) ({ Icon(
            modifier = Modifier
                .padding(all = DrsCardDefaults.IconSpacing)
                .requiredSize(DrsCardDefaults.IconRequiredSize),
            imageVector = Icons.Outlined.Warning,
            contentDescription = null,
        ) }) else null,
        text = text,
        contentPadding = contentPadding,
    )
}

@Composable
fun DrsInfoCard(
    text: String,
    modifier: Modifier = Modifier,
    showIcon: Boolean = true,
    contentPadding: PaddingValues = DrsCardDefaults.ContentPadding,
    onClick: (() -> Unit)? = null,
) {
    DrsSimpleCard(
        modifier = modifier,
        onClick = onClick,
        icon = if (showIcon) ({ Icon(
            modifier = Modifier
                .padding(all = DrsCardDefaults.IconSpacing)
                .requiredSize(DrsCardDefaults.IconRequiredSize),
            imageVector = Icons.Default.Info,
            contentDescription = null,
        ) }) else null,
        text = text,
        contentPadding = contentPadding,
    )
}

@Composable
fun DrsOutlinedBox(
    modifier: Modifier = Modifier,
    title: String,
    onTitleClick: (() -> Unit)? = null,
    subtitle: String? = null,
    onSubtitleClick: (() -> Unit)? = null,
    borderWidth: Dp = 1.dp,
    borderColor: Color = MaterialTheme.colorScheme.outlineVariant,
    shape: Shape = BoxDefaults.OutlinedBoxShape,
    contentPadding: PaddingValues = BoxDefaults.ContentPadding,
    content: @Composable ColumnScope.() -> Unit,
) {
    DrsOutlinedBox(
        modifier = modifier,
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        onTitleClick = onTitleClick,
        subtitle = if (subtitle != null) {
            {
                Text(
                    modifier = Modifier
                        .padding(start = 6.dp, end = 6.dp, bottom = 4.dp),
                    text = subtitle,
                    color = LocalContentColor.current.copy(alpha = 0.56f),
                    fontWeight = FontWeight.Normal,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                )
            }
        } else {
            null
        },
        onSubtitleClick = onSubtitleClick,
        borderWidth = borderWidth,
        borderColor = borderColor,
        shape = shape,
        contentPadding = contentPadding,
        content = content,
    )
}

// TODO: Rework internal implementation (with same API and visual appearance) of DrsOutlinedBox
//  to avoid too much nesting and improve performance
@Composable
fun DrsOutlinedBox(
    modifier: Modifier = Modifier,
    title: (@Composable () -> Unit)? = null,
    onTitleClick: (() -> Unit)? = null,
    subtitle: (@Composable () -> Unit)? = null,
    onSubtitleClick: (() -> Unit)? = null,
    borderWidth: Dp = 1.dp,
    borderColor: Color = MaterialTheme.colorScheme.outlineVariant,
    shape: Shape = BoxDefaults.OutlinedBoxShape,
    contentPadding: PaddingValues = BoxDefaults.ContentPadding,
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(
        modifier = modifier
            .padding(top = if (title != null) 11.dp else 0.dp),
    ) {
        Column(
            modifier = Modifier
                .border(borderWidth, borderColor, shape)
                .clip(shape)
                .padding(top = if (title != null) 11.dp else 0.dp),
        ) {
            if (title != null && subtitle != null) {
                Box(
                    modifier = Modifier
                        .padding(start = 10.dp, bottom = 4.dp)
                        .rippleClickable(enabled = onSubtitleClick != null) {
                            onSubtitleClick!!()
                        },
                ) {
                    subtitle()
                }
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(contentPadding),
                content = content,
            )
        }
        if (title != null) {
            Box(
                modifier = Modifier
                    .height(23.dp)
                    .offset(x = 10.dp, y = (-12).dp)
                    .background(MaterialTheme.colorScheme.background)
                    .rippleClickable(enabled = onTitleClick != null) {
                        onTitleClick!!()
                    }
                    .padding(horizontal = 6.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                title()
            }
        }
    }
}

fun Modifier.defaultDrsOutlinedBox(): Modifier {
    return this
        .fillMaxWidth()
        .padding(vertical = 8.dp, horizontal = 16.dp)
}
