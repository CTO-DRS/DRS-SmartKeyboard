/*
 * Copyright (C) 2021-2026 The DRS Smart Keyboard Project
 */

package org.drs.jetpref.datastore

import org.drs.jetpref.datastore.model.PreferenceModel
import org.drs.jetpref.datastore.runtime.DataStore
import org.drs.jetpref.datastore.runtime.PreferenceModelDuplicateKeyException
import org.drs.jetpref.datastore.runtime.PreferenceModelNotFoundException
import kotlin.reflect.KClass

@Suppress("unchecked_cast")
@Throws(PreferenceModelNotFoundException::class)
fun <T : PreferenceModel> jetprefDataStoreOf(modelClass: KClass<T>): DataStore<T> {
    val modelImplInstance = try {
        val modelImplName = modelClass.qualifiedName!! + "Impl"
        val modelImplClass = Class.forName(modelImplName)
        modelImplClass.getDeclaredConstructor().newInstance() as T
    } catch (e: PreferenceModelDuplicateKeyException) {
        throw e
    } catch (e: Throwable) {
        val cause = e.cause
        if (cause != null && cause is PreferenceModelDuplicateKeyException) {
            throw cause
        }
        throw PreferenceModelNotFoundException(modelClass.qualifiedName.toString(), e)
    }
    return DataStore(modelImplInstance)
}
