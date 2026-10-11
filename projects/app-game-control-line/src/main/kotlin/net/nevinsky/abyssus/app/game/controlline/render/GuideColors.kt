/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.app.game.controlline.render

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.math.Vector3
import net.nevinsky.abyssus.app.game.controlline.pattern.GuideSegment
import net.nevinsky.abyssus.app.game.controlline.pattern.GuideTag

private val AHEAD_COLOR = Color(0.2f, 0.95f, 1f, 1f)
private val DONE_COLOR = Color(0.2f, 0.95f, 1f, 0.3f)
private val GATE_COLOR = Color(1f, 0.9f, 0.1f, 1f)
private val RING_BASE_COLOR = Color(1f, 1f, 1f, 0.25f)
private val RING_45_COLOR = Color(1f, 1f, 1f, 0.12f)

/** The colour a guide segment of this tag is drawn in. */
fun GuideTag.color(): Color = when (this) {
    GuideTag.GUIDE_AHEAD -> AHEAD_COLOR
    GuideTag.GUIDE_DONE -> DONE_COLOR
    GuideTag.GATE -> GATE_COLOR
    GuideTag.RING_BASE -> RING_BASE_COLOR
    GuideTag.RING_45 -> RING_45_COLOR
}

/** The width of its line in pixels. */
fun GuideTag.lineWidth(): Float = when (this) {
    GuideTag.GUIDE_AHEAD -> 6f
    GuideTag.GUIDE_DONE -> 4f
    GuideTag.GATE -> 8f
    GuideTag.RING_BASE, GuideTag.RING_45 -> 2f
}

/** The segments of a guide as lines to draw, moved from the pilot's ground point to [ground]. */
fun List<GuideSegment>.toLines(ground: Vector3): List<LineSegment> =
    map { LineSegment(Vector3(ground).add(it.from), Vector3(ground).add(it.to), it.tag.color(), it.tag.lineWidth()) }
