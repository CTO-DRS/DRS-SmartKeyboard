/*
 * Copyright (C) 2025 The DRS Smart Keyboard Project
 */

package com.drs.smartkeyboard.drs

import com.drs.smartkeyboard.ime.nlp.DrsExternalDictFormats
import com.drs.smartkeyboard.ime.nlp.DrsExternalDictMerger
import com.drs.smartkeyboard.ime.nlp.DrsExternalDictStore
import com.drs.smartkeyboard.ime.theme.DrsThemeBackground
import com.drs.smartkeyboard.ime.theme.DrsThemeBackgroundPatcher
import androidx.compose.ui.graphics.Color
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.shouldBeSameInstanceAs
import kotlinx.serialization.json.Json
import org.drs.lib.snygg.SnyggElementRule
import org.drs.lib.snygg.SnyggStylesheet
import org.drs.lib.snygg.value.SnyggStaticColorValue
import org.drs.lib.snygg.value.SnyggUriValue
import java.io.File

/**
 * DRS v2.8.0 «خلفيتك من ألبومك، وقاموسك من ملفك» — contract tests for the two
 * honest, deterministic features shipped this round:
 *
 *  1. **User background image**: one device-picked picture, stored app-privately
 *     under a SHA-256-derived name, injected IN MEMORY into the active theme's
 *     `window` rule at load time, with a readability scrim.
 *  2. **External dictionary indexes**: user wordlists (bundled-JSON shape or
 *     `word,freq` lines) merged INTO the bundled dictionaries, never replacing
 *     them, deduped by max frequency, cached invalidation on import/remove.
 */
class DrsV2800Tests : FunSpec({

    // ------------------------------------------------------------------
    // 1) DrsThemeBackground — pure contract
    // ------------------------------------------------------------------

    test("dimness maps to a clamped scrim alpha in the 0..0.8 band") {
        DrsThemeBackground.dimnessToAlpha(0) shouldBe 0f
        DrsThemeBackground.dimnessToAlpha(30) shouldBe 0.3f
        DrsThemeBackground.dimnessToAlpha(DrsThemeBackground.MAX_DIMNESS) shouldBe 0.8f
        // out-of-range values degrade to readable bounds, never throw
        DrsThemeBackground.dimnessToAlpha(-7) shouldBe 0f
        DrsThemeBackground.dimnessToAlpha(100) shouldBe 0.8f
    }

    test("stored names must be exactly what the importer produces") {
        DrsThemeBackground.isSafeStoredName("abcdef0123456789.jpg") shouldBe true
        DrsThemeBackground.isSafeStoredName("0123456789abcdef.webp") shouldBe true
        DrsThemeBackground.isSafeStoredName(null) shouldBe false
        DrsThemeBackground.isSafeStoredName("") shouldBe false
        // traversal / separators / whitespace can never pass
        DrsThemeBackground.isSafeStoredName("../evil.jpg") shouldBe false
        DrsThemeBackground.isSafeStoredName("sub/dir.jpg") shouldBe false
        DrsThemeBackground.isSafeStoredName("ab.jpg") shouldBe false
        DrsThemeBackground.isSafeStoredName(" ".repeat(16) + ".jpg") shouldBe false
        DrsThemeBackground.isSafeStoredName("a".repeat(65)) shouldBe false
        DrsThemeBackground.isSafeStoredName("ABCDEF0123456789.jpg") shouldBe false
    }

    test("extension falls back honestly for unknown types") {
        DrsThemeBackground.extensionOf("photo.JPG") shouldBe "jpg"
        DrsThemeBackground.extensionOf("shot.webp") shouldBe "webp"
        DrsThemeBackground.extensionOf("mystery.heic") shouldBe "png"
        DrsThemeBackground.extensionOf(null) shouldBe "png"
        DrsThemeBackground.extensionOf("no-extension") shouldBe "png"
    }

    test("the drsimg URI scheme is anchored at the root") {
        DrsThemeBackground.uriFor("abcdef0123456789.jpg") shouldBe "drsimg:/abcdef0123456789.jpg"
        DrsThemeBackground.URI_SCHEME shouldBe "drsimg"
    }

    // ------------------------------------------------------------------
    // 2) SnyggUriValue — the drsimg scheme survives (de)serialization
    // ------------------------------------------------------------------

    test("SnyggUriValue round-trips a drsimg URI") {
        val serialized = SnyggUriValue.serialize(SnyggUriValue("drsimg:/abcdef0123456789.jpg")).getOrThrow()
        val back = SnyggUriValue.deserialize(serialized).getOrThrow()
        back shouldBe SnyggUriValue("drsimg:/abcdef0123456789.jpg")
    }

    test("SnyggUriValue still round-trips the flex scheme") {
        val serialized = SnyggUriValue.serialize(SnyggUriValue("flex:/images/bg.png")).getOrThrow()
        SnyggUriValue.deserialize(serialized).getOrThrow() shouldBe SnyggUriValue("flex:/images/bg.png")
    }

    // ------------------------------------------------------------------
    // 3) DrsThemeBackgroundPatcher — in-memory, total, structure-preserving
    // ------------------------------------------------------------------

    test("patcher injects the background image into a REAL bundled stylesheet") {
        val sheet = bundledStylesheet("org.drs.themes/stylesheets/drs_day.json")
        val originalWindow = sheet.rules[SnyggElementRule("window")] as? org.drs.lib.snygg.SnyggSinglePropertySet
        val patched = DrsThemeBackgroundPatcher.apply(sheet, "abcdef0123456789.jpg", 30)
        patched shouldNotBe sheet
        val window = patched.rules[SnyggElementRule("window")] as? org.drs.lib.snygg.SnyggSinglePropertySet
        window shouldNotBe null
        window!!.backgroundImage shouldBe SnyggUriValue("drsimg:/abcdef0123456789.jpg")
        // the theme author's own window properties survive the patch
        window.background shouldBe originalWindow!!.background
        window.foreground shouldBe originalWindow.foreground
    }

    test("patcher keeps the original stylesheet when there is nothing to inject") {
        val sheet = bundledStylesheet("org.drs.themes/stylesheets/drs_day.json")
        DrsThemeBackgroundPatcher.apply(sheet, null, 30).shouldBeSameInstanceAs(sheet)
        DrsThemeBackgroundPatcher.apply(sheet, "", 30).shouldBeSameInstanceAs(sheet)
        DrsThemeBackgroundPatcher.apply(sheet, "../evil.jpg", 30).shouldBeSameInstanceAs(sheet)
    }

    test("patched stylesheet survives a JSON round trip (render pipeline reads it)") {
        val sheet = bundledStylesheet("org.drs.themes/stylesheets/drs_day.json")
        val patched = DrsThemeBackgroundPatcher.apply(sheet, "abcdef0123456789.jpg", 30)
        val json = patched.toJson().getOrThrow()
        val reparsed = SnyggStylesheet.fromJson(json).getOrThrow()
        val window = reparsed.rules[SnyggElementRule("window")] as? org.drs.lib.snygg.SnyggSinglePropertySet
        window shouldNotBe null
        window!!.backgroundImage shouldBe SnyggUriValue("drsimg:/abcdef0123456789.jpg")
    }

    test("patcher adds a window rule to a stylesheet that lacks one") {
        val sheet = SnyggStylesheet.v2 {
            "key" {
                background = SnyggStaticColorValue(Color(0xFF112233))
            }
        }
        val patched = DrsThemeBackgroundPatcher.apply(sheet, "abcdef0123456789.png", 10)
        val window = patched.rules[SnyggElementRule("window")] as? org.drs.lib.snygg.SnyggSinglePropertySet
        window shouldNotBe null
        window!!.backgroundImage shouldBe SnyggUriValue("drsimg:/abcdef0123456789.png")
        // untouched rule stays untouched
        val key = patched.rules[SnyggElementRule("key")] as? org.drs.lib.snygg.SnyggSinglePropertySet
        key?.background shouldBe SnyggStaticColorValue(Color(0xFF112233))
    }
    // ------------------------------------------------------------------
    // 4) DrsExternalDictFormats — the two deterministic file formats
    // ------------------------------------------------------------------

    test("bundled-JSON shape parses with clamping, dedupe and word filtering") {
        val parsed = DrsExternalDictFormats.parse("""{"hello":200,"world":99,"bad word":50,"neg":-5,"huge":999,"":7}""")
        parsed.format shouldBe DrsExternalDictFormats.FORMAT_JSON
        parsed.words shouldBe linkedMapOf("hello" to 200, "world" to 99, "neg" to 1, "huge" to 255)
    }

    test("word,freq lines parse; comments and junk lines are skipped") {
        val text = """
            # my personal words
            hello
            world,20
            greetings,999
            spaced  word ,5
            notanumber,abc

        """.trimIndent()
        val parsed = DrsExternalDictFormats.parse(text)
        parsed.format shouldBe DrsExternalDictFormats.FORMAT_LINES
        // "notanumber,abc": the word survives, the unparseable frequency
        // degrades honestly to FREQ_DEFAULT — same decision as a missing freq.
        parsed.words shouldBe linkedMapOf(
            "hello" to DrsExternalDictFormats.FREQ_DEFAULT,
            "world" to 20,
            "greetings" to 255,
            "notanumber" to DrsExternalDictFormats.FREQ_DEFAULT,
        )
    }

    test("duplicates keep the higher frequency") {
        val parsed = DrsExternalDictFormats.parse("a,10\na,50\na,3")
        parsed.words["a"] shouldBe 50
    }

    test("a UTF-8 BOM does not break JSON detection") {
        val parsed = DrsExternalDictFormats.parse("\uFEFF{\"a\":1}")
        parsed.format shouldBe DrsExternalDictFormats.FORMAT_JSON
        parsed.words["a"] shouldBe 1
    }

    test("parse fails honestly on malformed JSON and on empty input") {
        runCatching { DrsExternalDictFormats.parse("{not json") }
            .exceptionOrNull()?.message shouldBe DrsExternalDictFormats.REASON_MALFORMED_JSON
        runCatching { DrsExternalDictFormats.parse("# only comments") }
            .exceptionOrNull()?.message shouldBe DrsExternalDictFormats.REASON_NO_WORDS
        runCatching { DrsExternalDictFormats.parse("   \n  ") }
            .exceptionOrNull()?.message shouldBe DrsExternalDictFormats.REASON_NO_WORDS
    }

    test("word acceptance is strict: single tokens up to 40 chars") {
        DrsExternalDictFormats.isAcceptableWord("hello") shouldBe true
        DrsExternalDictFormats.isAcceptableWord("") shouldBe false
        DrsExternalDictFormats.isAcceptableWord("two words") shouldBe false
        DrsExternalDictFormats.isAcceptableWord("a".repeat(40)) shouldBe true
        DrsExternalDictFormats.isAcceptableWord("a".repeat(41)) shouldBe false
    }

    test("the per-file word cap holds") {
        val text = (0 until DrsExternalDictFormats.MAX_WORDS_PER_FILE + 1)
            .joinToString("\n") { "w$it" }
        val parsed = DrsExternalDictFormats.parse(text)
        parsed.words.size shouldBe DrsExternalDictFormats.MAX_WORDS_PER_FILE
    }

    // ------------------------------------------------------------------
    // 5) DrsExternalDictMerger — extend, never replace
    // ------------------------------------------------------------------

    test("an empty external map returns the base dictionary untouched") {
        val base = mapOf("كلمة" to 128)
        DrsExternalDictMerger.merge(base, emptyMap()).shouldBeSameInstanceAs(base)
    }

    test("external words extend the base and duplicates keep the max") {
        val base = mapOf("existing" to 100)
        val external = mapOf("existing" to 50, "newcomer" to 20)
        val merged = DrsExternalDictMerger.merge(base, external)
        merged.size shouldBe 2
        merged["existing"] shouldBe 100 // never downgraded
        merged["newcomer"] shouldBe 20
    }

    // ------------------------------------------------------------------
    // 6) DrsExternalDictStore — the supported language set
    // ------------------------------------------------------------------

    test("supported languages are exactly the bundled dictionary languages plus English") {
        DrsExternalDictStore.SUPPORTED_LANGS shouldBe
            setOf("ar", "en", "fr", "de", "es", "it", "pt", "tr", "ru", "fa")
    }
})

/** Parses a REAL bundled theme stylesheet from the app assets (same pattern as DrsM2DesignTests). */
private fun bundledStylesheet(relativePath: String): SnyggStylesheet {
    val file = File("src/main/assets/ime/theme/$relativePath")
    require(file.exists()) { "Missing bundled stylesheet asset: ${file.path}" }
    return SnyggStylesheet.fromJson(file.readText()).getOrThrow()
}
