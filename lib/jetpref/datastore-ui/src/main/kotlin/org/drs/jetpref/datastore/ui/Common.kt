/*
 * Copyright (C) 2021-2026 The DRS Smart Keyboard Project
 */

package org.drs.jetpref.datastore.ui

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp


@Composable
internal fun maybeJetIcon(
    imageVector: ImageVector?,
    iconSpaceReserved: Boolean,
    contentDescription: String? = null,
): @Composable (() -> Unit)? {
    return when {
        imageVector != null -> ({
            Icon(
                imageVector = imageVector,
                contentDescription = contentDescription,
            )
        })
        iconSpaceReserved -> ({
            Spacer(modifier = Modifier.width(24.dp))
        })
        else -> null
    }
}
