/*
 * Copyright (C) 2022-2025 The DRS Smart Keyboard Project
 */

package com.drs.smartkeyboard.ime.media.emoji

import android.graphics.Paint
import android.graphics.Typeface
import android.util.TypedValue
import android.widget.TextView
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.isUnspecified
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Popup
import androidx.emoji2.text.EmojiCompat
import androidx.emoji2.widget.EmojiTextView
import com.drs.smartkeyboard.R
import com.drs.smartkeyboard.app.DrsPreferenceStore
import com.drs.smartkeyboard.editorInstance
import com.drs.smartkeyboard.ime.input.LocalInputFeedbackController
import com.drs.smartkeyboard.ime.keyboard.DrsImeSizing
import com.drs.smartkeyboard.ime.text.key.KeyVariation
import com.drs.smartkeyboard.ime.text.keyboard.TextKeyData
import com.drs.smartkeyboard.ime.theme.DrsImeUi
import com.drs.smartkeyboard.keyboardManager
import com.drs.smartkeyboard.ime.clipboard.readableTextColor
import org.drs.jetpref.datastore.model.collectAsState
import kotlinx.coroutines.launch
import org.drs.lib.android.AndroidKeyguardManager
import org.drs.lib.android.showShortToast
import org.drs.lib.android.systemService
import org.drs.lib.compose.drsScrollbar
import org.drs.lib.compose.header
import org.drs.lib.compose.stringRes
import org.drs.lib.snygg.SnyggSelector
import org.drs.lib.snygg.ui.SnyggBox
import org.drs.lib.snygg.ui.SnyggIcon
import org.drs.lib.snygg.ui.SnyggRow
import org.drs.lib.snygg.ui.SnyggText
import org.drs.lib.snygg.ui.rememberSnyggThemeQuery
import kotlin.math.ceil

private val EmojiCategoryValues = EmojiCategory.entries
private val EmojiBaseWidth = 42.dp
private val EmojiDefaultFontSize = 22.sp

/**
 * DRS v1.7.0: emoji SIZE scale (70–200%) read from the preference at
 * composition time. Both the adaptive grid cell width and the glyph font
 * size multiply by this factor, so grid density and glyph legibility
 * really follow the slider. Sanitization lives in ImeWindowSpec beside
 * the other visual scales (one source of truth + unit tests).
 */
@Composable
private fun emojiSizeScale(): Float {
    val prefs by DrsPreferenceStore
    return com.drs.smartkeyboard.ime.window.ImeWindowSpec.sanitizeEmojiScale(
        prefs.emoji.sizePercent.get(),
    )
}

private val VariantsTriangleShapeLtr = GenericShape { size, _ ->
    moveTo(x = size.width, y = 0f)
    lineTo(x = size.width, y = size.height)
    lineTo(x = 0f, y = size.height)
}

private val VariantsTriangleShapeRtl = GenericShape { size, _ ->
    moveTo(x = 0f, y = 0f)
    lineTo(x = size.width, y = size.height)
    lineTo(x = 0f, y = size.height)
}

data class EmojiMappingForView(
    val pinned: List<EmojiSet>,
    val recent: List<EmojiSet>,
    val simple: List<EmojiSet>,
)

@Composable
fun EmojiPaletteView(
    fullEmojiMappings: EmojiData,
    modifier: Modifier = Modifier,
) {
    val prefs by DrsPreferenceStore
    val context = LocalContext.current
    val editorInstance by context.editorInstance()
    val keyboardManager by context.keyboardManager()

    val activeEditorInfo by editorInstance.activeInfoFlow.collectAsState()
    val systemFontPaint = remember(Typeface.DEFAULT) {
        Paint().apply {
            typeface = Typeface.DEFAULT
        }
    }
    val metadataVersion = activeEditorInfo.emojiCompatMetadataVersion
    val replaceAll = activeEditorInfo.emojiCompatReplaceAll
    val emojiCompatInstance by DrsEmojiCompat.getAsFlow(replaceAll).collectAsState()
    val emojiMappings = remember(emojiCompatInstance, fullEmojiMappings, metadataVersion, systemFontPaint) {
        fullEmojiMappings.byCategory.mapValues { (_, emojiSetList) ->
            emojiSetList.mapNotNull { emojiSet ->
                emojiSet.emojis.filter { emoji ->
                    emojiCompatInstance?.getEmojiMatch(emoji.value, metadataVersion) == EmojiCompat.EMOJI_SUPPORTED ||
                        systemFontPaint.hasGlyph(emoji.value)
                }.let { if (it.isEmpty()) null else EmojiSet(it) }
            }
        }
    }
    val androidKeyguardManager = remember { context.systemService(AndroidKeyguardManager::class) }

    val deviceLocked = androidKeyguardManager.let { it.isDeviceLocked || it.isKeyguardLocked }

    val preferredSkinTone by prefs.emoji.preferredSkinTone.collectAsState()
    val emojiHistoryEnabled by prefs.emoji.historyEnabled.collectAsState()

    var activeCategory by remember(emojiHistoryEnabled) {
        if (emojiHistoryEnabled) {
            mutableStateOf(EmojiCategory.RECENTLY_USED)
        } else {
            mutableStateOf(EmojiCategory.SMILEYS_EMOTION)
        }
    }
    var recentlyUsedVersion by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()

    @Composable
    fun GridHeader(text: String) {
        SnyggText(
            elementName = DrsImeUi.MediaEmojiSubheader.elementName,
            text = text,
        )
    }

    @Composable
    fun EmojiKeyWrapper(
        emojiSet: EmojiSet,
        isPinned: Boolean = false,
        isRecent: Boolean = false,
        canMoveLeft: Boolean = false,
        canMoveRight: Boolean = false,
    ) {
        EmojiKey(
            emojiSet = emojiSet,
            emojiCompatInstance = emojiCompatInstance,
            preferredSkinTone = preferredSkinTone,
            isPinned = isPinned,
            isRecent = isRecent,
            canMoveLeft = canMoveLeft,
            canMoveRight = canMoveRight,
            onEmojiInput = { emoji ->
                keyboardManager.inputEventDispatcher.sendDownUp(emoji)
                scope.launch {
                    // DRS v2.14.0: الالتقاط يُحكم بالبوابة عند الحدث نفسه —
                    // حقائق السياق تُقرأ من الحالة الحية لحظة النقر (لا لقطة
                    // متراكمة)، فلا جلسة تخفي ولا حقل كلمة مرور ولا محرر خام
                    // ولا حقل بلا تكوين ولا قفل شاشي يترك أثرًا في الذاكرة.
                    val state = keyboardManager.activeState.value
                    EmojiHistoryHelper.markEmojiUsed(
                        prefs, emoji,
                        DrsEmojiHistoryGate.RecordContext(
                            isIncognitoMode = state.isIncognitoMode,
                            isPasswordVariation = state.keyVariation == KeyVariation.PASSWORD,
                            isRawInputEditor = activeEditorInfo.isRawInputEditor,
                            isComposingEnabled = state.isComposingEnabled,
                            isDeviceLocked = deviceLocked,
                        ),
                    )
                }
            },
            onHistoryAction = {
                recentlyUsedVersion++
            },
        )
    }

    fun calculatePageNumbers(): Int {
        return when {
            !emojiHistoryEnabled -> EmojiCategoryValues.size - 1
            else -> EmojiCategoryValues.size
        }
    }

    fun pageNumberToCategory(pageNumber: Int): EmojiCategory {
        return when {
            !emojiHistoryEnabled -> EmojiCategoryValues[pageNumber + 1]
            else -> EmojiCategoryValues[pageNumber]
        }
    }

    fun categoryToPageNumber(category: EmojiCategory): Int {
        return if (emojiHistoryEnabled) {
            EmojiCategoryValues.indexOf(category)
        } else {
            EmojiCategoryValues.indexOf(category) - 1
        }
    }


    @Composable
    fun EmojiCategoriesTabRow(
        activeCategory: EmojiCategory,
        onCategoryChange: (EmojiCategory) -> Unit,
    ) {
        val inputFeedbackController = LocalInputFeedbackController.current
        val selectedTabIndex = categoryToPageNumber(activeCategory)
        val style = rememberSnyggThemeQuery(DrsImeUi.MediaEmojiTab.elementName)
        PrimaryTabRow(
            modifier = Modifier
                .fillMaxWidth()
                .height(DrsImeSizing.smartbarHeight),
            selectedTabIndex = selectedTabIndex,
            containerColor = Color.Transparent,
            contentColor = style.foreground(),
            indicator = {
                val style = rememberSnyggThemeQuery(
                    elementName = DrsImeUi.MediaEmojiTab.elementName,
                    selector = SnyggSelector.FOCUS,
                )
                TabRowDefaults.PrimaryIndicator(
                    Modifier.tabIndicatorOffset(selectedTabIndex),
                    height = 4.dp,
                    color = style.foreground(),
                )
            },
        ) {
            for (category in EmojiCategoryValues) {
                if (category == EmojiCategory.RECENTLY_USED && !emojiHistoryEnabled) {
                    continue
                }
                // DRS v1.21.0: the tabs used to be icon-only — TalkBack
                // announced an unlabeled tab. The localized category name
                // rides the tab's semantics now.
                val context = LocalContext.current
                val categoryLabel = context.getString(category.labelRes())
                Tab(
                    modifier = Modifier.semantics { contentDescription = categoryLabel },
                    onClick = {
                        inputFeedbackController.keyPress(TextKeyData.UNSPECIFIED)
                        onCategoryChange(category)
                    },
                    selected = activeCategory == category,
                    icon = { SnyggIcon(
                        elementName = DrsImeUi.MediaEmojiTab.elementName,
                        selector = if (activeCategory == category) SnyggSelector.FOCUS else SnyggSelector.NONE,
                        modifier = Modifier.size(ButtonDefaults.IconSize),
                        imageVector = category.icon(),
                    ) },
                )
            }
        }
    }

    Column(
        modifier = modifier
    ) {
        // DRS v1.0.5: emoji search — while active, keyboard characters are
        // routed by KeyboardManager into mediaSearchQuery (IME-internal
        // fields can't receive the IME's own key events directly). Matches
        // emoji names/keywords from the :name suggestion metadata.
        val mediaSearchActive = keyboardManager.activeState.collectAsState().value.isMediaSearchActive
        val mediaSearchQuery by keyboardManager.mediaSearchQuery.collectAsState()
        // DRS v1.21.0: the search row used to paint itself from the default
        // MaterialTheme while every other panel follows the snygg keyboard
        // theme — on any custom theme the pill clashed. It reads the themed
        // window colors now, with a luminance-derived readable fallback.
        val windowStyle = rememberSnyggThemeQuery(DrsImeUi.Window.elementName)
        val themedForeground = windowStyle.foreground()
        val searchTextColor = if (themedForeground.isSpecified) {
            themedForeground
        } else {
            windowStyle.background()
                .takeIf { it.isSpecified }
                ?.let { readableTextColor(it) }
                ?: MaterialTheme.colorScheme.onSurfaceVariant
        }
        val searchContainer = windowStyle.background().takeIf { it.isSpecified }
            ?.copy(alpha = 0.45f)
            ?: MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(searchContainer)
                .clickable { keyboardManager.activeState.isMediaSearchActive = true }
                .padding(horizontal = 10.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = null,
                tint = searchTextColor,
                modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = if (mediaSearchQuery.isEmpty()) {
                    stringRes(R.string.emoji__search__hint)
                } else {
                    mediaSearchQuery
                },
                style = MaterialTheme.typography.bodySmall,
                color = if (mediaSearchQuery.isEmpty()) {
                    searchTextColor.copy(alpha = 0.7f)
                } else {
                    searchTextColor
                },
                maxLines = 1,
                modifier = Modifier.weight(1f),
            )
            if (mediaSearchActive || mediaSearchQuery.isNotEmpty()) {
                IconButton(
                    onClick = { keyboardManager.exitMediaSearch() },
                    modifier = Modifier.size(26.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = stringRes(R.string.emoji__search__clear),
                        tint = searchTextColor,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }

        val trimmedSearchQuery = mediaSearchQuery.trim()
        if (trimmedSearchQuery.isEmpty()) {
            val pagerState = rememberPagerState(
                pageCount = { calculatePageNumbers() }
            )

        // Reset the pager to the first page when emojiHistory is enabled
        LaunchedEffect(emojiHistoryEnabled) {
            pagerState.animateScrollToPage(0)
        }

        EmojiCategoriesTabRow(
            activeCategory = activeCategory,
            onCategoryChange = { category ->
                activeCategory = category
                scope.launch { pagerState.animateScrollToPage(categoryToPageNumber(activeCategory)) }
            },
        )
        HorizontalPager(pagerState, beyondViewportPageCount = 1) { page ->
            // Every page needs its own lazyGridState in order to scroll correctly
            val lazyGridState = rememberLazyGridState()

            // Update the lazyGridState and active category on scroll
            LaunchedEffect(pagerState) {
                snapshotFlow { pagerState.currentPage }.collect { page ->
                    lazyGridState.scrollToItem(0)
                    activeCategory = pageNumberToCategory(page)
                    recentlyUsedVersion++
                }
            }

            val category = pageNumberToCategory(page)
            val emojiMapping = if (category == EmojiCategory.RECENTLY_USED) {
                // Purposely using remember here to prevent recomposition, as this would cause rapid
                // emoji changes for the user when in recently used category.
                remember(recentlyUsedVersion) {
                    val data = prefs.emoji.historyData.get()
                    EmojiMappingForView(
                        pinned = data.pinned.map { EmojiSet(listOf(it)) },
                        recent = data.recent.map { EmojiSet(listOf(it)) },
                        simple = emptyList(),
                    )
                }
            } else {
                EmojiMappingForView(
                    pinned = emptyList(),
                    recent = emptyList(),
                    simple = emojiMappings[category]!!,
                )
            }

            val isEmojiHistoryEmpty = emojiMapping.pinned.isEmpty() && emojiMapping.recent.isEmpty()
            when (category) {
                EmojiCategory.RECENTLY_USED if deviceLocked -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(all = 8.dp),
                    ) {
                        Text(
                            text = stringRes(R.string.emoji__history__phone_locked_message),
                        )
                    }
                }
                EmojiCategory.RECENTLY_USED if isEmojiHistoryEmpty -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(all = 8.dp),
                    ) {
                        Text(
                            text = stringRes(R.string.emoji__history__empty_message),
                        )
                        Text(
                            modifier = Modifier.padding(top = 8.dp),
                            text = stringRes(R.string.emoji__history__usage_tip),
                            fontStyle = FontStyle.Italic,
                        )
                    }
                }
                else -> key(emojiMapping) {
                    LazyVerticalGrid(
                        modifier = Modifier
                            .fillMaxSize()
                            .drsScrollbar(lazyGridState),
                        // DRS v1.7.0: the grid cell follows the emoji size scale.
                        columns = GridCells.Adaptive(minSize = EmojiBaseWidth * emojiSizeScale()),
                        state = lazyGridState,
                    ) {
                        if (emojiMapping.pinned.isNotEmpty()) {
                            header("header_pinned") {
                                GridHeader(text = stringRes(R.string.emoji__history__pinned))
                            }
                            // DRS v1.19.0: edge-aware reorder arrows — the move arrows
                            // only render when a neighboring slot actually exists.
                            itemsIndexed(emojiMapping.pinned) { index, emojiSet ->
                                EmojiKeyWrapper(
                                    emojiSet,
                                    isPinned = true,
                                    canMoveLeft = index > 0,
                                    canMoveRight = index < emojiMapping.pinned.lastIndex,
                                )
                            }
                        }
                        if (emojiMapping.recent.isNotEmpty()) {
                            header("header_recent") {
                                GridHeader(text = stringRes(R.string.emoji__history__recent))
                            }
                            itemsIndexed(emojiMapping.recent) { index, emojiSet ->
                                EmojiKeyWrapper(
                                    emojiSet,
                                    isRecent = true,
                                    canMoveLeft = index > 0,
                                    canMoveRight = index < emojiMapping.recent.lastIndex,
                                )
                            }
                        }
                        if (emojiMapping.simple.isNotEmpty()) {
                            items(emojiMapping.simple) { emojiSet ->
                                EmojiKeyWrapper(emojiSet)
                            }
                        }
                    }
                }
            }
        }
        } else {
            // DRS v1.0.5: live search results across every category, using
            // the same emoji metadata that powers :name suggestions.
            val searchGridState = rememberLazyGridState()
            val query = trimmedSearchQuery.lowercase()
            val searchResults = remember(query, emojiMappings) {
                if (query.isEmpty()) {
                    emptyList()
                } else {
                    emojiMappings.values
                        .asSequence()
                        .flatten()
                        .filter { emojiSet ->
                            val base = emojiSet.emojis.first()
                            base.name.lowercase().contains(query) ||
                                base.keywords.any { it.lowercase().contains(query) }
                        }
                        .take(60)
                        .toList()
                }
            }
            if (searchResults.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(all = 8.dp),
                ) {
                    Text(
                        text = stringRes(R.string.emoji__search__no_results),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            } else {
                LazyVerticalGrid(
                    modifier = Modifier
                        .fillMaxSize()
                        .drsScrollbar(searchGridState),
                    // DRS v1.7.0: the search grid follows the scale too.
                    columns = GridCells.Adaptive(minSize = EmojiBaseWidth * emojiSizeScale()),
                    state = searchGridState,
                ) {
                    items(searchResults) { emojiSet ->
                        EmojiKeyWrapper(emojiSet)
                    }
                }
            }
        }
    }
}

@Composable
private fun EmojiKey(
    emojiSet: EmojiSet,
    emojiCompatInstance: EmojiCompat?,
    preferredSkinTone: EmojiSkinTone,
    isPinned: Boolean,
    isRecent: Boolean,
    canMoveLeft: Boolean,
    canMoveRight: Boolean,
    onEmojiInput: (Emoji) -> Unit,
    onHistoryAction: () -> Unit,
) {
    val inputFeedbackController = LocalInputFeedbackController.current
    val base = emojiSet.base(withSkinTone = preferredSkinTone)
    val variations = emojiSet.variations(withoutSkinTone = preferredSkinTone)
    var showVariantsBox by remember { mutableStateOf(false) }

    SnyggBox(DrsImeUi.MediaEmojiKey.elementName,
        modifier = Modifier
            .aspectRatio(1f)
            // DRS v1.17.0: the emoji itself is its own spoken label —
            // TalkBack resolves emoji characters to localized names.
            .semantics { contentDescription = base.value }
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        inputFeedbackController.keyPress(TextKeyData.UNSPECIFIED)
                    },
                    onTap = {
                        onEmojiInput(base)
                    },
                    onLongPress = {
                        inputFeedbackController.keyLongPress(TextKeyData.UNSPECIFIED)
                        if (variations.isNotEmpty() || isPinned || isRecent) {
                            showVariantsBox = true
                        }
                    },
                )
            },
    ) {
        EmojiText(
            modifier = Modifier.align(Alignment.Center),
            text = base.value,
            emojiCompatInstance = emojiCompatInstance,
        )
        if (variations.isNotEmpty() || isPinned || isRecent) {
            val style = rememberSnyggThemeQuery(DrsImeUi.MediaEmojiKeyPopupExtendedIndicator.elementName)
            val shape = when (LocalLayoutDirection.current) {
                LayoutDirection.Ltr -> VariantsTriangleShapeLtr
                LayoutDirection.Rtl -> VariantsTriangleShapeRtl
            }
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .offset(x = (-4).dp, y = (-4).dp)
                    .size(4.dp)
                    .background(style.foreground(), shape),
            )
        }

        if (isPinned || isRecent) {
            EmojiHistoryPopup(
                emoji = base,
                visible = showVariantsBox,
                isCurrentlyPinned = isPinned,
                canMoveLeft = canMoveLeft,
                canMoveRight = canMoveRight,
                onHistoryAction = {
                    onHistoryAction()
                    showVariantsBox = false
                },
                onDismiss = {
                    showVariantsBox = false
                },
            )
        } else {
            EmojiVariationsPopup(
                variations = variations,
                visible = showVariantsBox,
                emojiCompatInstance = emojiCompatInstance,
                onEmojiTap = { emoji ->
                    onEmojiInput(emoji)
                    showVariantsBox = false
                },
                onDismiss = {
                    showVariantsBox = false
                },
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EmojiVariationsPopup(
    variations: List<Emoji>,
    visible: Boolean,
    emojiCompatInstance: EmojiCompat?,
    onEmojiTap: (Emoji) -> Unit,
    onDismiss: () -> Unit,
) {
    val emojiKeyHeight = DrsImeSizing.smartbarHeight

    if (visible) {
        Popup(
            alignment = Alignment.TopCenter,
            offset = with(LocalDensity.current) {
                val y = -emojiKeyHeight * ceil(variations.size / 6f)
                IntOffset(x = 0, y = y.toPx().toInt())
            },
            onDismissRequest = onDismiss,
        ) {
            SnyggRow(
                elementName = DrsImeUi.MediaEmojiKeyPopupBox.elementName,
                modifier = Modifier
                    .widthIn(max = EmojiBaseWidth * emojiSizeScale() * 6),
            ) {
                for (emoji in variations) {
                    SnyggBox(
                        elementName = DrsImeUi.MediaEmojiKeyPopupElement.elementName,
                        modifier = Modifier
                            .pointerInput(Unit) {
                                detectTapGestures { onEmojiTap(emoji) }
                            }
                            .width(EmojiBaseWidth * emojiSizeScale())
                            .height(emojiKeyHeight),
                    ) {
                        EmojiText(
                            modifier = Modifier.align(Alignment.Center),
                            text = emoji.value,
                            emojiCompatInstance = emojiCompatInstance,
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EmojiHistoryPopup(
    emoji: Emoji,
    visible: Boolean,
    isCurrentlyPinned: Boolean,
    canMoveLeft: Boolean,
    canMoveRight: Boolean,
    onHistoryAction: () -> Unit,
    onDismiss: () -> Unit,
) {
    val prefs by DrsPreferenceStore
    val scope = rememberCoroutineScope()
    val emojiKeyHeight = DrsImeSizing.smartbarHeight
    val context = LocalContext.current
    val pinnedUS by prefs.emoji.historyPinnedUpdateStrategy.collectAsState()
    val recentUS by prefs.emoji.historyRecentUpdateStrategy.collectAsState()
    // DRS v1.19.0: the two conditions were copy-pasted identical, so both arrows
    // rendered at list edges where moveEmoji is a no-op, and the manual-sort gate
    // tested the wrong strategy for non-pinned emojis. Now: a pinned emoji reorders
    // the pinned list (gated by the pinned strategy), a recent emoji reorders the
    // recent list (gated by the recent strategy), and each arrow additionally
    // requires an existing neighbor slot (canMoveLeft/canMoveRight from the index).
    val moveAllowed = if (isCurrentlyPinned) !pinnedUS.isAutomatic else !recentUS.isAutomatic
    val showMoveLeft = canMoveLeft && moveAllowed
    val showMoveRight = canMoveRight && moveAllowed

    @Composable
    fun Action(icon: ImageVector, action: suspend () -> Unit) {
        SnyggBox(
            elementName = DrsImeUi.MediaEmojiKeyPopupElement.elementName,
            modifier = Modifier
                .pointerInput(Unit) {
                    detectTapGestures {
                        scope.launch {
                            action()
                            onHistoryAction()
                        }
                    }
                }
                .width(EmojiBaseWidth * emojiSizeScale())
                .height(emojiKeyHeight),
        ) {
            SnyggIcon(
                modifier = Modifier.align(Alignment.Center),
                imageVector = icon,
            )
        }
    }

    // DRS v1.19.0: the popup offset was computed from a hardcoded numActions = 1,
    // so the row floated too high whenever 2–4 actions were visible. It now counts
    // the actions actually shown.
    val numActions = 1 + (if (showMoveLeft) 1 else 0) + (if (showMoveRight) 1 else 0)
    if (visible) {
        Popup(
            alignment = Alignment.TopCenter,
            offset = with(LocalDensity.current) {
                val y = -emojiKeyHeight * ceil(numActions / 6f)
                IntOffset(x = 0, y = y.toPx().toInt())
            },
            onDismissRequest = onDismiss,
        ) {
            SnyggRow(
                elementName = DrsImeUi.MediaEmojiKeyPopupBox.elementName,
                modifier = Modifier
                    .widthIn(max = EmojiBaseWidth * emojiSizeScale() * 6),
            ) {
                if (isCurrentlyPinned) {
                    Action(
                        icon = Icons.Outlined.PushPin,
                        action = {
                            EmojiHistoryHelper.unpinEmoji(prefs, emoji)
                        },
                    )
                } else {
                    Action(
                        icon = Icons.Outlined.PushPin,
                        action = {
                            EmojiHistoryHelper.pinEmoji(prefs, emoji)
                        },
                    )
                }
                if (showMoveLeft) {
                    Action(
                        icon = Icons.AutoMirrored.Default.KeyboardArrowLeft,
                        action = {
                            EmojiHistoryHelper.moveEmoji(prefs, emoji, -1)
                        },
                    )
                }
                if (showMoveRight) {
                    Action(
                        icon = Icons.AutoMirrored.Default.KeyboardArrowRight,
                        action = {
                            EmojiHistoryHelper.moveEmoji(prefs, emoji, 1)
                        },
                    )
                }
                Action(
                    icon = Icons.Outlined.Delete,
                    action = {
                        EmojiHistoryHelper.removeEmoji(prefs, emoji)
                        context.showShortToast(
                            R.string.emoji__history__removal_success_message,
                            "emoji" to emoji.value,
                        )
                    },
                )
            }
        }
    }
}

@Composable
fun EmojiText(
    text: String,
    emojiCompatInstance: EmojiCompat?,
    modifier: Modifier = Modifier,
    color: Color = Color.Black,
    fontSize: TextUnit = TextUnit.Unspecified,
) {
    // DRS v1.7.0: the glyph follows the emoji size scale (70–200%) unless
    // the caller pinned an explicit size — the text view factory below
    // receives the effective size in sp.
    val effectiveSize =
        if (!fontSize.isUnspecified) fontSize else EmojiDefaultFontSize * emojiSizeScale()
    if (emojiCompatInstance != null) {
        AndroidView(
            modifier = modifier,
            factory = { context ->
                EmojiTextView(context).also {
                    it.setTextSize(TypedValue.COMPLEX_UNIT_SP, effectiveSize.value)
                    it.setTextColor(color.toArgb())
                }
            },
            update = { view ->
                // DRS p7 (E2-7): the factory runs once per composed node —
                // re-apply size/color here too, or changing the emoji-size
                // scale / theme while the palette is open leaves existing
                // glyph cells at their old size/color.
                view.setTextSize(TypedValue.COMPLEX_UNIT_SP, effectiveSize.value)
                view.setTextColor(color.toArgb())
                view.text = text
            },
        )
    } else {
        AndroidView(
            modifier = modifier,
            factory = { context ->
                TextView(context).also {
                    it.setTextSize(TypedValue.COMPLEX_UNIT_SP, effectiveSize.value)
                    it.setTextColor(color.toArgb())
                }
            },
            update = { view ->
                // DRS p7 (E2-7): same re-apply for the fallback painter path.
                view.setTextSize(TypedValue.COMPLEX_UNIT_SP, effectiveSize.value)
                view.setTextColor(color.toArgb())
                view.text = text
            },
        )
    }
}
