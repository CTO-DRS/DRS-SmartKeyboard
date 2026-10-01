/*
 * Copyright (C) 2025 The DRS Smart Keyboard Project
 */

package org.drs.lib.snygg.ui

import androidx.compose.foundation.clickable
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.drs.lib.snygg.SnyggQueryAttributes
import org.drs.lib.snygg.SnyggSelector
import org.drs.lib.snygg.SnyggStylesheet

/**
 * Simple layout composable that places its children in a horizontal sequence.
 *
 * This composable infers its style from the current [SnyggTheme][org.drs.lib.snygg.SnyggTheme], which is
 * required to be provided by [ProvideSnyggTheme].
 *
 * @param elementName The name of this element. If `null` the style will be inherited from the parent element.
 * @param attributes The attributes of the element used to refine the query.
 * @param selector A specific SnyggSelector to query the style for.
 * @param modifier The modifier to be applied to the Row.
 * @param onClick The action that is executed on click
 * @param enabled Weather the chip is enabled
 * @param imageVector Icon
 * @param text Text
 *
 * @since 0.5.0-alpha01
 *
 * @see [SnyggRow]
 * @see [SnyggText]
 * @see [SnyggIcon]
 */
@Composable
fun SnyggChip(
    elementName: String? = null,
    attributes: SnyggQueryAttributes = emptyMap(),
    selector: SnyggSelector? = null,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    enabled: Boolean = true,
    imageVector: ImageVector? = null,
    text: String,
) {
    SnyggChip(
        elementName,
        attributes,
        selector,
        modifier,
        onClick,
        enabled,
        icon = if (imageVector == null) null else ({
            SnyggIcon(
                elementName = "$elementName-icon",
                attributes = attributes,
                selector = selector,
                imageVector = imageVector,
            )
        }),
        text = {
            SnyggText(
                elementName = "$elementName-text",
                attributes = attributes,
                selector = selector,
                text = text,
            )
        },
    )
}

@Composable
internal fun SnyggChip(
    elementName: String? = null,
    attributes: SnyggQueryAttributes = emptyMap(),
    selector: SnyggSelector? = null,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    enabled: Boolean = true,
    icon: (@Composable () -> Unit)? = null,
    text: (@Composable () -> Unit)? = null,
) {
    SnyggRow(
        elementName = elementName,
        attributes = attributes,
        selector = selector,
        modifier = modifier,
        clickAndSemanticsModifier = Modifier
            .clickable(
                interactionSource = null,
                indication = ripple(),
                enabled = enabled,
                onClick = onClick,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        icon?.invoke()
        text?.invoke()
    }
}

@Preview
@Composable
private fun SimpleSnyggChip() {
    val stylesheet = SnyggStylesheet.v2 {
        "preview-row" {
            background = rgbaColor(255, 255, 255)
            foreground = rgbaColor(255, 0, 0)
            padding = padding(10.dp)
        }
        "chip" {
            background = rgbaColor(255, 0, 0)
            foreground = rgbaColor(255, 255, 255)
            shape = roundedCornerShape(20, 20, 20, 20)
        }
    }
    val theme = rememberSnyggTheme(stylesheet)

    ProvideSnyggTheme(theme) {
        SnyggRow("preview-row") {
            SnyggChip(
                elementName = "chip",
                onClick = {},
                text = "Hello",
            )
        }
    }
}
