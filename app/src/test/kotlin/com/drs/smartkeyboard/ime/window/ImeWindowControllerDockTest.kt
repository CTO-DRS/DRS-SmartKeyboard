/*
 * Copyright (C) 2026 The DRS Smart Keyboard Project
 */

package com.drs.smartkeyboard.ime.window

import androidx.compose.ui.unit.dp
import com.drs.smartkeyboard.app.DrsPreferenceModel
import io.kotest.core.spec.style.FunSpec
import io.kotest.engine.coroutines.backgroundScope
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kotest.property.Arb
import io.kotest.property.arbitrary.enum
import io.kotest.property.checkAll
import kotlinx.coroutines.flow.first
import org.drs.jetpref.datastore.jetprefDataStoreOf

/**
 * Regression tests for the DRS p7 (E2-6) fix: the dock decision in
 * [ImeWindowController.Editor.endMoveGesture] must be computed synchronously
 * from the gesture-end spec (offsetBottom vs dockToFixedHeight) and committed
 * into the editor state in the same call, instead of being read from a stale
 * initializer while the config update runs in a coroutine.
 */
class ImeWindowControllerDockTest : FunSpec({

    coroutineTestScope = true

    context("floating window move gestures") {
        test("ending a move above the dock threshold keeps the editor active") {
            checkAll(Arb.rootInsets()) { rootInsets ->
                val prefs by jetprefDataStoreOf(DrsPreferenceModel::class)
                val windowController = ImeWindowController(prefs, backgroundScope)
                windowController.updateRootInsets(rootInsets)
                windowController.updateWindowConfig {
                    ImeWindowConfig(ImeWindowMode.FLOATING, floatingMode = ImeWindowMode.Floating.NORMAL)
                }

                windowController.editor.beginMoveGesture()
                val specBefore = windowController.activeWindowSpec
                    .first { it !== ImeWindowSpec.Fallback }
                    .shouldBeInstanceOf<ImeWindowSpec.Floating>()
                val dock = specBefore.constraints.dockToFixedHeight

                val undockedSpec = specBefore.copy(
                    props = specBefore.props.copy(offsetBottom = dock + 1.dp),
                )
                windowController.editor.endMoveGesture(undockedSpec)

                windowController.editor.state.value shouldBe ImeWindowController.EditorState.ACTIVE
            }
        }

        test("ending a move exactly at the dock threshold docks the editor") {
            checkAll(Arb.rootInsets()) { rootInsets ->
                val prefs by jetprefDataStoreOf(DrsPreferenceModel::class)
                val windowController = ImeWindowController(prefs, backgroundScope)
                windowController.updateRootInsets(rootInsets)
                windowController.updateWindowConfig {
                    ImeWindowConfig(ImeWindowMode.FLOATING, floatingMode = ImeWindowMode.Floating.NORMAL)
                }

                windowController.editor.beginMoveGesture()
                val specBefore = windowController.activeWindowSpec
                    .first { it !== ImeWindowSpec.Fallback }
                    .shouldBeInstanceOf<ImeWindowSpec.Floating>()
                val dock = specBefore.constraints.dockToFixedHeight

                // Strict '>' comparison: offsetBottom == dockToFixedHeight must dock.
                val boundarySpec = specBefore.copy(
                    props = specBefore.props.copy(offsetBottom = dock),
                )
                windowController.editor.endMoveGesture(boundarySpec)

                windowController.editor.state.value shouldBe ImeWindowController.EditorState.INACTIVE
            }
        }

        test("ending a move below the dock threshold docks the editor") {
            checkAll(Arb.rootInsets()) { rootInsets ->
                val prefs by jetprefDataStoreOf(DrsPreferenceModel::class)
                val windowController = ImeWindowController(prefs, backgroundScope)
                windowController.updateRootInsets(rootInsets)
                windowController.updateWindowConfig {
                    ImeWindowConfig(ImeWindowMode.FLOATING, floatingMode = ImeWindowMode.Floating.NORMAL)
                }

                windowController.editor.beginMoveGesture()
                val specBefore = windowController.activeWindowSpec
                    .first { it !== ImeWindowSpec.Fallback }
                    .shouldBeInstanceOf<ImeWindowSpec.Floating>()
                val dock = specBefore.constraints.dockToFixedHeight

                val dockedSpec = specBefore.copy(
                    props = specBefore.props.copy(offsetBottom = dock - 1.dp),
                )
                windowController.editor.endMoveGesture(dockedSpec)

                windowController.editor.state.value shouldBe ImeWindowController.EditorState.INACTIVE
            }
        }

        test("ending a move on a fixed window keeps the editor active") {
            checkAll(Arb.rootInsets(), Arb.enum<ImeWindowMode.Fixed>()) { rootInsets, fixedMode ->
                val prefs by jetprefDataStoreOf(DrsPreferenceModel::class)
                val windowController = ImeWindowController(prefs, backgroundScope)
                windowController.updateRootInsets(rootInsets)
                windowController.updateWindowConfig {
                    ImeWindowConfig(ImeWindowMode.FIXED, fixedMode = fixedMode)
                }

                windowController.editor.beginMoveGesture()
                val specBefore = windowController.activeWindowSpec
                    .first { it !== ImeWindowSpec.Fallback }
                    .shouldBeInstanceOf<ImeWindowSpec.Fixed>()

                windowController.editor.endMoveGesture(specBefore)

                windowController.editor.state.value shouldBe ImeWindowController.EditorState.ACTIVE
            }
        }
    }
})
