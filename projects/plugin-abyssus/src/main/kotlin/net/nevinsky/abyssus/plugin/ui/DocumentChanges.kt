/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.plugin.ui

import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener

/** A Swing [DocumentListener] that runs [block] after any insert, removal or attribute change. */
fun documentChanges(block: () -> Unit): DocumentListener = object : DocumentListener {
    override fun insertUpdate(e: DocumentEvent) = block()
    override fun removeUpdate(e: DocumentEvent) = block()
    override fun changedUpdate(e: DocumentEvent) = block()
}
