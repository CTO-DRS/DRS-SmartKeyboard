/*
 * Copyright (C) 2021-2025 The DRS Smart Keyboard Project
 */

package com.drs.smartkeyboard.app.ext

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.drs.lib.compose.DrsChip

@Composable
fun ExtensionKeywordChip(
    keyword: String,
    modifier: Modifier = Modifier,
) {
    DrsChip(
        modifier = modifier,
        text = keyword,
        enabled = false,
        shape = RoundedCornerShape(4.dp),
    )
}
