/*
 * Copyright (C) 2021-2025 The DRS Smart Keyboard Project
 * Copyright (C) 2025 DRS Smart Keyboard contributors
 */

package com.drs.smartkeyboard.drs.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.automirrored.outlined.Backspace
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Abc
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloseFullscreen
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.ContentPasteGo
import androidx.compose.material.icons.filled.Dialpad
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.East
import androidx.compose.material.icons.filled.EmojiEmotions
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Spellcheck
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.FirstPage
import androidx.compose.material.icons.filled.Functions
import androidx.compose.material.icons.filled.KeyboardDoubleArrowLeft
import androidx.compose.material.icons.filled.KeyboardDoubleArrowRight
import androidx.compose.material.icons.filled.KeyboardHide
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.LastPage
import androidx.compose.material.icons.filled.Numbers
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.MergeType
import androidx.compose.material.icons.filled.Splitscreen
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.TextFormat
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.VerticalAlignBottom
import androidx.compose.material.icons.filled.VerticalAlignTop
import androidx.compose.material.icons.filled.ViewAgenda
import androidx.compose.material.icons.filled.West
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.drs.smartkeyboard.R
import com.drs.smartkeyboard.drs.DrsAdaptationEngine
import com.drs.smartkeyboard.drs.DrsContextMode
import com.drs.smartkeyboard.drs.DrsHybridViewMode
import com.drs.smartkeyboard.drs.DrsMotion
import com.drs.smartkeyboard.drs.DrsProfileManager
import com.drs.smartkeyboard.drs.DrsRuntimeState
import com.drs.smartkeyboard.drs.DrsSmartbarShape
import com.drs.smartkeyboard.drs.DrsStore
import com.drs.smartkeyboard.drs.DrsSystems
import com.drs.smartkeyboard.drs.DrsTechToolbarKeys
import com.drs.smartkeyboard.drs.rememberDrsMotionEnabled
import androidx.compose.foundation.shape.RoundedCornerShape
import com.drs.smartkeyboard.drs.DrsUnified
import com.drs.smartkeyboard.drs.DrsUnifiedTools
import com.drs.smartkeyboard.drs.DrsUserPath
import com.drs.smartkeyboard.app.DrsPreferenceStore
import com.drs.smartkeyboard.ime.ImeUiMode
import com.drs.smartkeyboard.ime.input.LocalInputFeedbackController
import com.drs.smartkeyboard.ime.keyboard.DrsImeSizing
import com.drs.smartkeyboard.ime.smartbar.quickaction.SmartToolCodes
import com.drs.smartkeyboard.ime.text.keyboard.TextKeyData
import com.drs.smartkeyboard.ime.text.key.KeyCode
import com.drs.smartkeyboard.ime.text.key.KeyType
import com.drs.smartkeyboard.ime.theme.DrsImeUi
import com.drs.smartkeyboard.ime.window.ImeWindowSpec
import com.drs.smartkeyboard.ime.window.LocalWindowController
import com.drs.smartkeyboard.keyboardManager
import androidx.compose.material3.Text
import org.drs.jetpref.datastore.model.collectAsState
import org.drs.lib.compose.stringRes
import org.drs.lib.snygg.ui.SnyggIcon
import org.drs.lib.snygg.ui.SnyggIconButton
import org.drs.lib.snygg.ui.SnyggRow

/**
 * DRS v1.0.7 — الشريط الموحد لنظام «كلاهما».
 *
 * One strip that serves all three systems and renders the RIGHT tools for
 * the active display level:
 *  - النظام العادي: hidden by default, opt-in from the unified tools
 *    manager; shows the simple level's tools.
 *  - النظام التقني: shows under the same conditions as the former
 *    technical toolbar (profile toggle or coding-context auto show) and
 *    renders the advanced level (user's technical keys + shared editing
 *    tools).
 *  - نظام كلاهما: adds the leading level-cycle button (بسيط/تقني/مزدوج)
 *    and renders the stored level - DUAL merges both worlds in one strip.
 *
 * Every button dispatches a REAL KeyCode through the input event
 * dispatcher (the same pipeline the physical layout uses), and the strip
 * never renders in password fields. Customization (order / pins / hidden
 * / per-level visibility) is persisted in DrsState.
 */

/**
 * DRS v2.2.1 «الشريطان الحيّان» — the shared press-pulse wiring of the
 * tasks bar. Every strip button hoists an interaction source, reads the
 * pressed state from it and drives the SAME DrsMotion pulse the keyboard
 * keys use (M2.2) — one motion language across the whole IME surface.
 * The scale rides graphicsLayer (draw-only), so the row geometry is
 * never re-measured per frame. Feedback (haptics/sound) fires at the
 * interaction site exactly like the keyboard keys (TextKeyboardLayout)
 * and the quick actions (QuickActionButton) — the strip was SILENT
 * before this round: the dispatcher pipeline owns no feedback.
 */
internal fun iconForTool(id: String) = when (id) {
    "emoji" -> Icons.Default.EmojiEmotions
    "clipboard" -> Icons.Default.ContentPaste
    "paste" -> Icons.Default.ContentPasteGo
    "numbers" -> Icons.Default.Numbers
    "language" -> Icons.Default.Language
    "settings" -> Icons.Default.Settings
    "share" -> Icons.Default.Share
    "undo" -> Icons.AutoMirrored.Filled.Undo
    "redo" -> Icons.AutoMirrored.Filled.Redo
    "select_all" -> Icons.Default.SelectAll
    "copy" -> Icons.Default.ContentCopy
    "cut" -> Icons.Default.ContentCut
    "select_word" -> Icons.Default.TextFields
    "word_left" -> Icons.Default.KeyboardDoubleArrowLeft
    "word_right" -> Icons.Default.KeyboardDoubleArrowRight
    "line_start" -> Icons.Default.FirstPage
    "line_end" -> Icons.Default.LastPage
    "delete_word" -> Icons.AutoMirrored.Outlined.Backspace
    "hide_keyboard" -> Icons.Default.KeyboardHide
    // DRS v1.0.8: the new unified tools.
    "theme_cycle" -> Icons.Default.Palette
    "insert_date_time" -> Icons.Default.Schedule
    "text_start" -> Icons.Default.VerticalAlignTop
    "text_end" -> Icons.Default.VerticalAlignBottom
    // DRS v1.1.0: one-handed window toggle + number-row toggle.
    "one_handed" -> Icons.Default.CloseFullscreen
    "number_row" -> Icons.Default.Dialpad
    // DRS v1.2.0: incognito + autocorrect toggles.
    "incognito" -> Icons.Default.VisibilityOff
    "autocorrect" -> Icons.Default.Spellcheck
    // DRS v1.3.0: clipboard clear + voice input.
    "clipboard_clear" -> Icons.Default.DeleteSweep
    "voice_input" -> Icons.Default.Mic
    // DRS v1.4.0: floating window + smartbar visibility toggles.
    "floating_mode" -> Icons.Default.PictureInPictureAlt
    "smartbar_toggle" -> Icons.Default.ViewAgenda
    // DRS v1.5.0: history wipe + next language + resize mode.
    "clipboard_history_clear" -> Icons.Default.History
    "next_language" -> Icons.Default.Translate
    "resize_mode" -> Icons.Default.OpenInFull
    // DRS v1.6.0: full history wipe + previous language + one-handed sides
    // + next keyboard app.
    "clipboard_full_clear" -> Icons.Default.DeleteSweep
    "prev_language" -> Icons.Default.Replay
    "one_handed_left" -> Icons.Default.West
    "one_handed_right" -> Icons.Default.East
    "next_keyboard_app" -> Icons.Default.SwapHoriz
    // DRS v1.7.0: the active-clip pin toggle.
    "clipboard_pin" -> Icons.Default.PushPin
    // DRS v1.8.0: quick actions overflow + the actions editor.
    "quick_actions" -> Icons.Default.Apps
    "actions_editor" -> Icons.Default.Tune
    // DRS v1.15.0: the three smart panels.
    "diacritics_panel" -> Icons.Default.TextFormat
    "smart_symbols" -> Icons.Default.Functions
    "arabic_letters" -> Icons.Default.Abc
    // DRS v1.19.0: the split/merge keyboard toggles.
    "split_keyboard" -> Icons.Default.Splitscreen
    "merge_keyboard" -> Icons.Default.MergeType
    else -> Icons.Default.Build
}

@Composable
fun DrsUnifiedStrip(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val keyboardManager by context.keyboardManager()

    // DRS v2.2.1: the strip finally speaks — haptics/sound fire at the
    // interaction site (same convention as the keyboard keys and the
    // quick actions). The buttons dispatch through the input event
    // dispatcher, which owns NO feedback, so every button was silent
    // before this round.
    val feedback = LocalInputFeedbackController.current

    // DRS v2.2.2 «الشريطان الصادقان سياقيًا»: the ten slots dispatch raw
    // KeyCodes with NO context gate — a paste slot on an empty clipboard,
    // a copy/cut slot with no selection and a language slot with a single
    // subtype all end in a silent no-op, and the strip 2 quick actions
    // beside us already refuse such lies through evaluateEnabled. The
    // strip joins the same single source of truth: the LIVE smartbar
    // evaluator (the same instance Smartbar.kt hands QuickActionButton).
    val evaluator by keyboardManager.activeSmartbarEvaluator.collectAsState()
    // DRS v2.2.2: the motion-respect gate — the system «remove animations»
    // accessibility switch snaps every pulse/dot below to its final target
    // (same durations contract, zero animation time).
    val motionEnabled = rememberDrsMotionEnabled()

    val drsState by DrsStore.state.collectAsState()
    val contextMode by DrsRuntimeState.contextMode.collectAsState()

    // DRS p7 (E3-3): ObservableKeyboardState IS a StateFlow<KeyboardState> —
    // the flags below used to be read as plain fields (no Compose
    // subscription), so the incognito/CTRL/ALT/FN dots and the text-tools
    // toggle showed STALE state until an unrelated recomposition. Reading
    // them from this subscribed snapshot fixes the whole family.
    val activeState by keyboardManager.activeState.collectAsState()

    val isHybrid = drsState.userPath == DrsUserPath.HYBRID.name

    // DRS v1.8.0: the tasks bar (شريط المهام) sits ABOVE the suggestions
    // strip for ALL three user systems — العادي والتقني وكلاهما. Exactly
    // two gates: the persisted master switch (unifiedStripEnabled, on by
    // default, toggled from the tools drawer) and the password-field
    // guard.
    val visible = drsState.unifiedStripEnabled &&
        contextMode != DrsContextMode.PASSWORD
    if (!visible) return

    // DRS v1.8.0: real on/off state of the toggle tools, read from the
    // same engine sources that own them (IME flags, jetpref settings and
    // the window controller) so the active dot never lies.
    val windowController = LocalWindowController.current
    val windowSpec by windowController.activeWindowSpec.collectAsState()
    val prefs by DrsPreferenceStore
    val suggestionEnabled by prefs.suggestion.enabled.collectAsState()
    val numberRowEnabled by prefs.keyboard.numberRow.collectAsState()
    val smartbarEnabled by prefs.smartbar.enabled.collectAsState()
    val toggleStates = remember(
        activeState.isIncognitoMode,
        suggestionEnabled, numberRowEnabled, smartbarEnabled,
        windowSpec,
        activeState.inputCtrlState,
        activeState.inputAltState,
        activeState.inputFnState,
    ) {
        DrsUnifiedTools.ToggleStates(
            incognito = activeState.isIncognitoMode,
            autocorrect = suggestionEnabled,
            numberRow = numberRowEnabled,
            smartbarVisible = smartbarEnabled,
            // DRS v2.1.1: افحص التخصيص لا خصائصه — القديم كان يخلط التسلسلين
            // المغلقين (ImeWindowProps مقابل ImeWindowSpec) فصار الفحص خطأً
            // ثابتًا بالبرهان، ومُجمِّع 2.4.20 يرفضه IMPOSSIBLE_IS_CHECK_ERROR،
            // وكانت نقطة floating_mode عاجزة عن الإضاءة أبدًا — بنفس اتفاقية
            // ImeWindow/ImeSystemUi/ImeWindowEditorHandles كلها.
            floatingWindow = windowSpec is ImeWindowSpec.Floating,
            // DRS v1.22.0: the revived CTRL/ALT latches show the truth —
            // the tile dot reads the same latch state the input pipeline
            // arms and consumes.
            ctrlArmed = activeState.inputCtrlState.isArmed,
            altArmed = activeState.inputAltState.isArmed,
            // DRS v1.23.0: FN completes the modifier family.
            fnArmed = activeState.inputFnState.isArmed,
        )
    }
    // Accent for the active dot, from the active user system's palette
    // (same identity the settings screens use).
    val systemSpec = DrsSystems.specOfName(drsState.userPath)
    val accentColor = if (isSystemInDarkTheme()) systemSpec.accentNight else systemSpec.accent

    val view = DrsUnifiedTools.viewForSystem(drsState.userPath, drsState.hybridViewMode)
    val tools = remember(
        view, drsState.unifiedToolOrder, drsState.hiddenUnifiedTools,
        drsState.pinnedUnifiedTools, drsState.unifiedToolViews,
    ) {
        DrsUnifiedTools.resolveFor(
            view = view,
            hidden = drsState.hiddenUnifiedTools,
            pinned = drsState.pinnedUnifiedTools,
            order = drsState.unifiedToolOrder,
            viewOverrides = drsState.unifiedToolViews,
        )
    }

    // DRS v1.16.0: «شريط المهام ثابت يعرض 10 مهام فقط» — the FIXED
    // slots. The user's pins lead in pin order, the visible defaults
    // fill the next slots, and the resolved visible catalogue pads the
    // tail so the bar always renders exactly ten tasks (or less only
    // when the whole catalogue is smaller). Hidden tools never occupy a
    // slot, honoring the manager's show/hide switches.
    val hiddenSet = remember(drsState.hiddenUnifiedTools) {
        drsState.hiddenUnifiedTools.toHashSet()
    }
    val visiblePins = drsState.pinnedUnifiedTools.filter { it !in hiddenSet }
    val visibleDefaults = DrsUnifiedTools.ALL
        .filter { it.defaultPinned && it.id !in hiddenSet }
        .filter { DrsUnifiedTools.isVisibleIn(it, view, drsState.unifiedToolViews[it.id]) }
        .map { it.id }
    val slots = remember(view, visiblePins, visibleDefaults, tools) {
        DrsUnifiedTools.fixedSlots(visiblePins, visibleDefaults, tools.map { it.id })
    }

    // Advanced technical keys ride along in the advanced and dual levels,
    // preserving the user's persisted arrangement from the toolbar editor.
    // They are KEYS, not tasks — the ten task slots stay ten either way;
    // the bar only scrolls when this tail exists.
    // DRS (D4a): the profile's techStripEnabled toggle now REALLY gates
    // the tail (it used to be a dead switch that only the suggestion
    // engine and the system screens touched). Default profiles for the
    // technical/hybrid systems enable it, so their behavior is unchanged;
    // a normal-system user on an advanced/dual level opts in via the
    // control-center toggle, which now does what it claims.
    val techProfileEnabled = DrsProfileManager.activeProfile(drsState)?.techStripEnabled == true
    val techKeys = if (view != DrsHybridViewMode.SIMPLE && techProfileEnabled) {
        remember(drsState.techToolbarKeys) {
            DrsTechToolbarKeys.resolve(drsState.techToolbarKeys)
        }
    } else {
        emptyList()
    }

    val isTextToolsOpen = activeState.imeUiMode == ImeUiMode.TEXT_TOOLS
    // DRS v1.0.8: the strip tracks the real Smartbar height (which follows
    // the keyboard height scale) instead of a hardcoded 40.dp, so growing
    // the keyboard grows the strip consistently.
    val stripHeight = DrsImeSizing.smartbarHeight
    // DRS M2.3 — the Design-2030 corner shape: the persisted radius flows
    // through DrsSmartbarShape's sanitizer and physically CLIPS the strip,
    // so the slider in the control center really rounds the bar.
    val cornerRadiusRaw by prefs.smartbar.drsCornerRadius.collectAsState()
    val cornerShape = RoundedCornerShape(DrsSmartbarShape.radiusDp(cornerRadiusRaw).dp)
    val barModifier = if (techKeys.isEmpty()) {
        modifier
            .fillMaxWidth()
            .height(stripHeight)
            .clip(cornerShape)
    } else {
        modifier
            .fillMaxWidth()
            .height(stripHeight)
            .clip(cornerShape)
            .horizontalScroll(rememberScrollState())
    }
    SnyggRow(
        DrsImeUi.Smartbar.elementName,
        modifier = barModifier,
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
    ) {
        // 0) the side-pull handle (زر السحب الجانبي). Always first so the
        //    pinned-tools drawer stays reachable — the drawer is where
        //    tasks are reordered (up/down), pinned and unpinned.
        //    DRS v2.2.1: pulse + feedback — the handle used to be the
        //    only silent control on the bar (no KeyCode to dispatch).
        val handleInteraction = remember { MutableInteractionSource() }
        val handlePressed by handleInteraction.collectIsPressedAsState()
        val handlePulse by animateFloatAsState(
            targetValue = DrsMotion.scaleFor(handlePressed),
            // DRS v2.2.2: snaps to 0ms under the system «remove animations»
            // switch — same targets, zero animation time.
            animationSpec = tween(durationMillis = DrsMotion.durationOrSnap(DrsMotion.durationFor(handlePressed), motionEnabled)),
            label = "drsStripHandlePulse",
        )
        SnyggIconButton(
            elementName = DrsImeUi.SmartbarActionKey.elementName,
            onClick = {
                feedback.keyPress()
                // Writes go through the OBSERVABLE state instance (mutating a
                // flow snapshot would write into a discarded bitfield); only
                // composition READS use the subscribed snapshot (E3-3).
                val state = keyboardManager.activeState
                DrsRuntimeState.closeStripSlotEditor()
                state.isActionsOverflowVisible = false
                state.isToolsDrawerVisible = !state.isToolsDrawerVisible
            },
            interactionSource = handleInteraction,
            modifier = Modifier
                .sizeIn(minWidth = 40.dp)
                .height(stripHeight)
                .graphicsLayer {
                    scaleX = handlePulse
                    scaleY = handlePulse
                },
        ) {
            SnyggIcon(
                imageVector = Icons.Default.DragHandle,
                // DRS a11y/i18n/ux (r0-I): the side-pull handle is icon-only.
                contentDescription = stringRes(R.string.drs__unified__a11y_open_drawer),
            )
        }

        // 1) the ten FIXED task slots — every slot dispatches its real
        //    KeyCode, and a LONG-PRESS opens the slot editor (change the
        //    task occupying that slot, «إمكانية تغييرها»).
        //    DRS v2.2.1: every slot lives — the same press pulse the keys
        //    use, an honest halo behind ACTIVE toggles, a dot that grows
        //    in and shrinks out instead of popping, and feedback on both
        //    click (keyPress) and long-press (keyLongPress, slot editor).
        slots.forEachIndexed { index, toolId ->
            val tool = DrsUnifiedTools.byId(toolId) ?: return@forEachIndexed
            val isTextTools = tool.code == KeyCode.IME_UI_MODE_TEXT_TOOLS
            val toggleOn = DrsUnifiedTools.toggleStateOf(tool.id, toggleStates)
            // DRS v2.2.2 «الخانة الصادقة سياقيًا»: the same single-source
            // truth QuickActionButton uses. Toggles, settings and navigation
            // tools evaluate true (always meaningful); clipboard/language
            // tools evaluate the REAL editor context. The evaluator is the
            // live smartbar one — recomputed by KeyboardManager as the
            // editor/prefs state moves, so the dim tracks reality.
            val slotData = TextKeyData(type = tool.type, code = tool.code, label = tool.id)
            val contextEnabled = evaluator.evaluateEnabled(slotData)
            val slotInteraction = remember(toolId) { MutableInteractionSource() }
            val slotPressed by slotInteraction.collectIsPressedAsState()
            val slotPulse by animateFloatAsState(
                targetValue = DrsMotion.scaleFor(slotPressed),
                animationSpec = tween(durationMillis = DrsMotion.durationOrSnap(DrsMotion.durationFor(slotPressed), motionEnabled)),
                label = "drsStripSlotPulse",
            )
            // The toggle dot breathes: it GROWS into place when the tool
            // turns on and shrinks away when it turns off (DrsMotion dot
            // contract). Draw-only — the 6.dp slot it occupies never
            // changes, so the row geometry is untouched while it animates.
            val dotScale by animateFloatAsState(
                targetValue = DrsMotion.dotScaleFor(toggleOn == true),
                animationSpec = tween(durationMillis = DrsMotion.durationOrSnap(DrsMotion.dotDurationFor(toggleOn == true), motionEnabled)),
                label = "drsStripSlotDot",
            )
            val slotModifier = if (techKeys.isEmpty()) {
                Modifier.weight(1f).height(stripHeight)
            } else {
                Modifier.sizeIn(minWidth = 38.dp).height(stripHeight)
            }
            SnyggIconButton(
                elementName = DrsImeUi.SmartbarActionKey.elementName,
                onClick = {
                    // DRS v2.2.2: the honest refusal — when the context says
                    // the action would be a silent no-op, the slot says so
                    // visually (dimmed) and semantically (disabled()), and
                    // the tap does NOTHING: no fake haptic, no usage record,
                    // no dispatch. The long-press slot editor below stays
                    // alive either way — re-configuring a slot is always
                    // meaningful, even when its action is not.
                    if (!contextEnabled) return@SnyggIconButton
                    feedback.keyPress()
                    keyboardManager.inputEventDispatcher.sendDownUp(
                        TextKeyData(type = tool.type, code = tool.code, label = tool.id),
                    )
                    if (isTextTools) {
                        // DRS p7 (E3-5): the dispatched IME_UI_MODE_TEXT_TOOLS code is
                        // auto-counted by KeyboardManager as a TOOL use, but KeyboardManager
                        // never records techToolUses/coins itself — this manual call is
                        // therefore not a double count (same convention as DrsTextToolsPanel).
                        DrsAdaptationEngine.recordTechToolUse()
                    } else if (tool.code !in SmartToolCodes) {
                        // DRS p7 (E3-5): KeyboardManager.onInputKeyUpBody already records
                        // every SmartToolCodes dispatch — recording here too inflated the
                        // usage stats ~2x. Only the three catalogue codes the engine does
                        // not cover (VIEW_NUMERIC / VIEW_SYMBOLS / DELETE_WORD) are
                        // counted manually now.
                        DrsAdaptationEngine.recordToolUse(tool.code)
                    }
                },
                onLongClick = {
                    feedback.keyLongPress()
                    keyboardManager.activeState.isToolsDrawerVisible = false
                    keyboardManager.activeState.isActionsOverflowVisible = false
                    DrsRuntimeState.openStripSlotEditor(index)
                },
                interactionSource = slotInteraction,
                modifier = slotModifier
                    .semantics {
                        role = Role.Button
                        // DRS v2.2.2: TalkBack hears the truth too — the
                        // gated slot is announced disabled, and its label
                        // carries the localized reason suffix.
                        if (!contextEnabled) disabled()
                    }
                    .graphicsLayer {
                        scaleX = slotPulse
                        scaleY = slotPulse
                        alpha = if (contextEnabled) 1f else DrsMotion.DISABLED_SLOT_ALPHA
                    },
            ) {
                // DRS v2.2.2: the a11y label carries the honest reason —
                // computed once per slot composition (stringRes needs
                // composition, the gate value is already in scope).
                val slotLabel = if (contextEnabled) toolTitle(tool.id) else {
                    toolTitle(tool.id) + " — " + stringRes(R.string.drs__unified__a11y_unavailable)
                }
                androidx.compose.foundation.layout.Box(
                    contentAlignment = androidx.compose.ui.Alignment.Center,
                ) {
                    // DRS v2.2.1: هالة التفعيل الصادقة — a soft accent disc
                    // behind the icon of an ACTIVE toggle, same accent the
                    // dot uses, so «مفعّل» reads at a glance from arm's
                    // length. matchParentSize keeps it behind the icon and
                    // OUT of the layout math (zero geometry change).
                    if (toggleOn == true) {
                        androidx.compose.foundation.layout.Box(
                            modifier = Modifier
                                .matchParentSize()
                                .clip(androidx.compose.foundation.shape.CircleShape)
                                .background(accentColor.copy(alpha = 0.14f)),
                        )
                    }
                    when {
                        isTextTools -> SnyggIcon(
                            imageVector = if (isTextToolsOpen) Icons.Default.Close else Icons.Default.Build,
                            // DRS a11y/i18n/ux (r0-I): the tile is icon-only.
                            // DRS v2.2.2: the label carries the honest
                            // unavailable suffix when the gate is closed.
                            contentDescription = slotLabel,
                        )
                        tool.id == "symbols" -> Text(text = "&#", fontSize = 14.sp, maxLines = 1)
                        else -> SnyggIcon(
                            imageVector = iconForTool(tool.id),
                            // DRS a11y/i18n/ux (r0-I): the tile is icon-only.
                            // DRS v2.2.2: the label carries the honest
                            // unavailable suffix when the gate is closed.
                            contentDescription = slotLabel,
                        )
                    }
                    // DRS v1.8.0: the real on/off state of toggle tools
                    // (incognito/autocorrect/number row/smartbar/floating)
                    // as a small accent dot — the tile shows the truth.
                    // DRS v2.2.1: the dot now BREATHES (grows in / shrinks
                    // out via DrsMotion's dot contract) instead of popping
                    // in and out of existence. Always composed, draw-only.
                    androidx.compose.foundation.layout.Box(
                        modifier = Modifier
                            .align(androidx.compose.ui.Alignment.TopEnd)
                            .padding(top = 6.dp, end = 5.dp)
                            .size(6.dp)
                            .graphicsLayer {
                                scaleX = dotScale
                                scaleY = dotScale
                            }
                            .clip(androidx.compose.foundation.shape.CircleShape)
                            .background(accentColor),
                    )
                }
            }
        }

        // 2) the user's technical keys (advanced/dual levels only).
        techKeys.forEach { key ->
            // DRS v1.22.0: the modifier latch keys show their live armed
            // state the same way the toggle tools do — the tile shows the
            // truth, LATCHED and LOCKED alike.
            val latchOn = when (key.id) {
                "ctrl" -> toggleStates.ctrlArmed
                "alt" -> toggleStates.altArmed
                else -> null
            }
            // DRS v2.2.1: the same living treatment as the task slots —
            // pulse, honest halo on armed latches, breathing dot, and
            // feedback (the tail keys used to be silent too).
            val keyInteraction = remember(key.id) { MutableInteractionSource() }
            val keyPressed by keyInteraction.collectIsPressedAsState()
            val keyPulse by animateFloatAsState(
                targetValue = DrsMotion.scaleFor(keyPressed),
                animationSpec = tween(durationMillis = DrsMotion.durationOrSnap(DrsMotion.durationFor(keyPressed), motionEnabled)),
                label = "drsStripTechPulse",
            )
            val dotScale by animateFloatAsState(
                targetValue = DrsMotion.dotScaleFor(latchOn == true),
                animationSpec = tween(durationMillis = DrsMotion.durationOrSnap(DrsMotion.dotDurationFor(latchOn == true), motionEnabled)),
                label = "drsStripTechDot",
            )
            SnyggIconButton(
                elementName = DrsImeUi.SmartbarActionKey.elementName,
                onClick = {
                    feedback.keyPress()
                    keyboardManager.inputEventDispatcher.sendDownUp(
                        TextKeyData(type = key.type, code = key.code, label = key.label),
                    )
                    // DRS p7 (E3-5): plain characters typed from the tech tail
                    // used to award tech-tool coins — ~80x the earn rate of
                    // typing the same character on the main keyboard (farmable
                    // asymmetry). Only real tech keys (navigation / function /
                    // modifier) count now.
                    if (key.type != KeyType.CHARACTER) {
                        DrsAdaptationEngine.recordTechToolUse()
                    }
                },
                interactionSource = keyInteraction,
                modifier = Modifier
                    .sizeIn(minWidth = 38.dp)
                    .height(stripHeight)
                    .graphicsLayer {
                        scaleX = keyPulse
                        scaleY = keyPulse
                    },
            ) {
                androidx.compose.foundation.layout.Box(
                    contentAlignment = androidx.compose.ui.Alignment.Center,
                ) {
                    if (latchOn == true) {
                        androidx.compose.foundation.layout.Box(
                            modifier = Modifier
                                .matchParentSize()
                                .clip(androidx.compose.foundation.shape.CircleShape)
                                .background(accentColor.copy(alpha = 0.14f)),
                        )
                    }
                    Text(
                        text = key.label,
                        fontSize = 14.sp,
                        maxLines = 1,
                    )
                    androidx.compose.foundation.layout.Box(
                        modifier = Modifier
                            .align(androidx.compose.ui.Alignment.TopEnd)
                            .padding(top = 6.dp, end = 5.dp)
                            .size(6.dp)
                            .graphicsLayer {
                                scaleX = dotScale
                                scaleY = dotScale
                            }
                            .clip(androidx.compose.foundation.shape.CircleShape)
                            .background(accentColor),
                    )
                }
            }
        }
    }
}

