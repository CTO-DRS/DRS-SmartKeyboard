/*
 * Copyright (C) 2025 The DRS Smart Keyboard Project
 */

package com.drs.smartkeyboard.ime.window

import androidx.compose.ui.unit.dp
import app.cash.turbine.test
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
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kotest.property.Arb
import io.kotest.property.PropTestConfig
import io.kotest.property.arbitrary.enum
import io.kotest.property.assume
import io.kotest.property.checkAll

class ImeWindowControllerActionsTest : FunSpec({
    val tolerance = 1e-3f.dp

    coroutineTestScope = true

    test("toggleFloatingWindow()") {
        checkAll(
            Arb.enum<ImeWindowMode>(),
            Arb.enum<ImeWindowMode.Fixed>(),
            Arb.enum<ImeWindowMode.Floating>(),
        ) { mode, fixedMode, floatingMode ->
            val prefs by jetprefDataStoreOf(DrsPreferenceModel::class)
            val windowController = ImeWindowController(prefs, backgroundScope)
            val config = ImeWindowConfig(mode, fixedMode = fixedMode, floatingMode = floatingMode)

            windowController.activeWindowSpec.test {
                skipItems(1)
                windowController.updateWindowConfig { config }
                val specBefore = awaitItem()
                windowController.actions.toggleFloatingWindow()
                val specAfter = awaitItem()
                assertSoftly {
                    when (specBefore) {
                        is ImeWindowSpec.Fixed -> specAfter.shouldBeInstanceOf<ImeWindowSpec.Floating>()
                        is ImeWindowSpec.Floating -> specAfter.shouldBeInstanceOf<ImeWindowSpec.Fixed>()
                    }
                }
            }
        }
    }

    test("toggleCompactLayout()") {
        checkAll(
            Arb.enum<ImeWindowMode>(),
            Arb.enum<ImeWindowMode.Fixed>(),
            Arb.enum<ImeWindowMode.Floating>(),
        ) { mode, fixedMode, floatingMode ->
            val prefs by jetprefDataStoreOf(DrsPreferenceModel::class)
            val windowController = ImeWindowController(prefs, backgroundScope)
            val config = ImeWindowConfig(mode, fixedMode = fixedMode, floatingMode = floatingMode)

            windowController.activeWindowSpec.test {
                skipItems(1)
                windowController.updateWindowConfig { config }
                val specBefore = awaitItem()
                windowController.actions.toggleCompactLayout()
                val specAfter = awaitItem()
                assertSoftly {
                    when (specBefore) {
                        is ImeWindowSpec.Fixed -> when (specBefore.fixedMode) {
                            ImeWindowMode.Fixed.COMPACT -> specAfter.shouldBeFixedNormal()
                            else -> specAfter.shouldBeFixedCompact()
                        }
                        is ImeWindowSpec.Floating -> specAfter.shouldBeFixedCompact()
                    }
                }
            }
        }
    }

    test("compactLayoutToLeft()") {
        checkAll(Arb.rootInsets()) { rootInsets ->
            val prefs by jetprefDataStoreOf(DrsPreferenceModel::class)
            val windowController = ImeWindowController(prefs, backgroundScope)

            windowController.activeWindowSpec.test {
                skipItems(1)
                windowController.updateRootInsets(rootInsets)
                skipItems(1)
                windowController.actions.compactLayoutToLeft()
                val spec = awaitItem()
                assertSoftly {
                    val spec = spec.shouldBeFixedCompact()
                    spec.props.paddingLeft.shouldBeLessThanOrEqualTo(spec.props.paddingRight, tolerance)
                }
            }
        }
    }

    test("compactLayoutToRight()") {
        checkAll(Arb.rootInsets()) { rootInsets ->
            val prefs by jetprefDataStoreOf(DrsPreferenceModel::class)
            val windowController = ImeWindowController(prefs, backgroundScope)

            windowController.activeWindowSpec.test {
                skipItems(1)
                windowController.updateRootInsets(rootInsets)
                skipItems(1)
                windowController.actions.compactLayoutToRight()
                val spec = awaitItem()
                assertSoftly {
                    val spec = spec.shouldBeFixedCompact()
                    spec.props.paddingLeft.shouldBeGreaterThanOrEqualTo(spec.props.paddingRight, tolerance)
                }
            }
        }
    }

    test("compactLayoutFlipSide()") {
        checkAll(Arb.rootInsets()) { rootInsets ->
            assume(ImeWindowConstraints.of(rootInsets, ImeWindowMode.Fixed.COMPACT).minPaddingHorizontal > 0.dp)

            val prefs by jetprefDataStoreOf(DrsPreferenceModel::class)
            val windowController = ImeWindowController(prefs, backgroundScope)

            fun paddingsShouldBeFlipped(a: ImeWindowProps.Fixed, b: ImeWindowProps.Fixed) {
                a.paddingLeft shouldBe b.paddingRight.plusOrMinus(tolerance)
                a.paddingRight shouldBe b.paddingLeft.plusOrMinus(tolerance)
            }

            windowController.activeWindowSpec.test {
                skipItems(1)
                windowController.updateRootInsets(rootInsets)
                skipItems(1)
                windowController.actions.compactLayoutToRight()
                val specBefore = awaitItem()
                windowController.actions.compactLayoutFlipSide()
                val specAfterSplit1 = awaitItem()
                windowController.actions.compactLayoutFlipSide()
                val specAfterSplit2 = awaitItem()
                windowController.actions.compactLayoutFlipSide()
                val specAfterSplit3 = awaitItem()
                assertSoftly {
                    val specBefore = specBefore.shouldBeFixedCompact()
                    val specAfterSplit1 = specAfterSplit1.shouldBeFixedCompact()
                    val specAfterSplit2 = specAfterSplit2.shouldBeFixedCompact()
                    val specAfterSplit3 = specAfterSplit3.shouldBeFixedCompact()
                    paddingsShouldBeFlipped(specBefore.props, specAfterSplit1.props)
                    paddingsShouldBeFlipped(specAfterSplit1.props, specAfterSplit2.props)
                    paddingsShouldBeFlipped(specAfterSplit2.props, specAfterSplit3.props)
                }
            }
        }
    }

    test("resetFixedSize()") {
        checkAll(Arb.rootInsets(), Arb.enum<ImeWindowMode.Fixed>()) { rootInsets, fixedMode ->
            val config = ImeWindowConfig(
                mode = ImeWindowMode.FIXED,
                fixedMode = fixedMode,
                fixedProps = mapOf(fixedMode to ImeWindowProps.Fixed(
                    keyboardHeight = 100.dp,
                    paddingLeft = 0.dp,
                    paddingRight = 0.dp,
                    paddingBottom = 0.dp,
                )),
            )

            val prefs by jetprefDataStoreOf(DrsPreferenceModel::class)
            prefs.keyboard.windowConfig.set(mapOf(rootInsets.formFactor.typeGuess to config))
            val windowController = ImeWindowController(prefs, backgroundScope)

            windowController.activeWindowSpec.test {
                skipItems(1)
                windowController.updateRootInsets(rootInsets)
                val specBefore = awaitItem()
                windowController.actions.resetFixedSize()
                val specAfter = awaitItem()
                assertSoftly {
                    // DRS p9 (Q-A): the old assertion compared an
                    // ImeWindowProps against an ImeWindowSpec — different
                    // classes, so it could never fail. The reset contract is
                    // that the stored props are removed and the spec falls
                    // back to the constrained defaults for the mode.
                    val propsBefore = specBefore.shouldBeInstanceOf<ImeWindowSpec.Fixed>().props
                    val propsAfter = specAfter.shouldBeInstanceOf<ImeWindowSpec.Fixed>().props
                    val constraints = ImeWindowConstraints.of(rootInsets, fixedMode)
                    val constrainedDefaults = constraints.defaultProps.constrained(constraints)
                    // Some root-insets/form-factor combinations degenerate the
                    // 100.dp seed into exactly the constrained defaults (e.g.
                    // desktop roots clamp keyboard height to 0) — skip those
                    // iterations for the "seed must survive" half.
                    assume(propsBefore != constrainedDefaults)
                    withClue("resetFixedSize() must restore the constrained default props") {
                        propsAfter shouldBe constrainedDefaults
                    }
                    withClue("resetFixedSize() must actually replace the seeded custom props") {
                        propsBefore shouldNotBe propsAfter
                    }
                }
            }
        }
    }

    test("resetFloatingSize()") {
        // DRS p9 (Q-A): the assume() below legitimately discards degenerate
        // iterations (seed already equals the reset result on desktop roots);
        // measured discard rate reached 21% — over kotest's default 20% —
        // raising the budget to 40% for THIS test only keeps the honest
        // discard behavior without weakening any other property test.
        checkAll(PropTestConfig(maxDiscardPercentage = 40), Arb.rootInsets(), Arb.enum<ImeWindowMode.Floating>()) { rootInsets, floatingMode ->
            val config = ImeWindowConfig(
                mode = ImeWindowMode.FLOATING,
                floatingMode = floatingMode,
                floatingProps = mapOf(floatingMode to ImeWindowProps.Floating(
                    keyboardHeight = 100.dp,
                    keyboardWidth = 100.dp,
                    offsetLeft = 40.dp,
                    offsetBottom = 40.dp,
                )),
            )

            val prefs by jetprefDataStoreOf(DrsPreferenceModel::class)
            prefs.keyboard.windowConfig.set(mapOf(rootInsets.formFactor.typeGuess to config))
            val windowController = ImeWindowController(prefs, backgroundScope)

            windowController.activeWindowSpec.test {
                skipItems(1)
                windowController.updateRootInsets(rootInsets)
                val specBefore = awaitItem()
                windowController.actions.resetFloatingSize()
                val specAfter = awaitItem()
                assertSoftly {
                    // DRS p9 (Q-A): the old assertion compared an
                    // ImeWindowProps against an ImeWindowSpec — different
                    // classes, so it could never fail. resetFloatingSize()
                    // takes the STORED props, resets keyboardHeight/Width to
                    // the constraint defaults, keeps the offsets, and
                    // constrains the result (offsets may shift onscreen —
                    // mirrored by constraining the expected value here).
                    val propsBefore = specBefore.shouldBeInstanceOf<ImeWindowSpec.Floating>().props
                    val propsAfter = specAfter.shouldBeInstanceOf<ImeWindowSpec.Floating>().props
                    val constraints = ImeWindowConstraints.of(rootInsets, floatingMode)
                    val defProps = constraints.defaultProps.shouldBeInstanceOf<ImeWindowProps.Floating>()
                    val expectedProps = ImeWindowProps.Floating(
                        keyboardHeight = 100.dp,
                        keyboardWidth = 100.dp,
                        offsetLeft = 40.dp,
                        offsetBottom = 40.dp,
                    ).copy(
                        keyboardHeight = defProps.keyboardHeight,
                        keyboardWidth = defProps.keyboardWidth,
                    ).constrained(constraints)
                    // Skip degenerate iterations where the constrained seed is
                    // ALREADY identical to what a reset would produce (desktop
                    // roots normalize sizes to the same value regardless of
                    // the seed) — reset is unobservable there.
                    assume(propsBefore != expectedProps)
                    withClue("resetFloatingSize() must reset sizes to defaults while keeping offsets") {
                        propsAfter shouldBe expectedProps
                    }
                    withClue("resetFloatingSize() must actually replace the seeded custom props") {
                        propsBefore shouldNotBe propsAfter
                    }
                }
            }
        }
    }
})
