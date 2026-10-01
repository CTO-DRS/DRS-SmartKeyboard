/*
 * Copyright (C) 2025 The DRS Smart Keyboard Project
 */

package com.drs.smartkeyboard.ime.keyboard

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * DRS M0.1 — the visual key-editor engine contracts, pinned on the REAL
 * qwerty and arabic_maghreb arrangement assets (round-trip guarantee) and
 * on synthetic polymorphic keys (the strict postcondition: a mutation
 * never writes a field into an object that did not carry it).
 */
class DrsM01LayoutEditorTest : FunSpec({

    val qwertyAsset = "src/main/assets/ime/keyboard/org.drs.layouts/layouts/characters/qwerty.json"
    val arabicAsset = "src/main/assets/ime/keyboard/org.drs.layouts.drs/layouts/characters/arabic_maghreb.json"

    fun parseAsset(path: String): LayoutArrangementEditor =
        LayoutArrangementEditor.parse(java.io.File(path).readText())
            ?: throw AssertionError("failed to parse real asset $path")

    fun structureOf(editor: LayoutArrangementEditor): String = editor.toJson().toString()

    context("round trip on the REAL qwerty asset") {
        test("parse → serialize re-parses identically (structure invariant)") {
            val raw = java.io.File(qwertyAsset).readText()
            val editor = LayoutArrangementEditor.parse(raw)!!
            val reparsed = LayoutArrangementEditor.parse(editor.toJson().toString())!!
            structureOf(reparsed) shouldBe structureOf(editor)
            // And the serialized tree parses back to the same counts.
            reparsed.rowCount shouldBe editor.rowCount
        }

        test("the real qwerty geometry is read correctly (row 0 starts with q…p)") {
            val editor = parseAsset(qwertyAsset)
            editor.rowCount shouldBe 3 // qwerty ships a 3-row letter arrangement
            editor.labelAt(0, 0) shouldBe "q"
            editor.codeAt(0, 0) shouldBe 113
            editor.labelAt(0, 9) shouldBe "p"
            editor.labelAt(1, 1) shouldBe "s"
        }

        test("editing the label of a real key survives serialization") {
            val editor = parseAsset(qwertyAsset)
            editor.setKeyLabel(0, 0, "Q2") shouldBe true
            val out = editor.toJson().toString()
            val root = Json.parseToJsonElement(out).jsonArray
            root[0].jsonArray[0].jsonObject["label"]!!.jsonPrimitive.content shouldBe "Q2"
            // code must be untouched
            root[0].jsonArray[0].jsonObject["code"]!!.jsonPrimitive.content shouldBe "113"
        }
    }

    context("round trip on the REAL arabic_maghreb asset") {
        test("arabic labels read and rewrite (ض is 1590)") {
            val editor = parseAsset(arabicAsset)
            editor.rowCount shouldNotBe 0
            editor.labelAt(0, 0) shouldBe "ض"
            editor.codeAt(0, 0) shouldBe 1590
            editor.setKeyCode(0, 0, 1600) shouldBe true
            editor.codeAt(0, 0) shouldBe 1600
            editor.labelAt(0, 0) shouldBe "ض" // label untouched by a code edit
        }
    }

    context("strict postcondition — no writing into objects lacking the field") {
        test("a case_selector (no label/code of its own) refuses both edits and stays byte-identical") {
            val editor = LayoutArrangementEditor(
                Json.parseToJsonElement(
                    """[ [ { "$": "case_selector", "lower": { "code": 59 }, "upper": { "code": 58 } } ] ]""",
                ),
            )
            editor.isLabelEditableAt(0, 0) shouldBe false
            editor.isCodeEditableAt(0, 0) shouldBe false
            val before = structureOf(editor)
            editor.setKeyLabel(0, 0, "x") shouldBe false
            editor.setKeyCode(0, 0, 1) shouldBe false
            structureOf(editor) shouldBe before
        }

        test("a numeric-only key object accepts a code edit but refuses a label edit") {
            val editor = LayoutArrangementEditor(
                Json.parseToJsonElement("""[ [ { "code": 32 } ] ]"""),
            )
            editor.isLabelEditableAt(0, 0) shouldBe false
            editor.isCodeEditableAt(0, 0) shouldBe true
            editor.setKeyCode(0, 0, 160) shouldBe true
            editor.setKeyLabel(0, 0, "x") shouldBe false
        }
    }

    context("structural operations — safe no-ops out of scope") {
        test("delete / add / move beyond the bounds are false, never throw") {
            val editor = parseAsset(qwertyAsset)
            val before = structureOf(editor)
            editor.deleteKey(99, 0) shouldBe false
            editor.deleteKey(0, 99) shouldBe false
            editor.addKey(99, 0) shouldBe false
            editor.addRow(editor.rowCount + 7) shouldBe false
            editor.deleteRow(editor.rowCount) shouldBe false
            editor.moveKey(0, 0, 77, 3) shouldBe false
            structureOf(editor) shouldBe before
        }

        test("deleteKey keeps the row, addKey appends at the row end") {
            val editor = parseAsset(qwertyAsset)
            val count = editor.keyCount(0)
            editor.deleteKey(0, 0) shouldBe true
            editor.keyCount(0) shouldBe count - 1
            editor.rowCount shouldBe 3 // row kept
            editor.addKey(0, editor.keyCount(0)) shouldBe true
            editor.keyCount(0) shouldBe count
            // the added key is the standard auto_text_key, label-editable
            val last = editor.keyCount(0) - 1
            editor.labelAt(0, last) shouldBe ""
            editor.isLabelEditableAt(0, last) shouldBe true
            editor.setKeyLabel(0, last, "ن") shouldBe true
            editor.labelAt(0, last) shouldBe "ن"
        }

        test("moveKey relocates within and across rows with clamped destination") {
            val editor = parseAsset(qwertyAsset)
            val q = editor.labelAt(0, 0)
            editor.moveKey(0, 0, 0, 4) shouldBe true
            editor.labelAt(0, 4) shouldBe q // inserted before old index 4 after removal
            editor.labelAt(0, 0) shouldNotBe q
            // clamp: a destination past the end still lands the key safely
            editor.moveKey(0, 4, 1, 999) shouldBe true
            editor.labelAt(1, editor.keyCount(1) - 1) shouldBe q
        }

        test("addRow/deleteRow manage rows") {
            val editor = parseAsset(qwertyAsset)
            val rows = editor.rowCount
            editor.addRow(rows) shouldBe true
            editor.rowCount shouldBe rows + 1
            editor.keyCount(rows) shouldBe 0
            editor.deleteRow(rows) shouldBe true
            editor.rowCount shouldBe rows
        }
    }
})
