/*
 * Copyright (C) 2022-2025 The DRS Smart Keyboard Project
 */

package com.drs.smartkeyboard.lib.ext

import androidx.core.text.trimmedLength
import com.drs.smartkeyboard.R
import com.drs.smartkeyboard.ime.theme.ThemeExtensionComponent
import com.drs.smartkeyboard.lib.ValidationRule
import org.drs.lib.snygg.SnyggStylesheet
import com.drs.smartkeyboard.lib.validate
import org.drs.lib.snygg.value.SnyggVarValue

object ExtensionValidation {
    /**
     * DRS v1.28.0 audit fix: shared contract for extension/package ids.
     * Kept in sync with [MetaId]'s validator below and now also enforced
     * by ExtensionManager.import() and DrsPackageManager at import time.
     */
    val META_ID_REGEX = """^[a-z][a-z0-9_]*(\.[a-z0-9][a-z0-9_]*)*${'$'}""".toRegex()
    private val MetaIdRegex = META_ID_REGEX
    private val ComponentIdRegex = """^[a-z][a-z0-9_]*${'$'}""".toRegex()
    private val ThemeComponentStylesheetPathRegex = """^[^:*<>"']*$""".toRegex()

    /**
     * DRS p7 (E3-4) — stylesheet-path containment contract: rejects absolute paths
     * (leading '/') and any '..' segment so a crafted/typo'd stylesheetPath can never
     * escape its extension's own directory when the editor saves the stylesheet or the
     * theme loader reads it back. Enforced at three gates:
     *  - [ThemeComponentStylesheetPath] (component-editor validation at save),
     *  - ExtensionManager.import (every ThemeExtension component, programmatic import),
     *  - ThemeManager.load (canonical containment of the resolved file inside the
     *    loaded dir → LoadFailure).
     * An empty path is valid (falls back to the component's default stylesheet path).
     */
    fun isSafeStylesheetPath(path: String): Boolean {
        if (path.isEmpty()) return true
        if (path.startsWith("/")) return false
        return path.split('/').none { it == ".." }
    }

    val MetaId = ValidationRule<String> {
        forKlass = ExtensionMeta::class
        forProperty = "id"
        validator { str ->
            when {
                str.isBlank() -> resultInvalid(error = R.string.ext__validation__enter_package_name)
                MetaIdRegex.matches(str) -> resultValid()
                else -> resultInvalid(error = R.string.ext__validation__error_package_name, "id_regex" to MetaIdRegex)
            }
        }
    }

    val MetaVersion = ValidationRule<String> {
        forKlass = ExtensionMeta::class
        forProperty = "version"
        validator { str ->
            when {
                str.isBlank() -> resultInvalid(error = R.string.ext__validation__enter_version)
                else -> resultValid()
            }
        }
    }

    val MetaTitle = ValidationRule<String> {
        forKlass = ExtensionMeta::class
        forProperty = "title"
        validator { str ->
            when {
                str.isBlank() -> resultInvalid(error = R.string.ext__validation__enter_title)
                else -> resultValid()
            }
        }
    }

    val MetaMaintainers = ValidationRule<String> {
        forKlass = ExtensionMeta::class
        forProperty = "maintainers"
        validator { str ->
            val maintainers = str.lines().filter { it.isNotBlank() }
            when {
                maintainers.isEmpty() -> resultInvalid(error = R.string.ext__validation__enter_maintainer)
                else -> resultValid()
            }
        }
    }

    val MetaLicense = ValidationRule<String> {
        forKlass = ExtensionMeta::class
        forProperty = "license"
        validator { str ->
            when {
                str.isBlank() -> resultInvalid(error = R.string.ext__validation__enter_license)
                else -> resultValid()
            }
        }
    }

    val ComponentId = ValidationRule<String> {
        forKlass = ExtensionComponent::class
        forProperty = "id"
        validator { str ->
            when {
                str.isBlank() -> resultInvalid(error = R.string.ext__validation__enter_component_id)
                !ComponentIdRegex.matches(str) -> resultInvalid(error = R.string.ext__validation__error_component_id, "component_id_regex" to ComponentIdRegex)
                else -> resultValid()
            }
        }
    }

    val ComponentLabel = ValidationRule<String> {
        forKlass = ExtensionComponent::class
        forProperty = "label"
        validator { str ->
            when {
                str.isBlank() -> resultInvalid(error = R.string.ext__validation__enter_component_label)
                str.trimmedLength() > 30 -> resultValid(hint = R.string.ext__validation__hint_component_label_to_long)
                else -> resultValid()
            }
        }
    }

    val ComponentAuthors = ValidationRule<String> {
        forKlass = ExtensionComponent::class
        forProperty = "authors"
        validator { str ->
            val authors = str.lines().filter { it.isNotBlank() }
            when {
                authors.isEmpty() -> resultInvalid(error = R.string.ext__validation__error_author)
                else -> resultValid()
            }
        }
    }

    val ThemeComponentStylesheetPath = ValidationRule<String> {
        forKlass = ThemeExtensionComponent::class
        forProperty = "stylesheetPath"
        validator { str ->
            when {
                str.isEmpty() -> resultValid()
                str.isBlank() -> resultInvalid(error = R.string.ext__validation__error_stylesheet_path_blank)
                !ThemeComponentStylesheetPathRegex.matches(str) -> {
                    resultInvalid(error = R.string.ext__validation__error_stylesheet_path, "stylesheet_path_regex" to ThemeComponentStylesheetPathRegex)
                }
                // DRS p7 (E3-4) — the char-class regex above still accepted ".." and
                // absolute paths; the containment contract closes the traversal hole
                // (existing error string reused — no new validation string needed).
                !isSafeStylesheetPath(str) -> {
                    resultInvalid(error = R.string.ext__validation__error_stylesheet_path, "stylesheet_path_regex" to ThemeComponentStylesheetPathRegex)
                }
                else -> resultValid()
            }
        }
    }

    val ThemeComponentVariableName = ValidationRule<String> {
        forKlass = SnyggStylesheet::class
        forProperty = "propertyName"
        validator { input ->
            val str = input.trim()
            when {
                str.isBlank() -> resultInvalid(error = R.string.ext__validation__enter_property)
                !SnyggVarValue.VariableNameRegex.matches(str) -> {
                    resultInvalid(error = R.string.ext__validation__error_property, "variable_name_regex" to SnyggVarValue.VariableNameRegex)
                }
                else -> resultValid()
            }
        }
    }

    val SnyggStaticColorValue = ValidationRule<String> {
        forKlass = org.drs.lib.snygg.value.SnyggStaticColorValue::class
        forProperty = "color"
        validator { input ->
            val str = input.trim()
            when {
                str.isBlank() -> resultInvalid(error = R.string.ext__validation__enter_color)
                org.drs.lib.snygg.value.SnyggStaticColorValue.deserialize(str).isFailure -> {
                    resultInvalid(error = R.string.ext__validation__error_color)
                }
                else -> resultValid()
            }
        }
    }

    val SnyggDpShapeValue = ValidationRule<String> {
        forKlass = org.drs.lib.snygg.value.SnyggDpShapeValue::class
        forProperty = "corner"
        validator { str ->
            val floatValue = str.toFloatOrNull()
            when {
                str.isBlank() -> resultInvalid(error = R.string.ext__validation__enter_dp_size)
                floatValue == null -> resultInvalid(error = R.string.ext__validation__enter_valid_number)
                floatValue < 0f -> resultInvalid(error = R.string.ext__validation__enter_positive_number)
                else -> resultValid()
            }
        }
    }

    val SnyggPercentShapeValue = ValidationRule<String> {
        forKlass = org.drs.lib.snygg.value.SnyggPercentShapeValue::class
        forProperty = "corner"
        validator { str ->
            val intValue = str.toIntOrNull()
            when {
                str.isBlank() -> resultInvalid(error = R.string.ext__validation__enter_percent_size)
                intValue == null -> resultInvalid(error = R.string.ext__validation__enter_valid_number)
                intValue < 0 || intValue > 100 -> resultInvalid(error = R.string.ext__validation__enter_number_between_0_100)
                intValue > 50 -> resultValid(hint = R.string.ext__validation__hint_value_above_50_percent)
                else -> resultValid()
            }
        }
    }
}

fun ExtensionMeta.validate(): Boolean {
    return with(ExtensionValidation) {
        validate(MetaId, id).isValid() &&
            validate(MetaVersion, version).isValid() &&
            validate(MetaTitle, title).isValid() &&
            validate(MetaMaintainers, maintainers.joinToString("\n")).isValid() &&
            validate(MetaLicense, license).isValid()
    }
}
