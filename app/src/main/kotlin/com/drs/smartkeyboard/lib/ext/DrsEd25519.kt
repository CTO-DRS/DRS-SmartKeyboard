/*
 * Copyright (C) 2025 The DRS Smart Keyboard Project
 */

package com.drs.smartkeyboard.lib.ext

import java.math.BigInteger
import java.security.MessageDigest

/**
 * DRS M0.3 — Ed25519 signatures per RFC 8032 (§5.1), implemented on
 * BigInteger with the curve arithmetic written out in full so every step
 * is auditable. No third-party crypto dependency, no hidden fallbacks.
 *
 * Verification follows RFC 8032 §5.1.7 EXACTLY:
 *  1. the signature decodes to R (32 bytes) and S (32 bytes, little-endian);
 *  2. S is rejected unless 0 < S < L (the group order) — signatures with
 *     S >= L are refused outright (malleability gate);
 *  3. A and R are decoded with strict y < p rejection (a point outside the
 *     field is refused, never silently reduced);
 *  4. the check is the COFACTORED equation [8][S]B == [8]R + [8][k]A —
 *     both sides carry the cofactor 8, exactly as the RFC text specifies
 *     (an uncofactored right-hand side fails real RFC vectors).
 *
 * Pinned by DrsM03Ed25519VectorsTest on the four RFC 8032 §7.1 TEST
 * vectors plus a golden cross-implementation vector produced (and
 * self-checked) by tools/drs_package_sign.py.
 */
object DrsEd25519 {

    const val SEED_SIZE = 32
    const val PUBLIC_KEY_SIZE = 32
    const val SIGNATURE_SIZE = 64

    // p = 2^255 - 19
    private val P: BigInteger = BigInteger.TWO.pow(255).subtract(BigInteger.valueOf(19))

    // L (group order) = 2^252 + 27742317777372353535851937790883648493
    private val L: BigInteger = BigInteger.TWO.pow(252).add(
        BigInteger("27742317777372353535851937790883648493"),
    )

    // d = -121665 * (121666)^-1 mod p
    private val D: BigInteger = BigInteger.valueOf(-121665).multiply(
        BigInteger.valueOf(121666).modInverse(P),
    ).mod(P)

    // sqrt(-1) mod p — for the (p+3)/8 square-root formula
    private val SQRT_M1: BigInteger = BigInteger.TWO.modPow(P.subtract(BigInteger.ONE).divide(BigInteger.valueOf(4)), P)

    // Base point B: y = 4/5, x = the even root
    private val BY: BigInteger = BigInteger.valueOf(4).multiply(BigInteger.valueOf(5).modInverse(P)).mod(P)
    private val BX: BigInteger = recoverX(BY, false)
    private val B = Point(BX, BY)

    /** Affine point on the curve (or the identity via null X in additive use). */
    private data class Point(val x: BigInteger, val y: BigInteger)

    private fun inv(a: BigInteger): BigInteger = a.modInverse(P)

    private fun isOnCurve(p: Point): Boolean {
        // -x^2 + y^2 - 1 - d*x^2*y^2 == 0 (mod p)
        val xx = p.x.multiply(p.x).mod(P)
        val yy = p.y.multiply(p.y).mod(P)
        val check = yy.subtract(xx)
            .subtract(BigInteger.ONE)
            .subtract(D.multiply(xx).multiply(yy))
            .mod(P)
        return check.signum() == 0
    }

    /** Recovers x from y with the given sign bit; throws when y is not on the curve. */
    private fun recoverX(y: BigInteger, sign: Boolean): BigInteger {
        // x^2 = (y^2 - 1) / (d*y^2 + 1)  →  c = u * v^-1 (mod p)
        val y2 = y.multiply(y).mod(P)
        val u = y2.subtract(BigInteger.ONE).mod(P)
        val v = D.multiply(y2).add(BigInteger.ONE).mod(P)
        val c = u.multiply(inv(v)).mod(P)
        // candidate sqrt via the exponent (p+3)/8, corrected by sqrt(-1)
        var x = c.modPow(P.add(BigInteger.valueOf(3)).divide(BigInteger.valueOf(8)), P)
        if (x.multiply(x).subtract(c).mod(P).signum() != 0) {
            x = x.multiply(SQRT_M1).mod(P)
        }
        if (x.multiply(x).subtract(c).mod(P).signum() != 0) {
            throw IllegalArgumentException("invalid point: no sqrt exists (y not on curve)")
        }
        return if (x.testBit(0) == sign) x else P.subtract(x)
    }

    private fun decodePoint(encoded: ByteArray): Point {
        require(encoded.size == PUBLIC_KEY_SIZE) { "point encoding must be 32 bytes" }
        val copy = encoded.copyOf()
        val sign = (copy[31].toInt() and 0x80) != 0
        copy[31] = (copy[31].toInt() and 0x7F).toByte()
        val y = littleEndianToBigInteger(copy)
        // RFC 8032 §5.1.3 step 2: reject y >= p (the hex string may encode
        // values outside the field — those are invalid, not reduced).
        if (y >= P || y.signum() < 0) {
            throw IllegalArgumentException("invalid point: y >= p")
        }
        val x = recoverX(y, sign)
        val point = Point(x, y)
        require(isOnCurve(point)) { "invalid point: not on curve" }
        return point
    }

    private fun encodePoint(p: Point): ByteArray {
        val out = bigIntegerToLittleEndian(p.y, 32)
        if (p.x.testBit(0)) {
            out[31] = (out[31].toInt() or 0x80).toByte()
        }
        return out
    }

    /**
     * Little-endian UNSIGNED decode: an extra ZERO byte is prepended (the
     * big-endian SIGN position) so BigInteger's two's-complement sign bit
     * stays clear. Without it, a hash whose top byte has the high bit set
     * decodes NEGATIVE — shifting every r/k derived from it by 2^512 mod L
     * and silently breaking real RFC vectors (the exact bug the official
     * vectors caught here).
     */
    private fun littleEndianToBigInteger(bytes: ByteArray): BigInteger {
        val big = ByteArray(bytes.size + 1)
        for (i in bytes.indices) big[i + 1] = bytes[bytes.size - 1 - i]
        return BigInteger(big)
    }

    private fun bigIntegerToLittleEndian(value: BigInteger, size: Int): ByteArray {
        val big = value.toByteArray()
        val out = ByteArray(size)
        var idx = big.size - 1
        var outIdx = 0
        while (idx >= 0 && outIdx < size) {
            out[outIdx++] = big[idx--]
        }
        return out
    }

    /** Point addition on the twisted Edwards curve (affine, mod p). */
    private fun add(a: Point, b: Point): Point {
        val x1 = a.x
        val y1 = a.y
        val x2 = b.x
        val y2 = b.y
        val x1x2 = x1.multiply(x2).mod(P)
        val y1y2 = y1.multiply(y2).mod(P)
        val dx1x2y1y2 = D.multiply(x1x2).multiply(y1y2).mod(P)
        val x3 = x1.multiply(y2).add(x2.multiply(y1))
            .multiply(inv(BigInteger.ONE.add(dx1x2y1y2)))
            .mod(P)
        val y3 = y1y2.add(x1x2)
            .multiply(inv(BigInteger.ONE.subtract(dx1x2y1y2)))
            .mod(P)
        return Point(x3, y3)
    }

    /** Scalar multiplication via double-and-add. */
    private fun scalarMul(k: BigInteger, p: Point): Point {
        var result: Point? = null
        var base = p
        var scalar = k
        while (scalar.signum() > 0) {
            if (scalar.testBit(0)) {
                result = if (result == null) base else add(result, base)
            }
            base = add(base, base)
            scalar = scalar.shiftRight(1)
        }
        return result ?: Point(BigInteger.ZERO, BigInteger.ONE) // identity
    }

    private fun sha512(vararg chunks: ByteArray): ByteArray {
        val digest = MessageDigest.getInstance("SHA-512")
        for (chunk in chunks) digest.update(chunk)
        return digest.digest()
    }

    private fun expandSeed(seed: ByteArray): Pair<BigInteger, ByteArray> {
        require(seed.size == SEED_SIZE) { "seed must be 32 bytes" }
        val h = sha512(seed)
        val a = clamp(h.copyOfRange(0, 32))
        return Pair(a, h.copyOfRange(32, 64))
    }

    /** RFC 8032 §5.1.5: clamping of the secret scalar bytes. */
    private fun clamp(bytes: ByteArray): BigInteger {
        bytes[0] = (bytes[0].toInt() and 248).toByte()
        bytes[31] = (bytes[31].toInt() and 127).toByte()
        bytes[31] = (bytes[31].toInt() or 64).toByte()
        return littleEndianToBigInteger(bytes)
    }

    /** Public key = [a]B where a is the clamped secret expanded from the seed. */
    fun publicKeyFromSeed(seed: ByteArray): ByteArray {
        val (a, _) = expandSeed(seed)
        return encodePoint(scalarMul(a, B))
    }

    /** RFC 8032 §5.1.6 — deterministic signature of [message] with [seed]. */
    fun sign(seed: ByteArray, message: ByteArray): ByteArray {
        val (a, prefix) = expandSeed(seed)
        val publicKey = encodePoint(scalarMul(a, B))
        val r = littleEndianToBigInteger(sha512(prefix, message)).mod(L)
        val rEncoded = encodePoint(scalarMul(r, B)) // this IS the encoding of R
        val k = littleEndianToBigInteger(sha512(rEncoded, publicKey, message)).mod(L)
        val s = r.add(k.multiply(a)).mod(L)
        val signature = ByteArray(SIGNATURE_SIZE)
        rEncoded.copyInto(signature, 0, 0, 32)
        bigIntegerToLittleEndian(s, 32).copyInto(signature, 32)
        return signature
    }

    /**
     * RFC 8032 §5.1.7 — verification with the strict gates:
     * S < L, y < p on decode, and the fully cofactored equation
     * [8][S]B == [8]R + [8][k]A.
     */
    fun verify(publicKey: ByteArray, message: ByteArray, signature: ByteArray): Boolean {
        if (signature.size != SIGNATURE_SIZE) return false
        if (publicKey.size != PUBLIC_KEY_SIZE) return false

        // S must be in [1, L-1] — little-endian decode of the second half.
        val sBytes = signature.copyOfRange(32, 64)
        val s = littleEndianToBigInteger(sBytes)
        if (s >= L || s.signum() == 0) return false

        val a = try {
            decodePoint(publicKey)
        } catch (_: IllegalArgumentException) {
            return false
        }
        val rPoint = try {
            decodePoint(signature.copyOfRange(0, 32))
        } catch (_: IllegalArgumentException) {
            return false
        }

        val k = littleEndianToBigInteger(sha512(signature.copyOfRange(0, 32), publicKey, message))

        // [8][S]B == [8]R + [8][k]A  (cofactor 8 on BOTH sides, per RFC text)
        val left = scalarMul(BigInteger.valueOf(8).multiply(s), B)
        val right = add(scalarMul(BigInteger.valueOf(8), rPoint), scalarMul(BigInteger.valueOf(8).multiply(k), a))
        return left.x.compareTo(right.x) == 0 && left.y.compareTo(right.y) == 0
    }

    /** Internal access for the golden-vector test: encodes a point for cross-checks. */
    internal fun testHooks() = Triple(L, P, D)
}
