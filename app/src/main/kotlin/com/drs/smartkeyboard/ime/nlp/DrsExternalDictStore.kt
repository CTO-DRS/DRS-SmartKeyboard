/*
 * Copyright (C) 2025 The DRS Smart Keyboard Project
 */

package com.drs.smartkeyboard.ime.nlp

import android.content.Context
import com.drs.smartkeyboard.ime.nlp.latin.LatinWordNormalize
import com.drs.smartkeyboard.lib.devtools.flogError
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.io.File
import java.util.UUID

/**
 * DRS v2.8.0 «قاموسك من ملفك» — app-private persistence of user-imported
 * external dictionary files.
 *
 * Deterministic by construction:
 *  - each import becomes ONE canonical JSON file in
 *    `noBackupFilesDir/drs_external_dicts/<lang>_<id8>.json`, written with the
 *    tmp+rename atomic pattern,
 *  - the payload carries `{"v":1,"id","lang","source","words":{...}}` so any
 *    future format stays readable,
 *  - [wordsFor] merges the files for a language in id-sorted order with the
 *    max-frequency dedupe — the same result on every device, every time,
 *  - ids are validated against a strict pattern before any file operation
 *    (the delete path can never escape the store dir).
 */
class DrsExternalDictStore(private val context: Context) {

    data class Entry(val id: String, val lang: String, val sourceName: String, val wordCount: Int)

    private val dir: File get() = File(context.noBackupFilesDir, DIR_NAME)
    private val json = Json

    /** All imported dictionaries, id-sorted (deterministic listing order). */
    fun list(): List<Entry> = runCatching {
        dir.listFiles { f -> f.isFile && f.name.endsWith(".json") }
            ?.mapNotNull { file ->
                runCatching {
                    val obj = json.parseToJsonElement(file.readText()).jsonObject
                    Entry(
                        id = obj.getValue("id").jsonPrimitive.content,
                        lang = obj.getValue("lang").jsonPrimitive.content,
                        sourceName = obj.getValue("source").jsonPrimitive.content,
                        wordCount = obj["words"]?.jsonObject?.size ?: 0,
                    )
                }.onFailure { t ->
                    flogError { "External dict store: unreadable file '${file.name}': ${t.message}" }
                }.getOrNull()
            }
            .orEmpty()
            .sortedBy { it.id }
    }.onFailure { t ->
        flogError { "External dict store: list failed: ${t.message}" }
    }.getOrDefault(emptyList())

    /**
     * Parses [text] with [DrsExternalDictFormats] and persists it for [lang].
     * Errors carry stable message keys (`UNSUPPORTED_LANG`, or the parser's
     * `MALFORMED_JSON` / `NO_WORDS`) the UI maps to localized strings.
     */
    fun import(lang: String, sourceName: String, text: String): Result<Entry> = runCatching {
        require(lang in SUPPORTED_LANGS) { "UNSUPPORTED_LANG" }
        val parsed = DrsExternalDictFormats.parse(text)
        dir.mkdirs()
        val id = "${lang}_${UUID.randomUUID().toString().take(8)}"
        val payload: JsonObject = buildJsonObject {
            put("v", 1)
            put("id", id)
            put("lang", lang)
            put("source", sourceName)
            put("words", JsonObject(parsed.words.mapValues { (_, f) -> JsonPrimitive(f) }))
        }
        val target = File(dir, "$id.json")
        val tmp = File(dir, "$id.json.tmp")
        tmp.writeText(payload.toString())
        if (!tmp.renameTo(target)) {
            tmp.copyTo(target, overwrite = true)
            tmp.delete()
        }
        Entry(id = id, lang = lang, sourceName = sourceName, wordCount = parsed.words.size)
    }.onFailure { t ->
        flogError { "External dict store: import failed: ${t.message}" }
    }

    /** Removes one imported dictionary; [id] must match the store's own naming. */
    fun remove(id: String): Boolean {
        if (!ID_PATTERN.matches(id)) return false
        val target = File(dir, "$id.json")
        return runCatching { target.exists() && target.delete() }
            .onFailure { t -> flogError { "External dict store: remove '$id' failed: ${t.message}" } }
            .getOrDefault(false)
    }

    /**
     * The merged word map for [lang]: every stored file's words folded together
     * in id-sorted order, max frequency winning on duplicates. Empty when the
     * user imported nothing for this language.
     */
    fun wordsFor(lang: String): Map<String, Int> = runCatching {
        val files = dir.listFiles { f -> f.isFile && f.name.startsWith("${lang}_") && f.name.endsWith(".json") }
            ?.sortedBy { it.name } ?: return emptyMap()
        if (files.isEmpty()) return emptyMap()
        var merged: Map<String, Int> = emptyMap()
        for (file in files) {
            val words = runCatching {
                json.parseToJsonElement(file.readText()).jsonObject["words"]?.jsonObject
                    ?.mapValues { (_, v) -> v.jsonPrimitive.content.toIntOrNull() ?: 0 }
                    ?.filterKeys { DrsExternalDictFormats.isAcceptableWord(it) }
                    .orEmpty()
            }.getOrDefault(emptyMap())
            merged = DrsExternalDictMerger.merge(merged, words)
        }
        merged
    }.onFailure { t ->
        flogError { "External dict store: wordsFor('$lang') failed: ${t.message}" }
    }.getOrDefault(emptyMap())

    companion object {
        const val DIR_NAME = "drs_external_dicts"

        /** `ar_ab12cd34` — language code + 8 hex chars. */
        private val ID_PATTERN = """^[a-z]{2,3}_[0-9a-f]{8}$""".toRegex()

        /** Every language the suggestion engine actually loads a dictionary for. */
        val SUPPORTED_LANGS: Set<String> = LatinWordNormalize.LANGUAGE_DICT_ASSETS.keys + "en"
    }
}
