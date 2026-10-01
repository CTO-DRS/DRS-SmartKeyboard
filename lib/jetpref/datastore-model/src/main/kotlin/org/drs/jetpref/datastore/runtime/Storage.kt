/*
 * Copyright (C) 2021-2026 The DRS Smart Keyboard Project
 */

package org.drs.jetpref.datastore.runtime

/**
 * Abstraction for concrete datastore readers. Readers can retrieve datastore files
 * from any type of storage, e.g. file system, network, databases, etc., depending on
 * the specific implementation.
 *
 * @since 0.3.0
 */
fun interface DataStoreReader {
    /**
     * Handle request to read current datastore from storage.
     *
     * On errors, this function may throw.
     *
     * @return The read datastore file content.
     *
     * @since 0.3.0
     */
    suspend fun read(): String
}

/**
 * Abstraction for concrete datastore writers. Writers can store datastore files
 * to any type of storage, e.g. file system, network, databases, etc., depending on
 * the specific implementation.
 *
 * @since 0.3.0
 */
fun interface DataStoreWriter {
    /**
     * Handle request to write current datastore to storage.
     *
     * On errors, this function may throw.
     *
     * @param content The datastore file content to write.
     *
     * @since 0.3.0
     */
    suspend fun write(content: String)
}

/**
 * The load strategy describes how the datastore should load its state.
 *
 * @since 0.3.0
 */
sealed interface LoadStrategy {
    /**
     * Do not load from storage.
     *
     * @since 0.3.0
     */
    data object Disabled : LoadStrategy

    /**
     * Load from storage with given [reader].
     *
     * @since 0.3.0
     */
    data class UseReader(
        val reader: DataStoreReader,
    ) : LoadStrategy
}

/**
 * The persist strategy describes how the datastore should persist its state.
 *
 * @since 0.3.0
 */
sealed interface PersistStrategy {
    /**
     * Do not persist back to storage.
     *
     * @since 0.3.0
     */
    data object Disabled : PersistStrategy

    /**
     * Persist to storage with given [writer].
     *
     * @since 0.3.0
     */
    data class UseWriter(
        val writer: DataStoreWriter,
    ) : PersistStrategy
}

/**
 * Describes how values should be handled upon importing a new datastore.
 *
 * @since 0.3.0
 */
enum class ImportStrategy {
    /**
     * Merge current in-memory values with incoming values.
     *
     * @since 0.3.0
     */
    Merge,
    /**
     * Erase current in-memory values and set incoming values.
     *
     * @since 0.3.0
     */
    Erase,
}
