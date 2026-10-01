/*
 * Copyright (C) 2022-2025 The DRS Smart Keyboard Project
 */

package com.drs.smartkeyboard.ime.media.emoji

@JvmInline
value class EmojiSet(val emojis: List<Emoji>) {
    companion object {
        val Unspecified = EmojiSet(listOf(Emoji("", "", emptyList())))
    }

    init {
        require(emojis.isNotEmpty()) { "Cannot create an EmojiSet with no emojis specified." }
    }

    fun base(
        withSkinTone: EmojiSkinTone = EmojiSkinTone.DEFAULT,
        withHairStyle: EmojiHairStyle = EmojiHairStyle.DEFAULT,
    ): Emoji {
        if (emojis.size == 1) return emojis[0] // Fast compute
        for (emoji in emojis) {
            if (emoji.skinTone == withSkinTone /*&& emoji.hairStyle == withHairStyle*/) {
                return emoji
            }
        }
        return emojis[0] // Fallback
    }

    fun variations(
        withoutSkinTone: EmojiSkinTone = EmojiSkinTone.DEFAULT,
        withoutHairStyle: EmojiHairStyle = EmojiHairStyle.DEFAULT,
    ): List<Emoji> {
        if (emojis.size == 1) return emptyList() // Fast compute
        return emojis.filterNot { it.skinTone == withoutSkinTone /*&& it.hairStyle == withoutHairStyle*/ }
    }
}
