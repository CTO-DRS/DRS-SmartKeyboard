/*
 * Copyright (C) 2021-2025 The DRS Smart Keyboard Project
 */

package com.drs.smartkeyboard.lib.compose

import androidx.compose.foundation.clickable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import com.drs.smartkeyboard.lib.util.launchUrl

@Composable
fun DrsHyperlinkText(
    text: String,
    url: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    color: Color = MaterialTheme.colorScheme.primary,
) {
    val context = LocalContext.current

    Text(
        modifier = modifier
            .clickable(enabled = enabled) {
                context.launchUrl(url)
            },
        text = text,
        color = color,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}
