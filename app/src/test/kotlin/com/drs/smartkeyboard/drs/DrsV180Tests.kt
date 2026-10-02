/*
 * Copyright (C) 2025-2026 The DRS Smart Keyboard Project
 */

package com.drs.smartkeyboard.drs

import com.drs.smartkeyboard.ime.keyboard.KeyboardState
import com.drs.smartkeyboard.ime.smartbar.quickaction.SmartToolCodes
import com.drs.smartkeyboard.ime.text.key.KeyCode
import com.drs.smartkeyboard.ime.text.keyboard.TextKeyData
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * DRS v1.8.0: the eighth comprehensive round — the tasks bar above the
 * suggestions strip for ALL user systems with a persisted master switch,
 * the side-pull handle that opens the pinned-tools drawer (pin/unpin/
 * reorder/hide for everyone, with pin-ensures-visible semantics), the
 * real on/off indicator mapping for toggle tools, two new catalogue tools
 * (quick actions + the actions editor, both real engine paths) and two
 * new pure text operations (digit/letter separation + punctuation
 * removal). Every behavior is pinned where the engine owns it.
 */
class DrsV180Tests : FunSpec({

    // -------------------------------------------------------------
    // Tasks bar above suggestions — the master switch
    // -------------------------------------------------------------

    test("the strip master switch defaults ON for everyone and survives round-trips") {
        // DRS v1.8.0: الشريط للجميع — a fresh state (and any old state
        // file without the new key) opens with the bar above suggestions.
        DrsState().unifiedStripEnabled shouldBe true
        val round = Json.decodeFromString<DrsState>(Json.encodeToString(DrsState()))
        round.unifiedStripEnabled shouldBe true
    }

    test("an old v1.7.0 state JSON decodes with the bar enabled") {
        // The upgrade path must turn the bar on exactly once, losslessly.
        val legacy = """
            {"version":1,"userPath":"NORMAL","unifiedStripForNormal":false}
        """.trimIndent()
        val state = Json { ignoreUnknownKeys = true }.decodeFromString<DrsState>(legacy)
        state.unifiedStripEnabled shouldBe true
        state.unifiedStripForNormal shouldBe false
    }

    // -------------------------------------------------------------
    // Pin-ensures-visible semantics (the drawer's pin contract)
    // -------------------------------------------------------------

    test("ensureVisibleOverride widens a TECHNICAL-default tool for the simple level") {
        val tool = DrsUnifiedTools.ALL.first { it.defaultView == DrsToolView.TECHNICAL }
        DrsUnifiedTools.ensureVisibleOverride(tool, DrsHybridViewMode.SIMPLE, null)
            .shouldBe(DrsToolView.BOTH.name)
    }

    test("ensureVisibleOverride widens a NORMAL-default tool for the advanced level") {
        val tool = DrsUnifiedTools.ALL.first { it.defaultView == DrsToolView.NORMAL }
        DrsUnifiedTools.ensureVisibleOverride(tool, DrsHybridViewMode.ADVANCED, null)
            .shouldBe(DrsToolView.BOTH.name)
    }

    test("ensureVisibleOverride leaves already-visible tools untouched") {
        val technical = DrsUnifiedTools.ALL.first { it.defaultView == DrsToolView.TECHNICAL }
        val normal = DrsUnifiedTools.ALL.first { it.defaultView == DrsToolView.NORMAL }
        // Established catalogue semantics: TECHNICAL tools hide from the
        // simple level, NORMAL tools hide from the advanced level, and DUAL
        // sees everything — so each of these is already rendered.
        DrsUnifiedTools.ensureVisibleOverride(technical, DrsHybridViewMode.DUAL, null).shouldBeNull()
        DrsUnifiedTools.ensureVisibleOverride(normal, DrsHybridViewMode.SIMPLE, null).shouldBeNull()
        DrsUnifiedTools.ensureVisibleOverride(technical, DrsHybridViewMode.ADVANCED, null).shouldBeNull()
        DrsUnifiedTools.ensureVisibleOverride(
            technical, DrsHybridViewMode.SIMPLE, DrsToolView.BOTH.name,
        ).shouldBeNull()
    }

    test("pinning a technical tool for a TYPICAL user really shows it in the strip") {
        // The drawer contract, end to end: without the widening the pin is
        // swallowed by the level filter; with it the tool renders.
        val tool = DrsUnifiedTools.ALL.first { it.defaultView == DrsToolView.TECHNICAL }
        val override = DrsUnifiedTools.ensureVisibleOverride(tool, DrsHybridViewMode.SIMPLE, null)
            .shouldNotBeNull()
        val swallowed = DrsUnifiedTools.resolveFor(
            view = DrsHybridViewMode.SIMPLE,
            hidden = emptyList(),
            pinned = listOf(tool.id),
            order = emptyList(),
            viewOverrides = emptyMap(),
        )
        swallowed.none { it.id == tool.id } shouldBe true
        val visible = DrsUnifiedTools.resolveFor(
            view = DrsHybridViewMode.SIMPLE,
            hidden = emptyList(),
            pinned = listOf(tool.id),
            order = emptyList(),
            viewOverrides = mapOf(tool.id to override),
        )
        visible.any { it.id == tool.id } shouldBe true
        // and the pin floats it into the head group (before unpinned tools).
        (visible.indexOfFirst { it.id == tool.id } <
            visible.indexOfFirst { it.id == "settings" }) shouldBe true
    }

    // -------------------------------------------------------------
    // The toggle-state indicator mapping
    // -------------------------------------------------------------

    test("toggleStateOf maps every toggle tool to its real state source") {
        val states = DrsUnifiedTools.ToggleStates(
            incognito = true,
            autocorrect = false,
            numberRow = true,
            smartbarVisible = false,
            floatingWindow = true,
        )
        DrsUnifiedTools.toggleStateOf("incognito", states) shouldBe true
        DrsUnifiedTools.toggleStateOf("autocorrect", states) shouldBe false
        DrsUnifiedTools.toggleStateOf("number_row", states) shouldBe true
        DrsUnifiedTools.toggleStateOf("smartbar_toggle", states) shouldBe false
        DrsUnifiedTools.toggleStateOf("floating_mode", states) shouldBe true
    }

    test("toggleStateOf returns null for non-toggle tools") {
        DrsUnifiedTools.toggleStateOf("emoji", DrsUnifiedTools.ToggleStates()).shouldBeNull()
        DrsUnifiedTools.toggleStateOf("copy", DrsUnifiedTools.ToggleStates()).shouldBeNull()
        DrsUnifiedTools.toggleStateOf("does_not_exist", DrsUnifiedTools.ToggleStates()).shouldBeNull()
    }

    // -------------------------------------------------------------
    // The two new catalogue tools (real engine paths)
    // -------------------------------------------------------------

    test("quick_actions opens the real overflow panel path") {
        val tool = DrsUnifiedTools.byId("quick_actions").shouldNotBeNull()
        tool.code shouldBe KeyCode.TOGGLE_ACTIONS_OVERFLOW
        SmartToolCodes shouldContain KeyCode.TOGGLE_ACTIONS_OVERFLOW
        TextKeyData.getCodeInfoAsTextKeyData(KeyCode.TOGGLE_ACTIONS_OVERFLOW)
            .shouldNotBeNull()
    }

    test("actions_editor opens the real actions editor path") {
        val tool = DrsUnifiedTools.byId("actions_editor").shouldNotBeNull()
        tool.code shouldBe KeyCode.TOGGLE_ACTIONS_EDITOR
        SmartToolCodes shouldContain KeyCode.TOGGLE_ACTIONS_EDITOR
        TextKeyData.getCodeInfoAsTextKeyData(KeyCode.TOGGLE_ACTIONS_EDITOR)
            .shouldNotBeNull()
    }

    test("the catalogue tail-append contract holds at 51 tools") {
        // DRS v1.15.0 appended the three smart panels after the pair, and
        // DRS v1.19.0 the split/merge keyboard pair after those, and the
        // DRS v1.2.0 release the smart numbers panel after those, and
        // DRS v1.3.0 the smart clipboard panel after those.
        DrsUnifiedTools.ALL.size shouldBe 51
        DrsUnifiedTools.ALL.last().id shouldBe "smart_clipboard_panel"
        DrsUnifiedTools.ALL[DrsUnifiedTools.ALL.size - 2].id shouldBe "smart_number_panel"
        DrsUnifiedTools.ALL[DrsUnifiedTools.ALL.size - 3].id shouldBe "merge_keyboard"
        DrsUnifiedTools.ALL[DrsUnifiedTools.ALL.size - 4].id shouldBe "split_keyboard"
        DrsUnifiedTools.ALL[DrsUnifiedTools.ALL.size - 9].id shouldBe "quick_actions"
        DrsUnifiedTools.ALL[DrsUnifiedTools.ALL.size - 8].id shouldBe "actions_editor"
        val ids = DrsUnifiedTools.ALL.map { it.id }
        ids.size shouldBe ids.toSet().size
    }

    // -------------------------------------------------------------
    // The tools-drawer keyboard-state flag
    // -------------------------------------------------------------

    test("isToolsDrawerVisible is an independent state bit") {
        val state = KeyboardState.new()
        state.isToolsDrawerVisible shouldBe false
        state.isActionsOverflowVisible shouldBe false
        state.isToolsDrawerVisible = true
        state.isToolsDrawerVisible shouldBe true
        state.isActionsOverflowVisible shouldBe false
        state.isActionsOverflowVisible = true
        state.isActionsOverflowVisible shouldBe true
        state.isToolsDrawerVisible shouldBe true
        state.isToolsDrawerVisible = false
        state.isActionsOverflowVisible shouldBe true
    }

    // -------------------------------------------------------------
    // The two new pure text operations
    // -------------------------------------------------------------

    test("SEPARATE_DIGIT_LETTERS splits both directions in Arabic and Latin") {
        val f = { t: String -> DrsTextTools.apply(DrsTextTool.SEPARATE_DIGIT_LETTERS, t) }
        f("12abc") shouldBe "12 abc"
        f("abc12") shouldBe "abc 12"
        f("٣س") shouldBe "٣ س"
        f("س٣") shouldBe "س ٣"
        f("س12اب3c") shouldBe "س 12 اب 3 c"
    }

    test("SEPARATE_DIGIT_LETTERS is idempotent and never touches punctuation") {
        val f = { t: String -> DrsTextTools.apply(DrsTextTool.SEPARATE_DIGIT_LETTERS, t) }
        f("12.5kg") shouldBe "12.5 kg" // the dot boundary is not a letter/digit edge
        f("12 abc") shouldBe "12 abc" // existing spaces are never doubled
        val once = f("عربي12English34عربي")
        f(once) shouldBe once
    }

    test("REMOVE_PUNCTUATION strips Arabic and Latin punctuation only") {
        val f = { t: String -> DrsTextTools.apply(DrsTextTool.REMOVE_PUNCTUATION, t) }
        f("مرحبا، العالم!") shouldBe "مرحبا العالم"
        f("Hello, world.") shouldBe "Hello world"
        f("(نص) «مقتبس»؟") shouldBe "نص مقتبس"
        f("عربي 123 English") shouldBe "عربي 123 English" // letters/digits/spaces survive
        f("$100") shouldBe "$100" // currency is a symbol, not punctuation
    }

    test("both new operations register inside the text-tool dispatch range") {
        val sep = DrsTextTool.fromCode(-644).shouldNotBeNull()
        val rem = DrsTextTool.fromCode(-645).shouldNotBeNull()
        sep shouldBe DrsTextTool.SEPARATE_DIGIT_LETTERS
        rem shouldBe DrsTextTool.REMOVE_PUNCTUATION
        // DRS v1.21.0: +4 mark-insertion tools (RLM/LRM/ZWJ/ZWNJ).
        (DrsTextTool.entries.size) shouldBe 51
    }
})
