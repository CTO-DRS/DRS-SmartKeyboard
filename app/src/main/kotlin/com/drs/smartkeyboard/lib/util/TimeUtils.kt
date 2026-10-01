/*
 * Copyright (C) 2022-2025 The DRS Smart Keyboard Project
 */

package com.drs.smartkeyboard.lib.util

import android.icu.text.SimpleDateFormat
import com.drs.smartkeyboard.lib.DrsLocale
import org.drs.jetpref.datastore.model.LocalTime
import java.time.Instant
import java.time.format.DateTimeFormatter

object TimeUtils {
    private val ISO_INSTANT = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ", DrsLocale.ENGLISH.base)

    fun currentUtcTimestamp(): CharSequence {
        return DateTimeFormatter.ISO_INSTANT.format(Instant.now())
    }

    val LocalTime.javaLocalTime: java.time.LocalTime
        get() = java.time.LocalTime.of(hour, minute)
}
