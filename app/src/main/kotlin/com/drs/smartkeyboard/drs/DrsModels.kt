/*
 * Copyright (C) 2021-2025 The DRS Smart Keyboard Project
 * Copyright (C) 2025 DRS Smart Keyboard contributors
 */

package com.drs.smartkeyboard.drs

import kotlinx.serialization.Serializable

/**
 * The user path selected during onboarding (or changed later from the
 * DRS Control Center). Defines which experience the keyboard prepares.
 */
enum class DrsUserPath {
    /** DRS Smart Basic Mode: everyday typing, chats, social, speed, emoji. */
    NORMAL,

    /** DRS Technical Mode: coding symbols, navigation keys, snippets. */
    TECHNICAL,

    /** DRS Hybrid Mode: both worlds with fast switching. */
    HYBRID,
}

/**
 * Context mode derived from the focused field (EditorInfo) each time input
 * starts. The keyboard adapts its toolbar/layout accordingly when possible.
 */
enum class DrsContextMode {
    NORMAL, CHAT, WRITING, CODING, NUMBERS, SEARCH, PASSWORD, TECHNICAL,
}

/** A self-contained keyboard profile with independent settings. */
@Serializable
data class DrsProfile(
    val id: String,
    val name: String,
    /** One of DrsUserPath names or CUSTOM. */
    val path: String,
    /** Full theme component id for the day theme, e.g. "org.drs.themes:drs_day". */
    val dayThemeId: String,
    /** Full theme component id for the night theme. */
    val nightThemeId: String,
    val numberRow: Boolean,
    val techStripEnabled: Boolean,
    val suggestionsEnabled: Boolean,
    val clipboardHistoryEnabled: Boolean,
    val audioFeedbackEnabled: Boolean,
    val hapticFeedbackEnabled: Boolean,
    /**
     * DRS M0.6 — per-language characters-layout overrides applied on
     * activation: languageTag → full layout component id (e.g.
     * "en" to "org.drs.layouts:qwerty"). Applied through the SAME
     * subtype preference SubtypeManager observes, so the keyboard
     * re-lays-out live without breaking subtype ids or ordering.
     */
    val subtypeLayouts: Map<String, String> = emptyMap(),
)

/** A text shortcut: typing [shortcut] then space expands to [expansion]. */
@Serializable
data class DrsShortcut(
    val id: Long,
    val shortcut: String,
    val expansion: String,
    val isTechnical: Boolean = false,
    /** DRS v1.0.6: disabled shortcuts stay stored but never expand. */
    val enabled: Boolean = true,
    /**
     * DRS v1.0.7: where the shortcut is available inside the unified
     * system - one of DrsShortcutScope names (NORMAL / TECHNICAL / BOTH).
     * The expansion engine really filters by the active system and (for
     * the hybrid system) the current display level, so a BOTH-only
     * shortcut really stays silent in the other levels.
     */
    val scope: String = DrsShortcutScope.BOTH.name,
)

/**
 * DRS v1.0.7: availability scope of a shortcut across the unified
 * system's levels.
 */
enum class DrsShortcutScope {
    /** Available when the normal level is active. */
    NORMAL,

    /** Available when the technical level is active. */
    TECHNICAL,

    /** Available in both levels (and the dual level). */
    BOTH,
}

/**
 * Aggregated, anonymous usage counters used by the local adaptation engine.
 * Only counts are stored - never typed text. Nothing leaves the device.
 */
@Serializable
data class DrsUsageStats(
    val keyPresses: Long = 0,
    val numberPresses: Long = 0,
    val symbolPresses: Long = 0,
    val emojiUses: Long = 0,
    val clipboardUses: Long = 0,
    val shortcutUses: Long = 0,
    // DRS v2.21.0: anonymous count of saved-text insertions (نصوصي).
    // Counts only — never WHICH text was inserted.
    val myTextsUses: Long = 0,
    // DRS v2.22.0: anonymous per-feature counters completing the
    // per-feature integration fabric (counts only — never WHAT was
    // inserted, never WHICH word was vocalized).
    val myLexiconUses: Long = 0,
    val harakatUses: Long = 0,
    val symbolUses: Long = 0,
    val letterUses: Long = 0,
    val numberUses: Long = 0,
    val techToolUses: Long = 0,
    val gestureUses: Long = 0,
    // DRS v1.6.0: how many times a suggestion/candidate row entry was
    // actually committed (auto-completion accepts included). Anonymous
    // count only — never WHICH word was accepted.
    val suggestionAccepts: Long = 0,
    // DRS v1.0.5: anonymous per-tool usage counts (KeyCode -> count) used to
    // surface the smart tools the user actually relies on. Counts only —
    // never text, never timestamps. Capped on merge to stay tiny.
    val toolUses: Map<Int, Long> = emptyMap(),
    // DRS v1.7.0: how many input sessions STARTED in each context mode
    // (normal/writing/password/…). Anonymous counts of a DETECTED attribute
    // of the focused field — never the field's content or identity.
    // Capped to the known mode names on merge to stay tiny.
    val contextStarts: Map<String, Long> = emptyMap(),
)

/**
 * DRS v1.0.8: one local day of anonymous usage counters, keyed by an ISO
 * day stamp (e.g. "2026-09-24") inside [DrsState.dailyStats]. Counts only
 * — never text, never keystroke timing, nothing leaves the device. Powers
 * the real daily usage statistics screen.
 */
@Serializable
data class DrsDayStats(
    /** ISO day stamp this bucket belongs to, e.g. "2026-09-24". */
    val day: String = "",
    val keyPresses: Long = 0,
    /** DRS v1.3.0: digit-key presses (numbers row / numeric layouts). */
    val numberPresses: Long = 0,
    /** DRS v1.3.0: symbol presses (non-digit, non-letter characters). */
    val symbolPresses: Long = 0,
    /** Unified strip / smartbar tool presses (any catalogue tool). */
    val toolUses: Long = 0,
    /** Technical text-tool presses (the technical toolbar actions). */
    val techToolUses: Long = 0,
    val gestureUses: Long = 0,
    val emojiUses: Long = 0,
    val clipboardUses: Long = 0,
    val shortcutUses: Long = 0,
    /** DRS v2.21.0: saved-text insertions (counts only). */
    val myTextsUses: Long = 0,
    /** DRS v2.22.0: the per-feature counters of the smart surfaces. */
    val myLexiconUses: Long = 0,
    val harakatUses: Long = 0,
    val symbolUses: Long = 0,
    val letterUses: Long = 0,
    val numberUses: Long = 0,
    /** DRS v1.6.0: committed suggestion-row entries (accepts). */
    val suggestionAccepts: Long = 0,
    /** DRS v1.7.0: input starts per context mode (counts only). */
    val contextStarts: Map<String, Long> = emptyMap(),
)

/**
 * DRS v2.21.0: one SAVED text of the «نصوصي» system (نص محفوظ واحد).
 * Unlike the clipboard history (auto-captured, auto-cleaned, transient),
 * a saved text is USER-CURATED permanent content: an address, a greeting,
 * a bank IBAN, a reply template — browsable from the sixth smart panel
 * and insertable with one tap. Templates share the shortcuts engine's
 * variables ({date}, {time}, {hijri}, {clipboard}, {newline}, {cursor})
 * and are expanded at INSERT time, never at store time.
 *
 * Only what the user explicitly typed/saved is stored — nothing is ever
 * captured implicitly, nothing leaves the device.
 */
@Serializable
data class DrsMyText(
    val id: Long,
    val text: String,
    /** Optional short display label; empty = derive an honest text preview. */
    val label: String = "",
    /** Optional free category tag; empty = the general (عام) bucket. */
    val category: String = "",
    /** Pinned texts float to the head of the panel and the manager. */
    val pinned: Boolean = false,
    val createdAtMs: Long = 0,
    val updatedAtMs: Long = 0,
)

/**
 * DRS v2.22.0: one USER-TAUGHT word of «قاموسي التشكيلي» (the personal
 * tashkeel lexicon) — the harakat system's own curated store. The 3000-word
 * asset lexicon and the seed are READ-ONLY references; this entry is the
 * user's explicit teaching: a stripped word mapped to its full vocalization.
 *
 * The lookup doctrine is «المستخدم تفوز» (the user wins): the personal
 * entry is consulted BEFORE the seed and the asset — an honest override,
 * never a guess. The word field is stored STRIPPED (the lexicon key
 * contract), and strip(vocalized) must equal word — the same closed
 * contract the seed and the asset obey, pinned by the engine.
 */
@Serializable
data class DrsMyLexiconEntry(
    val id: Long,
    /** The STRIPPED word — the lexicon key, never carrying marks. */
    val word: String,
    /** The taught vocalization — strip(vocalized) == word, enforced. */
    val vocalized: String,
    /** Pinned entries float to the head of the manager. */
    val pinned: Boolean = false,
    val createdAtMs: Long = 0,
    val updatedAtMs: Long = 0,
)

/**
 * A single entry in the rewards ledger (دفتر المكافآت). Only meaningful
 * events are logged: daily bonuses, feature rewards and store purchases.
 * Typing rewards accumulate silently without flooding the ledger.
 */
@Serializable
data class DrsLedgerEntry(
    /** Local day stamp, e.g. "2026-09-23". */
    val day: String = "",
    /** Machine reason key, e.g. "daily_bonus" or "buy:title_chat_legend". */
    val reason: String = "",
    /** Signed points delta (positive earn, negative spend). */
    val delta: Long = 0,
)

/**
 * The DRS rewards wallet: one dedicated balance per system, lifetime
 * counters, the daily streak and the owned/equipped store items.
 *
 * Every system owns its own currency: points earned while a system is
 * active are credited to THAT system's balance only, so each system has
 * its own economy that grows with its own usage.
 */
@Serializable
data class DrsWallet(
    val normal: Long = 0,
    val technical: Long = 0,
    val hybrid: Long = 0,
    /** Lifetime earned counters per system (never decreased by spending). */
    val earnedNormal: Long = 0,
    val earnedTechnical: Long = 0,
    val earnedHybrid: Long = 0,
    /** Consecutive daily-active days (caps the daily bonus size). */
    val streakDays: Int = 0,
    /** Last day any balance changed, e.g. "2026-09-23". */
    val lastActiveDay: String = "",
    /** Day the daily bonus was last granted (prevents double-grant). */
    val dailyBonusDay: String = "",
    /** Purchased reward item ids. */
    val ownedItems: List<String> = emptyList(),
    val equippedTitle: String = "",
    val equippedBadge: String = "",
    val equippedCard: String = "",
    /** Recent ledger entries, newest first, capped. */
    val ledger: List<DrsLedgerEntry> = emptyList(),
)

/** Root of the persisted DRS state (single small JSON file, local only). */
@Serializable
data class DrsState(
    val version: Int = 1,
    val onboardingDone: Boolean = false,
    /** DRS M2.6 — the last onboarding step index, for the live resume. */
    val onboardingStep: Int = 0,
    val userPath: String = DrsUserPath.NORMAL.name,
    val activeProfileId: String = "",
    val profiles: List<DrsProfile> = emptyList(),
    val shortcuts: List<DrsShortcut> = emptyList(),
    /**
     * DRS v2.21.0: the user's saved texts (نصوصي) — curated permanent
     * content for the sixth smart panel. Bounded by [DrsMyTexts.MAX_ITEMS];
     * local only, covered structurally by the backup like every other
     * [DrsState] field.
     */
    val myTexts: List<DrsMyText> = emptyList(),
    /** DRS v2.21.0: id allocator of the saved texts. */
    val nextMyTextId: Long = 1,
    /** DRS v2.21.0: master switch — gates the panel + insertion, not management. */
    val myTextsEnabled: Boolean = true,
    /**
     * DRS v2.22.0: the user's taught tashkeel words (قاموسي التشكيلي) —
     * the harakat system's personal lexicon. Bounded by
     * [DrsMyLexicon.MAX_ITEMS]; local only, covered structurally by the
     * backup like every other [DrsState] field.
     */
    val myLexicon: List<DrsMyLexiconEntry> = emptyList(),
    /** DRS v2.22.0: id allocator of the personal lexicon. */
    val nextMyLexiconId: Long = 1,
    /** DRS v2.22.0: master switch — gates the override lookup, not management. */
    val myLexiconEnabled: Boolean = true,
    val usage: DrsUsageStats = DrsUsageStats(),
    val dismissedSuggestionIds: List<String> = emptyList(),
    val adaptationEnabled: Boolean = true,
    val contextModesEnabled: Boolean = true,
    val shortcutsEnabled: Boolean = true,
    val nextShortcutId: Long = 1,
    val wallet: DrsWallet = DrsWallet(),
    /**
     * DRS v1.0.6: the user's customized technical toolbar key order.
     * Key ids follow [DrsTechToolbarKeys catalogue ids]; an empty list means
     * "use the default arrangement". Persisted with the rest of the DRS
     * state so the strip survives restarts exactly as customized.
     */
    val techToolbarKeys: List<String> = emptyList(),
    /**
     * DRS v1.0.7 (نظام كلاهما الموحد): the active display level of the
     * unified system (SIMPLE / ADVANCED / DUAL). Switching the level only
     * changes visibility - profiles, shortcuts, clipboard data and themes
     * are never touched, so level switches are lossless.
     */
    val hybridViewMode: String = DrsHybridViewMode.DUAL.name,
    /** DRS v1.0.7: gates the advanced groups of the unified System screen. */
    val advancedControlsEnabled: Boolean = false,
    /** DRS v1.0.7: opts the normal system's keyboard into the unified strip. */
    val unifiedStripForNormal: Boolean = false,
    /** DRS v1.0.7: explicit unified strip tool order (catalogue ids). */
    val unifiedToolOrder: List<String> = emptyList(),
    /** DRS v1.0.7: tools the user removed from the unified strip. */
    val hiddenUnifiedTools: List<String> = emptyList(),
    /** DRS v1.0.7: tools pinned to the head of the unified strip. */
    val pinnedUnifiedTools: List<String> = emptyList(),
    /** DRS v1.0.7: per-tool visibility override (tool id -> DrsToolView name). */
    val unifiedToolViews: Map<String, String> = emptyMap(),
    /**
     * DRS v1.0.8: per-day anonymous usage counters keyed by ISO day stamp
     * (see [DrsDayStats]). Bounded to a fixed retention window; used by the
     * daily usage statistics screen. Local only, never synced.
     */
    val dailyStats: Map<String, DrsDayStats> = emptyMap(),
    /** DRS v1.0.8: master switch of the daily usage statistics recording. */
    val dailyStatsEnabled: Boolean = true,
    /**
     * DRS v1.1.0: epoch millis of the last successful DRS-state backup
     * export (0 = never). Consumed by the diagnostics backup-age check.
     * Local only, never synced; old states/backups decode to 0 safely.
     */
    val lastBackupAt: Long = 0L,
    /**
     * DRS v1.8.0: master switch of the tasks bar (شريط المهام) that now
     * sits ABOVE the suggestions strip for ALL three user systems. Default
     * ON — everyone gets the bar; the tools drawer's side-pull button can
     * hide it. Old states/backups decode to true (the new default), so the
     * upgrade path turns the bar on exactly once, losslessly.
     */
    val unifiedStripEnabled: Boolean = true,
)

/** Suggestion ids produced by the local adaptation engine. */
object DrsSuggestionIds {
    const val NUMBER_ROW = "number_row"
    const val TECH_STRIP = "tech_strip"
    const val CLIPBOARD_HISTORY = "clipboard_history"
    const val TECHNICAL_PATH = "technical_path"
    const val SHORTCUTS = "shortcuts"
}
