/*
 * Copyright (C) 2025-2026 The DRS Smart Keyboard Project
 */

package com.drs.smartkeyboard.drs

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import java.io.ByteArrayInputStream
import java.io.InputStream

/**
 * DRS p9 (T-6): the bounded 64KiB-chunk read extracted from
 * DrsBackup.parseFrom. All streams are in-memory; a byte-for-byte wrapper
 * (one byte per read) verifies the cap logic is independent of the
 * provider's chunking.
 */
class DrsBackupBoundedReadTest : FunSpec({

    fun bytesOf(size: Int, seed: Int = 0x41): ByteArray =
        ByteArray(size) { i -> ((seed + i) % 127).toByte() }

    fun streamOf(bytes: ByteArray): InputStream = ByteArrayInputStream(bytes)

    test("a stream under the cap is read fully") {
        val data = bytesOf(1000)
        val read = DrsBackup.readBounded(streamOf(data), maxBytes = 4096)
        read.shouldNotBeNull()
        read.contentEquals(data).shouldBeTrue()
    }

    test("a stream of exactly the cap is read fully") {
        val data = bytesOf(64)
        val read = DrsBackup.readBounded(streamOf(data), maxBytes = 64)
        read.shouldNotBeNull()
        read.contentEquals(data).shouldBeTrue()
    }

    test("one byte over the cap returns null") {
        val data = bytesOf(65)
        DrsBackup.readBounded(streamOf(data), maxBytes = 64).shouldBeNull()
    }

    test("a cap landing mid-UTF-8-sequence returns null instead of a corrupt buffer") {
        // 'é' is two bytes (0xC3 0xA9): "éééé" is 8 bytes. Caps of 3 and 7
        // would split a sequence — the helper must refuse, never truncate.
        val text = "éééé".toByteArray(Charsets.UTF_8)
        text.size shouldBe 8
        DrsBackup.readBounded(streamOf(text), maxBytes = 3).shouldBeNull()
        DrsBackup.readBounded(streamOf(text), maxBytes = 7).shouldBeNull()
        val full = DrsBackup.readBounded(streamOf(text), maxBytes = 8)
        full.shouldNotBeNull()
        full.contentEquals(text).shouldBeTrue()
    }

    test("an empty stream yields an empty byte array, not null") {
        val read = DrsBackup.readBounded(streamOf(ByteArray(0)), maxBytes = 64)
        read.shouldNotBeNull()
        read.size shouldBe 0
    }

    test("a one-byte-at-a-time stream that exceeds the cap returns null") {
        val data = bytesOf(70)
        val slow = object : InputStream() {
            var pos = 0
            override fun read(): Int =
                if (pos < data.size) data[pos++].toInt() and 0xFF else -1
        }
        DrsBackup.readBounded(slow, maxBytes = 64).shouldBeNull()
    }
})
