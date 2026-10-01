/*
 * Copyright (C) 2025 The DRS Smart Keyboard Project
 */

package com.drs.smartkeyboard.lib.ext

import io.kotest.core.spec.style.FunSpec
import io.kotest.datatest.withData
import io.kotest.matchers.shouldBe

class ExtensionAuthorTest : FunSpec({
    val validAuthorPairs = listOf(
        "Jane Doe" to ExtensionMaintainer(name = "Jane Doe"),
        "jane123" to ExtensionMaintainer(name = "jane123"),
        "__jane__" to ExtensionMaintainer(name = "__jane__"),
        "jane.doe" to ExtensionMaintainer(name = "jane.doe"),
        "Jane Doe <jane.doe@gmail.com>" to ExtensionMaintainer(name = "Jane Doe", email = "jane.doe@gmail.com"),
        "Jane Doe (jane-doe.com)" to ExtensionMaintainer(name = "Jane Doe", url = "jane-doe.com"),
        "Jane Doe <jane.doe@gmail.com> (jane-doe.com)" to ExtensionMaintainer(name = "Jane Doe", email = "jane.doe@gmail.com", url = "jane-doe.com"),
    )

    context("ExtensionAuthor.from()") {
        context("with valid, well-formatted input") {
            withData(validAuthorPairs) { (authorStr, authorObj) ->
                ExtensionMaintainer.from(authorStr) shouldBe authorObj
            }
        }

        context("with valid, ill-formatted input") {
            withData(
                "  Jane Doe " to ExtensionMaintainer(name = "Jane Doe"),
                " jane123" to ExtensionMaintainer(name = "jane123"),
                "  Jane Doe    <jane.doe@gmail.com>     " to ExtensionMaintainer(name = "Jane Doe", email = "jane.doe@gmail.com"),
            ) { (authorStr, authorObj) ->
                ExtensionMaintainer.from(authorStr) shouldBe authorObj
            }
        }

        context("With invalid input") {
            withData(
                nameFn = { "`$it`" },
                "",
                " ",
                "<jane.doe@gmail.com>",
                " <jane.doe@gmail.com>",
                "<jane.doe@gmail.com> (jane-doe.com)",
                "Jane Doe <<jane.doe@gmail.com>> ((jane-doe.com))",
                "Jane Doe <jane.doe@gmail.com) (jane-doe.com)",
            ) { authorStr ->
                ExtensionMaintainer.from(authorStr) shouldBe null
            }
        }
    }

    context("Test ExtensionAuthor.toString()") {
        withData(validAuthorPairs) { (authorStr, authorObj) ->
            authorObj.toString() shouldBe authorStr
        }
    }
})
