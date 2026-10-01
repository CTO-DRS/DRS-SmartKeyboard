/*
 * Copyright (C) 2025-2026 The DRS Smart Keyboard Project
 */

package com.drs.smartkeyboard.ime.window

import androidx.compose.ui.unit.dp
import com.drs.smartkeyboard.app.DrsPreferenceModel
import com.drs.smartkeyboard.plusOrMinus
import com.drs.smartkeyboard.shouldBeGreaterThanOrEqualTo
import com.drs.smartkeyboard.shouldBeLessThanOrEqualTo
import org.drs.jetpref.datastore.jetprefDataStoreOf
import io.kotest.assertions.assertSoftly
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FunSpec
import io.kotest.engine.coroutines.backgroundScope
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kotest.property.Arb
import io.kotest.property.arbitrary.enum
import io.kotest.property.checkAll
import kotlinx.coroutines.flow.first

class ImeWindowControllerEditorMoveTest : FunSpec({
    val tolerance = 1e-3f.dp

    coroutineTestScope = true

    context("for all root insets and fixed modes") {
        test("for all upward moves") {
            checkAll(
                Arb.rootInsetsWithUpwardOffset(),
                Arb.enum<ImeWindowMode.Fixed>(),
            ) { (rootInsets, offset), fixedMode ->
                val prefs by jetprefDataStoreOf(DrsPreferenceModel::class)
                val windowController = ImeWindowController(prefs, backgroundScope)
                windowController.updateRootInsets(rootInsets)
                windowController.updateWindowConfig {
                    ImeWindowConfig(ImeWindowMode.FIXED, fixedMode = fixedMode)
                }

                windowController.editor.beginMoveGesture()
                val specBefore = windowController.activeWindowSpec.first { it !== ImeWindowSpec.Fallback }
                val specCalculated = specBefore.movedBy(offset, 4, 0)
                windowController.editor.onSpecUpdated(specCalculated)
                val specAfter = windowController.activeWindowSpec.value
                windowController.editor.endMoveGesture(specAfter)

                assertSoftly {
                    val specBefore = specBefore.shouldBeInstanceOf<ImeWindowSpec.Fixed>()
                    val specAfter = specAfter.shouldBeInstanceOf<ImeWindowSpec.Fixed>()
                    specCalculated.shouldBeInstanceOf<ImeWindowSpec.Fixed>().shouldBe(specAfter)
                    withClue("move operations must not alter keyboard height") {
                        specAfter.props.keyboardHeight shouldBe specBefore.props.keyboardHeight.plusOrMinus(tolerance)
                    }
                    withClue("move operations upward must not alter padding left") {
                        specAfter.props.paddingLeft shouldBe specBefore.props.paddingLeft.plusOrMinus(tolerance)
                    }
                    withClue("move operations upward must not alter padding right") {
                        specAfter.props.paddingRight shouldBe specBefore.props.paddingRight.plusOrMinus(tolerance)
                    }
                    withClue("move operations upward must not decrease padding bottom") {
                        specAfter.props.paddingBottom.shouldBeGreaterThanOrEqualTo(specBefore.props.paddingBottom, tolerance)
                    }
                    withClue("move operations upward must not push window out of root bounds") {
                        specAfter.props.shouldBeConstrainedTo(specAfter.constraints, tolerance)
                    }
                }
            }
        }

        test("for all downward moves") {
            checkAll(
                Arb.rootInsetsWithDownwardOffset(),
                Arb.enum<ImeWindowMode.Fixed>(),
            ) { (rootInsets, offset), fixedMode ->
                val prefs by jetprefDataStoreOf(DrsPreferenceModel::class)
                val windowController = ImeWindowController(prefs, backgroundScope)
                windowController.updateRootInsets(rootInsets)
                windowController.updateWindowConfig {
                    ImeWindowConfig(ImeWindowMode.FIXED, fixedMode = fixedMode)
                }

                windowController.editor.beginMoveGesture()
                val specBefore = windowController.activeWindowSpec.first { it !== ImeWindowSpec.Fallback }
                val specCalculated = specBefore.movedBy(offset, 4, 0)
                windowController.editor.onSpecUpdated(specCalculated)
                val specAfter = windowController.activeWindowSpec.value
                windowController.editor.endMoveGesture(specAfter)

                assertSoftly {
                    val specBefore = specBefore.shouldBeInstanceOf<ImeWindowSpec.Fixed>()
                    val specAfter = specAfter.shouldBeInstanceOf<ImeWindowSpec.Fixed>()
                    specCalculated.shouldBeInstanceOf<ImeWindowSpec.Fixed>().shouldBe(specAfter)
                    withClue("move operations must not alter keyboard height") {
                        specAfter.props.keyboardHeight shouldBe specBefore.props.keyboardHeight.plusOrMinus(tolerance)
                    }
                    withClue("move operations downward must not alter padding left") {
                        specAfter.props.paddingLeft shouldBe specBefore.props.paddingLeft.plusOrMinus(tolerance)
                    }
                    withClue("move operations downward must not alter padding right") {
                        specAfter.props.paddingRight shouldBe specBefore.props.paddingRight.plusOrMinus(tolerance)
                    }
                    withClue("move operations downward must not increase padding bottom") {
                        specAfter.props.paddingBottom.shouldBeLessThanOrEqualTo(specBefore.props.paddingBottom, tolerance)
                    }
                    withClue("move operations downward must not push window out of root bounds") {
                        specAfter.props.shouldBeConstrainedTo(specAfter.constraints, tolerance)
                    }
                }
            }
        }

        test("for all leftward moves") {
            checkAll(
                Arb.rootInsetsWithLeftwardOffset(),
                Arb.enum<ImeWindowMode.Fixed>(),
            ) { (rootInsets, offset), fixedMode ->
                val prefs by jetprefDataStoreOf(DrsPreferenceModel::class)
                val windowController = ImeWindowController(prefs, backgroundScope)
                windowController.updateRootInsets(rootInsets)
                windowController.updateWindowConfig {
                    ImeWindowConfig(ImeWindowMode.FIXED, fixedMode = fixedMode)
                }

                windowController.editor.beginMoveGesture()
                val specBefore = windowController.activeWindowSpec.first { it !== ImeWindowSpec.Fallback }
                val specCalculated = specBefore.movedBy(offset, 4, 0)
                windowController.editor.onSpecUpdated(specCalculated)
                val specAfter = windowController.activeWindowSpec.value
                windowController.editor.endMoveGesture(specAfter)

                assertSoftly {
                    val specBefore = specBefore.shouldBeInstanceOf<ImeWindowSpec.Fixed>()
                    val specAfter = specAfter.shouldBeInstanceOf<ImeWindowSpec.Fixed>()
                    specCalculated.shouldBeInstanceOf<ImeWindowSpec.Fixed>().shouldBe(specAfter)
                    withClue("move operations must not alter keyboard height") {
                        specAfter.props.keyboardHeight shouldBe specBefore.props.keyboardHeight.plusOrMinus(tolerance)
                    }
                    withClue("move operations leftward must not increase padding left") {
                        specAfter.props.paddingLeft.shouldBeLessThanOrEqualTo(specBefore.props.paddingLeft, tolerance)
                    }
                    withClue("move operations leftward must not decrease padding right") {
                        specAfter.props.paddingRight.shouldBeGreaterThanOrEqualTo(specBefore.props.paddingRight, tolerance)
                    }
                    withClue("move operations leftward must not alter padding bottom") {
                        specAfter.props.paddingBottom shouldBe specBefore.props.paddingBottom.plusOrMinus(tolerance)
                    }
                    withClue("move operations leftward must not push window out of root bounds") {
                        specAfter.props.shouldBeConstrainedTo(specAfter.constraints, tolerance)
                    }
                }
            }
        }

        test("for all rightward moves") {
            checkAll(
                Arb.rootInsetsWithRightwardOffset(),
                Arb.enum<ImeWindowMode.Fixed>(),
            ) { (rootInsets, offset), fixedMode ->
                val prefs by jetprefDataStoreOf(DrsPreferenceModel::class)
                val windowController = ImeWindowController(prefs, backgroundScope)
                windowController.updateRootInsets(rootInsets)
                windowController.updateWindowConfig {
                    ImeWindowConfig(ImeWindowMode.FIXED, fixedMode = fixedMode)
                }

                windowController.editor.beginMoveGesture()
                val specBefore = windowController.activeWindowSpec.first { it !== ImeWindowSpec.Fallback }
                val specCalculated = specBefore.movedBy(offset, 4, 0)
                windowController.editor.onSpecUpdated(specCalculated)
                val specAfter = windowController.activeWindowSpec.value
                windowController.editor.endMoveGesture(specAfter)

                assertSoftly {
                    val specBefore = specBefore.shouldBeInstanceOf<ImeWindowSpec.Fixed>()
                    val specAfter = specAfter.shouldBeInstanceOf<ImeWindowSpec.Fixed>()
                    specCalculated.shouldBeInstanceOf<ImeWindowSpec.Fixed>().shouldBe(specAfter)
                    withClue("move operations must not alter keyboard height") {
                        specAfter.props.keyboardHeight shouldBe specBefore.props.keyboardHeight.plusOrMinus(tolerance)
                    }
                    withClue("move operations rightward must not decrease padding left") {
                        specAfter.props.paddingLeft.shouldBeGreaterThanOrEqualTo(specBefore.props.paddingLeft, tolerance)
                    }
                    withClue("move operations rightward must not increase padding right") {
                        specAfter.props.paddingRight.shouldBeLessThanOrEqualTo(specBefore.props.paddingRight, tolerance)
                    }
                    withClue("move operations rightward must not alter padding bottom") {
                        specAfter.props.paddingBottom shouldBe specBefore.props.paddingBottom.plusOrMinus(tolerance)
                    }
                    withClue("move operations rightward must not push window out of root bounds") {
                        specAfter.props.shouldBeConstrainedTo(specAfter.constraints, tolerance)
                    }
                }
            }
        }
    }

    context("for all root insets and floating modes") {
        test("for all upward moves") {
            checkAll(
                Arb.rootInsetsWithUpwardOffset(),
                Arb.enum<ImeWindowMode.Floating>(),
            ) { (rootInsets, offset), floatingMode ->
                val prefs by jetprefDataStoreOf(DrsPreferenceModel::class)
                val windowController = ImeWindowController(prefs, backgroundScope)
                windowController.updateRootInsets(rootInsets)
                windowController.updateWindowConfig {
                    ImeWindowConfig(ImeWindowMode.FLOATING, floatingMode = floatingMode)
                }

                windowController.editor.beginMoveGesture()
                val specBefore = windowController.activeWindowSpec.first { it !== ImeWindowSpec.Fallback }
                val specCalculated = specBefore.movedBy(offset, 4, 0)
                windowController.editor.onSpecUpdated(specCalculated)
                val specAfter = windowController.activeWindowSpec.value
                windowController.editor.endMoveGesture(specAfter)

                assertSoftly {
                    val specBefore = specBefore.shouldBeInstanceOf<ImeWindowSpec.Floating>()
                    val specAfter = specAfter.shouldBeInstanceOf<ImeWindowSpec.Floating>()
                    specCalculated.shouldBeInstanceOf<ImeWindowSpec.Floating>().shouldBe(specAfter)
                    withClue("move operations must not alter keyboard height") {
                        specAfter.props.keyboardHeight shouldBe specBefore.props.keyboardHeight.plusOrMinus(tolerance)
                    }
                    withClue("move operations must not alter keyboard width") {
                        specAfter.props.keyboardWidth shouldBe specBefore.props.keyboardWidth.plusOrMinus(tolerance)
                    }
                    withClue("move operations upward must not alter offset left") {
                        specAfter.props.offsetLeft shouldBe specBefore.props.offsetLeft.plusOrMinus(tolerance)
                    }
                    withClue("move operations upward must not decrease offset bottom") {
                        specAfter.props.offsetBottom.shouldBeGreaterThanOrEqualTo(specBefore.props.offsetBottom, tolerance)
                    }
                    withClue("move operations upward must not push window out of root bounds") {
                        specAfter.props.shouldBeConstrainedTo(specAfter.constraints, tolerance)
                    }
                }
            }
        }

        test("for all downward moves") {
            checkAll(
                Arb.rootInsetsWithDownwardOffset(),
                Arb.enum<ImeWindowMode.Floating>(),
            ) { (rootInsets, offset), floatingMode ->
                val prefs by jetprefDataStoreOf(DrsPreferenceModel::class)
                val windowController = ImeWindowController(prefs, backgroundScope)
                windowController.updateRootInsets(rootInsets)
                windowController.updateWindowConfig {
                    ImeWindowConfig(ImeWindowMode.FLOATING, floatingMode = floatingMode)
                }

                windowController.editor.beginMoveGesture()
                val specBefore = windowController.activeWindowSpec.first { it !== ImeWindowSpec.Fallback }
                val specCalculated = specBefore.movedBy(offset, 4, 0)
                windowController.editor.onSpecUpdated(specCalculated)
                val specAfter = windowController.activeWindowSpec.value
                windowController.editor.endMoveGesture(specAfter)

                assertSoftly {
                    val specBefore = specBefore.shouldBeInstanceOf<ImeWindowSpec.Floating>()
                    val specAfter = specAfter.shouldBeInstanceOf<ImeWindowSpec.Floating>()
                    specCalculated.shouldBeInstanceOf<ImeWindowSpec.Floating>().shouldBe(specAfter)
                    withClue("move operations must not alter keyboard height") {
                        specAfter.props.keyboardHeight shouldBe specBefore.props.keyboardHeight.plusOrMinus(tolerance)
                    }
                    withClue("move operations must not alter keyboard width") {
                        specAfter.props.keyboardWidth shouldBe specBefore.props.keyboardWidth.plusOrMinus(tolerance)
                    }
                    withClue("move operations downward must not alter offset left") {
                        specAfter.props.offsetLeft shouldBe specBefore.props.offsetLeft.plusOrMinus(tolerance)
                    }
                    withClue("move operations downward must not increase offset bottom") {
                        specAfter.props.offsetBottom.shouldBeLessThanOrEqualTo(specBefore.props.offsetBottom, tolerance)
                    }
                    withClue("move operations downward must not push window out of root bounds") {
                        specAfter.props.shouldBeConstrainedTo(specAfter.constraints, tolerance)
                    }
                }
            }
        }

        test("for all leftward moves") {
            checkAll(
                Arb.rootInsetsWithLeftwardOffset(),
                Arb.enum<ImeWindowMode.Floating>(),
            ) { (rootInsets, offset), floatingMode ->
                val prefs by jetprefDataStoreOf(DrsPreferenceModel::class)
                val windowController = ImeWindowController(prefs, backgroundScope)
                windowController.updateRootInsets(rootInsets)
                windowController.updateWindowConfig {
                    ImeWindowConfig(ImeWindowMode.FLOATING, floatingMode = floatingMode)
                }

                windowController.editor.beginMoveGesture()
                val specBefore = windowController.activeWindowSpec.first { it !== ImeWindowSpec.Fallback }
                val specCalculated = specBefore.movedBy(offset, 4, 0)
                windowController.editor.onSpecUpdated(specCalculated)
                val specAfter = windowController.activeWindowSpec.value
                windowController.editor.endMoveGesture(specAfter)

                assertSoftly {
                    val specBefore = specBefore.shouldBeInstanceOf<ImeWindowSpec.Floating>()
                    val specAfter = specAfter.shouldBeInstanceOf<ImeWindowSpec.Floating>()
                    specCalculated.shouldBeInstanceOf<ImeWindowSpec.Floating>().shouldBe(specAfter)
                    withClue("move operations must not alter keyboard height") {
                        specAfter.props.keyboardHeight shouldBe specBefore.props.keyboardHeight.plusOrMinus(tolerance)
                    }
                    withClue("move operations must not alter keyboard width") {
                        specAfter.props.keyboardWidth shouldBe specBefore.props.keyboardWidth.plusOrMinus(tolerance)
                    }
                    withClue("move operations leftward must not increase offset left") {
                        specAfter.props.offsetLeft.shouldBeLessThanOrEqualTo(specBefore.props.offsetLeft, tolerance)
                    }
                    withClue("move operations leftward must not alter offset bottom") {
                        specAfter.props.offsetBottom shouldBe specBefore.props.offsetBottom.plusOrMinus(tolerance)
                    }
                    withClue("move operations leftward must not push window out of root bounds") {
                        specAfter.props.shouldBeConstrainedTo(specAfter.constraints, tolerance)
                    }
                }
            }
        }

        test("for all rightward moves") {
            checkAll(
                Arb.rootInsetsWithRightwardOffset(),
                Arb.enum<ImeWindowMode.Floating>(),
            ) { (rootInsets, offset), floatingMode ->
                val prefs by jetprefDataStoreOf(DrsPreferenceModel::class)
                val windowController = ImeWindowController(prefs, backgroundScope)
                windowController.updateRootInsets(rootInsets)
                windowController.updateWindowConfig {
                    ImeWindowConfig(ImeWindowMode.FLOATING, floatingMode = floatingMode)
                }

                windowController.editor.beginMoveGesture()
                val specBefore = windowController.activeWindowSpec.first { it !== ImeWindowSpec.Fallback }
                val specCalculated = specBefore.movedBy(offset, 4, 0)
                windowController.editor.onSpecUpdated(specCalculated)
                val specAfter = windowController.activeWindowSpec.value
                windowController.editor.endMoveGesture(specAfter)

                assertSoftly {
                    val specBefore = specBefore.shouldBeInstanceOf<ImeWindowSpec.Floating>()
                    val specAfter = specAfter.shouldBeInstanceOf<ImeWindowSpec.Floating>()
                    specCalculated.shouldBeInstanceOf<ImeWindowSpec.Floating>().shouldBe(specAfter)
                    withClue("move operations must not alter keyboard height") {
                        specAfter.props.keyboardHeight shouldBe specBefore.props.keyboardHeight.plusOrMinus(tolerance)
                    }
                    withClue("move operations must not alter keyboard width") {
                        specAfter.props.keyboardWidth shouldBe specBefore.props.keyboardWidth.plusOrMinus(tolerance)
                    }
                    withClue("move operations rightward must not decrease offset left") {
                        specAfter.props.offsetLeft.shouldBeGreaterThanOrEqualTo(specBefore.props.offsetLeft, tolerance)
                    }
                    withClue("move operations rightward must not alter offset bottom") {
                        specAfter.props.offsetBottom shouldBe specBefore.props.offsetBottom.plusOrMinus(tolerance)
                    }
                    withClue("move operations rightward must not push window out of root bounds") {
                        specAfter.props.shouldBeConstrainedTo(specAfter.constraints, tolerance)
                    }
                }
            }
        }
    }
})
