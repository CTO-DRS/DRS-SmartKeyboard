/*
 * Copyright (C) 2021-2025 The DRS Smart Keyboard Project
 */

package com.drs.smartkeyboard.drs.privacy

import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import android.util.Base64

/**
 * DRS roadmap phase 3 (privacy & trust, task 3): the E2E container that
 * makes the optional sync/backup channel Zero-Knowledge by construction.
 *
 * The contract, in one paragraph: everything the keyboard learns lives
 * on the device; when the user wants a copy of it in "the cloud" (any
 * drive, any future DRS sync service), the payload leaves the device
 * only as an opaque blob encrypted with a key derived from the user's
 * own passphrase. No server — present or future — ever sees the
 * passphrase, the key, or the plaintext: the server's only role is
 * storing bytes it cannot read. That is the Zero-Knowledge property:
 * a full server compromise reveals nothing but ciphertext whose
 * decryption costs a brute-force attack on the user's passphrase.
 *
 * Format (DRSYNC1) — deliberately tiny, versioned, and streaming-safe:
 *
 *   offset  size  field
 *   0       7     magic "DRSYNC1" (ASCII)
 *   7       1     format version (currently 1)
 *   8       1     salt length (bytes, currently 16)
 *   9       salt  PBKDF2 salt (random per container)
 *   ..      1     IV length (bytes, currently 12)
 *   ..      IV    AES-GCM nonce (random per container)
 *   ..      4     ciphertext length (big-endian u32)
 *   ..      ct    AES-256-GCM ciphertext || 128-bit auth tag
 *
 * The whole header — magic through the length field — is bound to the
 * GCM computation as AAD, so any tampering with the framing (not just
 * the ciphertext) fails authentication. Password verification is a
 * side effect of the tag: there is no password-check field to leak
 * whether a container exists, and a wrong passphrase is
 * indistinguishable from corruption (both surface as
 * [DrsE2EError.AuthenticationFailed]).
 *
 * Crypto choices and why (full rationale in ADR 0006):
 *  - AES-256-GCM: the standard AEAD on every Android release we ship
 *    (API 26+), hardware-accelerated, one-pass authenticate+encrypt.
 *  - PBKDF2WithHmacSHA256: available since API 26 without an additional
 *    dependency; 600,000 iterations (OWASP 2024 guidance) for the
 *    shipped default. Export/import runs once per user action, so a
 *    one-to-two-second derivation is the right trade — Argon2id would
 *    add a native dependency for marginal gain at this threat model.
 *  - Random 16-byte salt per container: same passphrase + same data
 *    still produces different bytes each export (no rainbow-table
 *    precomputation across exports).
 *
 * Pure logic aside from the JCA calls — fully JVM-testable. (Tests run
 * on the JVM where android.util.Base64 is stubbed by the unit-test
 * plugin; the object falls back to java.util.Base64 through
 * [b64Encode]/[b64Decode] when the Android classes are unavailable.)
 */
object DrsE2ECrypto {

    /** ASCII "DRSYNC1" — the only bytes guaranteed stable across versions. */
    val MAGIC: ByteArray = "DRSYNC1".toByteArray(Charsets.US_ASCII)

    const val FORMAT_VERSION: Int = 1

    const val DEFAULT_ITERATIONS: Int = 600_000

    const val SALT_BYTES: Int = 16
    const val IV_BYTES: Int = 12
    const val KEY_BITS: Int = 256
    const val GCM_TAG_BITS: Int = 128

    /** Fixed framing bytes (magic+ver+saltLen+ivLen+ctLen) — salt/IV ride after them. */
    const val HEADER_OVERHEAD: Int = 7 + 1 + 1 + 1 + 4

    /** Smallest possible well-formed container (empty plaintext: ct = tag only). */
    const val MIN_CONTAINER_BYTES: Int = HEADER_OVERHEAD + SALT_BYTES + IV_BYTES + GCM_TAG_BITS / 8

    /** Base64 ceiling for the shared text channel (a 1 MB payload never produces more). */
    const val MAX_TEXT_CHARS: Int = 2 * 1024 * 1024

    /** Every failure mode of [open], surfaced as data instead of stack traces. */
    sealed class DrsE2EError(message: String) : Exception(message) {
        /** Fewer bytes than the minimal well-formed container. */
        data object ContainerTooShort : DrsE2EError("container shorter than the fixed header")

        /** Leading bytes are not the DRSYNC1 magic. */
        data object BadMagic : DrsE2EError("magic mismatch — not a DRS sync container")

        /** Magic matches but the version byte is not the supported one. */
        data object UnsupportedVersion : DrsE2EError("container version is not supported by this build")

        /** Salt/IV lengths violate the constants, or the declared ct length is impossible. */
        data object MalformedContainer : DrsE2EError("framing fields are inconsistent")

        /** GCM tag verification failed: wrong passphrase or tampered bytes. */
        data object AuthenticationFailed : DrsE2EError("authentication failed — wrong passphrase or corrupted container")

        /** Text channel input was not valid Base64 (or absurdly large). */
        data object BadEncoding : DrsE2EError("container text is not valid Base64")
    }

    /** Immutable view of a parsed (not yet decrypted) container. */
    data class Parsed(
        val version: Int,
        val salt: ByteArray,
        val iv: ByteArray,
        val ciphertext: ByteArray,
        val header: ByteArray,
    )

    /**
     * Derives the AES key from [passphrase] and [salt]. Iterations are
     * injectable so unit tests can use tiny values; production callers
     * keep the default.
     */
    fun deriveKey(passphrase: CharArray, salt: ByteArray, iterations: Int = DEFAULT_ITERATIONS): SecretKey {
        require(passphrase.isNotEmpty()) { "passphrase must not be empty" }
        require(iterations in 1..DEFAULT_ITERATIONS) { "iterations out of range: $iterations" }
        val spec = PBEKeySpec(passphrase, salt, iterations, KEY_BITS)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        return try {
            SecretKeySpec(factory.generateSecret(spec).encoded, "AES")
        } finally {
            // PBEKeySpec holds the password in clear chars — zero it eagerly.
            spec.clearPassword()
        }
    }

    /**
     * Encrypts [plaintext] into a DRSYNC1 container. Salt and IV are
     * freshly random on every call — the same inputs never produce the
     * same output, so the container leaks no equality information about
     * its contents.
     */
    fun seal(
        plaintext: ByteArray,
        passphrase: CharArray,
        iterations: Int = DEFAULT_ITERATIONS,
        random: SecureRandom = SecureRandom(),
    ): ByteArray {
        val salt = ByteArray(SALT_BYTES).also(random::nextBytes)
        val iv = ByteArray(IV_BYTES).also(random::nextBytes)
        val key = deriveKey(passphrase, salt, iterations)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
        // GCM ciphertext is exactly plaintext + tag, so the declared length
        // is known BEFORE encryption and can ride inside the AAD'd header.
        val header = buildHeader(salt, iv, plaintext.size + GCM_TAG_BITS / 8)
        cipher.updateAAD(header)
        val ct = cipher.doFinal(plaintext)
        return header + ct
    }

    /**
     * Decrypts a DRSYNC1 container. Throws one of the [DrsE2EError]
     * subclasses — never a raw crypto exception — so callers handle
     * failures as data.
     */
    fun open(container: ByteArray, passphrase: CharArray, iterations: Int = DEFAULT_ITERATIONS): ByteArray {
        if (container.size < MIN_CONTAINER_BYTES) throw DrsE2EError.ContainerTooShort
        if (!container.copyOfRange(0, MAGIC.size).contentEquals(MAGIC)) throw DrsE2EError.BadMagic
        val version = container[MAGIC.size].toInt() and 0xFF
        if (version != FORMAT_VERSION) throw DrsE2EError.UnsupportedVersion
        var off = MAGIC.size + 1
        val saltLen = container[off].toInt() and 0xFF; off += 1
        if (saltLen != SALT_BYTES) throw DrsE2EError.MalformedContainer
        val salt = container.copyOfRange(off, off + saltLen); off += saltLen
        val ivLen = container[off].toInt() and 0xFF; off += 1
        if (ivLen != IV_BYTES) throw DrsE2EError.MalformedContainer
        val iv = container.copyOfRange(off, off + ivLen); off += ivLen
        if (off + 4 > container.size) throw DrsE2EError.MalformedContainer
        val ctLen = ((container[off].toInt() and 0xFF) shl 24) or
            ((container[off + 1].toInt() and 0xFF) shl 16) or
            ((container[off + 2].toInt() and 0xFF) shl 8) or
            (container[off + 3].toInt() and 0xFF)
        off += 4
        if (ctLen <= GCM_TAG_BITS / 8 || off + ctLen != container.size) throw DrsE2EError.MalformedContainer
        val header = container.copyOfRange(0, off)
        val ct = container.copyOfRange(off, container.size)
        val key = deriveKey(passphrase, salt, iterations)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
        cipher.updateAAD(header)
        return try {
            cipher.doFinal(ct)
        } catch (_: Exception) {
            // AEADBadTagException and friends all collapse to one honest answer:
            // either the passphrase is wrong or the bytes were altered.
            throw DrsE2EError.AuthenticationFailed
        }
    }

    /** Text-channel wrapper: Base64 (NO_WRAP) so the blob survives notes/email/URIs. */
    fun sealToText(
        plaintext: ByteArray,
        passphrase: CharArray,
        iterations: Int = DEFAULT_ITERATIONS,
        random: SecureRandom = SecureRandom(),
    ): String {
        val container = seal(plaintext, passphrase, iterations, random)
        val text = b64Encode(container)
        if (text.length > MAX_TEXT_CHARS) throw DrsE2EError.BadEncoding
        return text
    }

    /** Text-channel inverse of [sealToText]. Throws [DrsE2EError.BadEncoding] on garbage input. */
    fun openFromText(text: String, passphrase: CharArray, iterations: Int = DEFAULT_ITERATIONS): ByteArray {
        if (text.isBlank() || text.length > MAX_TEXT_CHARS) throw DrsE2EError.BadEncoding
        val container = b64Decode(text.trim()) ?: throw DrsE2EError.BadEncoding
        return open(container, passphrase, iterations)
    }

    // ------------------------------------------------------- Base64 bridge
    // android.util.Base64 exists on device; JVM unit tests get the
    // java.util.Base64 path. Both produce/consume the standard alphabet;
    // NO_WRAP only strips line breaks, which openFromText tolerates anyway.

    private fun b64Encode(data: ByteArray): String =
        try {
            Base64.encodeToString(data, Base64.NO_WRAP)
        } catch (_: Throwable) {
            java.util.Base64.getEncoder().encodeToString(data)
        }

    private fun b64Decode(text: String): ByteArray? =
        try {
            Base64.decode(text, Base64.NO_WRAP)
        } catch (tooShort: Throwable) {
            try {
                java.util.Base64.getMimeDecoder().decode(text)
            } catch (_: IllegalArgumentException) {
                null
            }
        }

    // ------------------------------------------------------------ internals

    private fun buildHeader(salt: ByteArray, iv: ByteArray, ctLen: Int): ByteArray =
        MAGIC + byteArrayOf(FORMAT_VERSION.toByte(), salt.size.toByte()) + salt +
            byteArrayOf(iv.size.toByte()) + iv + ctLen.toBytesBE()

    private fun Int.toBytesBE(): ByteArray = byteArrayOf(
        (this ushr 24).toByte(), (this ushr 16).toByte(), (this ushr 8).toByte(), this.toByte(),
    )
}
