/*
 * Copyright (C) 2021-2025 The DRS Smart Keyboard Project
 */

package com.drs.smartkeyboard.app.ext

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.drs.smartkeyboard.R
import com.drs.smartkeyboard.lib.compose.DrsScreen
import org.drs.lib.compose.stringRes

@Composable
internal fun ExtensionNotFoundScreen(id: String) = DrsScreen {
    title = stringRes(R.string.ext__error__not_found_title)

    content {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp),
        ) {
            Text(stringRes(R.string.ext__error__not_found_description, "id" to id))
        }
    }
}
