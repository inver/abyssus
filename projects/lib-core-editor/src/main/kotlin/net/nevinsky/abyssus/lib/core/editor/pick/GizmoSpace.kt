/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.editor.pick

import com.badlogic.gdx.math.Quaternion
import com.badlogic.gdx.math.Vector3
import net.nevinsky.abyssus.lib.core.editor.content.Quat

/** The axes a gizmo follows: the world's, or the selected object's own. */
enum class GizmoSpace { WORLD, LOCAL }

/** The three unit axes of a gizmo, by [GizmoAxis]. */
typealias GizmoAxes = Map<GizmoAxis, Vector3>

/** The world axes. */
val WORLD_AXES: GizmoAxes = GizmoAxis.entries.associateWith { it.direction }

/**
 * The axes a gizmo uses in [space] for an object turned by [rotation]: the world axes, or those axes turned with the
 * object. An object with no rotation has the same axes in both. No GL needed.
 */
fun gizmoAxes(space: GizmoSpace, rotation: Quat): GizmoAxes {
    if (space == GizmoSpace.WORLD) return WORLD_AXES
    val q = Quaternion(rotation.x, rotation.y, rotation.z, rotation.w).nor()
    return GizmoAxis.entries.associateWith { axis ->
        val v = q.transform(Vector3(axis.direction.x, axis.direction.y, axis.direction.z)).nor()
        Vector3(v.x, v.y, v.z)
    }
}

/**
 * Snapping of a gizmo drag: a move drag rounds the dragged object's coordinate along the axis to a multiple of
 * [moveStep], a rotate drag rounds the angle turned to a multiple of [rotateDegrees].
 */
data class GizmoSnap(val moveStep: Float, val rotateDegrees: Float) {
    init {
        require(moveStep > 0f && rotateDegrees > 0f) { "snap steps must be positive" }
    }

    /** [value] rounded to the nearest multiple of [moveStep] (never negative zero). */
    fun move(value: Float): Float = roundTo(value, moveStep)

    /** [degrees] rounded to the nearest multiple of [rotateDegrees] (never negative zero). */
    fun rotate(degrees: Float): Float = roundTo(degrees, rotateDegrees)

    private fun roundTo(value: Float, step: Float): Float {
        val rounded = Math.round(value / step) * step
        return if (rounded == 0f) 0f else rounded
    }
}
