/*
 * Copyright (C) 2021-2026 The DRS Smart Keyboard Project
 */

package org.drs.jetpref.datastore

import org.drs.jetpref.datastore.model.PreferenceModel
import org.drs.jetpref.datastore.runtime.DataStore
import org.drs.jetpref.datastore.runtime.PreferenceModelNotFoundException
import kotlin.reflect.KClass

/**
 * Creates a new datastore instance for given [modelClass] and returns it.
 *
 * @param modelClass The class of the preference model to create.
 * @throws PreferenceModelNotFoundException If the model referenced by [modelClass]
 *  does not exist.
 *
 * @since 0.3.0
 */
