/*
 * Copyright (C) 2026 The DRS Smart Keyboard Project
 */

package com.drs.smartkeyboard.ime.media.emoji

/**
 * DRS v2.14.0 «ذاكرة الإيموجي الأمينة» — بوابة تسجيل الإيموجي في السجل
 * المحلي الدائم.
 *
 * المشكلة التي أغلقها هذا العقد: مسارا تسجيل الإيموجي (نقرة لوحة الإيموجي،
 * وقبول اقتراح إيموجي) كانا يكتبان في السجل الدائم على القرص **دومًا** —
 * حتى في جلسة التخفي، وحتى في حقول كلمات المرور، وحتى في الحقول التي عطّلت
 * التكوين (الرقمية/الهاتفية)، وحتى والقفل الشاشي مرفوع. هذا يناقض العقيدة
 * والسوابق المؤسسية: تعلّم الكلمات (NlpManager.learnExternalWord) يرفض
 * التخفي وعطْل التكوين وتنويعات كلمات المرور، وأدوات النص (EditorInstance)
 * ترفض الثلاثية نفسها، والإملاء الصوتي يُرفض رفضًا صلبًا في الحقول الخاصة.
 * والأغرب تناقضًا: لوحة الإيموجي نفسها **تُخفي** عرض «المؤخر» والجهاز
 * مقفول لكنها كانت **تُسجّل** فيه — العرض محجوب والالتقاط مفتوح.
 *
 * الفلسفة: ما تكتبه في حقل خاص لا يترك أثرًا في ذاكرات لوحة المفاتيح —
 * لا سحابة ولا تخمين، ولا التزام صامت من ذاكرة الإيموجي. العرض والإدارة
 * (التثبيت/الإزالة/الترتيب/المسح) لأمورك المحفوظة سلفًا ليسا التزامًا —
 * لا يمران بهذه البوابة؛ **الالتقاط** هو التزام، وهو وحده ما تُحكمه.
 *
 * العقد نقي بلا Android إطلاقًا: المدخلات حقائق مقروءة من حالة المحرر
 * والنافذة، والمخرج حكم من نوعين فقط. ترتيب الأسباب مصمم ومثبت اختبارًا
 * (جدول كامل 64 تركيبة): أقوى سبب رفض يُذكر أولًا.
 */
object DrsEmojiHistoryGate {

    /** سبب الرفض — مرتب بترتيب الأولوية التصميمي (أقوى سبب أولًا). */
    enum class Refusal {
        /** السجل نفسه معطّل من الإعدادات (emoji__history_enabled). */
        HISTORY_DISABLED,

        /** جلسة خاصة (وضع التخفي) — لا ذاكرة لأي شيء يُكتب هنا. */
        PRIVATE_SESSION,

        /** حقل كلمة مرور — ما تكتبه لا يُلتقط أبدًا. */
        CREDENTIALS_FIELD,

        /** محرر خام بلا دلالات إدخال — لا فرض ذاكرة عليه. */
        RAW_EDITOR,

        /** التكوين معطّل (حقول رقمية/هاتفية/رمز) — بلا اقتراحات فلا تعلّم. */
        NON_COMPOSING_FIELD,

        /** القفل الشاشي مرفوع — العرض محجوب فالالتقاط محجوب بالتماثل. */
        DEVICE_LOCKED,
    }

    /** حكم البوابة: إما التسجيل، أو الرفض بسبب واحدٍ صريح. */
    sealed interface Verdict {
        /** اكتب في السجل — كل الحقائق سليمة ولا سياق خاص. */
        data object Record : Verdict

        /** ارفض الكتابة صامتًا في الذاكرة — ولا استثناء ولا سجل أخطاء. */
        data class Refuse(val reason: Refusal) : Verdict
    }

    /**
     * حكم التسجيل. كل الحقائق تُمرر صراحة — لا قراءة حالة كامنة داخل
     * البوابة، فالحكم حتمي بدوال مدخلاته واختباره جدول كامل بلا أثر جانبي.
     *
     * ترتيب الفحص (مثبت اختبارًا واحدًا لكل زوج متجاور):
     * السجل معطّل → التخفي → كلمة المرور → المحرر الخام → عطْل التكوين →
     * الجهاز مقفول → سجّل.
     */
    fun decide(
        historyEnabled: Boolean,
        isIncognitoMode: Boolean,
        isPasswordVariation: Boolean,
        isRawInputEditor: Boolean,
        isComposingEnabled: Boolean,
        isDeviceLocked: Boolean,
    ): Verdict {
        if (!historyEnabled) return Verdict.Refuse(Refusal.HISTORY_DISABLED)
        if (isIncognitoMode) return Verdict.Refuse(Refusal.PRIVATE_SESSION)
        if (isPasswordVariation) return Verdict.Refuse(Refusal.CREDENTIALS_FIELD)
        if (isRawInputEditor) return Verdict.Refuse(Refusal.RAW_EDITOR)
        if (!isComposingEnabled) return Verdict.Refuse(Refusal.NON_COMPOSING_FIELD)
        if (isDeviceLocked) return Verdict.Refuse(Refusal.DEVICE_LOCKED)
        return Verdict.Record
    }

    /**
     * حقائق السياق وقت الالتقاط، تُبنى عند حدث الإدخال نفسه (لا تُخزَّن ولا
     * تُتداول) — كل حقل بلا افتراضي، فلا مسار بناءٍ صامت يفتح باب الالتقاط.
     */
    data class RecordContext(
        val isIncognitoMode: Boolean,
        val isPasswordVariation: Boolean,
        val isRawInputEditor: Boolean,
        val isComposingEnabled: Boolean,
        val isDeviceLocked: Boolean,
    )
}
