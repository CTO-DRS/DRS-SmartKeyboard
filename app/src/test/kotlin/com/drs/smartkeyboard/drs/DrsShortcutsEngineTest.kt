/*
 * Copyright (C) 2025-2026 The DRS Smart Keyboard Project
 */

package com.drs.smartkeyboard.drs

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import java.util.Locale

/**
 * DRS p9: the text-expansion engine — gates, case-insensitive matching,
 * scope filtering, first-match-wins, the update() collision contract,
 * template expansion/validation and the {cursor} marker — plus the
 * DRS p9 (D-3) Turkish-I regression pin (matching is Locale.ROOT-based,
 * so a tr default locale can no longer corrupt it).
 */
class DrsShortcutsEngineTest : FunSpec({

    fun sc(
        id: Long,
        shortcut: String,
        expansion: String,
        enabled: Boolean = true,
        scope: DrsShortcutScope = DrsShortcutScope.BOTH,
    ) = DrsShortcut(
        id = id, shortcut = shortcut, expansion = expansion,
        isTechnical = false, enabled = enabled, scope = scope.name,
    )

    fun seed(
        path: String = DrsUserPath.NORMAL.name,
        shortcuts: List<DrsShortcut> = listOf(sc(1, "brb", "be right back")),
        shortcutsEnabled: Boolean = true,
        hybridViewMode: String = DrsHybridViewMode.DUAL.name,
    ) {
        runBlocking {
            DrsStore.updateNow { _ ->
                DrsState(
                    onboardingDone = true,
                    userPath = path,
                    shortcuts = shortcuts,
                    shortcutsEnabled = shortcutsEnabled,
                    hybridViewMode = hybridViewMode,
                )
            }
        }
    }

    test("the master switch and the per-item switch both gate expansion") {
        seed(shortcutsEnabled = false)
        DrsShortcuts.findExpansion("brb") shouldBe null
        seed(shortcuts = listOf(sc(1, "brb", "be right back", enabled = false)))
        DrsShortcuts.findExpansion("brb") shouldBe null
    }

    test("an empty word never expands") {
        seed()
        DrsShortcuts.findExpansion("") shouldBe null
    }

    test("matching is case-insensitive against the stored lowercase abbreviation") {
        seed()
        DrsShortcuts.findExpansion("BRB") shouldBe "be right back"
        DrsShortcuts.findExpansion("Brb") shouldBe "be right back"
    }

    test("the scope filter really filters by the active system") {
        val normalOnly = sc(1, "sig", "signature", scope = DrsShortcutScope.NORMAL)
        val both = sc(2, "brb", "be right back", scope = DrsShortcutScope.BOTH)
        seed(path = DrsUserPath.TECHNICAL.name, shortcuts = listOf(normalOnly, both))
        DrsShortcuts.findExpansion("sig") shouldBe null
        DrsShortcuts.findExpansion("brb") shouldBe "be right back"
        // the pure availability filter agrees with the end-to-end result
        val state = DrsStore.state.value
        DrsShortcuts.isShortcutAvailable(state, DrsShortcutScope.NORMAL.name).shouldBeFalse()
        DrsShortcuts.isShortcutAvailable(state, DrsShortcutScope.BOTH.name).shouldBeTrue()
    }

    test("the first stored entry wins for duplicate abbreviations") {
        seed(shortcuts = listOf(sc(1, "dup", "first wins"), sc(2, "dup", "second loses")))
        DrsShortcuts.findExpansion("dup") shouldBe "first wins"
    }

    test("renaming onto another item's abbreviation is refused without mutation") {
        seed(shortcuts = listOf(sc(1, "one", "first"), sc(2, "two", "second")))
        DrsShortcuts.update(2, "one", "hijack", false).shouldBeFalse()
        DrsStore.state.value.shortcuts.map { it.shortcut to it.expansion } shouldBe
            listOf("one" to "first", "two" to "second")
    }

    test("renaming to the item's own abbreviation succeeds and persists after a flush") {
        seed(shortcuts = listOf(sc(1, "one", "first"), sc(2, "two", "second")))
        DrsShortcuts.update(2, "two", "renamed", false).shouldBeTrue()
        runBlocking { DrsStore.updateNow { it } }
        DrsStore.state.value.shortcuts.first { it.id == 2L }.expansion shouldBe "renamed"
    }

    test("blank abbreviations, blank expansions and unknown ids are refused") {
        seed(shortcuts = listOf(sc(1, "one", "first")))
        DrsShortcuts.update(1, "   ", "x", false).shouldBeFalse()
        DrsShortcuts.update(1, "one", "", false).shouldBeFalse()
        DrsShortcuts.update(99, "one", "x", false).shouldBeFalse()
        DrsStore.state.value.shortcuts.single().expansion shouldBe "first"
    }

    test("date and time templates expand non-empty and case-insensitively") {
        val date = DrsShortcuts.expandTemplate("{date}")
        date.isNotEmpty().shouldBeTrue()
        val time = DrsShortcuts.expandTemplate("{time}")
        time.isNotEmpty().shouldBeTrue()
        DrsShortcuts.expandTemplate("{DATE}") shouldBe date
        DrsShortcuts.expandTemplate("{Time}") shouldBe time
    }

    test("clipboard, newline, unknown variables and {cursor} behave literally") {
        DrsShortcuts.expandTemplate("a{clipboard}b", null) shouldBe "ab"
        DrsShortcuts.expandTemplate("a{clipboard}b", "CLIP") shouldBe "aCLIPb"
        DrsShortcuts.expandTemplate("x{newline}y") shouldBe "x\ny"
        DrsShortcuts.expandTemplate("keep {foo} here") shouldBe "keep {foo} here"
        // {cursor} is NOT expanded by expandTemplate — the editor strips it
        DrsShortcuts.expandTemplate("a{cursor}b") shouldBe "a{cursor}b"
    }

    test("extractCursorMarker removes the first marker at its offset") {
        DrsShortcuts.extractCursorMarker("ab{cursor}cd") shouldBe ("abcd" to 2)
        DrsShortcuts.extractCursorMarker("{cursor}") shouldBe ("" to 0)
        DrsShortcuts.extractCursorMarker("a{cursor}b{cursor}c") shouldBe ("ab{cursor}c" to 1)
        DrsShortcuts.extractCursorMarker("plain") shouldBe ("plain" to -1)
    }

    test("templatesValid returns null when no enabled shortcut uses templates") {
        seed(shortcuts = listOf(sc(1, "brb", "plain text")))
        DrsShortcuts.templatesValid(DrsStore.state.value) shouldBe null
        // a disabled item's broken template must not raise the alarm either
        seed(shortcuts = listOf(sc(1, "brb", "bad {foo}", enabled = false)))
        DrsShortcuts.templatesValid(DrsStore.state.value) shouldBe null
    }

    test("templatesValid accepts known variables in any case and rejects unknown ones") {
        seed(shortcuts = listOf(sc(1, "d", "{DATE} {time} {clipboard}")))
        DrsShortcuts.templatesValid(DrsStore.state.value).shouldBeTrue()
        seed(shortcuts = listOf(sc(1, "d", "{DATE} {foo}")))
        DrsShortcuts.templatesValid(DrsStore.state.value).shouldBeFalse()
    }

    test("matching and collision checks are ROOT-based — a tr locale cannot corrupt them") {
        // DRS p9 (D-3) regression pin: with the default locale set to
        // Turkish, "DIL".lowercase() used to become "dıl" and break every
        // match and the update() collision check. The engine now lowercases
        // with Locale.ROOT, so the default locale must not matter at all.
        val original = Locale.getDefault()
        Locale.setDefault(Locale.forLanguageTag("tr"))
        try {
            seed(shortcuts = listOf(sc(1, "dil", "language"), sc(2, "gif", "image")))
            DrsShortcuts.findExpansion("DIL") shouldBe "language"
            // the collision check lowercases with ROOT too, so this collides
            DrsShortcuts.update(2, "DIL", "hijack", false).shouldBeFalse()
            DrsStore.state.value.shortcuts.map { it.expansion } shouldBe
                listOf("language", "image")
        } finally {
            Locale.setDefault(original)
        }
    }
})
