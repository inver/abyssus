/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.plugin.sceneview

/** The corner of the viewport an overlay sits in. */
enum class Corner { UPPER_RIGHT, LOWER_LEFT }

/** A rectangle in pixels with the origin at the top left of the view and y growing downward. */
data class PixelRect(val x: Int, val y: Int, val width: Int, val height: Int) {
    fun contains(px: Int, py: Int): Boolean = px >= x && px < x + width && py >= y && py < y + height
}

/**
 * Where an overlay of [width] x [height] sits in a view of [viewWidth] x [viewHeight], [margin] away from [corner]; all in
 * the same unit (logical or framebuffer pixels). No GL needed.
 */
fun cornerRect(corner: Corner, viewWidth: Int, viewHeight: Int, width: Int, height: Int, margin: Int): PixelRect = when (corner) {
    Corner.UPPER_RIGHT -> PixelRect(viewWidth - margin - width, margin, width, height)
    Corner.LOWER_LEFT -> PixelRect(margin, viewHeight - margin - height, width, height)
}

/** The logical gap between an overlay and the edge of the view. */
const val CORNER_MARGIN = 12
