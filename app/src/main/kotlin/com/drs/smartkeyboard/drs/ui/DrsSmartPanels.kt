/*
 * Copyright (C) 2025-2026 The DRS Smart Keyboard Project
 */

package com.drs.smartkeyboard.drs.ui

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Backspace
import androidx.compose.material.icons.filled.FormatClear
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.drs.smartkeyboard.R
import com.drs.smartkeyboard.app.DrsPreferenceStore
import com.drs.smartkeyboard.drs.DrsHarakat
import com.drs.smartkeyboard.drs.DrsKeyboardHarakat
import com.drs.smartkeyboard.drs.DrsKeyboardHarakatKey
import com.drs.smartkeyboard.drs.DrsPanelOrder
import com.drs.smartkeyboard.drs.DrsStore
import com.drs.smartkeyboard.drs.HarakaInsertMode
import com.drs.smartkeyboard.drs.HarakatSmartInsert
import com.drs.smartkeyboard.drs.PanelUsageTracker
import com.drs.smartkeyboard.drs.DrsSystems
import com.drs.smartkeyboard.drs.DrsRuntimeState
import com.drs.smartkeyboard.drs.SymbolSmartSuggestor
import com.drs.smartkeyboard.ime.ImeUiMode
import com.drs.smartkeyboard.ime.editor.OperationUnit
import com.drs.smartkeyboard.ime.input.LocalInputFeedbackController
import com.drs.smartkeyboard.ime.keyboard.DrsImeSizing
import com.drs.smartkeyboard.ime.text.key.KeyCode
import com.drs.smartkeyboard.ime.text.key.KeyType
import com.drs.smartkeyboard.ime.text.keyboard.TextKeyData
import com.drs.smartkeyboard.ime.theme.DrsImeUi
import com.drs.smartkeyboard.ime.window.LocalWindowController
import com.drs.smartkeyboard.keyboardManager
import com.drs.smartkeyboard.editorInstance
import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import org.drs.jetpref.datastore.model.collectAsState
import org.drs.lib.compose.rippleClickable
import org.drs.lib.compose.stringRes
import org.drs.lib.snygg.ui.SnyggBox
import org.drs.lib.snygg.ui.SnyggColumn
import org.drs.lib.snygg.ui.SnyggIcon
import org.drs.lib.snygg.ui.SnyggIconButton
import org.drs.lib.snygg.ui.SnyggRow
import org.drs.lib.snygg.ui.SnyggText
import org.drs.lib.snygg.SnyggSelector

/**
 * DRS v1.15.0/v1.16.0 — الوحات الذكية الثلاث.
 *
 *  - لوحة الحركات (DrsDiacriticsPanel): v1.16.0 أُعيد بناؤها كلوحة
 *    مفاتيح كاملة «مشابهة تمامًا للوحة الحروف أو الأرقام» — أربعة صفوف
 *    من مفاتيح العنصر المرئي نفسه الذي ترسم به المفاتيح الحقيقية، بارتفاع
 *    الصف والهوامش نفسهما: التنوينات، الحركات الأساسية، الشدة والتطويل
 *    مع مفتاحي الحذف والمسافة الحقيقيين، والتشكيل المزدوج — مع الدمج
 *    الذكي وتكرار الحذف بالضغط المطول.
 *  - لوحة الرموز الذكية (DrsSmartSymbolsPanel): صف اقتراحات سياقية يقرأ
 *    النص قبل المؤشر، ثم فئات الرموز الكاملة، وصف الأكثر استخدامًا.
 *  - لوحة الحروف الموسعة (DrsArabicLettersPanel): همزات ومشتقات وحروف
 *    الفارسية/الأردية/الكردية مع صف الأكثر استخدامًا.
 *
 * v1.16.0 — «أعد ترتيب وتطوير جميع الوحات بنظام مرتب وذكي»: رقاقات
 * المبدّل في اللوحات الثلاث تُرتَّب ذكيًا من عدّادات فتح اللوحات المحلية
 * (اللوحة الحالية أولًا ثم الأكثر فتحًا)، وكل لوحة تسجّل فتحها محليًا فقط
 * (الوضع الخفي لا يسجّل شيئًا إطلاقًا).
 */

/** The persisted panel-usage namespaces (per panel, local only). */
private const val USAGE_PANEL_HARAKAT = "harakat"
private const val USAGE_PANEL_SYMBOLS = "symbols"
private const val USAGE_PANEL_LETTERS = "letters"

/**
 * Thin SharedPreferences-backed store for the panels' most-used counters.
 * The growth/trim/top logic itself lives in the pure [PanelUsageTracker]
 * so it stays unit-testable; this object only loads and saves the map.
 * Local only — counts of which TILES were pressed, never any text.
 */
object DrsPanelUsageStore {
    private const val PREFS_NAME = "drs_panel_usage"
    private val json = Json { ignoreUnknownKeys = true }
    private val serializer = MapSerializer(String.serializer(), Int.serializer())

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun load(context: Context, panel: String): Map<String, Int> {
        return runCatching {
            json.decodeFromString(serializer, prefs(context).getString(panel, "{}") ?: "{}")
        }.getOrDefault(emptyMap())
    }

    fun record(context: Context, panel: String, key: String) {
        val updated = PanelUsageTracker.record(load(context, panel), key)
        prefs(context).edit().putString(panel, json.encodeToString(serializer, updated)).apply()
    }
}

/** The smart symbols panel catalogue (pure data, panel order). */
object DrsSymbolsCatalog {
    val MATH: List<String> = listOf(
        "+", "−", "×", "÷", "=", "≠", "≈", "<", ">", "≤", "≥",
        "±", "√", "∞", "π", "Σ", "∫", "°", "%",
    )
    val CURRENCY: List<String> = listOf(
        "$", "€", "£", "¥", "₽", "₹", "₺", "﷼", "ر.س", "د.إ",
    )
    val ARROWS: List<String> = listOf("←", "→", "↑", "↓", "↔", "⇒", "⇐", "➤")
    val PUNCTUATION: List<String> = listOf(
        "،", "؛", "؟", "…", "—", "–", "«", "»",
        "\"", "'", "•", "@", "#", "&", "*", "~", "^", "_", "|", "/", "\\",
    )
    val BRACKETS: List<String> = listOf("(", ")", "[", "]", "{", "}", "<", ">")
}

/** The extended Arabic letters catalogue (hamza variants + فارسية/أردية/كردية). */
object DrsArabicLettersCatalog {
    val LETTERS: List<String> = listOf(
        "ء", "أ", "إ", "آ", "ٱ", "ؤ", "ئ", "ة", "ى",
        "پ", "چ", "ژ", "گ", "ڤ", "ک", "ی", "ۋ", "ڭ", "ۀ", "ھ",
    )
}

/** The shared panel-switcher chips row, smart-ordered by local open counts. */
@Composable
private fun PanelSwitcherChips(current: ImeUiMode, keyboardManager: com.drs.smartkeyboard.ime.keyboard.KeyboardManager, accent: androidx.compose.ui.graphics.Color) {
    val context = LocalContext.current
    val prefs by DrsPreferenceStore
    val smartOrder by prefs.panels.panelSmartOrder.collectAsState()
    val catalogue = listOf(
        ImeUiMode.DIACRITICS to R.string.panel__switcher_harakat,
        ImeUiMode.SMART_SYMBOLS to R.string.panel__switcher_symbols,
        ImeUiMode.ARABIC_LETTERS to R.string.panel__switcher_letters,
    )
    val options = remember(current, smartOrder) {
        if (!smartOrder) {
            catalogue
        } else {
            val usage = DrsPanelUsageStore.load(context, DrsPanelOrder.USAGE_NAMESPACE)
            DrsPanelOrder.smartSwitcher(current, usage, catalogue.map { it.first })
                .map { mode -> mode to catalogue.first { it.first == mode }.second }
        }
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        options.forEach { (mode, labelRes) ->
            val selected = mode == current
            SnyggText(
                elementName = DrsImeUi.ClipboardSubheader.elementName,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(if (selected) accent.copy(alpha = 0.22f) else accent.copy(alpha = 0.06f))
                    .rippleClickable { keyboardManager.activeState.imeUiMode = mode }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                text = stringRes(labelRes),
            )
        }
    }
}

/**
 * Records one open of [mode] into the LOCAL panel-open counters (the
 * smart ordering input). Counts only — the incognito mode records
 * nothing at all, exactly like every other DRS usage counter.
 */
@Composable
private fun RecordPanelOpen(mode: ImeUiMode) {
    val context = LocalContext.current
    val keyboardManager by context.keyboardManager()
    LaunchedEffect(mode) {
        if (!keyboardManager.activeState.isIncognitoMode) {
            DrsPanelUsageStore.record(context, DrsPanelOrder.USAGE_NAMESPACE, mode.name)
        }
    }
}

/** The shared header of the three smart panels (back + title + actions). */
@Composable
private fun SmartPanelHeader(
    titleRes: Int,
    keyboardManager: com.drs.smartkeyboard.ime.keyboard.KeyboardManager,
    trailing: @Composable () -> Unit = {},
) {
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
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                // DRS a11y/i18n/ux (r0-I): the header back button is icon-only.
                contentDescription = stringRes(R.string.action__navigate_back),
            )
        }
        SnyggText(
            elementName = DrsImeUi.ClipboardHeaderText.elementName,
            modifier = Modifier.weight(1f),
            text = stringRes(titleRes),
        )
        trailing()
    }
}

/** One tile of the char grids: the big glyph plus its small local name. */
@Composable
private fun SmartTile(
    glyph: String,
    name: String,
    onApply: () -> Unit,
) {
    SnyggBox(
        elementName = DrsImeUi.ClipboardItem.elementName,
        modifier = Modifier
            .aspectRatio(1.4f)
            .padding(2.dp),
        clickAndSemanticsModifier = Modifier.rippleClickable { onApply() },
    ) {
        Column(
            modifier = Modifier.padding(4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            SnyggText(text = glyph)
            SnyggText(
                elementName = DrsImeUi.ClipboardItemDescription.elementName,
                text = name,
            )
        }
    }
}

/** A full-span subheader inside the LazyVerticalGrid of a panel. */
@Composable
private fun gridSubheader(text: String) {
    SnyggText(
        elementName = DrsImeUi.ClipboardSubheader.elementName,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, top = 10.dp, bottom = 2.dp),
        text = text,
    )
}

/**
 * لوحة الحركات — v1.16.0: the harakat KEYBOARD panel, «مشابه تمامًا
 * للوحة الحروف أو الأرقام»: four rows of the very same themed key
 * element the real keys render through (DrsImeUi.Key with its pressed
 * selector), at the real row height and margins. Smart insert (a mark
 * over a mark replaces it, shadda+haraka appends), the shadda combos
 * commit two characters, the real delete key repeats while held, the
 * real space key, and the recents strip leads with what this user
 * actually uses.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DrsDiacriticsPanel(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val keyboardManager by context.keyboardManager()
    val editorInstance by context.editorInstance()
    val prefs by DrsPreferenceStore
    val feedback = LocalInputFeedbackController.current

    val smartReplace by prefs.panels.harakatSmartReplace.collectAsState()
    val recentsEnabled by prefs.panels.panelRecents.collectAsState()
    var recents by remember { mutableStateOf(DrsPanelUsageStore.load(context, USAGE_PANEL_HARAKAT)) }

    RecordPanelOpen(ImeUiMode.DIACRITICS)

    fun recordUse(key: String) {
        if (keyboardManager.activeState.isIncognitoMode) return
        DrsPanelUsageStore.record(context, USAGE_PANEL_HARAKAT, key)
        recents = DrsPanelUsageStore.load(context, USAGE_PANEL_HARAKAT)
    }

    fun commitText(text: String, usageKey: String?) {
        editorInstance.commitText(text)
        if (usageKey != null) recordUse(usageKey)
    }

    fun insertHaraka(haraka: Char) {
        val glyph = haraka.toString()
        val replaced = if (keyboardManager.activeState.isSelectionMode) {
            HarakaInsertMode.APPEND
        } else {
            val previous = editorInstance.run { activeContent.getTextBeforeCursor(1) }.lastOrNull()
            HarakatSmartInsert.decide(previous, haraka, smartReplace)
        }
        if (replaced == HarakaInsertMode.REPLACE_PREVIOUS) {
            editorInstance.deleteBackwards(OperationUnit.CHARACTERS)
        }
        commitText(glyph, glyph)
    }

    fun applyKey(key: DrsKeyboardHarakatKey) {
        when (key) {
            is DrsKeyboardHarakatKey.Haraka -> insertHaraka(key.char)
            is DrsKeyboardHarakatKey.Combo -> commitText(key.text, key.text)
            DrsKeyboardHarakatKey.Tatweel -> commitText("${DrsHarakat.TATWEEL}", null)
            DrsKeyboardHarakatKey.Space ->
                keyboardManager.inputEventDispatcher.sendDownUp(TextKeyData.SPACE)
            DrsKeyboardHarakatKey.Delete ->
                keyboardManager.inputEventDispatcher.sendDownUp(TextKeyData.DELETE)
        }
    }

    fun dispatchRemoveDiacritics() {
        keyboardManager.inputEventDispatcher.sendDownUp(
            TextKeyData(type = KeyType.FUNCTION, code = KeyCode.TEXT_TOOL_REMOVE_DIACRITICS, label = "drs_text_tool"),
        )
    }

    val systemSpec = DrsSystems.specOfName(DrsStore.state.value.userPath)
    val accent = if (isSystemInDarkTheme()) systemSpec.accentNight else systemSpec.accent
    val recentsRow = remember(recents, recentsEnabled) {
        if (recentsEnabled) {
            PanelUsageTracker.topRecents(recents, DrsHarakat.GRID.map { it.toString() })
        } else {
            emptyList()
        }
    }

    val windowController = LocalWindowController.current
    val windowSpec by windowController.activeWindowSpec.collectAsState()
    val rowHeight = DrsImeSizing.keyboardRowBaseHeight

    SnyggColumn(
        modifier = modifier.fillMaxWidth(),
    ) {
        SmartPanelHeader(
            titleRes = R.string.panel__harakat__title,
            keyboardManager = keyboardManager,
        ) {
            val sizeModifier = Modifier
                .sizeIn(maxHeight = DrsImeSizing.smartbarHeight)
                .aspectRatio(1f)
            SnyggIconButton(
                elementName = DrsImeUi.ClipboardHeaderButton.elementName,
                onClick = { dispatchRemoveDiacritics() },
                modifier = sizeModifier,
            ) {
                SnyggIcon(
                    imageVector = Icons.Default.FormatClear,
                    // DRS a11y/i18n/ux (r0-I): the header action is icon-only.
                    contentDescription = stringRes(R.string.drs__text_tools__tool_remove_diacritics),
                )
            }
        }
        PanelSwitcherChips(ImeUiMode.DIACRITICS, keyboardManager, accent)

        // The recents strip — the smart layer above the keyboard rows.
        if (recentsRow.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(DrsImeSizing.smartbarHeight)
                    .padding(horizontal = windowSpec.keyMarginH),
                horizontalArrangement = Arrangement.spacedBy(windowSpec.keyMarginH * 2),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                recentsRow.forEach { char ->
                    SnyggText(
                        elementName = DrsImeUi.ClipboardSubheader.elementName,
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(accent.copy(alpha = 0.10f))
                            .rippleClickable { insertHaraka(char.first()) }
                            .padding(horizontal = 12.dp, vertical = 4.dp),
                        text = DrsKeyboardHarakat.label(
                            DrsKeyboardHarakatKey.Haraka(char.first()),
                        ),
                    )
                }
            }
        }

        // The keyboard itself: four rows x four keys, the exact anatomy
        // of the numbers panel, rendered through DrsImeUi.Key.
        DrsKeyboardHarakat.ROWS.forEach { rowKeys ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(rowHeight)
                    .padding(horizontal = windowSpec.keyMarginH),
                horizontalArrangement = Arrangement.spacedBy(windowSpec.keyMarginH * 2),
            ) {
                rowKeys.forEach { key ->
                    HarakatKeyboardKey(
                        key = key,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .padding(vertical = windowSpec.keyMarginV),
                        onPress = {
                            feedback.keyPress()
                            applyKey(key)
                        },
                        holdRepeat = key is DrsKeyboardHarakatKey.Delete,
                    )
                }
            }
        }
        Spacer(Modifier.height(4.dp))
    }
}

/**
 * One keyboard key of the harakat panel — the SAME themed element the
 * real keys render through (DrsImeUi.Key with its pressed selector), so
 * the panel looks exactly like the letters/numbers panels. [holdRepeat]
 * turns the key into the hold-to-repeat delete key.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HarakatKeyboardKey(
    key: DrsKeyboardHarakatKey,
    modifier: Modifier = Modifier,
    onPress: () -> Unit,
    holdRepeat: Boolean = false,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()

    // DRS v1.21.0: the hold-to-repeat key fires its first press the moment
    // the finger lands — a quick tap deletes immediately (it used to need a
    // full 400 ms hold before the first delete, leaving quick taps inert).
    LaunchedEffect(pressed, holdRepeat) {
        if (pressed && holdRepeat) {
            onPress()
            delay(400)
            while (true) {
                onPress()
                delay(60)
            }
        }
    }

    SnyggBox(
        elementName = DrsImeUi.Key.elementName,
        selector = if (pressed) SnyggSelector.PRESSED else SnyggSelector.NONE,
        modifier = modifier,
        clickAndSemanticsModifier = Modifier.combinedClickable(
            interactionSource = interaction,
            indication = null,
            onClick = { if (!holdRepeat) onPress() },
        ),
    ) {
        if (key is DrsKeyboardHarakatKey.Delete) {
            SnyggIcon(
                modifier = Modifier.align(Alignment.Center),
                imageVector = Icons.AutoMirrored.Outlined.Backspace,
                // DRS a11y/i18n/ux (r0-I): the delete key of the harakat
                // keyboard panel is icon-only (was null).
                contentDescription = stringRes(R.string.key__a11y_delete),
            )
        } else {
            SnyggText(
                modifier = Modifier.align(Alignment.Center),
                text = DrsKeyboardHarakat.label(key),
            )
        }
    }
}

/**
 * لوحة الرموز الذكية — the context-aware smart symbols panel. The
 * suggestions row recomputes from the text before the cursor after every
 * commit made from inside the panel, then the full catalogue follows by
 * category with the user's recents leading.
 */
@Composable
fun DrsSmartSymbolsPanel(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val keyboardManager by context.keyboardManager()
    val editorInstance by context.editorInstance()
    val prefs by DrsPreferenceStore

    val suggestionsEnabled by prefs.panels.symbolSmartSuggestions.collectAsState()
    val recentsEnabled by prefs.panels.panelRecents.collectAsState()
    var recents by remember { mutableStateOf(DrsPanelUsageStore.load(context, USAGE_PANEL_SYMBOLS)) }
    var commitStamp by remember { mutableIntStateOf(0) }

    RecordPanelOpen(ImeUiMode.SMART_SYMBOLS)

    fun commitText(text: String) {
        editorInstance.commitText(text)
        if (!keyboardManager.activeState.isIncognitoMode) {
            DrsPanelUsageStore.record(context, USAGE_PANEL_SYMBOLS, text)
            recents = DrsPanelUsageStore.load(context, USAGE_PANEL_SYMBOLS)
        }
        commitStamp++
    }

    val suggestions = remember(suggestionsEnabled, commitStamp) {
        if (suggestionsEnabled) {
            SymbolSmartSuggestor.suggest(editorInstance.run { activeContent.getTextBeforeCursor(8) })
        } else {
            emptyList()
        }
    }
    val recentsRow = remember(recents, recentsEnabled) {
        if (recentsEnabled) {
            PanelUsageTracker.topRecents(recents, DrsSymbolsCatalog.MATH + DrsSymbolsCatalog.CURRENCY +
                DrsSymbolsCatalog.ARROWS + DrsSymbolsCatalog.PUNCTUATION + DrsSymbolsCatalog.BRACKETS)
        } else {
            emptyList()
        }
    }

    val systemSpec = DrsSystems.specOfName(DrsStore.state.value.userPath)
    val accent = if (isSystemInDarkTheme()) systemSpec.accentNight else systemSpec.accent

    SnyggColumn(
        modifier = modifier
            .fillMaxWidth()
            .height(DrsImeSizing.imeUiHeight()),
    ) {
        SmartPanelHeader(R.string.panel__symbols__title, keyboardManager)
        PanelSwitcherChips(ImeUiMode.SMART_SYMBOLS, keyboardManager, accent)

        SnyggBox(DrsImeUi.ClipboardContent.elementName, modifier = Modifier.fillMaxWidth()) {
            LazyVerticalGrid(
                modifier = Modifier.fillMaxWidth(),
                columns = GridCells.Adaptive(DrsImeSizing.smartbarHeight * 1.35f),
            ) {
                if (suggestions.isNotEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        gridSubheader(stringRes(R.string.panel__symbols__suggestions_title))
                    }
                    items(suggestions, key = { "sugg_$it" }) { symbol ->
                        SmartTile(
                            glyph = symbol,
                            name = "",
                            onApply = { commitText(symbol) },
                        )
                    }
                }
                if (recentsRow.isNotEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        gridSubheader(stringRes(R.string.panel__recents_title))
                    }
                    items(recentsRow, key = { "recent_$it" }) { key ->
                        SmartTile(
                            glyph = key,
                            name = "",
                            onApply = { commitText(key) },
                        )
                    }
                }
                item(span = { GridItemSpan(maxLineSpan) }) {
                    gridSubheader(stringRes(R.string.panel__symbols__math_title))
                }
                items(DrsSymbolsCatalog.MATH, key = { "math_$it" }) { symbol ->
                    SmartTile(glyph = symbol, name = "", onApply = { commitText(symbol) })
                }
                item(span = { GridItemSpan(maxLineSpan) }) {
                    gridSubheader(stringRes(R.string.panel__symbols__currency_title))
                }
                items(DrsSymbolsCatalog.CURRENCY, key = { "cur_$it" }) { symbol ->
                    SmartTile(glyph = symbol, name = "", onApply = { commitText(symbol) })
                }
                item(span = { GridItemSpan(maxLineSpan) }) {
                    gridSubheader(stringRes(R.string.panel__symbols__arrows_title))
                }
                items(DrsSymbolsCatalog.ARROWS, key = { "arrow_$it" }) { symbol ->
                    SmartTile(glyph = symbol, name = "", onApply = { commitText(symbol) })
                }
                item(span = { GridItemSpan(maxLineSpan) }) {
                    gridSubheader(stringRes(R.string.panel__symbols__brackets_title))
                }
                items(DrsSymbolsCatalog.BRACKETS, key = { "brk_$it" }) { symbol ->
                    SmartTile(glyph = symbol, name = "", onApply = { commitText(symbol) })
                }
                item(span = { GridItemSpan(maxLineSpan) }) {
                    gridSubheader(stringRes(R.string.panel__symbols__punct_title))
                }
                items(DrsSymbolsCatalog.PUNCTUATION, key = { "pun_$it" }) { symbol ->
                    SmartTile(glyph = symbol, name = "", onApply = { commitText(symbol) })
                }
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Spacer(Modifier.height(12.dp))
                }
            }
        }
    }
}

/**
 * لوحة الحروف الموسعة — the extended Arabic letters panel: the hamza
 * variants and the Persian/Urdu/Kurdish letters the base layout has no
 * room for, with the user's most-used letters leading the grid.
 */
@Composable
fun DrsArabicLettersPanel(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val keyboardManager by context.keyboardManager()
    val editorInstance by context.editorInstance()
    val prefs by DrsPreferenceStore

    val recentsEnabled by prefs.panels.panelRecents.collectAsState()
    var recents by remember { mutableStateOf(DrsPanelUsageStore.load(context, USAGE_PANEL_LETTERS)) }

    RecordPanelOpen(ImeUiMode.ARABIC_LETTERS)

    fun commitText(text: String) {
        editorInstance.commitText(text)
        if (!keyboardManager.activeState.isIncognitoMode) {
            DrsPanelUsageStore.record(context, USAGE_PANEL_LETTERS, text)
            recents = DrsPanelUsageStore.load(context, USAGE_PANEL_LETTERS)
        }
    }

    val recentsRow = remember(recents, recentsEnabled) {
        if (recentsEnabled) PanelUsageTracker.topRecents(recents, DrsArabicLettersCatalog.LETTERS) else emptyList()
    }

    val systemSpec = DrsSystems.specOfName(DrsStore.state.value.userPath)
    val accent = if (isSystemInDarkTheme()) systemSpec.accentNight else systemSpec.accent

    SnyggColumn(
        modifier = modifier
            .fillMaxWidth()
            .height(DrsImeSizing.imeUiHeight()),
    ) {
        SmartPanelHeader(R.string.panel__letters__title, keyboardManager)
        PanelSwitcherChips(ImeUiMode.ARABIC_LETTERS, keyboardManager, accent)

        SnyggBox(DrsImeUi.ClipboardContent.elementName, modifier = Modifier.fillMaxWidth()) {
            LazyVerticalGrid(
                modifier = Modifier.fillMaxWidth(),
                columns = GridCells.Adaptive(DrsImeSizing.smartbarHeight * 1.35f),
            ) {
                if (recentsRow.isNotEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        gridSubheader(stringRes(R.string.panel__recents_title))
                    }
                    items(recentsRow, key = { "recent_$it" }) { key ->
                        SmartTile(glyph = key, name = "", onApply = { commitText(key) })
                    }
                }
                item(span = { GridItemSpan(maxLineSpan) }) {
                    gridSubheader(stringRes(R.string.panel__letters__grid_title))
                }
                items(DrsArabicLettersCatalog.LETTERS, key = { "letter_$it" }) { letter ->
                    SmartTile(glyph = letter, name = "", onApply = { commitText(letter) })
                }
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Spacer(Modifier.height(12.dp))
                }
            }
        }
    }
}
