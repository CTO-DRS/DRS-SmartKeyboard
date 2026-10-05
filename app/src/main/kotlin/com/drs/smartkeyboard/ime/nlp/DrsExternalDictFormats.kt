/*
 * Copyright (C) 2025 The DRS Smart Keyboard Project
 */

package com.drs.smartkeyboard.ime.nlp

import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer

/**
 * DRS v2.8.0 «قاموسك من ملفك» — the pure external-dictionary file parser.
 *
 * Two deterministic text formats are accepted, auto-detected by the first
 * non-whitespace character:
 *
 *  1. **JSON object** (`{` first): exactly the same `{"word": freq}` shape the
 *     bundled per-language dictionaries use — so any export of a DRS dictionary
 *     re-imports losslessly.
 *  2. **Word-list lines**: one word per line, optionally `word,freq`. Blank
 *     lines and `#` comments are skipped. A missing frequency defaults to
 *     [FREQ_DEFAULT] (the middle of the 1..255 band — the same default the
 *     internal user dictionary uses for a "regular" word).
 *
 * All frequencies are clamped into 1..255 and every entry is deduplicated with
 * the maximum frequency winning. Entries that can never match typing (empty,
 * containing whitespace, longer than [MAX_WORD_LENGTH]) are skipped — never
 * guessed at. Parsing fails with an honest [DictParseException] carrying a
 * stable reason the UI maps to a localized message.
 */
object DrsExternalDictFormats {

    const val MAX_WORDS_PER_FILE = 100_000
    const val MAX_WORD_LENGTH = 40
    const val FREQ_MIN = 1
    const val FREQ_MAX = 255
    const val FREQ_DEFAULT = 128

    const val FORMAT_JSON = "json"
    const val FORMAT_LINES = "lines"

    const val REASON_MALFORMED_JSON = "MALFORMED_JSON"
    const val REASON_NO_WORDS = "NO_WORDS"

    data class Parsed(val words: LinkedHashMap<String, Int>, val format: String)

    class DictParseException(val reason: String) : Exception(reason)

    private val json = Json

    fun parse(text: String): Parsed {
        val trimmed = text.trimStart('\uFEFF').trimStart()
        val parsed = if (trimmed.startsWith("{")) parseJson(trimmed) else parseLines(trimmed)
        if (parsed.words.isEmpty()) throw DictParseException(REASON_NO_WORDS)
        return parsed
    }

    private fun parseJson(text: String): Parsed {
        val raw = runCatching {
            json.decodeFromString(serializer<Map<String, Int>>(), text)
        }.getOrElse { throw DictParseException(REASON_MALFORMED_JSON) }
        val words = LinkedHashMap<String, Int>()
        for ((word, freq) in raw) {
            if (words.size >= MAX_WORDS_PER_FILE) break
            if (!isAcceptableWord(word)) continue
            mergeInto(words, word, freq)
        }
        return Parsed(words, FORMAT_JSON)
    }

    private fun parseLines(text: String): Parsed {
        val words = LinkedHashMap<String, Int>()
        for (line0 in text.lineSequence()) {
            if (words.size >= MAX_WORDS_PER_FILE) break
            val line = line0.trim()
            if (line.isEmpty() || line.startsWith("#")) continue
            val comma = line.indexOf(',')
            val word: String
            val freqRaw: String?
            if (comma >= 0) {
                word = line.substring(0, comma).trim()
                freqRaw = line.substring(comma + 1).trim()
            } else {
                word = line
                freqRaw = null
            }
            if (!isAcceptableWord(word)) continue
            val freq = freqRaw?.toIntOrNull()?.coerceIn(FREQ_MIN, FREQ_MAX) ?: FREQ_DEFAULT
            mergeInto(words, word, freq)
        }
        return Parsed(words, FORMAT_LINES)
    }

    /**
     * A word is acceptable when it can ever be typed as a single token:
     * non-empty, at most [MAX_WORD_LENGTH] chars, no whitespace inside.
     */
    fun isAcceptableWord(word: String): Boolean {
        if (word.isEmpty() || word.length > MAX_WORD_LENGTH) return false
        return word.none { it.isWhitespace() }
    }

    /** Deterministic dedupe: the HIGHER frequency wins, values clamped into range. */
    fun mergeInto(words: MutableMap<String, Int>, word: String, freq: Int) {
        val clamped = freq.coerceIn(FREQ_MIN, FREQ_MAX)
        val existing = words[word]
        words[word] = if (existing == null) clamped else maxOf(existing, clamped)
    }
}
