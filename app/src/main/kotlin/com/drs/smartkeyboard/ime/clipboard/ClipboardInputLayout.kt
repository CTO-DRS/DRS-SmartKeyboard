/*
 * Copyright (C) 2021-2025 The DRS Smart Keyboard Project
 */

package com.drs.smartkeyboard.ime.clipboard

import android.content.ContentUris
import android.content.Intent
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.media.ThumbnailUtils
import android.provider.MediaStore
import android.util.Size
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.automirrored.outlined.Backspace
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.FilterListOff
import androidx.compose.material.icons.filled.FormatLineSpacing
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Notes
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.ToggleOff
import androidx.compose.material.icons.filled.ToggleOn
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.outlined.ContentPasteGo
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.AlternateEmail
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateSetOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.drs.smartkeyboard.R
import com.drs.smartkeyboard.app.DrsPreferenceStore
import com.drs.smartkeyboard.clipboardManager
import com.drs.smartkeyboard.ime.ImeUiMode
import com.drs.smartkeyboard.ime.clipboard.provider.ClipboardFileStorage
import com.drs.smartkeyboard.ime.clipboard.provider.ClipboardItem
import com.drs.smartkeyboard.ime.clipboard.provider.ItemType
import com.drs.smartkeyboard.ime.clipboard.ClipCodeDetector
import com.drs.smartkeyboard.ime.clipboard.ClipCodeLanguage
import com.drs.smartkeyboard.ime.clipboard.ClipEditorPopupPolicy
import com.drs.smartkeyboard.ime.clipboard.ClipEditorRoute
import com.drs.smartkeyboard.ime.clipboard.ClipHistorySection
import com.drs.smartkeyboard.ime.clipboard.ClipHistorySections
import com.drs.smartkeyboard.ime.clipboard.ClipHistorySort
import com.drs.smartkeyboard.ime.clipboard.ClipHistorySorter
import com.drs.smartkeyboard.ime.clipboard.ClipItemCategory
import com.drs.smartkeyboard.ime.clipboard.ClipItemCategoryDetector
import com.drs.smartkeyboard.ime.clipboard.ClipPanelSearchResults
import com.drs.smartkeyboard.ime.clipboard.ClipPanelResultCard
import com.drs.smartkeyboard.ime.clipboard.ClipResultPalette
import com.drs.smartkeyboard.ime.clipboard.ClipResultCard
import com.drs.smartkeyboard.ime.clipboard.ClipSearchResults
import com.drs.smartkeyboard.ime.keyboard.DrsImeSizing
import com.drs.smartkeyboard.ime.media.KeyboardLikeButton
import com.drs.smartkeyboard.ime.smartbar.AnimationDuration
import com.drs.smartkeyboard.ime.smartbar.VerticalEnterTransition
import com.drs.smartkeyboard.ime.smartbar.VerticalExitTransition
import com.drs.smartkeyboard.ime.text.keyboard.TextKeyData
import com.drs.smartkeyboard.ime.theme.DrsImeUi
import com.drs.smartkeyboard.keyboardManager
import com.drs.smartkeyboard.lib.observeAsTransformingState
import com.drs.smartkeyboard.lib.util.NetworkUtils
import org.drs.jetpref.datastore.model.collectAsState
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import org.drs.lib.android.AndroidKeyguardManager
import org.drs.lib.android.AndroidVersion
import org.drs.lib.android.showShortToastSync
import org.drs.lib.android.systemService
import org.drs.lib.compose.LocalLocalizedDateTimeFormatter
import org.drs.lib.compose.autoMirrorForRtl
import org.drs.lib.compose.drsHorizontalScroll
import org.drs.lib.compose.drsVerticalScroll
import org.drs.lib.compose.rippleClickable
import org.drs.lib.compose.stringRes
import org.drs.lib.snygg.SnyggQueryAttributes
import org.drs.lib.snygg.ui.SnyggBox
import org.drs.lib.snygg.ui.SnyggButton
import org.drs.lib.snygg.ui.SnyggChip
import org.drs.lib.snygg.ui.SnyggColumn
import org.drs.lib.snygg.ui.SnyggIcon
import org.drs.lib.snygg.ui.SnyggIconButton
import org.drs.lib.snygg.ui.SnyggRow
import org.drs.lib.snygg.ui.SnyggText
import org.drs.lib.snygg.ui.rememberSnyggThemeQuery

private val ItemWidth = 200.dp
private val DialogWidth = 240.dp
private val EditDialogWidth = 320.dp

const val CLIPBOARD_HISTORY_NUM_GRID_COLUMNS_AUTO: Int = 0

@Composable
fun ClipboardInputLayout(
    modifier: Modifier = Modifier,
) {
    val prefs by DrsPreferenceStore
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val clipboardManager by context.clipboardManager()
    val keyboardManager by context.keyboardManager()
    val androidKeyguardManager = remember { context.systemService(AndroidKeyguardManager::class) }

    val deviceLocked = androidKeyguardManager.let { it.isDeviceLocked || it.isKeyguardLocked }
    val historyEnabled by prefs.clipboard.historyEnabled.collectAsState()

    var isFilterRowShown by remember { mutableStateOf(false) }
    var isSearchRowShown by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    val activeFilterTypes = remember { mutableStateSetOf<ItemType>() }

    // DRS v1.12.0 — the complete smart clipboard system: the persisted
    // sort order, the editor defaults from the app settings, and the
    // toggles of the colored search-result cards and code detection.
    // DRS v1.14.0 — the comprehensive settings list: the edit surface,
    // the jump behavior, the badges and warnings, and the panel
    // organization toggles all flow from the app settings too.
    val historySortPref by prefs.clipboard.historySort.collectAsState()
    val editorLimitPref by prefs.clipboard.editorCharLimit.collectAsState()
    val searchResultCardsPref by prefs.clipboard.searchResultCards.collectAsState()
    val autoResultsPref by prefs.clipboard.autoResultsPanel.collectAsState()
    val codeDetectionPref by prefs.clipboard.codeDetection.collectAsState()
    val defaultFontPref by prefs.clipboard.editorFont.collectAsState()
    val defaultFontSizePref by prefs.clipboard.editorFontSize.collectAsState()
    val defaultMatchCasePref by prefs.clipboard.matchCaseByDefault.collectAsState()
    val editRoutePref by prefs.clipboard.editRoute.collectAsState()
    val jumpToLinePref by prefs.clipboard.jumpToLine.collectAsState()
    val jumpCenterPref by prefs.clipboard.jumpCenter.collectAsState()
    val followCardsPref by prefs.clipboard.followActiveCard.collectAsState()
    val largeTextWarningPref by prefs.clipboard.largeTextWarning.collectAsState()
    val codeBadgePref by prefs.clipboard.codeBadge.collectAsState()
    val codeAutoMonospacePref by prefs.clipboard.codeAutoMonospace.collectAsState()
    val calendarSectionsPref by prefs.clipboard.calendarSections.collectAsState()
    val categoryBadgesPref by prefs.clipboard.categoryBadges.collectAsState()
    val editorLimit = editorLimitPref.effectiveLimit()
    var resultsPanelShown by remember { mutableStateOf(autoResultsPref) }

    val unfilteredHistory by clipboardManager.historyFlow.collectAsState()
    val filteredHistory = remember(unfilteredHistory, activeFilterTypes.toSet(), searchQuery, historySortPref) {
        var items = unfilteredHistory.all
        if (activeFilterTypes.isNotEmpty()) {
            items = items.filter { activeFilterTypes.contains(it.type) }
        }
        if (searchQuery.isNotBlank()) {
            items = items.filter { it.text?.contains(searchQuery, ignoreCase = true) == true }
        }
        // DRS v1.12.0: the user-chosen sort order of the panel
        // (newest / oldest / longest / shortest) — pure and persisted.
        items = ClipHistorySorter.sort(items, historySortPref)
        ClipboardHistory(items)
    }

    val gridState = rememberLazyStaggeredGridState()
    var popupItem by remember(filteredHistory) { mutableStateOf<ClipboardItem?>(null) }
    var showClearAllHistory by remember { mutableStateOf(false) }
    // DRS v1.9.0: the integrated clipboard system — an in-panel text editor
    // and a save-as-file dialog with a user-named file.
    var editingItem by remember { mutableStateOf<ClipboardItem?>(null) }
    var editingText by remember { mutableStateOf("") }
    var savingItem by remember { mutableStateOf<ClipboardItem?>(null) }
    var saveName by remember { mutableStateOf("") }
    // DRS v1.10.0: the popup editor's smart state — bounded undo/redo,
    // font customization, and the find/replace engine.
    val editorHistory = remember { ClipEditorHistory() }
    var editorFont by remember { mutableStateOf(ClipFontOption.DEFAULT) }
    var editorFontSize by remember { mutableStateOf(ClipFontSizeOption.NORMAL) }
    var findQuery by remember { mutableStateOf("") }
    var replaceQuery by remember { mutableStateOf("") }
    var matchCase by remember { mutableStateOf(false) }
    var activeMatch by remember { mutableStateOf(0) }

    fun isPopupSurfaceActive() = popupItem != null || showClearAllHistory ||
        editingItem != null || savingItem != null

    // DRS v1.10.0: shares any text through the system share sheet. The
    // chooser activity needs FLAG_ACTIVITY_NEW_TASK because the IME
    // service context is not an activity context.
    fun shareText(text: String) {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        runCatching {
            context.startActivity(
                Intent.createChooser(send, context.getString(R.string.clip__share_item))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }

    // ------------------------------------------------------------------
    // DRS v1.12.0 — the complete smart clipboard system helpers.
    // ------------------------------------------------------------------

    /** The title of a calendar section of the panel. */
    @StringRes
    fun sectionTitleRes(section: ClipHistorySection): Int = when (section) {
        ClipHistorySection.PINNED -> R.string.clipboard__group_pinned
        ClipHistorySection.TODAY -> R.string.clipboard__group_today
        ClipHistorySection.YESTERDAY -> R.string.clipboard__group_yesterday
        ClipHistorySection.THIS_WEEK -> R.string.clipboard__group_this_week
        ClipHistorySection.THIS_MONTH -> R.string.clipboard__group_this_month
        ClipHistorySection.OLDER -> R.string.clipboard__group_older
    }

    /** The badge icon of a smart category (null = no badge for plain text). */
    fun categoryBadgeIcon(category: ClipItemCategory): ImageVector? = when (category) {
        ClipItemCategory.URL -> Icons.Default.Link
        ClipItemCategory.EMAIL -> Icons.Outlined.AlternateEmail
        ClipItemCategory.PHONE -> Icons.Default.Phone
        ClipItemCategory.CODE -> Icons.Default.Code
        ClipItemCategory.TEXT -> null
    }

    /** The display label of a detected code language. */
    @Composable
    fun codeLanguageLabel(language: ClipCodeLanguage): String = stringRes(
        when (language) {
            ClipCodeLanguage.KOTLIN_JAVA -> R.string.clip__lang_kotlin_java
            ClipCodeLanguage.PYTHON -> R.string.clip__lang_python
            ClipCodeLanguage.JAVASCRIPT -> R.string.clip__lang_javascript
            ClipCodeLanguage.JSON -> R.string.clip__lang_json
            ClipCodeLanguage.HTML_XML -> R.string.clip__lang_html_xml
            ClipCodeLanguage.CSS -> R.string.clip__lang_css
            ClipCodeLanguage.SQL -> R.string.clip__lang_sql
            ClipCodeLanguage.C_CPP -> R.string.clip__lang_c_cpp
            ClipCodeLanguage.BASH -> R.string.clip__lang_bash
            ClipCodeLanguage.UNKNOWN -> R.string.clip__lang_unknown
        },
    )

    /**
     * One colored search-result card of the panel: the palette color fills
     * the card border and highlights the matched span inside the preview;
     * tapping navigates to the item's action ladder, long-press pastes.
     */
    @Composable
    fun PanelResultCardView(card: ClipPanelResultCard) {
        val color = Color(ClipResultPalette.COLORS[card.colorSlot])
        val windowStyle = rememberSnyggThemeQuery(DrsImeUi.Window.elementName)
        val themedForeground = windowStyle.foreground()
        val cardTextColor = if (themedForeground.isSpecified) {
            themedForeground
        } else {
            windowStyle.background()
                .takeIf { it.isSpecified }
                ?.let { readableTextColor(it) }
                // DRS a11y (r0-F/G/H): the hardcoded white fallback made the
                // card text invisible on light themes — follow the content
                // color of the surrounding theme instead.
                ?: LocalContentColor.current
        }
        val category = remember(card.item.text) { ClipItemCategoryDetector.detect(card.item.text) }
        // DRS v1.14.0: the category badges are a setting — with them off,
        // the card loses its badge and its "important" border weight.
        val badgeCategory = if (categoryBadgesPref) category else ClipItemCategory.TEXT
        val annotated = buildAnnotatedString {
            if (card.truncatedBefore) append("…")
            append(card.before)
            withStyle(SpanStyle(color = color, fontWeight = FontWeight.Bold)) {
                append(card.matched)
            }
            append(card.after)
            if (card.truncatedAfter) append("…")
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 3.dp)
                .background(color.copy(alpha = 0.12f), RoundedCornerShape(10.dp))
                .border(1.5.dp, if (badgeCategory != ClipItemCategory.TEXT) color else color.copy(alpha = 0.55f), RoundedCornerShape(10.dp))
                .combinedClickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = ripple(),
                    onClick = { popupItem = card.item },
                    onLongClick = { clipboardManager.pasteItem(card.item) },
                )
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val badge = categoryBadgeIcon(badgeCategory)
            if (badge != null) {
                Icon(
                    imageVector = badge,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.padding(end = 6.dp),
                )
            }
            Text(
                text = annotated,
                color = cardTextColor,
                style = LocalTextStyle.current.copy(fontSize = 13.sp),
                maxLines = 2,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            )
        }
    }

    /**
     * One colored search-result card of the editors: the line number plus
     * the same-line context with the matched span painted; tapping makes
     * the match the active one (the counter and the in-text glow follow).
     */
    @Composable
    fun EditorResultCardView(
        card: ClipResultCard,
        active: Boolean,
        onSelect: () -> Unit,
    ) {
        val color = Color(ClipResultPalette.COLORS[card.colorSlot])
        val annotated = buildAnnotatedString {
            if (card.truncatedBefore) append("…")
            append(card.before)
            withStyle(SpanStyle(color = color, fontWeight = FontWeight.Bold)) {
                append(card.matched)
            }
            append(card.after)
            if (card.truncatedAfter) append("…")
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 2.dp)
                .background(
                    color.copy(alpha = if (active) 0.30f else 0.12f),
                    RoundedCornerShape(8.dp),
                )
                .border(1.dp, if (active) color else color.copy(alpha = 0.45f), RoundedCornerShape(8.dp))
                .rippleClickable(onClick = onSelect)
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringRes(R.string.clip__result_line, "line" to card.lineNumber),
                color = color,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp,
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = annotated,
                style = LocalTextStyle.current.copy(fontSize = 12.sp),
                maxLines = 2,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            )
        }
    }

    LaunchedEffect(isFilterRowShown) {
        delay(AnimationDuration.toLong())
        if (!isFilterRowShown) {
            activeFilterTypes.clear()
        }
    }

    LaunchedEffect(activeFilterTypes.toSet()) {
        gridState.scrollToItem(0)
    }

    @Composable
    fun HeaderRow() {
        SnyggRow(DrsImeUi.ClipboardHeader.elementName,
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
                // DRS a11y (r0-F/G/H): the panel's icon buttons speak.
                SnyggIcon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringRes(R.string.action__navigate_back),
                )
            }
            SnyggText(
                elementName = DrsImeUi.ClipboardHeaderText.elementName,
                modifier = Modifier.weight(1f),
                text = stringRes(R.string.clipboard__header_title),
            )
            SnyggIconButton(
                elementName = DrsImeUi.ClipboardHeaderButton.elementName,
                onClick = { scope.launch { prefs.clipboard.historyEnabled.set(!historyEnabled) } },
                modifier = sizeModifier.autoMirrorForRtl(),
                enabled = !deviceLocked && !isPopupSurfaceActive(),
            ) {
                // DRS a11y (r0-F/G/H): history toggle speaks.
                SnyggIcon(
                    imageVector = if (historyEnabled) {
                        Icons.Default.ToggleOn
                    } else {
                        Icons.Default.ToggleOff
                    },
                    contentDescription = stringRes(R.string.pref__clipboard__enable_clipboard_history__label),
                )
            }
            SnyggIconButton(
                elementName = DrsImeUi.ClipboardHeaderButton.elementName,
                onClick = { showClearAllHistory = true },
                modifier = sizeModifier.autoMirrorForRtl(),
                enabled = !deviceLocked && historyEnabled && filteredHistory.all.isNotEmpty() && !isPopupSurfaceActive(),
            ) {
                // DRS a11y (r0-F/G/H): clear-history button speaks.
                SnyggIcon(
                    imageVector = Icons.Default.DeleteSweep,
                    contentDescription = stringRes(R.string.clip__clear_history),
                )
            }
            SnyggIconButton(
                elementName = DrsImeUi.ClipboardHeaderButton.elementName,
                onClick = {
                    isSearchRowShown = !isSearchRowShown
                    if (!isSearchRowShown) searchQuery = ""
                },
                modifier = sizeModifier,
                enabled = !deviceLocked && historyEnabled && unfilteredHistory.all.isNotEmpty() && !isPopupSurfaceActive(),
            ) {
                // DRS a11y (r0-F/G/H): search toggle speaks.
                SnyggIcon(
                    imageVector = if (!isSearchRowShown) {
                        Icons.Default.Search
                    } else {
                        Icons.Default.SearchOff
                    },
                    contentDescription = stringRes(R.string.clipboard__a11y_search),
                )
            }
            SnyggIconButton(
                elementName = DrsImeUi.ClipboardHeaderButton.elementName,
                onClick = { isFilterRowShown = !isFilterRowShown },
                modifier = sizeModifier,
                enabled = !deviceLocked && historyEnabled && unfilteredHistory.all.isNotEmpty() && !isPopupSurfaceActive(),
            ) {
                // DRS a11y (r0-F/G/H): filter toggle speaks.
                SnyggIcon(
                    imageVector = if (!isFilterRowShown) {
                        Icons.Default.FilterList
                    } else {
                        Icons.Default.FilterListOff
                    },
                    contentDescription = stringRes(R.string.clipboard__a11y_filter),
                )
            }
            KeyboardLikeButton(
                modifier = sizeModifier,
                inputEventDispatcher = keyboardManager.inputEventDispatcher,
                keyData = TextKeyData.DELETE,
                elementName = DrsImeUi.ClipboardHeaderButton.elementName,
            ) {
                SnyggIcon(imageVector = Icons.AutoMirrored.Outlined.Backspace)
            }
        }
    }

    @Composable
    fun ClipItemView(
        elementName: String,
        item: ClipboardItem,
        contentScrollInsteadOfClip: Boolean,
        modifier: Modifier = Modifier,
    ) {
        val attributes = remember(item) {
            mapOf("type" to item.type.toString().lowercase())
        }
        SnyggBox(
            elementName = elementName,
            attributes = attributes,
            modifier = modifier.fillMaxWidth(),
            clickAndSemanticsModifier = Modifier.combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(),
                enabled = popupItem == null,
                onLongClick = {
                    popupItem = item
                },
                onClick = {
                    clipboardManager.pasteItem(item)
                },
            ),
        ) {
            if (item.type == ItemType.IMAGE) {
                val id = ContentUris.parseId(item.uri!!)
                val file = ClipboardFileStorage.getFileForId(context, id)
                // DRS v1.19.0: the decode used to run synchronously INSIDE
                // composition (remember{} on the main thread) — a large photo
                // froze the whole keyboard for hundreds of ms. It now decodes
                // on Dispatchers.IO with a small LRU cache, so scrolling the
                // media history stays smooth and the main thread never blocks.
                val bitmap = rememberMediaThumbnail(id) {
                    check(file.exists()) { "Unable to resolve image at ${file.absolutePath}" }
                    val rawBitmap = BitmapFactory.decodeFile(file.absolutePath)
                    checkNotNull(rawBitmap) { "Unable to decode image at ${file.absolutePath}" }
                    rawBitmap.asImageBitmap()
                }
                val decoded = bitmap.value
                if (decoded != null && decoded.isSuccess) {
                    Image(
                        modifier = Modifier.fillMaxWidth(),
                        bitmap = decoded.getOrThrow(),
                        // DRS v1.17.0: media tiles speak — TalkBack announces
                        // what the tile holds instead of skipping it.
                        contentDescription = stringRes(R.string.clipboard__a11y_image_tile),
                        contentScale = ContentScale.FillWidth,
                    )
                } else if (decoded != null) {
                    SnyggText(
                        modifier = Modifier.fillMaxWidth(),
                        text = stringRes(R.string.clipboard__media_unresolvable),
                    )
                }
            } else if (item.type == ItemType.VIDEO) {
                val id = ContentUris.parseId(item.uri!!)
                val file = ClipboardFileStorage.getFileForId(context, id)
                // DRS v1.19.0: async twin of the image fix above —
                // MediaMetadataRetriever + thumbnail extraction leave the
                // main thread for good.
                val bitmap = rememberMediaThumbnail(id) {
                    check(file.exists()) { "Unable to resolve video at ${file.absolutePath}" }
                    val rawBitmap = if (AndroidVersion.ATLEAST_API29_Q) {
                        val dataRetriever = MediaMetadataRetriever()
                        dataRetriever.setDataSource(file.absolutePath)
                        val width = dataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
                        val height = dataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
                        ThumbnailUtils.createVideoThumbnail(file, Size(width!!.toInt(), height!!.toInt()), null)
                    } else {
                        @Suppress("DEPRECATION")
                        ThumbnailUtils.createVideoThumbnail(file.absolutePath, MediaStore.Video.Thumbnails.MINI_KIND)
                    }
                    checkNotNull(rawBitmap) { "Unable to decode video at ${file.absolutePath}" }
                    rawBitmap.asImageBitmap()
                }
                val decoded = bitmap.value
                if (decoded != null && decoded.isSuccess) {
                    Image(
                        modifier = Modifier.fillMaxWidth(),
                        bitmap = decoded.getOrThrow(),
                        // DRS v1.17.0: media tiles speak — see image twin above.
                        contentDescription = stringRes(R.string.clipboard__a11y_video_tile),
                        contentScale = ContentScale.FillWidth,
                    )
                    Icon(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(start = 4.dp, bottom = 4.dp)
                            .background(Color.White, CircleShape),
                        imageVector = Icons.Default.Videocam,
                        contentDescription = null,
                        tint = Color.Black,
                    )
                } else if (decoded != null) {
                    SnyggText(
                        modifier = Modifier.fillMaxWidth(),
                        text = stringRes(R.string.clipboard__media_unresolvable),
                    )
                }
            } else {
                val text = item.stringRepresentation()
                Column {
                    ClipTextItemDescription(
                        elementName = DrsImeUi.ClipboardItemDescription.elementName,
                        attributes = attributes,
                        text = text,
                    )
                    SnyggText(
                        modifier = Modifier
                            .fillMaxWidth()
                            .run { if (contentScrollInsteadOfClip) this.drsVerticalScroll() else this },
                        text = item.displayText(),
                    )
                }
            }
            // DRS v1.9.0: a real state badge on pinned tiles — the pin is
            // the first-class citizen of the smart clipboard system.
            if (item.isPinned) {
                Icon(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                        .background(Color.White, CircleShape),
                    imageVector = Icons.Outlined.PushPin,
                    // DRS v1.17.0: the pinned badge is spoken too.
                    contentDescription = stringRes(R.string.clipboard__a11y_pinned_badge),
                    tint = Color.Black,
                )
            }
            // DRS v1.12.0: the smart category badge of text tiles — a link,
            // an email, a phone number, or a code snippet is flagged on
            // sight (plain text stays unbadged to avoid noise).
            // DRS v1.14.0: the badges themselves are a setting now.
            if (item.type == ItemType.TEXT && categoryBadgesPref) {
                val category = remember(item.text) { ClipItemCategoryDetector.detect(item.text) }
                if (category != ClipItemCategory.TEXT) {
                    Icon(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(4.dp)
                            .background(Color.White, CircleShape),
                        // Non-null: every non-TEXT category maps to a badge icon.
                        imageVector = categoryBadgeIcon(category)!!,
                        contentDescription = null,
                        tint = Color.Black,
                    )
                }
            }
        }
    }

    @Composable
    fun SearchRow() {
        if (!isSearchRowShown) return
        // DRS v1.7.0: the search text used to be hardcoded WHITE — on any
        // light theme (drs_day and friends) it was invisible while typing.
        // It follows the themed window foreground now, falling back to a
        // luminance-derived readable color when the theme leaves it open.
        val windowStyle = rememberSnyggThemeQuery(DrsImeUi.Window.elementName)
        val themedForeground = windowStyle.foreground()
        val searchTextColor = if (themedForeground.isSpecified) {
            themedForeground
        } else {
            windowStyle.background()
                .takeIf { it.isSpecified }
                ?.let { readableTextColor(it) }
                // DRS a11y (r0-F/G/H): the hardcoded white fallback made the
                // search text invisible on light themes — follow the content
                // color of the surrounding theme instead.
                ?: LocalContentColor.current
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BasicTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier.weight(1f),
                singleLine = true,
                textStyle = LocalTextStyle.current.copy(color = searchTextColor, fontSize = 14.sp),
                decorationBox = { innerTextField ->
                    Box {
                        if (searchQuery.isEmpty()) {
                            Text(
                                text = stringRes(R.string.clipboard__search_hint),
                                color = searchTextColor.copy(alpha = 0.6f),
                                fontSize = 14.sp,
                            )
                        }
                        innerTextField()
                    }
                },
            )
            if (searchQuery.isNotEmpty()) {
                SnyggIconButton(
                    elementName = DrsImeUi.ClipboardHeaderButton.elementName,
                    onClick = { searchQuery = "" },
                ) {
                    // DRS a11y (r0-F/G/H): clear-search button speaks.
                    SnyggIcon(
                        imageVector = Icons.Default.Close,
                        contentDescription = stringRes(R.string.clip__a11y_clear_find),
                    )
                }
            }
        }
    }

    @Composable
    fun HistoryMainView() {
        SnyggBox(DrsImeUi.ClipboardContent.elementName,
            modifier = Modifier.fillMaxSize(),
        ) {
            val historyAlpha by animateFloatAsState(targetValue = if (isPopupSurfaceActive()) 0.12f else 1f)
            val staggeredGridCells by prefs.clipboard.historyNumGridColumns()
                .observeAsTransformingState { numGridColumns ->
                    if (numGridColumns == CLIPBOARD_HISTORY_NUM_GRID_COLUMNS_AUTO) {
                        StaggeredGridCells.Adaptive(160.dp)
                    } else {
                        StaggeredGridCells.Fixed(numGridColumns)
                    }
                }

            fun LazyStaggeredGridScope.clipboardItems(
                items: List<ClipboardItem>,
                key: String,
                @StringRes title: Int,
            ) {
                if (items.isNotEmpty()) {
                    item(key, span = StaggeredGridItemSpan.FullLine) {
                        ClipCategoryTitle(text = stringRes(title))
                    }
                    items(items) { item ->
                        ClipItemView(
                            elementName = DrsImeUi.ClipboardItem.elementName,
                            item = item,
                            contentScrollInsteadOfClip = false,
                        )
                    }
                }
            }

            Column(
                modifier = Modifier
                    .matchParentSize()
                    .alpha(historyAlpha),
            ) {
                AnimatedVisibility(
                    visible = isFilterRowShown,
                    enter = VerticalEnterTransition,
                    exit = VerticalExitTransition,
                ) {
                    SnyggRow(
                        elementName = DrsImeUi.ClipboardFilterRow.elementName,
                        modifier = Modifier.fillMaxWidth(),
                        clickAndSemanticsModifier = Modifier.drsHorizontalScroll(),
                    ) {
                        @Composable
                        fun FilterChip(
                            imageVector: ImageVector,
                            text: String,
                            itemType: ItemType,
                        ) {
                            val active = activeFilterTypes.contains(itemType)
                            val attributes = remember(active) {
                                mapOf(
                                    "state" to if (active) "active" else "inactive",
                                    "type" to itemType.toString().lowercase(),
                                )
                            }
                            SnyggChip(
                                elementName = DrsImeUi.ClipboardFilterChip.elementName,
                                attributes = attributes,
                                onClick = {
                                    if (!activeFilterTypes.add(itemType)) {
                                        activeFilterTypes.remove(itemType)
                                    }
                                },
                                imageVector = imageVector,
                                text = text,
                            )
                        }

                        FilterChip(
                            imageVector = Icons.Default.TextFields,
                            // DRS v1.19.0: was a hardcoded "Text" literal.
                            text = stringRes(R.string.clipboard__filter_text),
                            itemType = ItemType.TEXT,
                        )
                        FilterChip(
                            imageVector = Icons.Default.Image,
                            text = stringRes(R.string.clipboard__filter_images),
                            itemType = ItemType.IMAGE,
                        )
                        FilterChip(
                            imageVector = Icons.Default.Movie,
                            text = stringRes(R.string.clipboard__filter_videos),
                            itemType = ItemType.VIDEO,
                        )

                        // DRS v1.12.0: the sort orders of the panel — a
                        // persisted choice rendered right beside the type
                        // filters, so the full organization is one tap deep.
                        @Composable
                        fun SortChip(
                            imageVector: ImageVector,
                            text: String,
                            sort: ClipHistorySort,
                        ) {
                            val active = historySortPref == sort
                            val attributes = remember(active) {
                                mapOf("state" to if (active) "active" else "inactive")
                            }
                            SnyggChip(
                                elementName = DrsImeUi.ClipboardFilterChip.elementName,
                                attributes = attributes,
                                onClick = { scope.launch { prefs.clipboard.historySort.set(sort) } },
                                imageVector = imageVector,
                                text = text,
                            )
                        }

                        SortChip(
                            imageVector = Icons.Default.Schedule,
                            text = stringRes(R.string.clip__sort_newest),
                            sort = ClipHistorySort.NEWEST,
                        )
                        SortChip(
                            imageVector = Icons.Default.History,
                            text = stringRes(R.string.clip__sort_oldest),
                            sort = ClipHistorySort.OLDEST,
                        )
                        SortChip(
                            imageVector = Icons.Default.FormatLineSpacing,
                            text = stringRes(R.string.clip__sort_longest),
                            sort = ClipHistorySort.LONGEST,
                        )
                        SortChip(
                            imageVector = Icons.Default.Notes,
                            text = stringRes(R.string.clip__sort_shortest),
                            sort = ClipHistorySort.SHORTEST,
                        )
                    }
                }
                // DRS v1.12.0: while searching with the colored result
                // cards on, the results render as navigable colored cards
                // (tap opens the item's action ladder, long-press pastes);
                // otherwise the organized calendar sections render.
                if (searchQuery.isNotBlank() && searchResultCardsPref) {
                    val panelCards = remember(filteredHistory.all, searchQuery) {
                        ClipPanelSearchResults.buildCards(filteredHistory.all, searchQuery)
                    }
                    Column(modifier = Modifier.fillMaxSize()) {
                        SnyggText(
                            elementName = DrsImeUi.ClipboardItemTimestamp.elementName,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 2.dp),
                            text = stringRes(R.string.clipboard__search_results_count, "count" to panelCards.size),
                        )
                        LazyColumn(modifier = Modifier.fillMaxSize()) {
                            for (card in panelCards) {
                                item(key = card.item.id) {
                                    PanelResultCardView(card = card)
                                }
                            }
                        }
                    }
                } else {
                    SnyggBox(DrsImeUi.ClipboardGrid.elementName,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                    ) {
                        // DRS v1.12.0: the calendar-aware sections (pinned,
                        // today, yesterday, this week, this month, older)
                        // replace the old pinned/recent/other split — the
                        // comprehensive reorganization of the panel.
                        // DRS v1.14.0: the calendar sections themselves are
                        // a setting — with them off the panel falls back to
                        // one honest flat grid of the same sorted items.
                        val sectionGroups = remember(filteredHistory.all, calendarSectionsPref) {
                            if (calendarSectionsPref) {
                                ClipHistorySections.group(
                                    filteredHistory.all,
                                    System.currentTimeMillis(),
                                    ZoneId.systemDefault(),
                                )
                            } else {
                                emptyList()
                            }
                        }
                        LazyVerticalStaggeredGrid(
                            modifier = Modifier.fillMaxSize(),
                            state = gridState,
                            columns = staggeredGridCells,
                        ) {
                            if (calendarSectionsPref) {
                                for (group in sectionGroups) {
                                    clipboardItems(
                                        items = group.items,
                                        key = "section-${group.section.name.lowercase()}",
                                        title = sectionTitleRes(group.section),
                                    )
                                }
                            } else {
                                clipboardItems(
                                    items = filteredHistory.all,
                                    key = "section-flat",
                                    title = R.string.clipboard__group_flat,
                                )
                            }
                        }
                    }
                }
            }

            if (popupItem != null) {
                SnyggRow(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            detectTapGestures { popupItem = null }
                        },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceAround,
                ) {
                    SnyggColumn(modifier = Modifier.weight(0.5f)) {
                        ClipItemView(
                            elementName = DrsImeUi.ClipboardItemPopup.elementName,
                            modifier = Modifier
                                .widthIn(max = ItemWidth)
                                .weight(1f, fill = false),
                            item = popupItem!!,
                            contentScrollInsteadOfClip = true,
                        )
                        SnyggBox(DrsImeUi.ClipboardItemTimestamp.elementName) {
                            val formatter = LocalLocalizedDateTimeFormatter.current
                            SnyggText(
                                modifier = Modifier.fillMaxWidth(),
                                text = formatter.format(Instant.ofEpochMilli(popupItem!!.creationTimestampMs)),
                            )
                        }
                    }
                    SnyggColumn(modifier = Modifier.weight(0.5f)) {
                        SnyggColumn(DrsImeUi.ClipboardItemActions.elementName) {
                            // DRS v1.9.0: the organized action ladder of the
                            // smart clipboard — paste, copy back, pin, edit,
                            // save as file, delete (edit/save for text only).
                            PopupAction(
                                icon = Icons.Outlined.ContentPasteGo,
                                text = stringRes(R.string.clip__paste_item),
                            ) {
                                clipboardManager.pasteItem(popupItem!!)
                                popupItem = null
                            }
                            // DRS v1.0.6: copy the item back to the system
                            // clipboard without inserting it anywhere.
                            PopupAction(
                                icon = Icons.Default.ContentCopy,
                                text = stringRes(R.string.clip__copy_item_again),
                            ) {
                                clipboardManager.copyItemBack(popupItem!!)
                                popupItem = null
                            }
                            PopupAction(
                                icon = Icons.Outlined.PushPin,
                                text = stringRes(if (popupItem!!.isPinned) {
                                    R.string.clip__unpin_item
                                } else {
                                    R.string.clip__pin_item
                                }),
                            ) {
                                if (popupItem!!.isPinned) {
                                    clipboardManager.unpinClip(popupItem!!)
                                } else if (!clipboardManager.pinClip(popupItem!!)) {
                                    // DRS v1.22.0: the honest pin cap —
                                    // «سقف التثبيتات بلغ حده» instead of a
                                    // silent no-op.
                                    context.showShortToastSync(R.string.clipboard__pin_cap_toast)
                                }
                                popupItem = null
                            }
                            if (popupItem!!.type == ItemType.TEXT) {
                                // DRS v1.10.0: share straight from the item
                                // ladder through the system share sheet.
                                PopupAction(
                                    icon = Icons.Default.Share,
                                    text = stringRes(R.string.clip__share_item),
                                ) {
                                    shareText(popupItem!!.text.orEmpty())
                                    popupItem = null
                                }
                                PopupAction(
                                    icon = Icons.Default.Edit,
                                    text = stringRes(R.string.clip__edit_item),
                                ) {
                                    val target = popupItem!!
                                    popupItem = null
                                    // DRS v1.11.0: the edit request now opens
                                    // the floating popup window above the whole
                                    // screen (outside the panel). The in-panel
                                    // editor stays as the honest fallback when
                                    // the launch is blocked by the system.
                                    // DRS v1.14.0: the user's chosen edit
                                    // surface from the comprehensive settings
                                    // joins the routing — the in-panel choice
                                    // skips the popup window entirely.
                                    val preferred = ClipEditorPopupPolicy.routeFor(
                                        target.type, target.text, editRoutePref,
                                    )
                                    val route = if (preferred == ClipEditorRoute.IN_PANEL) {
                                        ClipEditorRoute.IN_PANEL
                                    } else {
                                        ClipEditorPopupLauncher.launch(context, target)
                                    }
                                    if (route == ClipEditorRoute.IN_PANEL) {
                                        editingItem = target
                                        editingText = target.text.orEmpty()
                                        editorHistory.clear()
                                        // DRS v1.12.0: the editor honors the app
                                        // settings defaults, and detected code
                                        // switches to the monospace family once.
                                        // DRS v1.14.0: the auto switch is its
                                        // own setting.
                                        editorFont = if (codeAutoMonospacePref &&
                                            codeDetectionPref &&
                                            ClipCodeDetector.analyze(target.text.orEmpty()).isCode
                                        ) {
                                            ClipFontOption.MONOSPACE
                                        } else {
                                            defaultFontPref
                                        }
                                        editorFontSize = defaultFontSizePref
                                        findQuery = ""
                                        replaceQuery = ""
                                        matchCase = defaultMatchCasePref
                                        activeMatch = 0
                                        resultsPanelShown = autoResultsPref
                                    }
                                }
                                PopupAction(
                                    icon = Icons.Default.SaveAlt,
                                    text = stringRes(R.string.clip__save_item_as_file),
                                ) {
                                    savingItem = popupItem!!
                                    saveName = ClipFileNamer.defaultFileName(
                                        System.currentTimeMillis(), ZoneId.systemDefault(),
                                    )
                                    popupItem = null
                                }
                            }
                            PopupAction(
                                icon = Icons.Default.Delete,
                                text = stringRes(R.string.clip__delete_item),
                            ) {
                                clipboardManager.deleteClip(popupItem!!, onlyIfUnpinned = false)
                                popupItem = null
                            }
                        }
                    }
                }
            }

            // DRS v1.10.0: the small v1.9.0 edit dialog was promoted to the
            // full-panel popup smart editor (ClipEditorScreen) that takes
            // over the whole clipboard surface — see the root composition.

            // DRS v1.9.0: save-as-file with a user-named file — the name is
            // normalized by ClipFileNamer and written by ClipFileSaver
            // (MediaStore Downloads on Android 10+, private dir before).
            if (savingItem != null) {
                SnyggRow(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            detectTapGestures { savingItem = null }
                        },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceAround,
                ) {
                    SnyggColumn(
                        elementName = DrsImeUi.ClipboardClearAllDialog.elementName,
                        modifier = Modifier
                            .width(EditDialogWidth)
                            .pointerInput(Unit) {
                                detectTapGestures { /* Do nothing */ }
                            },
                    ) {
                        SnyggText(
                            elementName = DrsImeUi.ClipboardHeaderText.elementName,
                            text = stringRes(R.string.clip__save_file_title),
                        )
                        BasicTextField(
                            value = saveName,
                            onValueChange = { saveName = it },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            textStyle = LocalTextStyle.current.copy(fontSize = 14.sp),
                            decorationBox = { innerTextField ->
                                Box {
                                    if (saveName.isEmpty()) {
                                        Text(
                                            text = stringRes(R.string.clip__save_file_name_hint),
                                            color = LocalTextStyle.current.color.copy(alpha = 0.6f),
                                            fontSize = 14.sp,
                                        )
                                    }
                                    innerTextField()
                                }
                            },
                        )
                        SnyggText(
                            elementName = DrsImeUi.ClipboardItemTimestamp.elementName,
                            text = stringRes(R.string.clip__save_file_note),
                        )
                        SnyggRow(DrsImeUi.ClipboardClearAllDialogButtons.elementName) {
                            Spacer(modifier = Modifier.weight(1f))
                            SnyggButton(
                                elementName = DrsImeUi.ClipboardClearAllDialogButton.elementName,
                                attributes = mapOf("action" to "no"),
                                onClick = { savingItem = null },
                            ) {
                                SnyggText(text = stringRes(R.string.action__cancel))
                            }
                            SnyggButton(
                                elementName = DrsImeUi.ClipboardClearAllDialogButton.elementName,
                                attributes = mapOf("action" to "yes"),
                                onClick = {
                                    val item = savingItem!!
                                    val name = ClipFileNamer.sanitize(saveName)
                                    savingItem = null
                                    scope.launch {
                                        val result = withContext(Dispatchers.IO) {
                                            ClipFileSaver.save(context, item.text.orEmpty(), name)
                                        }
                                        val message = when (result) {
                                            is ClipFileSaver.Result.PublicDownloads ->
                                                R.string.clip__saved_to_downloads
                                            is ClipFileSaver.Result.PrivateFiles ->
                                                R.string.clip__saved_to_app_files
                                            ClipFileSaver.Result.Failed ->
                                                R.string.clip__save_failed
                                        }
                                        context.showShortToastSync(message)
                                    }
                                },
                            ) {
                                SnyggText(text = stringRes(R.string.action__save))
                            }
                        }
                    }
                }
            }

            if (showClearAllHistory) {
                SnyggRow(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            detectTapGestures { showClearAllHistory = false }
                        },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceAround,
                ) {
                    SnyggColumn(
                        elementName = DrsImeUi.ClipboardClearAllDialog.elementName,
                        modifier = Modifier
                            .width(DialogWidth)
                            .pointerInput(Unit) {
                                detectTapGestures { /* Do nothing */ }
                            },
                    ) {
                        SnyggText(
                            elementName = DrsImeUi.ClipboardClearAllDialogMessage.elementName,
                            text = stringRes(
                                // DRS p7 (F7): the message must match the
                                // actual deletion scope — clearExactHistory
                                // deletes filteredHistory.unpinned, so the
                                // filtered wording keys off the real search /
                                // filter state, not the filter row visibility.
                                if (searchQuery.isNotBlank() || activeFilterTypes.isNotEmpty()) {
                                    R.string.clipboard__confirm_clear_filtered_history__message
                                } else {
                                    R.string.clipboard__confirm_clear_unfiltered_history__message
                                }
                            ),
                        )
                        SnyggRow(DrsImeUi.ClipboardClearAllDialogButtons.elementName) {
                            Spacer(modifier = Modifier.weight(1f))
                            SnyggButton(
                                elementName = DrsImeUi.ClipboardClearAllDialogButton.elementName,
                                attributes = mapOf("action" to "no"),
                                onClick = {
                                    showClearAllHistory = false
                                },
                            ) {
                                SnyggText(
                                    text = stringRes(R.string.action__no),
                                )
                            }
                            SnyggButton(
                                elementName = DrsImeUi.ClipboardClearAllDialogButton.elementName,
                                attributes = mapOf("action" to "yes"),
                                onClick = {
                                    clipboardManager.clearExactHistory(filteredHistory.unpinned)
                                    context.showShortToastSync(R.string.clipboard__cleared_history)
                                    showClearAllHistory = false
                                },
                            ) {
                                SnyggText(
                                    text = stringRes(R.string.action__yes),
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // DRS v1.10.0: the popup smart editor — a full-panel takeover replacing
    // the small v1.9.0 dialog. Find/replace with match navigation and case
    // control, sixteen smart transform/extract chips, font family and size
    // customization, live statistics, bounded undo/redo, share, and
    // save-as-file — all on top of the same engine-owned pure core.
    @Composable
    fun ClipEditorScreen() {
        // DRS v1.17.0: same background-debounced scan as the popup window —
        // huge texts no longer rescan synchronously per keystroke.
        var matches by remember { mutableStateOf(emptyList<ClipMatch>()) }
        LaunchedEffect(editingText, findQuery, matchCase) {
            if (findQuery.isEmpty()) {
                matches = emptyList()
                return@LaunchedEffect
            }
            delay(150)
            val snapshot = editingText
            matches = withContext(Dispatchers.Default) {
                ClipSearchEngine.findMatches(snapshot, findQuery, ignoreCase = !matchCase)
            }
        }
        val activeIndex = if (matches.isEmpty()) -1 else activeMatch.coerceIn(0, matches.size - 1)

        // DRS v1.13.0: the direct line jump — the editor viewport's scroll
        // state, its laid-out snapshot, its visible height, and the result
        // cards' list state. Tapping a card (or the navigation arrows)
        // animates the field straight to the match's row.
        val editorScrollState = rememberScrollState()
        val cardsListState = rememberLazyListState()
        val jumpScope = rememberCoroutineScope()
        var editorLayout by remember { mutableStateOf<TextLayoutResult?>(null) }
        var editorViewportPx by remember { mutableStateOf(0) }

        fun jumpToMatchLine(offset: Int) {
            // DRS v1.14.0: the jump itself is a setting — when the user
            // turns the direct line jump off, tapping a card (or the
            // arrows) only activates the match without scrolling.
            if (!jumpToLinePref) return
            val target = ClipResultJump.scrollOffsetFor(
                layout = editorLayout?.let(::ClipTextLineLayout),
                offset = offset,
                viewportPx = editorViewportPx,
                maxScrollPx = editorScrollState.maxValue,
                centerRow = jumpCenterPref,
            )
            if (target != null) {
                jumpScope.launch { editorScrollState.animateScrollTo(target) }
            }
        }

        fun applyTransform(newText: String) {
            if (newText != editingText) {
                editorHistory.push(editingText)
                editingText = ClipboardTextPolicy.truncateForStorage(newText, editorLimit)
            }
        }

        SnyggColumn(
            elementName = DrsImeUi.ClipboardContent.elementName,
            modifier = Modifier.fillMaxSize(),
        ) {
            // Header: back, title, share, save-as-file.
            SnyggRow(
                elementName = DrsImeUi.ClipboardHeader.elementName,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(DrsImeSizing.smartbarHeight),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val headerButton = Modifier
                    .sizeIn(maxHeight = DrsImeSizing.smartbarHeight)
                    .aspectRatio(1f)
                SnyggIconButton(
                    elementName = DrsImeUi.ClipboardHeaderButton.elementName,
                    onClick = { editingItem = null },
                    modifier = headerButton,
                ) {
                    // DRS a11y (r0-F/G/H): the editor's icon buttons speak.
                    SnyggIcon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringRes(R.string.action__navigate_back),
                    )
                }
                SnyggText(
                    elementName = DrsImeUi.ClipboardHeaderText.elementName,
                    modifier = Modifier.weight(1f),
                    text = stringRes(R.string.clip__editor_title),
                )
                SnyggIconButton(
                    elementName = DrsImeUi.ClipboardHeaderButton.elementName,
                    onClick = { shareText(editingText) },
                    modifier = headerButton,
                ) {
                    SnyggIcon(
                        imageVector = Icons.Default.Share,
                        contentDescription = stringRes(R.string.clip__a11y_share),
                    )
                }
                SnyggIconButton(
                    elementName = DrsImeUi.ClipboardHeaderButton.elementName,
                    onClick = {
                        savingItem = editingItem
                        saveName = ClipFileNamer.defaultFileName(
                            System.currentTimeMillis(), ZoneId.systemDefault(),
                        )
                        editingItem = null
                    },
                    modifier = headerButton,
                ) {
                    SnyggIcon(
                        imageVector = Icons.Default.SaveAlt,
                        contentDescription = stringRes(R.string.clip__a11y_save_file),
                    )
                }
            }

            // Live statistics.
            val stats = ClipTextStats.of(editingText)
            SnyggText(
                elementName = DrsImeUi.ClipboardItemTimestamp.elementName,
                modifier = Modifier.fillMaxWidth(),
                text = stringRes(
                    R.string.clip__stats_label,
                    "chars" to stats.chars,
                    "words" to stats.words,
                    "lines" to stats.lines,
                ),
            )

            // DRS v1.12.0: the programming-line badge — when the text is
            // detected as code (and detection is on), the language, the
            // code-line share, and the auto monospace family light up.
            val codeAnalysis = remember(editingText, codeDetectionPref) {
                if (codeDetectionPref && editingText.length <= ClipCodeDetector.LARGE_TEXT_CHARS) {
                    ClipCodeDetector.analyze(editingText)
                } else {
                    null
                }
            }
            if (codeBadgePref && codeAnalysis?.isCode == true) {
                SnyggText(
                    elementName = DrsImeUi.ClipboardItemTimestamp.elementName,
                    modifier = Modifier.fillMaxWidth(),
                    text = "\uD83D\uDCBB " + stringRes(
                        R.string.clip__code_badge,
                        "lang" to codeLanguageLabel(codeAnalysis.language),
                        "code" to codeAnalysis.codeLines,
                        "total" to codeAnalysis.totalLines,
                    ),
                )
            }
            // DRS v1.12.0: the slowdown warning for huge texts.
            // DRS v1.14.0: the warning itself is a setting now.
            if (largeTextWarningPref && editingText.length >= ClipboardTextPolicy.LARGE_TEXT_WARNING_CHARS) {
                SnyggText(
                    elementName = DrsImeUi.ClipboardItemTimestamp.elementName,
                    modifier = Modifier.fillMaxWidth(),
                    text = "⚠ " + stringRes(R.string.clip__large_text_warning),
                )
            }

            // Find row: query, case toggle, match counter, navigation.
            SnyggRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BasicTextField(
                    value = findQuery,
                    onValueChange = {
                        findQuery = it
                        activeMatch = 0
                    },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    textStyle = LocalTextStyle.current.copy(fontSize = 14.sp),
                    decorationBox = { innerTextField ->
                        Box {
                            if (findQuery.isEmpty()) {
                                Text(
                                    text = stringRes(R.string.clip__editor_find_hint),
                                    color = LocalTextStyle.current.color.copy(alpha = 0.6f),
                                    fontSize = 14.sp,
                                )
                            }
                            innerTextField()
                        }
                    },
                )
                SnyggChip(
                    elementName = DrsImeUi.ClipboardFilterChip.elementName,
                    attributes = mapOf("state" to if (matchCase) "active" else "inactive"),
                    onClick = {
                        matchCase = !matchCase
                        activeMatch = 0
                    },
                    text = stringRes(R.string.clip__editor_match_case),
                )
                if (findQuery.isNotEmpty()) {
                    SnyggText(
                        elementName = DrsImeUi.ClipboardItemTimestamp.elementName,
                        text = if (matches.isEmpty()) {
                            stringRes(R.string.clip__editor_no_matches)
                        } else {
                            stringRes(
                                R.string.clip__editor_matches_counter,
                                "active" to (activeIndex + 1),
                                "count" to matches.size,
                            )
                        },
                    )
                    SnyggIconButton(
                        elementName = DrsImeUi.ClipboardHeaderButton.elementName,
                        onClick = {
                            if (matches.isNotEmpty()) {
                                val index = ClipSearchEngine.prevMatchIndex(matches.size, activeIndex)
                                activeMatch = index
                                jumpToMatchLine(matches[index].start)
                            }
                        },
                    ) {
                        SnyggIcon(
                            imageVector = Icons.Default.KeyboardArrowUp,
                            contentDescription = stringRes(R.string.clip__a11y_prev),
                        )
                    }
                    SnyggIconButton(
                        elementName = DrsImeUi.ClipboardHeaderButton.elementName,
                        onClick = {
                            if (matches.isNotEmpty()) {
                                val index = ClipSearchEngine.nextMatchIndex(matches.size, activeIndex)
                                activeMatch = index
                                jumpToMatchLine(matches[index].start)
                            }
                        },
                    ) {
                        SnyggIcon(
                            imageVector = Icons.Default.KeyboardArrowDown,
                            contentDescription = stringRes(R.string.clip__a11y_next),
                        )
                    }
                    SnyggIconButton(
                        elementName = DrsImeUi.ClipboardHeaderButton.elementName,
                        onClick = {
                            findQuery = ""
                            replaceQuery = ""
                            activeMatch = 0
                        },
                    ) {
                        SnyggIcon(
                            imageVector = Icons.Default.Close,
                            contentDescription = stringRes(R.string.clip__a11y_clear_find),
                        )
                    }
                }
            }

            // Replace row: appears only while a find query is active.
            if (findQuery.isNotEmpty()) {
                SnyggRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    BasicTextField(
                        value = replaceQuery,
                        onValueChange = { replaceQuery = it },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        textStyle = LocalTextStyle.current.copy(fontSize = 14.sp),
                        decorationBox = { innerTextField ->
                            Box {
                                if (replaceQuery.isEmpty()) {
                                    Text(
                                        text = stringRes(R.string.clip__editor_replace_hint),
                                        color = LocalTextStyle.current.color.copy(alpha = 0.6f),
                                        fontSize = 14.sp,
                                    )
                                }
                                innerTextField()
                            }
                        },
                    )
                    SnyggChip(
                        elementName = DrsImeUi.ClipboardFilterChip.elementName,
                        onClick = {
                            // DRS p7 (F3): the matches are debounced while
                            // the field text keeps moving — a stale match is
                            // guarded by the shared replaceOneIfLive (null =
                            // honest no-op) instead of an IndexOutOfBounds
                            // crash that would kill the whole IME process.
                            val replaced = ClipSearchResults.replaceOneIfLive(
                                editingText, matches.getOrNull(activeIndex), replaceQuery,
                            )
                            if (replaced != null) {
                                applyTransform(replaced)
                            }
                        },
                        text = stringRes(R.string.clip__editor_replace_one),
                    )
                    SnyggChip(
                        elementName = DrsImeUi.ClipboardFilterChip.elementName,
                        onClick = {
                            if (matches.isNotEmpty()) {
                                val (newText, count) = ClipSearchEngine.replaceAll(
                                    editingText, findQuery, replaceQuery, ignoreCase = !matchCase,
                                )
                                applyTransform(newText)
                                context.showShortToastSync(
                                    R.string.clip__editor_replace_done, "count" to count,
                                )
                            }
                        },
                        text = stringRes(R.string.clip__editor_replace_all),
                    )
                }
            }

            // DRS v1.12.0: the colored search-result cards — every match
            // becomes a card with its line number and same-line context.
            // DRS v1.13.0: tapping a card jumps the editor straight to
            // that match's row, and the cards list tracks the active card
            // both ways.
            if (findQuery.isNotEmpty() && searchResultCardsPref && matches.isNotEmpty()) {
                val resultCards = remember(matches, editingText) {
                    ClipSearchResults.buildCards(editingText, matches)
                }
                SnyggRow(
                    elementName = DrsImeUi.ClipboardFilterRow.elementName,
                    modifier = Modifier.fillMaxWidth(),
                    clickAndSemanticsModifier = Modifier.drsHorizontalScroll(),
                ) {
                    SnyggChip(
                        elementName = DrsImeUi.ClipboardFilterChip.elementName,
                        attributes = mapOf("state" to if (resultsPanelShown) "active" else "inactive"),
                        onClick = { resultsPanelShown = !resultsPanelShown },
                        text = if (resultsPanelShown) {
                            stringRes(R.string.clip__results_hide)
                        } else {
                            stringRes(R.string.clip__results_title, "count" to matches.size)
                        },
                    )
                }
                if (resultsPanelShown) {
                    LazyColumn(
                        state = cardsListState,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 132.dp),
                    ) {
                        items(resultCards.size) { cardIndex ->
                            val card = resultCards[cardIndex]
                            EditorResultCardView(
                                card = card,
                                active = card.matchIndex == activeIndex,
                                onSelect = {
                                    activeMatch = card.matchIndex
                                    jumpToMatchLine(card.start)
                                },
                            )
                        }
                    }
                    // Keep the active card in sight — from the arrows,
                    // from a card tap, and after edits.
                    // DRS v1.14.0: the tracking itself is a setting.
                    LaunchedEffect(activeIndex, resultCards) {
                        if (followCardsPref && activeIndex >= 0 && resultCards.isNotEmpty()) {
                            cardsListState.animateScrollToItem(
                                activeIndex.coerceAtMost(resultCards.size - 1),
                            )
                        }
                    }
                }
            }

            // Smart algorithms row: the case family (language-neutral
            // labels), the whitespace/line surgeries, the Arabic-aware
            // normalization, and the smart extractors.
            SnyggRow(
                elementName = DrsImeUi.ClipboardFilterRow.elementName,
                modifier = Modifier.fillMaxWidth(),
                clickAndSemanticsModifier = Modifier.drsHorizontalScroll(),
            ) {
                @Composable
                fun EditorChip(text: String, onClick: () -> Unit) {
                    SnyggChip(
                        elementName = DrsImeUi.ClipboardFilterChip.elementName,
                        onClick = onClick,
                        text = text,
                    )
                }

                EditorChip("AA") { applyTransform(ClipTextTransforms.toUpper(editingText)) }
                EditorChip("aa") { applyTransform(ClipTextTransforms.toLower(editingText)) }
                EditorChip("Aa") { applyTransform(ClipTextTransforms.toTitleCase(editingText)) }
                EditorChip("aA") { applyTransform(ClipTextTransforms.invertCase(editingText)) }
                EditorChip(stringRes(R.string.clip__transform_trim)) {
                    applyTransform(ClipTextTransforms.trimLines(editingText))
                }
                EditorChip(stringRes(R.string.clip__transform_collapse)) {
                    applyTransform(ClipTextTransforms.collapseHorizontalWhitespace(editingText))
                }
                EditorChip(stringRes(R.string.clip__transform_remove_empty)) {
                    applyTransform(ClipTextTransforms.removeEmptyLines(editingText))
                }
                EditorChip(stringRes(R.string.clip__transform_dedupe)) {
                    applyTransform(ClipTextTransforms.removeDuplicateLines(editingText))
                }
                EditorChip(stringRes(R.string.clip__transform_sort_asc)) {
                    applyTransform(ClipTextTransforms.sortLinesAscending(editingText))
                }
                EditorChip(stringRes(R.string.clip__transform_sort_desc)) {
                    applyTransform(ClipTextTransforms.sortLinesDescending(editingText))
                }
                EditorChip(stringRes(R.string.clip__transform_reverse)) {
                    applyTransform(ClipTextTransforms.reverseLines(editingText))
                }
                // DRS v1.12.0: the line numbering and the code-line
                // surgery chips (extract keeps only code lines, remove
                // drops them) — the programming helpers of the editor.
                EditorChip(stringRes(R.string.clip__transform_number_lines)) {
                    applyTransform(ClipTextTransforms.numberLines(editingText))
                }
                EditorChip(stringRes(R.string.clip__transform_extract_code)) {
                    val extracted = ClipCodeDetector.extractCodeLines(editingText)
                    if (extracted.isEmpty()) {
                        context.showShortToastSync(R.string.clip__extract_none)
                    } else {
                        applyTransform(extracted)
                    }
                }
                EditorChip(stringRes(R.string.clip__transform_remove_code)) {
                    applyTransform(ClipCodeDetector.removeCodeLines(editingText))
                }
                EditorChip(stringRes(R.string.clip__transform_no_diacritics)) {
                    applyTransform(ClipTextTransforms.removeArabicDiacritics(editingText))
                }
                EditorChip(stringRes(R.string.clip__transform_normalize)) {
                    applyTransform(ClipTextTransforms.normalizeArabicLetters(editingText))
                }
                // The extractors replace the text with the found lines
                // (undoable) and report the count honestly via a toast.
                EditorChip(stringRes(R.string.clip__extract_urls)) {
                    val found = ClipTextTransforms.extractUrls(editingText)
                    if (found.isEmpty()) {
                        context.showShortToastSync(R.string.clip__extract_none)
                    } else {
                        applyTransform(found.joinToString("\n"))
                        context.showShortToastSync(R.string.clip__extract_done, "count" to found.size)
                    }
                }
                EditorChip(stringRes(R.string.clip__extract_emails)) {
                    val found = ClipTextTransforms.extractEmails(editingText)
                    if (found.isEmpty()) {
                        context.showShortToastSync(R.string.clip__extract_none)
                    } else {
                        applyTransform(found.joinToString("\n"))
                        context.showShortToastSync(R.string.clip__extract_done, "count" to found.size)
                    }
                }
                EditorChip(stringRes(R.string.clip__extract_phones)) {
                    val found = ClipTextTransforms.extractPhoneNumbers(editingText)
                    if (found.isEmpty()) {
                        context.showShortToastSync(R.string.clip__extract_none)
                    } else {
                        applyTransform(found.joinToString("\n"))
                        context.showShortToastSync(R.string.clip__extract_done, "count" to found.size)
                    }
                }
            }

            // Font row: five families + four size steps.
            SnyggRow(
                elementName = DrsImeUi.ClipboardFilterRow.elementName,
                modifier = Modifier.fillMaxWidth(),
                clickAndSemanticsModifier = Modifier.drsHorizontalScroll(),
            ) {
                for (font in ClipFontOption.entries) {
                    SnyggChip(
                        elementName = DrsImeUi.ClipboardFilterChip.elementName,
                        attributes = mapOf("state" to if (font == editorFont) "active" else "inactive"),
                        onClick = { editorFont = font },
                        text = stringRes(
                            when (font) {
                                ClipFontOption.DEFAULT -> R.string.clip__font_default
                                ClipFontOption.SANS_SERIF -> R.string.clip__font_sans
                                ClipFontOption.SERIF -> R.string.clip__font_serif
                                ClipFontOption.MONOSPACE -> R.string.clip__font_mono
                                ClipFontOption.CURSIVE -> R.string.clip__font_cursive
                            },
                        ),
                    )
                }
                for (size in ClipFontSizeOption.entries) {
                    SnyggChip(
                        elementName = DrsImeUi.ClipboardFilterChip.elementName,
                        attributes = mapOf("state" to if (size == editorFontSize) "active" else "inactive"),
                        onClick = { editorFontSize = size },
                        text = size.spValue.toString(),
                    )
                }
            }

            // The editor field itself — takes the remaining height and
            // scrolls under the current editor font.
            val editorFontFamily = when (editorFont) {
                ClipFontOption.DEFAULT -> FontFamily.Default
                ClipFontOption.SANS_SERIF -> FontFamily.SansSerif
                ClipFontOption.SERIF -> FontFamily.Serif
                ClipFontOption.MONOSPACE -> FontFamily.Monospace
                ClipFontOption.CURSIVE -> FontFamily.Cursive
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 8.dp)
                    .onSizeChanged { editorViewportPx = it.height }
                    .drsVerticalScroll(state = editorScrollState),
            ) {
                // DRS v1.12.0: the matches glow inside the text itself —
                // every match paints its palette color, the active one
                // strongest, so the cards and the field tell one story.
                // DRS v1.21.0: ranges are normalized against the current
                // text and re-filtered per apply — the debounced matches
                // can never paint past the shrinking field text.
                val highlightRanges = remember(matches, activeIndex, editingText) {
                    ClipSearchResults.highlightRanges(editingText, matches, activeIndex)
                }
                val highlightTransformation = remember(highlightRanges) {
                    VisualTransformation { fieldText ->
                        val safeRanges = ClipSearchResults.inBounds(
                            highlightRanges,
                            fieldText.text.length,
                        )
                        if (safeRanges.isEmpty()) {
                            TransformedText(
                                androidx.compose.ui.text.AnnotatedString(fieldText.text),
                                OffsetMapping.Identity,
                            )
                        } else {
                            val annotated = buildAnnotatedString {
                                append(fieldText.text)
                                for (range in safeRanges) {
                                    addStyle(
                                        SpanStyle(
                                            background = Color(
                                                ClipResultPalette.COLORS[range.colorSlot],
                                            ).copy(alpha = if (range.isActive) 0.55f else 0.22f),
                                        ),
                                        range.start,
                                        range.end,
                                    )
                                }
                            }
                            TransformedText(annotated, OffsetMapping.Identity)
                        }
                    }
                }
                BasicTextField(
                    value = editingText,
                    onValueChange = { editingText = ClipboardTextPolicy.truncateForStorage(it, editorLimit) },
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = LocalTextStyle.current.copy(
                        fontFamily = editorFontFamily,
                        fontSize = editorFontSize.spValue.sp,
                    ),
                    visualTransformation = highlightTransformation,
                    onTextLayout = { editorLayout = it },
                )
            }

            // Storage-policy counter + undo/redo + cancel/save.
            SnyggText(
                elementName = DrsImeUi.ClipboardItemTimestamp.elementName,
                modifier = Modifier.fillMaxWidth(),
                text = stringRes(
                    R.string.clip__char_limit_counter,
                    "used" to editingText.length,
                    "max" to editorLimit,
                ),
            )
            SnyggRow(DrsImeUi.ClipboardClearAllDialogButtons.elementName) {
                SnyggIconButton(
                    elementName = DrsImeUi.ClipboardHeaderButton.elementName,
                    onClick = { editorHistory.undo(editingText)?.let { editingText = it } },
                    enabled = editorHistory.canUndo,
                ) {
                    // DRS a11y (r0-F/G/H): undo/redo speak (shared keys).
                    SnyggIcon(
                        imageVector = Icons.AutoMirrored.Filled.Undo,
                        contentDescription = stringRes(R.string.quick_action__undo),
                    )
                }
                SnyggIconButton(
                    elementName = DrsImeUi.ClipboardHeaderButton.elementName,
                    onClick = { editorHistory.redo(editingText)?.let { editingText = it } },
                    enabled = editorHistory.canRedo,
                ) {
                    SnyggIcon(
                        imageVector = Icons.AutoMirrored.Filled.Redo,
                        contentDescription = stringRes(R.string.quick_action__redo),
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                SnyggButton(
                    elementName = DrsImeUi.ClipboardClearAllDialogButton.elementName,
                    attributes = mapOf("action" to "no"),
                    onClick = { editingItem = null },
                ) {
                    SnyggText(text = stringRes(R.string.action__cancel))
                }
                SnyggButton(
                    elementName = DrsImeUi.ClipboardClearAllDialogButton.elementName,
                    attributes = mapOf("action" to "yes"),
                    onClick = {
                        val edited = editingItem
                        editingItem = null
                        if (edited != null) {
                            clipboardManager.editClipText(edited, editingText)
                        }
                    },
                ) {
                    SnyggText(text = stringRes(R.string.action__save))
                }
            }
        }
    }

    @Composable
    fun HistoryEmptyView() {
        SnyggColumn(DrsImeUi.ClipboardContent.elementName,
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            SnyggText(
                text = stringRes(R.string.clipboard__empty__title),
            )
            SnyggText(
                text = stringRes(R.string.clipboard__empty__message),
            )
        }
    }

    @Composable
    fun HistoryDisabledView() {
        SnyggColumn(DrsImeUi.ClipboardContent.elementName,
            modifier = Modifier.fillMaxSize(),
        ) {
            SnyggText(
                elementName = DrsImeUi.ClipboardHistoryDisabledTitle.elementName,
                modifier = Modifier.padding(bottom = 8.dp),
                text = stringRes(R.string.clipboard__disabled__title),
            )
            SnyggText(
                elementName = DrsImeUi.ClipboardHistoryDisabledMessage.elementName,
                text = stringRes(R.string.clipboard__disabled__message),
            )
            SnyggButton(DrsImeUi.ClipboardHistoryDisabledButton.elementName,
                onClick = { scope.launch { prefs.clipboard.historyEnabled.set(true) } },
                modifier = Modifier.align(Alignment.End),
            ) {
                SnyggText(
                    text = stringRes(R.string.clipboard__disabled__enable_button),
                )
            }
        }
    }

    @Composable
    fun HistoryLockedView() {
        SnyggColumn(DrsImeUi.ClipboardContent.elementName,
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            SnyggText(
                elementName = DrsImeUi.ClipboardHistoryLockedTitle.elementName,
                text = stringRes(R.string.clipboard__locked__title),
            )
            SnyggText(
                elementName = DrsImeUi.ClipboardHistoryLockedMessage.elementName,
                text = stringRes(R.string.clipboard__locked__message),
            )
        }
    }

    SnyggColumn(
        modifier = modifier
            .fillMaxWidth()
            .height(DrsImeSizing.imeUiHeight()),
    ) {
        if (editingItem != null) {
            // DRS v1.10.0: the popup smart editor takes over the whole panel.
            ClipEditorScreen()
        } else {
            HeaderRow()
            SearchRow()
            if (deviceLocked) {
                HistoryLockedView()
            } else {
                if (historyEnabled) {
                    if (filteredHistory.all.isNotEmpty() || !activeFilterTypes.isEmpty() || searchQuery.isNotBlank()) {
                        HistoryMainView()
                    } else {
                        HistoryEmptyView()
                    }
                } else {
                    HistoryDisabledView()
                }
            }
        }
    }
}

/**
 * DRS v1.7.0: picks a readable text color for [background] by relative
 * luminance (the same threshold the system-bar contrast logic uses).
 * Pure and JVM-testable — the clipboard search row was hardcoded white
 * before, which was invisible on light themes.
 */
fun readableTextColor(background: Color): Color =
    if (background.luminance() >= 0.5f) Color.Black else Color.White

@Composable
private fun ClipCategoryTitle(
    text: String,
    modifier: Modifier = Modifier,
) {
    SnyggText(DrsImeUi.ClipboardSubheader.elementName,
        modifier = modifier.fillMaxWidth(),
        text = text.uppercase(),
    )
}

@Composable
private fun ClipTextItemDescription(
    elementName: String,
    attributes: SnyggQueryAttributes,
    text: String,
    modifier: Modifier = Modifier,
): Unit = with(LocalDensity.current) {
    val icon: ImageVector?
    val description: String?
    when {
        NetworkUtils.isEmailAddress(text) -> {
            icon = Icons.Outlined.Email
            description = stringRes(R.string.clipboard__item_description_email)
        }
        NetworkUtils.isUrl(text) -> {
            icon = Icons.Default.Link
            description = stringRes(R.string.clipboard__item_description_url)
        }
        NetworkUtils.isPhoneNumber(text) -> {
            icon = Icons.Default.Phone
            description = stringRes(R.string.clipboard__item_description_phone)
        }
        else -> {
            icon = null
            description = null
        }
    }
    if (icon != null && description != null) {
        SnyggRow(
            elementName = elementName,
            attributes = attributes,
            modifier = modifier,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SnyggIcon(
                imageVector = icon,
            )
            SnyggText(
                modifier = Modifier.weight(1f),
                text = description,
            )
        }
    }
}

@Composable
private fun PopupAction(
    icon: ImageVector,
    text: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    SnyggRow(DrsImeUi.ClipboardItemAction.elementName,
        modifier = modifier.rippleClickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SnyggIcon(DrsImeUi.ClipboardItemActionIcon.elementName,
            imageVector = icon,
        )
        SnyggText(DrsImeUi.ClipboardItemActionText.elementName,
            modifier = Modifier.weight(1f),
            text = text,
        )
    }
}

/**
 * DRS v1.19.0: bounded LRU of decoded media thumbnails keyed by clip id.
 * Synchronized because decodes run on Dispatchers.IO while the UI thread
 * reads on recomposition. 48 entries keeps memory in the low tens of MB
 * worst case (mini-kind video thumbs are small) without thrashing.
 */
private val mediaThumbnailCache = object : LinkedHashMap<Long, ImageBitmap>(16, 0.75f, true) {
    override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Long, ImageBitmap>?): Boolean {
        return size > 48
    }
}

/**
 * DRS v1.19.0: decodes a media thumbnail OFF the main thread. Previously
 * BitmapFactory.decodeFile / MediaMetadataRetriever ran synchronously inside
 * remember{} during composition, freezing the keyboard for large media and
 * re-decoding on every recomposition. Now the decode is a produceState job on
 * Dispatchers.IO with the LRU above as a second-level cache; the state starts
 * null (tile renders empty for a frame) and completes with the decode Result.
 */
@Composable
private fun rememberMediaThumbnail(
    id: Long,
    decode: suspend () -> ImageBitmap,
): State<Result<ImageBitmap>?> {
    return produceState<Result<ImageBitmap>?>(initialValue = null, id) {
        val cached = synchronized(mediaThumbnailCache) { mediaThumbnailCache[id] }
        if (cached != null) {
            value = Result.success(cached)
            return@produceState
        }
        value = withContext(Dispatchers.IO) {
            runCatching { decode() }
                .onSuccess { decoded ->
                    synchronized(mediaThumbnailCache) { mediaThumbnailCache[id] = decoded }
                }
        }
    }
}
