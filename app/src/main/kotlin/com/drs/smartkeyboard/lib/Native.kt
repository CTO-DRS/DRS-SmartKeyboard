/*
 * Copyright (C) 2022-2025 The DRS Smart Keyboard Project
 */

package com.drs.smartkeyboard.lib

/**
 * DRS v1.28.0 audit cleanup: this file previously carried a full native
 * toolbox (NativeStr converters, NativeInstanceWrapper) with ZERO callers
 * anywhere in the app — only NATIVE_NULLPTR was (mis)used as a literal 0 by
 * a handful of unrelated call sites. The dead surface is gone; the constant
 * and its alias stay because those call sites exist.
 */

/**
 * Type alias for a native pointer.
 */
typealias NativePtr = Long

/**
 * Constant value for a native null pointer.
 */
const val NATIVE_NULLPTR: NativePtr = 0L
