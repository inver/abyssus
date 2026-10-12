/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.lib.core.editor.ui

import com.fasterxml.jackson.databind.JsonNode
import net.nevinsky.abyssus.lib.core.ui.layout.UiLayoutResult
import net.nevinsky.abyssus.lib.core.ui.layout.UiRect
import net.nevinsky.abyssus.lib.core.ui.model.UiCollection
import net.nevinsky.abyssus.lib.core.ui.model.UiScreen
import net.nevinsky.abyssus.lib.core.ui.model.UiWidget
import net.nevinsky.abyssus.lib.core.ui.model.UiWidgetType
import kotlin.math.abs
import kotlin.math.roundToInt

/** The selected widget of an editor. A null [widgetId] means nothing is selected. */
data class UiSelection(val screenId: String? = null, val widgetId: String? = null) {
    /** The selection after the document changed: kept when the widget still exists, else its screen's root, else nothing. */
    fun reconcile(collection: UiCollection): UiSelection {
        val tree = UiTree(collection)
        if (widgetId != null && tree.widget(widgetId) != null) return UiSelection(tree.screenOf(widgetId)?.id, widgetId)
        val screen = screenId?.let(collection::screen) ?: collection.screens.firstOrNull() ?: return UiSelection()
        return UiSelection(screen.id, screen.root.id)
    }
}

/** Parent and screen lookup over a decoded collection; widget ids are unique across it. */
class UiTree(private val collection: UiCollection) {
    private val widgets = HashMap<String, UiWidget>()
    private val parents = HashMap<String, UiWidget>()
    private val screens = HashMap<String, UiScreen>()

    init {
        for (screen in collection.screens) {
            val stack = ArrayDeque<UiWidget>()
            stack.addLast(screen.root)
            while (stack.isNotEmpty()) {
                val w = stack.removeLast()
                widgets[w.id] = w
                screens[w.id] = screen
                w.children.forEach { parents[it.id] = w; stack.addLast(it) }
                // a list's template and empty state belong to the list
                listOfNotNull(w.template, w.empty).forEach { parents[it.id] = w; stack.addLast(it) }
            }
        }
    }

    fun widget(id: String): UiWidget? = widgets[id]
    fun parentOf(id: String): UiWidget? = parents[id]
    fun screenOf(id: String): UiScreen? = screens[id]
    fun isContainer(w: UiWidget) = w.type?.container == true
    fun isInside(candidateId: String, ancestorId: String): Boolean {
        var at: String? = candidateId
        while (at != null) { if (at == ancestorId) return true; at = parents[at]?.id }
        return false
    }
}

/** Picks widgets from viewport points through the shared layout, so clipping and letterboxing match what is drawn. */
class UiHitTest {
    /** The topmost visible widget under the viewport point ([x], [y] in layout viewport units); null outside the fitted screen. */
    fun pick(layout: UiLayoutResult, x: Float, y: Float): String? {
        val fit = layout.fit ?: return null
        return layout.hit(fit.toLogicalX(x), fit.toLogicalY(y))
    }

    /** The deepest container under the point, ignoring [exclude] and everything inside it. */
    fun containerAt(layout: UiLayoutResult, tree: UiTree, x: Float, y: Float, exclude: String? = null): String? {
        val fit = layout.fit ?: return null
        val lx = fit.toLogicalX(x); val ly = fit.toLogicalY(y)
        return layout.order.lastOrNull { id ->
            layout.nodes.getValue(id).visible.contains(lx, ly) && tree.widget(id)?.let(tree::isContainer) == true &&
                (exclude == null || !tree.isInside(id, exclude))
        }
    }
}

/** What a gesture would do if released now. */
sealed interface UiGesturePreview {
    /** Free placement in a panel: the widget's offsets become [x], [y]. */
    data class Move(val x: Float, val y: Float, val bounds: UiRect) : UiGesturePreview

    /** Drop position among the siblings of a row or column: the widget ends at [index] of [parentId]. */
    data class Reorder(val parentId: String, val index: Int) : UiGesturePreview

    /** Dropped into another container. */
    data class Reparent(val parentId: String, val index: Int?) : UiGesturePreview

    data object None : UiGesturePreview
}

/** The one edit a finished gesture makes, with the text it was started from, for stale detection. */
class UiGestureCommit(val baseText: String, val preview: UiGesturePreview, val widgetId: String, val edit: (UiDocumentEdits, JsonNode) -> UiEditResult)

/**
 * A drag of one widget in the canvas. Pointer positions are in layout viewport units; `update` only computes a
 * transient preview (nothing is written), and `commit` returns the single edit for the whole gesture, so one drag is
 * one undoable command. A gesture that never moved past [DRAG_THRESHOLD] viewport units is a click and commits nothing.
 * Panel children move by offsets; row and column children reorder (their numeric offsets are not available there);
 * releasing over another container reparents, converting the widget to that container's layout policy.
 */
class UiLayoutGesture(
    collection: UiCollection,
    private val layout: UiLayoutResult,
    private val widgetId: String,
    private val startX: Float,
    private val startY: Float,
    private val baseText: String,
    private val hits: UiHitTest = UiHitTest(),
) {
    private val tree = UiTree(collection)
    private val widget = tree.widget(widgetId)
    private val parent = tree.parentOf(widgetId)
    private var latest: UiGesturePreview = UiGesturePreview.None
    private var moved = false

    /** The preview for the pointer at ([x], [y]). Never touches a document. */
    fun update(x: Float, y: Float): UiGesturePreview {
        val fit = layout.fit
        val w = widget
        val p = parent
        if (fit == null || w == null || p == null) return UiGesturePreview.None.also { latest = it }
        if (abs(x - startX) >= DRAG_THRESHOLD || abs(y - startY) >= DRAG_THRESHOLD) moved = true
        latest = if (!moved) UiGesturePreview.None else compute(fit.scale, x, y, w, p)
        return latest
    }

    private fun compute(scale: Float, x: Float, y: Float, w: UiWidget, p: UiWidget): UiGesturePreview {
        val fit = layout.fit!!
        val target = hits.containerAt(layout, tree, x, y, exclude = w.id)?.let(tree::widget)
        val bounds = layout.nodes[w.id]?.bounds ?: return UiGesturePreview.None
        val dx = (x - startX) / scale
        val dy = (y - startY) / scale
        if (target != null && target.id != p.id) {
            val index = if (isFlow(target)) insertionIndex(target, fit.toLogicalX(x), fit.toLogicalY(y), w.id) else null
            return UiGesturePreview.Reparent(target.id, index)
        }
        return if (isFlow(p)) UiGesturePreview.Reorder(p.id, insertionIndex(p, fit.toLogicalX(x), fit.toLogicalY(y), w.id))
        else UiGesturePreview.Move(w.layout.x + dx, w.layout.y + dy, UiRect(bounds.x + dx, bounds.y + dy, bounds.width, bounds.height))
    }

    private fun isFlow(c: UiWidget) = c.type == UiWidgetType.ROW || c.type == UiWidgetType.COLUMN

    /** Index among [container]'s other children where the pointer (logical [lx], [ly]) falls along its main axis. */
    private fun insertionIndex(container: UiWidget, lx: Float, ly: Float, moving: String): Int {
        val horizontal = container.type == UiWidgetType.ROW
        val others = container.children.filter { it.id != moving }
        val at = others.count { child ->
            val b = layout.nodes[child.id]?.bounds ?: return@count false
            (if (horizontal) lx else ly) > (if (horizontal) b.x + b.width / 2 else b.y + b.height / 2)
        }
        return at
    }

    /** The finished gesture's single edit, or null when it was a click or could not be previewed. */
    fun commit(): UiGestureCommit? {
        val w = widget ?: return null
        return when (val result = latest) {
            UiGesturePreview.None -> null
            is UiGesturePreview.Move -> UiGestureCommit(baseText, result, w.id) { e, m ->
                combine(e.setLayout(m, w.id, "x", result.x.roundToInt().toDouble()), e.setLayout(m, w.id, "y", result.y.roundToInt().toDouble()))
            }
            is UiGesturePreview.Reorder -> UiGestureCommit(baseText, result, w.id) { e, m -> e.reorderWidget(m, w.id, result.index) }
            is UiGesturePreview.Reparent -> UiGestureCommit(baseText, result, w.id) { e, m -> e.reparentWidget(m, w.id, result.parentId, result.index) }
        }
    }

    private fun combine(vararg results: UiEditResult): UiEditResult =
        results.firstOrNull { it is UiEditResult.Rejected } ?: if (results.any { it != UiEditResult.Unchanged }) UiEditResult.Changed else UiEditResult.Unchanged
}

/** Viewport units a pointer must travel before a press becomes a drag. */
const val DRAG_THRESHOLD = 3f
