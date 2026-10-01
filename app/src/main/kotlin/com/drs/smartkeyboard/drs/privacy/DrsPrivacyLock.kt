/*
 * Copyright (C) 2021-2025 The DRS Smart Keyboard Project
 */

package com.drs.smartkeyboard.drs.privacy

import com.drs.smartkeyboard.ime.voice.VoiceRecognizerMode

/**
 * DRS roadmap phase 3 (privacy & trust, task 2): the absolute privacy
 * mode — one switch that closes every network-capable surface the app
 * owns, and the pure gate every surface consults.
 *
 * Honest scope of what the app's network surfaces ARE (the full audit
 * lives in docs/SECURITY_AUDIT.md and is enforced by ci/privacy_canary.sh):
 *   1. the update center (manual-by-default GitHub releases check),
 *   2. the flex package center (catalog manifest + package downloads),
 *   3. the voice recognizer when the user picked AUTO/STANDARD — a
 *      system service that MAY be a cloud recognizer on some ROMs
 *      (the shipped default is already ON_DEVICE_ONLY since v1.25.0).
 * The keyboard core (the ime and drs source trees) contains zero network code and
 * the CI canary fails the build if that ever changes.
 *
 * When the absolute mode is ON:
 *  - every [NetworkSurface] is refused (the gates below),
 *  - the recognizer is coerced to ON_DEVICE_ONLY ([effectiveRecognizer]),
 *  - the network sentinel attestation (DrsNetworkSentinel) reports the
 *    live traffic counters as the visible, user-verifiable proof.
 *
 * The lock state itself is ONE preference bit
 * (`privacy__absolute_mode`, default OFF — privacy features must not
 * silently change behavior for existing users); the provider wiring
 * lives in DrsApplication.onCreate. All gates are pure and JVM-tested.
 */
object DrsPrivacyLock {

    /** Every capability that can move bytes over a network socket. */
    enum class NetworkSurface {
        /** Update center: checking GitHub releases for a newer APK. */
        UPDATE_CHECK,

        /** Update center: downloading the APK itself. */
        UPDATE_DOWNLOAD,

        /** Package center: fetching the flex catalog manifest. */
        PACKAGE_MANIFEST,

        /** Package center: downloading a .flex package file. */
        PACKAGE_DOWNLOAD,
    }

    /** Pluggable absolute-mode reader — wired to the preference store at app start. */
    @Volatile
    var absoluteModeProvider: () -> Boolean = { false }
        private set

    /** Called once from DrsApplication.onCreate; pure test code never calls it. */
    fun init(provider: () -> Boolean) {
        absoluteModeProvider = provider
    }

    /** The current absolute-mode state, as the gates see it. */
    fun isAbsoluteMode(): Boolean = try {
        absoluteModeProvider()
    } catch (_: Throwable) {
        // A broken provider must never silently OPEN the gates.
        true
    }

    /** The single network gate: absolute mode refuses every surface, no exceptions. */
    fun allows(surface: NetworkSurface, absoluteMode: Boolean = isAbsoluteMode()): Boolean = !absoluteMode

    /**
     * Voice gate: absolute mode coerces the requested recognizer mode to
     * ON_DEVICE_ONLY — a network-backed recognizer is refused, while a
     * ROM-honored on-device recognizer keeps working (no network socket
     * in our process, matching the attestation contract). Returning the
     * effective mode (instead of a boolean) keeps the v1.25.0 route
     * plumbing intact at the call site.
     */
    fun effectiveRecognizer(
        requested: VoiceRecognizerMode,
        absoluteMode: Boolean = isAbsoluteMode(),
    ): VoiceRecognizerMode = if (absoluteMode) VoiceRecognizerMode.ON_DEVICE_ONLY else requested
}
