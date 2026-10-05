/*
 * Copyright (C) 2025 The DRS Smart Keyboard Project
 */

package com.drs.smartkeyboard.ime.nlp

/**
 * DRS v2.8.0 «قاموسك من ملفك» — the pure merge decision for external
 * dictionaries.
 *
 * Honesty rule: an imported dictionary EXTENDS the bundled one, it never
 * replaces or downgrades it. A word present in both keeps the HIGHER
 * frequency; a word only in the external file joins with its own clamped
 * frequency. The merge happens BEFORE normalization — both sides carry raw
 * display words exactly like the bundled JSON assets do.
 */
object DrsExternalDictMerger {

    fun merge(base: Map<String, Int>, external: Map<String, Int>): Map<String, Int> {
        if (external.isEmpty()) return base
        val out = HashMap<String, Int>(base.size + external.size)
        out.putAll(base)
        for ((word, freq) in external) {
            val existing = out[word]
            out[word] = if (existing == null) freq else maxOf(existing, freq)
        }
        return out
    }
}
