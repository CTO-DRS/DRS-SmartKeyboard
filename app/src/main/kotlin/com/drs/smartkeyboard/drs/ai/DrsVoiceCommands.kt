/*
 * Copyright (C) 2026 The DRS Smart Keyboard Project
 */

package com.drs.smartkeyboard.drs.ai

/**
 * DRS Phase 2 (roadmap task 12): voice EDIT commands — spoken editing
 * and punctuation for the continuous dictation flow.
 *
 * The recognizer returns free text; this parser decides whether that
 * text is a COMMAND the keyboard should execute (delete a word, start a
 * new line, type a period, clear the field…) or plain DICTATION the
 * keyboard should commit. Recognition of a command phrase must be
 * EXACT after trimming (never substring) so ordinary sentences that
 * merely contain command words are never eaten.
 *
 * Supported (Arabic + English):
 *  NEW_LINE («سطر جديد» / «new line»), PERIOD («نقطة» / «period»),
 *  COMMA («فاصلة» / «comma»), QUESTION («علامة استفهام» / «question
 *  mark»), EXCLAMATION («علامة تعجب» / «exclamation mark»),
 *  DELETE_LAST_WORD («احذف الكلمة الأخيرة» / «delete last word»),
 *  DELETE_LAST_SENTENCE («احذف الجملة الأخيرة» / «delete last
 *  sentence»), UNDO_LAST («تراجع» / «undo»), CLEAR_ALL («امسح الكل» /
 *  «clear all»), STOP («إيقاف الإملاء» / «stop dictation»).
 */
object DrsVoiceCommands {

    enum class Command {
        NEW_LINE,
        PERIOD,
        COMMA,
        QUESTION,
        EXCLAMATION,
        DELETE_LAST_WORD,
        DELETE_LAST_SENTENCE,
        UNDO_LAST,
        CLEAR_ALL,
        STOP,
    }

    /** What the parser decided the user meant. */
    sealed interface Parsed {
        /** The spoken phrase was a command — [command] must be executed and NOT committed as text. */
        data class IsCommand(val command: Command) : Parsed

        /** Plain dictation — commit [text] (already trimmed, never empty). */
        data class Dictation(val text: String) : Parsed
    }

    private val TABLE: Map<String, Command> = buildMap {
        putAll(
            listOf("سطر جديد", "سطر جديد.", "new line", "newline").associateWith {
                Command.NEW_LINE
            },
        )
        putAll(
            listOf("نقطة", "نقطة.", "علامة نقطة", "period", "full stop", "dot").associateWith {
                Command.PERIOD
            },
        )
        putAll(listOf("فاصلة", "فاصلة,", "comma").associateWith { Command.COMMA })
        putAll(
            listOf(
                "علامة استفهام",
                "علامة سؤال",
                "استفهام",
                "question mark",
                "question",
            ).associateWith { Command.QUESTION },
        )
        putAll(
            listOf("علامة تعجب", "تعجب", "exclamation mark", "exclamation").associateWith {
                Command.EXCLAMATION
            },
        )
        putAll(
            listOf(
                "احذف الكلمة الأخيرة",
                "احذف كلمة",
                "امسح الكلمة الأخيرة",
                "delete last word",
                "delete the last word",
            ).associateWith { Command.DELETE_LAST_WORD },
        )
        putAll(
            listOf(
                "احذف الجملة الأخيرة",
                "احذف جملة",
                "امسح الجملة الأخيرة",
                "delete last sentence",
                "delete the last sentence",
            ).associateWith { Command.DELETE_LAST_SENTENCE },
        )
        putAll(
            listOf("تراجع", "ارجع", "تراجع خطوة", "undo", "undo that").associateWith {
                Command.UNDO_LAST
            },
        )
        putAll(
            listOf("امسح الكل", "امسح كل شيء", "clear all", "clear everything").associateWith {
                Command.CLEAR_ALL
            },
        )
        putAll(
            listOf(
                "إيقاف الإملاء",
                "ايقاف الاملاء",
                "إيقاف",
                "كفى",
                "stop dictation",
                "stop",
            ).associateWith { Command.STOP },
        )
    }

    /**
     * Parses the recognizer's [raw] result. Command phrases match EXACTLY
     * (trimmed, diacritics-insensitive); anything else is dictation.
     * Never throws.
     */
    fun parse(raw: String): Parsed {
        val text = raw.trim()
        if (text.isEmpty()) return Parsed.Dictation("")
        val probe = stripTashkeel(text).lowercase()
        // Exact (trimmed) match against the table — substring matches are
        // deliberately NOT accepted.
        TABLE[probe]?.let { return Parsed.IsCommand(it) }
        TABLE[text.lowercase()]?.let { return Parsed.IsCommand(it) }
        return Parsed.Dictation(text)
    }

    /**
     * The committed text form of a punctuation command (what the editor
     * inserts when the command is executed with punctuation semantics).
     */
    fun punctuationFor(command: Command): String? = when (command) {
        Command.PERIOD -> "."
        Command.COMMA -> "،"
        Command.QUESTION -> "؟"
        Command.EXCLAMATION -> "!"
        else -> null
    }

    /** True when [command] must terminate the dictation session. */
    fun isTerminating(command: Command): Boolean =
        command == Command.STOP || command == Command.CLEAR_ALL

    private fun stripTashkeel(s: String): String = buildString(s.length) {
        for (c in s) {
            if (c in '\u064B'..'\u065F' || c == '\u0670' || c == '\u0640') continue
            append(c)
        }
    }
}
