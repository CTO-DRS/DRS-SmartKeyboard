/*
 * Copyright (C) 2025 The DRS Smart Keyboard Project
 */

package org.drs.lib.kotlin

/**
 * Utility class for matching MIME types against predefined filters.
 *
 * Supports wildcards at any position. E.g. &ast;/&ast;, font/&ast;, and application/font-&ast; are valid filter types.
 * Neither type nor subtype can be empty.
 *
 * MIME types can be null or ill-formatted. In such case they won't match anything.
 *
 * MIME type filters must be correctly formatted, or an exception will be thrown.
 *
 * Inspired by the Android [MimeTypeFilter](https://cs.android.com/android/platform/superproject/main/+/main:frameworks/base/core/java/android/content/MimeTypeFilter.java)
 */
class MimeTypeFilter {
    /**
     * The original list of filter types.
     */
    val types: List<String>

    private val filters: List<Pair<Regex, Regex>>

    internal constructor(filterTypes: List<String>) {
        types = filterTypes
        filters = filterTypes.map { filterType ->
            val filterTypeParts = filterType.split("/")
            require(filterTypeParts.size == 2) {
                "Ill-formatted MIME type filter '$filterType'. Must be type/subtype."
            }
            require(filterTypeParts[0].isNotEmpty() && filterTypeParts[1].isNotEmpty()) {
                "Ill-formatted MIME type filter '$filterType'. Type or subtype empty."
            }
            val filter0 = filterTypeParts[0].replace("*", "[^\\s]+").toRegex()
            val filter1 = filterTypeParts[1].replace("*", "[^\\s]+").toRegex()
            filter0 to filter1
        }
        println(filters)
    }

    private fun matchMimeTypeAgainstFilters(mimeType: String): Boolean {
        val mimeTypeParts = mimeType.split("/")
        if (mimeTypeParts.size != 2 || mimeTypeParts[0].isEmpty() || mimeTypeParts[1].isEmpty()) {
            return false
        }
        for ((filter0, filter1) in filters) {
            if (mimeTypeParts[0].matches(filter0) && mimeTypeParts[1].matches(filter1)) {
                return true
            }
        }
        return false
    }

    /**
     * Matches a given [mimeType] against the filter types of this [MimeTypeFilter]. If the MIME type is null or
     * ill-formatted, it will not match.
     */
    fun matches(mimeType: String?): Boolean {
        if (mimeType.isNullOrEmpty()) {
            return false
        }
        return matchMimeTypeAgainstFilters(mimeType)
    }



}

/**
 * Creates a new [MimeTypeFilter] with given [filterTypes].
 */
fun mimeTypeFilterOf(vararg filterTypes: String): MimeTypeFilter {
    return MimeTypeFilter(filterTypes.toList())
}
