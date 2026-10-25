/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.editor.arrange

import net.nevinsky.abyssus.lib.core.ecs.component.PathArrangementComponent
import net.nevinsky.abyssus.lib.core.ecs.component.PathPoint
import com.badlogic.gdx.math.Vector3

/**
 * A copy of [base] (default settings without one) along [points]; [closed] overrides its flag when given. The view
 * uses it to lay out the copies of a line that is still being drawn or dragged.
 */
fun arrangementAlong(points: List<Vector3>, base: PathArrangementComponent? = null, closed: Boolean? = null): PathArrangementComponent =
    PathArrangementComponent().also {
        if (base != null) {
            it.closed = base.closed
            it.asset = base.asset.copy()
            it.spacing = base.spacing
            it.offset = base.offset
            it.sides = base.sides
            it.alignToPath = base.alignToPath
            it.yawOffset = base.yawOffset
        }
        if (closed != null) it.closed = closed
        it.points = points.map { p -> PathPoint(p.x, p.y, p.z) }.toMutableList()
    }

/** The points of [arrangement] as editor vectors. */
fun pointsOf(arrangement: PathArrangementComponent): List<Vector3> = arrangement.points.map { Vector3(it.x, it.y, it.z) }
