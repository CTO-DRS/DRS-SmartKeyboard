/*
 * Copyright (C) 2025 The DRS Smart Keyboard Project
 */

package com.drs.smartkeyboard.lib.ext

/**
 * DRS M0.3 — the package trust root. The release signing key is pinned
 * HERE, in code: a package catalog entry is only trusted when its
 * `pubkey_id` names a known key and its `signature` verifies against the
 * pinned public key bytes. There is no remote key discovery, no
 * trust-on-first-use, no override — a key rotation ships as a new pinned
 * id in an app release.
 *
 * The private seed NEVER appears in the repository: it lives in
 * `.local-secrets/cto-drs-release-1.seed` (git-ignored on purpose) and is
 * loaded only by tools/drs_package_sign.py, which refuses to run before
 * proving itself on the official RFC 8032 §7.1 vectors.
 */
object DrsPackageTrust {

    /** The only release signing key this app build trusts. */
    const val RELEASE_KEY_ID = "cto-drs-release-1"

    /** Public key of [RELEASE_KEY_ID] — 32 raw bytes, lowercase hex. */
    const val RELEASE_PUBKEY_HEX =
        "9fef1d98448fc8625dfcc3fdc32d8ce78701d59ff69d018075590033df39865c"

    private val releasePubkey: ByteArray = requireNotNull(RELEASE_PUBKEY_HEX.hexToBytes())

    /**
     * Verifies a package signature. [signatureHex] and [keyId] come from
     * the catalog entry; [packageBytes] are the exact raw bytes of the
     * .flex archive (the signature covers the file bytes — verification
     * happens BEFORE any decompression).
     *
     * Fail-closed on every oddity: unknown key id, malformed hex, wrong
     * length, missing fields — all return false, never throw.
     */
    fun verifyPackageSignature(packageBytes: ByteArray, signatureHex: String?, keyId: String?): Boolean {
        if (keyId != RELEASE_KEY_ID) return false
        if (signatureHex.isNullOrBlank()) return false
        val signature = signatureHex.hexToBytes() ?: return false
        return DrsEd25519.verify(releasePubkey, packageBytes, signature)
    }

    private fun String.hexToBytes(): ByteArray? {
        if (length % 2 != 0) return null
        if (!all { it.isDigit() || it in 'a'..'f' || it in 'A'..'F' }) return null
        return chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    }
}
