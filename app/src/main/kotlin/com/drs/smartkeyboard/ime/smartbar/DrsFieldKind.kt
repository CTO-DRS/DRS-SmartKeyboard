/*
 * Copyright (C) 2026 The DRS Smart Keyboard Project
 */

package com.drs.smartkeyboard.ime.smartbar

import com.drs.smartkeyboard.ime.editor.InputAttributes

/**
 * DRS v2.11.0 «المُرتّب السياقي الصادق» — the honest fine-grained field
 * classification that drives the smartbar's contextual action ranking.
 *
 * WHY a second classifier beside [com.drs.smartkeyboard.drs.DrsContextMode]:
 * the coarse context mode folds away exactly the signals the strip needs —
 * a URI field is reported as SEARCH, an email field as WRITING, a chat box
 * as NORMAL, and a real filter/search bar is never detected at all. The
 * mode keeps its stable stats contract (mode names are persisted by name);
 * THIS classifier is the lossless reading of what the field actually
 * declares, feeding the ranking matrix only.
 *
 * Honesty rules (mirroring the DRS creed):
 *  - Classification reads DECLARED attributes only (inputType variations,
 *    classes, multiline flag, an anonymous package hint). No content is
 *    ever read — the field's text stays unreachable from here.
 *  - No guessing: an unspecified/rich-less field is GENERAL, never a
 *    fabricated special kind.
 *  - Pure and deterministic: the same attributes classify to the same
 *    kind on every device, every day, forever.
 */
enum class DrsFieldKind {
    /** The declared-attributes desert: plain single-line text editing. */
    GENERAL,

    /** A URL field (TYPE_TEXT_VARIATION_URI). */
    URI,

    /** An email address field (EMAIL_ADDRESS / WEB_EMAIL_ADDRESS). */
    EMAIL,

    /** A messaging box (SHORT_MESSAGE / LONG_MESSAGE variation). */
    MESSAGE,

    /** A filter/search query bar (TYPE_TEXT_VARIATION_FILTER). */
    SEARCH_FILTER,

    /** Number, phone or datetime fields. */
    NUMBER,

    /** Password family (PASSWORD / VISIBLE_PASSWORD / WEB_PASSWORD). */
    PASSWORD,

    /** Coding hosts (termux/IDE/editor packages) — the technical claw. */
    CODE,

    /** A plain multiline text field (long-form writing). */
    MULTILINE,
    ;

    companion object {

        /**
         * Classifies a field from its declared [attributes] plus an
         * anonymous [codingPackageHint] (the host package smells like a
         * coding tool — computed by the caller, never logged here).
         *
         * Priority ladder (first match wins, mirroring the coarse mode's
         * proven order so both classifiers never disagree on the kind
         * they share):
         *  1. PASSWORD family — the privacy claw outranks everything.
         *  2. NUMBER/PHONE/DATETIME classes.
         *  3. URI variation.
         *  4. EMAIL variations.
         *  5. MESSAGE variations.
         *  6. FILTER variation (search bars).
         *  7. coding-package hint.
         *  8. multiline flag.
         *  9. GENERAL.
         */
        fun kindOf(attributes: InputAttributes, codingPackageHint: Boolean = false): DrsFieldKind {
            return when {
                attributes.variation == InputAttributes.Variation.PASSWORD ||
                    attributes.variation == InputAttributes.Variation.VISIBLE_PASSWORD ||
                    attributes.variation == InputAttributes.Variation.WEB_PASSWORD ->
                    PASSWORD

                attributes.type == InputAttributes.Type.NUMBER ||
                    attributes.type == InputAttributes.Type.PHONE ||
                    attributes.type == InputAttributes.Type.DATETIME ->
                    NUMBER

                attributes.variation == InputAttributes.Variation.URI ->
                    URI

                attributes.variation == InputAttributes.Variation.EMAIL_ADDRESS ||
                    attributes.variation == InputAttributes.Variation.WEB_EMAIL_ADDRESS ->
                    EMAIL

                attributes.variation == InputAttributes.Variation.SHORT_MESSAGE ||
                    attributes.variation == InputAttributes.Variation.LONG_MESSAGE ->
                    MESSAGE

                attributes.variation == InputAttributes.Variation.FILTER ->
                    SEARCH_FILTER

                codingPackageHint ->
                    CODE

                attributes.flagTextMultiLine ->
                    MULTILINE

                else ->
                    GENERAL
            }
        }
    }
}
