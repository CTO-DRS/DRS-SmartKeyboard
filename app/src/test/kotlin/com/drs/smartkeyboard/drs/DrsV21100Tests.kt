/*
 * Copyright (C) 2026 The DRS Smart Keyboard Project
 */

package com.drs.smartkeyboard.drs

import android.text.InputType
import com.drs.smartkeyboard.ime.editor.InputAttributes
import com.drs.smartkeyboard.ime.smartbar.DrsContextualRank
import com.drs.smartkeyboard.ime.smartbar.DrsFieldKind
import com.drs.smartkeyboard.ime.smartbar.quickaction.QuickAction
import com.drs.smartkeyboard.ime.text.keyboard.TextKeyData
import io.kotest.core.spec.style.FunSpec
import io.kotest.datatest.withData
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe

/**
 * DRS v2.11.0 «المُرتّب السياقي الصادق» — contract tests for the honest
 * contextual ranker (roadmap M1.4).
 *
 *  - **classification**: the fine field kind reads DECLARED attributes
 *    only and never folds them away (URI stays URI, email stays email,
 *    a filter bar is finally seen) with the password claw outranking
 *    everything.
 *  - **the four honesty oaths**: no invented tiles (missing actions are
 *    never added), strict permutation (same multiset, same count),
 *    user-arrangement is the base truth (stability of the un-promoted
 *    tail), and password fields never promote clipboard or voice.
 *  - **determinism**: the same kind + signals + base produce the exact
 *    same order, forever.
 */
class DrsV21100Tests : FunSpec({

    // ------------------------------------------------------------------
    // Fixtures
    // ------------------------------------------------------------------

    fun attrs(inputType: Int): InputAttributes = InputAttributes.wrap(inputType)

    fun action(data: TextKeyData): QuickAction = QuickAction.InsertKey(data)

    /** A base arrangement covering EVERY promotion matrix code + fillers. */
    val base = listOf(
        action(TextKeyData.SETTINGS),
        action(TextKeyData.UNDO),
        action(TextKeyData.REDO),
        action(TextKeyData.CLIPBOARD_COPY),
        action(TextKeyData.CLIPBOARD_CUT),
        action(TextKeyData.CLIPBOARD_PASTE),
        action(TextKeyData.CLIPBOARD_SHARE),
        action(TextKeyData.CLIPBOARD_SELECT_ALL),
        action(TextKeyData.CLIPBOARD_CLEAR_PRIMARY_CLIP),
        action(TextKeyData.TOGGLE_INCOGNITO_MODE),
        action(TextKeyData.INSERT_DATE_TIME),
        action(TextKeyData.MOVE_WORD_LEFT),
        action(TextKeyData.MOVE_WORD_RIGHT),
        action(TextKeyData.MOVE_START_OF_LINE),
        action(TextKeyData.MOVE_END_OF_LINE),
        action(TextKeyData.FORWARD_DELETE),
    )

    val none = DrsContextualRank.Signals.NONE
    val selected = DrsContextualRank.Signals(hasSelection = true, clipboardFresh = false)
    val fresh = DrsContextualRank.Signals(hasSelection = false, clipboardFresh = true)

    /** The base-relative order of [codes] among the NOT-promoted tail. */
    fun tailOrder(ranked: List<QuickAction>, promoted: Set<Int>): List<Int> =
        ranked.filterIndexed { i, _ -> i >= promoted.size }.map { DrsContextualRank.codeOf(it) }

    // ------------------------------------------------------------------
    // 1) classification — the lossless field reading
    // ------------------------------------------------------------------

    context("classification matrix") {
        withData(
            Triple(
                "password",
                (InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD),
                DrsFieldKind.PASSWORD,
            ),
            Triple(
                "visible password",
                (InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD),
                DrsFieldKind.PASSWORD,
            ),
            Triple(
                "web password",
                (InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD),
                DrsFieldKind.PASSWORD,
            ),
            Triple(
                "number",
                InputType.TYPE_CLASS_NUMBER,
                DrsFieldKind.NUMBER,
            ),
            Triple(
                "phone",
                InputType.TYPE_CLASS_PHONE,
                DrsFieldKind.NUMBER,
            ),
            Triple(
                "datetime",
                InputType.TYPE_CLASS_DATETIME,
                DrsFieldKind.NUMBER,
            ),
            Triple(
                "uri",
                (InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI),
                DrsFieldKind.URI,
            ),
            Triple(
                "email",
                (InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS),
                DrsFieldKind.EMAIL,
            ),
            Triple(
                "web email",
                (InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS),
                DrsFieldKind.EMAIL,
            ),
            Triple(
                "short message",
                (InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_SHORT_MESSAGE),
                DrsFieldKind.MESSAGE,
            ),
            Triple(
                "long message",
                (InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_LONG_MESSAGE),
                DrsFieldKind.MESSAGE,
            ),
            Triple(
                "filter",
                (InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_FILTER),
                DrsFieldKind.SEARCH_FILTER,
            ),
            Triple(
                "plain text",
                InputType.TYPE_CLASS_TEXT,
                DrsFieldKind.GENERAL,
            ),
            Triple(
                "null type",
                0,
                DrsFieldKind.GENERAL,
            ),
        ) { (_, inputType, expected) ->
            DrsFieldKind.kindOf(attrs(inputType)) shouldBe expected
        }

        test("coding package hint alone yields CODE") {
            DrsFieldKind.kindOf(attrs(InputType.TYPE_CLASS_TEXT), codingPackageHint = true) shouldBe
                DrsFieldKind.CODE
        }

        test("multiline plain text yields MULTILINE") {
            val type = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            DrsFieldKind.kindOf(attrs(type)) shouldBe DrsFieldKind.MULTILINE
        }

        test("the password claw outranks even a numeric OTP field") {
            val type = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
            // A number-password field DECLARES the password variation — the
            // claw wins, exactly like the coarse context detector. The
            // smart number panel reads its own OTP classification
            // independently; the ranker follows the privacy claw.
            DrsFieldKind.kindOf(attrs(type)) shouldBe DrsFieldKind.PASSWORD
        }

        test("URI stays URI even when multiline or hosted by a coding app") {
            val type = InputType.TYPE_CLASS_TEXT or
                InputType.TYPE_TEXT_VARIATION_URI or
                InputType.TYPE_TEXT_FLAG_MULTI_LINE
            DrsFieldKind.kindOf(attrs(type), codingPackageHint = true) shouldBe DrsFieldKind.URI
        }

        test("email stays email when multiline") {
            val type = InputType.TYPE_CLASS_TEXT or
                InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS or
                InputType.TYPE_TEXT_FLAG_MULTI_LINE
            DrsFieldKind.kindOf(attrs(type)) shouldBe DrsFieldKind.EMAIL
        }

        test("filter outranks the multiline flag") {
            val type = InputType.TYPE_CLASS_TEXT or
                InputType.TYPE_TEXT_VARIATION_FILTER or
                InputType.TYPE_TEXT_FLAG_MULTI_LINE
            DrsFieldKind.kindOf(attrs(type)) shouldBe DrsFieldKind.SEARCH_FILTER
        }
    }

    // ------------------------------------------------------------------
    // 2) the four honesty oaths
    // ------------------------------------------------------------------

    context("honesty oaths") {
        withData(
            DrsFieldKind.URI,
            DrsFieldKind.EMAIL,
            DrsFieldKind.MESSAGE,
            DrsFieldKind.SEARCH_FILTER,
            DrsFieldKind.NUMBER,
            DrsFieldKind.PASSWORD,
            DrsFieldKind.CODE,
            DrsFieldKind.MULTILINE,
            DrsFieldKind.GENERAL,
        ) { kind ->
            for (signals in listOf(none, selected, fresh)) {
                val ranked = DrsContextualRank.rank(base, kind, signals)
                // Oath 2: strict permutation — same tiles, same count.
                DrsContextualRank.isPermutationOf(base, ranked) shouldBe true
                ranked shouldHaveSize base.size
                // Oath 1: no invented tiles — nothing outside the base.
                ranked.all { it in base } shouldBe true
            }
        }

        test("missing actions are never added — a bare arrangement grows nothing") {
            val bare = listOf(action(TextKeyData.SETTINGS), action(TextKeyData.ARROW_LEFT))
            for (kind in DrsFieldKind.entries) {
                val ranked = DrsContextualRank.rank(bare, kind, fresh)
                DrsContextualRank.isPermutationOf(bare, ranked) shouldBe true
                ranked shouldHaveSize bare.size
            }
        }

        test("un-promoted tail keeps the exact base relative order") {
            val ranked = DrsContextualRank.rank(base, DrsFieldKind.URI, none)
            val promoted = DrsContextualRank.promotedCodes(DrsFieldKind.URI, none).toSet()
            val expectedTail = base.map { DrsContextualRank.codeOf(it) }
                .filter { it !in promoted }
            tailOrder(ranked, promoted) shouldBe expectedTail
        }

        test("duplicated promoted action keeps every copy — count never changes") {
            val dup = listOf(
                action(TextKeyData.UNDO),
                action(TextKeyData.SETTINGS),
                action(TextKeyData.UNDO),
            )
            val ranked = DrsContextualRank.rank(dup, DrsFieldKind.PASSWORD, none)
            ranked shouldHaveSize dup.size
            DrsContextualRank.isPermutationOf(dup, ranked) shouldBe true
            // First copy promoted, the second keeps its base position.
            DrsContextualRank.codeOf(ranked[0]) shouldBe TextKeyData.UNDO.code
            DrsContextualRank.codeOf(ranked[2]) shouldBe TextKeyData.UNDO.code
        }

        test("InsertText actions are never promoted — they stay put") {
            val insert = QuickAction.InsertText("مرحبا")
            val mixed = listOf(action(TextKeyData.UNDO), insert, action(TextKeyData.REDO))
            val ranked = DrsContextualRank.rank(mixed, DrsFieldKind.MESSAGE, none)
            // INSERT_DATE_TIME is NOT in the base → never invented; UNDO and
            // REDO are promoted in base order; the InsertText tile lands
            // last with its content untouched.
            ranked[0] shouldBe action(TextKeyData.UNDO)
            ranked[1] shouldBe action(TextKeyData.REDO)
            ranked[2] shouldBe insert
        }

        test("empty base ranks to empty — no crash, no fabrication") {
            DrsContextualRank.rank(emptyList(), DrsFieldKind.URI, fresh) shouldHaveSize 0
        }
    }

    // ------------------------------------------------------------------
    // 3) the promotion matrix itself
    // ------------------------------------------------------------------

    context("promotion matrix") {
        test("URI promotes paste, share, select-all — in that order") {
            DrsContextualRank.promotedCodes(DrsFieldKind.URI, none) shouldContainExactly listOf(
                TextKeyData.CLIPBOARD_PASTE.code,
                TextKeyData.CLIPBOARD_SHARE.code,
                TextKeyData.CLIPBOARD_SELECT_ALL.code,
            )
        }

        test("EMAIL promotes paste then undo then redo") {
            DrsContextualRank.promotedCodes(DrsFieldKind.EMAIL, none) shouldContainExactly listOf(
                TextKeyData.CLIPBOARD_PASTE.code,
                TextKeyData.UNDO.code,
                TextKeyData.REDO.code,
            )
        }

        test("MESSAGE promotes insert-date-time then undo/redo") {
            DrsContextualRank.promotedCodes(DrsFieldKind.MESSAGE, none) shouldContainExactly listOf(
                TextKeyData.INSERT_DATE_TIME.code,
                TextKeyData.UNDO.code,
                TextKeyData.REDO.code,
            )
        }

        test("SEARCH_FILTER promotes paste, select-all, clear-primary-clip") {
            DrsContextualRank.promotedCodes(DrsFieldKind.SEARCH_FILTER, none) shouldContainExactly listOf(
                TextKeyData.CLIPBOARD_PASTE.code,
                TextKeyData.CLIPBOARD_SELECT_ALL.code,
                TextKeyData.CLIPBOARD_CLEAR_PRIMARY_CLIP.code,
            )
        }

        test("NUMBER and GENERAL stay silent — the honest desert") {
            DrsContextualRank.promotedCodes(DrsFieldKind.NUMBER, fresh) shouldContainExactly emptyList()
            DrsContextualRank.promotedCodes(DrsFieldKind.GENERAL, fresh) shouldContainExactly emptyList()
        }

        test("PASSWORD promotes ONLY undo/redo/incognito — boosts unreachable") {
            val codes = DrsContextualRank.promotedCodes(DrsFieldKind.PASSWORD, selected.copy(clipboardFresh = true))
            codes shouldContainExactly listOf(
                TextKeyData.UNDO.code,
                TextKeyData.REDO.code,
                TextKeyData.TOGGLE_INCOGNITO_MODE.code,
            )
        }

        test("password fields NEVER promote clipboard — even fresh and selected") {
            val ranked = DrsContextualRank.rank(base, DrsFieldKind.PASSWORD, fresh.copy(hasSelection = true))
            val pasteIndex = ranked.indexOfFirst {
                DrsContextualRank.codeOf(it) == TextKeyData.CLIPBOARD_PASTE.code
            }
            // Front = UNDO, REDO, INCOGNITO (3 tiles). UNDO and REDO sat
            // before PASTE in the base, so PASTE lands at 3 + (5 - 2) = 6 —
            // shifted only by the honest permutation, never promoted.
            pasteIndex shouldBe 6
            DrsContextualRank.codeOf(ranked[0]) shouldBe TextKeyData.UNDO.code
        }

        test("selection boosts copy/cut to the front in a URI field") {
            val ranked = DrsContextualRank.rank(base, DrsFieldKind.URI, selected)
            DrsContextualRank.codeOf(ranked[0]) shouldBe TextKeyData.CLIPBOARD_COPY.code
            DrsContextualRank.codeOf(ranked[1]) shouldBe TextKeyData.CLIPBOARD_CUT.code
        }

        test("fresh clipboard boosts paste/share to the front in a chat box") {
            val ranked = DrsContextualRank.rank(base, DrsFieldKind.MESSAGE, fresh)
            DrsContextualRank.codeOf(ranked[0]) shouldBe TextKeyData.CLIPBOARD_PASTE.code
            DrsContextualRank.codeOf(ranked[1]) shouldBe TextKeyData.CLIPBOARD_SHARE.code
        }

        test("CODE promotes word navigation plus undo/redo") {
            val codes = DrsContextualRank.promotedCodes(DrsFieldKind.CODE, none)
            codes shouldContainExactly listOf(
                TextKeyData.MOVE_WORD_LEFT.code,
                TextKeyData.MOVE_WORD_RIGHT.code,
                TextKeyData.UNDO.code,
                TextKeyData.REDO.code,
            )
        }

        test("MULTILINE promotes line jumps plus undo/redo") {
            val codes = DrsContextualRank.promotedCodes(DrsFieldKind.MULTILINE, none)
            codes shouldContainExactly listOf(
                TextKeyData.MOVE_START_OF_LINE.code,
                TextKeyData.MOVE_END_OF_LINE.code,
                TextKeyData.UNDO.code,
                TextKeyData.REDO.code,
            )
        }

        test("every matrix code is a real shipped keycode — no phantoms") {
            for (kind in DrsFieldKind.entries) {
                for (signals in listOf(none, selected, fresh)) {
                    val codes = DrsContextualRank.promotedCodes(kind, signals)
                    codes.all { it in DrsContextualRank.FIXTURE_CODES } shouldBe true
                }
            }
        }

        test("the fresh window is exactly 30 seconds") {
            DrsContextualRank.FRESH_CLIPBOARD_WINDOW_MS shouldBe 30_000L
        }
    }

    // ------------------------------------------------------------------
    // 4) determinism — same inputs, same order, forever
    // ------------------------------------------------------------------

    context("determinism") {
        withData(
            DrsFieldKind.URI,
            DrsFieldKind.EMAIL,
            DrsFieldKind.MESSAGE,
            DrsFieldKind.SEARCH_FILTER,
            DrsFieldKind.PASSWORD,
            DrsFieldKind.CODE,
            DrsFieldKind.MULTILINE,
        ) { kind ->
            val signals = DrsContextualRank.Signals(hasSelection = true, clipboardFresh = true)
            val first = DrsContextualRank.rank(base, kind, signals)
            val second = DrsContextualRank.rank(base, kind, signals)
            first shouldContainExactly second
        }

        test("ranking the ranked list is a fixed point — idempotent") {
            val signals = DrsContextualRank.Signals(hasSelection = false, clipboardFresh = true)
            val once = DrsContextualRank.rank(base, DrsFieldKind.URI, signals)
            val twice = DrsContextualRank.rank(once, DrsFieldKind.URI, signals)
            once shouldContainExactly twice
        }
    }
})
