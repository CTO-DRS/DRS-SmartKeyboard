/*
 * Copyright (C) 2025 The DRS Smart Keyboard Project
 */

package org.drs.lib.snygg.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.drs.lib.snygg.SnyggQueryAttributes
import org.drs.lib.snygg.SnyggSelector
import org.drs.lib.snygg.SnyggStylesheet

/**
 * Simple Icon composable, which displays a given [imageVector] annotated by the [contentDescription].
 *
 * This composable infers its style from the current [SnyggTheme][org.drs.lib.snygg.SnyggTheme], which is
 * required to be provided by [ProvideSnyggTheme].
 *
 * @param elementName The name of this element. If `null` the style will be inherited from the parent element.
 * @param attributes The attributes of the element used to refine the query.
 * @param selector A specific SnyggSelector to query the style for.
 * @param modifier The modifier to be applied to the Icon.
 * @param imageVector The imageVector which will be drawn as Icon.
 * @param contentDescription Text used by accessibility services to describe what this icon represents.
 * This should always be provided unless this icon is used for decorative purposes,
 * and does not represent a meaningful action that a user can take.
 *
 * @since 0.5.0-alpha01
 *
 * @see [Icon]
 */
@Composable
fun SnyggIcon(
    elementName: String? = null,
    attributes: SnyggQueryAttributes = emptyMap(),
    selector: SnyggSelector? = null,
    modifier: Modifier = Modifier,
    imageVector: ImageVector,
    contentDescription: String? = null,
) {
    ProvideSnyggStyle(elementName, attributes, selector) { style ->
        Icon(
            modifier = modifier.snyggIconSize(style),
            imageVector = imageVector,
            contentDescription = contentDescription,
            tint = style.foreground(),
        )
    }
}

/**
 * Simple Icon composable, which displays a given [bitmap] annotated by the [contentDescription].
 *
 * This composable infers its style from the current [SnyggTheme][org.drs.lib.snygg.SnyggTheme], which is
 * required to be provided by [ProvideSnyggTheme].
 *
 * @param elementName The name of this element. If `null` the style will be inherited from the parent element.
 * @param attributes The attributes of the element used to refine the query.
 * @param selector A specific SnyggSelector to query the style for.
 * @param modifier The modifier to be applied to the Icon.
 * @param bitmap The imageBitmap which will be drawn as Icon.
 * @param contentDescription Text used by accessibility services to describe what this icon represents.
 * This should always be provided unless this icon is used for decorative purposes,
 * and does not represent a meaningful action that a user can take.
 *
 * @since 0.5.0-alpha01
 *
 * @see [Icon]
 */
@Composable
fun SnyggIcon(
    elementName: String? = null,
    attributes: SnyggQueryAttributes = emptyMap(),
    selector: SnyggSelector? = null,
    modifier: Modifier = Modifier,
    bitmap: ImageBitmap,
    contentDescription: String? = null,
) {
    ProvideSnyggStyle(elementName, attributes, selector) { style ->
        Icon(
            modifier = modifier.snyggIconSize(style),
            bitmap = bitmap,
            contentDescription = contentDescription,
            tint = style.foreground(),
        )
    }
}

/**
 * Simple Icon composable, which displays a given [painter] annotated by the [contentDescription].
 *
 * This composable infers its style from the current [SnyggTheme][org.drs.lib.snygg.SnyggTheme], which is
 * required to be provided by [ProvideSnyggTheme].
 *
 * @param elementName The name of this element. If `null` the style will be inherited from the parent element.
 * @param attributes The attributes of the element used to refine the query.
 * @param selector A specific SnyggSelector to query the style for.
 * @param modifier The modifier to be applied to the Icon.
 * @param painter The painter which will be drawn as Icon.
 * @param contentDescription Text used by accessibility services to describe what this icon represents.
 * This should always be provided unless this icon is used for decorative purposes,
 * and does not represent a meaningful action that a user can take.
 *
 * @since 0.5.0-alpha01
 *
 * @see [Icon]
 */
@Composable
fun SnyggIcon(
    elementName: String? = null,
    attributes: SnyggQueryAttributes = emptyMap(),
    selector: SnyggSelector? = null,
    modifier: Modifier = Modifier,
    painter: Painter,
    contentDescription: String? = null,
) {
    ProvideSnyggStyle(elementName, attributes, selector) { style ->
        Icon(
            modifier = modifier.snyggIconSize(style),
            painter = painter,
            contentDescription = contentDescription,
            tint = style.foreground(),
        )
    }
}

@Preview
@Composable
private fun SimpleSnyggIcon() {
    val stylesheet = SnyggStylesheet.v2 {
        "preview-column" {
            fontSize = fontSize(20.sp)
            foreground = rgbaColor(0, 0, 255)
        }
        "preview-icon" {
            padding = padding(6.dp)
        }
    }
    val theme = rememberSnyggTheme(stylesheet)

    ProvideSnyggTheme(theme) {
        SnyggColumn("preview-column") {
            SnyggText("preview-text", text = "blue text")
            SnyggIcon("preview-icon", imageVector = Icons.Default.Search)
        }
    }
}
