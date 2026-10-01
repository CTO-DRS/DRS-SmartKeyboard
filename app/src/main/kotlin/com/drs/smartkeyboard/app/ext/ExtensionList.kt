/*
 * Copyright (C) 2021-2025 The DRS Smart Keyboard Project
 */

package com.drs.smartkeyboard.app.ext

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.drs.smartkeyboard.app.LocalNavController
import com.drs.smartkeyboard.app.Routes
import com.drs.smartkeyboard.lib.ext.Extension
import org.drs.jetpref.material.ui.JetPrefListItem

@Composable
fun <T : Extension> ExtensionList(
    extList: List<T>,
    modifier: Modifier = Modifier,
    summaryProvider: (T) -> String? = { null },
) {
    val navController = LocalNavController.current

    Column(
        modifier = modifier.fillMaxWidth(),
    ) {
        for (ext in extList) {
            JetPrefListItem(
                icon = { },
                modifier = Modifier
                    .clickable {
                        navController.navigate(Routes.Ext.View(ext.meta.id))
                    },
                text = ext.meta.title,
                secondaryText = summaryProvider(ext),
            )
        }
    }
}
