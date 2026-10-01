/*
 * Copyright (C) 2024-2025 The DRS Smart Keyboard Project
 */

package com.drs.smartkeyboard.ime.media.emoji

import android.content.Context
import com.drs.smartkeyboard.lib.DrsLocale
import org.drs.lib.android.bufferedReader
import io.github.reactivecircus.cache4k.Cache
import java.util.*

private typealias EmojiDataByCategoryImpl = EnumMap<EmojiCategory, MutableList<EmojiSet>>
private typealias EmojiDataBySkinToneImpl = EnumMap<EmojiSkinTone, MutableList<Emoji>>
typealias EmojiDataByCategory = Map<EmojiCategory, List<EmojiSet>>
typealias EmojiDataBySkinTone = Map<EmojiSkinTone, List<Emoji>>

data class EmojiData(
    val byCategory: EmojiDataByCategory,
    val bySkinTone: EmojiDataBySkinTone,
) {
    companion object {
        private val cache = Cache.Builder<String, EmojiData>().build()
        val Fallback = empty()

        private fun newByCategory(): EmojiDataByCategoryImpl {
            return EmojiDataByCategoryImpl(EmojiCategory::class.java).also { map ->
                for (category in EmojiCategory.entries) {
                    map[category] = mutableListOf()
                }
            }
        }

        private fun newBySkinTone(): EmojiDataBySkinToneImpl {
            return EmojiDataBySkinToneImpl(EmojiSkinTone::class.java).also { map ->
                for (skinTone in EmojiSkinTone.entries) {
                    map[skinTone] = mutableListOf()
                }
            }
        }

        fun empty(): EmojiData {
            return EmojiData(newByCategory(), newBySkinTone())
        }

        suspend fun get(context: Context, path: String): EmojiData {
            return cache.get(path) {
                loadEmojiDataMap(context, path)
            }
        }

        suspend fun get(context: Context, locale: DrsLocale): EmojiData {
            val available = context.assets.list(EMOJI_ASSETS_DIR)?.toList().orEmpty()
            return get(context, resolveEmojiAssetPathWithFallback(available, locale))
        }

        private fun loadEmojiDataMap(context: Context, path: String): EmojiData {
            val byCategory = newByCategory()
            val bySkinTone = newBySkinTone()

            var ec: EmojiCategory? = null
            var emojiEditorList: MutableList<Emoji>? = null

            fun commitEmojiEditorList() {
                emojiEditorList?.let { byCategory[ec]!!.add(EmojiSet(it)) }
                emojiEditorList = null
            }

            context.assets.bufferedReader(path).useLines { lines ->
                for (line in lines) {
                    if (line.startsWith("#")) {
                        // Comment line
                    } else if (line.startsWith("[")) {
                        commitEmojiEditorList()
                        ec = EmojiCategory.entries.find { it.id == line.slice(1 until (line.length - 1)) }
                    } else if (line.trim().isEmpty() || ec == null) {
                        // Empty line
                        continue
                    } else {
                        if (!line.startsWith("\t")) {
                            commitEmojiEditorList()
                        }
                        // Assume it is a data line
                        val data = line.split(";")
                        if (data.size == 3) {
                            val base = emojiEditorList?.first()
                            val emoji = Emoji(
                                value = data[0].trim(),
                                name = base?.name ?: data[1].trim(),
                                keywords = data[2].split("|").map { it.trim() },
                            )
                            if (emojiEditorList != null) {
                                emojiEditorList!!.add(emoji)
                            } else {
                                emojiEditorList = mutableListOf(emoji)
                            }
                        }
                    }
                }
                commitEmojiEditorList()
            }

            for (category in byCategory.keys) {
                for (emojiSet in byCategory[category]!!) {
                    if (emojiSet.emojis.size == 1) {
                        // No variations provided, we fallback to using the base for all skin tones
                        val base = emojiSet.emojis.first()
                        for (skinTone in EmojiSkinTone.entries) {
                            bySkinTone[skinTone]!!.add(base)
                        }
                        continue
                    }
                    for (emoji in emojiSet.emojis) {
                        bySkinTone[emoji.skinTone]!!.add(emoji)
                    }
                }
            }

            return EmojiData(byCategory, bySkinTone)
        }

        /**
         * DRS v1.19.0: pure, testable resolution of the emoji asset file for a locale.
         *
         * The previous behavior was the root cause of the dead emoji search: the palette
         * loaded "root.txt" whose metadata columns are empty for ~99.8% of the lines, so
         * the search engine had nothing to match against. Meanwhile the full CLDR v48
         * annotation files (ar/en/de/es/fr/it/pt) shipped in the very same assets
         * directory, consumed only by [EmojiSuggestionProvider].
         *
         * File matching follows this preference order:
         * - {language}_{country}_{variant}.txt
         * - {language}_{country}.txt
         * - {language}.txt
         *
         * @param available The file names inside "ime/media/emoji/" (no directory prefix).
         * @param language Lowercase-agnostic language code of the locale.
         * @param country Country/region code of the locale, or null.
         * @param variant Variant code of the locale, or null.
         *
         * @return The full asset path of the best-matching file, or null when the language
         *         has no annotation file at all.
         */
        internal fun pickEmojiAssetPath(
            available: List<String>,
            language: String,
            country: String?,
            variant: String?,
        ): String? {
            val makePath = { file: String -> "$EMOJI_ASSETS_DIR$file" }
            val lang = language.lowercase()
            if (variant != null && country != null) {
                "${lang}_${country}_${variant}.txt".takeIf { available.contains(it) }?.let {
                    return makePath(it)
                }
            }
            if (country != null) {
                "${lang}_${country}.txt".takeIf { available.contains(it) }?.let { return makePath(it) }
            }
            "${lang}.txt".takeIf { available.contains(it) }?.let {
                return makePath(it)
            }
            return null
        }

        /**
         * DRS v1.19.0: resolves the emoji annotation file for a locale with honest fallbacks
         * so the palette (and the emoji suggestions) never degrade to metadata-less data:
         * 1. The locale's own annotation file (native-language keyword search).
         * 2. The English annotation file — search still works via the documented English
         *    keywords ("heart, fire…") instead of not working at all.
         * 3. The structural root file (last resort; same emoji set, empty metadata).
         */
        internal fun resolveEmojiAssetPathWithFallback(available: List<String>, locale: DrsLocale): String {
            pickEmojiAssetPath(
                available = available,
                language = locale.language,
                country = locale.country.takeIf { it.isNotBlank() },
                variant = locale.variant.takeIf { it.isNotBlank() },
            )?.let { return it }
            if (available.contains("en.txt")) {
                return EMOJI_ASSETS_DIR + "en.txt"
            }
            return EMOJI_ASSETS_DIR + EMOJI_ROOT_FILE
        }

        private const val EMOJI_ASSETS_DIR = "ime/media/emoji/"
        private const val EMOJI_ROOT_FILE = "root.txt"
    }
}
