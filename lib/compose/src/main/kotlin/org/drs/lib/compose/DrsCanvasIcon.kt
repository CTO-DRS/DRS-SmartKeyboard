/*
 * Copyright (C) 2021-2025 The DRS Smart Keyboard Project
 */

package org.drs.lib.compose

import android.graphics.Canvas
import android.graphics.drawable.Drawable
import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.createBitmap

@Composable
fun DrsCanvasIcon(
    @DrawableRes iconId: Int,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
) {
    ResourcesCompat.getDrawable(
        LocalContext.current.resources,
        iconId,
        null,
    )?.let { drawable ->
        DrsCanvasIcon(
            drawable = drawable,
            modifier = modifier,
            contentDescription = contentDescription,
        )
    }
}

@Composable
fun DrsCanvasIcon(
    drawable: Drawable,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
) {
    val bitmap = createBitmap(
        width = drawable.intrinsicWidth,
        height = drawable.intrinsicHeight,
    )
    val canvas = Canvas(bitmap)
    drawable.setBounds(0, 0, canvas.width, canvas.height)
    drawable.draw(canvas)
    Image(
        modifier = modifier,
        bitmap = bitmap.asImageBitmap(),
        contentDescription = contentDescription,
    )
}
