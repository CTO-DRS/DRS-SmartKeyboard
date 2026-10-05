/*
 * Copyright (C) 2025 The DRS Smart Keyboard Project
 */

package org.drs.lib.snygg.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.allowHardware
import org.drs.lib.snygg.SnyggQueryAttributes
import org.drs.lib.snygg.SnyggSelector
import org.drs.lib.snygg.SnyggStylesheet

/**
 * Simple layout composable with [content]
 *
 * This composable infers its style from the current [SnyggTheme][org.drs.lib.snygg.SnyggTheme], which is
 * required to be provided by [ProvideSnyggTheme].
 *
 * @param elementName The name of this element. If `null` the style will be inherited from the parent element.
 * @param attributes The attributes of the element used to refine the query.
 * @param selector A specific SnyggSelector to query the style for.
 * @param modifier The modifier to be applied to the layout.
 * @param clickAndSemanticsModifier The modifier to be applied to the layout after drawing the background.
 * @param contentAlignment The default alignment inside the Box.
 * @param propagateMinConstraints Whether the incoming min constraints should be passed to content.
 * @param supportsBackgroundImage controls if this Box supports background images.
 * @param backgroundImageDim An optional dimming scrim (alpha 0..1) drawn between the background
 *   image and the content, so keys/text stay readable over bright pictures. Only applies when a
 *   background image is actually rendered. DRS v2.8.0: wired to the user background-image
 *   preference (see ImeWindow), the same injection pattern as the dynamic accent color.
 * @param backgroundImageDescription The content description of the background image.
 * @param allowClip If clipping should be allowed on this box.
 * @param content The content of the Box
 *
 * @since 0.5.0-alpha01
 *
 * @see [Box]
 */
@Composable
fun SnyggBox(
    elementName: String? = null,
    attributes: SnyggQueryAttributes = emptyMap(),
    selector: SnyggSelector? = null,
    modifier: Modifier = Modifier,
    clickAndSemanticsModifier: Modifier = Modifier,
    contentAlignment: Alignment = Alignment.TopStart,
    propagateMinConstraints: Boolean = false,
    supportsBackgroundImage: Boolean = false,
    backgroundImageDim: Float = 0f,
    backgroundImageDescription: String? = null,
    allowClip: Boolean = true,
    content: @Composable BoxScope.() -> Unit,
) {
    ProvideSnyggStyle(elementName, attributes, selector) { style ->
        val assetResolver = LocalSnyggAssetResolver.current
        val context = LocalContext.current
        val imagePath = when {
            supportsBackgroundImage -> {
                style.backgroundImage.uriOrNull()?.let { imageUri ->
                    assetResolver.resolveAbsolutePath(imageUri).getOrNull()
                }
            }
            else -> null
        }
        Box(
            modifier = modifier
                .snyggMargin(style)
                .snyggShadow(style)
                .snyggBorder(style)
                .snyggBackground(style, allowClip = allowClip)
                .then(clickAndSemanticsModifier)
                .snyggPadding(style),
            contentAlignment = contentAlignment,
            propagateMinConstraints = propagateMinConstraints,
        ) {
            if (imagePath != null) {
                AsyncImage(
                    modifier = Modifier
                        .matchParentSize()
                        .clip(style.shape()),
                    // https://github.com/coil-kt/coil/issues/159
                    model = ImageRequest.Builder(context)
                        .data(imagePath)
                        .allowHardware(false) // slower, but hey at least it doesn't crash out of the blue
                        .build(),
                    contentScale = style.contentScale(),
                    contentDescription = backgroundImageDescription,
                )
                // DRS v2.8.0: honest readability scrim — sits between the image and the
                // content, never over the content itself.
                val scrimAlpha = backgroundImageDim.coerceIn(0f, 1f)
                if (scrimAlpha > 0f) {
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .clip(style.shape())
                            .background(Color.Black.copy(alpha = scrimAlpha)),
                    )
                }
            }
            content()
        }
    }
}

@Preview
@Composable
private fun SimpleSnyggBox() {
    val stylesheet = SnyggStylesheet.v2 {
        "preview-surface" {
            background = rgbaColor(255, 255, 255)
            foreground = rgbaColor(255, 0, 0)
            padding = padding(10.dp)
            shape = roundedCornerShape(20)
            clip = yes()
        }
        "preview-text" {
            fontSize = fontSize(12.sp)
        }
    }
    val theme = rememberSnyggTheme(stylesheet)

    ProvideSnyggTheme(theme) {
        SnyggBox("preview-surface") {
            SnyggColumn("column") {
                SnyggText("preview-text", text = "hello world")
                SnyggText("preview-text", text = "second text")
            }
        }
    }
}
