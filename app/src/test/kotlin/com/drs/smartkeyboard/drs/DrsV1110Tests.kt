/*
 * Copyright (C) 2025-2026 The DRS Smart Keyboard Project
 */

package com.drs.smartkeyboard.drs

import com.drs.smartkeyboard.ime.clipboard.ClipEditorPopupPolicy
import com.drs.smartkeyboard.ime.clipboard.ClipEditorPopupSpec
import com.drs.smartkeyboard.ime.clipboard.ClipEditorRoute
import com.drs.smartkeyboard.ime.clipboard.ClipEditorPopupStore
import com.drs.smartkeyboard.ime.clipboard.provider.ClipboardItem
import com.drs.smartkeyboard.ime.clipboard.provider.ItemType
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

/**
 * DRS v1.11.0: the popup clipboard editor window contracts — the routing
 * policy (popup window vs the in-panel fallback), the honest fallback
 * contract after a launch attempt, the exactly-once handoff store between
 * the IME and the popup activity, and the popup window geometry spec.
 * Every rule is pinned where the engine owns it — pure, no fakes.
 */
class DrsV1110Tests : FunSpec({

    // -------------------------------------------------------------
    // The routing policy
    // -------------------------------------------------------------

    test("a real text item routes to the popup window") {
        ClipEditorPopupPolicy.routeFor(ItemType.TEXT, "نص للتحرير") shouldBe ClipEditorRoute.POPUP_WINDOW
        ClipEditorPopupPolicy.routeFor(ItemType.TEXT, "") shouldBe ClipEditorRoute.POPUP_WINDOW
    }

    test("non-text items and the defensive null-text case stay in-panel") {
        ClipEditorPopupPolicy.routeFor(ItemType.IMAGE, null) shouldBe ClipEditorRoute.IN_PANEL
        ClipEditorPopupPolicy.routeFor(ItemType.VIDEO, null) shouldBe ClipEditorRoute.IN_PANEL
        ClipEditorPopupPolicy.routeFor(ItemType.TEXT, null) shouldBe ClipEditorRoute.IN_PANEL
    }

    test("the fallback contract is honest in every direction") {
        // Intended popup + successful launch → popup stays.
        ClipEditorPopupPolicy.fallbackAfterLaunch(ClipEditorRoute.POPUP_WINDOW, true) shouldBe
            ClipEditorRoute.POPUP_WINDOW
        // Intended popup + blocked launch → degrades to panel, honestly.
        ClipEditorPopupPolicy.fallbackAfterLaunch(ClipEditorRoute.POPUP_WINDOW, false) shouldBe
            ClipEditorRoute.IN_PANEL
        // Intended panel never escalates on its own.
        ClipEditorPopupPolicy.fallbackAfterLaunch(ClipEditorRoute.IN_PANEL, true) shouldBe
            ClipEditorRoute.IN_PANEL
        ClipEditorPopupPolicy.fallbackAfterLaunch(ClipEditorRoute.IN_PANEL, false) shouldBe
            ClipEditorRoute.IN_PANEL
    }

    // -------------------------------------------------------------
    // The handoff store
    // -------------------------------------------------------------

    test("an empty store is inactive and consumes to nothing") {
        ClipEditorPopupStore.clear()
        ClipEditorPopupStore.isActive shouldBe false
        ClipEditorPopupStore.peek() shouldBe null
        ClipEditorPopupStore.consume() shouldBe null
    }

    test("put makes the store active and peek does not consume") {
        ClipEditorPopupStore.clear()
        val item = ClipboardItem.text("مرحبا").copy(id = 11, isPinned = true)
        ClipEditorPopupStore.put(item, openedAtMs = 1_234L)
        ClipEditorPopupStore.isActive shouldBe true
        ClipEditorPopupStore.peek()?.text shouldBe "مرحبا"
        ClipEditorPopupStore.isActive shouldBe true
        ClipEditorPopupStore.clear()
    }

    test("consume returns the exact request once and clears the slot") {
        ClipEditorPopupStore.clear()
        val item = ClipboardItem.text("hello popup").copy(id = 42, isPinned = false)
        ClipEditorPopupStore.put(item, openedAtMs = 7_000L)
        val consumed = ClipEditorPopupStore.consume()
        consumed!!.item.id shouldBe 42L
        consumed.item.isPinned shouldBe false
        consumed.item.text shouldBe "hello popup"
        consumed.text shouldBe "hello popup"
        consumed.openedAtMs shouldBe 7_000L
        ClipEditorPopupStore.isActive shouldBe false
        ClipEditorPopupStore.consume() shouldBe null
    }

    test("a second put overwrites the first pending request") {
        ClipEditorPopupStore.clear()
        val first = ClipboardItem.text("first").copy(id = 1)
        val second = ClipboardItem.text("second").copy(id = 2)
        ClipEditorPopupStore.put(first, openedAtMs = 1L)
        ClipEditorPopupStore.put(second, openedAtMs = 2L)
        val consumed = ClipEditorPopupStore.consume()
        consumed!!.item.text shouldBe "second"
        consumed.item.id shouldBe 2L
        ClipEditorPopupStore.clear()
    }

    test("the store holds null text defensively as an empty string") {
        ClipEditorPopupStore.clear()
        val item = ClipboardItem(type = ItemType.TEXT, text = null, uri = null, creationTimestampMs = 5L, isPinned = false, mimeTypes = listOf("text/plain"))
        ClipEditorPopupStore.put(item, openedAtMs = 9L)
        ClipEditorPopupStore.consume()!!.text shouldBe ""
        ClipEditorPopupStore.clear()
    }

    // -------------------------------------------------------------
    // The popup window geometry spec
    // -------------------------------------------------------------

    test("the card fractions stay inside the (0, 1] contract") {
        (ClipEditorPopupSpec.WIDTH_FRACTION > 0f && ClipEditorPopupSpec.WIDTH_FRACTION <= 1f) shouldBe true
        (ClipEditorPopupSpec.HEIGHT_FRACTION > 0f && ClipEditorPopupSpec.HEIGHT_FRACTION <= 1f) shouldBe true
        (ClipEditorPopupSpec.SCRIM_ALPHA >= 0f && ClipEditorPopupSpec.SCRIM_ALPHA <= 1f) shouldBe true
    }

    test("the px math applies the fractions with a guarded zero screen") {
        ClipEditorPopupSpec.cardWidthPx(1000) shouldBe 920
        ClipEditorPopupSpec.cardHeightPx(1000) shouldBe 860
        ClipEditorPopupSpec.cardWidthPx(0) shouldBe 0
        ClipEditorPopupSpec.cardHeightPx(0) shouldBe 0
        ClipEditorPopupSpec.cardWidthPx(-50) shouldBe 0
        ClipEditorPopupSpec.cardHeightPx(-50) shouldBe 0
    }
})
