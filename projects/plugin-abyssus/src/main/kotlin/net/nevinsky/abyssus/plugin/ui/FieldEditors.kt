/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.plugin.ui

import com.intellij.openapi.ui.ComboBox
import com.intellij.ui.CollectionComboBoxModel
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTextField
import java.awt.event.FocusAdapter
import java.awt.event.FocusEvent
import javax.swing.ListCellRenderer

/** What saving one edited field came to; each panel maps its own result type to one of these. */
sealed interface CommitOutcome {
    /** The new value was written. */
    data object Saved : CommitOutcome

    /** Nothing was written (the value is the current one). */
    data object Unchanged : CommitOutcome

    /** The value was refused with [reason], and nothing was written. */
    data class Refused(val reason: String) : CommitOutcome
}

/**
 * Shows what [outcome] says beside the field in [error]: a saved or unchanged value clears the reason, a refused one
 * shows it. [revert] puts the field back to the stored value for everything but a saved one.
 */
internal fun settle(outcome: CommitOutcome, error: JBLabel, revert: () -> Unit) {
    when (outcome) {
        CommitOutcome.Saved -> error.text = ""
        CommitOutcome.Unchanged -> {
            error.text = ""; revert()
        }

        is CommitOutcome.Refused -> {
            error.text = outcome.reason; revert()
        }
    }
}

/**
 * A text field showing [current] that commits on Enter and when focus leaves, never for text equal to the value it
 * last showed or saved; a value that is not saved goes back to that value. EDT only, like every Swing component.
 */
internal fun textEditor(current: String, error: JBLabel, commit: (String) -> CommitOutcome): JBTextField {
    val field = JBTextField(current)
    // what the file holds as far as this editor knows: a saved value is the new baseline, so the focus loss that
    // follows Enter does not commit it again (a panel that rebuilds on save leaves this editor with its old value)
    var stored = current
    val save = {
        if (field.text != stored) {
            val outcome = commit(field.text)
            settle(outcome, error) { field.text = stored }
            if (outcome == CommitOutcome.Saved) {
                stored = field.text
            }
        }
    }
    field.addActionListener { save() }
    field.addFocusListener(object : FocusAdapter() {
        override fun focusLost(e: FocusEvent) = save()
    })
    return field
}

/**
 * A combo box over [choices] showing [selected] that commits when another choice is picked; a value that is not saved
 * goes back to [selected]. [renderer] draws the choices when the default text is not right.
 */
internal fun <T : Any> choiceEditor(
    choices: List<T>,
    selected: T?,
    error: JBLabel,
    renderer: ListCellRenderer<in T>? = null,
    commit: (T) -> CommitOutcome,
): ComboBox<T> {
    val combo = ComboBox(CollectionComboBoxModel(choices))
    renderer?.let { combo.renderer = it }
    combo.selectedItem = selected
    combo.addActionListener {
        @Suppress("UNCHECKED_CAST")
        val chosen = combo.selectedItem as? T ?: return@addActionListener
        if (chosen != selected) settle(commit(chosen), error) { combo.selectedItem = selected }
    }
    return combo
}

/** A check box showing [selected] that commits when toggled; a value that is not saved is switched back. */
internal fun checkEditor(selected: Boolean, error: JBLabel, commit: (Boolean) -> CommitOutcome): JBCheckBox {
    val box = JBCheckBox("", selected)
    box.addActionListener {
        if (box.isSelected != selected) settle(commit(box.isSelected), error) { box.isSelected = selected }
    }
    return box
}
