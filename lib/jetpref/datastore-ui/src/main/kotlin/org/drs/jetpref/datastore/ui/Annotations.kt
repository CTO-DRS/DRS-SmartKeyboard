/*
 * Copyright (C) 2021-2026 The DRS Smart Keyboard Project
 */

package org.drs.jetpref.datastore.ui

/**
 * Marks composables, class or property declarations that are still **experimental** within the
 * `org.drs.jetpref.datastore.ui` package. This means that the design, behavior or API
 * has open issues, which may or may not lead to changes or deprecation of declarations marked
 * with this annotation.
 */
@Target(AnnotationTarget.CLASS, AnnotationTarget.PROPERTY, AnnotationTarget.FUNCTION, AnnotationTarget.TYPEALIAS)
@RequiresOptIn(level = RequiresOptIn.Level.WARNING)
@Retention(value = AnnotationRetention.BINARY)
annotation class ExperimentalJetPrefDatastoreUi
