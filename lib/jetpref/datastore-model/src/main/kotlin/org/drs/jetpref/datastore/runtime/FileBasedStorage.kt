/*
 * Copyright (C) 2021-2026 The DRS Smart Keyboard Project
 */

package org.drs.jetpref.datastore.runtime

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Implements a file-based datastore storage for JVM-backed applications.
 *
 * @param path The path to the datastore file.
 *
 * @since 0.3.0
 */
class FileBasedStorage(path: String) : DataStoreReader, DataStoreWriter {
    private val datastoreFile = File(path)
    private val tempFile = File("${path}.tmp")

    override suspend fun read(): String {
        return withContext(Dispatchers.IO) {
            datastoreFile.readText()
        }
    }

    override suspend fun write(content: String) {
        withContext(Dispatchers.IO) {
            val parentDir = requireNotNull(tempFile.parentFile) {
                "Temp file '$tempFile' has no associated parent dir (was null)"
            }
            if (!parentDir.exists()) {
                check(parentDir.mkdirs()) {
                    "Failed to perform mkdirs for parent dir '$parentDir'"
                }
            }
            tempFile.writeText(content)
            check(tempFile.renameTo(datastoreFile)) {
                "Failed to rename temp file '$tempFile' to actual file name '$datastoreFile'"
            }
        }
    }
}
