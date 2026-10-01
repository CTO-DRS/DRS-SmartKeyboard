/*
 * Copyright (C) 2025 The DRS Smart Keyboard Project
 */

package org.drs.lib.kotlin

import kotlin.reflect.KClass

fun KClass<*>.simpleNameOrEnclosing(): String? {
    return if (this.simpleName == "Companion") {
        // Companion object => get the enclosing class
        this.java.enclosingClass.simpleName
    } else {
        // Normal object => directly get class
        this.simpleName
    }
}
