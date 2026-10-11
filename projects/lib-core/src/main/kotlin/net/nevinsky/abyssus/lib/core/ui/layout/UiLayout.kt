/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.lib.core.ui.layout

import net.nevinsky.abyssus.lib.core.ui.model.*
import kotlin.math.max
import kotlin.math.min

/** A rectangle in logical units with a top-left origin. */
data class UiRect(val x: Float, val y: Float, val width: Float, val height: Float) {
    val right: Float get() = x + width
    val bottom: Float get() = y + height
    val isEmpty: Boolean get() = width <= 0f || height <= 0f

    fun contains(px: Float, py: Float): Boolean = px >= x && py >= y && px < right && py < bottom

    fun intersect(other: UiRect): UiRect {
        val left = max(x, other.x)
        val top = max(y, other.y)
        return UiRect(left, top, max(0f, min(right, other.right) - left), max(0f, min(bottom, other.bottom) - top))
    }
}

data class UiSizeF(val width: Float, val height: Float)

/** Supplies the preferred size of a leaf widget (text metrics, image size); the layout adds nothing of its own. */
fun interface UiMeasurer {
    fun preferredSize(widget: UiWidget): UiSizeF
}

/**
 * Uniform fit of a reference rectangle into a viewport: [scale] maps logical to viewport units and the fitted
 * rectangle starts at ([offsetX], [offsetY]), centered, so the unused side is letterboxed.
 */
data class UiFit(val scale: Float, val offsetX: Float, val offsetY: Float, val referenceWidth: Float, val referenceHeight: Float) {
    fun toViewportX(x: Float) = offsetX + x * scale
    fun toViewportY(y: Float) = offsetY + y * scale
    fun toLogicalX(x: Float) = (x - offsetX) / scale
    fun toLogicalY(y: Float) = (y - offsetY) / scale
}

enum class UiLayoutIssue { INVALID_VIEWPORT, INVALID_MEASURE, OVERFLOW }

data class UiLayoutDiagnostic(val code: UiLayoutIssue, val widgetId: String?)

/** [bounds] is the widget's own rectangle; [visible] is [bounds] clipped to every ancestor, and is what hit testing uses. */
data class UiLayoutNode(val id: String, val bounds: UiRect, val visible: UiRect)

/** Resolved rectangles in reference space; [order] lists widget ids parent first, in paint order. */
data class UiLayoutResult(
    val fit: UiFit?,
    val nodes: Map<String, UiLayoutNode>,
    val order: List<String>,
    val diagnostics: List<UiLayoutDiagnostic>,
) {
    /** The topmost widget whose visible rectangle contains the reference-space point, or null. */
    fun hit(x: Float, y: Float): String? = order.lastOrNull { nodes.getValue(it).visible.contains(x, y) }
}

/** Fits [referenceWidth] x [referenceHeight] into the viewport; null when any size is not positive and finite. */
fun fitReference(referenceWidth: Float, referenceHeight: Float, viewportWidth: Float, viewportHeight: Float): UiFit? {
    if (!listOf(referenceWidth, referenceHeight, viewportWidth, viewportHeight).all { it.isFinite() && it > 0f }) return null
    val scale = min(viewportWidth / referenceWidth, viewportHeight / referenceHeight)
    return UiFit(scale, (viewportWidth - referenceWidth * scale) / 2f, (viewportHeight - referenceHeight * scale) / 2f, referenceWidth, referenceHeight)
}

/**
 * Pure resolution of a screen's widget tree into rectangles: anchors inside panels, measured sizes plus fixed sizes,
 * padding, spacing and grow inside rows and columns, clipping to ancestors. Authored sizes are never changed; space that
 * is too small clips and is reported. No GL, no IO.
 */
class UiLayoutEngine(private val measurer: UiMeasurer) {

    fun resolve(screen: UiScreen, viewportWidth: Float, viewportHeight: Float): UiLayoutResult {
        val fit = fitReference(screen.referenceWidth, screen.referenceHeight, viewportWidth, viewportHeight)
            ?: return UiLayoutResult(null, emptyMap(), emptyList(), listOf(UiLayoutDiagnostic(UiLayoutIssue.INVALID_VIEWPORT, null)))
        return Run(measurer).resolve(screen, fit)
    }

    private class Run(private val measurer: UiMeasurer) {
        val nodes = LinkedHashMap<String, UiLayoutNode>()
        val diagnostics = mutableListOf<UiLayoutDiagnostic>()
        private val preferred = HashMap<String, UiSizeF>()

        fun resolve(screen: UiScreen, fit: UiFit): UiLayoutResult {
            val reference = UiRect(0f, 0f, screen.referenceWidth, screen.referenceHeight)
            val root = screen.root
            val size = UiSizeF(root.layout.width ?: reference.width, root.layout.height ?: reference.height)
            place(root, anchored(root.layout, reference, size), reference)
            return UiLayoutResult(fit, nodes, nodes.keys.toList(), diagnostics)
        }

        private fun sane(value: Float, widget: UiWidget): Float =
            if (value.isFinite() && value >= 0f) value else { diagnostics += UiLayoutDiagnostic(UiLayoutIssue.INVALID_MEASURE, widget.id); 0f }

        /** Explicit size, else content size: a container from its children, a leaf from the measurer. */
        private fun preferred(widget: UiWidget): UiSizeF = preferred.getOrPut(widget.id) {
            val layout = widget.layout
            val content = when (widget.type) {
                UiWidgetType.ROW, UiWidgetType.COLUMN, UiWidgetType.PANEL -> containerContent(widget)
                else -> measurer.preferredSize(widget).let { UiSizeF(sane(it.width, widget), sane(it.height, widget)) }
            }
            UiSizeF(layout.width ?: content.width, layout.height ?: content.height)
        }

        private fun containerContent(widget: UiWidget): UiSizeF {
            val children = widget.children.map(::preferred)
            val pad = widget.layout.padding * 2
            val gaps = widget.layout.spacing * max(0, children.size - 1)
            return when (widget.type) {
                UiWidgetType.ROW -> UiSizeF(children.sumOf { it.width.toDouble() }.toFloat() + gaps + pad, (children.maxOfOrNull { it.height } ?: 0f) + pad)
                UiWidgetType.COLUMN -> UiSizeF((children.maxOfOrNull { it.width } ?: 0f) + pad, children.sumOf { it.height.toDouble() }.toFloat() + gaps + pad)
                else -> UiSizeF((children.maxOfOrNull { it.width } ?: 0f) + pad, (children.maxOfOrNull { it.height } ?: 0f) + pad)
            }
        }

        private fun fraction(anchor: UiAnchor): Pair<Float, Float> = when (anchor) {
            UiAnchor.TOP_LEFT -> 0f to 0f
            UiAnchor.TOP -> .5f to 0f
            UiAnchor.TOP_RIGHT -> 1f to 0f
            UiAnchor.LEFT -> 0f to .5f
            UiAnchor.CENTER -> .5f to .5f
            UiAnchor.RIGHT -> 1f to .5f
            UiAnchor.BOTTOM_LEFT -> 0f to 1f
            UiAnchor.BOTTOM -> .5f to 1f
            UiAnchor.BOTTOM_RIGHT -> 1f to 1f
        }

        /** The rectangle of a [size] box whose pivot sits at the anchor point of [area], moved by the layout offsets. */
        private fun anchored(layout: UiLayout, area: UiRect, size: UiSizeF): UiRect {
            val anchor = layout.anchor ?: UiAnchor.TOP_LEFT
            val (ax, ay) = fraction(anchor)
            val (px, py) = fraction(layout.pivot ?: anchor)
            return UiRect(
                area.x + ax * area.width + layout.x - px * size.width,
                area.y + ay * area.height + layout.y - py * size.height,
                size.width, size.height,
            )
        }

        private fun place(widget: UiWidget, bounds: UiRect, parentVisible: UiRect) {
            val visible = bounds.intersect(parentVisible)
            nodes[widget.id] = UiLayoutNode(widget.id, bounds, visible)
            if (widget.children.isEmpty()) return
            val pad = widget.layout.padding
            val inner = UiRect(bounds.x + pad, bounds.y + pad, max(0f, bounds.width - 2 * pad), max(0f, bounds.height - 2 * pad))
            when (widget.type) {
                UiWidgetType.ROW -> flow(widget, inner, visible, horizontal = true)
                UiWidgetType.COLUMN -> flow(widget, inner, visible, horizontal = false)
                else -> widget.children.forEach { child -> place(child, anchored(child.layout, inner, preferred(child)), visible) }
            }
        }

        private fun flow(widget: UiWidget, inner: UiRect, visible: UiRect, horizontal: Boolean) {
            val children = widget.children
            val sizes = children.map(::preferred)
            val main = sizes.map { if (horizontal) it.width else it.height }.toMutableList()
            val available = if (horizontal) inner.width else inner.height
            val crossAvailable = if (horizontal) inner.height else inner.width
            val spacing = widget.layout.spacing
            val remaining = available - main.sum() - spacing * (children.size - 1)
            if (remaining < -EPSILON) diagnostics += UiLayoutDiagnostic(UiLayoutIssue.OVERFLOW, widget.id)
            val growTotal = children.sumOf { it.layout.grow.toDouble() }.toFloat()
            if (remaining > 0f && growTotal > 0f)
                children.forEachIndexed { i, child -> main[i] += remaining * child.layout.grow / growTotal }
            var cursor = if (horizontal) inner.x else inner.y
            children.forEachIndexed { i, child ->
                val explicitCross = if (horizontal) child.layout.height else child.layout.width
                val cross = explicitCross ?: if (widget.layout.align == UiAlign.STRETCH) crossAvailable else if (horizontal) sizes[i].height else sizes[i].width
                val crossStart = (if (horizontal) inner.y else inner.x) + when (widget.layout.align) {
                    UiAlign.CENTER -> (crossAvailable - cross) / 2f
                    UiAlign.END -> crossAvailable - cross
                    else -> 0f
                }
                val rect = if (horizontal) UiRect(cursor, crossStart, main[i], cross) else UiRect(crossStart, cursor, cross, main[i])
                place(child, rect, visible)
                cursor += main[i] + spacing
            }
        }
    }
}

private const val EPSILON = 0.001f
