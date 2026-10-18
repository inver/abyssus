/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.plugin.gameui

import com.fasterxml.jackson.databind.JsonNode
import net.nevinsky.abyssus.lib.core.editor.ui.UiDocumentEdits
import net.nevinsky.abyssus.lib.core.editor.ui.UiEditResult
import net.nevinsky.abyssus.lib.core.editor.ui.UiGesturePreview
import net.nevinsky.abyssus.lib.core.editor.ui.UiLayoutGesture
import net.nevinsky.abyssus.lib.core.editor.ui.UiTree
import net.nevinsky.abyssus.lib.core.ui.layout.UiLayoutResult
import net.nevinsky.abyssus.lib.core.ui.model.UiCollection

/**
 * The press-drag-release of the edit canvas, apart from Swing and GL: a press selects a widget and starts a gesture, a
 * drag only previews, and the release writes one command for the whole drag. The document, the selection and the write
 * belong to the editor model, which supplies them through the constructor.
 */
internal class UiCanvasGestures(
    private val collection: () -> UiCollection?,
    private val text: () -> String,
    private val layoutOf: (screenId: String) -> UiLayoutResult?,
    private val select: (widgetId: String) -> Unit,
    /** Writes the drag as one command against [baseText]; true when it was written. */
    private val commit: (baseText: String, edit: (UiDocumentEdits, JsonNode) -> UiEditResult) -> Boolean,
) {
    private var gesture: UiLayoutGesture? = null

    val active: Boolean get() = gesture != null

    /** A press on [widgetId] in the edit canvas: selects it and starts a drag at viewport point ([x], [y]). */
    fun begin(widgetId: String, x: Float, y: Float) {
        val c = collection() ?: return
        val screen = UiTree(c).screenOf(widgetId) ?: return
        select(widgetId)
        val layout = layoutOf(screen.id) ?: return
        gesture = UiLayoutGesture(c, layout, widgetId, x, y, text())
    }

    /** Transient preview only; nothing is written. */
    fun update(x: Float, y: Float): UiGesturePreview = gesture?.update(x, y) ?: UiGesturePreview.None

    /** The release: one command for the whole drag, refused when the document changed since the press. */
    fun end(x: Float, y: Float): Boolean {
        val g = gesture ?: return false
        gesture = null
        g.update(x, y)
        val done = g.commit() ?: return false
        return commit(done.baseText, done.edit)
    }

    fun cancel() {
        gesture = null
    }
}
