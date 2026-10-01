/*
 * Copyright (C) 2026 The DRS Smart Keyboard Project
 */

package com.drs.smartkeyboard.ime.window

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import com.drs.smartkeyboard.shouldBeGreaterThanOrEqualTo
import com.drs.smartkeyboard.shouldBeLessThanOrEqualTo
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

fun ImeWindowSpec.shouldBeFixedNormal() = this
    .shouldBeInstanceOf<ImeWindowSpec.Fixed>()
    .also { fixedMode shouldBe ImeWindowMode.Fixed.NORMAL }

fun ImeWindowSpec.shouldBeFixedCompact() = this
    .shouldBeInstanceOf<ImeWindowSpec.Fixed>()
    .also { fixedMode shouldBe ImeWindowMode.Fixed.COMPACT }

fun ImeWindowProps.Fixed.shouldBeConstrainedTo(constraints: ImeWindowConstraints.Fixed, tolerance: Dp) {
    val rootBounds = constraints.rootBounds
    withClue("keyboard height should be constrained") {
        keyboardHeight.shouldBeGreaterThanOrEqualTo(constraints.minKeyboardHeight)
        keyboardHeight.shouldBeLessThanOrEqualTo(constraints.maxKeyboardHeight)
    }
    withClue("padding left+right should not push window out of root bounds") {
        paddingLeft.shouldBeGreaterThanOrEqualTo(0.dp)
        paddingRight.shouldBeGreaterThanOrEqualTo(0.dp)
        (paddingLeft + paddingRight).shouldBeGreaterThanOrEqualTo(constraints.minPaddingHorizontal, tolerance)
        (paddingLeft + paddingRight).shouldBeLessThanOrEqualTo(constraints.maxPaddingHorizontal, tolerance)
    }
    withClue("padding bottom should not push window out of root bounds") {
        paddingBottom.shouldBeGreaterThanOrEqualTo(0.dp)
        (paddingBottom + keyboardHeight).shouldBeLessThanOrEqualTo(rootBounds.height, tolerance)
    }
}

fun ImeWindowProps.Floating.shouldBeConstrainedTo(constraints: ImeWindowConstraints.Floating, tolerance: Dp) {
    val rootBounds = constraints.rootBounds
    withClue("keyboard height should be constrained") {
        keyboardHeight.shouldBeGreaterThanOrEqualTo(constraints.minKeyboardHeight)
        keyboardHeight.shouldBeLessThanOrEqualTo(constraints.maxKeyboardHeight)
    }
    withClue("keyboard width should be constrained") {
        keyboardWidth.shouldBeGreaterThanOrEqualTo(constraints.minKeyboardWidth)
        keyboardWidth.shouldBeLessThanOrEqualTo(constraints.maxKeyboardWidth)
    }
    withClue("offset left should not push window out of root bounds") {
        offsetLeft.shouldBeGreaterThanOrEqualTo(0.dp)
        offsetLeft.shouldBeLessThanOrEqualTo(rootBounds.width - keyboardWidth, tolerance)
    }
    withClue("offset bottom should not push window out of root bounds") {
        offsetBottom.shouldBeGreaterThanOrEqualTo(0.dp)
        offsetBottom.shouldBeLessThanOrEqualTo(rootBounds.height - keyboardHeight, tolerance)
    }
}
