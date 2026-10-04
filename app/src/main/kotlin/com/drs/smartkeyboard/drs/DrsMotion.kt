/*
 * Copyright (C) 2025 The DRS Smart Keyboard Project
 */

package com.drs.smartkeyboard.drs

/**
 * DRS M2.2 — the motion contract of the Design-2030 surface. One source
 * of truth for the key-tap pulse: a press compresses the key to
 * [PULSE_PRESSED_SCALE] in [PULSE_DURATION_MS], and the release springs
 * back slower ([PULSE_RELEASE_DURATION_MS]) — the asymmetry is what makes
 * the tap feel physical instead of mechanical.
 *
 * DRS v2.2.1 — the same source of truth now owns the two strips above
 * the keyboard too: the toggle-dot grow/shrink contract and the
 * candidates' capped stagger entrance. All contracts are DRAW-ONLY
 * (graphicsLayer): layout is never re-measured per frame. Pinned by
 * DrsM2DesignTests and DrsV2210Tests.
 */
object DrsMotion {

    /** Pressed key scale — perceptibly compressed, not collapsed. */
    const val PULSE_PRESSED_SCALE = 0.94f

    /** Idle scale (the release target). */
    const val PULSE_IDLE_SCALE = 1f

    /** Press direction: fast — the finger already announced the intent. */
    const val PULSE_DURATION_MS = 60

    /** Release direction: slower spring back — the tactile afterglow. */
    const val PULSE_RELEASE_DURATION_MS = 140

    /** The animation spec durations for a given pressed state. */
    fun durationFor(pressed: Boolean): Int =
        if (pressed) PULSE_DURATION_MS else PULSE_RELEASE_DURATION_MS

    /** The target scale for a given pressed state. */
    fun scaleFor(pressed: Boolean): Float =
        if (pressed) PULSE_PRESSED_SCALE else PULSE_IDLE_SCALE

    // DRS v2.2.1 — «الشريطان الحيّان»: عقدا الحركة للشريطين أعلى اللوحة
    // (شريط المهام الموحد وشريط الاقتراحات). نقيّان بنيويًا مثل عقد النبضة
    // أعلاه: بلا UI ولا Context، فيُثبتهما المُجمِّع والاختبار معًا.

    /**
     * The toggle dot contract (نقطة التفعيل): the dot GROWS into place
     * when a toggle turns on and shrinks away when it turns off — an
     * appearing state must feel additive, a disappearing one must feel
     * cheaper than the appearance. Draw-only (graphicsLayer scale).
     */
    const val DOT_IDLE_SCALE = 0f

    /** The grown dot scale (the active target). */
    const val DOT_ACTIVE_SCALE = 1f

    /** Appear direction: quick enough to feel attached to the tap. */
    const val DOT_APPEAR_DURATION_MS = 140

    /** Disappear direction: faster than the appear — states fade, not linger. */
    const val DOT_DISAPPEAR_DURATION_MS = 90

    /** The dot scale for a given active state. */
    fun dotScaleFor(active: Boolean): Float =
        if (active) DOT_ACTIVE_SCALE else DOT_IDLE_SCALE

    /** The dot animation duration for a given active state. */
    fun dotDurationFor(active: Boolean): Int =
        if (active) DOT_APPEAR_DURATION_MS else DOT_DISAPPEAR_DURATION_MS

    /**
     * The candidate entrance contract (دخول المرشحين): suggestions in the
     * Smartbar fade/slide in with a fixed per-index step so the row reads
     * left-to-right (start-to-end) in one quick wave, never a pop-in.
     * The step is small (one frame-and-a-half class) and the total wait
     * is CAPPED — a deep index must never feel laggy; the cap keeps the
     * whole wave inside ~1/5 of a second no matter how wide the row is.
     */
    const val ENTRANCE_DURATION_MS = 130

    /** Delay added per candidate index. */
    const val ENTRANCE_STAGGER_STEP_MS = 28

    /** The hard ceiling on any single candidate's delay. */
    const val ENTRANCE_STAGGER_MAX_MS = 112

    /**
     * The deterministic entrance delay for [index]: 0 for the first
     * candidate (and defensively for any negative index), stepping by
     * [ENTRANCE_STAGGER_STEP_MS] and clamped at [ENTRANCE_STAGGER_MAX_MS].
     */
    fun staggerFor(index: Int): Int =
        (index.coerceAtLeast(0) * ENTRANCE_STAGGER_STEP_MS).coerceAtMost(ENTRANCE_STAGGER_MAX_MS)

    // DRS v2.2.2 — «الشريطان الصادقان سياقيًا»: عقدا الإتاحة والإيقاع.
    // نقيا بنيويًا كأسلافهما: بلا UI ولا Context، فيُثبتهما المُجمِّع
    // والاختبار معًا.

    /**
     * The honest-disabled visual contract (عقد الخانة المعطَّلة سياقيًا):
     * a context-gated slot renders at this alpha so «غير متاح الآن» reads
     * at a glance WITHOUT pretending the slot vanished — the tile stays
     * in place, keeps its slot-editor long-press, and merely refuses to
     * act. Draw-only (graphicsLayer alpha), zero geometry change.
     */
    const val DISABLED_SLOT_ALPHA = 0.38f

    /**
     * The motion-respect contract (عقد احترام الإيقاع): when the system
     * «remove animations» accessibility switch is on (ANIMATOR_DURATION_SCALE
     * = 0), every DrsMotion duration SNAPS to 0 — states still land on the
     * same final targets, they just stop animating. The contract is a pure
     * passthrough while motion is enabled, so the pinned v2.2.1 timings
     * are untouched in the default world.
     */
    fun durationOrSnap(durationMs: Int, motionEnabled: Boolean): Int =
        if (motionEnabled) durationMs else 0

    /**
     * The motion-respect twin of [staggerFor]: the capped entrance delay
     * for [index], or a full snap to 0 when motion is disabled — the wave
     * collapses to an instant, ordered-nothing appearance.
     */
    fun staggerOrSnap(index: Int, motionEnabled: Boolean): Int =
        if (motionEnabled) staggerFor(index) else 0
}
