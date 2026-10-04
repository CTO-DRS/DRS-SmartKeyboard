/*
 * Copyright (C) 2021-2025 The DRS Smart Keyboard Project
 * Copyright (C) 2025 DRS Smart Keyboard contributors
 */

package com.drs.smartkeyboard.drs.ui

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Functions
import androidx.compose.material.icons.filled.Percent
import androidx.compose.material.icons.filled.ClearAll
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Compress
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Today
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.FirstPage
import androidx.compose.material.icons.filled.FormatClear
import androidx.compose.material.icons.filled.FormatLineSpacing
import androidx.compose.material.icons.filled.FormatListBulleted
import androidx.compose.material.icons.filled.FormatListNumbered
import androidx.compose.material.icons.filled.FormatQuote
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.filled.HighlightOff
import androidx.compose.material.icons.filled.KeyboardTab
import androidx.compose.material.icons.filled.LastPage
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.MergeType
import androidx.compose.material.icons.filled.Paid
import androidx.compose.material.icons.filled.SentimentNeutral
import androidx.compose.material.icons.filled.Rule
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.ShortText
import androidx.compose.material.icons.filled.Spellcheck
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.SortByAlpha
import androidx.compose.material.icons.filled.SpaceBar
import androidx.compose.material.icons.filled.SubdirectoryArrowRight
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.TextIncrease
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.WrapText
import androidx.compose.material.icons.filled.QuestionMark
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.drs.smartkeyboard.R
import com.drs.smartkeyboard.app.DrsPreferenceStore
import com.drs.smartkeyboard.drs.DrsAdaptationEngine
import com.drs.smartkeyboard.drs.DrsStore
import com.drs.smartkeyboard.drs.DrsSystems
import com.drs.smartkeyboard.drs.DrsTextTool
import com.drs.smartkeyboard.drs.DrsTileContextAdvisor
import com.drs.smartkeyboard.drs.DrsTileFavorites
import com.drs.smartkeyboard.drs.PanelUsageTracker
import com.drs.smartkeyboard.editorInstance
import com.drs.smartkeyboard.ime.ImeUiMode
import com.drs.smartkeyboard.ime.keyboard.DrsImeSizing
import com.drs.smartkeyboard.ime.text.key.KeyCode
import com.drs.smartkeyboard.ime.text.key.KeyType
import com.drs.smartkeyboard.ime.text.keyboard.TextKeyData
import com.drs.smartkeyboard.ime.theme.DrsImeUi
import com.drs.smartkeyboard.keyboardManager
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import org.drs.jetpref.datastore.model.collectAsState
import org.drs.lib.compose.rippleClickable
import org.drs.lib.compose.stringRes
import org.drs.lib.snygg.ui.SnyggBox
import org.drs.lib.snygg.ui.SnyggColumn
import org.drs.lib.snygg.ui.SnyggIcon
import org.drs.lib.snygg.ui.SnyggIconButton
import org.drs.lib.snygg.ui.SnyggRow
import org.drs.lib.snygg.ui.SnyggText

/**
 * One actionable row of the text tools panel. [code] is either a real
 * [KeyCode] (quick editor actions like undo/copy) or a [DrsTextTool] code,
 * both dispatched through the standard input event pipeline so feedback,
 * adaptation stats and the economy hooks all apply.
 */
private data class DrsTextToolsPanelItem(
    val code: Int,
    val labelRes: Int,
    val descRes: Int,
    val icon: ImageVector,
)

private data class DrsTextToolsPanelSection(
    val titleRes: Int,
    val items: List<DrsTextToolsPanelItem>,
)

/** Builds a panel item for a [DrsTextTool] from its string resources. */
private fun toolItem(tool: DrsTextTool, labelRes: Int, descRes: Int, icon: ImageVector) =
    DrsTextToolsPanelItem(code = tool.code, labelRes = labelRes, descRes = descRes, icon = icon)

/** The persisted namespace of the tiles' per-tile usage counters. */
private const val USAGE_PANEL_TEXT_TOOLS = "text_tools"

/** The tiles' filter state behind the chips row. */
private sealed interface TileFilter {

    /** Everything: the advisory row, the favorites, then all sections. */
    data object All : TileFilter

    /** The pinned tiles only, in pin order. */
    data object Favorites : TileFilter

    /** The most-used tiles only, by the local counters. */
    data object Recents : TileFilter

    /** One catalogue section only. */
    data class Section(val index: Int) : TileFilter
}

/**
 * DRS v2.2.0: the favorites' local persistence — the same SharedPreferences
 * file the panel counters live in (`drs_panel_usage`), a JSON list of tile
 * codes under its own key. Local only; the stored values are CODES of the
 * tiles the user pinned — never any text, on the house doctrine.
 */
private object DrsTileFavoritesStore {
    private const val PREFS_NAME = "drs_panel_usage"
    private const val KEY = "text_tools_favorites"
    private val json = Json { ignoreUnknownKeys = true }
    private val serializer = ListSerializer(Int.serializer())

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun load(context: Context): List<Int> = runCatching {
        json.decodeFromString(serializer, prefs(context).getString(KEY, "[]") ?: "[]")
    }.getOrDefault(emptyList())

    fun save(context: Context, codes: List<Int>) {
        prefs(context).edit().putString(KEY, json.encodeToString(serializer, codes)).apply()
    }
}

private val PANEL_SECTIONS: List<DrsTextToolsPanelSection> = listOf(
    DrsTextToolsPanelSection(
        titleRes = R.string.drs__text_tools__section_case,
        items = listOf(
            toolItem(
                DrsTextTool.UPPERCASE,
                R.string.drs__text_tools__tool_uppercase,
                R.string.drs__text_tools__desc_uppercase,
                Icons.Default.FormatSize,
            ),
            toolItem(
                DrsTextTool.LOWERCASE,
                R.string.drs__text_tools__tool_lowercase,
                R.string.drs__text_tools__desc_lowercase,
                Icons.Default.TextFields,
            ),
            toolItem(
                DrsTextTool.TITLE_CASE,
                R.string.drs__text_tools__tool_title_case,
                R.string.drs__text_tools__desc_title_case,
                Icons.Default.TextIncrease,
            ),
            toolItem(
                DrsTextTool.SENTENCE_CASE,
                R.string.drs__text_tools__tool_sentence_case,
                R.string.drs__text_tools__desc_sentence_case,
                Icons.Default.ShortText,
            ),
            // DRS v1.5.0: inverts the case of every cased letter.
            toolItem(
                DrsTextTool.TOGGLE_CASE,
                R.string.drs__text_tools__tool_toggle_case,
                R.string.drs__text_tools__desc_toggle_case,
                Icons.Default.SwapVert,
            ),
            // DRS v2.2.0: the code-style case pair — snake joins with
            // underscores (Arabic words join too, no case invented),
            // camel refuses non-ASCII tokens byte-identically.
            toolItem(
                DrsTextTool.TO_SNAKE_CASE,
                R.string.drs__text_tools__tool_to_snake_case,
                R.string.drs__text_tools__desc_to_snake_case,
                Icons.Default.Code,
            ),
            toolItem(
                DrsTextTool.TO_CAMEL_CASE,
                R.string.drs__text_tools__tool_to_camel_case,
                R.string.drs__text_tools__desc_to_camel_case,
                Icons.Default.TextFields,
            ),
        ),
    ),
    DrsTextToolsPanelSection(
        titleRes = R.string.drs__text_tools__section_spaces,
        items = listOf(
            toolItem(
                DrsTextTool.TRIM_SPACES,
                R.string.drs__text_tools__tool_trim_spaces,
                R.string.drs__text_tools__desc_trim_spaces,
                Icons.Default.SpaceBar,
            ),
            toolItem(
                DrsTextTool.TRIM_LINE_EDGES,
                R.string.drs__text_tools__tool_trim_line_edges,
                R.string.drs__text_tools__desc_trim_line_edges,
                Icons.Default.ClearAll,
            ),
            // DRS v1.5.0: whitespace family additions.
            toolItem(
                DrsTextTool.TABS_TO_SPACES,
                R.string.drs__text_tools__tool_tabs_to_spaces,
                R.string.drs__text_tools__desc_tabs_to_spaces,
                Icons.Default.KeyboardTab,
            ),
            // DRS v1.6.0: the inverse mapping — runs of 4 spaces -> tab.
            toolItem(
                DrsTextTool.SPACES_TO_TABS,
                R.string.drs__text_tools__tool_spaces_to_tabs,
                R.string.drs__text_tools__desc_spaces_to_tabs,
                Icons.Default.KeyboardTab,
            ),
            // DRS v1.7.0: removes EVERY horizontal space (hashtag style).
            toolItem(
                DrsTextTool.REMOVE_ALL_SPACES,
                R.string.drs__text_tools__tool_remove_all_spaces,
                R.string.drs__text_tools__desc_remove_all_spaces,
                Icons.Default.SpaceBar,
            ),
            // DRS v1.8.0: one space between digits and letters (both ways).
            toolItem(
                DrsTextTool.SEPARATE_DIGIT_LETTERS,
                R.string.drs__text_tools__tool_separate_digit_letters,
                R.string.drs__text_tools__desc_separate_digit_letters,
                Icons.Default.SpaceBar,
            ),
            toolItem(
                DrsTextTool.REMOVE_ZERO_WIDTH,
                R.string.drs__text_tools__tool_remove_zero_width,
                R.string.drs__text_tools__desc_remove_zero_width,
                Icons.Default.VisibilityOff,
            ),
            toolItem(
                DrsTextTool.REMOVE_EMPTY_LINES,
                R.string.drs__text_tools__tool_remove_empty_lines,
                R.string.drs__text_tools__desc_remove_empty_lines,
                Icons.Default.FormatLineSpacing,
            ),
            // DRS v1.3.0: collapse runs of blank lines into one.
            toolItem(
                DrsTextTool.COLLAPSE_EMPTY_LINES,
                R.string.drs__text_tools__tool_collapse_empty_lines,
                R.string.drs__text_tools__desc_collapse_empty_lines,
                Icons.Default.Compress,
            ),
            // DRS v1.6.0: blank lines at the text's edges only.
            toolItem(
                DrsTextTool.TRIM_BLANK_EDGES,
                R.string.drs__text_tools__tool_trim_blank_edges,
                R.string.drs__text_tools__desc_trim_blank_edges,
                Icons.Default.ClearAll,
            ),
            toolItem(
                DrsTextTool.REMOVE_LINE_BREAKS,
                R.string.drs__text_tools__tool_remove_line_breaks,
                R.string.drs__text_tools__desc_remove_line_breaks,
                Icons.Default.SubdirectoryArrowRight,
            ),
            // DRS v1.7.0: list <-> lines (Arabic comma aware) — the two
            // directions of turning a comma list into one item per line.
            toolItem(
                DrsTextTool.SPLIT_TO_LINES,
                R.string.drs__text_tools__tool_split_to_lines,
                R.string.drs__text_tools__desc_split_to_lines,
                Icons.Default.FormatListBulleted,
            ),
            toolItem(
                DrsTextTool.JOIN_LINES,
                R.string.drs__text_tools__tool_join_lines,
                R.string.drs__text_tools__desc_join_lines,
                Icons.Default.MergeType,
            ),
            // DRS v1.7.0: strips emoji so pasted social text stops
            // polluting the counting/sorting tools.
            toolItem(
                DrsTextTool.STRIP_EMOJI,
                R.string.drs__text_tools__tool_strip_emoji,
                R.string.drs__text_tools__desc_strip_emoji,
                Icons.Default.SentimentNeutral,
            ),
            toolItem(
                DrsTextTool.SORT_LINES,
                R.string.drs__text_tools__tool_sort_lines,
                R.string.drs__text_tools__desc_sort_lines,
                Icons.Default.Sort,
            ),
            toolItem(
                DrsTextTool.REMOVE_DUPLICATE_LINES,
                R.string.drs__text_tools__tool_remove_duplicate_lines,
                R.string.drs__text_tools__desc_remove_duplicate_lines,
                Icons.Default.FilterList,
            ),
            // DRS v1.1.0: line-ordering additions.
            toolItem(
                DrsTextTool.NUMBER_LINES,
                R.string.drs__text_tools__tool_number_lines,
                R.string.drs__text_tools__desc_number_lines,
                Icons.Default.FormatListNumbered,
            ),
            toolItem(
                DrsTextTool.REVERSE_LINES,
                R.string.drs__text_tools__tool_reverse_lines,
                R.string.drs__text_tools__desc_reverse_lines,
                Icons.Default.SwapVert,
            ),
            // DRS v1.4.0: descending counterpart of SORT_LINES.
            toolItem(
                DrsTextTool.SORT_LINES_DESC,
                R.string.drs__text_tools__tool_sort_lines_desc,
                R.string.drs__text_tools__desc_sort_lines_desc,
                Icons.Default.SortByAlpha,
            ),
            // DRS v1.5.0: stable sort by line length.
            toolItem(
                DrsTextTool.SORT_LINES_BY_LENGTH,
                R.string.drs__text_tools__tool_sort_lines_by_length,
                R.string.drs__text_tools__desc_sort_lines_by_length,
                Icons.Default.FormatLineSpacing,
            ),
            // DRS v2.2.0: the natural sort — numbers the way a human
            // reads them (file2 قبل file10، قائمة 2 قبل قائمة 10)،
            // بأرقام عشوائية الدقة لا تفيض أبدًا.
            toolItem(
                DrsTextTool.SORT_LINES_NATURAL,
                R.string.drs__text_tools__tool_sort_lines_natural,
                R.string.drs__text_tools__desc_sort_lines_natural,
                Icons.Default.Sort,
            ),
            // DRS v1.5.0: word-level dedup (first occurrence wins).
            toolItem(
                DrsTextTool.REMOVE_DUPLICATE_WORDS,
                R.string.drs__text_tools__tool_remove_duplicate_words,
                R.string.drs__text_tools__desc_remove_duplicate_words,
                Icons.Default.WrapText,
            ),
            // DRS v1.6.0: word-order reversal of every line.
            toolItem(
                DrsTextTool.REVERSE_WORDS,
                R.string.drs__text_tools__tool_reverse_words,
                R.string.drs__text_tools__desc_reverse_words,
                Icons.Default.SwapHoriz,
            ),
        ),
    ),
    DrsTextToolsPanelSection(
        titleRes = R.string.drs__text_tools__section_arabic,
        items = listOf(
            toolItem(
                DrsTextTool.REMOVE_DIACRITICS,
                R.string.drs__text_tools__tool_remove_diacritics,
                R.string.drs__text_tools__desc_remove_diacritics,
                Icons.Default.FormatClear,
            ),
            // DRS v1.4.0 (public): the sentence vocalization — the honest
            // counterpart: known words take their canonical harakat, unknown
            // words stay untouched, never guessed.
            toolItem(
                DrsTextTool.TASHKEEL_TEXT,
                R.string.drs__text_tools__tool_tashkeel_text,
                R.string.drs__text_tools__desc_tashkeel_text,
                Icons.Default.Spellcheck,
            ),
            // DRS v1.6.0: the number-to-words rendering — a clean integer
            // field speaks its Arabic words, prose stays untouched.
            toolItem(
                DrsTextTool.NUMBER_WORDS,
                R.string.drs__text_tools__tool_number_words,
                R.string.drs__text_tools__desc_number_words,
                Icons.Default.Calculate,
            ),
            // DRS v1.7.0: the financial tafqit — a clean amount speaks its
            // formal check words «فقط … لا غير», prose stays untouched.
            toolItem(
                DrsTextTool.TAFQIT,
                R.string.drs__text_tools__tool_tafqit,
                R.string.drs__text_tools__desc_tafqit,
                Icons.Default.Paid,
            ),
            // DRS v1.8.0: the date in words — a clean date speaks its formal
            // documentary phrase, prose stays untouched. The trilogy closes:
            // numbers, amounts, dates.
            toolItem(
                DrsTextTool.DATE_WORDS,
                R.string.drs__text_tools__tool_date_words,
                R.string.drs__text_tools__desc_date_words,
                Icons.Default.Event,
            ),
            // DRS v1.9.0: the clock time in words — a clean clock time speaks
            // its formal spoken phrase, prose stays untouched. The quartet
            // closes: numbers, amounts, dates, times.
            toolItem(
                DrsTextTool.TIME_WORDS,
                R.string.drs__text_tools__tool_time_words,
                R.string.drs__text_tools__desc_time_words,
                Icons.Default.Schedule,
            ),
            // DRS v1.10.0: the fraction in words — a clean fraction speaks
            // its formal written words, prose stays untouched. The fifth
            // round opens: fractions, ordinals, weekdays.
            toolItem(
                DrsTextTool.FRACTION_WORDS,
                R.string.drs__text_tools__tool_fraction_words,
                R.string.drs__text_tools__desc_fraction_words,
                Icons.Default.PieChart,
            ),
            // DRS v1.10.0: the weekday — a clean date resolves to its
            // weekday name by the closed Sakamoto congruence, prose stays
            // untouched.
            toolItem(
                DrsTextTool.WEEKDAY,
                R.string.drs__text_tools__tool_weekday,
                R.string.drs__text_tools__desc_weekday,
                Icons.Default.Today,
            ),
            // DRS v1.10.0: the ordinal in words — a clean integer speaks
            // its ordinal (الأول، الحادي والعشرون، الألف), prose stays
            // untouched. The fifth round closes its trio.
            toolItem(
                DrsTextTool.ORDINAL_WORDS,
                R.string.drs__text_tools__tool_ordinal_words,
                R.string.drs__text_tools__desc_ordinal_words,
                Icons.Default.EmojiEvents,
            ),
            // DRS v2.0.0: the decimal in words — a clean decimal speaks
            // its digit-by-digit words (واحد فاصلة خمسة صفر), prose
            // stays untouched. The sixth round opens: the decimal tail
            // speaks, and the words speak back numbers.
            toolItem(
                DrsTextTool.DECIMAL_WORDS,
                R.string.drs__text_tools__tool_decimal_words,
                R.string.drs__text_tools__desc_decimal_words,
                Icons.Default.Percent,
            ),
            // DRS v2.0.0: words to number — the release lexicon speaks
            // back: «ثلاثة وعشرون» returns 23. The words family now
            // round-trips on one source of truth.
            toolItem(
                DrsTextTool.WORDS_TO_NUMBER,
                R.string.drs__text_tools__tool_words_to_number,
                R.string.drs__text_tools__desc_words_to_number,
                Icons.Default.Functions,
            ),
            // DRS v2.1.0: the Gregorian→Hijri conversion — a clean date
            // speaks its Hijri phrase (الأول من رمضان عام ...) on the
            // platform's Umm al-Qura table, the zone closed 1300..1600.
            toolItem(
                DrsTextTool.GREGORIAN_TO_HIJRI,
                R.string.drs__text_tools__tool_gregorian_to_hijri,
                R.string.drs__text_tools__desc_gregorian_to_hijri,
                Icons.Default.CalendarMonth,
            ),
            // DRS v2.1.0: the Hijri→Gregorian conversion — the inverse:
            // a clean Hijri date speaks its Gregorian phrase through
            // the DATE_WORDS formatter. The calendar round-trips.
            toolItem(
                DrsTextTool.HIJRI_TO_GREGORIAN,
                R.string.drs__text_tools__tool_hijri_to_gregorian,
                R.string.drs__text_tools__desc_hijri_to_gregorian,
                Icons.Default.DateRange,
            ),
            // DRS v1.3.0: tatweel removal + digit conversions.
            toolItem(
                DrsTextTool.REMOVE_TATWEEL,
                R.string.drs__text_tools__tool_remove_tatweel,
                R.string.drs__text_tools__desc_remove_tatweel,
                Icons.Default.HighlightOff,
            ),
            toolItem(
                DrsTextTool.TO_ARABIC_DIGITS,
                R.string.drs__text_tools__tool_to_arabic_digits,
                R.string.drs__text_tools__desc_to_arabic_digits,
                Icons.Default.Translate,
            ),
            toolItem(
                DrsTextTool.TO_WESTERN_DIGITS,
                R.string.drs__text_tools__tool_to_western_digits,
                R.string.drs__text_tools__desc_to_western_digits,
                Icons.Default.SwapHoriz,
            ),
            // DRS v2.2.0: the link-safe slug — العربية تحتفظ بحروفها
            // المطوية لا بنقل صوتي مخترَع، والترقيم الصافي يعود كما هو.
            toolItem(
                DrsTextTool.SLUGIFY,
                R.string.drs__text_tools__tool_slugify,
                R.string.drs__text_tools__desc_slugify,
                Icons.Default.Link,
            ),
            // DRS v1.4.0: unify Arabic letter variants for copy/search.
            toolItem(
                DrsTextTool.NORMALIZE_ARABIC,
                R.string.drs__text_tools__tool_normalize_arabic,
                R.string.drs__text_tools__desc_normalize_arabic,
                Icons.Default.TextFields,
            ),
            // DRS v1.7.0: repairs PDF/web Arabic presentation forms.
            toolItem(
                DrsTextTool.NORMALIZE_ARABIC_FORMS,
                R.string.drs__text_tools__tool_normalize_arabic_forms,
                R.string.drs__text_tools__desc_normalize_arabic_forms,
                Icons.Default.TextFields,
            ),
            // DRS v1.5.0: Latin sentence punctuation → Arabic marks.
            toolItem(
                DrsTextTool.TO_ARABIC_PUNCTUATION,
                R.string.drs__text_tools__tool_to_arabic_punctuation,
                R.string.drs__text_tools__desc_to_arabic_punctuation,
                Icons.Default.QuestionMark,
            ),
            // DRS v1.6.0: the inverse mapping back to Latin marks.
            toolItem(
                DrsTextTool.TO_WESTERN_PUNCTUATION,
                R.string.drs__text_tools__tool_to_western_punctuation,
                R.string.drs__text_tools__desc_to_western_punctuation,
                Icons.Default.QuestionMark,
            ),
        ),
    ),
    DrsTextToolsPanelSection(
        titleRes = R.string.drs__text_tools__section_punctuation,
        items = listOf(
            toolItem(
                DrsTextTool.NORMALIZE_PUNCTUATION,
                R.string.drs__text_tools__tool_normalize_punctuation,
                R.string.drs__text_tools__desc_normalize_punctuation,
                Icons.Default.Rule,
            ),
            toolItem(
                DrsTextTool.CLEAN_TEXT,
                R.string.drs__text_tools__tool_clean_text,
                R.string.drs__text_tools__desc_clean_text,
                Icons.Default.AutoFixHigh,
            ),
            // DRS v1.8.0: strips every Unicode punctuation mark.
            toolItem(
                DrsTextTool.REMOVE_PUNCTUATION,
                R.string.drs__text_tools__tool_remove_punctuation,
                R.string.drs__text_tools__desc_remove_punctuation,
                Icons.Default.Block,
            ),
            // DRS v1.2.0: locale-aware quote wrapping.
            toolItem(
                DrsTextTool.WRAP_QUOTES,
                R.string.drs__text_tools__tool_wrap_quotes,
                R.string.drs__text_tools__desc_wrap_quotes,
                Icons.Default.FormatQuote,
            ),
            // DRS v1.5.0: paren wrapping + one sentence per line.
            toolItem(
                DrsTextTool.WRAP_PARENS,
                R.string.drs__text_tools__tool_wrap_parens,
                R.string.drs__text_tools__desc_wrap_parens,
                Icons.Default.Code,
            ),
            toolItem(
                DrsTextTool.SENTENCE_PER_LINE,
                R.string.drs__text_tools__tool_sentence_per_line,
                R.string.drs__text_tools__desc_sentence_per_line,
                Icons.Default.FormatListBulleted,
            ),
            // DRS v1.7.0: the URL encode/decode pair — Arabic links become
            // shareable percent-encoded components and back.
            toolItem(
                DrsTextTool.URL_ENCODE,
                R.string.drs__text_tools__tool_url_encode,
                R.string.drs__text_tools__desc_url_encode,
                Icons.Default.Link,
            ),
            toolItem(
                DrsTextTool.URL_DECODE,
                R.string.drs__text_tools__tool_url_decode,
                R.string.drs__text_tools__desc_url_decode,
                Icons.Default.LinkOff,
            ),
        ),
    ),
    DrsTextToolsPanelSection(
        titleRes = R.string.drs__text_tools__section_lines,
        items = listOf(
            toolItem(
                DrsTextTool.DELETE_LINE,
                R.string.drs__text_tools__tool_delete_line,
                R.string.drs__text_tools__desc_delete_line,
                Icons.Default.DeleteSweep,
            ),
            toolItem(
                DrsTextTool.DELETE_TO_LINE_START,
                R.string.drs__text_tools__tool_delete_to_line_start,
                R.string.drs__text_tools__desc_delete_to_line_start,
                Icons.Default.FirstPage,
            ),
            toolItem(
                DrsTextTool.DELETE_TO_LINE_END,
                R.string.drs__text_tools__tool_delete_to_line_end,
                R.string.drs__text_tools__desc_delete_to_line_end,
                Icons.Default.LastPage,
            ),
        ),
    ),
    // DRS v1.21.0: the invisible directional/joining marks — the tools
    // RTL authors keep reaching for: RLM/LRM steer neutral punctuation
    // between mixed-direction runs, ZWJ/ZWNJ (نصف المسافة) join or break
    // cursive connections and emoji sequences.
    DrsTextToolsPanelSection(
        titleRes = R.string.drs__text_tools__section_marks,
        items = listOf(
            toolItem(
                DrsTextTool.INSERT_RLM,
                R.string.drs__text_tools__tool_insert_rlm,
                R.string.drs__text_tools__desc_insert_rlm,
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
            ),
            toolItem(
                DrsTextTool.INSERT_LRM,
                R.string.drs__text_tools__tool_insert_lrm,
                R.string.drs__text_tools__desc_insert_lrm,
                Icons.AutoMirrored.Filled.KeyboardArrowLeft,
            ),
            toolItem(
                DrsTextTool.INSERT_ZWJ,
                R.string.drs__text_tools__tool_insert_zwj,
                R.string.drs__text_tools__desc_insert_zwj,
                Icons.Default.Link,
            ),
            toolItem(
                DrsTextTool.INSERT_ZWNJ,
                R.string.drs__text_tools__tool_insert_zwnj,
                R.string.drs__text_tools__desc_insert_zwnj,
                Icons.Default.LinkOff,
            ),
        ),
    ),
    DrsTextToolsPanelSection(
        titleRes = R.string.drs__text_tools__section_quick,
        items = listOf(
            DrsTextToolsPanelItem(
                code = KeyCode.UNDO,
                labelRes = R.string.quick_action__undo,
                descRes = R.string.drs__text_tools__desc_undo,
                icon = Icons.AutoMirrored.Filled.Undo,
            ),
            DrsTextToolsPanelItem(
                code = KeyCode.REDO,
                labelRes = R.string.quick_action__redo,
                descRes = R.string.drs__text_tools__desc_redo,
                icon = Icons.AutoMirrored.Filled.Redo,
            ),
            DrsTextToolsPanelItem(
                code = KeyCode.CLIPBOARD_SELECT_ALL,
                labelRes = R.string.quick_action__clipboard_select_all,
                descRes = R.string.drs__text_tools__desc_select_all,
                icon = Icons.Default.SelectAll,
            ),
            DrsTextToolsPanelItem(
                code = KeyCode.CLIPBOARD_COPY,
                labelRes = R.string.quick_action__clipboard_copy,
                descRes = R.string.drs__text_tools__desc_copy,
                icon = Icons.Default.ContentCopy,
            ),
            DrsTextToolsPanelItem(
                code = KeyCode.CLIPBOARD_CUT,
                labelRes = R.string.quick_action__clipboard_cut,
                descRes = R.string.drs__text_tools__desc_cut,
                icon = Icons.Default.ContentCut,
            ),
            DrsTextToolsPanelItem(
                code = KeyCode.CLIPBOARD_PASTE,
                labelRes = R.string.quick_action__clipboard_paste,
                descRes = R.string.drs__text_tools__desc_paste,
                icon = Icons.Default.ContentPaste,
            ),
            toolItem(
                DrsTextTool.COUNT,
                R.string.drs__text_tools__tool_count,
                R.string.drs__text_tools__desc_count,
                Icons.Default.Calculate,
            ),
        ),
    ),
)

/**
 * DRS v2.2.0 — the flat tile catalogue shared by the recents engine and
 * the favorites resolution: every tile the panel renders, in panel order.
 * Declared AFTER [PANEL_SECTIONS] on purpose — top-level initializers run
 * in file order, and a forward reference would see a null catalogue.
 */
private val ALL_ITEMS: List<DrsTextToolsPanelItem> =
    PANEL_SECTIONS.flatMap { section -> section.items }

private val ITEMS_BY_CODE: Map<Int, DrsTextToolsPanelItem> =
    ALL_ITEMS.associateBy { it.code }

/**
 * DRS v1.5.0: the panel title of every text tool, shared with the smart
 * bar's most-used tiles so a heavy text-tool user sees real names instead
 * of the invalid-fatal placeholder. Mirrors [PANEL_SECTIONS] labels.
 */
fun textToolTitleRes(tool: DrsTextTool): Int = when (tool) {
    DrsTextTool.UPPERCASE -> R.string.drs__text_tools__tool_uppercase
    DrsTextTool.LOWERCASE -> R.string.drs__text_tools__tool_lowercase
    DrsTextTool.TITLE_CASE -> R.string.drs__text_tools__tool_title_case
    DrsTextTool.SENTENCE_CASE -> R.string.drs__text_tools__tool_sentence_case
    DrsTextTool.TOGGLE_CASE -> R.string.drs__text_tools__tool_toggle_case
    DrsTextTool.TRIM_SPACES -> R.string.drs__text_tools__tool_trim_spaces
    DrsTextTool.TRIM_LINE_EDGES -> R.string.drs__text_tools__tool_trim_line_edges
    DrsTextTool.TABS_TO_SPACES -> R.string.drs__text_tools__tool_tabs_to_spaces
    DrsTextTool.SPACES_TO_TABS -> R.string.drs__text_tools__tool_spaces_to_tabs
    DrsTextTool.TRIM_BLANK_EDGES -> R.string.drs__text_tools__tool_trim_blank_edges
    DrsTextTool.REVERSE_WORDS -> R.string.drs__text_tools__tool_reverse_words
    DrsTextTool.NORMALIZE_ARABIC_FORMS -> R.string.drs__text_tools__tool_normalize_arabic_forms
    DrsTextTool.SPLIT_TO_LINES -> R.string.drs__text_tools__tool_split_to_lines
    DrsTextTool.JOIN_LINES -> R.string.drs__text_tools__tool_join_lines
    DrsTextTool.REMOVE_ALL_SPACES -> R.string.drs__text_tools__tool_remove_all_spaces
    DrsTextTool.STRIP_EMOJI -> R.string.drs__text_tools__tool_strip_emoji
    DrsTextTool.URL_ENCODE -> R.string.drs__text_tools__tool_url_encode
    DrsTextTool.URL_DECODE -> R.string.drs__text_tools__tool_url_decode
    // DRS v1.8.0: the mixed-script cleanup pair.
    DrsTextTool.SEPARATE_DIGIT_LETTERS -> R.string.drs__text_tools__tool_separate_digit_letters
    DrsTextTool.REMOVE_PUNCTUATION -> R.string.drs__text_tools__tool_remove_punctuation
    DrsTextTool.REMOVE_ZERO_WIDTH -> R.string.drs__text_tools__tool_remove_zero_width
    DrsTextTool.REMOVE_EMPTY_LINES -> R.string.drs__text_tools__tool_remove_empty_lines
    DrsTextTool.COLLAPSE_EMPTY_LINES -> R.string.drs__text_tools__tool_collapse_empty_lines
    DrsTextTool.REMOVE_LINE_BREAKS -> R.string.drs__text_tools__tool_remove_line_breaks
    DrsTextTool.SORT_LINES -> R.string.drs__text_tools__tool_sort_lines
    DrsTextTool.SORT_LINES_DESC -> R.string.drs__text_tools__tool_sort_lines_desc
    DrsTextTool.SORT_LINES_BY_LENGTH -> R.string.drs__text_tools__tool_sort_lines_by_length
    DrsTextTool.REMOVE_DUPLICATE_LINES -> R.string.drs__text_tools__tool_remove_duplicate_lines
    DrsTextTool.REMOVE_DUPLICATE_WORDS -> R.string.drs__text_tools__tool_remove_duplicate_words
    DrsTextTool.NUMBER_LINES -> R.string.drs__text_tools__tool_number_lines
    DrsTextTool.REVERSE_LINES -> R.string.drs__text_tools__tool_reverse_lines
    DrsTextTool.REMOVE_DIACRITICS -> R.string.drs__text_tools__tool_remove_diacritics
    // DRS v1.4.0 (public): the sentence vocalization (REMOVE_DIACRITICS' counterpart).
    DrsTextTool.TASHKEEL_TEXT -> R.string.drs__text_tools__tool_tashkeel_text
    // DRS v1.6.0: the number-to-words rendering.
    DrsTextTool.NUMBER_WORDS -> R.string.drs__text_tools__tool_number_words
    // DRS v1.7.0: the financial tafqit.
    DrsTextTool.TAFQIT -> R.string.drs__text_tools__tool_tafqit
    // DRS v1.8.0: the date in words.
    DrsTextTool.DATE_WORDS -> R.string.drs__text_tools__tool_date_words
    // DRS v1.9.0: the clock time in words.
    DrsTextTool.TIME_WORDS -> R.string.drs__text_tools__tool_time_words
    // DRS v1.10.0: the fraction in words.
    DrsTextTool.FRACTION_WORDS -> R.string.drs__text_tools__tool_fraction_words
    // DRS v1.10.0: the weekday resolution.
    DrsTextTool.WEEKDAY -> R.string.drs__text_tools__tool_weekday
    // DRS v1.10.0: the ordinal in words.
    DrsTextTool.ORDINAL_WORDS -> R.string.drs__text_tools__tool_ordinal_words
    // DRS v2.0.0: the decimal in words.
    DrsTextTool.DECIMAL_WORDS -> R.string.drs__text_tools__tool_decimal_words
    // DRS v2.0.0: the words-to-number mirror.
    DrsTextTool.WORDS_TO_NUMBER -> R.string.drs__text_tools__tool_words_to_number
    DrsTextTool.GREGORIAN_TO_HIJRI -> R.string.drs__text_tools__tool_gregorian_to_hijri
    DrsTextTool.HIJRI_TO_GREGORIAN -> R.string.drs__text_tools__tool_hijri_to_gregorian
    // DRS v2.2.0: the four new tiles.
    DrsTextTool.SORT_LINES_NATURAL -> R.string.drs__text_tools__tool_sort_lines_natural
    DrsTextTool.SLUGIFY -> R.string.drs__text_tools__tool_slugify
    DrsTextTool.TO_SNAKE_CASE -> R.string.drs__text_tools__tool_to_snake_case
    DrsTextTool.TO_CAMEL_CASE -> R.string.drs__text_tools__tool_to_camel_case
    DrsTextTool.REMOVE_TATWEEL -> R.string.drs__text_tools__tool_remove_tatweel
    DrsTextTool.TO_ARABIC_DIGITS -> R.string.drs__text_tools__tool_to_arabic_digits
    DrsTextTool.TO_WESTERN_DIGITS -> R.string.drs__text_tools__tool_to_western_digits
    DrsTextTool.TO_ARABIC_PUNCTUATION -> R.string.drs__text_tools__tool_to_arabic_punctuation
    DrsTextTool.TO_WESTERN_PUNCTUATION -> R.string.drs__text_tools__tool_to_western_punctuation
    DrsTextTool.NORMALIZE_ARABIC -> R.string.drs__text_tools__tool_normalize_arabic
    DrsTextTool.NORMALIZE_PUNCTUATION -> R.string.drs__text_tools__tool_normalize_punctuation
    DrsTextTool.CLEAN_TEXT -> R.string.drs__text_tools__tool_clean_text
    DrsTextTool.WRAP_QUOTES -> R.string.drs__text_tools__tool_wrap_quotes
    DrsTextTool.WRAP_PARENS -> R.string.drs__text_tools__tool_wrap_parens
    DrsTextTool.SENTENCE_PER_LINE -> R.string.drs__text_tools__tool_sentence_per_line
    DrsTextTool.COUNT -> R.string.drs__text_tools__tool_count
    DrsTextTool.DELETE_LINE -> R.string.drs__text_tools__tool_delete_line
    DrsTextTool.DELETE_TO_LINE_START -> R.string.drs__text_tools__tool_delete_to_line_start
    DrsTextTool.DELETE_TO_LINE_END -> R.string.drs__text_tools__tool_delete_to_line_end
    DrsTextTool.INSERT_RLM -> R.string.drs__text_tools__tool_insert_rlm
    DrsTextTool.INSERT_LRM -> R.string.drs__text_tools__tool_insert_lrm
    DrsTextTool.INSERT_ZWJ -> R.string.drs__text_tools__tool_insert_zwj
    DrsTextTool.INSERT_ZWNJ -> R.string.drs__text_tools__tool_insert_zwnj
}

/** DRS v1.5.0: the panel description of every text tool (tile tooltips). */
fun textToolDescRes(tool: DrsTextTool): Int = when (tool) {
    DrsTextTool.UPPERCASE -> R.string.drs__text_tools__desc_uppercase
    DrsTextTool.LOWERCASE -> R.string.drs__text_tools__desc_lowercase
    DrsTextTool.TITLE_CASE -> R.string.drs__text_tools__desc_title_case
    DrsTextTool.SENTENCE_CASE -> R.string.drs__text_tools__desc_sentence_case
    DrsTextTool.TOGGLE_CASE -> R.string.drs__text_tools__desc_toggle_case
    DrsTextTool.TRIM_SPACES -> R.string.drs__text_tools__desc_trim_spaces
    DrsTextTool.TRIM_LINE_EDGES -> R.string.drs__text_tools__desc_trim_line_edges
    DrsTextTool.TABS_TO_SPACES -> R.string.drs__text_tools__desc_tabs_to_spaces
    DrsTextTool.SPACES_TO_TABS -> R.string.drs__text_tools__desc_spaces_to_tabs
    DrsTextTool.TRIM_BLANK_EDGES -> R.string.drs__text_tools__desc_trim_blank_edges
    DrsTextTool.REVERSE_WORDS -> R.string.drs__text_tools__desc_reverse_words
    DrsTextTool.NORMALIZE_ARABIC_FORMS -> R.string.drs__text_tools__desc_normalize_arabic_forms
    DrsTextTool.SPLIT_TO_LINES -> R.string.drs__text_tools__desc_split_to_lines
    DrsTextTool.JOIN_LINES -> R.string.drs__text_tools__desc_join_lines
    DrsTextTool.REMOVE_ALL_SPACES -> R.string.drs__text_tools__desc_remove_all_spaces
    DrsTextTool.STRIP_EMOJI -> R.string.drs__text_tools__desc_strip_emoji
    DrsTextTool.URL_ENCODE -> R.string.drs__text_tools__desc_url_encode
    DrsTextTool.URL_DECODE -> R.string.drs__text_tools__desc_url_decode
    // DRS v1.8.0: the mixed-script cleanup pair.
    DrsTextTool.SEPARATE_DIGIT_LETTERS -> R.string.drs__text_tools__desc_separate_digit_letters
    DrsTextTool.REMOVE_PUNCTUATION -> R.string.drs__text_tools__desc_remove_punctuation
    DrsTextTool.REMOVE_ZERO_WIDTH -> R.string.drs__text_tools__desc_remove_zero_width
    DrsTextTool.REMOVE_EMPTY_LINES -> R.string.drs__text_tools__desc_remove_empty_lines
    DrsTextTool.COLLAPSE_EMPTY_LINES -> R.string.drs__text_tools__desc_collapse_empty_lines
    DrsTextTool.REMOVE_LINE_BREAKS -> R.string.drs__text_tools__desc_remove_line_breaks
    DrsTextTool.SORT_LINES -> R.string.drs__text_tools__desc_sort_lines
    DrsTextTool.SORT_LINES_DESC -> R.string.drs__text_tools__desc_sort_lines_desc
    DrsTextTool.SORT_LINES_BY_LENGTH -> R.string.drs__text_tools__desc_sort_lines_by_length
    DrsTextTool.REMOVE_DUPLICATE_LINES -> R.string.drs__text_tools__desc_remove_duplicate_lines
    DrsTextTool.REMOVE_DUPLICATE_WORDS -> R.string.drs__text_tools__desc_remove_duplicate_words
    DrsTextTool.NUMBER_LINES -> R.string.drs__text_tools__desc_number_lines
    DrsTextTool.REVERSE_LINES -> R.string.drs__text_tools__desc_reverse_lines
    DrsTextTool.REMOVE_DIACRITICS -> R.string.drs__text_tools__desc_remove_diacritics
    // DRS v1.4.0 (public): the sentence vocalization (REMOVE_DIACRITICS' counterpart).
    DrsTextTool.TASHKEEL_TEXT -> R.string.drs__text_tools__desc_tashkeel_text
    // DRS v1.6.0: the number-to-words rendering.
    DrsTextTool.NUMBER_WORDS -> R.string.drs__text_tools__desc_number_words
    // DRS v1.7.0: the financial tafqit.
    DrsTextTool.TAFQIT -> R.string.drs__text_tools__desc_tafqit
    // DRS v1.8.0: the date in words.
    DrsTextTool.DATE_WORDS -> R.string.drs__text_tools__desc_date_words
    // DRS v1.9.0: the clock time in words.
    DrsTextTool.TIME_WORDS -> R.string.drs__text_tools__desc_time_words
    // DRS v1.10.0: the fraction in words.
    DrsTextTool.FRACTION_WORDS -> R.string.drs__text_tools__desc_fraction_words
    // DRS v1.10.0: the weekday resolution.
    DrsTextTool.WEEKDAY -> R.string.drs__text_tools__desc_weekday
    // DRS v1.10.0: the ordinal in words.
    DrsTextTool.ORDINAL_WORDS -> R.string.drs__text_tools__desc_ordinal_words
    // DRS v2.0.0: the decimal in words.
    DrsTextTool.DECIMAL_WORDS -> R.string.drs__text_tools__desc_decimal_words
    // DRS v2.0.0: the words-to-number mirror.
    DrsTextTool.WORDS_TO_NUMBER -> R.string.drs__text_tools__desc_words_to_number
    DrsTextTool.GREGORIAN_TO_HIJRI -> R.string.drs__text_tools__desc_gregorian_to_hijri
    DrsTextTool.HIJRI_TO_GREGORIAN -> R.string.drs__text_tools__desc_hijri_to_gregorian
    // DRS v2.2.0: the four new tiles.
    DrsTextTool.SORT_LINES_NATURAL -> R.string.drs__text_tools__desc_sort_lines_natural
    DrsTextTool.SLUGIFY -> R.string.drs__text_tools__desc_slugify
    DrsTextTool.TO_SNAKE_CASE -> R.string.drs__text_tools__desc_to_snake_case
    DrsTextTool.TO_CAMEL_CASE -> R.string.drs__text_tools__desc_to_camel_case
    DrsTextTool.REMOVE_TATWEEL -> R.string.drs__text_tools__desc_remove_tatweel
    DrsTextTool.TO_ARABIC_DIGITS -> R.string.drs__text_tools__desc_to_arabic_digits
    DrsTextTool.TO_WESTERN_DIGITS -> R.string.drs__text_tools__desc_to_western_digits
    DrsTextTool.TO_ARABIC_PUNCTUATION -> R.string.drs__text_tools__desc_to_arabic_punctuation
    DrsTextTool.TO_WESTERN_PUNCTUATION -> R.string.drs__text_tools__desc_to_western_punctuation
    DrsTextTool.NORMALIZE_ARABIC -> R.string.drs__text_tools__desc_normalize_arabic
    DrsTextTool.NORMALIZE_PUNCTUATION -> R.string.drs__text_tools__desc_normalize_punctuation
    DrsTextTool.CLEAN_TEXT -> R.string.drs__text_tools__desc_clean_text
    DrsTextTool.WRAP_QUOTES -> R.string.drs__text_tools__desc_wrap_quotes
    DrsTextTool.WRAP_PARENS -> R.string.drs__text_tools__desc_wrap_parens
    DrsTextTool.SENTENCE_PER_LINE -> R.string.drs__text_tools__desc_sentence_per_line
    DrsTextTool.COUNT -> R.string.drs__text_tools__desc_count
    DrsTextTool.DELETE_LINE -> R.string.drs__text_tools__desc_delete_line
    DrsTextTool.DELETE_TO_LINE_START -> R.string.drs__text_tools__desc_delete_to_line_start
    DrsTextTool.DELETE_TO_LINE_END -> R.string.drs__text_tools__desc_delete_to_line_end
    DrsTextTool.INSERT_RLM -> R.string.drs__text_tools__desc_insert_rlm
    DrsTextTool.INSERT_LRM -> R.string.drs__text_tools__desc_insert_lrm
    DrsTextTool.INSERT_ZWJ -> R.string.drs__text_tools__desc_insert_zwj
    DrsTextTool.INSERT_ZWNJ -> R.string.drs__text_tools__desc_insert_zwnj
}

/**
 * DRS v2.2.0 — الجولة الثامنة «البلاطة الذكية»: the technical text tools
 * panel, rebuilt around its TILES. Shown when the keyboard UI mode is
 * [ImeUiMode.TEXT_TOOLS]. Every tile dispatches a real key event through
 * the input pipeline; transformations run inside the editor's batch-edit
 * against the actual input connection of the focused field.
 *
 * The modern anatomy, on top of the classic sections:
 *  - شريط الرقائق (the chips bar): الكل · المفضلة · الأكثر استخدامًا ·
 *    then every catalogue section — one tap filters the tiles.
 *  - صف «مقترحات لهذا النص»: the deterministic tiles' brain
 *    ([DrsTileContextAdvisor]) ranks the tools that make sense for the
 *    text before the cursor — closed reasons only, honest silence when
 *    there is no signal, gated by the textToolsSmartContext switch.
 *  - المفضلة: long-press any tile to pin it (bounded by
 *    [DrsTileFavorites.MAX_FAVORITES] with an honest refusal toast on a
 *    full board); the favorites lead the «الكل» view in pin order.
 *  - الأكثر استخدامًا: per-tile LOCAL counters (codes only, never text)
 *    through [DrsPanelUsageStore] — nothing at all in incognito, on the
 *    doctrine every other panel follows.
 *  - شبكة ثنائية الأعمدة: the «الكل» browse view renders compact
 *    two-column tiles; every focused filter keeps the rich rows with
 *    descriptions.
 *
 * Transformation tools act on the current selection, or on the whole field
 * when nothing is selected. Nothing is ever stored, logged or transmitted -
 * the host app's own undo remains the way back.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DrsTextToolsPanel(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val keyboardManager by context.keyboardManager()
    val editorInstance by context.editorInstance()
    val prefs by DrsPreferenceStore

    val recentsEnabled by prefs.panels.panelRecents.collectAsState()
    val contextEnabled by prefs.panels.textToolsSmartContext.collectAsState()

    var favorites by remember { mutableStateOf(DrsTileFavoritesStore.load(context)) }
    var recents by remember { mutableStateOf(DrsPanelUsageStore.load(context, USAGE_PANEL_TEXT_TOOLS)) }
    var filter by remember { mutableStateOf<TileFilter>(TileFilter.All) }
    var commitStamp by remember { mutableIntStateOf(0) }

    fun dispatch(code: Int) {
        keyboardManager.inputEventDispatcher.sendDownUp(
            TextKeyData(type = KeyType.FUNCTION, code = code, label = "drs_text_tool"),
        )
        DrsAdaptationEngine.recordTechToolUse()
        // DRS v2.2.0: the per-tile LOCAL counter (the tile's code only —
        // never text). The incognito mode records nothing, exactly like
        // the harakat/symbols/letters panels' counters.
        if (!keyboardManager.activeState.isIncognitoMode) {
            DrsPanelUsageStore.record(context, USAGE_PANEL_TEXT_TOOLS, code.toString())
            recents = DrsPanelUsageStore.load(context, USAGE_PANEL_TEXT_TOOLS)
        }
        commitStamp++
    }

    fun toggleFavorite(code: Int) {
        when (val result = DrsTileFavorites.toggled(favorites, code)) {
            is DrsTileFavorites.ToggleResult.Ok -> {
                favorites = result.favorites
                DrsTileFavoritesStore.save(context, result.favorites)
            }
            DrsTileFavorites.ToggleResult.Full -> Toast.makeText(
                context,
                context.getString(R.string.drs__text_tools__favorites_full),
                Toast.LENGTH_SHORT,
            ).show()
        }
    }

    // The contextual advisory picks — recomputed after every commit the
    // same way the harakat board's advisor refreshes.
    val contextPicks = remember(contextEnabled, commitStamp) {
        if (contextEnabled) {
            val before = editorInstance.run { activeContent.getTextBeforeCursor(48) }
            DrsTileContextAdvisor.advise(before)
        } else {
            emptyList()
        }
    }

    val systemSpec = DrsSystems.specOfName(DrsStore.state.value.userPath)
    val accent = if (isSystemInDarkTheme()) systemSpec.accentNight else systemSpec.accent
    val favoriteSet = remember(favorites) { favorites.toHashSet() }

    SnyggColumn(
        modifier = modifier
            .fillMaxWidth()
            .height(DrsImeSizing.imeUiHeight()),
    ) {
        // ---------------- header (unchanged anatomy) ----------------
        SnyggRow(
            DrsImeUi.ClipboardHeader.elementName,
            modifier = Modifier
                .fillMaxWidth()
                .height(DrsImeSizing.smartbarHeight),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val sizeModifier = Modifier
                .sizeIn(maxHeight = DrsImeSizing.smartbarHeight)
                .aspectRatio(1f)
            SnyggIconButton(
                elementName = DrsImeUi.ClipboardHeaderButton.elementName,
                onClick = { keyboardManager.activeState.imeUiMode = ImeUiMode.TEXT },
                modifier = sizeModifier,
            ) {
                SnyggIcon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    // DRS a11y/i18n/ux (r0-I): the close/back-to-keyboard button
                    // is icon-only (this header's forward arrow closes the panel).
                    contentDescription = stringRes(R.string.action__navigate_back),
                )
            }
            SnyggText(
                elementName = DrsImeUi.ClipboardHeaderText.elementName,
                modifier = Modifier.weight(1f),
                text = stringRes(R.string.drs__text_tools__header_title),
            )
            SnyggIconButton(
                elementName = DrsImeUi.ClipboardHeaderButton.elementName,
                onClick = { dispatch(KeyCode.UNDO) },
                modifier = sizeModifier,
            ) {
                SnyggIcon(
                    imageVector = Icons.AutoMirrored.Filled.Undo,
                    // DRS a11y/i18n/ux (r0-I): the header undo button is icon-only.
                    contentDescription = stringRes(R.string.quick_action__undo),
                )
            }
            SnyggIconButton(
                elementName = DrsImeUi.ClipboardHeaderButton.elementName,
                onClick = { dispatch(KeyCode.REDO) },
                modifier = sizeModifier,
            ) {
                SnyggIcon(
                    imageVector = Icons.AutoMirrored.Filled.Redo,
                    // DRS a11y/i18n/ux (r0-I): the header redo button is icon-only.
                    contentDescription = stringRes(R.string.quick_action__redo),
                )
            }
        }

        // ---------------- شريط الرقائق (the chips bar) ----------------
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FilterChip(
                label = stringRes(R.string.drs__text_tools__chip_all),
                selected = filter is TileFilter.All,
                accent = accent,
                onClick = { filter = TileFilter.All },
            )
            FilterChip(
                label = stringRes(R.string.drs__text_tools__section_favorites),
                selected = filter is TileFilter.Favorites,
                accent = accent,
                onClick = { filter = TileFilter.Favorites },
            )
            FilterChip(
                label = stringRes(R.string.drs__text_tools__section_recents),
                selected = filter is TileFilter.Recents,
                accent = accent,
                onClick = { filter = TileFilter.Recents },
            )
            PANEL_SECTIONS.forEachIndexed { index, section ->
                FilterChip(
                    label = stringRes(section.titleRes),
                    selected = (filter as? TileFilter.Section)?.index == index,
                    accent = accent,
                    onClick = { filter = TileFilter.Section(index) },
                )
            }
        }

        // ---------------- body ----------------
        SnyggBox(
            DrsImeUi.ClipboardContent.elementName,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
            ) {
                when (val current = filter) {
                    TileFilter.All -> {
                        // 1) مقترحات لهذا النص — the deterministic advisory row.
                        if (contextPicks.isNotEmpty()) {
                            SectionSubheader(R.string.drs__text_tools__section_context)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState())
                                    .padding(horizontal = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                contextPicks.forEach { tool ->
                                    FilterChip(
                                        label = stringRes(textToolTitleRes(tool)),
                                        selected = false,
                                        accent = accent,
                                        onClick = { dispatch(tool.code) },
                                    )
                                }
                            }
                        }
                        // 2) المفضلة — rich rows leading the browse view.
                        if (favorites.isNotEmpty()) {
                            SectionSubheader(R.string.drs__text_tools__section_favorites)
                            favorites.mapNotNull { ITEMS_BY_CODE[it] }.forEach { item ->
                                ToolRow(
                                    item = item,
                                    isFavorite = true,
                                    onApply = ::dispatch,
                                    onToggleFavorite = ::toggleFavorite,
                                )
                            }
                        }
                        // 3) كل الأقسام — the compact two-column grid.
                        PANEL_SECTIONS.forEach { section ->
                            SectionSubheader(section.titleRes)
                            section.items.chunked(2).forEach { rowItems ->
                                SnyggRow(modifier = Modifier.fillMaxWidth()) {
                                    rowItems.forEach { item ->
                                        ToolTile(
                                            item = item,
                                            isFavorite = item.code in favoriteSet,
                                            onApply = ::dispatch,
                                            onToggleFavorite = ::toggleFavorite,
                                            modifier = Modifier.weight(1f),
                                        )
                                    }
                                    if (rowItems.size == 1) {
                                        Spacer(modifier = Modifier.weight(1f))
                                    }
                                }
                            }
                        }
                    }
                    TileFilter.Favorites -> {
                        if (favorites.isEmpty()) {
                            HintText(stringRes(R.string.drs__text_tools__favorites_hint))
                        } else {
                            favorites.mapNotNull { ITEMS_BY_CODE[it] }.forEach { item ->
                                ToolRow(
                                    item = item,
                                    isFavorite = true,
                                    onApply = ::dispatch,
                                    onToggleFavorite = ::toggleFavorite,
                                )
                            }
                        }
                    }
                    TileFilter.Recents -> {
                        // The recents engine works on the CODE catalogue; the
                        // panelRecents switch owns the whole row on/off.
                        val top = if (recentsEnabled) {
                            PanelUsageTracker.topRecents(
                                recents,
                                ALL_ITEMS.map { it.code.toString() },
                            ).mapNotNull { it.toIntOrNull() }
                        } else {
                            emptyList()
                        }
                        if (top.isEmpty()) {
                            HintText(stringRes(R.string.drs__text_tools__recents_hint))
                        } else {
                            top.mapNotNull { ITEMS_BY_CODE[it] }.forEach { item ->
                                ToolRow(
                                    item = item,
                                    isFavorite = item.code in favoriteSet,
                                    onApply = ::dispatch,
                                    onToggleFavorite = ::toggleFavorite,
                                )
                            }
                        }
                    }
                    is TileFilter.Section -> {
                        val section = PANEL_SECTIONS[current.index]
                        SectionSubheader(section.titleRes)
                        section.items.forEach { item ->
                            ToolRow(
                                item = item,
                                isFavorite = item.code in favoriteSet,
                                onApply = ::dispatch,
                                onToggleFavorite = ::toggleFavorite,
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
            }
        }
    }
}

/** A catalogue-section title inside the scrollable body. */
@Composable
private fun SectionSubheader(titleRes: Int) {
    SnyggText(
        elementName = DrsImeUi.ClipboardSubheader.elementName,
        modifier = Modifier.padding(start = 16.dp, top = 14.dp, bottom = 2.dp),
        text = stringRes(titleRes),
    )
}

/** The honest empty-state hint of the favorites and recents filters. */
@Composable
private fun HintText(text: String) {
    SnyggText(
        elementName = DrsImeUi.ClipboardItemDescription.elementName,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
        text = text,
    )
}

/**
 * One chip of the tiles' filter bar — the shared accent-pill pattern the
 * panel switcher chips established (v1.16.0); a tap filters, the selected
 * chip carries the stronger accent wash.
 */
@Composable
private fun FilterChip(
    label: String,
    selected: Boolean,
    accent: Color,
    onClick: () -> Unit,
) {
    SnyggText(
        elementName = DrsImeUi.ClipboardSubheader.elementName,
        modifier = Modifier
            .clip(CircleShape)
            .background(if (selected) accent.copy(alpha = 0.22f) else accent.copy(alpha = 0.06f))
            .rippleClickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 6.dp),
        text = label,
    )
}

/**
 * The compact two-column grid tile: the icon plus a one-line label. A tap
 * dispatches the tool; a LONG-PRESS pins or unpins it (the favorites).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ToolTile(
    item: DrsTextToolsPanelItem,
    isFavorite: Boolean,
    onApply: (Int) -> Unit,
    onToggleFavorite: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    SnyggBox(
        elementName = DrsImeUi.ClipboardItem.elementName,
        modifier = modifier
            .padding(horizontal = 3.dp, vertical = 2.dp),
        clickAndSemanticsModifier = Modifier.combinedClickable(
            onClick = { onApply(item.code) },
            onLongClick = { onToggleFavorite(item.code) },
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box {
                SnyggIconButton(
                    elementName = DrsImeUi.ClipboardHeaderButton.elementName,
                    onClick = { onApply(item.code) },
                    modifier = Modifier
                        .sizeIn(minWidth = 40.dp)
                        .height(40.dp),
                ) {
                    SnyggIcon(
                        imageVector = item.icon,
                        // DRS a11y/i18n/ux (r0-I): the tile's icon button is
                        // icon-only; it announces the tool it dispatches.
                        contentDescription = stringRes(item.labelRes),
                    )
                }
                if (isFavorite) {
                    SnyggIcon(
                        imageVector = Icons.Default.Star,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .size(12.dp),
                        contentDescription = null,
                    )
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            SnyggText(text = stringRes(item.labelRes))
        }
    }
}

/**
 * One rich row of the panel: the icon, the label and its description.
 * A tap dispatches the tool; a LONG-PRESS pins or unpins it (the
 * favorites) — and a starred tile carries its badge honestly.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ToolRow(
    item: DrsTextToolsPanelItem,
    isFavorite: Boolean,
    onApply: (Int) -> Unit,
    onToggleFavorite: (Int) -> Unit,
) {
    SnyggBox(
        elementName = DrsImeUi.ClipboardItem.elementName,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 2.dp),
        clickAndSemanticsModifier = Modifier.combinedClickable(
            onClick = { onApply(item.code) },
            onLongClick = { onToggleFavorite(item.code) },
        ),
    ) {
        SnyggRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SnyggIconButton(
                elementName = DrsImeUi.ClipboardHeaderButton.elementName,
                onClick = { onApply(item.code) },
                modifier = Modifier
                    .sizeIn(minWidth = 40.dp)
                    .height(40.dp),
            ) {
                SnyggIcon(
                    imageVector = item.icon,
                    // DRS a11y/i18n/ux (r0-I): the row's icon button is icon-only;
                    // it announces the tool it dispatches (the row text carries the
                    // description separately).
                    contentDescription = stringRes(item.labelRes),
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                SnyggText(
                    text = stringRes(item.labelRes),
                )
                SnyggText(
                    elementName = DrsImeUi.ClipboardItemDescription.elementName,
                    text = stringRes(item.descRes),
                )
            }
            if (isFavorite) {
                SnyggIcon(
                    imageVector = Icons.Default.Star,
                    modifier = Modifier.padding(start = 8.dp),
                    contentDescription = stringRes(R.string.drs__text_tools__section_favorites),
                )
            }
        }
    }
}
