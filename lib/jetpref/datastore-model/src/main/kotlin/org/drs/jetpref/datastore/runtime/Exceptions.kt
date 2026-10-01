/*
 * Copyright (C) 2021-2026 The DRS Smart Keyboard Project
 */

package org.drs.jetpref.datastore.runtime

/**
 * Exception indicating that the requested preference model could not be found.
 *
 * Hint: most likely, this either means you forgot to annotate the model with
 * [org.drs.jetpref.datastore.annotations.Preferences], or you did not configure
 * KSP correctly in your `build.gradle.kts`.
 *
 * @since 0.3.0
 */
class PreferenceModelNotFoundException(
    modelQualifiedName: String,
    cause: Throwable,
) : Exception(
    "No preference model with qualified name '$modelQualifiedName' could be found",
    cause,
)

/**
 * Exception indicating that a preference model contains duplicate keys.
 *
 * Hint: have a look at your model's entries and remove duplicate keys. A common pitfall are
 * entries with the same key but different types, these are counted as duplicates!
 *
 * @since 0.3.0
 */
class PreferenceModelDuplicateKeyException(
    modelQualifiedName: String,
    duplicates: Map<String, List<String>>,
) : Exception(
    buildString {
        appendLine("Preference model '$modelQualifiedName' contains duplicate keys $duplicates")
    },
)
