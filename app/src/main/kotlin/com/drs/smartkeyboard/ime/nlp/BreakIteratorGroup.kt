/*
 * Copyright (C) 2022-2025 The DRS Smart Keyboard Project
 */

package com.drs.smartkeyboard.ime.nlp

import android.icu.text.BreakIterator
import com.drs.smartkeyboard.lib.DrsLocale
import io.github.reactivecircus.cache4k.Cache
import org.drs.lib.kotlin.GuardedByLock
import org.drs.lib.kotlin.guardedByLock

open class BreakIteratorGroup {
    private val charInstances = Cache.Builder<DrsLocale, GuardedByLock<BreakIterator>>().build()

    private val wordInstances = Cache.Builder<DrsLocale, GuardedByLock<BreakIterator>>().build()

    private val sentenceInstances = Cache.Builder<DrsLocale, GuardedByLock<BreakIterator>>().build()

    suspend fun <R> character(locale: DrsLocale, action: (BreakIterator) -> R): R {
        val instance = charInstances.get(locale) {
            guardedByLock { BreakIterator.getCharacterInstance(locale.base) }
        }
        return instance.withLock(null, action)
    }

    suspend fun <R> word(locale: DrsLocale, action: (BreakIterator) -> R): R {
        val instance = wordInstances.get(locale) {
            guardedByLock { BreakIterator.getWordInstance(locale.base) }
        }
        return instance.withLock(null, action)
    }

    suspend fun <R> sentence(locale: DrsLocale, action: (BreakIterator) -> R): R {
        val instance = sentenceInstances.get(locale) {
            guardedByLock { BreakIterator.getSentenceInstance(locale.base) }
        }
        return instance.withLock(null, action)
    }

    suspend fun measureUChars(
        text: String,
        numUnicodeChars: Int,
        locale: DrsLocale = DrsLocale.default(),
    ): Int {
        return character(locale) {
            it.setText(text)
            val start = it.first()
            var end: Int
            var n = 0
            do {
                end = it.next()
            } while (end != BreakIterator.DONE && ++n < numUnicodeChars)
            (if (end == BreakIterator.DONE) text.length else end) - start
        }.coerceIn(0, text.length)
    }

    suspend fun measureLastUChars(
        text: String,
        numUnicodeChars: Int,
        locale: DrsLocale = DrsLocale.default(),
    ): Int {
        return character(locale) {
            it.setText(text)
            val end = it.last()
            var start: Int
            var n = 0
            do {
                start = it.previous()
            } while (start != BreakIterator.DONE && ++n < numUnicodeChars)
            end - (if (start == BreakIterator.DONE) 0 else start)
        }.coerceIn(0, text.length)
    }

    suspend fun measureUWords(
        text: String,
        numUnicodeWords: Int,
        locale: DrsLocale = DrsLocale.default(),
    ): Int {
        return word(locale) {
            it.setText(text)
            val start = it.first()
            var end: Int
            var n = 0
            do {
                end = it.next()
                if (it.ruleStatus != BreakIterator.WORD_NONE) n++
            } while (end != BreakIterator.DONE && n < numUnicodeWords)
            (if (end == BreakIterator.DONE) text.length else end) - start
        }.coerceIn(0, text.length)
    }

    suspend fun measureLastUWords(
        text: String,
        numUnicodeWords: Int,
        locale: DrsLocale = DrsLocale.default(),
    ): Int {
        return word(locale) {
            it.setText(text)
            val end = it.last()
            var start: Int
            var n = 0
            do {
                if (it.ruleStatus != BreakIterator.WORD_NONE) n++
                start = it.previous()
            } while (start != BreakIterator.DONE && n < numUnicodeWords)
            end - (if (start == BreakIterator.DONE) 0 else start)
        }.coerceIn(0, text.length)
    }
}
