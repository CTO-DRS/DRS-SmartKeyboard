/*
 * Copyright (C) 2021-2025 The DRS Smart Keyboard Project
 */

package com.drs.smartkeyboard.app.settings.theme

/**
 * SnyggLevel indicates if a rule property is intended to be edited by all users (BASIC) or only by advanced users
 * (ADVANCED). This level is intended for theme editor UIs to hide certain properties in a "basic" mode, for the Snygg
 * theme engine internally this level will be ignored completely.
 */
enum class SnyggLevel : Comparable<SnyggLevel> {
    /** A property is intended to be edited by all users **/
    BASIC,
    /** A property is intended to be edited by advanced users **/
    ADVANCED,
    /** A property is intended to be edited by developers **/
    DEVELOPER;
}
