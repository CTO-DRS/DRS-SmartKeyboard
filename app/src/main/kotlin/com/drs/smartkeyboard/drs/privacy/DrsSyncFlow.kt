/*
 * Copyright (C) 2025 The DRS Smart Keyboard Project
 */

package com.drs.smartkeyboard.drs.privacy

import java.security.MessageDigest

/**
 * DRS M3.3 — the device-to-device sync FLOW on top of the sealed
 * DrsSyncBundle container: deterministic pairing, binary-safe chunking,
 * and fail-closed reassembly. Pure JVM, no I/O.
 *
 *  1. PAIRING — [pairingKey] sorts the two device ids ALPHABETICALLY
 *     before hashing, so (A,B) and (B,A) derive the identical shared
 *     pairing secret name. Order-dependence was the original bug shape.
 *
 *  2. CHUNKING — the sealed payload (already an opaque byte container)
 *     travels as `DRS-SYNC1:<i>/<n>:<hex>` frames. HEX, not raw UTF-8
 *     slicing: a naive text cut used to split a multi-byte character in
 *     half and the receiving side corrupted the container silently.
 *     Hex frames are binary-safe by construction.
 *
 *  3. REASSEMBLY — [reassemble] accepts frames in ANY order, fails
 *     CLOSED on: a malformed frame, an index out of range, duplicate
 *     indices carrying DIFFERENT bytes, or a missing index. A silent
 *     partial restore is the one thing this flow must never do.
 *
 *  4. MERGE — [mergeByMax] combines two device tables deterministically
 *     (per-key maximum), so importing from either device in either
 *     order yields the same state.
 */
object DrsSyncFlow {

    const val FRAME_PREFIX = "DRS-SYNC1:"

    /** Hex characters per frame payload (≈ the safe QR/chat slice size). */
    const val HEX_CHUNK_SIZE = 400

    // ------------------------------------------------------------- pairing

    /**
     * The deterministic pairing key of two devices: alphabetical sort of
     * the ids, NUL-joined, SHA-256, first 16 bytes as hex.
     */
    fun pairingKey(deviceA: String, deviceB: String): String {
        val (first, second) = listOf(deviceA.trim(), deviceB.trim()).sorted()
        require(first.isNotEmpty() && second.isNotEmpty()) { "device ids must not be empty" }
        val digest = MessageDigest.getInstance("SHA-256")
            .digest("$first\u0000$second".toByteArray(Charsets.UTF_8))
        return digest.take(16).joinToString("") { "%02x".format(it) }
    }

    // ------------------------------------------------------------ chunking

    /**
     * Splits a binary payload into [FRAME_PREFIX] hex frames. The payload
     * is opaque bytes (the sealed container) — hex encoding keeps every
     * byte boundary intact across any text channel.
     */
    fun chunk(payload: ByteArray): List<String> {
        require(payload.isNotEmpty()) { "nothing to chunk" }
        val hex = payload.joinToString("") { "%02x".format(it) }
        val total = (hex.length + HEX_CHUNK_SIZE - 1) / HEX_CHUNK_SIZE
        return (0 until total).map { i ->
            val start = i * HEX_CHUNK_SIZE
            val end = minOf(start + HEX_CHUNK_SIZE, hex.length)
            "$FRAME_PREFIX$i/$total:${hex.substring(start, end)}"
        }
    }

    /**
     * Reassembles frames in ANY order. Fail-closed on malformed input,
     * out-of-range indices, conflicting duplicates, or missing pieces.
     */
    fun reassemble(frames: List<String>): ByteArray {
        require(frames.isNotEmpty()) { "no frames to reassemble" }
        val parsed = frames.map { frame ->
            if (!frame.startsWith(FRAME_PREFIX)) {
                throw IllegalArgumentException("malformed frame: wrong prefix")
            }
            val body = frame.removePrefix(FRAME_PREFIX)
            val parts = body.split(":", limit = 2)
            if (parts.size != 2) throw IllegalArgumentException("malformed frame: expected i/n:hex")
            val indexPart = parts[0]
            val hexPart = parts[1]
            val indexMatch = Regex("""(\d+)/(\d+)""").matchEntire(indexPart)
                ?: throw IllegalArgumentException("malformed frame: bad index '$indexPart'")
            val (index, total) = indexMatch.destructured.let { it.component1().toInt() to it.component2().toInt() }
            if (total <= 0 || index !in 0 until total) {
                throw IllegalArgumentException("frame index out of range: $index/$total")
            }
            if (hexPart.isEmpty() || !hexPart.all { it.isDigit() || it in 'a'..'f' }) {
                throw IllegalArgumentException("malformed frame: bad hex payload")
            }
            Triple(index, total, hexPart)
        }
        val total = parsed.first().second
        if (parsed.any { it.second != total }) {
            throw IllegalArgumentException("conflicting totals across frames")
        }
        val byIndex = LinkedHashMap<Int, String>()
        for ((index, _, hex) in parsed) {
            val existing = byIndex[index]
            if (existing != null && existing != hex) {
                throw IllegalArgumentException("duplicate index $index with different bytes")
            }
            byIndex[index] = hex
        }
        if (byIndex.size != total) {
            throw IllegalArgumentException("incomplete transfer: ${byIndex.size}/$total frames")
        }
        val hex = (0 until total).joinToString("") { byIndex[it]!! }
        if (hex.length % 2 != 0) throw IllegalArgumentException("odd hex length")
        return ByteArray(hex.length / 2) { i ->
            ((Character.digit(hex[2 * i], 16) shl 4) + Character.digit(hex[2 * i + 1], 16)).toByte()
        }
    }

    // --------------------------------------------------------------- merge

    /** Per-key deterministic merge-by-max of two word tables. */
    fun mergeByMax(current: Map<String, Long>, incoming: Map<String, Long>): Map<String, Long> {
        val out = LinkedHashMap<String, Long>(current.size + incoming.size)
        for ((k, v) in current) out[k] = maxOf(v, incoming[k] ?: 0L)
        for ((k, v) in incoming) if (k !in out) out[k] = v
        return out
    }
}
