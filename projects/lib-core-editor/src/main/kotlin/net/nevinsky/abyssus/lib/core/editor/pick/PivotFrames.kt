/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.editor.pick

import com.badlogic.gdx.math.Matrix4
import com.badlogic.gdx.math.Quaternion
import com.badlogic.gdx.math.Vector3
import net.nevinsky.abyssus.lib.core.assets.model.PartPivot
import net.nevinsky.abyssus.lib.core.assets.model.PartQuat
import net.nevinsky.abyssus.lib.core.assets.model.PartVec3
import net.nevinsky.abyssus.lib.core.editor.modelmesh.pivotMatrix

/**
 * Conversions between a pivot as the model asset stores it (relative to the node's frame in the model file) and the
 * frame in world space that the gizmo drags.
 */
class PivotFrames {
    /** The world frame of a pivot: `entityWorld x fileFrame x pivot`. */
    fun worldFrame(entityWorld: Matrix4, fileFrame: Matrix4, pivot: PartPivot?): Matrix4 {
        val frame = Matrix4(entityWorld).mul(fileFrame)
        return if (pivot == null) frame else frame.mul(pivotMatrix(pivot))
    }

    /** The pivot that puts the node's frame at [worldFrame]: `(entityWorld x fileFrame)^-1 x worldFrame`. */
    fun toFilePivot(entityWorld: Matrix4, fileFrame: Matrix4, worldFrame: Matrix4): PartPivot {
        val local = Matrix4(entityWorld).mul(fileFrame).inv().mul(worldFrame)
        val position = local.getTranslation(Vector3())
        val rotation = local.getRotation(Quaternion(), true).nor()
        return PartPivot(PartVec3(position.x, position.y, position.z), PartQuat(rotation.x, rotation.y, rotation.z, rotation.w))
    }
}

/** How a click on a part sets the pivot frame. */
enum class PivotSnap { NONE, EDGE, FACE }

/** Frames from the geometry under a click. Pure math on world-space frames. */
class PivotSnaps {
    /**
     * Position on the nearest edge of the hit triangle, closest to the hit point; X along that edge, pointing the way
     * closest to the current X; Y and Z turned as little as possible from [current].
     */
    fun edge(hit: PartHit, current: Matrix4): Matrix4 {
        val corners = listOf(hit.a, hit.b, hit.c)
        var bestPoint = Vector3()
        var bestDirection = Vector3()
        var bestDistance = Float.MAX_VALUE
        for (i in 0 until 3) {
            val from = corners[i]
            val to = corners[(i + 1) % 3]
            val edge = Vector3(to).sub(from)
            val length2 = edge.len2()
            if (length2 < 1e-20f) continue
            val t = (Vector3(hit.point).sub(from).dot(edge) / length2).coerceIn(0f, 1f)
            val point = Vector3(from).mulAdd(edge, t)
            val distance = point.dst2(hit.point)
            if (distance < bestDistance) {
                bestDistance = distance
                bestPoint = point
                bestDirection = edge.nor()
            }
        }
        if (bestDistance == Float.MAX_VALUE) return Matrix4(current)
        val currentX = axisX(current)
        val x = if (currentX.dot(bestDirection) < 0f) bestDirection.scl(-1f) else bestDirection
        val turn = Quaternion().setFromCross(currentX, x)
        val rotation = turn.mul(current.getRotation(Quaternion(), true))
        return Matrix4().set(bestPoint, rotation.nor())
    }

    /** Position at the hit and Y along the face normal; X is the current X projected onto the face. */
    fun face(hit: PartHit, current: Matrix4): Matrix4 {
        val y = hit.normal()
        if (y.isZero) return Matrix4(current)
        var x = axisX(current)
        x.mulAdd(y, -x.dot(y))
        if (x.len2() < 1e-10f) {
            x = Vector3(hit.b).sub(hit.a)
            x.mulAdd(y, -x.dot(y))
        }
        if (x.len2() < 1e-10f) return Matrix4(current)
        x.nor()
        val z = Vector3(x).crs(y).nor()
        // libGDX reads these nine numbers (and Matrix4.set(x, y, z, pos)) as matrix rows, so pass the axes as columns.
        val rotation = Quaternion().setFromAxes(x.x, y.x, z.x, x.y, y.y, z.y, x.z, y.z, z.z).nor()
        return Matrix4().set(hit.point, rotation)
    }

    private fun axisX(frame: Matrix4): Vector3 = Vector3(1f, 0f, 0f).mul(frame.getRotation(Quaternion(), true)).nor()
}
