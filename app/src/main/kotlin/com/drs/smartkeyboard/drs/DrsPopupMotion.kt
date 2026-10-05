/*
 * Copyright (C) 2025 The DRS Smart Keyboard Project
 */

package com.drs.smartkeyboard.drs

/**
 * DRS v2.6.0 — «المنبثق الحي الصادق»: عقد حركة المنبثقات (الضغط المطوّل).
 *
 * الجولة 14: بعد أن غطّت عقيدتا الحركة والصدق الشريطين (v2.2.1/v2.2.2)
 * واللوحات الذكية (v2.3.0) واللوحة نفسها (v2.4.0)، بقي سطح واحد صامتًا:
 * المنبثقات. العنصر النشط فيها يتوهّج باللون فقط (محدد FOCUS) بلا نبضة
 * ولا اهتزاز، والصندوق يظهر فجأة بلا دخول — فجوة لغة، لا فجوة قدرة.
 *
 * نقيّ بنيويًا مثل سائر عقود [DrsMotion]: بلا UI ولا Context، فيثبته
 * المجمّع والاختبار معًا (DrsV2600Tests). ثلاثة أقسام:
 *
 * 1. الدخول: الصندوق ينمو من [ENTRANCE_SCALE_FROM] خلال [ENTRANCE_DURATION_MS]
 *    — أصغر وأسرع من موجة المرشحين (130ms) لأن المنبثق أثر لحظي ملتصق
 *    بالإصبع لا سطحًا قائمًا بذاته.
 * 2. التمايل الداخلي: عناصر المنبثق الممتد تدخل بخطوة [ELEMENT_STAGGER_STEP_MS]
 *    مع سقف [ELEMENT_STAGGER_MAX_MS] — أضيق من سقف المرشحين (112ms) لأن
 *    أعمق منبثق لا يتجاوز صفّين.
 * 3. صدق التحويل: [shouldAnnounceHover] يقرر متى يستحق التحول بين العناصر
 *    اهتزازًا — تحولٌ حقيقي إلى عنصر صالح فقط، لا أول تركيب ولا إعادة
 *    المرور فوق العنصر ذاته. الاهتزاز نفسه يستعير عقد gestureMovingSwipe
 *    القائم (بلا تفضيلات جديدة — إعادة استخدام صادقة).
 *
 * نبضة العنصر النشط ليست هنا عمدًا: هي نفس عقد النبضة في [DrsMotion]
 * (scaleFor/durationFor) — المنبثق يتكلم لغة المفاتيح المضغوطة ذاتها.
 */
object DrsPopupMotion {

    /** The scale the popup box grows from on entrance. */
    const val ENTRANCE_SCALE_FROM = 0.86f

    /** Entrance duration — attached to the finger, quicker than a panel. */
    const val ENTRANCE_DURATION_MS = 90

    /** Delay added per extended-popup element index. */
    const val ELEMENT_STAGGER_STEP_MS = 16

    /** The hard ceiling on any single element's delay. */
    const val ELEMENT_STAGGER_MAX_MS = 96

    /**
     * The deterministic entrance delay for element [index]: 0 for the
     * first element (and defensively for any negative index), stepping by
     * [ELEMENT_STAGGER_STEP_MS] and clamped at [ELEMENT_STAGGER_MAX_MS].
     */
    fun elementStaggerFor(index: Int): Int =
        (index.coerceAtLeast(0) * ELEMENT_STAGGER_STEP_MS).coerceAtMost(ELEMENT_STAGGER_MAX_MS)

    /**
     * The honest hover gate: announce a change between extended-popup
     * elements only when the new index is a REAL move to a VALID element.
     * The extend() initialization (same index twice) and a slide-out
     * (index reset to -1) are silent by definition — silence here is
     * truth, not a missing feature.
     */
    fun shouldAnnounceHover(previousIndex: Int, newIndex: Int): Boolean =
        newIndex >= 0 && newIndex != previousIndex
}
