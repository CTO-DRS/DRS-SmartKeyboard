/*
 * Copyright (C) 2021-2025 The DRS Smart Keyboard Project
 */

package org.drs.lib.compose

import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * DRS modern top app bar: transparent surface with a very subtle vertical
 * hero gradient, a bold title and a dynamic hairline divider that fades in
 * only while the content scrolls — a clean, premium, world-class look that
 * also adapts to light/dark themes automatically.
 *
 * The hairline is drawn inside the bar itself (instead of a sibling row) so
 * that it collapses together with the bar's shrinking height when the bar
 * scrolls away, never leaving an orphaned line behind.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DrsAppBar(
    title: String,
    navigationIcon: (@Composable () -> Unit)?,
    actions: @Composable RowScope.() -> Unit = { },
    scrollBehavior: TopAppBarScrollBehavior
) {
    val colorScheme = MaterialTheme.colorScheme
    val collapsedFraction = scrollBehavior.state.collapsedFraction
    val dividerAlpha = (collapsedFraction * 0.5f).coerceIn(0f, 0.5f)
    val heroGradient = Brush.verticalGradient(
        0f to colorScheme.primary.copy(alpha = 0.06f),
        1f to Color.Transparent,
    )

    TopAppBar(
        navigationIcon = navigationIcon ?: {},
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = colorScheme.onSurface,
                overflow = TextOverflow.Ellipsis,
                maxLines = 1,
            )
        },
        actions = actions,
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = Color.Transparent,
            scrolledContainerColor = Color.Transparent,
        ),
        scrollBehavior = scrollBehavior,
        modifier = Modifier.drawBehind {
            drawRect(brush = heroGradient)
            // Dynamic hairline pinned to the bar's live bottom edge — it
            // follows the collapsing height and disappears with the bar.
            val hairline = 1.dp.toPx()
            drawRect(
                color = colorScheme.outlineVariant.copy(alpha = dividerAlpha),
                topLeft = Offset(0f, size.height - hairline),
                size = Size(size.width, hairline),
            )
        },
    )
}
