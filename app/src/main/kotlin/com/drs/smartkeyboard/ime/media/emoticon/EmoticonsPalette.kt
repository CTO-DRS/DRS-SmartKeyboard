/*
 * Copyright (C) 2025-2026 The DRS Smart Keyboard Project
 */

package com.drs.smartkeyboard.ime.media.emoticon

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.drs.smartkeyboard.ime.input.InputEventDispatcher
import com.drs.smartkeyboard.ime.keyboard.KeyData
import com.drs.smartkeyboard.ime.media.KeyboardLikeButton
import com.drs.smartkeyboard.ime.text.key.KeyCode
import com.drs.smartkeyboard.ime.text.key.KeyType
import com.drs.smartkeyboard.ime.text.keyboard.TextKeyData
import com.drs.smartkeyboard.ime.theme.DrsImeUi
import org.drs.lib.snygg.ui.SnyggText

/**
 * DRS v1.23.0: the kaomoji palette — «لوحة الكاوموجي». The 21-entry
 * emoticons.json shipped in the APK since day one while its loader stayed
 * a stubbed null, so the whole asset was invisible. The grid renders it
 * through the exact same keyboard-like tile the emoji palette uses, and a
 * tap commits the kaomoji text at the cursor through the normal MEDIA
 * commit path (a fabricated character key whose label IS the payload —
 * the commit branch reads the label for code < SPACE).
 */
@Composable
fun EmoticonsPalette(
    layoutData: EmoticonLayoutData,
    inputEventDispatcher: InputEventDispatcher,
    modifier: Modifier = Modifier,
) {
    val emoticons = remember(layoutData) { layoutData.arrangement.flatten() }

    LazyVerticalGrid(
        columns = GridCells.Fixed(4),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
        content = {
            items(emoticons) { emoticon ->
                EmoticonTile(emoticon = emoticon, inputEventDispatcher = inputEventDispatcher)
            }
        },
    )
}

@Composable
private fun EmoticonTile(
    emoticon: EmoticonKeyData,
    inputEventDispatcher: InputEventDispatcher,
) {
    // The commit payload rides a fabricated character key: code = 0
    // (< SPACE) makes asString(isForDisplay = false) return the label,
    // and the MEDIA commit branch inserts it verbatim at the cursor.
    val keyData: KeyData = remember(emoticon.icon) {
        TextKeyData(
            type = KeyType.CHARACTER,
            code = KeyCode.UNSPECIFIED,
            label = emoticon.icon,
        )
    }
    KeyboardLikeButton(
        elementName = DrsImeUi.MediaEmojiKey.elementName,
        inputEventDispatcher = inputEventDispatcher,
        keyData = keyData,
        modifier = Modifier.fillMaxSize(),
    ) {
        // elementName = null → inherits the MediaEmojiKey style, so the
        // kaomoji text takes its color/size from the active keyboard
        // theme (never a hardcoded white-on-white).
        SnyggText(text = emoticon.icon)
    }
}
