/*
 * Copyright (C) 2021-2025 The DRS Smart Keyboard Project
 * Copyright (C) 2025 DRS Smart Keyboard contributors
 */

package com.drs.smartkeyboard.drs

import com.drs.smartkeyboard.ime.text.key.KeyCode
import com.drs.smartkeyboard.ime.text.key.KeyType

/**
 * DRS v1.0.7 — نظام «كلاهما» الموحد (Unified Dual System).
 *
 * This is the pure-logic core of the unified system: one catalogue of REAL
 * tools (every entry maps to a KeyCode the keyboard engine actually
 * handles), three display levels (simple / advanced / dual), and a scope
 * model (basic / advanced / shared) that resolves conflicts between the
 * normal and technical experiences instead of duplicating them.
 *
 * Nothing in this file touches Android UI or Context, so the whole
 * resolution layer is covered by JVM unit tests. Icon resources and
 * strings are resolved in the UI layer by tool id.
 */

/**
 * The three display levels of the unified system. The level only changes
 * WHAT IS VISIBLE - it never deletes settings, profiles, shortcuts,
 * clipboard data or themes, so switching is always lossless.
 */
enum class DrsHybridViewMode {
    /** الوضع البسيط: basic tools and essential options only. */
    SIMPLE,

    /** الوضع المتقدم: technical tools and expert controls. */
    ADVANCED,

    /** الوضع المزدوج: both worlds in the same session. */
    DUAL;

    companion object {
        /** Safe parse of a persisted name; unknown values fall back to DUAL. */
        fun fromNameSafe(name: String?): DrsHybridViewMode {
            return entries.firstOrNull { it.name == name } ?: DUAL
        }
    }
}

/**
 * Scope of a setting or tool inside the unified system. Scopes prevent
 * conflicts: a basic setting stays available to both levels, an advanced
 * setting is hidden until the user enables Advanced Controls, and a shared
 * setting belongs to the whole system rather than to one level.
 */
enum class DrsSettingScope {
    /** إعداد أساسي — shows for everyone. */
    BASIC,

    /** إعداد متقدم — hidden until Advanced Controls is on. */
    ADVANCED,

    /** إعداد مشترك — belongs to the whole system. */
    SHARED,
}

/** Which display level(s) a tool appears in. */
enum class DrsToolView {
    /** يظهر في الوضع البسيط فقط. */
    NORMAL,

    /** يظهر في الوضع المتقدم فقط. */
    TECHNICAL,

    /** يظهر في كلا الوضعين (والمزدوج). */
    BOTH,
}

/** Display group of a tool inside the unified tools manager. */
enum class DrsToolGroup {
    /** أدوات سريعة: panels, emoji, clipboard, language… */
    TOOLS,

    /** تحرير: undo/redo, copy/cut/paste, selection. */
    EDITING,

    /** المؤشر: word/line cursor movement. */
    CURSOR,
}

/**
 * One entry of the unified tool catalogue. [code] is a KeyCode the
 * keyboard engine already handles (see KeyboardManager), so every tool
 * here performs a REAL action on the live keyboard - there are no
 * placeholder entries. UI-only attributes (icon, localized label) are
 * resolved from [id] in the UI layer.
 */
data class DrsUnifiedTool(
    /** Stable id persisted in DrsState customizations. */
    val id: String,

    /** KeyCode dispatched when the tool is tapped. */
    val code: Int,

    /** Key type of the dispatched event. */
    val type: KeyType,

    /** Scope inside the unified system. */
    val scope: DrsSettingScope,

    /** Default visibility across display levels. */
    val defaultView: DrsToolView,

    /** Default pin state (pinned tools float to the strip head). */
    val defaultPinned: Boolean = false,

    /** Group used by the tools manager. */
    val group: DrsToolGroup,
)

/**
 * The unified tool catalogue. Keys come from the same catalogues the
 * engine handles (TextKeyData/SwipeAction handling paths), so wiring a
 * tool into the strip is exactly as real as the keys of the physical
 * layout itself.
 */
object DrsUnifiedTools {

    /**
     * DRS v1.15.0: the hard cap of the tasks bar's pinned head — «المهام
     * المثبتة 10 فقط». The user's explicit pins always win (in pin order)
     * and the default pins fill the remaining slots; anything beyond the
     * cap stays in the catalogue and in the strip tail, never lost.
     */
    const val MAX_PINNED_TOOLS = 10

    val EMOJI = DrsUnifiedTool(
        id = "emoji",
        code = KeyCode.IME_UI_MODE_MEDIA,
        type = KeyType.SYSTEM_GUI,
        scope = DrsSettingScope.BASIC,
        defaultView = DrsToolView.BOTH,
        defaultPinned = true,
        group = DrsToolGroup.TOOLS,
    )

    val CLIPBOARD = DrsUnifiedTool(
        id = "clipboard",
        code = KeyCode.IME_UI_MODE_CLIPBOARD,
        type = KeyType.SYSTEM_GUI,
        scope = DrsSettingScope.BASIC,
        defaultView = DrsToolView.BOTH,
        defaultPinned = true,
        group = DrsToolGroup.TOOLS,
    )

    val NUMBERS = DrsUnifiedTool(
        id = "numbers",
        code = KeyCode.VIEW_NUMERIC,
        type = KeyType.SYSTEM_GUI,
        scope = DrsSettingScope.BASIC,
        defaultView = DrsToolView.BOTH,
        group = DrsToolGroup.TOOLS,
    )

    val SYMBOLS = DrsUnifiedTool(
        id = "symbols",
        code = KeyCode.VIEW_SYMBOLS,
        type = KeyType.SYSTEM_GUI,
        scope = DrsSettingScope.BASIC,
        defaultView = DrsToolView.BOTH,
        group = DrsToolGroup.TOOLS,
    )

    val LANGUAGE = DrsUnifiedTool(
        id = "language",
        // DRS v1.5.0 fix: this tool pointed at IME_SUBTYPE_PICKER (-224),
        // a code with NO handler — pressing it silently logged "unknown
        // key". SHOW_SUBTYPE_PICKER is the real in-keyboard subtype picker
        // path (KeyboardManager sets isSubtypeSelectionVisible).
        code = KeyCode.SHOW_SUBTYPE_PICKER,
        type = KeyType.FUNCTION,
        scope = DrsSettingScope.BASIC,
        defaultView = DrsToolView.BOTH,
        group = DrsToolGroup.TOOLS,
    )

    val SETTINGS = DrsUnifiedTool(
        id = "settings",
        code = KeyCode.SETTINGS,
        type = KeyType.FUNCTION,
        scope = DrsSettingScope.BASIC,
        defaultView = DrsToolView.NORMAL,
        group = DrsToolGroup.TOOLS,
    )

    val PASTE = DrsUnifiedTool(
        id = "paste",
        code = KeyCode.CLIPBOARD_PASTE,
        type = KeyType.SYSTEM_GUI,
        scope = DrsSettingScope.BASIC,
        defaultView = DrsToolView.NORMAL,
        group = DrsToolGroup.EDITING,
    )

    val SHARE = DrsUnifiedTool(
        id = "share",
        code = KeyCode.CLIPBOARD_SHARE,
        type = KeyType.SYSTEM_GUI,
        scope = DrsSettingScope.BASIC,
        defaultView = DrsToolView.NORMAL,
        group = DrsToolGroup.TOOLS,
    )

    val TEXT_TOOLS = DrsUnifiedTool(
        id = "text_tools",
        code = KeyCode.IME_UI_MODE_TEXT_TOOLS,
        type = KeyType.SYSTEM_GUI,
        scope = DrsSettingScope.SHARED,
        defaultView = DrsToolView.BOTH,
        defaultPinned = true,
        group = DrsToolGroup.TOOLS,
    )

    val UNDO = DrsUnifiedTool(
        id = "undo",
        code = KeyCode.UNDO,
        type = KeyType.SYSTEM_GUI,
        scope = DrsSettingScope.SHARED,
        defaultView = DrsToolView.BOTH,
        group = DrsToolGroup.EDITING,
    )

    val REDO = DrsUnifiedTool(
        id = "redo",
        code = KeyCode.REDO,
        type = KeyType.SYSTEM_GUI,
        scope = DrsSettingScope.SHARED,
        defaultView = DrsToolView.BOTH,
        group = DrsToolGroup.EDITING,
    )

    val SELECT_ALL = DrsUnifiedTool(
        id = "select_all",
        code = KeyCode.CLIPBOARD_SELECT_ALL,
        type = KeyType.SYSTEM_GUI,
        scope = DrsSettingScope.SHARED,
        defaultView = DrsToolView.TECHNICAL,
        group = DrsToolGroup.EDITING,
    )

    val COPY = DrsUnifiedTool(
        id = "copy",
        code = KeyCode.CLIPBOARD_COPY,
        type = KeyType.SYSTEM_GUI,
        scope = DrsSettingScope.SHARED,
        defaultView = DrsToolView.TECHNICAL,
        group = DrsToolGroup.EDITING,
    )

    val CUT = DrsUnifiedTool(
        id = "cut",
        code = KeyCode.CLIPBOARD_CUT,
        type = KeyType.SYSTEM_GUI,
        scope = DrsSettingScope.SHARED,
        defaultView = DrsToolView.TECHNICAL,
        group = DrsToolGroup.EDITING,
    )

    val SELECT_WORD = DrsUnifiedTool(
        id = "select_word",
        code = KeyCode.CLIPBOARD_SELECT,
        type = KeyType.SYSTEM_GUI,
        scope = DrsSettingScope.SHARED,
        defaultView = DrsToolView.TECHNICAL,
        group = DrsToolGroup.EDITING,
    )

    val WORD_LEFT = DrsUnifiedTool(
        id = "word_left",
        code = KeyCode.MOVE_WORD_LEFT,
        type = KeyType.NAVIGATION,
        scope = DrsSettingScope.SHARED,
        defaultView = DrsToolView.TECHNICAL,
        group = DrsToolGroup.CURSOR,
    )

    val WORD_RIGHT = DrsUnifiedTool(
        id = "word_right",
        code = KeyCode.MOVE_WORD_RIGHT,
        type = KeyType.NAVIGATION,
        scope = DrsSettingScope.SHARED,
        defaultView = DrsToolView.TECHNICAL,
        group = DrsToolGroup.CURSOR,
    )

    val LINE_START = DrsUnifiedTool(
        id = "line_start",
        code = KeyCode.MOVE_START_OF_LINE,
        type = KeyType.NAVIGATION,
        scope = DrsSettingScope.SHARED,
        defaultView = DrsToolView.TECHNICAL,
        group = DrsToolGroup.CURSOR,
    )

    val LINE_END = DrsUnifiedTool(
        id = "line_end",
        code = KeyCode.MOVE_END_OF_LINE,
        type = KeyType.NAVIGATION,
        scope = DrsSettingScope.SHARED,
        defaultView = DrsToolView.TECHNICAL,
        group = DrsToolGroup.CURSOR,
    )

    val DELETE_WORD = DrsUnifiedTool(
        id = "delete_word",
        code = KeyCode.DELETE_WORD,
        type = KeyType.ENTER_EDITING,
        scope = DrsSettingScope.SHARED,
        defaultView = DrsToolView.TECHNICAL,
        group = DrsToolGroup.EDITING,
    )

    val HIDE_KEYBOARD = DrsUnifiedTool(
        id = "hide_keyboard",
        code = KeyCode.IME_HIDE_UI,
        type = KeyType.FUNCTION,
        scope = DrsSettingScope.BASIC,
        defaultView = DrsToolView.BOTH,
        group = DrsToolGroup.TOOLS,
    )

    /**
     * DRS v1.0.8: cycles the keyboard theme of the currently effective
     * day/night slot (real pref write — the live keyboard re-styles).
     */
    val THEME_CYCLE = DrsUnifiedTool(
        id = "theme_cycle",
        code = KeyCode.THEME_CYCLE,
        type = KeyType.FUNCTION,
        scope = DrsSettingScope.BASIC,
        defaultView = DrsToolView.BOTH,
        group = DrsToolGroup.TOOLS,
    )

    /**
     * DRS v1.0.8: commits the current date & time formatted with the
     * active subtype's locale through the normal commitText path.
     */
    val INSERT_DATE_TIME = DrsUnifiedTool(
        id = "insert_date_time",
        code = KeyCode.INSERT_DATE_TIME,
        type = KeyType.FUNCTION,
        scope = DrsSettingScope.BASIC,
        defaultView = DrsToolView.BOTH,
        group = DrsToolGroup.TOOLS,
    )

    /** DRS v1.0.8: moves the cursor to the very start of the whole text. */
    val TEXT_START = DrsUnifiedTool(
        id = "text_start",
        code = KeyCode.MOVE_START_OF_PAGE,
        type = KeyType.NAVIGATION,
        scope = DrsSettingScope.SHARED,
        defaultView = DrsToolView.TECHNICAL,
        group = DrsToolGroup.CURSOR,
    )

    /** DRS v1.0.8: moves the cursor to the very end of the whole text. */
    val TEXT_END = DrsUnifiedTool(
        id = "text_end",
        code = KeyCode.MOVE_END_OF_PAGE,
        type = KeyType.NAVIGATION,
        scope = DrsSettingScope.SHARED,
        defaultView = DrsToolView.TECHNICAL,
        group = DrsToolGroup.CURSOR,
    )

    /**
     * DRS v1.1.0: toggles the compact one-handed window mode through the
     * real window-controller action (same path as the existing on-panel
     * one-handed controls and its KeyCode dispatch).
     */
    val ONE_HANDED = DrsUnifiedTool(
        id = "one_handed",
        code = KeyCode.TOGGLE_COMPACT_LAYOUT,
        type = KeyType.SYSTEM_GUI,
        scope = DrsSettingScope.BASIC,
        defaultView = DrsToolView.BOTH,
        group = DrsToolGroup.TOOLS,
    )

    /**
     * DRS v1.1.0: toggles the top number row on the character layout —
     * a real pref write; the LayoutManager recomputes the layout through
     * the existing pref collector.
     */
    val NUMBER_ROW = DrsUnifiedTool(
        id = "number_row",
        code = KeyCode.TOGGLE_NUMBER_ROW,
        type = KeyType.FUNCTION,
        scope = DrsSettingScope.BASIC,
        defaultView = DrsToolView.BOTH,
        group = DrsToolGroup.TOOLS,
    )

    /**
     * DRS v1.2.0: toggles incognito typing through the real engine action
     * (same KeyCode the existing quick actions use — handled in
     * KeyboardManager.handleToggleIncognitoMode).
     */
    val INCOGNITO = DrsUnifiedTool(
        id = "incognito",
        code = KeyCode.TOGGLE_INCOGNITO_MODE,
        type = KeyType.FUNCTION,
        scope = DrsSettingScope.BASIC,
        defaultView = DrsToolView.BOTH,
        group = DrsToolGroup.TOOLS,
    )

    /**
     * DRS v1.2.0: toggles auto-correction through the real engine action
     * (same KeyCode the existing quick actions use — handled in
     * KeyboardManager.handleToggleAutocorrect).
     */
    val AUTOCORRECT = DrsUnifiedTool(
        id = "autocorrect",
        code = KeyCode.TOGGLE_AUTOCORRECT,
        type = KeyType.FUNCTION,
        scope = DrsSettingScope.BASIC,
        defaultView = DrsToolView.BOTH,
        group = DrsToolGroup.TOOLS,
    )

    /**
     * DRS v1.3.0: clears the primary clipboard through the real engine
     * action (KeyCode.CLIPBOARD_CLEAR_PRIMARY_CLIP — handled in
     * KeyboardManager with the existing history-aware path + toast).
     */
    val CLIPBOARD_CLEAR = DrsUnifiedTool(
        id = "clipboard_clear",
        code = KeyCode.CLIPBOARD_CLEAR_PRIMARY_CLIP,
        type = KeyType.FUNCTION,
        scope = DrsSettingScope.BASIC,
        defaultView = DrsToolView.BOTH,
        group = DrsToolGroup.EDITING,
    )

    /**
     * DRS v1.3.0: switches to the voice input method through the real
     * engine action (KeyCode.VOICE_INPUT — handled in KeyboardManager via
     * DrsImeService.switchToVoiceInputMethod).
     */
    val VOICE_INPUT = DrsUnifiedTool(
        id = "voice_input",
        code = KeyCode.VOICE_INPUT,
        type = KeyType.FUNCTION,
        scope = DrsSettingScope.BASIC,
        defaultView = DrsToolView.BOTH,
        group = DrsToolGroup.TOOLS,
    )

    /**
     * DRS v1.4.0: toggles the floating-window mode through the real engine
     * action (KeyCode.TOGGLE_FLOATING_WINDOW — handled in KeyboardManager
     * via windowController.actions.toggleFloatingWindow, the same path the
     * existing quick action uses).
     */
    val FLOATING_MODE = DrsUnifiedTool(
        id = "floating_mode",
        code = KeyCode.TOGGLE_FLOATING_WINDOW,
        type = KeyType.FUNCTION,
        scope = DrsSettingScope.BASIC,
        defaultView = DrsToolView.BOTH,
        group = DrsToolGroup.TOOLS,
    )

    /**
     * DRS v1.4.0: shows/hides the smart bar through the real engine action
     * (KeyCode.TOGGLE_SMARTBAR_VISIBILITY — handled in KeyboardManager on
     * the same path the swipe action uses).
     */
    val SMARTBAR_TOGGLE = DrsUnifiedTool(
        id = "smartbar_toggle",
        code = KeyCode.TOGGLE_SMARTBAR_VISIBILITY,
        type = KeyType.FUNCTION,
        scope = DrsSettingScope.BASIC,
        defaultView = DrsToolView.BOTH,
        group = DrsToolGroup.TOOLS,
    )

    /**
     * DRS v1.5.0: clears the UNPINNED clipboard history through the real
     * engine action (KeyCode.CLIPBOARD_CLEAR_HISTORY — handled in
     * KeyboardManager via clipboardManager.clearHistory). Pinned items
     * survive, so the tool is a safe one-tap history wipe.
     */
    val CLIPBOARD_HISTORY_CLEAR = DrsUnifiedTool(
        id = "clipboard_history_clear",
        code = KeyCode.CLIPBOARD_CLEAR_HISTORY,
        type = KeyType.FUNCTION,
        scope = DrsSettingScope.BASIC,
        defaultView = DrsToolView.BOTH,
        group = DrsToolGroup.EDITING,
    )

    /**
     * DRS v1.5.0: switches to the NEXT configured subtype through the real
     * engine action (KeyCode.IME_NEXT_SUBTYPE — handled in KeyboardManager
     * via subtypeManager.switchToNextSubtype), the same switch the
     * language key performs under its "next language" utility action.
     */
    val NEXT_LANGUAGE = DrsUnifiedTool(
        id = "next_language",
        code = KeyCode.IME_NEXT_SUBTYPE,
        type = KeyType.FUNCTION,
        scope = DrsSettingScope.BASIC,
        defaultView = DrsToolView.BOTH,
        group = DrsToolGroup.TOOLS,
    )

    /**
     * DRS v1.5.0: enters/leaves the window-resize mode through the real
     * engine action (KeyCode.TOGGLE_RESIZE_MODE — handled in
     * KeyboardManager via windowController.editor.toggleEnabled, the same
     * path the existing quick action uses).
     */
    val RESIZE_MODE = DrsUnifiedTool(
        id = "resize_mode",
        code = KeyCode.TOGGLE_RESIZE_MODE,
        type = KeyType.FUNCTION,
        scope = DrsSettingScope.BASIC,
        defaultView = DrsToolView.BOTH,
        group = DrsToolGroup.TOOLS,
    )

    /**
     * DRS v1.6.0: clears the FULL clipboard history (pinned items
     * included) through the real engine action
     * (KeyCode.CLIPBOARD_CLEAR_FULL_HISTORY — handled in KeyboardManager
     * via clipboardManager.clearFullHistory). The destructive sibling of
     * [CLIPBOARD_HISTORY_CLEAR], which keeps pinned entries.
     */
    val CLIPBOARD_FULL_CLEAR = DrsUnifiedTool(
        id = "clipboard_full_clear",
        code = KeyCode.CLIPBOARD_CLEAR_FULL_HISTORY,
        type = KeyType.FUNCTION,
        scope = DrsSettingScope.BASIC,
        defaultView = DrsToolView.BOTH,
        group = DrsToolGroup.EDITING,
    )

    /**
     * DRS v1.6.0: switches to the PREVIOUS configured subtype through the
     * real engine action (KeyCode.IME_PREV_SUBTYPE — handled in
     * KeyboardManager via subtypeManager.switchToPrevSubtype), the exact
     * counterpart of [NEXT_LANGUAGE].
     */
    val PREV_LANGUAGE = DrsUnifiedTool(
        id = "prev_language",
        code = KeyCode.IME_PREV_SUBTYPE,
        type = KeyType.FUNCTION,
        scope = DrsSettingScope.BASIC,
        defaultView = DrsToolView.BOTH,
        group = DrsToolGroup.TOOLS,
    )

    /**
     * DRS v1.6.0: moves the compact one-handed window to the LEFT edge
     * through the real engine action (KeyCode.COMPACT_LAYOUT_TO_LEFT —
     * handled in KeyboardManager via
     * windowController.actions.compactLayoutToLeft), the deterministic
     * complement of the [ONE_HANDED] toggle.
     */
    val ONE_HANDED_LEFT = DrsUnifiedTool(
        id = "one_handed_left",
        code = KeyCode.COMPACT_LAYOUT_TO_LEFT,
        type = KeyType.SYSTEM_GUI,
        scope = DrsSettingScope.SHARED,
        defaultView = DrsToolView.TECHNICAL,
        group = DrsToolGroup.TOOLS,
    )

    /**
     * DRS v1.6.0: moves the compact one-handed window to the RIGHT edge
     * through the real engine action (KeyCode.COMPACT_LAYOUT_TO_RIGHT —
     * handled in KeyboardManager via
     * windowController.actions.compactLayoutToRight).
     */
    val ONE_HANDED_RIGHT = DrsUnifiedTool(
        id = "one_handed_right",
        code = KeyCode.COMPACT_LAYOUT_TO_RIGHT,
        type = KeyType.SYSTEM_GUI,
        scope = DrsSettingScope.SHARED,
        defaultView = DrsToolView.TECHNICAL,
        group = DrsToolGroup.TOOLS,
    )

    /**
     * DRS v1.6.0: switches to the NEXT input method in the system list
     * through the real engine action (KeyCode.SYSTEM_NEXT_INPUT_METHOD —
     * handled in KeyboardManager via DrsImeService.switchToNextInputMethod,
     * the same path the utility key's SWITCH_KEYBOARD_APP action uses).
     */
    val NEXT_KEYBOARD_APP = DrsUnifiedTool(
        id = "next_keyboard_app",
        code = KeyCode.SYSTEM_NEXT_INPUT_METHOD,
        type = KeyType.FUNCTION,
        scope = DrsSettingScope.BASIC,
        defaultView = DrsToolView.BOTH,
        group = DrsToolGroup.TOOLS,
    )

    /**
     * DRS v1.7.0: pins/unpins the ACTIVE clipboard entry through the real
     * engine action (KeyCode.CLIPBOARD_PIN_ACTIVE — handled in
     * KeyboardManager via the same pinClip/unpinClip pair the clipboard
     * panel long-press popup uses). Pinned entries survive clearHistory
     * by contract, so this is a real one-tap "keep this" tool.
     */
    val CLIPBOARD_PIN = DrsUnifiedTool(
        id = "clipboard_pin",
        code = KeyCode.CLIPBOARD_PIN_ACTIVE,
        type = KeyType.SYSTEM_GUI,
        scope = DrsSettingScope.BASIC,
        defaultView = DrsToolView.BOTH,
        group = DrsToolGroup.TOOLS,
    )

    /**
     * DRS v1.8.0: opens the quick-actions overflow panel (most-used tiles
     * + the user's quick actions) through the REAL engine action
     * (KeyCode.TOGGLE_ACTIONS_OVERFLOW — handled in KeyboardManager by
     * flipping isActionsOverflowVisible, the same path the sticky action
     * uses). This is the fastest «كل المهام» surface for TYPICAL users.
     */
    val QUICK_ACTIONS = DrsUnifiedTool(
        id = "quick_actions",
        code = KeyCode.TOGGLE_ACTIONS_OVERFLOW,
        type = KeyType.FUNCTION,
        scope = DrsSettingScope.BASIC,
        defaultView = DrsToolView.BOTH,
        group = DrsToolGroup.TOOLS,
    )

    /**
     * DRS v1.8.0: opens the quick-actions EDITOR through the real engine
     * action (KeyCode.TOGGLE_ACTIONS_EDITOR — handled in KeyboardManager
     * via isActionsEditorVisible). Everyone can now rearrange their quick
     * actions without leaving the keyboard.
     */
    val ACTIONS_EDITOR = DrsUnifiedTool(
        id = "actions_editor",
        code = KeyCode.TOGGLE_ACTIONS_EDITOR,
        type = KeyType.FUNCTION,
        scope = DrsSettingScope.BASIC,
        defaultView = DrsToolView.BOTH,
        group = DrsToolGroup.TOOLS,
    )

    /**
     * DRS v1.15.0: opens the smart harakat panel (لوحة الحركات) through
     * the REAL engine action (KeyCode.IME_UI_MODE_DIACRITICS — handled in
     * KeyboardManager). The panel's smart stacking replaces a tap on a
     * mark with a mark instead of stacking, and shadda combos commit
     * two characters in one tap.
     */
    val DIACRITICS_PANEL = DrsUnifiedTool(
        id = "diacritics_panel",
        code = KeyCode.IME_UI_MODE_DIACRITICS,
        type = KeyType.SYSTEM_GUI,
        scope = DrsSettingScope.BASIC,
        defaultView = DrsToolView.BOTH,
        group = DrsToolGroup.TOOLS,
    )

    // DRS v1.19.0: one-tap split keyboard activation/merge — the v1.18.0
    // split layout was settings-only; these two catalogue tools drive the
    // same splitMode pref with a status toast.
    val SPLIT_KEYBOARD = DrsUnifiedTool(
        id = "split_keyboard",
        code = KeyCode.SPLIT_LAYOUT,
        type = KeyType.FUNCTION,
        scope = DrsSettingScope.BASIC,
        defaultView = DrsToolView.BOTH,
        group = DrsToolGroup.TOOLS,
    )

    val MERGE_KEYBOARD = DrsUnifiedTool(
        id = "merge_keyboard",
        code = KeyCode.MERGE_LAYOUT,
        type = KeyType.FUNCTION,
        scope = DrsSettingScope.BASIC,
        defaultView = DrsToolView.BOTH,
        group = DrsToolGroup.TOOLS,
    )

    /**
     * DRS v1.15.0: opens the context-aware smart symbols panel (لوحة
     * الرموز الذكية) through the REAL engine action
     * (KeyCode.IME_UI_MODE_SMART_SYMBOLS). The suggestions row reads the
     * text before the cursor and leads with the symbols the context
     * actually asks for.
     */
    val SMART_SYMBOLS = DrsUnifiedTool(
        id = "smart_symbols",
        code = KeyCode.IME_UI_MODE_SMART_SYMBOLS,
        type = KeyType.SYSTEM_GUI,
        scope = DrsSettingScope.BASIC,
        defaultView = DrsToolView.BOTH,
        group = DrsToolGroup.TOOLS,
    )

    /**
     * DRS v1.15.0: opens the extended Arabic letters panel (لوحة الحروف
     * الموسعة) through the REAL engine action
     * (KeyCode.IME_UI_MODE_ARABIC_LETTERS) — hamza variants and the
     * Persian/Urdu/Kurdish letters the base layout has no room for.
     */
    val ARABIC_LETTERS = DrsUnifiedTool(
        id = "arabic_letters",
        code = KeyCode.IME_UI_MODE_ARABIC_LETTERS,
        type = KeyType.SYSTEM_GUI,
        scope = DrsSettingScope.BASIC,
        defaultView = DrsToolView.BOTH,
        group = DrsToolGroup.TOOLS,
    )

    /** The full basic/shared catalogue in default display order. */
    val ALL: List<DrsUnifiedTool> = listOf(
        EMOJI, CLIPBOARD, TEXT_TOOLS, NUMBERS, SYMBOLS, LANGUAGE,
        UNDO, REDO, SELECT_ALL, COPY, CUT, PASTE, SHARE,
        SELECT_WORD, WORD_LEFT, WORD_RIGHT, LINE_START, LINE_END,
        DELETE_WORD, HIDE_KEYBOARD, SETTINGS,
        // DRS v1.0.8: appended at the tail so every existing persisted
        // order/pin arrangement keeps its exact meaning.
        THEME_CYCLE, INSERT_DATE_TIME, TEXT_START, TEXT_END,
        // DRS v1.1.0: same tail-append contract for the new tools.
        ONE_HANDED, NUMBER_ROW,
        // DRS v1.2.0: same tail-append contract for the new tools.
        INCOGNITO, AUTOCORRECT,
        // DRS v1.3.0: same tail-append contract for the new tools.
        CLIPBOARD_CLEAR, VOICE_INPUT,
        // DRS v1.4.0: same tail-append contract for the new tools.
        FLOATING_MODE, SMARTBAR_TOGGLE,
        // DRS v1.5.0: same tail-append contract for the new tools.
        CLIPBOARD_HISTORY_CLEAR, NEXT_LANGUAGE, RESIZE_MODE,
        // DRS v1.6.0: same tail-append contract for the new tools.
        CLIPBOARD_FULL_CLEAR, PREV_LANGUAGE, ONE_HANDED_LEFT, ONE_HANDED_RIGHT,
        NEXT_KEYBOARD_APP,
        // DRS v1.7.0: same tail-append contract for the new tools.
        CLIPBOARD_PIN,
        // DRS v1.8.0: same tail-append contract for the new tools.
        QUICK_ACTIONS, ACTIONS_EDITOR,
        // DRS v1.15.0: same tail-append contract for the new tools.
        DIACRITICS_PANEL, SMART_SYMBOLS, ARABIC_LETTERS,
        // DRS v1.19.0: same tail-append contract for the new tools.
        SPLIT_KEYBOARD, MERGE_KEYBOARD,
    )

    private val BY_ID = ALL.associateBy { it.id }

    /** Looks a catalogue tool up by id (null for unknown ids). */
    fun byId(id: String): DrsUnifiedTool? = BY_ID[id]

    /**
     * Resolves the effective visibility of [tool] for the display level
     * [view], honoring the user's per-tool override. DUAL shows everything
     * the two single levels show, so a tool visible in BOTH simple and
     * advanced levels is visible in dual as well.
     */
    fun isVisibleIn(
        tool: DrsUnifiedTool,
        view: DrsHybridViewMode,
        viewOverride: String?,
    ): Boolean {
        val effective = when (viewOverride) {
            DrsToolView.NORMAL.name -> DrsToolView.NORMAL
            DrsToolView.TECHNICAL.name -> DrsToolView.TECHNICAL
            DrsToolView.BOTH.name -> DrsToolView.BOTH
            else -> tool.defaultView
        }
        return when (view) {
            DrsHybridViewMode.SIMPLE -> effective != DrsToolView.TECHNICAL
            DrsHybridViewMode.ADVANCED -> effective != DrsToolView.NORMAL
            DrsHybridViewMode.DUAL -> true
        }
    }

    /**
     * Orders the catalogue tools for the strip: pinned first (in pin
     * order), then the rest. [order] may be a partial permutation - tools
     * missing from it keep their catalogue order afterwards. Unknown ids
     * are dropped defensively so a corrupt customization can never break
     * the strip.
     */
    fun applyOrderAndPins(
        tools: List<DrsUnifiedTool>,
        order: List<String>,
        pinned: List<String>,
    ): List<DrsUnifiedTool> {
        val byId = tools.associateBy { it.id }
        val ordered = ArrayList<DrsUnifiedTool>(tools.size)
        // 1) explicit order (only known ids, in given order)
        for (id in order) {
            val tool = byId[id] ?: continue
            if (tool !in ordered) ordered += tool
        }
        // 2) catalogue order for the rest
        for (tool in tools) {
            if (tool !in ordered) ordered += tool
        }
        // 3) pins float to the head (most recently pinned first is not
        //    required - stable pin order keeps the strip predictable)
        val pinnedSet = pinned.toHashSet()
        val (pinnedTools, unpinned) = ordered.partition { it.id in pinnedSet }
        return pinnedTools + unpinned
    }

    /**
     * DRS v1.15.0: the capped effective pinned set — «المهام المثبتة 10
     * فقط». The user's explicit pins keep their order and always win;
     * the [defaultPinnedIds] fill the remaining slots only. Pure and
     * deterministic: the same inputs always resolve the same 10 (or less).
     */
    fun capPinned(userPinned: List<String>, defaultPinnedIds: List<String>): List<String> {
        val capped = ArrayList<String>(MAX_PINNED_TOOLS)
        for (id in userPinned) {
            if (capped.size >= MAX_PINNED_TOOLS) break
            if (id !in capped) capped += id
        }
        for (id in defaultPinnedIds) {
            if (capped.size >= MAX_PINNED_TOOLS) break
            if (id !in capped) capped += id
        }
        return capped
    }

    /**
     * DRS v1.15.0: whether one more user pin still fits under the cap —
     * the user's own pins are capped at [MAX_PINNED_TOOLS]; the default
     * pins only fill the remaining rendered slots. The tools drawer uses
     * this to refuse the 11th pin with an honest message instead of a
     * silent no-op.
     */
    fun canPinMore(userPinned: List<String>): Boolean {
        return userPinned.distinct().size < MAX_PINNED_TOOLS
    }

    /**
     * DRS v1.16.0: «شريط المهام الثابت» — the FIXED slots list of the
     * tasks bar. The bar always renders exactly ten tasks: the user's
     * explicit pins lead in pin order, the default pins fill the next
     * slots, and the visible catalogue order pads the tail so the bar
     * never shows a hole. Pure and deterministic: same inputs, same ten.
     */
    fun fixedSlots(
        userPinned: List<String>,
        defaultPinnedIds: List<String>,
        visibleCatalogueOrder: List<String>,
    ): List<String> {
        val slots = ArrayList<String>(MAX_PINNED_TOOLS)
        for (id in userPinned) {
            if (slots.size >= MAX_PINNED_TOOLS) break
            if (id !in slots) slots += id
        }
        for (id in defaultPinnedIds) {
            if (slots.size >= MAX_PINNED_TOOLS) break
            if (id !in slots) slots += id
        }
        for (id in visibleCatalogueOrder) {
            if (slots.size >= MAX_PINNED_TOOLS) break
            if (id !in slots) slots += id
        }
        return slots
    }

    /**
     * DRS v1.22.0: the pure core of «إعادة الترتيب بالسحب» — moving the
     * task at [from] to [to] inside the pinned head. Out-of-range indexes
     * and no-op moves return the input unchanged; the result stays
     * deduplicated and capped so a malformed drag can never fabricate an
     * 11th pin or a duplicate slot.
     */
    fun reorderPinnedSlots(slots: List<String>, from: Int, to: Int): List<String> {
        if (from !in slots.indices || to !in slots.indices || from == to) return slots
        val mutable = slots.toMutableList()
        val moved = mutable.removeAt(from)
        mutable.add(to, moved)
        return mutable.distinct().take(MAX_PINNED_TOOLS)
    }

    /**
     * DRS v1.16.0: the pure core of «إمكانية تغيير المهام» — replacing
     * the tool at slot [index] with [newId] in the CURRENT slots list.
     * The result is the materialized pinned list (every slot becomes an
     * explicit pin, so the replacement stays exactly where the user put
     * it), deduplicated and capped at [MAX_PINNED_TOOLS]. An out-of-range
     * index or an unknown id returns the input unchanged.
     */
    fun materializeSlotReplace(
        currentSlots: List<String>,
        index: Int,
        newId: String,
    ): List<String> {
        if (index < 0 || index >= currentSlots.size) return currentSlots
        if (newId !in DrsUnifiedTools.ALL.map { it.id }) return currentSlots
        val replaced = currentSlots.toMutableList()
        replaced[index] = newId
        val deduped = replaced.distinct()
        return deduped.take(MAX_PINNED_TOOLS)
    }

    /**
     * Full resolution of the unified strip content for one display level:
     * filters by visibility + hidden list, then applies order and pins.
     * Tools marked [DrsUnifiedTool.defaultPinned] float to the head unless
     * the user explicitly unpinned them, and the pinned head is capped at
     * [MAX_PINNED_TOOLS] since v1.15.0. Pure and side-effect free - the
     * same function feeds the IME strip, the tools manager preview and the
     * unit tests.
     */
    fun resolveFor(
        view: DrsHybridViewMode,
        hidden: List<String>,
        pinned: List<String>,
        order: List<String>,
        viewOverrides: Map<String, String>,
    ): List<DrsUnifiedTool> {
        val hiddenSet = hidden.toHashSet()
        val defaultPinnedIds = ALL.filter { it.defaultPinned }.map { it.id }
        val effectivePinned = capPinned(pinned, defaultPinnedIds)
        val visible = ALL.filter { tool ->
            tool.id !in hiddenSet &&
                isVisibleIn(tool, view, viewOverrides[tool.id])
        }
        return applyOrderAndPins(visible, order, effectivePinned)
    }

    /**
     * Resolves which display level applies right now: the normal system
     * always lives in the simple level, the technical system in the
     * advanced level, and the hybrid system uses the user's stored level.
     */
    fun viewForSystem(userPath: String, hybridViewMode: String): DrsHybridViewMode {
        return when (userPath) {
            DrsUserPath.NORMAL.name -> DrsHybridViewMode.SIMPLE
            DrsUserPath.TECHNICAL.name -> DrsHybridViewMode.ADVANCED
            else -> DrsHybridViewMode.fromNameSafe(hybridViewMode)
        }
    }

    /**
     * DRS v1.8.0: returns the per-tool view override name that GUARANTEES
     * [tool] renders in [view] given its current stored [override], or
     * null when it is already visible and the state must stay untouched.
     * Pure and side-effect free.
     *
     * A tool pinned from the tools drawer while its effective view hides
     * it on the current level (e.g. a TECHNICAL-only tool pinned from the
     * simple level) used to disappear silently; the drawer now applies
     * this helper so «التثبيت للجميع» really means visible for everyone.
     */
    fun ensureVisibleOverride(
        tool: DrsUnifiedTool,
        view: DrsHybridViewMode,
        override: String?,
    ): String? {
        val effective = when (override) {
            DrsToolView.NORMAL.name -> DrsToolView.NORMAL
            DrsToolView.TECHNICAL.name -> DrsToolView.TECHNICAL
            DrsToolView.BOTH.name -> DrsToolView.BOTH
            else -> tool.defaultView
        }
        return if (isVisibleIn(tool, view, override)) {
            null
        } else {
            if (effective == DrsToolView.BOTH) null else DrsToolView.BOTH.name
        }
    }

    /**
     * DRS v1.8.0: snapshot of the REAL on/off state of every strip toggle
     * tool. The strip reads each value from its own engine source (IME
     * state flags, jetpref settings, the window controller), while this
     * pure structure keeps the mapping testable on the JVM.
     */
    data class ToggleStates(
        val incognito: Boolean = false,
        val autocorrect: Boolean = true,
        val numberRow: Boolean = false,
        val smartbarVisible: Boolean = true,
        val floatingWindow: Boolean = false,
        // DRS v1.22.0: the revived CTRL/ALT modifier latches (شريط التقني)
        // — the dot shows the truth, LOCKED and LATCHED alike.
        val ctrlArmed: Boolean = false,
        val altArmed: Boolean = false,
        // DRS v1.23.0: FN completes the modifier family — same honest dot.
        val fnArmed: Boolean = false,
    )

    /**
     * DRS v1.8.0: the live on/off state of a toggle tool, or null when the
     * tool is not a toggle (it has no state to show). Drives the small
     * active dot on the strip tiles so «مفعّل» is visible at a glance.
     */
    fun toggleStateOf(id: String, states: ToggleStates): Boolean? = when (id) {
        "incognito" -> states.incognito
        "autocorrect" -> states.autocorrect
        "number_row" -> states.numberRow
        "smartbar_toggle" -> states.smartbarVisible
        "floating_mode" -> states.floatingWindow
        // DRS v1.22.0: the modifier latch toggles of the tech toolbar.
        "ctrl" -> states.ctrlArmed
        "alt" -> states.altArmed
        // DRS v1.23.0: FN joins the family.
        "fn" -> states.fnArmed
        else -> null
    }
}

/**
 * State mutation helpers for the unified layer. Every change is persisted
 * inside DrsState (the same atomic local JSON file as the rest of the DRS
 * layer) and never touches profiles, shortcuts, clipboard data or themes,
 * so level switching and customization are lossless by construction.
 */
object DrsUnified {

    /** Applies a new display level (persisted; no other state is touched). */
    fun setViewMode(mode: DrsHybridViewMode) {
        DrsStore.update { it.copy(hybridViewMode = mode.name) }
    }


    /** Gates the advanced settings groups of the unified System screen. */
    fun setAdvancedControls(enabled: Boolean) {
        DrsStore.update { it.copy(advancedControlsEnabled = enabled) }
    }

    /** Opts the normal system's keyboard into the unified strip. */
    fun setStripForNormal(enabled: Boolean) {
        DrsStore.update { it.copy(unifiedStripForNormal = enabled) }
    }

    /**
     * DRS v1.8.0: master switch of the tasks bar above the suggestions
     * strip. Applies to ALL three user systems (العادي/التقني/كلاهما) —
     * the bar is on by default and this is the single honest way off.
     */
    fun setStripEnabled(enabled: Boolean) {
        DrsStore.update { it.copy(unifiedStripEnabled = enabled) }
    }

    /**
     * DRS v1.8.0: pins a tool AND guarantees it is actually visible in the
     * [view] the user is currently on. Pinning a technical-only tool from
     * the drawer while on the simple level used to swallow the pin
     * silently (pinned but never rendered); now the same update also
     * widens the per-tool view override so the pin really shows up.
     *
     * DRS v1.15.0: the pin is refused honestly (returns false) once the
     * user already holds [DrsUnifiedTools.MAX_PINNED_TOOLS] pins — the
     * drawer tells the user to unpin one first. Unpinning frees a slot.
     */
    fun setToolPinnedEnsureVisible(id: String, view: DrsHybridViewMode): Boolean {
        val tool = DrsUnifiedTools.byId(id) ?: return false
        // DRS v1.28.0 audit fix (High): the decision used to be computed by
        // mutating `applied` inside DrsStore.update {} — a fire-and-forget
        // coroutine. The return statement read the flag BEFORE the transform
        // ever ran, so this function (and its siblings) reported refusal on
        // virtually every call: every pin attempt raised the "cap reached"
        // toast and the slot editor never closed. The outcome is now decided
        // synchronously against the state the user is actually looking at
        // (single UI-thread writer for these fields), then applied async.
        val current = DrsStore.state.value
        if (!current.pinnedUnifiedTools.contains(id) &&
            !DrsUnifiedTools.canPinMore(current.pinnedUnifiedTools)
        ) {
            return false
        }
        DrsStore.update { state ->
            if (state.pinnedUnifiedTools.contains(id)) {
                return@update state
            }
            if (!DrsUnifiedTools.canPinMore(state.pinnedUnifiedTools)) {
                return@update state
            }
            val newOverride = DrsUnifiedTools.ensureVisibleOverride(
                tool = tool,
                view = view,
                override = state.unifiedToolViews[id],
            )
            state.copy(
                pinnedUnifiedTools = (state.pinnedUnifiedTools + id).distinct(),
                unifiedToolViews = if (newOverride != null) {
                    state.unifiedToolViews + (id to newOverride)
                } else {
                    state.unifiedToolViews
                },
            )
        }
        return true
    }

    /** Shows/hides one tool (hiding never deletes the customization). */
    fun setToolHidden(id: String, hidden: Boolean) {
        if (DrsUnifiedTools.byId(id) == null) return
        DrsStore.update { state ->
            state.copy(
                hiddenUnifiedTools = if (hidden) {
                    (state.hiddenUnifiedTools + id).distinct()
                } else {
                    state.hiddenUnifiedTools - id
                },
            )
        }
    }

    /**
     * Pins/unpins one tool; pinned tools float to the strip head under the
     * v1.15.0 cap of [DrsUnifiedTools.MAX_PINNED_TOOLS] — the 11th pin is
     * refused honestly (returns false), unpinning always works.
     *
     * DRS v1.28.0 audit fix (High): synchronous snapshot decision, see
     * [setToolPinnedEnsureVisible] — the old in-transform flag mutation
     * reported refusal on every call.
     */
    fun setToolPinned(id: String, pinned: Boolean): Boolean {
        if (DrsUnifiedTools.byId(id) == null) return false
        val current = DrsStore.state.value
        val applied = if (pinned) {
            current.pinnedUnifiedTools.contains(id) ||
                DrsUnifiedTools.canPinMore(current.pinnedUnifiedTools)
        } else {
            current.pinnedUnifiedTools.contains(id)
        }
        if (!applied) return false
        DrsStore.update { state ->
            state.copy(
                pinnedUnifiedTools = if (pinned) {
                    if (state.pinnedUnifiedTools.contains(id)) {
                        state.pinnedUnifiedTools
                    } else if (!DrsUnifiedTools.canPinMore(state.pinnedUnifiedTools)) {
                        state.pinnedUnifiedTools
                    } else {
                        (state.pinnedUnifiedTools + id).distinct()
                    }
                } else {
                    state.pinnedUnifiedTools - id
                },
            )
        }
        return true
    }

    /** Overrides which level(s) a tool appears in. */
    fun setToolView(id: String, view: DrsToolView) {
        if (DrsUnifiedTools.byId(id) == null) return
        DrsStore.update { state ->
            state.copy(unifiedToolViews = state.unifiedToolViews + (id to view.name))
        }
    }

    /**
     * DRS v1.16.0: «إمكانية تغيير المهام» — replaces the tool rendered at
     * bar slot [index] with [newId]. The replacement materializes the
     * current ten slots into explicit pins (so the swapped-in tool stays
     * exactly at that slot), and the swapped-in tool is guaranteed
     * visible on [view] the same way a drawer pin is. Honest return:
     * false for an unknown id or an out-of-range slot — nothing silently
     * swallowed.
     */
    fun replaceStripSlot(index: Int, newId: String, view: DrsHybridViewMode): Boolean {
        if (DrsUnifiedTools.byId(newId) == null) return false
        // DRS v1.28.0 audit fix (High): synchronous slot-range decision against
        // the visible state — the old in-transform `applied` flag was read
        // before the async transform ran, so this never returned true and the
        // slot editor panel never closed on a successful pick.
        val snapshot = DrsStore.state.value
        val snapshotSlots = run {
            val hiddenSet = snapshot.hiddenUnifiedTools.toHashSet()
            val visiblePins = snapshot.pinnedUnifiedTools.filter { it !in hiddenSet }
            val visibleDefaults = DrsUnifiedTools.ALL
                .filter { it.defaultPinned && it.id !in hiddenSet }
                .filter { DrsUnifiedTools.isVisibleIn(it, view, snapshot.unifiedToolViews[it.id]) }
                .map { it.id }
            val resolved = DrsUnifiedTools.resolveFor(
                view = view,
                hidden = snapshot.hiddenUnifiedTools,
                pinned = snapshot.pinnedUnifiedTools,
                order = snapshot.unifiedToolOrder,
                viewOverrides = snapshot.unifiedToolViews,
            )
            DrsUnifiedTools.fixedSlots(visiblePins, visibleDefaults, resolved.map { it.id })
        }
        if (index < 0 || index >= snapshotSlots.size) return false
        DrsStore.update { state ->
            val hiddenSet = state.hiddenUnifiedTools.toHashSet()
            val visiblePins = state.pinnedUnifiedTools.filter { it !in hiddenSet }
            val visibleDefaults = DrsUnifiedTools.ALL
                .filter { it.defaultPinned && it.id !in hiddenSet }
                .filter { DrsUnifiedTools.isVisibleIn(it, view, state.unifiedToolViews[it.id]) }
                .map { it.id }
            val resolved = DrsUnifiedTools.resolveFor(
                view = view,
                hidden = state.hiddenUnifiedTools,
                pinned = state.pinnedUnifiedTools,
                order = state.unifiedToolOrder,
                viewOverrides = state.unifiedToolViews,
            )
            val slots = DrsUnifiedTools.fixedSlots(
                visiblePins, visibleDefaults, resolved.map { it.id },
            )
            if (index < 0 || index >= slots.size) return@update state
            val newPins = DrsUnifiedTools.materializeSlotReplace(slots, index, newId)
            val tool = DrsUnifiedTools.byId(newId)!!
            val newOverride = DrsUnifiedTools.ensureVisibleOverride(
                tool = tool,
                view = view,
                override = state.unifiedToolViews[newId],
            )
            state.copy(
                pinnedUnifiedTools = newPins,
                unifiedToolViews = if (newOverride != null) {
                    state.unifiedToolViews + (newId to newOverride)
                } else {
                    state.unifiedToolViews
                },
            )
        }
        return true
    }

    /**
     * DRS v1.22.0: «إعادة الترتيب بالسحب» — moves the task rendered at
     * pinned-head position [from] to position [to] (the same ten-slot
     * geometry [replaceStripSlot] uses). The strip order list is rewritten
     * so the pinned head keeps the dragged order, and the explicit pin
     * list is reordered coherently so cap priority (user pins win in pin
     * order) follows what the user sees. Honest return: false for
     * out-of-range indexes or a no-op move — nothing silently swallowed.
     */
    fun reorderPinnedTool(from: Int, to: Int, view: DrsHybridViewMode): Boolean {
        if (from == to) return false
        // DRS v1.28.0 audit fix (High): synchronous range decision, see
        // [replaceStripSlot] — the in-transform flag was read too early.
        val snapshot = DrsStore.state.value
        val snapshotHead = run {
            val defaultPinnedIds = DrsUnifiedTools.ALL.filter { it.defaultPinned }.map { it.id }
            val effectivePinned = DrsUnifiedTools
                .capPinned(snapshot.pinnedUnifiedTools, defaultPinnedIds)
                .toHashSet()
            DrsUnifiedTools.resolveFor(
                view = view,
                hidden = snapshot.hiddenUnifiedTools,
                pinned = snapshot.pinnedUnifiedTools,
                order = snapshot.unifiedToolOrder,
                viewOverrides = snapshot.unifiedToolViews,
            ).filter { it.id in effectivePinned }.map { it.id }
        }
        if (from < 0 || from >= snapshotHead.size || to < 0 || to >= snapshotHead.size) return false
        DrsStore.update { state ->
            val defaultPinnedIds = DrsUnifiedTools.ALL.filter { it.defaultPinned }.map { it.id }
            val effectivePinned = DrsUnifiedTools
                .capPinned(state.pinnedUnifiedTools, defaultPinnedIds)
                .toHashSet()
            val head = DrsUnifiedTools.resolveFor(
                view = view,
                hidden = state.hiddenUnifiedTools,
                pinned = state.pinnedUnifiedTools,
                order = state.unifiedToolOrder,
                viewOverrides = state.unifiedToolViews,
            ).filter { it.id in effectivePinned }.map { it.id }
            if (from < 0 || from >= head.size || to < 0 || to >= head.size) return@update state
            val newHead = DrsUnifiedTools.reorderPinnedSlots(head, from, to)
            // Rewrite the catalogue order: pinned head first in the dragged
            // order, everything else keeps its relative tail order.
            val catalogueIds = DrsUnifiedTools.ALL.map { it.id }
            val baseOrder = if (state.unifiedToolOrder.isEmpty()) {
                catalogueIds
            } else {
                val known = state.unifiedToolOrder.filter { it in catalogueIds }
                known + catalogueIds.filter { it !in known.toSet() }
            }
            val newOrder = (newHead + baseOrder.filter { it !in newHead.toSet() }).distinct()
            // Keep the explicit pin order coherent with the visible head —
            // pins hidden on this level keep their relative order after it.
            val explicitSet = state.pinnedUnifiedTools.toHashSet()
            val reorderedPins = newHead.filter { it in explicitSet }
            val remainingPins = state.pinnedUnifiedTools.filter { it !in reorderedPins.toSet() }
            state.copy(
                unifiedToolOrder = newOrder,
                pinnedUnifiedTools = (reorderedPins + remainingPins).distinct(),
            )
        }
        return true
    }

    /** Moves a tool one slot up (towards the strip head) in the order. */
    fun moveTool(id: String, up: Boolean) {
        if (DrsUnifiedTools.byId(id) == null) return
        DrsStore.update { state ->
            val catalogueIds = DrsUnifiedTools.ALL.map { it.id }
            val current = catalogueIds.filter { it !in state.hiddenUnifiedTools }
            val ordered = if (state.unifiedToolOrder.isEmpty()) {
                current
            } else {
                // merge: explicit order first, then remaining catalogue ids
                val known = state.unifiedToolOrder.filter { it in catalogueIds }
                val rest = current.filter { it !in known }
                known + rest
            }
            val index = ordered.indexOf(id)
            if (index == -1) return@update state
            val target = if (up) index - 1 else index + 1
            if (target < 0 || target >= ordered.size) return@update state
            val mutable = ordered.toMutableList()
            val item = mutable.removeAt(index)
            mutable.add(target, item)
            state.copy(unifiedToolOrder = mutable)
        }
    }

    /** Clears ALL strip customizations back to the catalogue defaults. */
    fun resetCustomization() {
        DrsStore.update {
            it.copy(
                unifiedToolOrder = emptyList(),
                hiddenUnifiedTools = emptyList(),
                pinnedUnifiedTools = emptyList(),
                unifiedToolViews = emptyMap(),
            )
        }
    }

    /** DRS v1.0.8: master switch of the daily usage statistics recording. */
    fun setDailyStatsEnabled(enabled: Boolean) {
        DrsStore.update { it.copy(dailyStatsEnabled = enabled) }
    }

    /** DRS v1.0.8: wipes all recorded daily usage buckets (local privacy action). */
    fun resetDailyStats() {
        DrsStore.update { it.copy(dailyStats = emptyMap()) }
    }

    /**
     * DRS v1.1.0: stamps the moment of the last successful DRS-state backup
     * export (epoch millis). Consumed by the diagnostics backup-age check.
     */
    fun markBackupExported() {
        // DRS (B2): backup bookkeeping is economy-critical — durable write.
        DrsStore.update(immediate = true) { it.copy(lastBackupAt = System.currentTimeMillis()) }
    }
}
