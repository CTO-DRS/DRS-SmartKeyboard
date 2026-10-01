/*
 * Copyright (C) 2025 The DRS Smart Keyboard Project
 */

package com.drs.smartkeyboard.ime.text.gestures

import com.drs.smartkeyboard.ime.core.Subtype
import com.drs.smartkeyboard.ime.text.keyboard.TextKey

/**
 * Inherit this to be able to handle gesture typing. Takes in raw pointer data, and
 * spits out what it thinks the gesture is.
 */
interface GlideTypingClassifier {
    /**
     * Called to notify gesture classifier that it can add a new point to the gesture.
     *
     * @param position The position to add
     */
    fun addGesturePoint(position: GlideTypingGesture.Detector.Position)

    /**
     * Change the layout of the gesture classifier.
     */
    fun setLayout(keyViews: List<TextKey>, subtype: Subtype)

    // DRS v1.20.0: `setWordData` was removed from this interface — it was only ever
    // called from the classifier's own setLayout, and word-data loading now happens
    // on a background scope inside the classifier (the ready gate covers it).

    /**
     * Process a completed gesture and find its location.
     */
    fun initGestureFromPointerData(pointerData: GlideTypingGesture.Detector.PointerData)

    /**
     * Generate suggestions to show to the user.
     *
     * @param maxSuggestionCount The maximum number of suggestions that are accepted.
     * @param gestureCompleted Whether the gesture is finished. (e.g to use a different algorithm for in progress words)
     */
    fun getSuggestions(maxSuggestionCount: Int, gestureCompleted: Boolean): List<CharSequence>

    fun clear()
}
