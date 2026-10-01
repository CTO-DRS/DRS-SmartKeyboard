/*
 * Copyright (C) 2021-2025 The DRS Smart Keyboard Project
 */

package com.drs.smartkeyboard.ime.dictionary

import android.content.Context
import androidx.room.Room
import com.drs.smartkeyboard.app.DrsPreferenceStore
import com.drs.smartkeyboard.ime.nlp.SuggestionCandidate
import com.drs.smartkeyboard.ime.nlp.WordSuggestionCandidate
import com.drs.smartkeyboard.lib.DrsLocale
import java.io.File
import java.lang.ref.WeakReference

/**
 * Owns every dictionary-facing subsystem the NLP layer reads through:
 * the DRS user-dictionary Room database, the system user-dictionary
 * content resolver, the background training corpora, and the composite
 * dictionaries keyed by locale. Constructed via [getInstance] on the
 * application context (the databases follow the app's own storage, so
 * no activity context may leak in); a single instance lives for the
 * whole process, so every add/edit/reset call here is the authority the
 * settings screens and the suggestion pipeline both observe.
 */
class DictionaryManager private constructor(context: Context) {
    private val applicationContext: WeakReference<Context> = WeakReference(context.applicationContext ?: context)
    private val prefs by DrsPreferenceStore

    private var drsUserDictionaryDatabase: DrsUserDictionaryDatabase? = null
    private var systemUserDictionaryDatabase: SystemUserDictionaryDatabase? = null

    companion object {
        private var defaultInstance: DictionaryManager? = null

        /**
         * DRS v1.20.0: pure demotion arithmetic — the new frequency, or null when the
         * entry must be deleted (drained to zero or below). JVM-tested.
         */
        internal fun demoteTarget(freq: Int, by: Int): Int? = (freq - by).takeIf { it > 0 }

        fun init(applicationContext: Context): DictionaryManager {
            val instance = DictionaryManager(applicationContext)
            defaultInstance = instance
            return instance
        }

        fun default(): DictionaryManager {
            val instance = defaultInstance
            if (instance != null) {
                return instance
            } else {
                throw UninitializedPropertyAccessException(
                    "${DictionaryManager::class.simpleName} has not been initialized previously. Make sure to call init(applicationContext) before using default()."
                )
            }
        }
    }


    fun spell(word: String, locale: DrsLocale): Boolean {
        val drsDao = drsUserDictionaryDao()
        val systemDao = systemUserDictionaryDao()
        if (drsDao == null && systemDao == null) {
            return false
        }
        var ret = false
        if (prefs.dictionary.enableDrsUserDictionary.get()) {
            ret = ret || drsDao?.queryExactFuzzyLocale(word, locale)?.isNotEmpty() ?: false
            ret = ret || drsDao?.queryShortcut(word, locale)?.isNotEmpty() ?: false
        }
        if (prefs.dictionary.enableSystemUserDictionary.get()) {
            ret = ret || systemDao?.queryExactFuzzyLocale(word, locale)?.isNotEmpty() ?: false
            ret = ret || systemDao?.queryShortcut(word, locale)?.isNotEmpty() ?: false
        }
        return ret
    }

    /**
     * DRS: returns all user dictionary entries for [locale] (including entries stored with
     * a null locale), merging the app-private and system dictionaries according to the
     * user's preferences. Used by the suggestion engine to blend personally learned words
     * into the candidate row. Entries are deduplicated by word (highest freq wins).
     */
    fun queryAllUserWords(locale: DrsLocale): List<UserDictionaryEntry> {
        loadUserDictionariesIfNecessary()
        val byWord = LinkedHashMap<String, UserDictionaryEntry>()
        if (prefs.dictionary.enableDrsUserDictionary.get()) {
            drsUserDictionaryDao()?.queryAll(locale)?.forEach { entry ->
                val existing = byWord[entry.word]
                if (existing == null || entry.freq > existing.freq) byWord[entry.word] = entry
            }
        }
        if (prefs.dictionary.enableSystemUserDictionary.get()) {
            systemUserDictionaryDao()?.queryAll(locale)?.forEach { entry ->
                val existing = byWord[entry.word]
                if (existing == null || entry.freq > existing.freq) byWord[entry.word] = entry
            }
        }
        return byWord.values.toList()
    }

    /**
     * DRS: inserts [word] into the app-private user dictionary for [locale], or bumps its
     * frequency when it already exists. Returns true when the dictionary was modified.
     * Writes only go to the app-private dictionary so the data stays local, exportable
     * and manageable from the built-in dictionary screen.
     */
    fun insertOrBumpUserWord(word: String, locale: DrsLocale, insertFreq: Int): Boolean {
        loadUserDictionariesIfNecessary()
        val dao = drsUserDictionaryDao() ?: return false
        val existing = dao.queryExactFuzzyLocale(word, locale).firstOrNull()
            ?: dao.queryExact(word, null).firstOrNull()
        return if (existing != null) {
            if (existing.freq < FREQUENCY_MAX) {
                dao.update(existing.copy(freq = (existing.freq + 16).coerceAtMost(FREQUENCY_MAX)))
                true
            } else {
                false
            }
        } else {
            dao.insert(UserDictionaryEntry(id = 0, word = word, freq = insertFreq, locale = locale.localeTag(), shortcut = null))
            true
        }
    }

    /**
     * DRS v1.20.0: drains [by] frequency points from a personally learned word after a
     * REJECTED (reverted) suggestion, and deletes the entry entirely once its frequency
     * drops to zero — unlearning what the user explicitly refused, instead of keeping
     * it at full strength forever. Returns true when the dictionary was modified.
     */
    fun demoteUserWord(word: String, locale: DrsLocale, by: Int): Boolean {
        if (by <= 0) return false
        loadUserDictionariesIfNecessary()
        val dao = drsUserDictionaryDao() ?: return false
        val existing = dao.queryExactFuzzyLocale(word, locale).firstOrNull()
            ?: dao.queryExact(word, null).firstOrNull()
            ?: return false
        val target = demoteTarget(existing.freq, by)
        return if (target == null) {
            dao.delete(existing)
            true
        } else {
            dao.update(existing.copy(freq = target))
            true
        }
    }

    @Synchronized
    fun drsUserDictionaryDao(): UserDictionaryDao? {
        return if (prefs.dictionary.enableDrsUserDictionary.get()) {
            drsUserDictionaryDatabase?.userDictionaryDao()
        } else {
            null
        }
    }

    @Synchronized
    fun drsUserDictionaryDatabase(): DrsUserDictionaryDatabase? {
        return if (prefs.dictionary.enableDrsUserDictionary.get()) {
            drsUserDictionaryDatabase
        } else {
            null
        }
    }

    @Synchronized
    fun systemUserDictionaryDao(): UserDictionaryDao? {
        return if (prefs.dictionary.enableSystemUserDictionary.get()) {
            systemUserDictionaryDatabase?.userDictionaryDao()
        } else {
            null
        }
    }

    @Synchronized
    fun systemUserDictionaryDatabase(): SystemUserDictionaryDatabase? {
        return if (prefs.dictionary.enableSystemUserDictionary.get()) {
            systemUserDictionaryDatabase
        } else {
            null
        }
    }

    @Synchronized
    fun loadUserDictionariesIfNecessary() {
        val context = applicationContext.get() ?: return

        if (drsUserDictionaryDatabase == null && prefs.dictionary.enableDrsUserDictionary.get()) {
            drsUserDictionaryDatabase = Room.databaseBuilder(
                context,
                DrsUserDictionaryDatabase::class.java,
                DrsUserDictionaryDatabase.DB_FILE_NAME
            ).allowMainThreadQueries().build()
        }
        if (systemUserDictionaryDatabase == null && prefs.dictionary.enableSystemUserDictionary.get()) {
            systemUserDictionaryDatabase = SystemUserDictionaryDatabase(context)
        }
    }


}
