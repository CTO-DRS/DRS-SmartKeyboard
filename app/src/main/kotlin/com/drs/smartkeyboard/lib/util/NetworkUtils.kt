/*
 * Copyright (C) 2022-2025 The DRS Smart Keyboard Project
 */

package com.drs.smartkeyboard.lib.util

import org.drs.lib.android.AndroidVersion

/**
 * Helper object containing methods to validate and extract network names and components from strings.
 */
@Suppress("RegExpRedundantEscape", "RegExpUnnecessaryNonCapturingGroup")
object NetworkUtils {
    private val Ipv4Regex = """(?<Ipv4>(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?\.){3}(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?))""".toRegex()
    private val Ipv6Regex = """(?<Ipv6>(([0-9a-fA-F]{1,4}:){7}[0-9a-fA-F]{1,4}|([0-9a-fA-F]{1,4}:){1,7}:|([0-9a-fA-F]{1,4}:){1,6}:[0-9a-fA-F]{1,4}|([0-9a-fA-F]{1,4}:){1,5}(:[0-9a-fA-F]{1,4}){1,2}|([0-9a-fA-F]{1,4}:){1,4}(:[0-9a-fA-F]{1,4}){1,3}|([0-9a-fA-F]{1,4}:){1,3}(:[0-9a-fA-F]{1,4}){1,4}|([0-9a-fA-F]{1,4}:){1,2}(:[0-9a-fA-F]{1,4}){1,5}|[0-9a-fA-F]{1,4}:((:[0-9a-fA-F]{1,4}){1,6})|:((:[0-9a-fA-F]{1,4}){1,7}|:)|fe80:(:[0-9a-fA-F]{0,4}){0,4}%[0-9a-zA-Z]+|::(ffff(:0{1,4})?:)?((25[0-5]|(2[0-4]|1?[0-9])?[0-9])\.){3}(25[0-5]|(2[0-4]|1?[0-9])?[0-9])|([0-9a-fA-F]{1,4}:){1,4}:((25[0-5]|(2[0-4]|1?[0-9])?[0-9])\.){3}(25[0-5]|(2[0-4]|1?[0-9])?[0-9])))""".toRegex()
    private val HostRegex = """(?<Host>(?:[a-zA-Z0-9][a-zA-Z0-9\-]+[a-zA-Z0-9]\.)+[a-zA-Z]{2,}|$Ipv4Regex|$Ipv6Regex)""".toRegex()
    private val TcpIpPortRegex = """(?<TcpIpPort>6553[0-5]|655[0-2][0-9]|65[0-4][0-9]{2}|6[0-4][0-9]{3}|(?<![0-9])[0-5]?[0-9]{1,4}(?![0-9]))""".toRegex()
    private val UrlRegex = """(?<Url>(?:(?:(?:https?:\/\/)?$HostRegex)|(?:https?:\/\/[a-zA-Z]+))(?::$TcpIpPortRegex)?(?:\/[\p{L}0-9.,;?'\\\/+&%$#=~_\-]*)?)""".toRegex()
    private val EmailRegex = """(?<Email>(?:[a-zA-Z0-9!#${'$'}%&'*+/=?^_`{|}~-]+(?:\.[a-zA-Z0-9!#${'$'}%&'*+/=?^_`{|}~-]+)*|"(?:[\x01-\x08\x0b\x0c\x0e-\x1f\x21\x23-\x5b\x5d-\x7f]|\\[\x01-\x09\x0b\x0c\x0e-\x7f])*")@$HostRegex)""".toRegex()
    private val PhoneNumberRegex = """(?<Phone>(?<![0-9]|[0-9][\x20.-]|[+]|[-])(?:(?:0?1-)?[0-9]{3}-[A-Z]{2}(?:-?[A-Z]){4}[A-Z]|(?:[(]?(?:[+]|00)[\x20.-]?)?(?:[(]?[0-9](?:[)]?[\x20.-]?[(]?[0-9]){4,14}[)]?))(?![\x20.-][0-9]|[0-9]|[-]))""".toRegex()

    fun isUrl(str: CharSequence): Boolean {
        return UrlRegex.matches(str.trim())
    }

    fun getUrls(str: CharSequence): List<MatchGroup> {
        return UrlRegex.findAll(str).mapNotNull { it.groups["Url"] }.toList()
    }

    fun isEmailAddress(str: CharSequence): Boolean {
        return EmailRegex.matches(str.trim())
    }

    fun getEmailAddresses(str: CharSequence): List<MatchGroup> {
        return EmailRegex.findAll(str).mapNotNull { it.groups["Email"] }.toList()
    }

    fun isPhoneNumber(str: CharSequence): Boolean {
        return PhoneNumberRegex.matches(str.trim())
    }

    fun getPhoneNumbers(str: CharSequence): List<MatchGroup> {
        return PhoneNumberRegex.findAll(str).mapNotNull { it.groups["Phone"] }.toList()
    }

    /**
     * DRS v1.26.0: the tested normalization of a phone-number match — the
     * regex deliberately tolerates a fully parenthesized number like
     * `(0541234567)` (java.util.regex has no `\K`, so the capture cannot
     * exclude its own surrounding parens), and this pure step strips the
     * wrapper the user never typed as the suggestion's text:
     * - a complete outer pair `(...)` is removed;
     * - a leading `(` with no closing `)` anywhere is removed (a truncated
     *   wrapper the regex had to consume to reach the digits);
     * - anything balanced (like the area-code pair in `(054) 123 4567`)
     *   stays exactly as the user wrote it.
     */
    fun normalizePhoneNumberMatch(value: String): String {
        val trimmed = value.trim()
        if (trimmed.length < 2) return trimmed
        if (trimmed.first() == '(' && trimmed.last() == ')') {
            return trimmed.substring(1, trimmed.length - 1).trim()
        }
        if (trimmed.startsWith("(") && !isParenBalanced(trimmed)) {
            return trimmed.removePrefix("(").trim()
        }
        return trimmed
    }

    /**
     * Pure: a simple depth scan — `(` descends, `)` ascends, and the walk
     * must never go below zero nor end above zero. JVM-tested.
     */
    internal fun isParenBalanced(value: String): Boolean {
        var depth = 0
        for (ch in value) {
            when (ch) {
                '(' -> depth++
                ')' -> depth--
            }
            if (depth < 0) return false
        }
        return depth == 0
    }
}
