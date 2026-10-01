/*
 * Copyright (C) 2026 The DRS Smart Keyboard Project
 */

package com.drs.smartkeyboard.ime.nlp.latin

import io.kotest.core.spec.style.FunSpec
import io.kotest.datatest.withData
import io.kotest.matchers.shouldBe

class LatinWordNormalizeTest : FunSpec({
    context("Latin accent folding (matching only)") {
        withData(
            Pair("éléve", "eleve"), // French: é folds, suggestions keep "élève"
            Pair("ÉCOLE", "ecole"), // accented uppercase folds + lowercases
            Pair("größe", "grosse"), // German ß folds to ss (length may grow)
            Pair("año", "ano"), // Spanish ñ folds
            Pair("beyazı", "beyazi"), // Turkish dotless ı folds to i
            Pair("İstanbul", "istanbul"), // dotted capital I folds cleanly
            Pair("cœur", "coeur"), // French ligature folds
            Pair("Straße", "strasse"), // German capitalization + ß
            Pair("café", "cafe"), // already-typed accents still match dict norms
        ) { (input, expected) ->
            LatinWordNormalize.normalize(input) shouldBe expected
        }
    }

    context("Arabic unification is preserved exactly") {
        withData(
            Pair("مدرسة", "مدرسه"), // ta-marbuta -> ha
            Pair("أكبر", "اكبر"), // hamza carrier -> bare alef
            Pair("إلى", "الي"), // hamza-below carrier -> bare alef, maqsura -> ya
            Pair("علىٰ", "علي"), // dagger alef dropped, alif maqsura -> ya
            Pair("كِتاب", "كتاب"), // harakat stripped
            Pair("سلامـ", "سلام"), // tatweel stripped
            Pair("ؤلاء", "ولاء"), // waw-hamza -> waw
        ) { (input, expected) ->
            LatinWordNormalize.normalize(input) shouldBe expected
        }
    }

    test("plain Latin and digits pass through lowercased") {
        LatinWordNormalize.normalize("Hello") shouldBe "hello"
        LatinWordNormalize.normalize("WORLD123") shouldBe "world123"
    }

    context("dictionary asset mapping: per-language, honest fallback") {
        withData(
            Pair("ar", "ime/dict/ar.json"),
            Pair("fr", "ime/dict/fr.json"),
            Pair("de", "ime/dict/de.json"),
            Pair("es", "ime/dict/es.json"),
            Pair("it", "ime/dict/it.json"),
            Pair("pt", "ime/dict/pt.json"),
            Pair("tr", "ime/dict/tr.json"),
            Pair("ru", "ime/dict/ru.json"),
            Pair("fa", "ime/dict/fa.json"),
            Pair("en", "ime/dict/data.json"), // English uses the generic asset directly
            Pair("nl", "ime/dict/data.json"), // unknown -> honest English fallback
            Pair("", "ime/dict/data.json"),
        ) { (lang, expected) ->
            LatinWordNormalize.dictAssetFor(lang) shouldBe expected
        }
    }

    test("all mapped dictionary assets are unique") {
        val assets = LatinWordNormalize.LANGUAGE_DICT_ASSETS.values
        assets.distinct() shouldBe assets.toList()
    }
})
