/*
 * Copyright (C) 2025 The DRS Smart Keyboard Project
 */

package com.drs.smartkeyboard.ime.keyboard

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * DRS M0.1 — pure-JVM editor for a keyboard layout arrangement (the JSON
 * array-of-rows-of-keys structure stored in `layouts/<type>/<id>.json`).
 *
 * The editor works on the raw polymorphic JSON tree, NOT on deserialized
 * key models, because arrangement files legitimately mix key families
 * (auto_text_key, auto_key_id, case_selector, ...) and a lossy model
 * round-trip would silently drop unknown variants. Every mutation is a
 * polymorphic JSON patch governed by ONE strict postcondition:
 *
 *   «لا كتابة إلا في كائن يحمل الحقل أصلًا» — a mutation may only write
 *   a field into a key object that ALREADY carries that field as a JSON
 *   primitive. Anything else is a documented, safe, value-returning
 *   no-op (false) — never an exception, never a corrupted selector.
 *
 * All row/column coordinates are also safe no-ops when out of range, so
 * the UI layer can drive the editor directly from user input without
 * pre-validating every tap.
 *
 * Round-trip guarantee (pinned by DrsM01LayoutEditorTest on the REAL
 * qwerty and arabic_maghreb assets): parse → any sequence of operations
 * → serialize re-parses to the same structure, and untouched keys keep
 * their exact original JSON (field order, whitespace-independent).
 */
class LayoutArrangementEditor(root: JsonElement) {

    companion object {
        /**
         * The standard key inserted by [addKey]: an auto_text_key with the
         * unspecified code and an empty label — the neutral starting point
         * the visual editor immediately relabels/recodes.
         */
        val STANDARD_AUTO_TEXT_KEY: JsonObject = JsonObject(
            linkedMapOf(
                "\$" to JsonPrimitive("auto_text_key"),
                "code" to JsonPrimitive(0), // KeyCode.UNSPECIFIED
                "label" to JsonPrimitive(""),
            ),
        )

        private const val AUTO_TEXT_KEY_TYPE = "auto_text_key"
        private const val TYPE_FIELD = "\$"
        private const val LABEL_FIELD = "label"
        private const val CODE_FIELD = "code"

        /** Parses arrangement JSON text; null when the text is not an arrangement. */
        fun parse(text: String): LayoutArrangementEditor? = runCatching {
            LayoutArrangementEditor(Json.parseToJsonElement(text))
        }.getOrNull()
    }

    private val rows: MutableList<MutableList<JsonElement>> = decode(root)

    private fun decode(root: JsonElement): MutableList<MutableList<JsonElement>> {
        require(root is JsonArray) { "arrangement root must be a JSON array" }
        return root.mapTo(mutableListOf()) { row ->
            require(row is JsonArray) { "arrangement row must be a JSON array" }
            row.toMutableList()
        }
    }

    // ------------------------------------------------------------------
    // Read API
    // ------------------------------------------------------------------

    val rowCount: Int get() = rows.size

    fun keyCount(row: Int): Int = rows.getOrNull(row)?.size ?: 0

    /** The raw key element at the position, or null when out of range. */
    fun keyAt(row: Int, index: Int): JsonElement? = rows.getOrNull(row)?.getOrNull(index)

    /**
     * The raw label string of the key at the position — read straight from
     * the JSON (no ICU, no computing evaluator), or null when the key has
     * no label primitive (selectors, spacers).
     */
    fun labelAt(row: Int, index: Int): String? {
        val value = (keyAt(row, index) as? JsonObject)?.get(LABEL_FIELD) ?: return null
        return (value as? JsonPrimitive)?.takeIf { it.isString }?.content
    }

    /** The integer code of the key at the position, or null when absent/non-numeric. */
    fun codeAt(row: Int, index: Int): Int? {
        val value = (keyAt(row, index) as? JsonObject)?.get(CODE_FIELD) ?: return null
        return (value as? JsonPrimitive)?.takeIf { !it.isString }?.content?.toIntOrNull()
    }

    /** Whether the key carries a string label primitive this editor may rewrite. */
    fun isLabelEditableAt(row: Int, index: Int): Boolean =
        isStringPrimitiveAt(row, index, LABEL_FIELD)

    /** Whether the key carries a numeric code primitive this editor may rewrite. */
    fun isCodeEditableAt(row: Int, index: Int): Boolean =
        isNumericPrimitiveAt(row, index, CODE_FIELD)

    private fun isStringPrimitiveAt(row: Int, index: Int, field: String): Boolean {
        val value = (keyAt(row, index) as? JsonObject)?.get(field) ?: return false
        return value is JsonPrimitive && value.isString
    }

    private fun isNumericPrimitiveAt(row: Int, index: Int, field: String): Boolean {
        val value = (keyAt(row, index) as? JsonObject)?.get(field) ?: return false
        if (value !is JsonPrimitive || value.isString) return false
        return value.content.toIntOrNull() != null
    }

    // ------------------------------------------------------------------
    // Write API — every operation returns true iff it really mutated.
    // ------------------------------------------------------------------

    /**
     * Strict postcondition: rewrites the label ONLY when the key object
     * already carries a string `label` primitive. Returns false (no-op)
     * for out-of-range positions, selectors, or keys without a label.
     */
    fun setKeyLabel(row: Int, index: Int, label: String): Boolean {
        val obj = keyAt(row, index) as? JsonObject ?: return false
        if (!isLabelEditableAt(row, index)) return false
        rows[row][index] = JsonObject(LinkedHashMap(obj).apply { put(LABEL_FIELD, JsonPrimitive(label)) })
        return true
    }

    /**
     * Strict postcondition: rewrites the code ONLY when the key object
     * already carries a numeric `code` primitive. Returns false (no-op)
     * otherwise.
     */
    fun setKeyCode(row: Int, index: Int, code: Int): Boolean {
        val obj = keyAt(row, index) as? JsonObject ?: return false
        if (!isCodeEditableAt(row, index)) return false
        rows[row][index] = JsonObject(LinkedHashMap(obj).apply { put(CODE_FIELD, JsonPrimitive(code)) })
        return true
    }

    /**
     * Removes the key at the position. The row itself is kept (possibly
     * empty) so the operation stays reversible via [addKey] / visible via
     * [deleteRow].
     */
    fun deleteKey(row: Int, index: Int): Boolean {
        val r = rows.getOrNull(row) ?: return false
        if (index !in r.indices) return false
        r.removeAt(index)
        return true
    }

    /**
     * Inserts [STANDARD_AUTO_TEXT_KEY] before [index] (index == keyCount →
     * append) into an existing row. Returns false when the row is out of
     * range or the index is negative or beyond append position.
     */
    fun addKey(row: Int, index: Int): Boolean {
        val r = rows.getOrNull(row) ?: return false
        if (index !in 0..r.size) return false
        r.add(index, STANDARD_AUTO_TEXT_KEY)
        return true
    }

    /**
     * Moves a key: removes it from (fromRow, fromIndex) then inserts it
     * before toIndex of the destination row AS IT EXISTS AFTER the removal
     * (same-row moves shift accordingly). The destination index clamps to
     * the destination row bounds so a long drag never loses the key.
     * Returns false when the source position is out of range.
     */
    fun moveKey(fromRow: Int, fromIndex: Int, toRow: Int, toIndex: Int): Boolean {
        val src = rows.getOrNull(fromRow) ?: return false
        if (fromIndex !in src.indices) return false
        val dst = rows.getOrNull(toRow) ?: return false
        val key = src.removeAt(fromIndex)
        val insertAt = toIndex.coerceIn(0, dst.size)
        dst.add(insertAt, key)
        return true
    }

    /**
     * Inserts an empty row before [index] (index == rowCount → append).
     * Returns false when the index is out of range.
     */
    fun addRow(index: Int): Boolean {
        if (index !in 0..rows.size) return false
        rows.add(index, mutableListOf())
        return true
    }

    /** Removes a whole row. Returns false when out of range. */
    fun deleteRow(row: Int): Boolean {
        if (row !in rows.indices) return false
        rows.removeAt(row)
        return true
    }

    // ------------------------------------------------------------------
    // Serialization
    // ------------------------------------------------------------------

    /** Rebuilds the arrangement JSON with untouched keys preserved as-is. */
    fun toJson(): JsonElement = JsonArray(rows.map { row -> JsonArray(row.toList()) })
}
