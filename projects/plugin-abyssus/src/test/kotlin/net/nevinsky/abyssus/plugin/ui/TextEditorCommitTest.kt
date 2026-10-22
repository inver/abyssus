/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.plugin.ui

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.ui.components.JBLabel
import java.awt.event.FocusEvent

/**
 * A text editor commits a value once. A panel that rebuilds after a save leaves the old editor with its old baseline,
 * and the focus loss that follows Enter used to commit the same text again against the stale stored value, which an
 * asset reported as "changed elsewhere" (FLD-03; found by `AssetPropertyLifecycleUiTest`).
 */
class TextEditorCommitTest : BasePlatformTestCase() {
    private fun focusLost(field: javax.swing.JComponent) =
        field.focusListeners.forEach { it.focusLost(FocusEvent(field, FocusEvent.FOCUS_LOST)) }

    fun testFocusLossAfterEnterDoesNotCommitAgain() {
        val commits = ArrayList<String>()
        val field = textEditor("1600", JBLabel()) { commits += it; CommitOutcome.Saved }
        field.text = "800"
        field.postActionEvent()
        focusLost(field)
        assertEquals("the value was committed once", listOf("800"), commits)
    }

    fun testARefusedValueGoesBackToTheLastSavedValue() {
        val commits = ArrayList<String>()
        val error = JBLabel()
        val field = textEditor("1600", error) { commits += it; if (it == "abc") CommitOutcome.Refused("no") else CommitOutcome.Saved }
        field.text = "800"
        field.postActionEvent()
        field.text = "abc"
        field.postActionEvent()
        assertEquals("the refusal restores what was saved, not the first value", "800", field.text)
        assertEquals("no", error.text)
        field.text = "900"
        field.postActionEvent()
        assertEquals(listOf("800", "abc", "900"), commits)
        assertEquals("a saved value clears the reason", "", error.text)
    }

    fun testTheSameTextIsNotCommittedWhenFocusLeavesAnUntouchedField() {
        val commits = ArrayList<String>()
        val field = textEditor("1600", JBLabel()) { commits += it; CommitOutcome.Saved }
        focusLost(field)
        assertEquals(emptyList<String>(), commits)
    }
}
