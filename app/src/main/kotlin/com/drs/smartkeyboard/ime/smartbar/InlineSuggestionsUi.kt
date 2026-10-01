/*
 * Copyright (C) 2024-2025 The DRS Smart Keyboard Project
 */

package com.drs.smartkeyboard.ime.smartbar

import android.os.Build
import android.view.View
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.drs.smartkeyboard.R
import com.drs.smartkeyboard.ime.nlp.NlpInlineAutofillSuggestion
import com.drs.smartkeyboard.ime.popup.GlobalStateNumPopupsShowing
import com.drs.smartkeyboard.ime.theme.DrsImeUi
import com.drs.smartkeyboard.lib.toIntOffset
import org.drs.lib.compose.drsHorizontalScroll
import org.drs.lib.snygg.SnyggSinglePropertySet
import org.drs.lib.snygg.ui.rememberSnyggThemeQuery

val InlineSuggestionsChipMargin = PaddingValues(5.dp)

var CachedInlineSuggestionsChipStyleSet: SnyggSinglePropertySet? = null

@Composable
fun InlineSuggestionsStyleCache() {
    val chipStyleSet = rememberSnyggThemeQuery(DrsImeUi.InlineAutofillChip.elementName)
    LaunchedEffect(chipStyleSet) {
        CachedInlineSuggestionsChipStyleSet = chipStyleSet
    }
}

@RequiresApi(Build.VERSION_CODES.R)
@Composable
fun InlineSuggestionsUi(
    inlineSuggestions: List<NlpInlineAutofillSuggestion>,
    modifier: Modifier = Modifier,
) {
    val scrollState = rememberScrollState()
    val numPopupsShowing by GlobalStateNumPopupsShowing.collectAsState()
    val isZOrderedOnTop by remember { derivedStateOf { numPopupsShowing == 0 } }

    Row(
        modifier
            .fillMaxSize()
            .drsHorizontalScroll(
                state = scrollState,
                scrollbarHeight = CandidatesRowScrollbarHeight,
            ),
    ) {
        for (inlineSuggestion in inlineSuggestions) {
            if (inlineSuggestion.view == null) {
                continue
            }
            // DRS p7 (E3-7) — key() per suggestion instance. AndroidView's factory runs
            // once per composition NODE, but NlpInlineAutofill publishes FRESH
            // InlineContentViews per autofill request; positional reuse kept hosting the
            // previous batch's views at earlier positions (stale/blank chips, taps hit the
            // old suggestion). A keyed scope forces a fresh composition per new instance.
            key(inlineSuggestion) {
                var chipPos by remember { mutableStateOf(IntOffset.Zero) }
                val corderRadius = dimensionResource(R.dimen.suggestions_chip_corner_radius)
                val shape = remember(corderRadius) { RoundedCornerShape(corderRadius) }
                AndroidView(
                    modifier = Modifier
                        .onGloballyPositioned { chipPos = it.positionInParent().toIntOffset() }
                        .padding(InlineSuggestionsChipMargin)
                        .clip(shape),
                    factory = { inlineSuggestion.view },
                    update = { view ->
                        view.isZOrderedOnTop = isZOrderedOnTop
                        // TODO scroll clip can probably also be done in Jetpack Compose
                        val xMin = scrollState.value
                        val xMax = scrollState.value + scrollState.viewportSize
                        view.clipBounds = android.graphics.Rect(
                            (xMin - chipPos.x).coerceAtLeast(0),
                            0,
                            (xMax - chipPos.x).coerceAtMost(view.width),
                            view.height,
                        )
                        view.visibility = if (view.clipBounds.isEmpty) View.INVISIBLE else View.VISIBLE
                    }
                )
            }
        }
    }
}
