/*
 * Copyright (C) 2025-2026 The DRS Smart Keyboard Project
 */

package com.drs.smartkeyboard.lib.ext

/**
 * DRS v1.27.0: human-readable SPDX license names — «الترخيص بلغة
 * الناس». The extension manifest stores the license as an SPDX
 * identifier or expression ([ExtensionMeta.license] — the machine
 * truth, exactly what the SPDX spec demands), but the extension view
 * screen should not greet a normal user with "Apache-2.0" when the
 * world knows that license as the Apache License 2.0.
 *
 * The map covers the identifiers that actually appear in the wild for
 * keyboard extensions and their bundled libraries. The official
 * license titles are proper nouns and stay in English — translating a
 * legal name would be neither honest nor useful. Any identifier the
 * map does not know falls through untouched: a raw SPDX id is the
 * honest answer when no official title is known, never a made-up one.
 */

/**
 * Renders [spdx] for the extension view screen. Pure — JVM-tested.
 *
 * SPDX expressions are honored on their own operators: each operand
 * between `OR` / `AND` / `WITH` is mapped independently, operators and
 * parentheses are preserved verbatim, and matching is case-insensitive
 * (the spec's identifiers are exact, but manifests are written by
 * humans and "mit" happens). A blank input comes back as-is.
 */
fun extensionLicenseDisplayName(spdx: String): String {
    if (spdx.isBlank()) return spdx
    val tokenized = spdx.trim().split(OPERATOR_REGEX)
    if (tokenized.size == 1) return mapOperand(spdx.trim())
    val rebuilt = StringBuilder()
    spdx.trim().splitToSequence(OPERATOR_REGEX)
        .forEachIndexed { index, operand ->
            rebuilt.append(mapOperand(operand))
            if (index < tokenized.size - 1) {
                rebuilt.append(operatorAt(spdx, index))
            }
        }
    return rebuilt.toString()
}

private fun mapOperand(operand: String): String {
    val trimmed = operand.trim()
    if (trimmed.isEmpty()) return operand
    // Parentheses may hug one side of an operand inside a grouped
    // expression ("(MIT" and "Apache-2.0)") — strip them independently
    // so the identifier underneath still gets mapped.
    val open = trimmed.takeWhile { it == '(' }
    val close = trimmed.takeLastWhile { it == ')' }
    val bare = trimmed.substring(
        open.length,
        (trimmed.length - close.length).coerceAtLeast(open.length),
    ).trim()
    if (bare.isEmpty()) return operand
    val known = LICENSE_NAMES[bare.lowercase()] ?: return operand
    return "$open$known$close"
}

private fun operatorAt(spdx: String, afterTokenIndex: Int): String {
    val matches = OPERATOR_REGEX.findAll(spdx).toList()
    return matches.getOrNull(afterTokenIndex)?.value ?: ""
}

private val OPERATOR_REGEX = Regex("""\s+(OR|AND|WITH)\s+""")

/** Common SPDX ids (lowercased) → their official human-readable titles. */
private val LICENSE_NAMES: Map<String, String> = mapOf(
    "proprietary" to "ملكية خاصة — DRS Smart Keyboard",
    "mit" to "MIT License",
    "mit-0" to "MIT No Attribution",
    "apache-1.0" to "Apache License 1.0",
    "apache-1.1" to "Apache License 1.1",
    "apache-2.0" to "Apache License 2.0",
    "bsd-1-clause" to "BSD 1-Clause License",
    "bsd-2-clause" to "BSD 2-Clause \"Simplified\" License",
    "bsd-3-clause" to "BSD 3-Clause \"New\" or \"Revised\" License",
    "bsd-4-clause" to "BSD 4-Clause \"Original\" or \"Old\" License",
    "bsd-zero-clause" to "BSD Zero Clause License",
    "0bsd" to "BSD Zero Clause License",
    "isc" to "ISC License",
    "zlib" to "zlib License",
    "bsl-1.0" to "Boost Software License 1.0",
    "unlicense" to "The Unlicense",
    "wtfpl" to "Do What The F*ck You Want To Public License",
    "cc0-1.0" to "Creative Commons Zero v1.0 Universal",
    "cc-by-1.0" to "Creative Commons Attribution 1.0 Generic",
    "cc-by-2.0" to "Creative Commons Attribution 2.0 Generic",
    "cc-by-3.0" to "Creative Commons Attribution 3.0 Unported",
    "cc-by-4.0" to "Creative Commons Attribution 4.0 International",
    "cc-by-sa-3.0" to "Creative Commons Attribution Share Alike 3.0 Unported",
    "cc-by-sa-4.0" to "Creative Commons Attribution Share Alike 4.0 International",
    "gpl-1.0-only" to "GNU General Public License v1.0 only",
    "gpl-1.0-or-later" to "GNU General Public License v1.0 or later",
    "gpl-2.0-only" to "GNU General Public License v2.0 only",
    "gpl-2.0-or-later" to "GNU General Public License v2.0 or later",
    "gpl-3.0-only" to "GNU General Public License v3.0 only",
    "gpl-3.0-or-later" to "GNU General Public License v3.0 or later",
    "lgpl-2.0-only" to "GNU Library General Public License v2 only",
    "lgpl-2.0-or-later" to "GNU Library General Public License v2 or later",
    "lgpl-2.1-only" to "GNU Lesser General Public License v2.1 only",
    "lgpl-2.1-or-later" to "GNU Lesser General Public License v2.1 or later",
    "lgpl-3.0-only" to "GNU Lesser General Public License v3.0 only",
    "lgpl-3.0-or-later" to "GNU Lesser General Public License v3.0 or later",
    "agpl-1.0-only" to "GNU Affero General Public License v1.0 only",
    "agpl-3.0-only" to "GNU Affero General Public License v3.0 only",
    "agpl-3.0-or-later" to "GNU Affero General Public License v3.0 or later",
    "mpl-1.0" to "Mozilla Public License 1.0",
    "mpl-1.1" to "Mozilla Public License 1.1",
    "mpl-2.0" to "Mozilla Public License 2.0",
    "epl-1.0" to "Eclipse Public License 1.0",
    "epl-2.0" to "Eclipse Public License 2.0",
    "cddl-1.0" to "Common Development and Distribution License 1.0",
    "cddl-1.1" to "Common Development and Distribution License 1.1",
    "artistic-2.0" to "Artistic License 2.0",
    "ofl-1.0" to "SIL Open Font License 1.0",
    "ofl-1.1" to "SIL Open Font License 1.1",
    "json" to "JSON License",
    "postgresql" to "PostgreSQL License",
    "openssl" to "OpenSSL License",
    "x11" to "X11 License",
    "unicode-tou" to "Unicode Terms of Use",
    "unicode-3.0" to "Unicode License v3",
    "libpng" to "PNG Reference Library version 2",
    "curl" to "curl License",
)
