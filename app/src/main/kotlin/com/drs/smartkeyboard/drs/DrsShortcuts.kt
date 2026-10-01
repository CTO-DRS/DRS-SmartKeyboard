/*
 * Copyright (C) 2021-2025 The DRS Smart Keyboard Project
 * Copyright (C) 2025 DRS Smart Keyboard contributors
 */

package com.drs.smartkeyboard.drs

import java.text.DateFormat
import java.util.Date
import java.util.Locale

/**
 * Text expansion engine: an abbreviation committed as the current word is
 * replaced with its full text as soon as the user hits space. All matching is
 * local; nothing is recorded or transmitted.
 *
 * DRS templates: an expansion may contain variables like {date}, {time},
 * {hijri}, {clipboard} or {newline} which are expanded at insertion time
 * (see [expandTemplate]). Unknown variables are kept literally so the user
 * never loses text.
 */
object DrsShortcuts {

    /** Supported template variables, in display order for the editor UI. */
    val TEMPLATE_VARIABLES: List<Pair<String, String>> = listOf(
        "{date}" to "01/01/2030",
        "{time}" to "09:41",
        "{hijri}" to "١٤٥٠ رمضان",
        "{clipboard}" to "...",
        "{newline}" to "",
        "{cursor}" to "",
    )

    private val TEMPLATE_REGEX = Regex("\\{([a-zA-Z_]+)\\}")

    private val HIJRI_MONTHS_AR = listOf(
        "محرم", "صفر", "ربيع الأول", "ربيع الآخر", "جمادى الأولى", "جمادى الآخرة",
        "رجب", "شعبان", "رمضان", "شوال", "ذو القعدة", "ذو الحجة",
    )

    /** Returns true when the given expansion text contains at least one template variable. */
    fun isTemplate(expansion: String): Boolean = TEMPLATE_REGEX.containsMatchIn(expansion)

    /**
     * Expands all supported {variables} in [text]. Never throws - on any
     * failure the original text is returned untouched so typing is safe.
     */
    fun expandTemplate(text: String, clipboardText: String? = null): String {
        if (!isTemplate(text)) return text
        return try {
            TEMPLATE_REGEX.replace(text) { match ->
                when (match.groupValues[1].lowercase(Locale.ROOT)) {
                    "date" -> formatDate()
                    "time" -> formatTime()
                    "hijri" -> formatHijriDate()
                    "clipboard" -> clipboardText.orEmpty()
                    "newline" -> "\n"
                    else -> match.value
                }
            }
        } catch (_: Throwable) {
            text
        }
    }

    /** True when the given expansion text places the cursor via {cursor}. */
    fun hasCursorMarker(text: String): Boolean = text.contains(CURSOR_MARKER)

    const val CURSOR_MARKER = "{cursor}"

    /**
     * DRS v1.7.0: pure template validation for the diagnostics runner.
     * Every {variable} in every enabled shortcut's expansion must belong
     * to the known set (case-insensitive, matching expandTemplate).
     * Returns null when NO shortcut uses templates at all — the caller
     * treats that as a pass (no false alarm for plain-text users).
     */
    fun templatesValid(state: DrsState): Boolean? {
        var sawTemplate = false
        for (shortcut in state.shortcuts) {
            if (!shortcut.enabled) continue
            if (!isTemplate(shortcut.expansion)) continue
            sawTemplate = true
            for (match in TEMPLATE_REGEX.findAll(shortcut.expansion)) {
                val name = match.groupValues[1].lowercase(Locale.ROOT)
                val known = TEMPLATE_VARIABLES.any { (sample, _) ->
                    sample.removeSurrounding("{", "}").equals(name, ignoreCase = true)
                }
                if (!known) return false
            }
        }
        return if (sawTemplate) true else null
    }

    /**
     * Removes the first {cursor} marker from [text] and returns the cleaned
     * text plus the character offset (from the start) where the cursor must
     * land. When there is no marker the offset is -1 (= normal behavior:
     * cursor ends up after the inserted text).
     */
    fun extractCursorMarker(text: String): Pair<String, Int> {
        val index = text.indexOf(CURSOR_MARKER)
        if (index == -1) return text to -1
        return (text.substring(0, index) + text.substring(index + CURSOR_MARKER.length)) to index
    }

    private fun formatDate(): String =
        DateFormat.getDateInstance(DateFormat.DEFAULT, Locale.getDefault()).format(Date())

    private fun formatTime(): String =
        DateFormat.getTimeInstance(DateFormat.SHORT, Locale.getDefault()).format(Date())

    /**
     * Hijri (Islamic) date via android.icu, rendered with Arabic month names
     * when the device locale is Arabic. Falls back to an empty string on the
     * rare devices without ICU support so the template still commits.
     */
    private fun formatHijriDate(): String = try {
        val cal = android.icu.util.IslamicCalendar()
        val day = cal.get(android.icu.util.Calendar.DAY_OF_MONTH)
        val month = cal.get(android.icu.util.Calendar.MONTH)
        val year = cal.get(android.icu.util.Calendar.YEAR)
        "$day ${HIJRI_MONTHS_AR[month.coerceIn(0, 11)]} $year هـ"
    } catch (_: Throwable) {
        ""
    }

    /** Looks up an expansion for the given word (case-insensitive, exact). */
    fun findExpansion(word: String): String? {
        if (word.isEmpty()) return null
        val state = DrsStore.state.value
        if (!state.shortcutsEnabled || state.shortcuts.isEmpty()) return null
        val needle = word.lowercase(Locale.ROOT) // DRS p9 (D-3): ROOT — tr locale would map "DIL"→"dıl"
        return state.shortcuts.firstOrNull {
            it.shortcut == needle && it.enabled && isShortcutAvailable(state, it.scope)
        }?.expansion
    }

    /**
     * DRS v1.0.7: real availability filter for the unified system. A
     * shortcut is expandable only when its scope matches the active
     * system - and for the hybrid system, the current display level.
     * Unknown scope names fall back to BOTH so legacy data stays usable.
     */
    fun isShortcutAvailable(state: DrsState, scopeName: String): Boolean {
        val scope = DrsShortcutScope.entries.firstOrNull { it.name == scopeName }
            ?: DrsShortcutScope.BOTH
        return when (state.userPath) {
            DrsUserPath.NORMAL.name ->
                scope == DrsShortcutScope.NORMAL || scope == DrsShortcutScope.BOTH
            DrsUserPath.TECHNICAL.name ->
                scope == DrsShortcutScope.TECHNICAL || scope == DrsShortcutScope.BOTH
            else -> when (
                DrsUnifiedTools.viewForSystem(
                    state.userPath,
                    state.hybridViewMode,
                )
            ) {
                DrsHybridViewMode.SIMPLE ->
                    scope == DrsShortcutScope.NORMAL || scope == DrsShortcutScope.BOTH
                DrsHybridViewMode.ADVANCED ->
                    scope == DrsShortcutScope.TECHNICAL || scope == DrsShortcutScope.BOTH
                DrsHybridViewMode.DUAL -> true
            }
        }
    }

    fun add(shortcut: String, expansion: String, isTechnical: Boolean) {
        add(shortcut, expansion, isTechnical, DrsShortcutScope.BOTH)
    }

    fun add(
        shortcut: String,
        expansion: String,
        isTechnical: Boolean,
        scope: DrsShortcutScope,
    ) {
        val trimmedShortcut = shortcut.trim().lowercase(Locale.ROOT) // DRS p9 (D-3): ROOT, see findExpansion
        val trimmedExpansion = expansion.trim()
        if (trimmedShortcut.isEmpty() || trimmedExpansion.isEmpty()) return
        DrsStore.update { state ->
            val filtered = state.shortcuts.filter { it.shortcut != trimmedShortcut }
            val id = state.nextShortcutId
            state.copy(
                shortcuts = filtered + DrsShortcut(
                    id,
                    trimmedShortcut,
                    trimmedExpansion,
                    isTechnical,
                    scope = scope.name,
                ),
                nextShortcutId = id + 1,
            )
        }
    }

    /**
     * DRS v1.0.6: edits an existing shortcut in place, keyed by id.
     * Returns false (without touching anything) when the new abbreviation is
     * already owned by a DIFFERENT shortcut - previously this collision
     * silently created a duplicate entry with the old abbreviation left
     * behind. The caller surfaces the error to the user.
     */
    fun update(id: Long, shortcut: String, expansion: String, isTechnical: Boolean): Boolean {
        return update(id, shortcut, expansion, isTechnical, null)
    }

    /** DRS v1.0.7: edit that also persists the availability scope (null keeps it). */
    fun update(
        id: Long,
        shortcut: String,
        expansion: String,
        isTechnical: Boolean,
        scope: DrsShortcutScope?,
    ): Boolean {
        val trimmedShortcut = shortcut.trim().lowercase(Locale.ROOT) // DRS p9 (D-3): ROOT, also guards the collision check below
        val trimmedExpansion = expansion.trim()
        if (trimmedShortcut.isEmpty() || trimmedExpansion.isEmpty()) return false
        val current = DrsStore.state.value
        if (current.shortcuts.none { it.id == id }) return false
        if (current.shortcuts.any { it.id != id && it.shortcut == trimmedShortcut }) return false
        DrsStore.update { state ->
            state.copy(
                shortcuts = state.shortcuts.map { sc ->
                    if (sc.id == id) {
                        sc.copy(
                            shortcut = trimmedShortcut,
                            expansion = trimmedExpansion,
                            isTechnical = isTechnical,
                            scope = scope?.name ?: sc.scope,
                        )
                    } else {
                        sc
                    }
                },
            )
        }
        return true
    }

    /** DRS v1.0.6: enables/disables a single shortcut without removing it. */
    fun setItemEnabled(id: Long, enabled: Boolean) {
        DrsStore.update { state ->
            state.copy(
                shortcuts = state.shortcuts.map { sc ->
                    if (sc.id == id) sc.copy(enabled = enabled) else sc
                },
            )
        }
    }

    fun remove(id: Long) {
        DrsStore.update { state ->
            state.copy(shortcuts = state.shortcuts.filter { it.id != id })
        }
    }

    fun setEnabled(enabled: Boolean) {
        DrsStore.update { it.copy(shortcutsEnabled = enabled) }
    }

    /** Removes all shortcuts in one step (used by the shortcuts screen). */
    fun removeAll() {
        DrsStore.update { state ->
            state.copy(shortcuts = emptyList())
        }
    }
}
