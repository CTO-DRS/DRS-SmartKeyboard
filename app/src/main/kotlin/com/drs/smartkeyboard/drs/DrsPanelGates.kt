/*
 * Copyright (C) 2025 The DRS Smart Keyboard Project
 */

package com.drs.smartkeyboard.drs

import com.drs.smartkeyboard.ime.text.key.KeyCode

/**
 * DRS v2.3.0 — «اللوحات الحيّة الصادقة»: العقد الخالص لبوابات السياق
 * على أسطح اللوحات الذكية.
 *
 * جولتا v2.2.1/v2.2.2 جعلتا الشريطين أعلى اللوحة حيَّين وصادقين: نبضة
 * موحدة واهتزاز عند كل تفاعل، وخانة تُعلن «غير متاح الآن» حين يكون فعلها
 * مستحيلًا (لاصقٍ بلا حافظة، لنسخٍ بلا تحديد). لكن اللوحات تحت الشريطين
 * بقيت خارج العقدين: لوحة الأدوات النصية ترسل CLIPBOARD_COPY/CUT/PASTE/
 * SELECT_ALL خامًا عبر المُرسل فتنتهي إلى لا شيء صامت في السياق نفسه الذي
 * يرفضه الشريط — مخالفة مباشرة لعقيدة «النقطة لا تكذب».
 *
 * هذا العائد يثبّت المجموعة المغلقة للأكواد التي تخضع للبوابة السياقية
 * على اللوحات — نفس البوابة الوحيدة (`ComputingEvaluator.evaluateEnabled`)
 * التي يسألها QuickActionButton وشريط المهام الموحد. نقيٌّ بنيويًا: بلا
 * UI ولا Context ولا حالة، فيُثبته المُجمِّع والاختبار معًا. كل كود خارج
 * هذه المجموعة يُقيَّم صادقًا «ممكنًا دائمًا» — تحويلات النص الحتمية
 * (الفرز، التسطير، الأختام) تعمل بلا سيد، وUNDO/REDO ليسا بوابتهما
 * المُقيِّم (المُحرِّك لا يتتبع رصة التراجع، فالكذب هنا سيكون في البوابة
 * لا في الزر).
 */
object DrsPanelGates {

    /**
     * The closed set of tool codes whose feasibility depends on the live
     * editor context (selection, clipboard, keyguard) — the very codes
     * `evaluateEnabled` owns on every other surface. Anything outside this
     * set is deterministically always-applicable.
     */
    val CONTEXT_GATED_TOOL_CODES: Set<Int> = setOf(
        KeyCode.CLIPBOARD_SELECT_ALL,
        KeyCode.CLIPBOARD_COPY,
        KeyCode.CLIPBOARD_CUT,
        KeyCode.CLIPBOARD_PASTE,
    )

    /** Deterministic membership test — the panel's single gate question. */
    fun isContextGated(code: Int): Boolean = code in CONTEXT_GATED_TOOL_CODES
}
