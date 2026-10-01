/*
 * Copyright (C) 2025 The DRS Smart Keyboard Project
 */

package com.drs.smartkeyboard.drs

import com.drs.smartkeyboard.ime.core.Subtype
import com.drs.smartkeyboard.ime.core.SubtypeJsonConfig
import com.drs.smartkeyboard.ime.core.SubtypeLayoutMap
import com.drs.smartkeyboard.ime.keyboard.extCoreComposer
import com.drs.smartkeyboard.ime.keyboard.extCoreCurrencySet
import com.drs.smartkeyboard.ime.keyboard.extCoreLayout
import com.drs.smartkeyboard.ime.keyboard.extCorePopupMapping
import com.drs.smartkeyboard.lib.DrsLocale
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * DRS M0.6 — the profile → subtypes characters-layout transform: a pure
 * conversion that swaps the characters layout of MATCHING languages only,
 * never touches ids or ordering, matches language tags case-insensitively
 * (the documented lesson: a stored "en_us" must match an override "en_US"),
 * and stays honest on unparsable or broken input.
 */
class DrsM06ProfileSubtypeTest : FunSpec({

    fun subtypeOf(tag: String, charactersLayout: String, id: Long): Subtype = Subtype(
        id = id,
        primaryLocale = DrsLocale.from(tag),
        secondaryLocales = emptyList(),
        composer = extCoreComposer("appender"),
        currencySet = extCoreCurrencySet("rial"),
        popupMapping = extCorePopupMapping("ar"),
        layoutMap = SubtypeLayoutMap(
            characters = extCoreLayout(charactersLayout),
        ),
    )

    fun encode(list: List<Subtype>): String = SubtypeJsonConfig.encodeToString(list)

    fun decode(raw: String): List<Subtype> = SubtypeJsonConfig.decodeFromString(raw)

    context("the transform replaces characters layouts of matching languages only") {
        test("matching tag gets the override, other subtype untouched, order and ids kept") {
            val list = listOf(
                subtypeOf("en", "qwerty", 100),
                subtypeOf("ar", "arabic", 200),
            )
            val out = transformSubtypesForProfile(
                encode(list),
                mapOf("en" to "org.drs.layouts:colemak"),
            )
            val parsed = decode(out)
            parsed.size shouldBe 2
            parsed[0].id shouldBe 100 // ids untouched
            parsed[0].layoutMap.characters.toString() shouldBe "org.drs.layouts:colemak"
            parsed[1].layoutMap.characters.toString() shouldBe "org.drs.layouts:arabic" // untouched
            parsed[1].id shouldBe 200 // ordering untouched
        }

        test("CASE-INSENSITIVE match: an override key en_us applies to a stored en_US") {
            val list = listOf(subtypeOf("en_US", "qwerty", 300))
            val out = transformSubtypesForProfile(
                encode(list),
                mapOf("en_us" to "org.drs.layouts:dvorak"),
            )
            decode(out)[0].layoutMap.characters.toString() shouldBe "org.drs.layouts:dvorak"
        }

        test("a layout override for an absent language changes nothing") {
            val list = listOf(subtypeOf("ar", "arabic", 400))
            val raw = encode(list)
            transformSubtypesForProfile(raw, mapOf("fr" to "org.drs.layouts:azerty")) shouldBe raw
        }
    }

    context("honesty on broken input") {
        test("empty overrides and blank input return unchanged") {
            val raw = encode(listOf(subtypeOf("en", "qwerty", 500)))
            transformSubtypesForProfile(raw, emptyMap()) shouldBe raw
            transformSubtypesForProfile("", mapOf("en" to "org.drs.layouts:dvorak")) shouldBe ""
        }

        test("an unparsable preference value is returned unchanged, never rewritten") {
            val broken = "this is not a subtype list"
            transformSubtypesForProfile(broken, mapOf("en" to "org.drs.layouts:dvorak")) shouldBe broken
        }

        test("a malformed override component id skips that subtype, applies the rest") {
            val list = listOf(
                subtypeOf("en", "qwerty", 600),
                subtypeOf("ar", "arabic", 700),
            )
            val out = transformSubtypesForProfile(
                encode(list),
                mapOf(
                    "en" to "no-delimiter-here", // malformed → skipped
                    "ar" to "org.drs.layouts:arabic_pc102",
                ),
            )
            val parsed = decode(out)
            parsed[0].layoutMap.characters.toString() shouldBe "org.drs.layouts:qwerty"
            parsed[1].layoutMap.characters.toString() shouldBe "org.drs.layouts:arabic_pc102"
        }
    }

    context("structural backup coverage") {
        test("DrsProfile with subtypeLayouts survives the DrsState JSON round trip") {
            val profile = DrsSystems.profileFor(DrsUserPath.NORMAL, "structural-test")
                .copy(subtypeLayouts = mapOf("en" to "org.drs.layouts:dvorak", "AR" to "org.drs.layouts:arabic"))
            val json = Json.encodeToString(DrsState.serializer(), DrsState(profiles = listOf(profile)))
            val decoded = Json.decodeFromString(DrsState.serializer(), json)
            decoded.profiles[0].subtypeLayouts shouldBe profile.subtypeLayouts
            // and the raw JSON carries the map under its own name
            json shouldContainRawKey "subtypeLayouts"
        }

        test("old payloads without subtypeLayouts decode with an empty map (backward compatibility)") {
            val legacy = """{"profiles":[{"id":"p","name":"n","path":"CUSTOM","dayThemeId":"a:b",""" +
                """"nightThemeId":"a:c","numberRow":false,"techStripEnabled":false,""" +
                """"suggestionsEnabled":false,"clipboardHistoryEnabled":false,""" +
                """"audioFeedbackEnabled":false,"hapticFeedbackEnabled":false}]}"""
            val state = Json.decodeFromString(DrsState.serializer(), legacy)
            state.profiles[0].subtypeLayouts shouldBe emptyMap()
        }
    }

    context("raw JSON contract") {
        test("the transformed preference stays a subtype array with real layout component names") {
            val out = transformSubtypesForProfile(
                encode(listOf(subtypeOf("en", "qwerty", 800))),
                mapOf("en" to "org.drs.layouts:workman"),
            )
            val root = Json.parseToJsonElement(out).jsonArray
            val characters = root[0].jsonObject["layoutMap"]!!.jsonObject["characters"]!!.jsonPrimitive.content
            characters shouldBe "org.drs.layouts:workman"
        }
    }
})

private infix fun String.shouldContainRawKey(key: String) {
    if (!contains("\"$key\"")) throw AssertionError("JSON does not carry key '$key': $this")
}
