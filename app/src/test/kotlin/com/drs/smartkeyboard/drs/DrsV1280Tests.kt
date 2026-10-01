/*
 * Copyright (C) 2025-2026 The DRS Smart Keyboard Project
 */

package com.drs.smartkeyboard.drs

import com.drs.smartkeyboard.app.drsupdater.DrsUpdateCenter
import com.drs.smartkeyboard.ime.theme.ThemeManager
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.maps.shouldContain
import io.kotest.matchers.maps.shouldHaveSize
import io.kotest.matchers.shouldBe
import java.time.LocalTime

/**
 * DRS v1.28.0 audit round — regression contracts for the fixes shipped by
 * the full-project audit:
 *  1. The SHA256SUMS parser must key FILENAME -> HASH (the old inverted
 *     hash->filename map made the integrity gate fail closed forever).
 *  2. cycleTheme's night-slot decision must agree with ThemeDayWindow
 *     (midnight-wrapping schedules were previously inconsistent).
 */
class DrsV1280Tests : FunSpec({

    context("SHA256SUMS parsing — filename→hash contract") {
        val hash1 = "a".repeat(64)
        val hash2 = "B".repeat(64) // uppercase must survive as the authority
        val standard = "$hash1  DRS-Smart-Keyboard-v1.0.0.apk\n" +
            "$hash2 *SHA256SUMS.txt\n"

        test("standard sha256sum output keys by lowercase filename") {
            val map = DrsUpdateCenter.parseChecksumLines(standard)
            map shouldHaveSize 2
            map shouldContain ("drs-smart-keyboard-v1.0.0.apk" to hash1)
            map shouldContain ("sha256sums.txt" to hash2.lowercase())
        }

        test("verifyDownload-style lookup finds the asset by name") {
            val map = DrsUpdateCenter.parseChecksumLines(standard)
            val expected = map["DRS-Smart-Keyboard-v1.0.0.apk".lowercase()]
            expected shouldBe hash1
        }

        test("filename-first layout is also understood (defensive)") {
            val body = "$hash1 apk-file.apk\n"
            val map = DrsUpdateCenter.parseChecksumLines(body)
            map shouldContain ("apk-file.apk" to hash1)
        }

        test("binary-mode * prefix is stripped from filenames") {
            val body = "$hash1 *binary.apk\n"
            val map = DrsUpdateCenter.parseChecksumLines(body)
            map shouldContain ("binary.apk" to hash1)
        }

        test("malformed lines are skipped, not fatal") {
            val body = "garbage\n\n$hash1  ok.apk\nshort  line\n"
            val map = DrsUpdateCenter.parseChecksumLines(body)
            map shouldHaveSize 1
            map shouldContain ("ok.apk" to hash1)
        }
    }

    context("ThemeDayWindow — midnight wrap used by cycleTheme") {
        fun slot(sunrise: String, sunset: String, now: String): Boolean {
            val sr = LocalTime.parse(sunrise)
            val ss = LocalTime.parse(sunset)
            val t = LocalTime.parse(now)
            return ThemeManager.ThemeDayWindow.isDaytime(t, sr, ss)
        }

        test("normal schedule: day between sunrise and sunset") {
            slot("06:00", "18:00", "12:00").shouldBe(true)
            slot("06:00", "18:00", "03:00").shouldBe(false)
            slot("06:00", "18:00", "23:00").shouldBe(false)
        }

        test("night-shift schedule (sunrise 22:00, sunset 06:00) wraps midnight") {
            // The day window [22:00 → 06:00] crosses midnight: 03:00 lies
            // INSIDE it (this is the v1.21.0 wrap contract), noon lies outside.
            slot("22:00", "06:00", "03:00").shouldBe(true)
            slot("22:00", "06:00", "12:00").shouldBe(false)
            slot("22:00", "06:00", "23:00").shouldBe(true)
        }

        test("degenerate equal pair falls back to day") {
            slot("08:00", "08:00", "08:00").shouldBe(true)
        }

        test("boundaries are inclusive") {
            slot("06:00", "18:00", "06:00").shouldBe(true)
            slot("06:00", "18:00", "18:00").shouldBe(true)
        }
    }
})
