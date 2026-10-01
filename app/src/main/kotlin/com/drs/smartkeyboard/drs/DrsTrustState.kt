/*
 * Copyright (C) 2025 The DRS Smart Keyboard Project
 */

package com.drs.smartkeyboard.drs

import com.drs.smartkeyboard.lib.ext.DrsPackageTrust

/**
 * DRS M3.2 — the four-way trust classification of a package, computed
 * from the catalog fields the app REALLY has in hand (signature hex +
 * key id). The full byte-level verification runs at install time (M0.3);
 * the badge is the honest pre-download statement of what the catalog
 * claims and whether it names OUR key.
 *
 *  VERIFIED       — signed AND names the pinned release key (green)
 *  UNKNOWN_KEY    — signed but names a key we do not know (amber)
 *  UNSIGNED       — no signature at all (red; M0.3 drops these entries)
 *  INVALID_SIG    — the signature field is not even well-formed (red)
 *
 * The fail-closed catalog gate (M0.3) already drops UNSIGNED and
 * UNKNOWN_KEY entries before they ever render; the classifier exists so
 * the badge logic and the gate can never drift apart.
 */
enum class DrsTrustState {
    VERIFIED,
    UNKNOWN_KEY,
    UNSIGNED,
    INVALID_SIG,
    ;

    companion object {
        /** Pure classification from the catalog fields of a package. */
        fun of(signature: String?, pubkeyId: String?): DrsTrustState = when {
            signature.isNullOrBlank() -> UNSIGNED
            !isWellFormedHex(signature) -> INVALID_SIG
            pubkeyId != DrsPackageTrust.RELEASE_KEY_ID -> UNKNOWN_KEY
            else -> VERIFIED
        }

        private fun isWellFormedHex(s: String): Boolean =
            s.length % 2 == 0 && s.length >= 64 &&
                s.all { it.isDigit() || it in 'a'..'f' || it in 'A'..'F' }
    }
}
