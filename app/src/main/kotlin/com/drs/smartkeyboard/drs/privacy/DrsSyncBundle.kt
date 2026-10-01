/*
 * Copyright (C) 2021-2025 The DRS Smart Keyboard Project
 */

package com.drs.smartkeyboard.drs.privacy

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * DRS roadmap phase 3 (privacy & trust, task 3): the payload that rides
 * inside the DRSYNC1 E2E container — what the keyboard is willing to
 * sync, and nothing else.
 *
 * Scope discipline: the payload carries exactly the personalization
 * data the user typed into their own keyboard (learned words, learned
 * bigram rows) plus two metadata fields (app version, export moment).
 * No typed text history, no clipboard, no crash data, no contacts,
 * no settings dump beyond what adaptation needs. The allowlist is the
 * class itself — adding a field is a deliberate, reviewable act.
 *
 * Caps mirror [com.drs.smartkeyboard.drs.ai.DrsLearningEngine]'s own
 * ceilings (2000 words / 1024 bigram rows) with a byte cap sized at
 * 2× the learning file cap, so a malicious or broken "restore" cannot
 * smuggle an unbounded payload into the device (same zip-bomb
 * discipline as the flex-package and backup importers).
 */
object DrsSyncBundle {

    @Serializable
    data class Payload(
        val v: Int = FORMAT_VERSION,
        val app: String = "",
        val exportedAt: Long = 0L,
        val words: Map<String, Long> = emptyMap(),
        val bigrams: Map<String, Map<String, Long>> = emptyMap(),
    )

    const val FORMAT_VERSION: Int = 1

    /** Mirrors DrsLearningEngine.MAX_WORDS. */
    const val MAX_WORDS: Int = 2000

    /** Mirrors DrsLearningEngine.MAX_BIGRAMS. */
    const val MAX_BIGRAM_ROWS: Int = 1024

    /** 2× the 128 KB learning-file cap — JSON overhead headroom. */
    const val MAX_PAYLOAD_BYTES: Int = 256 * 1024

    /** Largest sealed-text accepted for restore (Base64 of the capped container). */
    const val MAX_SEALED_TEXT_CHARS: Int = DrsE2ECrypto.MAX_TEXT_CHARS

    /** Per-entry hard limits — a word is a word, not a document. */
    const val MAX_WORD_CHARS: Int = 40

    sealed class BundleError(message: String) : Exception(message) {
        data object PayloadTooLarge : BundleError("payload exceeds the sync cap")
        data object TooManyEntries : BundleError("payload exceeds the entries cap")
        data object MalformedEntry : BundleError("a payload entry violates its shape limits")
        data object UnsupportedVersion : BundleError("payload version is not supported by this build")
        data object MalformedJson : BundleError("payload is not valid JSON")
    }

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    /**
     * Validates a payload against every cap and encodes it to the
     * plaintext bytes that [DrsE2ECrypto.seal] will carry. Throws
     * [BundleError] on any violation — validation happens BEFORE
     * encryption so callers get honest, specific errors.
     */
    fun encode(payload: Payload): ByteArray {
        if (payload.v > FORMAT_VERSION) throw BundleError.UnsupportedVersion
        validate(payload)
        val bytes = json.encodeToString(Payload.serializer(), payload).toByteArray(Charsets.UTF_8)
        if (bytes.size > MAX_PAYLOAD_BYTES) throw BundleError.PayloadTooLarge
        return bytes
    }

    /**
     * Decodes and validates plaintext bytes back into a [Payload].
     * Unknown keys are ignored (forward-compat) but the caps are
     * enforced on the DECODED value — a larger-than-cap payload never
     * reaches the learning engine.
     */
    fun decode(bytes: ByteArray): Payload {
        if (bytes.size > MAX_PAYLOAD_BYTES) throw BundleError.PayloadTooLarge
        val payload = try {
            json.decodeFromString(Payload.serializer(), bytes.toString(Charsets.UTF_8))
        } catch (_: Exception) {
            throw BundleError.MalformedJson
        }
        if (payload.v > FORMAT_VERSION) throw BundleError.UnsupportedVersion
        validate(payload)
        return payload
    }

    /** Validates then seals to a DRSYNC1 text container. */
    fun seal(
        payload: Payload,
        passphrase: CharArray,
        iterations: Int = DrsE2ECrypto.DEFAULT_ITERATIONS,
    ): String = DrsE2ECrypto.sealToText(encode(payload), passphrase, iterations)

    /** Opens a DRSYNC1 text container and decodes+validates its payload. */
    fun open(
        text: String,
        passphrase: CharArray,
        iterations: Int = DrsE2ECrypto.DEFAULT_ITERATIONS,
    ): Payload = decode(DrsE2ECrypto.openFromText(text, passphrase, iterations))

    private fun validate(payload: Payload) {
        if (payload.words.size > MAX_WORDS || payload.bigrams.size > MAX_BIGRAM_ROWS) {
            throw BundleError.TooManyEntries
        }
        if (payload.words.keys.any { it.isEmpty() || it.length > MAX_WORD_CHARS || it != it.trim().lowercase() }) {
            throw BundleError.MalformedEntry
        }
        payload.bigrams.forEach { (prev, row) ->
            if (prev.isEmpty() || prev.length > MAX_WORD_CHARS || prev != prev.trim().lowercase()) {
                throw BundleError.MalformedEntry
            }
            if (row.size > MAX_BIGRAM_ROWS || row.keys.any { it.isEmpty() || it.length > MAX_WORD_CHARS }) {
                throw BundleError.MalformedEntry
            }
            if (payload.words.size.toLong() + row.size > Int.MAX_VALUE) throw BundleError.TooManyEntries
        }
        payload.words.values.forEach { if (it < 0) throw BundleError.MalformedEntry }
        payload.bigrams.values.forEach { row -> row.values.forEach { if (it < 0) throw BundleError.MalformedEntry } }
    }
}
