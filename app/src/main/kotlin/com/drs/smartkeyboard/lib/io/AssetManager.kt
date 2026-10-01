/*
 * Copyright (C) 2020-2025 The DRS Smart Keyboard Project
 */

package com.drs.smartkeyboard.lib.io

import android.content.Context
import com.drs.smartkeyboard.ime.keyboard.AbstractKeyData
import com.drs.smartkeyboard.ime.keyboard.CaseSelector
import com.drs.smartkeyboard.ime.keyboard.CharWidthSelector
import com.drs.smartkeyboard.ime.keyboard.KanaSelector
import com.drs.smartkeyboard.ime.keyboard.KeyData
import com.drs.smartkeyboard.ime.keyboard.LayoutDirectionSelector
import com.drs.smartkeyboard.ime.keyboard.ShiftStateSelector
import com.drs.smartkeyboard.ime.keyboard.VariationSelector
import com.drs.smartkeyboard.ime.text.keyboard.AutoTextKeyData
import com.drs.smartkeyboard.ime.text.keyboard.MultiTextKeyData
import com.drs.smartkeyboard.ime.text.keyboard.TextKeyData
import java.io.File
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import org.drs.lib.android.reader
import org.drs.lib.kotlin.resultErr
import org.drs.lib.kotlin.resultErrStr
import org.drs.lib.kotlin.resultOk

val DefaultJsonConfig = Json {
    classDiscriminator = "$"
    encodeDefaults = true
    ignoreUnknownKeys = true
    isLenient = true
    serializersModule = SerializersModule {
        polymorphic(AbstractKeyData::class) {
            subclass(TextKeyData::class, TextKeyData.serializer())
            subclass(AutoTextKeyData::class, AutoTextKeyData.serializer())
            subclass(MultiTextKeyData::class, MultiTextKeyData.serializer())
            subclass(CaseSelector::class, CaseSelector.serializer())
            subclass(ShiftStateSelector::class, ShiftStateSelector.serializer())
            subclass(VariationSelector::class, VariationSelector.serializer())
            subclass(LayoutDirectionSelector::class, LayoutDirectionSelector.serializer())
            subclass(CharWidthSelector::class, CharWidthSelector.serializer())
            subclass(KanaSelector::class, KanaSelector.serializer())
            defaultDeserializer { TextKeyData.serializer() }
        }
        polymorphic(KeyData::class) {
            subclass(TextKeyData::class, TextKeyData.serializer())
            subclass(AutoTextKeyData::class, AutoTextKeyData.serializer())
            subclass(MultiTextKeyData::class, MultiTextKeyData.serializer())
            defaultDeserializer { TextKeyData.serializer() }
        }
    }
}

fun DrsRef.delete(context: Context) {
    when {
        isCache || isInternal -> {
            absoluteFile(context).delete()
        }
        else -> error("Can not delete directory/file in location '${scheme}'.")
    }
}

fun DrsRef.hasAsset(context: Context): Boolean {
    return when {
        isAssets -> {
            try {
                val file = File(relativePath)
                val list = context.assets.list(file.parent ?: "")
                list?.contains(file.name) == true
            } catch (e: Exception) {
                false
            }
        }
        isCache || isInternal -> {
            val file = File(absolutePath(context))
            file.exists() && file.isFile
        }
        else -> false
    }
}

fun DrsRef.list(context: Context) = list(context, files = true, dirs = true)

fun DrsRef.listFiles(context: Context) = list(context, files = true, dirs = false)

fun DrsRef.listDirs(context: Context) = list(context, files = false, dirs = true)

private fun DrsRef.list(appContext: Context, files: Boolean, dirs: Boolean) = runCatching<List<DrsRef>> {
    when {
        !files && !dirs -> listOf()
        isAssets -> {
            appContext.assets.list(relativePath)?.mapNotNull { fileName ->
                val subList = appContext.assets.list("${relativePath}/$fileName") ?: return@mapNotNull null
                when {
                    files && dirs || files && subList.isEmpty() || dirs && subList.isNotEmpty() -> {
                        subRef(fileName)
                    }
                    else -> null
                }
            } ?: listOf()
        }
        isCache || isInternal -> {
            val dir = absoluteFile(appContext)
            if (dir.isDirectory) {
                when {
                    files && dirs -> dir.listFiles()?.toList()
                    files -> dir.listFiles()?.filter { it.isFile }
                    dirs -> dir.listFiles()?.filter { it.isDirectory }
                    else -> null
                }!!.map { subRef(it.name) }
            } else {
                listOf()
            }
        }
        else -> error("Unsupported DrsRef source!")
    }
}

fun <T> DrsRef.loadJsonAsset(
    context: Context,
    serializer: KSerializer<T>,
    jsonConfig: Json = DefaultJsonConfig,
) = runCatching<T> {
    val jsonStr = loadTextAsset(context).getOrThrow()
    jsonConfig.decodeFromString(serializer, jsonStr)
}

inline fun <reified T> loadJsonAsset(jsonStr: String, jsonConfig: Json = DefaultJsonConfig): Result<T> {
    return runCatching { jsonConfig.decodeFromString(jsonStr) }
}

fun <T> loadJsonAsset(
    jsonStr: String,
    serializer: KSerializer<T>,
    jsonConfig: Json = DefaultJsonConfig,
) = runCatching<T> {
    jsonConfig.decodeFromString(serializer, jsonStr)
}

fun DrsRef.loadTextAsset(context: Context): Result<String> {
    return when {
        isAssets -> runCatching {
            context.assets.reader(relativePath).use { it.readText() }
        }
        isCache || isInternal -> {
            val file = File(absolutePath(context))
            val contents = readTextFile(file).getOrElse { return resultErr(it) }
            if (contents.isBlank()) {
                resultErrStr("File is blank!")
            } else {
                resultOk(contents)
            }
        }
        else -> resultErrStr("Unsupported asset ref!")
    }
}

/**
 * Reads a given [file] and returns its content.
 *
 * @param file The file object.
 * @return The contents of the file or an empty string, if the file does not exist.
 */
private fun readTextFile(file: File) = runCatching {
    file.readText(Charsets.UTF_8)
}


