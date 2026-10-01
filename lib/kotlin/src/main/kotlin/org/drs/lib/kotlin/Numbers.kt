/*
 * Copyright (C) 2022-2025 The DRS Smart Keyboard Project
 */

package org.drs.lib.kotlin

fun Number.toStringWithoutDotZero(): String = this.toString().removeSuffix(".0")
