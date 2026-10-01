/*
 * Copyright (C) 2025 The DRS Smart Keyboard Project
 */

package com.drs.smartkeyboard.drs

import android.content.Context
import com.drs.smartkeyboard.ime.theme.ThemeManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * DRS M2.4 — the theme gateway: ONE shared [ThemeManager] instance for
 * the whole process (created eagerly on first access from the app
 * context, never per-screen), plus a deterministic SAVE GENERATION — a
 * monotonic counter that bumps on every published theme save.
 *
 * The generation answers a real question the editor had no answer for:
 * "did THIS save actually re-color the keyboard?" — the editor compares
 * the generation before and after its publish; a bump means the live
 * keyboard picked the change up. Listeners (diagnostics, the theme
 * editor preview badge) subscribe to [generation]. Pure JVM, pinned by
 * DrsM2DesignTests.
 */
object DrsThemeService {

    @Volatile
    private var shared: ThemeManager? = null

    private val _generation = MutableStateFlow(0L)
    val generation: StateFlow<Long> = _generation.asStateFlow()

    /**
     * The shared ThemeManager — one instance per process, created from
     * the APPLICATION context so no screen can leak its own context
     * into the theme engine.
     */
    fun shared(context: Context): ThemeManager =
        shared ?: synchronized(this) {
            shared ?: ThemeManager(context.applicationContext).also { shared = it }
        }

    /**
     * Bumps the save generation and stamps its timestamp — called AFTER a
     * theme save was published to the live keyboard. Deterministic: two
     * consecutive calls always differ by exactly one.
     */
    fun notifyPublished(): Long = synchronized(this) {
        val next = _generation.value + 1L
        _generation.value = next
        next
    }

    /** Current generation (0 = nothing published yet this process). */
    fun currentGeneration(): Long = _generation.value

    /**
     * The generation snapshot to take BEFORE a save starts — the pair
     * (before, after) is what the editor compares. Pure and testable.
     */
    fun snapshotBeforeSave(): Long = currentGeneration()

    /** True when the (before, after) pair proves the keyboard re-colored. */
    fun didRecolor(before: Long, after: Long): Boolean = after > before
}
