/*
 * Copyright (C) 2026 The DRS Smart Keyboard Project
 */

package com.drs.smartkeyboard.lib.ext

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeInstanceOf

/**
 * Contract tests for [ExtensionMeta] parsing (the `extension.json` payload of
 * every `.flex` package) and [ExtensionMaintainer] string parsing. Uses the
 * same [Json] configuration knobs as [ExtensionManager]'s ExtensionJsonConfig
 * (encodeDefaults=false, ignoreUnknownKeys=true, isLenient=true).
 */
@OptIn(ExperimentalSerializationApi::class)
class ExtensionMetaTest : FunSpec({

    val json = Json {
        encodeDefaults = false
        ignoreUnknownKeys = true
        isLenient = true
    }

    val fullMetaJson = """
        {
          "id": "org.drs.sample",
          "version": "1.0.0",
          "title": "Sample",
          "description": "A sample extension",
          "keywords": ["sample", "test"],
          "homepage": "https://example.com",
          "issueTracker": "https://example.com/issues",
          "maintainers": ["John Doe <john@example.com> (https://example.com/~john)"],
          "license": "Apache-2.0"
        }
    """.trimIndent()

    test("a fully populated extension.json decodes into all fields") {
        val meta = json.decodeFromString(ExtensionMeta.serializer(), fullMetaJson)

        meta.id shouldBe "org.drs.sample"
        meta.version shouldBe "1.0.0"
        meta.title shouldBe "Sample"
        meta.description shouldBe "A sample extension"
        meta.keywords shouldBe listOf("sample", "test")
        meta.homepage shouldBe "https://example.com"
        meta.issueTracker shouldBe "https://example.com/issues"
        meta.maintainers shouldBe listOf(
            ExtensionMaintainer(
                name = "John Doe",
                email = "john@example.com",
                url = "https://example.com/~john",
            ),
        )
        meta.license shouldBe "Apache-2.0"
    }

    test("the legacy 'authors' key is accepted for maintainers") {
        val legacyJson = """
            {
              "id": "org.drs.legacy",
              "version": "0.1.0",
              "title": "Legacy",
              "authors": ["Jane"],
              "license": "MIT"
            }
        """.trimIndent()

        val meta = json.decodeFromString(ExtensionMeta.serializer(), legacyJson)

        meta.maintainers shouldBe listOf(ExtensionMaintainer(name = "Jane"))
    }

    test("missing optional fields decode to null defaults without failing") {
        val minimalJson = """
            {
              "id": "org.drs.minimal",
              "version": "1.2.3",
              "title": "Minimal",
              "maintainers": ["Alice"],
              "license": "MIT"
            }
        """.trimIndent()

        val meta = json.decodeFromString(ExtensionMeta.serializer(), minimalJson)

        meta.description.shouldBeNull()
        meta.keywords.shouldBeNull()
        meta.homepage.shouldBeNull()
        meta.issueTracker.shouldBeNull()
    }

    test("unknown top-level keys are ignored") {
        val withUnknown = """
            {
              "id": "org.drs.future",
              "version": "1.0.0",
              "title": "Future",
              "maintainers": ["Alice"],
              "license": "MIT",
              "futureField": 42,
              "anotherUnknown": {"nested": true}
            }
        """.trimIndent()

        val meta = json.decodeFromString(ExtensionMeta.serializer(), withUnknown)

        meta.id shouldBe "org.drs.future"
        meta.title shouldBe "Future"
    }

    test("a missing required field fails honestly") {
        val noLicense = """
            {
              "id": "org.drs.broken",
              "version": "1.0.0",
              "title": "Broken",
              "maintainers": ["Alice"]
            }
        """.trimIndent()

        val result = Result.runCatching {
            json.decodeFromString(ExtensionMeta.serializer(), noLicense)
        }

        result.exceptionOrNull().shouldBeInstanceOf<SerializationException>()
    }

    test("encode then decode round-trip preserves the meta") {
        val meta = json.decodeFromString(ExtensionMeta.serializer(), fullMetaJson)

        val encoded = json.encodeToString(ExtensionMeta.serializer(), meta)
        val decoded = json.decodeFromString(ExtensionMeta.serializer(), encoded)

        decoded shouldBe meta
    }

    test("maintainer string parses name, email and url") {
        val maintainer = ExtensionMaintainer.from("John Doe <john@example.com> (https://example.com)")

        maintainer.shouldNotBeNull()
        maintainer.name shouldBe "John Doe"
        maintainer.email shouldBe "john@example.com"
        maintainer.url shouldBe "https://example.com"
    }

    test("maintainer string with name only leaves email and url null") {
        val maintainer = ExtensionMaintainer.from("Jane")

        maintainer.shouldNotBeNull()
        maintainer.name shouldBe "Jane"
        maintainer.email.shouldBeNull()
        maintainer.url.shouldBeNull()
    }

    test("maintainer parsing rejects malformed strings honestly") {
        ExtensionMaintainer.from("").shouldBeNull()
        ExtensionMaintainer.from("   ").shouldBeNull()
        ExtensionMaintainer.from("a<b").shouldBeNull()
        ExtensionMaintainer.from("<john@example.com>").shouldBeNull()
    }

    test("maintainer stringifies back into the human-readable format") {
        val maintainer = ExtensionMaintainer(
            name = "John Doe",
            email = "john@example.com",
            url = "https://example.com",
        )

        maintainer.toString().shouldContain("John Doe")
        maintainer.toString().shouldContain("<john@example.com>")
        maintainer.toString().shouldContain("(https://example.com)")
    }
})
