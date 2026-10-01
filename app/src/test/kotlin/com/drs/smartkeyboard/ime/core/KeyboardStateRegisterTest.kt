/*
 * Copyright (C) 2025-2026 The DRS Smart Keyboard Project
 */

// NOTE: the register lives in ime/keyboard/KeyboardState.kt (package
// com.drs.smartkeyboard.ime.keyboard), not in ime/core as the phase plan
// sketched. The test file path mirrors the planned location, the imports
// mirror the real package.

package com.drs.smartkeyboard.ime.core

import app.cash.turbine.test
import com.drs.smartkeyboard.ime.ImeUiMode
import com.drs.smartkeyboard.ime.input.InputModifierState
import com.drs.smartkeyboard.ime.keyboard.KeyboardMode
import com.drs.smartkeyboard.ime.keyboard.KeyboardState
import com.drs.smartkeyboard.ime.keyboard.ObservableKeyboardState
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

class KeyboardStateRegisterTest : FunSpec({

    coroutineTestScope = true

    context("packed ULong register layout") {
        test("non-adjacent regions round-trip without collisions") {
            val state = KeyboardState.new()

            state.keyboardMode = KeyboardMode.PHONE
            state.imeUiMode = ImeUiMode.MEDIA
            state.inputCtrlState = InputModifierState.LATCHED
            state.inputAltState = InputModifierState.LOCKED
            state.inputFnState = InputModifierState.LATCHED

            // Bits 0-3 keyboard mode, 18-19 CTRL latch, 24-26 ImeUiMode,
            // 28-29 ALT latch, 30-31 FN latch — pin the exact packed layout.
            val expectedRaw = 6uL or // KeyboardMode.PHONE
                (1uL shl 18) or // InputModifierState.LATCHED (CTRL)
                (1uL shl 24) or // ImeUiMode.MEDIA
                (2uL shl 28) or // InputModifierState.LOCKED (ALT)
                (1uL shl 30) // InputModifierState.LATCHED (FN)
            state.rawValue shouldBe expectedRaw

            state.keyboardMode shouldBe KeyboardMode.PHONE
            state.imeUiMode shouldBe ImeUiMode.MEDIA
            state.inputCtrlState shouldBe InputModifierState.LATCHED
            state.inputAltState shouldBe InputModifierState.LOCKED
            state.inputFnState shouldBe InputModifierState.LATCHED

            // And the reverse direction: build from the raw value alone.
            val rebuilt = KeyboardState.new(expectedRaw)
            rebuilt.keyboardMode shouldBe KeyboardMode.PHONE
            rebuilt.imeUiMode shouldBe ImeUiMode.MEDIA
            rebuilt.inputCtrlState shouldBe InputModifierState.LATCHED
            rebuilt.inputAltState shouldBe InputModifierState.LOCKED
            rebuilt.inputFnState shouldBe InputModifierState.LATCHED
        }

        test("flags are independent and individually clearable") {
            val state = KeyboardState.new()

            state.isIncognitoMode = true
            state.isMediaSearchActive = true
            state.isToolsDrawerVisible = true
            state.isSubtypeSelectionVisible = true

            state.isIncognitoMode shouldBe true
            state.isMediaSearchActive shouldBe true
            state.isToolsDrawerVisible shouldBe true
            state.isSubtypeSelectionVisible shouldBe true

            // Bits 15, 32, 33, 34 — non-adjacent flag layout, pinned raw.
            state.rawValue shouldBe (
                KeyboardState.F_IS_INCOGNITO_MODE or
                    KeyboardState.F_IS_MEDIA_SEARCH_ACTIVE or
                    KeyboardState.F_IS_TOOLS_DRAWER_VISIBLE or
                    KeyboardState.F_IS_SUBTYPE_SELECTION_VISIBLE
                )

            state.isIncognitoMode = false
            state.isIncognitoMode shouldBe false
            state.isMediaSearchActive shouldBe true
            state.isToolsDrawerVisible shouldBe true
            state.isSubtypeSelectionVisible shouldBe true

            state.isMediaSearchActive = false
            state.isToolsDrawerVisible = false
            state.isSubtypeSelectionVisible = false
            state.rawValue shouldBe 0uL
        }

        test("garbage keyboard-mode bits fall back to CHARACTERS") {
            // Raw value with keyboard-mode bits 0xF (=15) — no KeyboardMode has
            // this value, KeyboardMode.fromInt must fall back to CHARACTERS.
            val state = KeyboardState.new(0xFuL)
            state.keyboardMode shouldBe KeyboardMode.CHARACTERS
            state.keyboardMode shouldNotBe KeyboardMode.UNSPECIFIED
            state.rawValue shouldBe 0xFuL
        }
    }

    context("snapshot") {
        test("snapshot is an independent copy of the register") {
            val state = KeyboardState.new()
            state.keyboardMode = KeyboardMode.PHONE
            state.isIncognitoMode = true

            val snapshot = state.snapshot()
            snapshot.rawValue shouldBe state.rawValue

            state.keyboardMode = KeyboardMode.SYMBOLS
            state.isIncognitoMode = false

            snapshot.keyboardMode shouldBe KeyboardMode.PHONE
            snapshot.isIncognitoMode shouldBe true
            snapshot.rawValue shouldNotBe state.rawValue
        }
    }

    context("observable dispatching") {
        test("direct property write emits exactly one state, same-value rewrite emits nothing") {
            val state = ObservableKeyboardState.new()

            state.test {
                skipItems(1) // initial state dispatched in init {}

                state.imeUiMode = ImeUiMode.MEDIA
                val first = awaitItem()
                first.imeUiMode shouldBe ImeUiMode.MEDIA

                // Rewriting the same value must not dispatch (old != new guard
                // in the observable rawValue delegate).
                state.imeUiMode = ImeUiMode.MEDIA
                expectNoEvents()

                // The turbine must still be live after the suppressed write.
                state.imeUiMode = ImeUiMode.CLIPBOARD
                awaitItem().imeUiMode shouldBe ImeUiMode.CLIPBOARD

                // Dispatched values are independent snapshots: the earlier
                // emission must be untouched by the newer mutation.
                first.imeUiMode shouldBe ImeUiMode.MEDIA
                first.rawValue shouldNotBe state.rawValue
            }
        }

        test("batchEdit suppresses intermediate emissions until the block ends") {
            val state = ObservableKeyboardState.new()

            state.test {
                skipItems(1)

                state.batchEdit {
                    it.keyboardMode = KeyboardMode.PHONE
                    it.imeUiMode = ImeUiMode.MEDIA
                }

                // Exactly one emission: if intermediates leaked, the first
                // awaited item would carry imeUiMode TEXT.
                val after = awaitItem()
                after.keyboardMode shouldBe KeyboardMode.PHONE
                after.imeUiMode shouldBe ImeUiMode.MEDIA
            }
        }

        test("nested batches emit nothing until the outer batch ends") {
            val state = ObservableKeyboardState.new()

            state.test {
                skipItems(1)

                state.batchEdit { outer ->
                    outer.isIncognitoMode = true
                    state.batchEdit { inner ->
                        inner.imeUiMode = ImeUiMode.MEDIA
                    }
                    // The inner endBatchEdit must not dispatch while the outer
                    // batch is still active.
                    expectNoEvents()
                }

                val after = awaitItem()
                after.isIncognitoMode shouldBe true
                after.imeUiMode shouldBe ImeUiMode.MEDIA
            }
        }

        test("exception inside batchEdit propagates and still dispatches the final state") {
            val state = ObservableKeyboardState.new()

            state.test {
                skipItems(1)

                shouldThrow<IllegalStateException> {
                    state.batchEdit {
                        it.isIncognitoMode = true
                        throw IllegalStateException("boom")
                    }
                }

                // The finally-endBatchEdit must have dispatched the partial state.
                val after = awaitItem()
                after.isIncognitoMode shouldBe true
            }
        }
    }
})
