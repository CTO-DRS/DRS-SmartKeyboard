/*
 * Copyright (C) 2025 The DRS Smart Keyboard Project
 */

package org.drs.lib.kotlin

import io.kotest.core.spec.style.FunSpec
import io.kotest.datatest.withData
import io.kotest.matchers.shouldBe

class MimeTypeFilterTest : FunSpec({
    context("basic matching") {
        context("valid matches") {
            withData(
                "font/woff2",
                "image/png",
                "application/x-font-otf",
            ) { mimeType ->
                val filter = mimeTypeFilterOf(mimeType)
                filter.matches(mimeType) shouldBe true
            }
        }

        test("null does not match") {
            val filter = mimeTypeFilterOf("image/png")
            filter.matches(null) shouldBe false
        }

        test("empty string does not match") {
            val filter = mimeTypeFilterOf("image/png")
            filter.matches("") shouldBe false
        }

        test("blank string does not match") {
            val filter = mimeTypeFilterOf("image/png")
            filter.matches("   ") shouldBe false
        }
    }

    context("wildcard matching") {
        context("should match type=any subtype=any") {
            val filter = mimeTypeFilterOf("*/*")
            withData(
                "image/png",
                "image/jpeg",
                "font/woff2",
                "application/x-font-otf",
            ) { mimeType ->
                filter.matches(mimeType) shouldBe true
            }
        }

        context("should not match type=any subtype=any") {
            val filter = mimeTypeFilterOf("*/*")
            withData(
                nameFn = { "`$it`" },
                "",
                "   ",
                "/",
                "   /",
                "/    ",
                "image/",
                "/jpeg",
                "image/   ",
                "   /jpeg",
                "image/png/jpeg",
                "image-jpeg",
            ) { mimeType ->
                filter.matches(mimeType) shouldBe false
            }
        }

        context("should match type=image subtype=any") {
            val filter = mimeTypeFilterOf("image/*")
            withData(
                "image/png",
                "image/jpeg",
            ) { mimeType ->
                filter.matches(mimeType) shouldBe true
            }
        }

        test("legacy otf file should work with wildcard filters") {
            // Workaround for MimeTypeFilter edge case (regex alternation trimming)
            val filter = mimeTypeFilterOf(
                "font/*",
                "application/font-*",
                "application/x-font-*",
                "application/vnd.ms-fontobject",
            )
            filter.matches("application/x-font-otf") shouldBe true
        }

        test("should match type=any subtype=font-any") {
            val filter = mimeTypeFilterOf(
                "*/x-font-*",
            )
            filter.matches("application/x-font-otf") shouldBe true
        }

        test("should match type=application subtype=any-font-any") {
            val filter = mimeTypeFilterOf(
                "application/*-font-*",
            )
            filter.matches("application/x-font-otf") shouldBe true
        }

        test("should match type=any-application-any subtype=any-font-any") {
            val filter = mimeTypeFilterOf(
                "*-application-*/*-font-*",
            )
            filter.matches("x-application-custom/x-font-otf") shouldBe true
        }
    }
})
