/*
 * Copyright (C) 2025 The DRS Smart Keyboard Project
 */

package com.drs.smartkeyboard.lib.compose

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.drs.smartkeyboard.lib.ValidationResult

@Composable
fun Validation(
    showValidationErrors: Boolean,
    validationResult: ValidationResult?,
) {
    if (showValidationErrors) {
        if (validationResult is ValidationResult.Valid && validationResult.hasHintMessage()) {
            Text(
                text = validationResult.hintMessage(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.56f),
            )
        }
        if (validationResult is ValidationResult.Invalid && validationResult.hasErrorMessage()) {
            Text(
                text = validationResult.errorMessage(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}
