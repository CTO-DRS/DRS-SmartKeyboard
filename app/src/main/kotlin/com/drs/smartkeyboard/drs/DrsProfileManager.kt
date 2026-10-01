/*
 * Copyright (C) 2021-2025 The DRS Smart Keyboard Project
 * Copyright (C) 2025 DRS Smart Keyboard contributors
 */

package com.drs.smartkeyboard.drs

import com.drs.smartkeyboard.app.DrsPreferenceStore
import com.drs.smartkeyboard.ime.core.Subtype
import com.drs.smartkeyboard.ime.core.SubtypeJsonConfig
import com.drs.smartkeyboard.lib.ext.ExtensionComponentName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.drs.lib.kotlin.tryOrNull
import java.util.UUID

/**
 * Applies a DRS profile to the underlying keyboard preferences. Profiles are
 * stored inside [DrsStore]; switching a profile only rewrites the affected
 * preference values - no user data is ever lost.
 */
object DrsProfileManager {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /**
     * DRS (D2): serializes profile applications. NOTE: kotlinx.coroutines
     * Mutex is NON-FAIR — under contention there is no arrival-order
     * guarantee. That is acceptable here: profile switches are user-paced,
     * and serialization (not fairness) is what keeps two racing
     * applyProfile calls from interleaving their store write and pref
     * writes.
     */
    private val applyMutex = Mutex()

    const val DEFAULT_DAY_THEME = "org.drs.themes:drs_day"
    const val DEFAULT_NIGHT_THEME = "org.drs.themes:drs_night"
    const val BORDERLESS_DAY_THEME = "org.drs.themes:drs_day_borderless"
    const val BORDERLESS_NIGHT_THEME = "org.drs.themes:drs_night_borderless"

    /**
     * Builds the default profile for a user path (used by onboarding).
     * Defaults come from [DrsSystems] - the single source of truth for the
     * three systems' own keyboard configuration.
     */
    fun defaultProfileFor(path: DrsUserPath): DrsProfile {
        return DrsSystems.profileFor(path, UUID.randomUUID().toString())
    }

    /**
     * Activates one of the three systems: guarantees the system owns its
     * own profile (created from the system defaults on first use), applies
     * it to the live keyboard, and stores the system as the user path.
     * Each system keeps its own tuning across switches - experimenting
     * with another system never destroys a system's configuration.
     *
     * DRS (D2): the userPath write rides the durable atomic path
     * ([DrsStore.updateNow]) and is ordered AFTER the profile application
     * inside one serialized coroutine, so a switch can never persist the
     * path while the profile write is still pending.
     */
    fun applySystem(path: DrsUserPath) {
        val profile = DrsSystems.ensureProfile(DrsStore.state.value, path)
        scope.launch {
            applyProfileInternal(profile)
            DrsStore.updateNow { s -> s.copy(userPath = path.name) }
        }
    }

    /**
     * Provisions a dedicated profile for every system that does not own
     * one yet (idempotent). Called when the control center opens so each
     * system always has its own ready-to-activate profile.
     */
    fun ensureSystemProfiles() {
        val state = DrsStore.state.value
        val missing = DrsUserPath.entries.any { path ->
            state.profiles.none { it.path == path.name }
        }
        if (!missing) return
        DrsStore.update { s ->
            val profiles = s.profiles.toMutableList()
            DrsUserPath.entries.forEach { path ->
                if (profiles.none { it.path == path.name }) {
                    profiles += DrsSystems.profileFor(path, UUID.randomUUID().toString())
                }
            }
            s.copy(profiles = profiles)
        }
    }

    /** Creates an empty custom profile based on another profile's settings. */
    fun customProfileFrom(base: DrsProfile, name: String): DrsProfile {
        return base.copy(id = UUID.randomUUID().toString(), name = name, path = "CUSTOM")
    }

    /**
     * Persists the profile and applies its settings to the live keyboard
     * preferences. The active theme ids are written through the same
     * preference path the theme manager observes, so the keyboard re-skins
     * immediately.
     *
     * DRS (D2): the whole application runs serialized under [applyMutex]
     * (see its KDoc: kotlinx Mutex, non-fair) with the store write on the
     * durable atomic path ([DrsStore.updateNow]) instead of the
     * fire-and-forget update.
     */
    fun applyProfile(profile: DrsProfile) {
        scope.launch { applyProfileInternal(profile) }
    }

    /** DRS (D2): the serialized core of [applyProfile]. */
    private suspend fun applyProfileInternal(profile: DrsProfile) {
        applyMutex.withLock {
            DrsStore.updateNow { state ->
                state.copy(
                    activeProfileId = profile.id,
                    profiles = if (state.profiles.any { it.id == profile.id }) {
                        state.profiles.map { if (it.id == profile.id) profile else it }
                    } else {
                        state.profiles + profile
                    },
                )
            }
            val prefs by DrsPreferenceStore
            prefs.keyboard.numberRow.set(profile.numberRow)
            prefs.suggestion.enabled.set(profile.suggestionsEnabled)
            prefs.clipboard.historyEnabled.set(profile.clipboardHistoryEnabled)
            prefs.inputFeedback.audioEnabled.set(profile.audioFeedbackEnabled)
            prefs.inputFeedback.hapticEnabled.set(profile.hapticFeedbackEnabled)
            runCatching {
                val day = tryOrNull { ExtensionComponentName.from(profile.dayThemeId) }
                val night = tryOrNull { ExtensionComponentName.from(profile.nightThemeId) }
                if (day != null) prefs.theme.dayThemeId.set(day)
                if (night != null) prefs.theme.nightThemeId.set(night)
            }
            // DRS M0.6 — apply the profile's characters-layout overrides to
            // the LIVE subtype list. The write goes through the exact
            // preference SubtypeManager collects interactively, so matching
            // languages re-lay-out immediately: a pure transform (ids and
            // ordering untouched) with an unparsable current value left as-is.
            if (profile.subtypeLayouts.isNotEmpty()) {
                val currentRaw = prefs.localization.subtypes.get()
                val transformed = transformSubtypesForProfile(currentRaw, profile.subtypeLayouts)
                if (transformed != currentRaw) {
                    prefs.localization.subtypes.set(transformed)
                }
            }
        }
    }

    /** Adds a newly created profile and activates it. */
    fun createAndApply(profile: DrsProfile) {
        // applyProfile already inserts the profile when absent - no separate
        // add step, so two racing updates can never duplicate an entry.
        applyProfile(profile)
    }

    /** Returns the currently active profile, or null when none exists yet. */
    fun activeProfile(state: DrsState): DrsProfile? {
        return state.profiles.firstOrNull { it.id == state.activeProfileId }
            ?: state.profiles.firstOrNull()
    }
}

/**
 * DRS M0.6 — the pure, JVM-testable core of the profile→subtypes
 * application: for every subtype whose primary language tag matches one
 * of [layoutOverrides] (CASE-INSENSITIVELY and with '-'/'_' normalized —
 * the test exposed that a stored "en-US" and an override "en_us" are the
 * same language), replaces the characters layout with the override's
 * component id. Everything else is preserved: subtype ids, list ordering,
 * all non-matching subtypes.
 *
 * An unparsable current value returns unchanged — a broken preference is
 * never "fixed" by silently rewriting it with a partial list.
 */
fun transformSubtypesForProfile(listRaw: String, layoutOverrides: Map<String, String>): String {
    if (layoutOverrides.isEmpty() || listRaw.isBlank()) return listRaw
    fun normalize(tag: String) = tag.lowercase().replace('-', '_')
    return runCatching {
        val list = SubtypeJsonConfig.decodeFromString<List<Subtype>>(listRaw)
        val normalizedOverrides = layoutOverrides.mapKeys { (key, _) -> normalize(key) }
        val transformed = list.map { subtype ->
            val tag = normalize(subtype.primaryLocale.languageTag())
            val overrideId = normalizedOverrides[tag] ?: return@map subtype
            // from() throws on a malformed id — an override with a broken
            // component id is skipped, never allowed to break the subtype list.
            val component = tryOrNull { ExtensionComponentName.from(overrideId) } ?: return@map subtype
            subtype.copy(layoutMap = subtype.layoutMap.copy(characters = component))
        }
        SubtypeJsonConfig.encodeToString(transformed)
    }.getOrDefault(listRaw)
}
