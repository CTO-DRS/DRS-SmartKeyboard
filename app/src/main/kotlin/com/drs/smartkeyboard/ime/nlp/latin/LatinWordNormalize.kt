/*
 * Copyright (C) 2026 The DRS Smart Keyboard Project
 */

package com.drs.smartkeyboard.ime.nlp.latin

/**
 * DRS v1.18.0: pure word-normalization pipeline for the dictionary provider,
 * extracted from LatinLanguageProvider so both the loaded dictionary and the
 * typed prefix go through ONE tested code path.
 *
 * The normalization is used for MATCHING only — suggestions always keep
 * their original (correct) spelling:
 *  - Arabic: diacritics and tatweel are dropped, hamza carriers unify to
 *    bare alef (أ إ آ ٱ -> ا), ta-marbuta (ة -> ه), alif maqsura (ى -> ي),
 *    subordinated hamza forms (ئ -> ي, ؤ -> و)
 *  - DRS p6 (A12): the Quranic annotation signs (U+06D6..U+06ED) and the
 *    open-tanween marks (U+08F0..U+08F3) are dropped as well — this is the
 *    same set the DrsTextTools ARABIC_DIACRITICS normalizer strips, so a
 *    word pasted through the text tools matches the same dictionary entry —
 *    and the Persian letterforms fold onto their Arabic equivalents
 *    (ی U+06CC -> ي, ک U+06A9 -> ك) so a Persian-typed word matches the
 *    Arabic-script dictionary norms
 *  - Latin: accented letters fold to their base letter (é -> e, ß -> ss,
 *    ı -> i, …) so accent-less input matches accented dictionary words —
 *    typing "eleve" still surfaces "élève"
 *  - everything else lowercases
 */
object LatinWordNormalize {

    private val LATIN_FOLDS: Map<Char, String> = mapOf(
        'á' to "a", 'à' to "a", 'â' to "a", 'ä' to "a", 'ã' to "a", 'å' to "a",
        'é' to "e", 'è' to "e", 'ê' to "e", 'ë' to "e",
        'í' to "i", 'ì' to "i", 'î' to "i", 'ï' to "i",
        'ó' to "o", 'ò' to "o", 'ô' to "o", 'ö' to "o", 'õ' to "o",
        'ú' to "u", 'ù' to "u", 'û' to "u", 'ü' to "u",
        'ý' to "y", 'ÿ' to "y",
        'ç' to "c",
        'ñ' to "n",
        'ß' to "ss",
        'œ' to "oe",
        'æ' to "ae",
        'ı' to "i",
        'İ' to "i",
    )

    fun normalize(word: String): String = buildString(word.length) {
        for (c in word) {
            when {
                // DRS p6 (A12): also drop the Quranic annotation signs
                // (U+06D6..U+06ED) and the open tanween (U+08F0..U+08F3) —
                // same set as the DrsTextTools normalizer.
                c in '\u064B'..'\u065F' || c == '\u0670' || c == '\u0640' ||
                    c in '\u06D6'..'\u06ED' || c in '\u08F0'..'\u08F3' -> {
                    // Arabic diacritic / tatweel / annotation mark: drop
                }
                c == '\u0623' || c == '\u0625' || c == '\u0622' || c == '\u0671' -> append('\u0627')
                c == '\u0629' -> append('\u0647')
                c == '\u0649' || c == '\u0626' -> append('\u064A')
                c == '\u0624' -> append('\u0648')
                // DRS p6 (A12): Persian letterforms fold onto their Arabic
                // equivalents (Farsi yeh -> yeh, keheh -> kaf).
                c == '\u06CC' -> append('\u064A')
                c == '\u06A9' -> append('\u0643')
                else -> {
                    // Fold accented Latin letters (lowercase or uppercase —
                    // 'É' folds through its lowercased form) to the base
                    // letter; everything else lowercases.
                    LATIN_FOLDS[c]?.let { append(it) }
                        ?: LATIN_FOLDS[c.lowercaseChar()]?.let { append(it) }
                        ?: append(c.lowercaseChar())
                }
            }
        }
    }

    /**
     * The bundled per-language dictionary assets. Every language listed here
     * gets its own frequency dictionary; unknown languages degrade honestly
     * to the generic (English) fallback instead of pretending otherwise.
     */
    val LANGUAGE_DICT_ASSETS: Map<String, String> = mapOf(
        "ar" to "ime/dict/ar.json",
        "fr" to "ime/dict/fr.json",
        "de" to "ime/dict/de.json",
        "es" to "ime/dict/es.json",
        "it" to "ime/dict/it.json",
        "pt" to "ime/dict/pt.json",
        "tr" to "ime/dict/tr.json",
        "ru" to "ime/dict/ru.json",
        "fa" to "ime/dict/fa.json",
    )

    const val FALLBACK_DICT_ASSET = "ime/dict/data.json"

    fun dictAssetFor(lang: String): String =
        LANGUAGE_DICT_ASSETS[lang] ?: FALLBACK_DICT_ASSET
}
