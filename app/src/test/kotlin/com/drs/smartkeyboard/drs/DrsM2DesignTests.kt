/*
 * Copyright (C) 2025 The DRS Smart Keyboard Project
 */

package com.drs.smartkeyboard.drs

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import java.io.File

/**
 * DRS M2.1–M2.6 — the Design-2030 contracts: the Halo themes (generated
 * assets verified against the manifest), the key-tap pulse timings, the
 * smartbar corner-shape sanitizer, the theme save generation, the
 * growth-only adaptive row scale, and the onboarding resume machine.
 */
class DrsM2DesignTests : FunSpec({

    context("M2.1 — Halo 2030 themes") {
        val sheetDay = File("src/main/assets/ime/theme/org.drs.themes.drs/stylesheets/drs_halo_day.json")
        val sheetNight = File("src/main/assets/ime/theme/org.drs.themes.drs/stylesheets/drs_halo_night.json")
        val manifest = File("src/main/assets/ime/theme/org.drs.themes.drs/extension.json")

        test("both Halo stylesheets exist with 97 real rules each") {
            sheetDay.shouldExist()
            sheetNight.shouldExist()
            val day = Json.parseToJsonElement(sheetDay.readText()).jsonObject
            val night = Json.parseToJsonElement(sheetNight.readText()).jsonObject
            val dayRules = day.keys.count { it != "\$schema" && it != "@defines" }
            val nightRules = night.keys.count { it != "\$schema" && it != "@defines" }
            dayRules shouldBe 97
            nightRules shouldBe 97
        }

        test("the Halo palette is the Design-2030 teal (defines swapped, structure intact)") {
            val day = Json.parseToJsonElement(sheetDay.readText()).jsonObject
            val defines = day["@defines"]!!.jsonObject
            defines["--primary"]!!.toString() shouldBe "\"#0E9488\""
            val night = Json.parseToJsonElement(sheetNight.readText()).jsonObject
            night["@defines"]!!.jsonObject["--primary"]!!.toString() shouldBe "\"#14B8A6\""
        }

        test("both themes are registered in the manifest (version 1.2.0, 14 themes)") {
            val manifestJson = Json.parseToJsonElement(manifest.readText()).jsonObject
            val meta = manifestJson["meta"]!!.jsonObject
            meta["version"]!!.toString() shouldBe "\"1.2.0\""
            val themes = manifestJson["themes"]!!.toString()
            themes.contains("drs_halo_day") shouldBe true
            themes.contains("drs_halo_night") shouldBe true
            manifestJson["themes"]!!.toString().split("\"id\"").size shouldBe 15 // 14 themes + split offset
        }
    }

    context("M2.2 — DrsMotion key pulse") {
        test("the pulse contract: 0.94 pressed, 1.0 idle, asymmetric timing") {
            DrsMotion.scaleFor(pressed = true) shouldBe 0.94f
            DrsMotion.scaleFor(pressed = false) shouldBe 1f
            DrsMotion.durationFor(pressed = true) shouldBe 60
            DrsMotion.durationFor(pressed = false) shouldBe 140
        }

        test("the release is SLOWER than the press — the tactile asymmetry") {
            (DrsMotion.PULSE_RELEASE_DURATION_MS > DrsMotion.PULSE_DURATION_MS) shouldBe true
        }
    }

    context("M2.3 — DrsSmartbarShape") {
        test("the sanitizer clamps into 0..28") {
            DrsSmartbarShape.sanitize(-5) shouldBe 0
            DrsSmartbarShape.sanitize(0) shouldBe 0
            DrsSmartbarShape.sanitize(12) shouldBe 12
            DrsSmartbarShape.sanitize(99) shouldBe 28
        }

        test("the slider bounds match the sanitizer bounds") {
            // the slider min/max in DrsUnifiedSystemScreen use these constants
            DrsSmartbarShape.MIN_RADIUS_DP shouldBe 0
            DrsSmartbarShape.MAX_RADIUS_DP shouldBe 28
        }
    }

    context("M2.4 — DrsThemeService save generation") {
        test("notifyPublished is MONOTONIC and increments by exactly one") {
            val before = DrsThemeService.currentGeneration()
            val afterOne = DrsThemeService.notifyPublished()
            val afterTwo = DrsThemeService.notifyPublished()
            afterOne shouldBe before + 1
            afterTwo shouldBe afterOne + 1
        }

        test("didRecolor proves the keyboard re-colored from a (before, after) pair") {
            DrsThemeService.didRecolor(3, 4) shouldBe true
            DrsThemeService.didRecolor(4, 4) shouldBe false
        }
    }

    context("M2.5 — DrsAdaptiveLayout") {
        test("growth-only: small fonts never shrink the rows") {
            DrsAdaptiveLayout.rowScaleForFont(0.5f) shouldBe 1.0f
            DrsAdaptiveLayout.rowScaleForFont(0.85f) shouldBe 1.0f
            DrsAdaptiveLayout.rowScaleForFont(1.0f) shouldBe 1.0f
        }

        test("large fonts grow rows half-strength, capped at 1.15") {
            DrsAdaptiveLayout.rowScaleForFont(1.2f) shouldBe 1.1f
            DrsAdaptiveLayout.rowScaleForFont(1.3f) shouldBe 1.15f
            DrsAdaptiveLayout.rowScaleForFont(2.0f) shouldBe 1.15f
        }

        test("the sanitizer clamps into 1.0..1.15") {
            DrsAdaptiveLayout.sanitize(0.9f) shouldBe 1.0f
            DrsAdaptiveLayout.sanitize(1.5f) shouldBe 1.15f
        }
    }

    context("M2.6 — DrsOnboardingState") {
        test("a mid-flow exit resumes at the EXACT saved step") {
            DrsOnboardingState.resumeStep(false, 4, 11) shouldBe 4
            DrsOnboardingState.resumeStep(false, 9, 11) shouldBe 9
        }

        test("a fresh user and a completed onboarding both start clean") {
            DrsOnboardingState.resumeStep(false, 0, 11) shouldBe 0
            DrsOnboardingState.resumeStep(true, 4, 11) shouldBe 0
        }

        test("the DONE terminal step never re-enters the flow") {
            DrsOnboardingState.resumeStep(false, 10, 11) shouldBe 0
        }

        test("out-of-range saved steps sanitize to a clean start") {
            DrsOnboardingState.resumeStep(false, -3, 11) shouldBe 0
            DrsOnboardingState.resumeStep(false, 99, 11) shouldBe 0
        }

        test("isResumable agrees with resumeStep") {
            DrsOnboardingState.isResumable(false, 4, 11) shouldBe true
            DrsOnboardingState.isResumable(true, 4, 11) shouldBe false
            DrsOnboardingState.isResumable(false, 0, 11) shouldBe false
        }
    }
})

private fun File.shouldExist() {
    if (!isFile) throw AssertionError("expected file to exist: $this")
}
