/*
 * Copyright (C) 2026 The DRS Smart Keyboard Project
 */

package com.drs.smartkeyboard.lib

import java.util.Locale
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

/**
 * Contract tests for [DrsLocale] tag parsing/building and the capitalization /
 * auto-space language whitelists that steer input behavior per locale.
 */
class DrsLocaleTagTest : FunSpec({

    context("tag parsing and building") {
        test("language-only tags round-trip") {
            DrsLocale.fromTag("en").languageTag() shouldBe "en"
        }

        test("locale tags with underscore delimiters build dashed language tags") {
            DrsLocale.fromTag("en_US").languageTag() shouldBe "en-US"
            DrsLocale.fromTag("pt_BR").languageTag() shouldBe "pt-BR"
        }

        test("three-segment tags keep the variant") {
            DrsLocale.fromTag("en-US-posix").languageTag() shouldBe "en-US-posix"
        }

        test("four-segment tags silently drop everything past the variant") {
            // fromTag takes lc[0], lc[1], lc[2] for >= 3 segments — extra
            // segments are dropped by design.
            DrsLocale.fromTag("en-US-posix-x").languageTag() shouldBe "en-US-posix"
        }

        test("mixed underscore and dash delimiters are accepted") {
            DrsLocale.fromTag("en-US_posix").languageTag() shouldBe "en-US-posix"
            DrsLocale.fromTag("pt_BR-x").localeTag() shouldBe "pt_BR_x"
        }

        test("locale tags round-trip through localeTag") {
            DrsLocale.fromTag("en_US").localeTag() shouldBe "en_US"
        }

        test("blank country omits the delimiter when building tags (quirk pin)") {
            // buildLocaleString only appends the delimiter when the preceding
            // component is non-blank, so a blank country glues language and
            // variant together. Pinned as the actual contract.
            DrsLocale.from("en", "", "US").languageTag() shouldBe "enUS"
        }

        test("wrapping a java.util.Locale matches the equivalent tag locale") {
            DrsLocale.from(Locale.US) shouldBe DrsLocale.fromTag("en_US")
        }

        test("ROOT has empty language, country and variant") {
            DrsLocale.ROOT.language shouldBe ""
            DrsLocale.ROOT.languageTag() shouldBe ""
        }
    }

    context("supportsCapitalization whitelist") {
        test("ideographic and abugida scripts have no capitalization") {
            DrsLocale.fromTag("zh").supportsCapitalization shouldBe false
            DrsLocale.fromTag("ko").supportsCapitalization shouldBe false
            DrsLocale.fromTag("th").supportsCapitalization shouldBe false
            DrsLocale.fromTag("bn").supportsCapitalization shouldBe false
            DrsLocale.fromTag("hi").supportsCapitalization shouldBe false
        }

        test("alphabetic scripts support capitalization") {
            DrsLocale.fromTag("ar").supportsCapitalization shouldBe true
            DrsLocale.fromTag("en").supportsCapitalization shouldBe true
            DrsLocale.fromTag("ru").supportsCapitalization shouldBe true
        }

        test("ja is treated as capitalization-capable by the hard-coded list (pin)") {
            // The whitelist only excludes zh/ko/th/bn/hi — Japanese falls into
            // the `else -> true` branch (see the TODO in DrsLocale). Pinned as
            // the actual contract.
            DrsLocale.fromTag("ja").supportsCapitalization shouldBe true
        }
    }

    context("supportsAutoSpace whitelist") {
        test("CJK and Thai scripts get no automatic spacing") {
            DrsLocale.fromTag("zh").supportsAutoSpace shouldBe false
            DrsLocale.fromTag("ko").supportsAutoSpace shouldBe false
            DrsLocale.fromTag("ja").supportsAutoSpace shouldBe false
            DrsLocale.fromTag("th").supportsAutoSpace shouldBe false
        }

        test("jp (non-ISO code) still gets auto spacing (v1.28.0 regression guard)") {
            // DRS v1.28.0 audit fix: the no-auto-space contract now lists the
            // real "ja" code; the legacy "jp" code therefore must NOT disable
            // auto spacing.
            DrsLocale.fromTag("jp").supportsAutoSpace shouldBe true
        }

        test("latin scripts get auto spacing") {
            DrsLocale.fromTag("en").supportsAutoSpace shouldBe true
            DrsLocale.fromTag("ar").supportsAutoSpace shouldBe true
        }
    }
})
