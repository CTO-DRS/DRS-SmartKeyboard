/*
 * Copyright (C) 2024-2025 The DRS Smart Keyboard Project
 */

package com.drs.smartkeyboard.ime.media.emoji

import com.drs.smartkeyboard.ime.keyboard.AbstractKeyData
import com.drs.smartkeyboard.ime.keyboard.ComputingEvaluator
import com.drs.smartkeyboard.ime.keyboard.KeyData
import com.drs.smartkeyboard.ime.popup.PopupSet
import com.drs.smartkeyboard.ime.text.key.KeyCode
import com.drs.smartkeyboard.ime.text.key.KeyType
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.util.stream.IntStream
import kotlin.streams.toList

enum class EmojiSkinTone(val id: Int) {
    DEFAULT(0x0),
    LIGHT_SKIN_TONE(0x1F3FB),
    MEDIUM_LIGHT_SKIN_TONE(0x1F3FC),
    MEDIUM_SKIN_TONE(0x1F3FD),
    MEDIUM_DARK_SKIN_TONE(0x1F3FE),
    DARK_SKIN_TONE(0x1F3FF);

    /**
     * DRS v1.21.0: the next tone in the palette selector's cycle (pure,
     * JVM-testable) — DEFAULT → LIGHT → … → DARK → DEFAULT.
     */
    fun next(): EmojiSkinTone = entries[(ordinal + 1) % entries.size]

    /**
     * DRS v1.21.0: the swatch color the in-palette selector dot paints
     * with (pure, JVM-testable) — the twemoji skin-tone palette so the
     * dot reads as the exact tone the people emoji will render with.
     */
    fun swatchColor(): Long = when (this) {
        DEFAULT -> 0xFFCC4D
        LIGHT_SKIN_TONE -> 0xFFF7DECE
        MEDIUM_LIGHT_SKIN_TONE -> 0xFFF3D2A2
        MEDIUM_SKIN_TONE -> 0xFFD5AB88
        MEDIUM_DARK_SKIN_TONE -> 0xFFAF7E57
        DARK_SKIN_TONE -> 0xFF7C533E
    }
}

enum class EmojiHairStyle(val id: Int) {
    DEFAULT(0x0),
    RED_HAIR(0x1F9B0),
    CURLY_HAIR(0x1F9B1),
    WHITE_HAIR(0x1F9B2),
    BALD(0x1F9B3);
}

class Emoji(val value: String, val name: String, val keywords: List<String>) : KeyData {
    override val type = KeyType.CHARACTER
    override val code = KeyCode.UNSPECIFIED
    override val label = value
    override val groupId = 0
    override val popup: PopupSet<AbstractKeyData>? = null

    val skinTone: EmojiSkinTone

    val hairStyle: EmojiHairStyle

    val codePoints: IntStream
        get() = value.codePoints()

    init {
        val codePoints = value.codePoints().toList()
        skinTone = EmojiSkinTone.entries.firstOrNull { codePoints.contains(it.id) } ?: EmojiSkinTone.DEFAULT
        hairStyle = EmojiHairStyle.entries.firstOrNull { codePoints.contains(it.id) } ?: EmojiHairStyle.DEFAULT
    }

    override fun compute(evaluator: ComputingEvaluator): KeyData {
        return this
    }

    override fun asString(isForDisplay: Boolean): String {
        return value
    }

    override fun toString(): String {
        return "Emoji { value=$value, name=$name, keywords=$keywords }"
    }

    override fun hashCode(): Int {
        return value.hashCode()
    }

    override fun equals(other: Any?): Boolean {
        return other is Emoji && value == other.value
    }

    object ValueOnlySerializer : KSerializer<Emoji> {
        override val descriptor = PrimitiveSerialDescriptor("EmojiValueOnly", PrimitiveKind.STRING)

        override fun serialize(encoder: Encoder, value: Emoji) {
            encoder.encodeString(value.value)
        }

        override fun deserialize(decoder: Decoder): Emoji {
            return Emoji(decoder.decodeString(), "", emptyList())
        }
    }
}
