/*
 * Copyright (C) 2025 The DRS Smart Keyboard Project
 */

package com.drs.smartkeyboard.ime.smartbar.quickaction

import com.drs.smartkeyboard.ime.text.keyboard.TextKeyData
import io.kotest.core.spec.style.FunSpec
import io.kotest.datatest.withData
import io.kotest.matchers.shouldBe

class QuickActionArrangementTest : FunSpec({
    context("contains behavior") {
        withData(
            Triple(
                QuickActionArrangement(
                    stickyAction = null,
                    dynamicActions = listOf(),
                    hiddenActions = listOf(),
                ),
                QuickAction.InsertKey(TextKeyData.SETTINGS),
                false,
            ),
            Triple(
                QuickActionArrangement(
                    stickyAction = QuickAction.InsertKey(TextKeyData.SETTINGS),
                    dynamicActions = listOf(),
                    hiddenActions = listOf(),
                ),
                QuickAction.InsertKey(TextKeyData.SETTINGS),
                true,
            ),
            Triple(
                QuickActionArrangement(
                    stickyAction = null,
                    dynamicActions = listOf(QuickAction.InsertKey(TextKeyData.SETTINGS)),
                    hiddenActions = listOf(),
                ),
                QuickAction.InsertKey(TextKeyData.SETTINGS),
                true,
            ),
            Triple(
                QuickActionArrangement(
                    stickyAction = null,
                    dynamicActions = listOf(),
                    hiddenActions = listOf(QuickAction.InsertKey(TextKeyData.SETTINGS)),
                ),
                QuickAction.InsertKey(TextKeyData.SETTINGS),
                true,
            ),
        ) { (arrangement, action, expectedContains) ->
            arrangement.contains(action) shouldBe expectedContains
        }
    }

    context("distinct behavior") {
        withData(
            QuickActionArrangement(
                stickyAction = null,
                dynamicActions = listOf(),
                hiddenActions = listOf(),
            ) to QuickActionArrangement(
                stickyAction = null,
                dynamicActions = listOf(),
                hiddenActions = listOf(),
            ),
            QuickActionArrangement(
                stickyAction = QuickAction.InsertKey(TextKeyData.SETTINGS),
                dynamicActions = listOf(),
                hiddenActions = listOf(),
            ) to QuickActionArrangement(
                stickyAction = QuickAction.InsertKey(TextKeyData.SETTINGS),
                dynamicActions = listOf(),
                hiddenActions = listOf(),
            ),
            QuickActionArrangement(
                stickyAction = QuickAction.InsertKey(TextKeyData.SETTINGS),
                dynamicActions = listOf(
                    QuickAction.InsertKey(TextKeyData.SETTINGS),
                ),
                hiddenActions = listOf(),
            ) to QuickActionArrangement(
                stickyAction = QuickAction.InsertKey(TextKeyData.SETTINGS),
                dynamicActions = listOf(),
                hiddenActions = listOf(),
            ),
            QuickActionArrangement(
                stickyAction = QuickAction.InsertKey(TextKeyData.SETTINGS),
                dynamicActions = listOf(
                    QuickAction.InsertKey(TextKeyData.SETTINGS),
                    QuickAction.InsertKey(TextKeyData.SETTINGS),
                ),
                hiddenActions = listOf(),
            ) to QuickActionArrangement(
                stickyAction = QuickAction.InsertKey(TextKeyData.SETTINGS),
                dynamicActions = listOf(),
                hiddenActions = listOf(),
            ),
            QuickActionArrangement(
                stickyAction = QuickAction.InsertKey(TextKeyData.SETTINGS),
                dynamicActions = listOf(
                    QuickAction.InsertKey(TextKeyData.CLIPBOARD_SELECT_ALL),
                ),
                hiddenActions = listOf(
                    QuickAction.InsertKey(TextKeyData.VIEW_SYMBOLS),
                ),
            ) to QuickActionArrangement(
                stickyAction = QuickAction.InsertKey(TextKeyData.SETTINGS),
                dynamicActions = listOf(
                    QuickAction.InsertKey(TextKeyData.CLIPBOARD_SELECT_ALL),
                ),
                hiddenActions = listOf(
                    QuickAction.InsertKey(TextKeyData.VIEW_SYMBOLS),
                ),
            ),
            QuickActionArrangement(
                stickyAction = null,
                dynamicActions = listOf(
                    QuickAction.InsertKey(TextKeyData.CLIPBOARD_SELECT_ALL),
                ),
                hiddenActions = listOf(
                    QuickAction.InsertKey(TextKeyData.CLIPBOARD_SELECT_ALL),
                    QuickAction.InsertKey(TextKeyData.VIEW_SYMBOLS),
                ),
            ) to QuickActionArrangement(
                stickyAction = null,
                dynamicActions = listOf(
                    QuickAction.InsertKey(TextKeyData.CLIPBOARD_SELECT_ALL),
                ),
                hiddenActions = listOf(
                    QuickAction.InsertKey(TextKeyData.VIEW_SYMBOLS),
                ),
            ),
            QuickActionArrangement(
                stickyAction = null,
                dynamicActions = listOf(
                    QuickAction.InsertKey(TextKeyData.CLIPBOARD_SELECT_ALL),
                ),
                hiddenActions = listOf(
                    QuickAction.InsertKey(TextKeyData.VIEW_SYMBOLS),
                    QuickAction.InsertKey(TextKeyData.CLIPBOARD_SELECT_ALL),
                ),
            ) to QuickActionArrangement(
                stickyAction = null,
                dynamicActions = listOf(
                    QuickAction.InsertKey(TextKeyData.CLIPBOARD_SELECT_ALL),
                ),
                hiddenActions = listOf(
                    QuickAction.InsertKey(TextKeyData.VIEW_SYMBOLS),
                ),
            ),
        ) { (beforeDistinct, afterDistinct) ->
            beforeDistinct.distinct() shouldBe afterDistinct
        }
    }
})
