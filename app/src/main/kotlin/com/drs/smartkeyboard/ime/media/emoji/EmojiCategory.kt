/*
 * Copyright (C) 2022-2025 The DRS Smart Keyboard Project
 */

package com.drs.smartkeyboard.ime.media.emoji

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEmotions
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.EmojiFlags
import androidx.compose.material.icons.filled.EmojiFoodBeverage
import androidx.compose.material.icons.filled.EmojiNature
import androidx.compose.material.icons.filled.EmojiObjects
import androidx.compose.material.icons.filled.EmojiPeople
import androidx.compose.material.icons.filled.EmojiSymbols
import androidx.compose.material.icons.filled.EmojiTransportation
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.ui.graphics.vector.ImageVector
import com.drs.smartkeyboard.R

enum class EmojiCategory(val id: String) {
    RECENTLY_USED("recently_used"),
    SMILEYS_EMOTION("smileys_emotion"),
    PEOPLE_BODY("people_body"),
    ANIMALS_NATURE("animals_nature"),
    FOOD_DRINK("food_drink"),
    TRAVEL_PLACES("travel_places"),
    ACTIVITIES("activities"),
    OBJECTS("objects"),
    SYMBOLS("symbols"),
    FLAGS("flags");

    fun icon(): ImageVector {
        return when (this) {
            RECENTLY_USED -> Icons.Default.Schedule
            SMILEYS_EMOTION -> Icons.Default.EmojiEmotions
            PEOPLE_BODY -> Icons.Default.EmojiPeople
            ANIMALS_NATURE -> Icons.Default.EmojiNature
            FOOD_DRINK -> Icons.Default.EmojiFoodBeverage
            TRAVEL_PLACES -> Icons.Default.EmojiTransportation
            ACTIVITIES -> Icons.Default.EmojiEvents
            OBJECTS -> Icons.Default.EmojiObjects
            SYMBOLS -> Icons.Default.EmojiSymbols
            FLAGS -> Icons.Default.EmojiFlags
        }
    }

    /**
     * DRS v1.21.0: the localized tab name — the palette tabs used to be
     * icon-only, so TalkBack announced an unlabeled tab for each one.
     */
    fun labelRes(): Int {
        return when (this) {
            RECENTLY_USED -> R.string.emoji__history__recent
            SMILEYS_EMOTION -> R.string.emoji__category__smileys_emotion
            PEOPLE_BODY -> R.string.emoji__category__people_body
            ANIMALS_NATURE -> R.string.emoji__category__animals_nature
            FOOD_DRINK -> R.string.emoji__category__food_drink
            TRAVEL_PLACES -> R.string.emoji__category__travel_places
            ACTIVITIES -> R.string.emoji__category__activities
            OBJECTS -> R.string.emoji__category__objects
            SYMBOLS -> R.string.emoji__category__symbols
            FLAGS -> R.string.emoji__category__flags
        }
    }
}
