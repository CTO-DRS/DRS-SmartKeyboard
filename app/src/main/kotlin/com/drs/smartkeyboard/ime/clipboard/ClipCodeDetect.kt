/*
 * Copyright (C) 2025-2026 The DRS Smart Keyboard Project
 */

package com.drs.smartkeyboard.ime.clipboard

/**
 * DRS v1.12.0 — the programming-line detector of the smart clipboard.
 * A copied snippet is analyzed line by line with language-agnostic syntax
 * signals (braces, semicolons, comment markers, assignment operators,
 * keyword vocabulary, tag/JSON shapes); the aggregate decides whether the
 * text is code, which language it most resembles, and which exact lines
 * are code — powering the editor badge, the auto monospace switch, and
 * the extract/remove code chips.
 *
 * Everything is pure and JVM-testable. The detector is deliberately
 * heuristic (no tokenizer, no parser) so it stays fast on 500,000
 * characters while remaining accurate on real snippets.
 */

/** The languages the detector can recognize. */
enum class ClipCodeLanguage(val id: String) {
    KOTLIN_JAVA("kotlin_java"),
    PYTHON("python"),
    JAVASCRIPT("javascript"),
    JSON("json"),
    HTML_XML("html_xml"),
    CSS("css"),
    SQL("sql"),
    C_CPP("c_cpp"),
    BASH("bash"),
    UNKNOWN("unknown"),
}

/**
 * The analysis verdict: [isCode] with a [confidence] in `0.0..1.0`, the
 * best-guess [language], how many of the [totalLines] non-blank lines
 * scored as code, and the 0-based indexes of those lines.
 */
data class ClipCodeAnalysis(
    val isCode: Boolean,
    val language: ClipCodeLanguage,
    val confidence: Double,
    val codeLines: Int,
    val totalLines: Int,
    val codeLineIndexes: List<Int>,
)

object ClipCodeDetector {

    /** Fewer code lines than this never qualifies as code. */
    const val MIN_CODE_LINES: Int = 3

    /** At least this share of non-blank lines must score as code. */
    const val MIN_RATIO: Double = 0.4

    /** Text at or above this character count is treated as large by the editor. */
    const val LARGE_TEXT_CHARS: Int = 100_000

    // Language-specific vocabulary (lowercased, matched per line).
    private val KOTLIN_JAVA_WORDS = listOf(
        "fun ", "val ", "var ", "public ", "private ", "protected ", "class ",
        "interface ", "object ", "override ", "suspend ", "package ", "import ",
        "extends ", "implements ", "void ", "static ", "final ", "system.out",
        "@override", "println(", "system.err", "toast.maketext",
    )
    private val PYTHON_WORDS = listOf(
        "def ", "elif ", "self.", "import ", "from ", "print(", "lambda ",
        "__init__", "raise ", "with open", "try:", "except ",
    )
    private val JS_WORDS = listOf(
        "const ", "let ", "function ", "=>", "console.", "document.",
        "async ", "await ", "export ", "require(", "npm ",
    )
    private val SQL_WORDS = listOf(
        "select ", "insert into", "update ", "delete from", "create table",
        "alter table", "where ", "join ", "group by", "order by",
    )
    private val C_CPP_WORDS = listOf(
        "#include", "printf(", "scanf(", "std::", "int main", "cout ", "cin ",
        "malloc(", "struct ", "typedef ",
    )
    private val BASH_WORDS = listOf(
        "#!/", "echo ", "sudo ", "apt-get", "esac", "\${", "\$(",
    )
    private val CSS_WORDS = listOf(
        "color:", "margin:", "padding:", "font-size:", "background:",
        "display:", "border:", "width:", "height:",
    )

    private val TAG_PATTERN = Regex("</?[A-Za-z][A-Za-z0-9]*(\\s[^<>]*)?/?>")
    private val JSON_PAIR_PATTERN = Regex("\"[^\"]*\"\\s*:\\s*(\"[^\"]*\"|[-\\d.]+|true|false|null|\\{|\\[)")
    private val CSS_RULE_PATTERN = Regex("[A-Za-z-]+\\s*:\\s*[^;{}]+;")
    private val IDENTIFIER_OP_PATTERN = Regex("[A-Za-z_][A-Za-z0-9_]*\\s*(==|!=|<=|>=|=|:=|<-|->|=>)[^=]")
    private val CALL_PATTERN = Regex("[A-Za-z_][A-Za-z0-9_.]*\\([^)]*\\)")
    private val CAMEL_OR_SNAKE = Regex("([a-z]+[A-Z][A-Za-z0-9]*|[a-z]+_[a-z0-9_]+)")

    /**
     * The per-line verdict. A line scores when it carries unmistakable
     * syntax signals; a strong single signal (a tag, an `#include`, a
     * shebang, a JSON pair, a call followed by a semicolon) is enough,
     * weaker signals need to combine.
     */
    fun isCodeLine(line: String): Boolean {
        val trimmed = line.trim()
        if (trimmed.length < 3) return false

        // DRS v1.21.0: a leading '*' is ambiguous — it opens a JSDoc/doc
        // continuation AND a Markdown bullet («* item one») — so it no
        // longer decides alone: it is stripped and the remainder scored.
        // The unambiguous markers below still decide immediately.
        val scored = if (trimmed.startsWith("*")) trimmed.trimStart('*').trim() else trimmed

        // Indentation of an interior line is a weak, supporting signal —
        // handled by callers via the aggregate; alone it never decides.
        var score = 0

        // Structural punctuation.
        if (scored.endsWith(";")) score += 2
        if (scored.startsWith("{") || scored.startsWith("}") || scored == "{" || scored == "}" || scored.endsWith("}")) score += 1
        if ((scored.startsWith("[") || scored.endsWith("]")) && scored.contains(',')) score += 1

        // Comment markers.
        if (scored.startsWith("//") || scored.startsWith("/*") || scored.endsWith("*/") || scored.startsWith("#!")) {
            return true
        }

        // Language keyword vocabulary (case-insensitive where it matters).
        val lowered = scored.lowercase(java.util.Locale.ROOT)
        when {
            KOTLIN_JAVA_WORDS.any { lowered.contains(it) } -> score += 2
            PYTHON_WORDS.any { lowered.contains(it) } -> score += 2
            JS_WORDS.any { lowered.contains(it) } -> score += 2
            SQL_WORDS.any { lowered.startsWith(it) || lowered.contains(" $it") } -> score += 2
            C_CPP_WORDS.any { lowered.contains(it) } -> return true
            BASH_WORDS.any { lowered.contains(it) } -> score += 2
            CSS_WORDS.any { lowered.contains(it) } -> score += 2
        }

        // Shape-based signals.
        if (TAG_PATTERN.containsMatchIn(scored)) score += 2
        if (JSON_PAIR_PATTERN.containsMatchIn(scored)) score += 2
        if (CSS_RULE_PATTERN.containsMatchIn(scored)) score += 2
        if (IDENTIFIER_OP_PATTERN.containsMatchIn(scored)) score += 1
        if (CALL_PATTERN.containsMatchIn(scored) &&
            (scored.contains(';') || scored.contains('{') || scored.endsWith("):") || scored.endsWith(")"))
        ) {
            score += 1
        }
        if (CAMEL_OR_SNAKE.containsMatchIn(scored) && (scored.contains('(') || scored.contains('='))) score += 1

        return score >= 2
    }

    /**
     * Analyzes [text]: counts code lines over the non-blank lines, decides
     * [ClipCodeAnalysis.isCode] by [MIN_CODE_LINES] and [MIN_RATIO], picks
     * the strongest language signature, and computes a bounded confidence.
     */
    fun analyze(
        text: String,
        minLines: Int = MIN_CODE_LINES,
        minRatio: Double = MIN_RATIO,
    ): ClipCodeAnalysis {
        if (text.isBlank()) {
            return ClipCodeAnalysis(false, ClipCodeLanguage.UNKNOWN, 0.0, 0, 0, emptyList())
        }
        var codeCount = 0
        val indexes = ArrayList<Int>()
        var nonBlank = 0
        for ((position, line) in text.lines().withIndex()) {
            if (line.isBlank()) continue
            nonBlank += 1
            if (isCodeLine(line)) {
                codeCount += 1
                indexes.add(position)
            }
        }
        val ratio = if (nonBlank == 0) 0.0 else codeCount.toDouble() / nonBlank.toDouble()
        val isCode = codeCount >= minLines && ratio >= minRatio
        val language = if (isCode) detectLanguage(text) else ClipCodeLanguage.UNKNOWN
        val confidence = (ratio * 0.6 + (codeCount.toDouble() / 10.0).coerceAtMost(1.0) * 0.4)
            .coerceIn(0.0, 1.0)
        return ClipCodeAnalysis(isCode, language, confidence, codeCount, nonBlank, indexes)
    }

    /**
     * The best-guess language by signature hits over the whole text
     * (JSON additionally requires the overall shape to start like a
     * document). Ties and zero-hit cases resolve to [ClipCodeLanguage.UNKNOWN].
     */
    fun detectLanguage(text: String): ClipCodeLanguage {
        val lowered = text.lowercase(java.util.Locale.ROOT)
        val scores = mutableMapOf(
            ClipCodeLanguage.KOTLIN_JAVA to KOTLIN_JAVA_WORDS.count { lowered.contains(it) },
            ClipCodeLanguage.PYTHON to PYTHON_WORDS.count { lowered.contains(it) },
            ClipCodeLanguage.JAVASCRIPT to JS_WORDS.count { lowered.contains(it) },
            ClipCodeLanguage.JSON to JSON_PAIR_PATTERN.findAll(text).count(),
            ClipCodeLanguage.HTML_XML to TAG_PATTERN.findAll(text).count(),
            ClipCodeLanguage.CSS to CSS_WORDS.count { lowered.contains(it) },
            ClipCodeLanguage.SQL to SQL_WORDS.count { lowered.contains(it) },
            ClipCodeLanguage.C_CPP to C_CPP_WORDS.count { lowered.contains(it) },
            ClipCodeLanguage.BASH to BASH_WORDS.count { lowered.contains(it) },
        )
        val trimmedStart = text.trimStart()
        if (trimmedStart.startsWith("{") || trimmedStart.startsWith("[")) {
            scores[ClipCodeLanguage.JSON] = (scores[ClipCodeLanguage.JSON] ?: 0) + 3
        }
        val best = scores.maxByOrNull { it.value } ?: return ClipCodeLanguage.UNKNOWN
        return if (best.value >= 2) best.key else ClipCodeLanguage.UNKNOWN
    }

    /**
     * Keeps only the code lines (blank lines are dropped so the extract
     * reads as one clean snippet); the original order is preserved.
     */
    fun extractCodeLines(text: String): String {
        if (text.isEmpty()) return text
        return text.lines().filter { isCodeLine(it) }.joinToString("\n")
    }

    /** Removes the code lines and keeps everything else, order preserved. */
    fun removeCodeLines(text: String): String {
        if (text.isEmpty()) return text
        return text.lines().filterNot { isCodeLine(it) }.joinToString("\n")
    }
}
