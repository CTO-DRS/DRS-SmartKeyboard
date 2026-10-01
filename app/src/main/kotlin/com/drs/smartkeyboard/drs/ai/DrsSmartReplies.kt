/*
 * Copyright (C) 2026 The DRS Smart Keyboard Project
 */

package com.drs.smartkeyboard.drs.ai

/**
 * DRS Phase 2 (roadmap task 11): smart replies inside any app — a fully
 * on-device, pattern-based reply engine exposed through the contextual
 * suggestion pipeline.
 *
 * HOW it plugs in: when the composing region is empty (the user just
 * received a message and has not started typing), the suggestion provider
 * feeds the tail of the editor content to [repliesFor] and shows the
 * returned chips. Tapping a chip commits it through the ordinary
 * candidate path — no new surface, no new permission, no network.
 *
 * WHY patterns, not a model: reply INTENTS (greeting, thanks, apology,
 * confirmation, farewell, congratulations, condolences, availability)
 * are a small closed class where a transparent rule table reaches
 * excellent precision with zero model weight, zero latency beyond a
 * regex scan, and a trivial privacy story — nothing to audit because
 * nothing exists beyond this file.
 *
 * Selection is DETERMINISTIC with day-rotation (the same message maps
 * to the same reply within one day, different days rotate through the
 * alternatives) so conversations never feel canned while reproducibility
 * is preserved for testing.
 */
object DrsSmartReplies {

    private val ARABIC_RANGE = '\u0600'..'\u06FF'

    // ------------------------------------------------------------ intents

    private data class Intent(
        val ar: List<Regex>,
        val en: List<Regex>,
        val replies: List<String>,
    )

    private val INTENTS: List<Intent> = listOf(
        Intent(
            // تحية / سلام
            ar = listOf(Regex("^(ال)?سلام عليكم"), Regex("^(مرحب|أهلا|هلا|صباح|مساء)")),
            en = listOf(Regex("^\\s*(hi|hello|hey|good (morning|evening|afternoon))\\b", RegexOption.IGNORE_CASE)),
            replies = listOf(
                "وعليكم السلام",
                "أهلًا وسهلًا 👋",
                "مرحبًا بك",
                "Hello! 👋",
                "Hi there",
            ),
        ),
        Intent(
            // كيف الحال
            ar = listOf(Regex("كيف (حال|الحال|أحوال|الأحوال)"), Regex("شخبارك|اخبارك|أخبارك")),
            en = listOf(Regex("\\b(how are you|how's it going|how are things)\\b", RegexOption.IGNORE_CASE)),
            replies = listOf(
                "الحمد لله بخير، وأنت؟",
                "بخير والحمد لله 🌿",
                "تمام الحمد لله، كيف أنت؟",
                "All good, thanks!",
                "Doing great, how about you?",
            ),
        ),
        Intent(
            // شكر
            ar = listOf(Regex("شكرا|شكرًا|متشكر|يعطيك العافية|يسلمو")),
            en = listOf(Regex("\\b(thanks|thank you|thx|appreciate)\\b", RegexOption.IGNORE_CASE)),
            replies = listOf(
                "العفو",
                "لا شكر على واجب 🌟",
                "على الرحب والسعة",
                "You're welcome",
                "Anytime!",
            ),
        ),
        Intent(
            // اعتذار
            ar = listOf(Regex("آسف|اسف|أعتذر|اعتذر|معلش|متأخر")),
            en = listOf(Regex("\\b(sorry|my bad|apolog)\\w*", RegexOption.IGNORE_CASE)),
            replies = listOf(
                "لا عليك، ولا يهمك",
                "ولا يهمك أبدًا",
                "معك حق، يحدث",
                "No worries",
                "It's totally fine",
            ),
        ),
        Intent(
            // وداع
            ar = listOf(Regex("مع السلامة|السلامة|تصبح على خير|بانتظارك|إلى اللقاء|الى اللقاء")),
            en = listOf(Regex("\\b(bye|goodbye|good night|see you|take care)\\b", RegexOption.IGNORE_CASE)),
            replies = listOf(
                "في أمان الله",
                "مع السلامة 🤍",
                "تصبح على خير",
                "Take care!",
                "See you soon",
            ),
        ),
        Intent(
            // تأكيد نعم
            ar = listOf(Regex("^(نعم|أيوه|ايوه|أيوة|طيب|حسنا|تمام|أكيد|اكيد|حاضر|ماشي|اوكي|أوكي|على راسي)")),
            en = listOf(Regex("^\\s*(yes|yeah|yep|ok|okay|sure|fine|alright|sounds good)\\b", RegexOption.IGNORE_CASE)),
            replies = listOf(
                "تمام، بالتوفيق 👍",
                "حاضر، سأنفذ",
                "ممتاز، نتحدث لاحقًا",
                "Great, thanks!",
                "Perfect",
            ),
        ),
        Intent(
            // رفض مؤدب
            ar = listOf(Regex("^(لا|لأ|مو|مش|مستحيل|لا أستطيع|لا استطيع)")),
            en = listOf(Regex("^\\s*(no|nope|can't|cannot|not now)\\b", RegexOption.IGNORE_CASE)),
            replies = listOf(
                "لا مشكلة، شكرًا لإبلاغي",
                "تمام، لا بأس",
                "نرتب أمرًا آخر إذًا",
                "No problem",
                "Understood, thanks for letting me know",
            ),
        ),
        Intent(
            // تهنئة
            ar = listOf(Regex("مبارك|ألف مبروك|الف مبروك|تهانينا|مبروك")),
            en = listOf(Regex("\\b(congrats|congratulations|mabrook)\\b", RegexOption.IGNORE_CASE)),
            replies = listOf(
                "الله يبارك لك، وشكرًا 🎉",
                "شكرًا جزيلًا 🌟",
                "أرقّ التحيات",
                "Thank you so much! 🎉",
                "Much appreciated!",
            ),
        ),
        Intent(
            // مواساة
            ar = listOf(Regex("بالكرفس|البقية|اللهم اجعل|عظم الله|إنا لله|انا لله|احتسب|البقاء")),
            en = listOf(Regex("\\b(condolences|rest in peace|rip)\\b", RegexOption.IGNORE_CASE)),
            replies = listOf(
                "البقية في حياتك، وعظم الله أجرك",
                "الله يعينك ويصبرك",
                "أحسن الله عزائكم",
                "My deepest condolences",
                "Sending you strength",
            ),
        ),
        Intent(
            // توفر/موعد
            ar = listOf(Regex("(متى|كم الساعة|أين نلتقي|وين|ممكن نتصل|عندك وقت|موجود)")),
            en = listOf(Regex("\\b(what time|when (are|can|is)|where (do|should) we|are you (free|available))\\b", RegexOption.IGNORE_CASE)),
            replies = listOf(
                "متى يناسبك؟ أنا متفرغ بعد العصر",
                "أي وقت يناسبك ينبسط لي",
                "أقترح اللقاء غدًا إن أمكن",
                "Sure, what time works for you?",
                "I'm available — when?",
            ),
        ),
    )

    // --------------------------------------------------------- selection

    /**
     * Returns up to [max] reply chips for the conversation tail [text]
     * (typically the last 1–3 lines before the cursor). Pure and
     * deterministic: the same (text, day) pair yields the same replies.
     * Returns an empty list when no confident intent matches — a wrong
     * reply chip is worse than no chip.
     */
    fun repliesFor(text: String, max: Int, dayStamp: Long = todayStamp()): List<String> {
        if (max <= 0) return emptyList()
        val trimmed = text.trim()
        if (trimmed.length < 2 || trimmed.length > 600) return emptyList()

        val isArabic = trimmed.any { it in ARABIC_RANGE }
        var bestIdx = -1
        var bestHits = 0
        for ((idx, intent) in INTENTS.withIndex()) {
            val patterns = if (isArabic) intent.ar + intent.en else intent.en + intent.ar
            var hits = 0
            for (re in patterns) if (re.containsMatchIn(trimmed)) hits++
            if (hits > bestHits) {
                bestHits = hits
                bestIdx = idx
            }
        }
        if (bestIdx < 0 || bestHits == 0) return emptyList()

        val replies = INTENTS[bestIdx].replies
        // Language-matched pool: Arabic conversations get the Arabic chips,
        // everything else gets the Latin ones, falling back to the full
        // list when a table only carries one language.
        val pool = (if (isArabic) {
            replies.filter { it.any { c -> c in ARABIC_RANGE } }
        } else {
            replies.filterNot { it.any { c -> c in ARABIC_RANGE } }
        }).ifEmpty { replies }
        if (pool.isEmpty()) return emptyList()
        val start = (dayStamp % pool.size).toInt()
        return (0 until pool.size).map { pool[(start + it) % pool.size] }.take(max)
    }

    /**
     * Day stamp for rotation (UTC days since epoch — stable across
     * timezones, tests inject fixed values).
     */
    fun todayStamp(nowMillis: Long = System.currentTimeMillis()): Long =
        nowMillis / (24L * 60L * 60L * 1000L)
}
