/*
 * Copyright (C) 2025 The DRS Smart Keyboard Project
 */

package com.drs.smartkeyboard.ime.window

import androidx.compose.ui.unit.dp
import io.kotest.assertions.assertSoftly
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.comparables.shouldBeLessThanOrEqualTo
import io.kotest.property.Arb
import io.kotest.property.arbitrary.enum
import io.kotest.property.checkAll

class ImeWindowConstraintsTest : FunSpec({
    val tolerance = 1e-3f.dp

    context("for all root insets and fixed modes") {
        test("default props are fully visible") {
            checkAll(Arb.rootInsets(), Arb.enum<ImeWindowMode.Fixed>()) { rootInsets, fixedMode ->
                val constraints = ImeWindowConstraints.of(rootInsets, fixedMode)
                val defaultProps = constraints.defaultProps

                assertSoftly {
                    defaultProps.shouldBeConstrainedTo(constraints, tolerance)
                }
            }
        }

        test("0.dp <= min <= def <= max keyboard width") {
            checkAll(Arb.rootInsets(), Arb.enum<ImeWindowMode.Fixed>()) { rootInsets, fixedMode ->
                val constraints = ImeWindowConstraints.of(rootInsets, fixedMode)

                assertSoftly {
                    // no tolerance here, potential rounding errors must be mitigated by constraints itself
                    0.dp.shouldBeLessThanOrEqualTo(constraints.minKeyboardWidth)
                    constraints.minKeyboardWidth.shouldBeLessThanOrEqualTo(constraints.defKeyboardWidth)
                    constraints.defKeyboardWidth.shouldBeLessThanOrEqualTo(constraints.maxKeyboardWidth)
                }
            }
        }

        test("0.dp <= min <= def <= max keyboard height") {
            checkAll(Arb.rootInsets(), Arb.enum<ImeWindowMode.Fixed>()) { rootInsets, fixedMode ->
                val constraints = ImeWindowConstraints.of(rootInsets, fixedMode)

                assertSoftly {
                    // no tolerance here, potential rounding errors must be mitigated by constraints itself
                    0.dp.shouldBeLessThanOrEqualTo(constraints.minKeyboardHeight)
                    constraints.minKeyboardHeight.shouldBeLessThanOrEqualTo(constraints.defKeyboardHeight)
                    constraints.defKeyboardHeight.shouldBeLessThanOrEqualTo(constraints.maxKeyboardHeight)
                }
            }
        }

        test("0.dp <= min <= def <= max padding horizontal") {
            checkAll(Arb.rootInsets(), Arb.enum<ImeWindowMode.Fixed>()) { rootInsets, fixedMode ->
                val constraints = ImeWindowConstraints.of(rootInsets, fixedMode)

                assertSoftly {
                    // no tolerance here, potential rounding errors must be mitigated by constraints itself
                    0.dp.shouldBeLessThanOrEqualTo(constraints.minPaddingHorizontal)
                    constraints.minPaddingHorizontal.shouldBeLessThanOrEqualTo(constraints.defPaddingHorizontal)
                    constraints.defPaddingHorizontal.shouldBeLessThanOrEqualTo(constraints.maxPaddingHorizontal)
                }
            }
        }
    }

    context("for all root insets and floating modes") {
        test("default props are fully visible") {
            checkAll(Arb.rootInsets(), Arb.enum<ImeWindowMode.Floating>()) { rootInsets, floatingMode ->
                val constraints = ImeWindowConstraints.of(rootInsets, floatingMode)
                val defaultProps = constraints.defaultProps

                assertSoftly {
                    defaultProps.shouldBeConstrainedTo(constraints, tolerance)
                }
            }
        }

        test("0.dp <= min <= def <= max keyboard width") {
            checkAll(Arb.rootInsets(), Arb.enum<ImeWindowMode.Floating>()) { rootInsets, floatingMode ->
                val constraints = ImeWindowConstraints.of(rootInsets, floatingMode)

                assertSoftly {
                    // no tolerance here, potential rounding errors must be mitigated by constraints itself
                    0.dp.shouldBeLessThanOrEqualTo(constraints.minKeyboardWidth)
                    constraints.minKeyboardWidth.shouldBeLessThanOrEqualTo(constraints.defKeyboardWidth)
                    constraints.defKeyboardWidth.shouldBeLessThanOrEqualTo(constraints.maxKeyboardWidth)
                }
            }
        }

        test("0.dp <= min <= def <= max keyboard height") {
            checkAll(Arb.rootInsets(), Arb.enum<ImeWindowMode.Floating>()) { rootInsets, floatingMode ->
                val constraints = ImeWindowConstraints.of(rootInsets, floatingMode)

                assertSoftly {
                    // no tolerance here, potential rounding errors must be mitigated by constraints itself
                    0.dp.shouldBeLessThanOrEqualTo(constraints.minKeyboardHeight)
                    constraints.minKeyboardHeight.shouldBeLessThanOrEqualTo(constraints.defKeyboardHeight)
                    constraints.defKeyboardHeight.shouldBeLessThanOrEqualTo(constraints.maxKeyboardHeight)
                }
            }
        }
    }
})
