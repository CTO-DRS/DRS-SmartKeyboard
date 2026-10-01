/*
 * Copyright (C) 2021-2025 The DRS Smart Keyboard Project
 * Copyright (C) 2025 DRS Smart Keyboard contributors
 */

package com.drs.smartkeyboard.drs

import androidx.annotation.StringRes
import androidx.compose.ui.graphics.Color
import com.drs.smartkeyboard.R

/**
 * Complete specification of one DRS user system (نظام).
 *
 * The three systems are first-class products inside DRS Smart Keyboard:
 * every system carries its own visual identity (accent colors), its own
 * default keyboard configuration (profile defaults), its own curated
 * feature list, and its own copy shown across the whole app. Whenever a
 * feature is added or improved anywhere in the project, all three systems
 * are revisited here so each one stays complete and coherent on its own.
 */
data class DrsSystemSpec(
    /** The user path this system is built on. */
    val path: DrsUserPath,

    /** Identity accent used in day theme surfaces. */
    val accent: Color,

    /** Identity accent used in night theme surfaces (softer). */
    val accentNight: Color,

    /** Short slogan shown under the system name. */
    @StringRes val taglineRes: Int,

    /** One-line description of who this system is for. */
    @StringRes val bodyRes: Int,

    /** Curated feature bullets shown in onboarding and control center. */
    @StringRes val feature1Res: Int,
    @StringRes val feature2Res: Int,
    @StringRes val feature3Res: Int,
    @StringRes val feature4Res: Int,

    // ----- default keyboard configuration applied when this system activates -----
    val numberRow: Boolean,
    val techStripEnabled: Boolean,
    val suggestionsEnabled: Boolean,
    val clipboardHistoryEnabled: Boolean,
    val audioFeedbackEnabled: Boolean,
    val hapticFeedbackEnabled: Boolean,

    // ----- system-owned package of smart accessories (one tap to apply) -----
    val accessoryPreset: DrsAccessoryPreset,
)

/**
 * The system-owned package of smart accessories (الملحقات الذكية).
 *
 * Every DRS system carries its own accessory preset so the accessories
 * screen can apply a complete, coherent package of real keyboard modules
 * in one tap. Values are tuned per system: the normal system favors a
 * rich chat experience, the technical system strips away chat-flavored
 * extras, and the hybrid system enables everything including glide.
 */
data class DrsAccessoryPreset(
    val smartbarEnabled: Boolean,
    val glideEnabled: Boolean,
    val glideTrail: Boolean,
    val nextWordEnabled: Boolean,
    val emojiSuggestions: Boolean,
    val emojiHistory: Boolean,
    val keyPopups: Boolean,
)

/**
 * The three DRS systems registry. This is the single source of truth for
 * everything that differs between systems; UI screens and managers read
 * from here so a change in one place updates all three systems everywhere.
 */
object DrsSystems {

    // ---------------------------------------------------------------
    // النظام العادي — simplicity first: clean typing for everyday apps
    // ---------------------------------------------------------------
    val NORMAL = DrsSystemSpec(
        path = DrsUserPath.NORMAL,
        accent = Color(0xFF0E9488),
        accentNight = Color(0xFF2DD4BF),
        taglineRes = R.string.drs__system__normal_tagline,
        bodyRes = R.string.drs__path_normal_body,
        feature1Res = R.string.drs__system__normal_f1,
        feature2Res = R.string.drs__system__normal_f2,
        feature3Res = R.string.drs__system__normal_f3,
        feature4Res = R.string.drs__system__normal_f4,
        numberRow = false,
        techStripEnabled = false,
        suggestionsEnabled = true,
        clipboardHistoryEnabled = true,
        audioFeedbackEnabled = true,
        hapticFeedbackEnabled = true,
        accessoryPreset = DrsAccessoryPreset(
            smartbarEnabled = true,
            glideEnabled = false,
            glideTrail = false,
            nextWordEnabled = true,
            emojiSuggestions = true,
            emojiHistory = true,
            keyPopups = true,
        ),
    )

    // ---------------------------------------------------------------
    // النظام التقني — power tools: coding symbols, navigation, snippets
    // ---------------------------------------------------------------
    val TECHNICAL = DrsSystemSpec(
        path = DrsUserPath.TECHNICAL,
        accent = Color(0xFF4F5BD5),
        accentNight = Color(0xFF8B93F8),
        taglineRes = R.string.drs__system__technical_tagline,
        bodyRes = R.string.drs__path_technical_body,
        feature1Res = R.string.drs__system__technical_f1,
        feature2Res = R.string.drs__system__technical_f2,
        feature3Res = R.string.drs__system__technical_f3,
        feature4Res = R.string.drs__system__technical_f4,
        numberRow = true,
        techStripEnabled = true,
        suggestionsEnabled = true,
        clipboardHistoryEnabled = true,
        audioFeedbackEnabled = false,
        hapticFeedbackEnabled = true,
        accessoryPreset = DrsAccessoryPreset(
            smartbarEnabled = true,
            glideEnabled = false,
            glideTrail = false,
            nextWordEnabled = true,
            emojiSuggestions = false,
            emojiHistory = true,
            keyPopups = true,
        ),
    )

    // ---------------------------------------------------------------
    // النظام المتكامل — both worlds together with smart adaptation
    // ---------------------------------------------------------------
    val HYBRID = DrsSystemSpec(
        path = DrsUserPath.HYBRID,
        accent = Color(0xFFB45309),
        accentNight = Color(0xFFFBBF24),
        taglineRes = R.string.drs__system__hybrid_tagline,
        bodyRes = R.string.drs__path_hybrid_body,
        feature1Res = R.string.drs__system__hybrid_f1,
        feature2Res = R.string.drs__system__hybrid_f2,
        feature3Res = R.string.drs__system__hybrid_f3,
        feature4Res = R.string.drs__system__hybrid_f4,
        numberRow = true,
        techStripEnabled = true,
        suggestionsEnabled = true,
        clipboardHistoryEnabled = true,
        audioFeedbackEnabled = true,
        hapticFeedbackEnabled = true,
        accessoryPreset = DrsAccessoryPreset(
            smartbarEnabled = true,
            glideEnabled = true,
            glideTrail = true,
            nextWordEnabled = true,
            emojiSuggestions = true,
            emojiHistory = true,
            keyPopups = true,
        ),
    )

    /** All systems in display order (normal, technical, hybrid). */
    val ALL: List<DrsSystemSpec> = listOf(NORMAL, TECHNICAL, HYBRID)

    /** Resolves the spec for a raw stored path name, defaulting to NORMAL. */
    fun specOfName(pathName: String): DrsSystemSpec {
        return ALL.firstOrNull { it.path.name == pathName } ?: NORMAL
    }

    /** Resolves the spec for a [DrsUserPath]. */
    fun specOf(path: DrsUserPath): DrsSystemSpec {
        return ALL.firstOrNull { it.path == path } ?: NORMAL
    }

    /** Resolves the spec matching a profile's stored path tag. */
    fun specOfProfile(profile: DrsProfile): DrsSystemSpec? {
        return ALL.firstOrNull { it.path.name == profile.path }
    }

    /**
     * Builds a fresh system profile from this system's defaults. Each
     * system owns one dedicated profile so switching systems switches the
     * whole keyboard configuration in one step, and each system keeps its
     * own tuning even after the user experiments with another system.
     */
    fun profileFor(path: DrsUserPath, id: String = ""): DrsProfile {
        val spec = specOf(path)
        return DrsProfile(
            id = id,
            name = when (path) {
                DrsUserPath.NORMAL -> "Personal"
                DrsUserPath.TECHNICAL -> "Technical"
                DrsUserPath.HYBRID -> "Hybrid"
            },
            path = path.name,
            dayThemeId = DrsProfileManager.DEFAULT_DAY_THEME,
            nightThemeId = DrsProfileManager.DEFAULT_NIGHT_THEME,
            numberRow = spec.numberRow,
            techStripEnabled = spec.techStripEnabled,
            suggestionsEnabled = spec.suggestionsEnabled,
            clipboardHistoryEnabled = spec.clipboardHistoryEnabled,
            audioFeedbackEnabled = spec.audioFeedbackEnabled,
            hapticFeedbackEnabled = spec.hapticFeedbackEnabled,
        )
    }

    /**
     * Returns the system's stored profile when it exists, or creates it
     * from the system defaults. Guarantees every system always has its own
     * ready-to-activate profile with its own settings preserved.
     */
    fun ensureProfile(state: DrsState, path: DrsUserPath): DrsProfile {
        val existing = state.profiles.firstOrNull { it.path == path.name }
        return existing ?: profileFor(path)
    }
}
