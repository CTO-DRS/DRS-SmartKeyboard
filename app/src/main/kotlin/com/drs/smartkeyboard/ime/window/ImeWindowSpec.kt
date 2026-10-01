/*
 * Copyright (C) 2025-2026 The DRS Smart Keyboard Project
 */

package com.drs.smartkeyboard.ime.window

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.coerceAtLeast
import androidx.compose.ui.unit.coerceAtMost
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.max
import androidx.compose.ui.unit.width
import kotlin.math.abs

/**
 * The window spec describes the computed window props and constraints which is used within the UI.
 */
sealed class ImeWindowSpec {
    /**
     * The computed window props, constrained to the root bounds.
     */
    abstract val props: ImeWindowProps

    /**
     * The constraints accompanying the root bounds and props.
     */
    abstract val constraints: ImeWindowConstraints

    abstract val userPreferredOptions: UserPreferredOptions

    val keyMarginH: Dp by lazy { props.calcKeyMarginH(constraints) * userPreferredOptions.keySpacingFactorH }

    val keyMarginV: Dp by lazy { props.calcKeyMarginV(constraints) * userPreferredOptions.keySpacingFactorV }

    val fontScale: Float by lazy { props.calcFontScale(constraints) * userPreferredOptions.fontScale }

    /**
     * Calculate how a move gesture would change the computed props.
     *
     * @param offset The offset by which the gesture moved. Must be a real number pair.
     * @param rowCount The effective row count. Must be between 4 and 6.
     * @param smartbarRowCount The effective Smartbar row count.
     *
     * @return A moved window spec, possibly unchanged.
     */
    abstract fun movedBy(
        offset: DpOffset,
        rowCount: Int,
        smartbarRowCount: Int,
    ): ImeWindowSpec

    /**
     * Calculate how a resize gesture would change the computed props.
     *
     * @param offset The offset by which the gesture resized. Must be a real number pair.
     * @param handle The resize handle. Determines from which angle the keyboard is resized.
     * @param rowCount The effective row count. Must be between 4 and 6.
     * @param smartbarRowCount The effective Smartbar row count.
     *
     * @return A resized window spec, possibly unchanged.
     */
    abstract fun resizedBy(
        offset: DpOffset,
        handle: ImeWindowResizeHandle,
        rowCount: Int,
        smartbarRowCount: Int,
    ): ImeWindowSpec

    /**
     * Calculates the row height for given baseline [keyboardHeight].
     *
     * DRS v1.0.8: the result is scaled by the user's keyboard height scale
     * ([UserPreferredOptions.heightScale]), so the preference really
     * changes the rendered keyboard: every consumer (row height, Smartbar
     * blend, touch bounds, resize conversions) derives from this method.
     */
    fun calcRowHeight(keyboardHeight: Dp): Dp {
        return keyboardHeight * userPreferredOptions.heightScale / constraints.baselineRowCount
    }

    /**
     * Calculates the Smartbar row height for given baseline [keyboardHeight].
     */
    fun calcSmartbarRowHeight(keyboardHeight: Dp): Dp {
        val defRowHeight = calcRowHeight(constraints.defKeyboardHeight)
        val rowHeight = calcRowHeight(keyboardHeight)
        return defRowHeight * constraints.smartbarStaticScalingFactor +
            rowHeight * constraints.smartbarDynamicScalingFactor
    }

    protected fun Dp.toEffective(rowCount: Int, smartbarRowCount: Int): Dp = let { keyboardHeight ->
        require(rowCount in 4..6)
        require(smartbarRowCount in 0..2)
        val effKeyboardHeight = calcRowHeight(keyboardHeight) * rowCount
        val effSmartbarHeight = calcSmartbarRowHeight(keyboardHeight) * smartbarRowCount
        return effKeyboardHeight + effSmartbarHeight
    }

    protected fun Dp.toBaseline(rowCount: Int, smartbarRowCount: Int): Dp  = let { effKeyboardHeight ->
        require(rowCount in 4..6)
        require(smartbarRowCount in 0..2)
        val staticSmartbarHeight = calcRowHeight(constraints.defKeyboardHeight * constraints.smartbarStaticScalingFactor * smartbarRowCount)
        val keyboardHeight = ((effKeyboardHeight - staticSmartbarHeight) * constraints.baselineRowCount) /
            (rowCount + constraints.smartbarDynamicScalingFactor * smartbarRowCount)
        return keyboardHeight
    }

    /**
     * Specialized spec for fixed mode.
     */
    data class Fixed(
        val fixedMode: ImeWindowMode.Fixed,
        override val props: ImeWindowProps.Fixed,
        override val constraints: ImeWindowConstraints.Fixed,
        override val userPreferredOptions: UserPreferredOptions,
    ) : ImeWindowSpec() {
        override fun movedBy(
            offset: DpOffset,
            rowCount: Int,
            smartbarRowCount: Int,
        ): ImeWindowSpec {
            var paddingLeft = props.paddingLeft
            var paddingRight = props.paddingRight
            var paddingBottom = props.paddingBottom

            if (offset.x < 0.dp) {
                // move to left
                val newPaddingLeft = (paddingLeft + offset.x)
                    .coerceAtLeast(0.dp)
                paddingRight -= (newPaddingLeft - paddingLeft)
                paddingLeft = newPaddingLeft
            } else {
                // move to right
                val newPaddingRight = (paddingRight - offset.x)
                    .coerceAtLeast(0.dp)
                paddingLeft -= (newPaddingRight - paddingRight)
                paddingRight = newPaddingRight
            }

            if (fixedMode == ImeWindowMode.Fixed.NORMAL && paddingLeft != 0.dp && paddingRight != 0.dp) {
                // maybe snap to center
                if (abs((paddingLeft - paddingRight).value).dp <= constraints.snapToCenterWidth) {
                    val avgPadding = (paddingLeft + paddingRight) / 2
                    paddingLeft = avgPadding
                    paddingRight = avgPadding
                }
            }

            paddingBottom = (paddingBottom - offset.y)
                .coerceAtMost(constraints.maxKeyboardHeight - props.keyboardHeight.toEffective(rowCount, smartbarRowCount))
                .coerceAtLeast(0.dp)

            val newProps = props.copy(
                paddingLeft = paddingLeft,
                paddingRight = paddingRight,
                paddingBottom = paddingBottom,
            ).constrained(constraints)
            val newSpec = copy(props = newProps)
            return newSpec
        }

        override fun resizedBy(
            offset: DpOffset,
            handle: ImeWindowResizeHandle,
            rowCount: Int,
            smartbarRowCount: Int,
        ): ImeWindowSpec {
            var keyboardHeight = props.keyboardHeight.toEffective(rowCount, smartbarRowCount)
            var paddingLeft = props.paddingLeft
            var paddingRight = props.paddingRight
            var paddingBottom = props.paddingBottom

            if (handle.top) {
                keyboardHeight = (keyboardHeight - offset.y)
                    .coerceAtLeast(constraints.minKeyboardHeight.toEffective(rowCount, smartbarRowCount))
                    .coerceAtMost(constraints.maxKeyboardHeight - paddingBottom)
            } else if (handle.bottom) {
                val newKeyboardHeight = (keyboardHeight + offset.y)
                    .coerceAtLeast(constraints.minKeyboardHeight.toEffective(rowCount, smartbarRowCount))
                    .coerceAtMost(keyboardHeight + paddingBottom)
                paddingBottom += (keyboardHeight - newKeyboardHeight)
                keyboardHeight = newKeyboardHeight
            }

            if (handle.left) {
                val newPaddingLeft = (paddingLeft + offset.x)
                    .coerceAtLeast(constraints.minPaddingHorizontal - paddingRight)
                    .coerceAtLeast(0.dp)
                    .coerceAtMost(max(constraints.rootBounds.width - paddingRight - constraints.minKeyboardWidth, 0.dp))
                paddingLeft = newPaddingLeft
            } else if (handle.right) {
                val newPaddingRight = (paddingRight - offset.x)
                    .coerceAtLeast(constraints.minPaddingHorizontal - paddingLeft)
                    .coerceAtLeast(0.dp)
                    .coerceAtMost(max(constraints.rootBounds.width - paddingLeft - constraints.minKeyboardWidth, 0.dp))
                paddingRight = newPaddingRight
            }

            val newProps = ImeWindowProps.Fixed(
                keyboardHeight = keyboardHeight.toBaseline(rowCount, smartbarRowCount),
                paddingLeft = paddingLeft,
                paddingRight = paddingRight,
                paddingBottom = paddingBottom,
            ).constrained(constraints)
            val newSpec = copy(props = newProps)
            return newSpec
        }
    }

    /**
     * Specialized spec for floating mode.
     */
    data class Floating(
        val floatingMode: ImeWindowMode.Floating,
        override val props: ImeWindowProps.Floating,
        override val constraints: ImeWindowConstraints.Floating,
        override val userPreferredOptions: UserPreferredOptions,
    ) : ImeWindowSpec() {
        override fun movedBy(
            offset: DpOffset,
            rowCount: Int,
            smartbarRowCount: Int,
        ): ImeWindowSpec {
            val newProps = props.copy(
                offsetLeft = props.offsetLeft + offset.x,
                offsetBottom = props.offsetBottom - offset.y,
            ).constrained(constraints)
            val newSpec = copy(props = newProps)
            return newSpec
        }

        override fun resizedBy(
            offset: DpOffset,
            handle: ImeWindowResizeHandle,
            rowCount: Int,
            smartbarRowCount: Int,
        ): ImeWindowSpec {
            var keyboardHeight = props.keyboardHeight.toEffective(rowCount, smartbarRowCount)
            var keyboardWidth = props.keyboardWidth
            var offsetLeft = props.offsetLeft
            var offsetBottom = props.offsetBottom

            if (handle.top) {
                val offsetTop = constraints.rootBounds.height - keyboardHeight - offsetBottom
                keyboardHeight = (keyboardHeight - offset.y.coerceAtLeast(-offsetTop))
                    .coerceAtLeast(constraints.minKeyboardHeight.toEffective(rowCount, smartbarRowCount))
                    .coerceAtMost(constraints.maxKeyboardHeight)
            } else if (handle.bottom) {
                val newKeyboardHeight = (keyboardHeight + offset.y.coerceAtLeast(-offsetBottom))
                    .coerceAtLeast(constraints.minKeyboardHeight.toEffective(rowCount, smartbarRowCount))
                    .coerceAtMost(constraints.maxKeyboardHeight)
                offsetBottom -= (newKeyboardHeight - keyboardHeight)
                keyboardHeight = newKeyboardHeight
            }

            if (handle.left) {
                val newKeyboardWidth = (keyboardWidth - offset.x.coerceAtLeast(-offsetLeft))
                    .coerceIn(constraints.minKeyboardWidth..constraints.maxKeyboardWidth)
                    .coerceAtMost(constraints.rootBounds.width - offsetLeft)
                offsetLeft -= (newKeyboardWidth - keyboardWidth)
                keyboardWidth = newKeyboardWidth
            } else if (handle.right) {
                keyboardWidth = (keyboardWidth + offset.x)
                    .coerceIn(constraints.minKeyboardWidth..constraints.maxKeyboardWidth)
                    .coerceAtMost(constraints.rootBounds.width - offsetLeft)
            }

            val newProps = ImeWindowProps.Floating(
                keyboardHeight = keyboardHeight.toBaseline(rowCount, smartbarRowCount),
                keyboardWidth = keyboardWidth,
                offsetLeft = offsetLeft,
                offsetBottom = offsetBottom,
            ).constrained(constraints)
            val newSpec = copy(props = newProps)
            return newSpec
        }
    }

    data class UserPreferredOptions(
        val keySpacingFactorH: Float,
        val keySpacingFactorV: Float,
        val fontScale: Float,
        /**
         * DRS v1.0.8: multiplier applied to the keyboard height (and thus
         * every row/key height derived from it). 1f = default height; the
         * value is sanitized from the [companion object] bounds.
         */
        val heightScale: Float = 1f,
    )

    companion object {
        /**
         * DRS v1.0.8: bounds of the keyboard height scale preference, in
         * percent. Kept here so the slider, the controller and the unit
         * tests share one source of truth.
         */
        const val HEIGHT_SCALE_MIN_PERCENT = 80
        const val HEIGHT_SCALE_MAX_PERCENT = 130
        const val HEIGHT_SCALE_DEFAULT_PERCENT = 100

        /**
         * DRS v1.2.0: bounds of the Smartbar height scale preference, in
         * percent. Kept beside the keyboard height scale so the slider,
         * the sizing layer and the unit tests share one source of truth.
         * The scale is applied at the presentation layer
         * (ProvideKeyboardRowBaseHeight) so every Smartbar consumer
         * (Smartbar rows, clipboard/emoji headers, autofill chip height)
         * follows it, while the window resize math stays untouched.
         */
        const val SMARTBAR_HEIGHT_SCALE_MIN_PERCENT = 70
        const val SMARTBAR_HEIGHT_SCALE_MAX_PERCENT = 140
        const val SMARTBAR_HEIGHT_SCALE_DEFAULT_PERCENT = 100

        /**
         * DRS v1.6.0: bounds of the key-preview (popup) scale preference,
         * in percent, beside the two height scales so the slider, the
         * popup bounds provider and the unit tests share one source of
         * truth. Applied at the presentation layer only — window and row
         * math stay untouched.
         */
        const val KEY_PREVIEW_SCALE_MIN_PERCENT = 70
        const val KEY_PREVIEW_SCALE_MAX_PERCENT = 200
        const val KEY_PREVIEW_SCALE_DEFAULT_PERCENT = 100

        /**
         * DRS v1.7.0: bounds of the glide-trail WIDTH scale, in percent of
         * the density-correct baseline radius (20dp). Beside the other
         * visual scales so the slider, the trail draw call and the unit
         * tests share one source of truth. Applied at the presentation
         * layer only — window and row math stay untouched.
         */
        const val GLIDE_TRAIL_SCALE_MIN_PERCENT = 70
        const val GLIDE_TRAIL_SCALE_MAX_PERCENT = 200
        const val GLIDE_TRAIL_SCALE_DEFAULT_PERCENT = 100

        /**
         * DRS v1.7.0: bounds of the emoji SIZE scale, in percent of the
         * default grid cell (42dp) and glyph (22sp). Same contract as the
         * other presentation-layer scales.
         */
        const val EMOJI_SCALE_MIN_PERCENT = 70
        const val EMOJI_SCALE_MAX_PERCENT = 200
        const val EMOJI_SCALE_DEFAULT_PERCENT = 100

        /** Clamps a raw percent value into the valid scale range and converts it to a multiplier. */
        fun sanitizeHeightScale(percent: Int): Float {
            return percent.coerceIn(HEIGHT_SCALE_MIN_PERCENT, HEIGHT_SCALE_MAX_PERCENT) / 100f
        }

        /** DRS v1.2.0: same clamp-and-convert contract for the Smartbar scale. */
        fun sanitizeSmartbarHeightScale(percent: Int): Float {
            return percent.coerceIn(SMARTBAR_HEIGHT_SCALE_MIN_PERCENT, SMARTBAR_HEIGHT_SCALE_MAX_PERCENT) / 100f
        }

        /** DRS v1.6.0: same clamp-and-convert contract for the key-preview scale. */
        fun sanitizeKeyPreviewScale(percent: Int): Float {
            return percent.coerceIn(KEY_PREVIEW_SCALE_MIN_PERCENT, KEY_PREVIEW_SCALE_MAX_PERCENT) / 100f
        }

        /** DRS v1.7.0: same clamp-and-convert contract for the glide-trail width scale. */
        fun sanitizeGlideTrailScale(percent: Int): Float {
            return percent.coerceIn(GLIDE_TRAIL_SCALE_MIN_PERCENT, GLIDE_TRAIL_SCALE_MAX_PERCENT) / 100f
        }

        /** DRS v1.7.0: same clamp-and-convert contract for the emoji size scale. */
        fun sanitizeEmojiScale(percent: Int): Float {
            return percent.coerceIn(EMOJI_SCALE_MIN_PERCENT, EMOJI_SCALE_MAX_PERCENT) / 100f
        }

        /**
         * The fallback window spec. It does not guarantee the window fits into the root window, and assumes
         * the root window is zero-sized. In practice this is never used, but allows for always having rootInsets,
         * simplifying sme of the calculation logic.
         */
        val Fallback: ImeWindowSpec = Fixed(
            fixedMode = ImeWindowMode.Fixed.NORMAL,
            props = ImeWindowProps.Fixed(
                keyboardHeight = 250.dp,
                paddingLeft = 0.dp,
                paddingRight = 0.dp,
                paddingBottom = 0.dp,
            ),
            userPreferredOptions = UserPreferredOptions(
                keySpacingFactorH = 1f,
                keySpacingFactorV = 1f,
                fontScale = 1f,
            ),
            constraints = ImeWindowConstraints.Fixed.Normal(ImeInsets.Root.Zero),
        )
    }
}
