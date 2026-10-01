/*
 * Copyright (C) 2021-2025 The DRS Smart Keyboard Project
 */

package org.drs.lib.compose

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import org.drs.jetpref.material.ui.JetPrefDropdownMenuDefaults
import org.drs.jetpref.material.ui.JetPrefTextField
import org.drs.jetpref.material.ui.JetPrefTextFieldAppearance


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DrsDropdownLikeButton(
    item: String,
    modifier: Modifier = Modifier,
    isError: Boolean = false,
    onClick: () -> Unit = { },
    appearance: JetPrefTextFieldAppearance = JetPrefDropdownMenuDefaults.filled(),
) {
    Box(
        modifier = modifier.wrapContentSize(Alignment.TopStart)
    ) {
        val interactionSource = remember { MutableInteractionSource() }
        val pressed by interactionSource.collectIsPressedAsState()

        if (pressed) {
            onClick()
        }

        JetPrefTextField(
            modifier = Modifier.fillMaxWidth(),
            value = item,
            onValueChange = {},
            enabled = true,
            readOnly = true,
            isError = isError,
            singleLine = true,
            trailingIcon = {
                ExposedDropdownMenuDefaults.TrailingIcon(
                    expanded = true,
                    modifier = Modifier.rotate(90f), //Arrow to the right
                )
            },
            appearance = appearance,
            interactionSource = interactionSource,
        )
    }
}
