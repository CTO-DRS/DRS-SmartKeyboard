/*
 * Copyright (C) 2025 The DRS Smart Keyboard Project
 */

package com.drs.smartkeyboard.ime.theme

import org.drs.lib.snygg.SnyggElementRule
import org.drs.lib.snygg.SnyggSelector
import org.drs.lib.snygg.SnyggSinglePropertySetEditor
import org.drs.lib.snygg.SnyggStylesheet
import org.drs.lib.snygg.value.SnyggUriValue

/**
 * DRS v2.8.0 «خلفيتك من ألبومك» — pure, total stylesheet patcher.
 *
 * Given a freshly-parsed theme stylesheet, injects the user background image as a
 * `background-image` property on the `window` element rule. The existing rule (and
 * its `background` color, `foreground`, etc.) is preserved — the image renders on
 * top of the color and beneath the keys, exactly like the built-in Snygg
 * background-image pipeline already renders it for theme-pack assets.
 *
 * The patcher is TOTAL: it never throws. A null/blank/unsafe file name or any
 * unexpected structural surprise returns the original stylesheet unchanged — a
 * broken preference value can never fail a theme load.
 */
object DrsThemeBackgroundPatcher {

    fun apply(stylesheet: SnyggStylesheet, fileName: String?, dimness: Int): SnyggStylesheet {
        if (fileName == null || !DrsThemeBackground.isSafeStoredName(fileName)) {
            return stylesheet
        }
        return runCatching { inject(stylesheet, fileName) }.getOrDefault(stylesheet)
    }

    private fun inject(stylesheet: SnyggStylesheet, fileName: String): SnyggStylesheet {
        val editor = stylesheet.edit()
        val rule = SnyggElementRule(DrsImeUi.Window.elementName)
        when (val propertySet = editor.rules[rule]) {
            null -> {
                val fresh = SnyggSinglePropertySetEditor()
                fresh.backgroundImage = SnyggUriValue(DrsThemeBackground.uriFor(fileName))
                editor.rules[rule] = fresh
            }
            is SnyggSinglePropertySetEditor -> {
                propertySet.backgroundImage = SnyggUriValue(DrsThemeBackground.uriFor(fileName))
            }
            // Unknown rule shape (e.g. a multiple-sets rule): refuse to clobber the
            // theme author's structure — the user image simply does not apply there.
            else -> return stylesheet
        }
        return editor.build()
    }
}
