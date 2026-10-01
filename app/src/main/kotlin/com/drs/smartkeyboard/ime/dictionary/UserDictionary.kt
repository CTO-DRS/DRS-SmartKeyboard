/*
 * Copyright (C) 2021-2025 The DRS Smart Keyboard Project
 */

package com.drs.smartkeyboard.ime.dictionary

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.UserDictionary
import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.Update
import com.drs.smartkeyboard.R
import com.drs.smartkeyboard.lib.DrsLocale
import com.drs.smartkeyboard.lib.ValidationRule
import org.drs.lib.android.readText
import org.drs.lib.android.writeText
import org.drs.lib.kotlin.tryOrNull
import java.lang.ref.WeakReference

private const val WORDS_TABLE = "words"

/**
 * DRS p6 (A4): pure locale-tag fallback helper — the bare language tag of
 * [tag] ("en_US" -> "en"). Returns null when [tag] is null/blank or already
 * language-only (no fallback exists). JVM-testable; the Room DAO mirrors
 * this exact fallback in SQL (substr/instr on the bound :locale) because
 * Room cannot derive a second parameter from one argument.
 */
internal fun languageFallbackTag(tag: String?): String? {
    if (tag.isNullOrBlank()) return null
    val dash = tag.indexOf('-')
    return if (dash <= 0) null else tag.substring(0, dash)
}

const val FREQUENCY_MIN = 1
const val FREQUENCY_MAX = 255
const val FREQUENCY_DEFAULT = 128

private const val SORT_BY_WORD_ASC = "${UserDictionary.Words.WORD} ASC"
private const val SORT_BY_WORD_DESC = "${UserDictionary.Words.WORD} DESC"
private const val SORT_BY_FREQ_ASC = "${UserDictionary.Words.FREQUENCY} ASC"
private const val SORT_BY_FREQ_DESC = "${UserDictionary.Words.FREQUENCY} DESC"

private val PROJECTIONS: Array<String> = arrayOf(
    UserDictionary.Words._ID,
    UserDictionary.Words.WORD,
    UserDictionary.Words.FREQUENCY,
    UserDictionary.Words.LOCALE,
    UserDictionary.Words.SHORTCUT,
)

private val PROJECTIONS_LANGUAGE: Array<String> = arrayOf(
    UserDictionary.Words.LOCALE,
)

@Entity(tableName = WORDS_TABLE)
data class UserDictionaryEntry(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = UserDictionary.Words._ID, index = true)
    val id: Long,
    @ColumnInfo(name = UserDictionary.Words.WORD)
    val word: String,
    @ColumnInfo(name = UserDictionary.Words.FREQUENCY)
    val freq: Int,
    @ColumnInfo(name = UserDictionary.Words.LOCALE)
    val locale: String?,
    @ColumnInfo(name = UserDictionary.Words.SHORTCUT)
    val shortcut: String?,
)

@Dao
interface UserDictionaryDao {
    companion object {
        private const val SELECT_ALL_FROM_WORDS =
            "SELECT * FROM $WORDS_TABLE"

        /**
         * DRS p6 (A4): locale fallback — match the full tag ("en_US"), the
         * bare language ("en", computed in SQL as the substr/instr mirror of
         * [languageFallbackTag]) AND global null-locale entries, so words
         * learned under en_US are visible from en subtypes and vice versa.
         * With a NULL :locale binding every comparison involving it is
         * SQL-NULL (never true), leaving only the IS NULL branch — the
         * documented "ask for null -> global words only" contract.
         */
        private const val LOCALE_MATCHES =
            "(${UserDictionary.Words.LOCALE} = :locale " +
                "OR ${UserDictionary.Words.LOCALE} = substr(:locale, 1, instr(:locale || '-', '-') - 1) " +
                "OR ${UserDictionary.Words.LOCALE} IS NULL)"

        /**
         * DRS p6 (A9): the no-locale / per-locale full scans are ordered by
         * frequency DESC so the take(4000) personalization cut (and any UI
         * limit) keeps the most valuable (most frequently used) words
         * instead of an arbitrary insertion-order slice.
         */
        private const val ORDER_FREQ_DESC = " ORDER BY ${UserDictionary.Words.FREQUENCY} DESC"
    }

    @Query("$SELECT_ALL_FROM_WORDS WHERE ${UserDictionary.Words.WORD} LIKE '%' || :word || '%'")
    fun query(word: String): List<UserDictionaryEntry>

    @Query("$SELECT_ALL_FROM_WORDS WHERE ${UserDictionary.Words.WORD} LIKE '%' || :word || '%' AND $LOCALE_MATCHES")
    fun query(word: String, locale: DrsLocale?): List<UserDictionaryEntry>

    @Query("$SELECT_ALL_FROM_WORDS WHERE ${UserDictionary.Words.SHORTCUT} = :shortcut")
    fun queryShortcut(shortcut: String): List<UserDictionaryEntry>

    @Query("$SELECT_ALL_FROM_WORDS WHERE ${UserDictionary.Words.SHORTCUT} = :shortcut AND $LOCALE_MATCHES")
    fun queryShortcut(shortcut: String, locale: DrsLocale?): List<UserDictionaryEntry>

    @Query("$SELECT_ALL_FROM_WORDS$ORDER_FREQ_DESC")
    fun queryAll(): List<UserDictionaryEntry>

    // DRS v1.20.0: entries stored WITHOUT a locale are global ("all languages") words.
    // The old query returned them only when asking for null, so a word learned while a
    // different locale was active (or imported as "all") never surfaced in suggestions
    // for any specific language — the documented contract said the opposite.
    // DRS p6 (A4): the full tag, its bare-language fallback AND null-locale
    // entries all match (see [LOCALE_MATCHES]); ordered by frequency (A9).
    @Query("$SELECT_ALL_FROM_WORDS WHERE (${UserDictionary.Words.LOCALE} = :locale OR ${UserDictionary.Words.LOCALE} = substr(:locale, 1, instr(:locale || '-', '-') - 1) OR ${UserDictionary.Words.LOCALE} IS NULL)$ORDER_FREQ_DESC")
    fun queryAll(locale: DrsLocale?): List<UserDictionaryEntry>

    @Query("$SELECT_ALL_FROM_WORDS WHERE ${UserDictionary.Words.WORD} = :word")
    fun queryExact(word: String): List<UserDictionaryEntry>

    @Query("$SELECT_ALL_FROM_WORDS WHERE ${UserDictionary.Words.WORD} = :word AND (${UserDictionary.Words.LOCALE} = :locale OR (${UserDictionary.Words.LOCALE} IS NULL AND :locale IS NULL))")
    fun queryExact(word: String, locale: DrsLocale?): List<UserDictionaryEntry>

    @Query("$SELECT_ALL_FROM_WORDS WHERE ${UserDictionary.Words.WORD} = :word AND $LOCALE_MATCHES")
    fun queryExactFuzzyLocale(word: String, locale: DrsLocale?): List<UserDictionaryEntry>

    @Query("SELECT DISTINCT ${UserDictionary.Words.LOCALE} FROM $WORDS_TABLE")
    fun queryLanguageList(): List<DrsLocale?>

    @Insert
    fun insert(entry: UserDictionaryEntry)

    @Update
    fun update(entry: UserDictionaryEntry)

    @Delete
    fun delete(entry: UserDictionaryEntry)

    @Query("DELETE FROM $WORDS_TABLE")
    fun deleteAll()
}

interface UserDictionaryDatabase {
    fun userDictionaryDao(): UserDictionaryDao

    fun reset()

    fun importCombinedList(context: Context, uri: Uri) {
        context.contentResolver.readText(uri) { src ->
            var isFirstLine = true
            src.forEachLine { line ->
                if (isFirstLine) {
                    // Ignore
                    isFirstLine = false
                } else {
                    var word: String? = null
                    var freq: Int? = null
                    var locale: String? = null
                    var shortcut: String? = null
                    for (property in line.split(';')) {
                        val keyValuePair = property.split('=')
                        check(keyValuePair.size == 2) { "Error at source line `$line`: Key-Value pair expected, but either only key or too many values provided" }
                        val key = keyValuePair[0].trim().lowercase()
                        val value = keyValuePair[1].trim()
                        when (key) {
                            "w", "word" -> word = value.ifBlank { null }
                            "f", "freq" -> {
                                val number = value.toIntOrNull(10)
                                checkNotNull(number) { "Error at source line `$line`: Freq is not a valid decimal number" }
                                check(number in FREQUENCY_MIN..FREQUENCY_MAX) {
                                    "Error at source line `$line`: Freq not within range of $FREQUENCY_MIN and $FREQUENCY_MAX"
                                }
                                freq = number
                            }
                            "l", "locale" -> locale = when (value) {
                                "all", "null", "" -> null
                                else -> value.ifBlank { null }
                            }
                            "s", "shortcut" -> shortcut = value.ifBlank { null }
                        }
                    }
                    checkNotNull(word) { "Error at source line `$line`: Word cannot be empty or missing" }
                    checkNotNull(freq) { "Error at source line `$line`: Freq cannot be empty or missing" }
                    val alreadyExistingEntries = userDictionaryDao().queryExact(
                        word, locale?.let { DrsLocale.fromTag(it) },
                    )
                    if (alreadyExistingEntries.isNotEmpty()) {
                        userDictionaryDao().update(UserDictionaryEntry(alreadyExistingEntries[0].id, word, freq, locale, shortcut))
                    } else {
                        userDictionaryDao().insert(UserDictionaryEntry(0, word, freq, locale, shortcut))
                    }
                }
            }
        }
    }

    fun exportCombinedList(context: Context, uri: Uri) {
        context.contentResolver.writeText(uri) { dst ->
            StringBuilder().apply {
                append("dictionary=")
                append(uri.lastPathSegment)
                append(";date=")
                append(System.currentTimeMillis())
                append(";generated-by=")
                append(context.packageName)
                append(";version=1")
                appendLine()
                dst.write(toString())
            }
            for (entry in userDictionaryDao().queryAll()) {
                dst.write(UserDictionaryFormats.entryLine(entry) + "\n")
            }
        }
    }

    /**
     * DRS v1.21.0: writes the whole DRS user dictionary in the same
     * combined-list text format [importCombinedList] parses — the backup
     * archive carries the learned words so a restore/factory-reset no
     * longer loses them. Returns the entry count.
     */
    fun exportToWriter(dst: java.io.Writer): Int {
        dst.append("dictionary=user_dictionary;version=1\n")
        val entries = userDictionaryDao().queryAll()
        for (entry in entries) {
            dst.append(UserDictionaryFormats.entryLine(entry)).append('\n')
        }
        return entries.size
    }

    /**
     * DRS v1.21.0: reads the combined-list text format from [src] and
     * merges it into the dictionary (existing words are updated, new ones
     * inserted — the same dedup [importCombinedList] applies). Malformed
     * lines are skipped honestly; returns the entries merged.
     */
    fun importFromReader(src: java.io.Reader): Int {
        var merged = 0
        var isFirstLine = true
        src.forEachLine { line ->
            if (isFirstLine) {
                isFirstLine = false
                return@forEachLine
            }
            val entry = UserDictionaryFormats.parseEntryLine(line) ?: return@forEachLine
            val existing = userDictionaryDao().queryExact(entry.word, entry.locale?.let { DrsLocale.fromTag(it) })
            if (existing.isNotEmpty()) {
                userDictionaryDao().update(UserDictionaryEntry(existing[0].id, entry.word, entry.freq, entry.locale, entry.shortcut))
            } else {
                userDictionaryDao().insert(UserDictionaryEntry(0, entry.word, entry.freq, entry.locale, entry.shortcut))
            }
            merged += 1
        }
        return merged
    }
}

/**
 * DRS v1.21.0: the pure line-format helpers of the combined-list text
 * format — [entryLine] renders one entry the way `exportCombinedList`
 * always has, [parseEntryLine] parses one data line back (null when the
 * line is malformed or lacks a word/freq). JVM-testable round trip.
 */
object UserDictionaryFormats {
    fun entryLine(entry: UserDictionaryEntry): String {
        return StringBuilder().apply {
            append(" w=")
            append(entry.word)
            append(";f=")
            append(entry.freq)
            append(";l=")
            append(entry.locale) // always append locale even if null
            if (entry.shortcut != null) {
                append(";s=")
                append(entry.shortcut)
            }
        }.toString()
    }

    fun parseEntryLine(line: String): UserDictionaryEntry? {
        var word: String? = null
        var freq: Int? = null
        var locale: String? = null
        var shortcut: String? = null
        for (property in line.split(';')) {
            if (property.isBlank()) continue
            val keyValuePair = property.split('=', limit = 2)
            if (keyValuePair.size != 2) return null
            val key = keyValuePair[0].trim().lowercase()
            val value = keyValuePair[1].trim()
            when (key) {
                "w", "word" -> word = value.ifBlank { null }
                "f", "freq" -> freq = value.toIntOrNull(10)
                "l", "locale" -> locale = when (value) {
                    "all", "null", "" -> null
                    else -> value.ifBlank { null }
                }
                "s", "shortcut" -> shortcut = value.ifBlank { null }
            }
        }
        val w = word ?: return null
        val f = freq ?: return null
        if (f !in FREQUENCY_MIN..FREQUENCY_MAX) return null
        return UserDictionaryEntry(0, w, f, locale, shortcut)
    }
}

@Database(entities = [UserDictionaryEntry::class], version = 1)
@TypeConverters(DrsUserDictionaryDatabase.Converters::class)
abstract class DrsUserDictionaryDatabase : RoomDatabase(), UserDictionaryDatabase {
    companion object {
        const val DB_FILE_NAME = "drs_user_dictionary"
    }

    abstract override fun userDictionaryDao(): UserDictionaryDao

    /**
     * DRS v1.19.0: real reset — wipes every personal word from the Room
     * database. The method was a `TODO("Not yet implemented")` landmine
     * since the beginning: any caller would have crashed the process.
     */
    override fun reset() {
        userDictionaryDao().deleteAll()
    }

    class Converters {
        @TypeConverter
        fun localeToString(locale: DrsLocale?): String? {
            return when (locale) {
                null -> null
                else -> locale.localeTag()
            }
        }

        @TypeConverter
        fun stringToLocale(string: String?): DrsLocale? {
            return when (string) {
                null, "all", "null", "" -> null
                else -> DrsLocale.fromTag(string)
            }
        }
    }
}

class SystemUserDictionaryDatabase(context: Context) : UserDictionaryDatabase {
    private val applicationContext: WeakReference<Context> = WeakReference(context.applicationContext ?: context)

    private val dao = object : UserDictionaryDao {
        override fun query(word: String): List<UserDictionaryEntry> {
            return queryResolver(
                selection = "${UserDictionary.Words.WORD} LIKE ?",
                selectionArgs = arrayOf("%$word%"),
                sortOrder = SORT_BY_FREQ_DESC,
            )
        }

        override fun query(word: String, locale: DrsLocale?): List<UserDictionaryEntry> {
            return if (locale == null) {
                queryResolver(
                    selection = "${UserDictionary.Words.WORD} LIKE ? AND ${UserDictionary.Words.LOCALE} IS NULL",
                    selectionArgs = arrayOf("%$word%"),
                    sortOrder = SORT_BY_FREQ_DESC,
                )
            } else {
                queryResolver(
                    selection = "${UserDictionary.Words.WORD} LIKE ? AND (${UserDictionary.Words.LOCALE} = ? OR ${UserDictionary.Words.LOCALE} = ? OR ${UserDictionary.Words.LOCALE} IS NULL)",
                    selectionArgs = arrayOf("%$word%", locale.localeTag(), locale.language),
                    sortOrder = SORT_BY_FREQ_DESC,
                )
            }
        }

        override fun queryShortcut(shortcut: String): List<UserDictionaryEntry> {
            return queryResolver(
                selection = "${UserDictionary.Words.SHORTCUT} = ?",
                selectionArgs = arrayOf(shortcut),
                sortOrder = SORT_BY_FREQ_DESC,
            )
        }

        override fun queryShortcut(shortcut: String, locale: DrsLocale?): List<UserDictionaryEntry> {
            return if (locale == null) {
                queryResolver(
                    selection = "${UserDictionary.Words.SHORTCUT} = ? AND ${UserDictionary.Words.LOCALE} IS NULL",
                    selectionArgs = arrayOf(shortcut),
                    sortOrder = SORT_BY_FREQ_DESC,
                )
            } else {
                queryResolver(
                    selection = "${UserDictionary.Words.SHORTCUT} = ? AND (${UserDictionary.Words.LOCALE} = ? OR ${UserDictionary.Words.LOCALE} = ? OR ${UserDictionary.Words.LOCALE} IS NULL)",
                    selectionArgs = arrayOf(shortcut, locale.localeTag(), locale.language),
                    sortOrder = SORT_BY_FREQ_DESC,
                )
            }
        }

        override fun queryAll(): List<UserDictionaryEntry> {
            return queryResolver(
                selection = null,
                selectionArgs = null,
                sortOrder = SORT_BY_FREQ_DESC,
            )
        }

        override fun queryAll(locale: DrsLocale?): List<UserDictionaryEntry> {
            return if (locale == null) {
                queryResolver(
                    selection = "${UserDictionary.Words.LOCALE} IS NULL",
                    selectionArgs = null,
                    sortOrder = SORT_BY_FREQ_DESC,
                )
            } else {
                // DRS v1.20.0: mirror queryExactFuzzyLocale's semantics — match the full
                // tag, the bare language, AND global (null-locale) entries, so system
                // words stored for "en" serve an "en_US" subtype and null-locale words
                // serve every language.
                queryResolver(
                    selection = "${UserDictionary.Words.LOCALE} = ? OR ${UserDictionary.Words.LOCALE} = ? OR ${UserDictionary.Words.LOCALE} IS NULL",
                    selectionArgs = arrayOf(locale.localeTag(), locale.language),
                    sortOrder = SORT_BY_FREQ_DESC,
                )
            }
        }

        override fun queryExact(word: String): List<UserDictionaryEntry> {
            return queryResolver(
                selection = "${UserDictionary.Words.WORD} = ?",
                selectionArgs = arrayOf(word),
                sortOrder = null,
            )
        }

        override fun queryExact(word: String, locale: DrsLocale?): List<UserDictionaryEntry> {
            return if (locale == null) {
                queryResolver(
                    selection = "${UserDictionary.Words.WORD} = ? AND ${UserDictionary.Words.LOCALE} IS NULL",
                    selectionArgs = arrayOf(word),
                    sortOrder = SORT_BY_FREQ_DESC,
                )
            } else {
                queryResolver(
                    selection = "${UserDictionary.Words.WORD} = ? AND ${UserDictionary.Words.LOCALE} = ?",
                    selectionArgs = arrayOf(word, locale.localeTag()),
                    sortOrder = SORT_BY_FREQ_DESC,
                )
            }
        }

        override fun queryExactFuzzyLocale(word: String, locale: DrsLocale?): List<UserDictionaryEntry> {
            return if (locale == null) {
                queryResolver(
                    selection = "${UserDictionary.Words.WORD} = ? AND ${UserDictionary.Words.LOCALE} IS NULL",
                    selectionArgs = arrayOf(word),
                    sortOrder = SORT_BY_FREQ_DESC,
                )
            } else {
                // DRS p6 (A4): mirror the Room DAO's (tag OR lang OR NULL)
                // fallback — the bare-language binding comes from the pure
                // [languageFallbackTag] helper (was: tag-or-null only, so a
                // word stored under "en" was invisible to an "en_US" subtype).
                queryResolver(
                    selection = "${UserDictionary.Words.WORD} = ? AND (${UserDictionary.Words.LOCALE} = ? OR ${UserDictionary.Words.LOCALE} = ? OR ${UserDictionary.Words.LOCALE} IS NULL)",
                    selectionArgs = arrayOf(
                        word,
                        locale.localeTag(),
                        languageFallbackTag(locale.localeTag()) ?: locale.localeTag(),
                    ),
                    sortOrder = SORT_BY_FREQ_DESC,
                )
            }
        }

        override fun queryLanguageList(): List<DrsLocale?> {
            val resolver = applicationContext.get()?.contentResolver ?: return listOf()
            val cursor = resolver.query(
                UserDictionary.Words.CONTENT_URI,
                PROJECTIONS_LANGUAGE,
                null,
                null,
                null
            ) ?: return listOf()
            if (cursor.count <= 0) {
                return listOf()
            }
            val localeIndex = cursor.getColumnIndex(UserDictionary.Words.LOCALE)
            val retList = mutableSetOf<DrsLocale?>()
            while (cursor.moveToNext()) {
                val localeStr = cursor.getString(localeIndex)
                if (localeStr == null) {
                    retList.add(null)
                } else {
                    retList.add(DrsLocale.fromTag(localeStr))
                }
            }
            cursor.close()
            return retList.toList()
        }

        private fun queryResolver(selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): List<UserDictionaryEntry> {
            val resolver = applicationContext.get()?.contentResolver ?: return listOf()
            val cursor = resolver.query(
                UserDictionary.Words.CONTENT_URI,
                PROJECTIONS,
                selection,
                selectionArgs,
                sortOrder
            ) ?: return listOf()
            return parseEntries(cursor).also { cursor.close() }
        }

        private fun parseEntries(cursor: Cursor): List<UserDictionaryEntry> {
            if (cursor.count <= 0) {
                return listOf()
            }
            val idIndex = cursor.getColumnIndex(UserDictionary.Words._ID)
            val wordIndex = cursor.getColumnIndex(UserDictionary.Words.WORD)
            val freqIndex = cursor.getColumnIndex(UserDictionary.Words.FREQUENCY)
            val localeIndex = cursor.getColumnIndex(UserDictionary.Words.LOCALE)
            val shortcutIndex = cursor.getColumnIndex(UserDictionary.Words.SHORTCUT)
            val retList = mutableListOf<UserDictionaryEntry>()
            while (cursor.moveToNext()) {
                retList.add(
                    UserDictionaryEntry(
                        id = cursor.getLong(idIndex),
                        word = cursor.getString(wordIndex),
                        freq = cursor.getInt(freqIndex),
                        locale = cursor.getString(localeIndex),
                        shortcut = cursor.getString(shortcutIndex)
                    )
                )
            }
            return retList
        }

        override fun insert(entry: UserDictionaryEntry) {
            val resolver = applicationContext.get()?.contentResolver ?: return
            val contentValues = ContentValues(5).apply {
                put(UserDictionary.Words.WORD, entry.word)
                put(UserDictionary.Words.FREQUENCY, entry.freq)
                put(UserDictionary.Words.LOCALE, entry.locale)
                put(UserDictionary.Words.APP_ID, 0)
                put(UserDictionary.Words.SHORTCUT, entry.shortcut)
            }
            resolver.insert(UserDictionary.Words.CONTENT_URI, contentValues)
        }

        override fun update(entry: UserDictionaryEntry) {
            val resolver = applicationContext.get()?.contentResolver ?: return
            val contentValues = ContentValues(4).apply {
                put(UserDictionary.Words.WORD, entry.word)
                put(UserDictionary.Words.FREQUENCY, entry.freq)
                put(UserDictionary.Words.LOCALE, entry.locale)
                put(UserDictionary.Words.SHORTCUT, entry.shortcut)
            }
            resolver.update(UserDictionary.Words.CONTENT_URI, contentValues, "${UserDictionary.Words._ID} = ${entry.id}", null)
        }

        override fun delete(entry: UserDictionaryEntry) {
            val resolver = applicationContext.get()?.contentResolver ?: return
            resolver.delete(UserDictionary.Words.CONTENT_URI, "${UserDictionary.Words._ID} = ${entry.id}", null)
        }

        override fun deleteAll() {
            // DRS v1.19.0: real wipe through the system provider. The
            // "Unsupported action" stub left deleteAll() silently doing
            // nothing and reset() as a TODO crash — both are real now.
            val resolver = applicationContext.get()?.contentResolver ?: return
            resolver.delete(UserDictionary.Words.CONTENT_URI, null, null)
        }
    }

    override fun userDictionaryDao(): UserDictionaryDao {
        return dao
    }

    override fun reset() {
        dao.deleteAll()
    }
}

object UserDictionaryValidation {
    private val WordRegex = """^[^\s;,]+${'$'}""".toRegex()

    val Word = ValidationRule<String> {
        forKlass = UserDictionaryEntry::class
        forProperty = "word"
        validator { input ->
            val str = input.trim()
            when {
                input.isBlank() -> resultInvalid(error = R.string.settings__udm__dialog__word_error_empty)
                !str.matches(WordRegex) -> resultInvalid(error = R.string.settings__udm__dialog__word_error_invalid, "regex" to WordRegex)
                else -> resultValid()
            }
        }
    }

    val Freq = ValidationRule<String> {
        forKlass = UserDictionaryEntry::class
        forProperty = "freq"
        validator { input ->
            val freq = input.trim().toIntOrNull(10)
            when {
                input.isBlank() -> resultInvalid(error = R.string.settings__udm__dialog__freq_error_empty)
                freq == null -> resultInvalid(error = R.string.settings__udm__dialog__freq_error_empty)
                freq < FREQUENCY_MIN || freq > FREQUENCY_MAX -> resultInvalid(error = R.string.settings__udm__dialog__freq_error_invalid)
                else -> resultValid()
            }
        }
    }

    val Shortcut = ValidationRule<String> {
        forKlass = UserDictionaryEntry::class
        forProperty = "shortcut"
        validator { input ->
            val str = input.trim()
            when {
                input.isBlank() -> resultValid() // Is optional
                !str.matches(WordRegex) -> resultInvalid(error = R.string.settings__udm__dialog__shortcut_error_invalid, "regex" to WordRegex)
                else -> resultValid()
            }
        }
    }

    val Locale = ValidationRule<String> {
        forKlass = UserDictionaryEntry::class
        forProperty = "locale"
        validator { input ->
            val str = input.trim()
            when {
                input.isBlank() -> resultValid() // Is optional
                tryOrNull { DrsLocale.fromTag(str) } == null -> resultInvalid(error = R.string.settings__udm__dialog__locale_error_invalid)
                else -> resultValid()
            }
        }
    }
}
