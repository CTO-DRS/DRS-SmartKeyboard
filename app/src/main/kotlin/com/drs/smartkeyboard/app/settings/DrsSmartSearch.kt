/*
 * Copyright (C) 2021-2025 The DRS Smart Keyboard Project
 */

package com.drs.smartkeyboard.app.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Gesture
import androidx.compose.material.icons.filled.Healing
import androidx.compose.material.icons.filled.Help
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Numbers
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SentimentSatisfiedAlt
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SettingsInputComponent
import androidx.compose.material.icons.filled.SmartButton
import androidx.compose.material.icons.filled.SpaceBar
import androidx.compose.material.icons.filled.Spellcheck
import androidx.compose.material.icons.filled.Swipe
import androidx.compose.material.icons.filled.SettingsBackupRestore
import androidx.compose.material.icons.filled.SystemUpdateAlt
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.Keyboard
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.drs.smartkeyboard.R
import com.drs.smartkeyboard.app.DrsPreferenceModel
import com.drs.smartkeyboard.app.DrsPreferenceStore
import com.drs.smartkeyboard.app.Routes
import com.drs.smartkeyboard.app.ext.ExtensionImportScreenType
import com.drs.smartkeyboard.app.settings.localization.LanguagePackManagerScreenAction
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.drs.jetpref.datastore.model.PreferenceSerializer
import org.drs.jetpref.datastore.model.collectAsState
import org.drs.lib.compose.autoMirrorForRtl
import org.drs.lib.compose.stringRes
import java.text.Normalizer

/**
 * DRS smart search: an advanced, Arabic-aware settings search engine.
 *
 * The query is normalized (diacritics stripped, hamza/alef forms unified,
 * taa marbuta and alef maqsura folded) so that typing "سمات", "السمة" or
 * "ثيم" all find the theme screen. Results navigate directly to the target
 * screen — one tap from thought to setting.
 */
data class DrsSearchEntry(
    val title: String,
    val keywords: List<String>,
    val icon: ImageVector,
    val route: Any,
)

/**
 * DRS v1.0.4: persistent recent-search history. Queries the user actually
 * acted on are stored most-recent-first, deduplicated (Arabic-normalized
 * comparison) and capped — then surfaced as one-tap rows the next time the
 * search dialog opens. Serialized as JSON into the JetPref datastore,
 * mirroring the emoji-history storage pattern.
 */
@Serializable
data class DrsSearchHistory(val queries: List<String> = emptyList()) {
    object Serializer : PreferenceSerializer<DrsSearchHistory> {
        override fun serialize(value: DrsSearchHistory): String {
            return Json.encodeToString(value)
        }

        override fun deserialize(value: String): DrsSearchHistory {
            return runCatching { Json.decodeFromString<DrsSearchHistory>(value) }
                .getOrDefault(Empty)
        }
    }

    companion object {
        val Empty = DrsSearchHistory()
    }
}

/** Thread-safe mutation helpers for [DrsSearchHistory] in the prefs store. */
object DrsSearchHistoryHelper {
    const val MaxSize = 8
    private const val MinRecordLength = 2
    private val mutex = Mutex()

    /** Record a query the user acted on (front-insert, dedupe, cap). */
    suspend fun record(prefs: DrsPreferenceModel, rawQuery: String) {
        if (!prefs.search.historyEnabled.get()) return
        val trimmed = rawQuery.trim()
        if (trimmed.length < MinRecordLength) return
        mutex.withLock {
            val normalized = drsSearchNormalize(trimmed)
            val current = prefs.search.historyData.get().queries
            val next = (listOf(trimmed) + current.filter { drsSearchNormalize(it) != normalized })
                .take(MaxSize)
            prefs.search.historyData.set(DrsSearchHistory(next))
        }
    }

    /** Remove one query (normalized comparison, so casing/diacritics don't matter). */
    suspend fun remove(prefs: DrsPreferenceModel, rawQuery: String) {
        val normalized = drsSearchNormalize(rawQuery)
        mutex.withLock {
            val current = prefs.search.historyData.get().queries
            prefs.search.historyData.set(
                DrsSearchHistory(current.filter { drsSearchNormalize(it) != normalized }),
            )
        }
    }

    /** Drop the whole history ("مسح الكل"). */
    suspend fun clear(prefs: DrsPreferenceModel) {
        mutex.withLock {
            prefs.search.historyData.set(DrsSearchHistory.Empty)
        }
    }
}

private val drsMnRegex = Regex("\\p{Mn}+")
private val drsNonLetterRegex = Regex("[^\\p{L}\\p{Nd}\\s]")

/** Normalize an Arabic/Latin query for forgiving, smart matching. */
fun drsSearchNormalize(raw: String): String {
    val decomposed = Normalizer.normalize(raw.lowercase(), Normalizer.Form.NFD)
    return decomposed
        .replace(drsMnRegex, "")
        .replace('أ', 'ا')
        .replace('إ', 'ا')
        .replace('آ', 'ا')
        .replace('ة', 'ه')
        .replace('ى', 'ي')
        .replace(drsNonLetterRegex, " ")
        .replace(Regex("\\s+"), " ")
        .trim()
}

private fun isSubsequence(query: String, text: String): Boolean {
    if (query.isEmpty() || text.isEmpty()) return false
    var i = 0
    for (c in text) {
        if (c == query[i]) {
            i++
            if (i == query.length) return true
        }
    }
    return false
}

/**
 * DRS v1.0.3: score ONE normalized query token against ONE normalized
 * candidate (title or keyword). Higher wins; 0 = no match. The subsequence
 * fallback keeps typos and dropped letters productive (e.g. "ثمت" still
 * finds السمات) so the no-results dead end almost never happens.
 */
private fun scoreToken(token: String, candidate: String): Int {
    if (token.isEmpty() || candidate.isEmpty()) return 0
    return when {
        candidate == token -> 100
        candidate.startsWith(token) -> 80
        candidate.contains(token) -> 60
        isSubsequence(token, candidate) -> 25
        else -> 0
    }
}

private fun DrsSearchEntry.matches(normalizedQuery: String): Boolean {
    return score(normalizedQuery) > 0
}

/**
 * DRS v1.0.3: ranked, multi-word matching. The query is split into tokens;
 * EVERY token must hit somewhere in the title or keywords (AND semantics),
 * title hits weigh double, and the per-token scores sum into a ranking score
 * so the best matches always float to the top instead of index order.
 */
private fun DrsSearchEntry.score(normalizedQuery: String): Int {
    val tokens = normalizedQuery.split(' ').filter { it.isNotEmpty() }
    if (tokens.isEmpty()) return 0
    val normTitle = drsSearchNormalize(title)
    var total = 0
    for (token in tokens) {
        var best = scoreToken(token, normTitle) * 2
        for (keyword in keywords) {
            val s = scoreToken(token, drsSearchNormalize(keyword))
            if (s > best) best = s
        }
        if (best == 0) return 0
        total += best
    }
    return total
}

/** The full search index of the settings app (titles resolved for the current locale). */
@Composable
private fun rememberDrsSearchEntries(): List<DrsSearchEntry> {
    return listOf(
        // DRS v1.0.7: the unified «كلاهما» screens.
        DrsSearchEntry(
            title = stringRes(R.string.drs__unified__dashboard_title),
            keywords = listOf(
                "unified", "dashboard", "both", "status", "overview",
                "كلاهما", "لوحة", "موحد", "الحالة", "نظرة عامة", "مزدوج",
            ),
            icon = Icons.Default.Dashboard,
            route = Routes.Settings.DrsUnifiedDashboard,
        ),
        DrsSearchEntry(
            title = stringRes(R.string.drs__unified__tools_title),
            keywords = listOf(
                "tools", "strip", "toolbar", "pin", "reorder", "unified tools",
                "أدوات", "شريط", "تثبيت", "ترتيب", "موحدة", "أدوات ذكية",
            ),
            icon = Icons.Default.Extension,
            route = Routes.Settings.DrsUnifiedTools,
        ),
        DrsSearchEntry(
            title = stringRes(R.string.drs__unified__basics_title),
            keywords = listOf(
                "basics", "spacing", "long press", "repeat", "glide", "number row", "keyboard height",
                "أساسيات", "مسافات", "ضغط مطول", "تكرار", "صف أرقام", "تمرير", "ارتفاع اللوحة",
            ),
            icon = Icons.Default.Tune,
            route = Routes.Settings.DrsUnifiedBasics,
        ),
        DrsSearchEntry(
            title = stringRes(R.string.drs__unified__writing_title),
            keywords = listOf(
                "writing", "editing", "cursor", "select", "templates", "undo",
                "كتابة", "تحرير", "مؤشر", "تحديد", "قوالب", "تراجع",
            ),
            icon = Icons.Default.Edit,
            route = Routes.Settings.DrsUnifiedWriting,
        ),
        DrsSearchEntry(
            title = stringRes(R.string.drs__unified__system_title),
            keywords = listOf(
                "system", "advanced controls", "haptic", "sound level", "engine",
                "نظام", "تحكم متقدم", "اهتزاز", "صوت", "محرك",
            ),
            icon = Icons.Default.Settings,
            route = Routes.Settings.DrsUnifiedSystem,
        ),
        // DRS v1.0.8: daily usage statistics + height scale keywords.
        DrsSearchEntry(
            title = stringRes(R.string.drs__unified__stats_title),
            keywords = listOf(
                "statistics", "usage", "daily", "keys count", "stats",
                "إحصاءات", "إحصائيات", "استخدام", "يومي", "عدد ضغطات", "عدادات",
            ),
            icon = Icons.Default.Insights,
            route = Routes.Settings.DrsUnifiedStats,
        ),
        DrsSearchEntry(
            title = stringRes(R.string.drs__control_center__title),
            keywords = listOf("control center", "مركز", "تحكم", "شامل", "كل شيء"),
            icon = Icons.Default.Dashboard,
            route = Routes.Settings.DrsControlCenter,
        ),
        DrsSearchEntry(
            title = stringRes(R.string.drs__shortcuts__title),
            keywords = listOf("shortcuts", "templates", "اختصار", "اختصارات", "قالب", "قوالب", "سريع"),
            icon = Icons.AutoMirrored.Filled.Assignment,
            route = Routes.Settings.DrsShortcuts,
        ),
        DrsSearchEntry(
            title = stringRes(R.string.drs__profiles__title),
            keywords = listOf("profile", "profiles", "بروفايل", "بروفيل", "ملف شخصي", "ملفات", "حساب"),
            icon = Icons.Default.People,
            route = Routes.Settings.DrsProfiles,
        ),
        DrsSearchEntry(
            title = stringRes(R.string.drs__gestures__title),
            keywords = listOf("drs gestures", "حركة", "حركات", "لمسة", "لمس ذكي"),
            icon = Icons.Default.TouchApp,
            route = Routes.Settings.DrsGestures,
        ),
        DrsSearchEntry(
            title = stringRes(R.string.drs__diagnostics__title),
            keywords = listOf("diagnostics", "تشخيص", "تشخيصات", "فحص", "مشاكل", "مشكلة", "إصلاح", "صيانة"),
            icon = Icons.Default.Healing,
            route = Routes.Settings.DrsDiagnostics,
        ),
        DrsSearchEntry(
            title = stringRes(R.string.settings__localization__title),
            keywords = listOf("languages", "layouts", "لغة", "لغات", "تخطيط", "تخطيطات", "عربي", "إنجليزي", "لوحات"),
            icon = Icons.Default.Language,
            route = Routes.Settings.Localization,
        ),
        DrsSearchEntry(
            title = stringRes(R.string.settings__theme__title),
            keywords = listOf("themes", "appearance", "theme", "سمة", "سمات", "ثيم", "لون", "ألوان", "مظهر", "شكل"),
            icon = Icons.Outlined.Palette,
            route = Routes.Settings.Theme,
        ),
        DrsSearchEntry(
            title = stringRes(R.string.settings__keyboard__title),
            keywords = listOf("keyboard", "لوحة المفاتيح", "ارتفاع", "أصوات", "صوت", "اهتزاز", "طول"),
            icon = Icons.Outlined.Keyboard,
            route = Routes.Settings.Keyboard,
        ),
        DrsSearchEntry(
            title = stringRes(R.string.settings__smartbar__title),
            keywords = listOf("smartbar", "toolbar", "الشريط الذكي", "شريط", "أدوات", "صف الأدوات"),
            icon = Icons.Default.SmartButton,
            route = Routes.Settings.Smartbar,
        ),
        DrsSearchEntry(
            title = stringRes(R.string.settings__typing__title),
            keywords = listOf("typing", "correction", "كتابة", "تصحيح", "اقتراح", "اقتراحات", "تلميح", "إملائي"),
            icon = Icons.Default.Spellcheck,
            route = Routes.Settings.Typing,
        ),
        DrsSearchEntry(
            title = stringRes(R.string.settings__gestures__title),
            keywords = listOf("gestures", "swipe", "إيماءة", "إيماءات", "سحب", "تمرير"),
            icon = Icons.Default.Gesture,
            route = Routes.Settings.Gestures,
        ),
        DrsSearchEntry(
            title = stringRes(R.string.settings__clipboard__title),
            keywords = listOf("clipboard", "paste", "حافظة", "لصق", "نسخ"),
            icon = Icons.Default.ContentPaste,
            route = Routes.Settings.Clipboard,
        ),
        DrsSearchEntry(
            title = stringRes(R.string.settings__media__title),
            keywords = listOf("media", "emoji", "emoticon", "إيموجي", "وسائط", "رموز", "صور"),
            icon = Icons.Default.SentimentSatisfiedAlt,
            route = Routes.Settings.Media,
        ),
        DrsSearchEntry(
            title = stringRes(R.string.ext__home__title),
            keywords = listOf("extensions", "addons", "packages", "store", "ملحقات", "إضافات", "حزم", "إضافة", "متجر", "ثيمات"),
            icon = Icons.Default.Extension,
            route = Routes.Ext.Home,
        ),
        DrsSearchEntry(
            title = stringRes(R.string.accessories__title),
            keywords = listOf("accessories", "modules", "modules", "glide", "clipboard", "ملحقات ذكية", "موديول", "انزلاق", "حافظة"),
            icon = Icons.Default.SmartButton,
            route = Routes.Ext.Accessories,
        ),
        DrsSearchEntry(
            title = stringRes(R.string.settings__other__title),
            keywords = listOf("other", "advanced", "backup", "أخرى", "أخرى متقدمة", "متقدم", "احتياطي", "نسخ احتياطي"),
            icon = Icons.Default.Build,
            route = Routes.Settings.Other,
        ),
        DrsSearchEntry(
            title = stringRes(R.string.about__title),
            keywords = listOf("about", "version", "حول", "نسخة", "إصدار", "معلومات", "عن التطبيق"),
            icon = Icons.Outlined.Info,
            route = Routes.Settings.About,
        ),
        // DRS v1.0.3: previously unreachable screens — the update center,
        // package center, rewards store and backup/restore are now all
        // one search away.
        DrsSearchEntry(
            title = stringRes(R.string.updates__center__title),
            keywords = listOf("update", "upgrade", "تحديث", "تحديثات", "ترقية", "إصدار جديد", "فحص التحديثات"),
            icon = Icons.Default.SystemUpdateAlt,
            route = Routes.Ext.CheckUpdates,
        ),
        DrsSearchEntry(
            title = stringRes(R.string.packages__center__title),
            keywords = listOf("packages", "package center", "حزم", "حزمة", "مركز الحزم", "سمات رسمية", "كتالوج"),
            icon = Icons.Outlined.CloudDownload,
            route = Routes.Ext.Packages,
        ),
        DrsSearchEntry(
            title = stringRes(R.string.drs__rewards__title),
            keywords = listOf("rewards", "points", "مكافآت", "مكافأة", "نقاط", "عملات", "ألقاب", "أوسمة", "متجر"),
            icon = Icons.Default.CardGiftcard,
            route = Routes.Settings.DrsRewards,
        ),
        DrsSearchEntry(
            title = stringRes(R.string.backup_and_restore__title),
            keywords = listOf("backup", "restore", "نسخ احتياطي", "احتياطي", "استعادة", "تصدير الإعدادات", "حفظ الإعدادات"),
            icon = Icons.Default.SettingsBackupRestore,
            route = Routes.Settings.Backup,
        ),
        // DRS v1.0.4: deeper reach — sub-screens and individual settings are
        // now searchable, routing straight to the page that owns them.
        DrsSearchEntry(
            title = stringRes(R.string.settings__input_feedback__title),
            keywords = listOf("sound", "haptic", "vibration", "audio", "feedback", "صوت", "أصوات", "اهتزاز", "لمس", "نقرة"),
            icon = Icons.Default.Vibration,
            route = Routes.Settings.InputFeedback,
        ),
        DrsSearchEntry(
            title = stringRes(R.string.physical_keyboard__title),
            keywords = listOf("physical", "hardware", "bluetooth", "usb", "فيزيائية", "خارجية", "بلوتوث"),
            icon = Icons.Default.SettingsInputComponent,
            route = Routes.Settings.PhysicalKeyboard,
        ),
        DrsSearchEntry(
            title = stringRes(R.string.settings__dictionary__title),
            keywords = listOf("dictionary", "words", "learn", "قاموس", "كلمات", "تعلم", "تعليم"),
            icon = Icons.AutoMirrored.Filled.MenuBook,
            route = Routes.Settings.Dictionary,
        ),
        DrsSearchEntry(
            title = stringRes(R.string.backup_and_restore__restore__title),
            keywords = listOf("restore", "استعادة", "استرجاع", "استيراد الإعدادات", "رجوع"),
            icon = Icons.Default.Restore,
            route = Routes.Settings.Restore,
        ),
        DrsSearchEntry(
            title = stringRes(R.string.settings__localization__subtype_add_title),
            keywords = listOf("add language", "new layout", "إضافة لغة", "لوحة جديدة", "نوع فرعي", "لغة جديدة"),
            icon = Icons.Default.AddCircle,
            route = Routes.Settings.SubtypeAdd,
        ),
        DrsSearchEntry(
            title = stringRes(R.string.settings__localization__language_pack_title),
            keywords = listOf("language pack", "packs", "حزم اللغة", "حزمة لغة", "تثبيت لغة", "لغات إضافية"),
            icon = Icons.Default.Translate,
            route = Routes.Settings.LanguagePackManager(LanguagePackManagerScreenAction.MANAGE),
        ),
        DrsSearchEntry(
            title = stringRes(R.string.ext__import__ext_any),
            keywords = listOf("import", "file", "flex", "استيراد", "ملف", "ملحق", "إضافة من ملف"),
            icon = Icons.Default.FileDownload,
            route = Routes.Ext.Import(ExtensionImportScreenType.EXT_ANY),
        ),
        DrsSearchEntry(
            title = stringRes(R.string.pref__keyboard__number_row__label),
            keywords = listOf("number row", "أرقام", "صف الأرقام", "أعداد"),
            icon = Icons.Default.Numbers,
            route = Routes.Settings.Keyboard,
        ),
        DrsSearchEntry(
            title = stringRes(R.string.pref__suggestion__next_word_enabled__label),
            keywords = listOf("next word", "prediction", "تنبؤ", "الكلمة التالية", "اقتراح"),
            icon = Icons.Default.FastForward,
            route = Routes.Settings.Typing,
        ),
        DrsSearchEntry(
            title = stringRes(R.string.pref__correction__auto_capitalization__label),
            keywords = listOf("capitalization", "caps", "حرف كبير", "أحرف كبيرة", "تلقائي"),
            icon = Icons.Default.TextFields,
            route = Routes.Settings.Typing,
        ),
        DrsSearchEntry(
            title = stringRes(R.string.pref__correction__double_space_period__label),
            keywords = listOf("period", "double space", "نقطة", "مسافة مزدوجة"),
            icon = Icons.Default.SpaceBar,
            route = Routes.Settings.Typing,
        ),
        DrsSearchEntry(
            title = stringRes(R.string.pref__glide__title),
            keywords = listOf("glide", "swipe typing", "كتابة بالتمرير", "انزلاق", "سحب متصل"),
            icon = Icons.Default.Swipe,
            route = Routes.Settings.Gestures,
        ),
        DrsSearchEntry(
            title = stringRes(R.string.help__title),
            keywords = listOf("help", "guide", "faq", "how to", "مساعدة", "دليل", "كيف", "شرح", "استخدام"),
            icon = Icons.Default.Help,
            route = Routes.Settings.Help,
        ),
        // DRS v1.0.6: technical system surfaces.
        DrsSearchEntry(
            title = stringRes(R.string.drs__tech_toolbar__title),
            keywords = listOf("tech toolbar", "toolbar", "strip", "الشريط التقني", "تخصيص الشريط", "مفاتيح", "ترتيب"),
            icon = Icons.Default.Build,
            route = Routes.Settings.DrsTechToolbar,
        ),
        DrsSearchEntry(
            title = stringRes(R.string.drs__performance__title),
            keywords = listOf("performance", "latency", "memory", "speed", "monitor", "أداء", "الذاكرة", "سرعة", "مراقبة", "استجابة"),
            icon = Icons.Default.Speed,
            route = Routes.Settings.DrsPerformance,
        ),
        DrsSearchEntry(
            title = stringRes(R.string.drs__diagnostics__backup_section),
            keywords = listOf("backup", "restore", "export", "import state", "نسخ احتياطي", "استيراد", "تصدير", "استعادة"),
            icon = Icons.Default.Backup,
            route = Routes.Settings.DrsDiagnostics,
        ),
    )
}

/**
 * Quick suggestions shown before the user types anything. DRS v1.0.4:
 * resolved by ROUTE (not list index) so the list can grow without silently
 * pointing at the wrong entries.
 */
private val drsSuggestionRoutes = listOf(
    Routes.Settings.DrsControlCenter,
    Routes.Settings.DrsProfiles,
    Routes.Settings.Keyboard,
    Routes.Settings.Localization,
    Routes.Settings.DrsShortcuts,
    Routes.Ext.CheckUpdates,
)

@Composable
private fun DrsSearchResultRow(
    entry: DrsSearchEntry,
    query: String,
    onClick: () -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable { onClick() }
            .padding(horizontal = 8.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(colorScheme.primary.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = entry.icon,
                contentDescription = null,
                tint = colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        // DRS v1.0.3: word-level match highlighting — the words that caused
        // the hit are bolded in the primary color, so the user immediately
        // sees WHY each result matched.
        Text(
            text = buildHighlightedTitle(entry.title, query),
            style = MaterialTheme.typography.bodyLarge,
            color = colorScheme.onSurface,
            maxLines = 1,
            modifier = Modifier.weight(1f),
        )
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier
                .size(18.dp)
                // DRS v1.28.0 audit fix (High, RTL): mirror the result chevron,
                // see HomeScreen — it pointed backward in Arabic.
                .autoMirrorForRtl(),
        )
    }
}

/**
 * DRS v1.0.3: bolds every word of the title that contributed to the match
 * (word-level, so no fragile index mapping across normalization — diacritics
 * stripped, hamza folded, etc. never break the styling).
 */
private fun buildHighlightedTitle(title: String, rawQuery: String): AnnotatedString {
    val tokens = drsSearchNormalize(rawQuery).split(' ').filter { it.isNotEmpty() }
    if (tokens.isEmpty()) return AnnotatedString(title)
    val highlight = SpanStyle(fontWeight = FontWeight.Bold)
    return buildAnnotatedString {
        append(title)
        var offset = 0
        for (word in title.split(' ')) {
            if (word.isNotEmpty()) {
                val normWord = drsSearchNormalize(word)
                val hit = tokens.any { token ->
                    normWord.contains(token) || isSubsequence(token, normWord)
                }
                if (hit) addStyle(highlight, offset, offset + word.length)
            }
            offset += word.length + 1
        }
    }
}

/**
 * DRS v1.0.4: one-tap row for a recent search — tap re-runs the query,
 * the trailing X removes it from the history.
 */
@Composable
private fun DrsSearchHistoryRow(
    queryText: String,
    onSelect: () -> Unit,
    onRemove: () -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable { onSelect() }
            .padding(horizontal = 8.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(colorScheme.onSurfaceVariant.copy(alpha = 0.08f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Default.History,
                contentDescription = null,
                tint = colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = queryText,
            style = MaterialTheme.typography.bodyLarge,
            color = colorScheme.onSurface,
            maxLines = 1,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onRemove, modifier = Modifier.size(30.dp)) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = stringRes(R.string.action__delete),
                tint = colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

/**
 * DRS smart search popup: a floating, elevated dialog with an auto-focused
 * search field, Arabic-aware live filtering and one-tap navigation to any
 * settings screen. Shows recent searches (persistent) plus quick suggestions
 * when the query is empty.
 */
@Composable
fun DrsSmartSearchDialog(
    onDismiss: () -> Unit,
    onNavigate: (Any) -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme
    var query by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }
    val entries = rememberDrsSearchEntries()

    // DRS v1.0.4: persistent recent-searches — every result the user actually
    // opens is recorded (NonCancellable so the write survives the dialog
    // leaving composition) and shown as one-tap rows next time.
    val prefs by DrsPreferenceStore
    val scope = rememberCoroutineScope()
    val historyEnabled by prefs.search.historyEnabled.collectAsState()
    val historyData by prefs.search.historyData.collectAsState()
    val historyQueries = historyData.queries

    fun openResult(entry: DrsSearchEntry) {
        if (query.trim().length >= 2) {
            scope.launch(NonCancellable) {
                DrsSearchHistoryHelper.record(prefs, query)
            }
        }
        onNavigate(entry.route)
    }

    val normalizedQuery = drsSearchNormalize(query)
    // DRS v1.0.3: ranked results — best match first (title hits weigh double,
    // per-token scores sum). Stable sort keeps the index order for ties.
    val results = if (normalizedQuery.isEmpty()) {
        emptyList()
    } else {
        entries.asSequence()
            .map { it to it.score(normalizedQuery) }
            .filter { it.second > 0 }
            .sortedByDescending { it.second }
            .map { it.first }
            .toList()
    }
    val suggestions = drsSuggestionRoutes.mapNotNull { route ->
        entries.firstOrNull { it.route == route }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier
                .padding(horizontal = 20.dp, vertical = 64.dp)
                .fillMaxWidth()
                .heightIn(max = 640.dp),
            shape = RoundedCornerShape(26.dp),
            color = colorScheme.surface,
            tonalElevation = 8.dp,
            border = BorderStroke(1.dp, colorScheme.outlineVariant.copy(alpha = 0.5f)),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = {
                        Text(text = stringRes(R.string.drs__home__search_hint))
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = null,
                            tint = colorScheme.primary,
                        )
                    },
                    trailingIcon = {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = colorScheme.tertiary,
                            modifier = Modifier.size(18.dp),
                        )
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(22.dp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(
                        onSearch = {
                            results.firstOrNull()?.let { openResult(it) }
                        },
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester),
                )
                LaunchedEffect(Unit) {
                    kotlinx.coroutines.delay(120)
                    focusRequester.requestFocus()
                }
                Spacer(modifier = Modifier.padding(top = 12.dp))

                LazyColumn(modifier = Modifier.fillMaxWidth()) {
                    if (normalizedQuery.isEmpty()) {
                        // DRS v1.0.4: persistent recent searches first — one
                        // tap re-runs the query, X removes a single row,
                        // "مسح الكل" drops the whole history.
                        if (historyEnabled && historyQueries.isNotEmpty()) {
                            item(key = "history_header") {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(start = 8.dp, end = 2.dp, bottom = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        text = stringRes(R.string.drs__search__history__title),
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = colorScheme.primary,
                                    )
                                    Spacer(modifier = Modifier.weight(1f))
                                    TextButton(
                                        onClick = {
                                            scope.launch {
                                                DrsSearchHistoryHelper.clear(prefs)
                                            }
                                        },
                                    ) {
                                        Text(
                                            text = stringRes(R.string.drs__search__history__clear),
                                            style = MaterialTheme.typography.labelMedium,
                                            color = colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }
                            items(historyQueries.size, key = { "history_$it" }) { index ->
                                DrsSearchHistoryRow(
                                    queryText = historyQueries[index],
                                    onSelect = { query = historyQueries[index] },
                                    onRemove = {
                                        scope.launch {
                                            DrsSearchHistoryHelper.remove(prefs, historyQueries[index])
                                        }
                                    },
                                )
                            }
                        }
                        item(key = "suggestions_header") {
                            Text(
                                text = stringRes(R.string.drs__home__search_suggestions),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = colorScheme.primary,
                                modifier = Modifier.padding(start = 8.dp, bottom = 4.dp, top = 8.dp),
                            )
                        }
                        items(suggestions) { entry ->
                            DrsSearchResultRow(entry = entry, query = query, onClick = { openResult(entry) })
                        }
                    } else if (results.isEmpty()) {
                        item(key = "no_results") {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 32.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = null,
                                    tint = colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                                    modifier = Modifier.size(36.dp),
                                )
                                Spacer(modifier = Modifier.padding(top = 12.dp))
                                Text(
                                    text = stringRes(R.string.drs__home__search_no_results),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center,
                                )
                            }
                        }
                    } else {
                        items(results) { entry ->
                            DrsSearchResultRow(entry = entry, query = query, onClick = { openResult(entry) })
                        }
                    }
                }
            }
        }
    }
}
