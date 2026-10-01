/*
 * Copyright (C) 2021-2025 The DRS Smart Keyboard Project
 */

package com.drs.smartkeyboard.lib.io

import com.drs.smartkeyboard.lib.cache.CacheManager
import org.drs.lib.kotlin.io.FsFile

object FileRegistry {
    val BackupArchive = Entry(
        type = Type.BINARY,
        fileExt = "zip",
        mediaType = "application/zip",
        alternativeMediaTypes = listOf(
            "application/octet-stream",
        ),
    )

    val FlexExtension = Entry(
        type = Type.BINARY,
        fileExt = "flex",
        mediaType = "application/vnd.drskeyboard.extension+zip",
        alternativeMediaTypes = listOf(
            "application/zip",
            "application/octet-stream",
        ),
    )

    fun guessMediaType(file: FsFile, givenMediaType: String?): String? {
        return when (file.extension) {
            FlexExtension.fileExt -> {
                if (FlexExtension.alternativeMediaTypes.contains(givenMediaType)) {
                    FlexExtension.mediaType
                } else {
                    givenMediaType
                }
            }
            else -> givenMediaType
        }
    }

    fun matchesFileFilter(fileInfo: CacheManager.FileInfo, filter: List<Entry>): Boolean {
        val fileExt = fileInfo.file.extension
        filter.forEach {
            if (it.fileExt == fileExt ||
                it.mediaType == fileInfo.mediaType ||
                it.alternativeMediaTypes.contains(fileInfo.mediaType)
            ) {
                return true
            }
        }
        return false
    }

    data class Entry(
        val type: Type,
        val fileExt: String,
        val mediaType: String,
        val alternativeMediaTypes: List<String> = emptyList(),
    )

    enum class Type(val id: String) {
        BINARY("bin"),
        TEXT("txt");
    }
}
