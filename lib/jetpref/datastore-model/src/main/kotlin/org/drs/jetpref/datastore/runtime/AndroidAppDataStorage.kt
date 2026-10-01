/*
 * Copyright (C) 2021-2026 The DRS Smart Keyboard Project
 */

package org.drs.jetpref.datastore.runtime

import android.content.Context
import org.drs.jetpref.datastore.model.PreferenceModel
import org.drs.jetpref.datastore.model.Validator
import java.io.File

object AndroidAppDataStorage {
    const val JETPREF_DIR_NAME = "jetpref_datastore"
    const val JETPREF_FILE_EXT = "jetpref"
}

private fun androidAppDataStorageOf(
    context: Context,
    datastoreName: String,
) : FileBasedStorage {
    Validator.validateFileName(datastoreName)
    val path = context.jetprefDatastoreDir.jetprefDatastoreFile(datastoreName).absolutePath
    return FileBasedStorage(path)
}

/**
 * Initialize the datastore with given [context] and [datastoreName]. The name should be constant, as it
 * is used for loading and persisting the datastore file from the same location again.
 *
 * Typically, this method is called once in the `Application.onCreate()` method. If this method is never
 * called, the model behaves as if it was an in-memory-only model.
 *
 * This method can be called multiple times, this can be useful in context switch scenarios (e.g. direct
 * boot to user unlocked switch). In this case, ALL existing runtime data gets overridden, regardless of
 * the success or failure of this request.
 *
 * @param context The Android context, typically the application context. Caution: the datastore file location
 *  is derived from given context. For work profiles or direct-boot contexts the locations might differ.
 * @param datastoreName The name of the datastore name. Should be constant.
 * @param shouldPersist If live changes to the model's values should be persisted back to the datastore file
 *  on-disk. Defaults to true.
 * @return A result object. When this method returns, the model is guaranteed to have been initialized,
 *  either with the values, or with default values for failure.
 *
 * @since 0.3.0
 */
suspend fun <T : PreferenceModel> DataStore<T>.initAndroid(
    context: Context,
    datastoreName: String,
    shouldPersist: Boolean = true,
): Result<Unit> {
    val storageProvider = androidAppDataStorageOf(context, datastoreName)
    return this.init(
        loadStrategy = LoadStrategy.UseReader(storageProvider),
        persistStrategy = if (shouldPersist) {
            PersistStrategy.UseWriter(storageProvider)
        } else {
            PersistStrategy.Disabled
        },
    )
}

/**
 * Returns the absolute path to the directory on the filesystem where preference datastore
 * files are created and used by JetPref are stored. Note that this path may change over time,
 * so only relative paths should be stored.
 *
 * @return The path of the directory holding JetPref datastore files.
 *
 * @since 0.1.0
 */
val Context.jetprefDatastoreDir: File
    get() = File(this.filesDir.parent, AndroidAppDataStorage.JETPREF_DIR_NAME)

private fun File.jetprefDatastoreFile(name: String): File {
    return File(this, "$name.${AndroidAppDataStorage.JETPREF_FILE_EXT}")
}
