/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.lib.physics

import com.badlogic.gdx.math.Matrix4
import com.badlogic.gdx.math.Quaternion
import com.badlogic.gdx.math.Vector3
import net.nevinsky.abyssus.lib.core.assets.foliage.FoliageCollider
import net.nevinsky.abyssus.lib.core.assets.foliage.FoliageColliderShape
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Where and how big one foliage copy's collider is in the world: the sub-shape [shape] centred at ([x], [y], [z]),
 * turned by the rotation ([qx], [qy], [qz], [qw]), with its sizes already scaled. A BOX uses [halfX], [halfY] and
 * [halfZ] along its own axes, a SPHERE its [radius], a CAPSULE its [radius] and [halfHeight] along its own Y axis.
 * [approximated] is true for a sphere or capsule that took the largest scale axis of a non-uniform copy.
 */
class ColliderPlacement(
    val shape: FoliageColliderShape,
    val x: Float, val y: Float, val z: Float,
    val qx: Float, val qy: Float, val qz: Float, val qw: Float,
    val halfX: Float, val halfY: Float, val halfZ: Float,
    val radius: Float, val halfHeight: Float,
    val approximated: Boolean,
)

/** The outcome of [FoliageColliderTransforms.place]. */
sealed interface ColliderPlacementResult {
    class Placed(val placement: ColliderPlacement) : ColliderPlacementResult

    /** The copy's transform cannot carry a rigid primitive; [reason] says why. */
    class Refused(val reason: String) : ColliderPlacementResult
}

/**
 * Turns a copy's world matrix (see `FoliageMatrices`) and its layer's [FoliageCollider] into a rigid pose and sizes a
 * Jolt primitive can take, before anything native is allocated. The collider's offset is in model units and is carried
 * by the whole matrix. The matrix must be an affine transform whose axes are finite, non-zero, mutually orthogonal and
 * right-handed: such a matrix is a rotation times a positive per-axis scale, so a box keeps its exact (possibly
 * non-uniform) size. A sheared, reflected or singular matrix has no rigid pose and is refused rather than placed
 * wrongly; a sphere or capsule on a non-uniform scale takes the largest axis, which is only an approximation.
 * Holds scratch vectors: one instance for one thread at a time.
 */
class FoliageColliderTransforms {
    private val rotation = Quaternion()
    private val center = Vector3()

    fun place(collider: FoliageCollider, matrix: Matrix4): ColliderPlacementResult {
        val m = matrix.`val`
        if (m.any { !it.isFinite() }) return refused("its transform is not finite")
        if (abs(m[Matrix4.M30]) > EPS || abs(m[Matrix4.M31]) > EPS || abs(m[Matrix4.M32]) > EPS || abs(m[Matrix4.M33] - 1f) > EPS) {
            return refused("its transform is not affine")
        }
        val sx = axisLength(m, 0)
        val sy = axisLength(m, 1)
        val sz = axisLength(m, 2)
        if (!(sx > MIN_SCALE && sy > MIN_SCALE && sz > MIN_SCALE)) return refused("its transform has a zero scale")
        if (!orthogonal(m, 0, 1, sx * sy) || !orthogonal(m, 0, 2, sx * sz) || !orthogonal(m, 1, 2, sy * sz)) {
            return refused("its transform shears (a rotated copy under a non-uniform scale)")
        }
        // right-handed: (x axis cross y axis) points along the z axis
        val cx = m[1] * m[6] - m[2] * m[5]
        val cy = m[2] * m[4] - m[0] * m[6]
        val cz = m[0] * m[5] - m[1] * m[4]
        if (cx * m[8] + cy * m[9] + cz * m[10] <= 0f) return refused("its transform is reflected")

        matrix.getRotation(rotation, true).nor()
        center.set(collider.offsetX, collider.offsetY, collider.offsetZ).mul(matrix)
        val largest = max(sx, max(sy, sz))
        val uniform = abs(sx - sy) <= UNIFORM * largest && abs(sy - sz) <= UNIFORM * largest
        val placement = ColliderPlacement(
            collider.shape, center.x, center.y, center.z, rotation.x, rotation.y, rotation.z, rotation.w,
            collider.halfX * sx, collider.halfY * sy, collider.halfZ * sz,
            collider.radius * largest, collider.halfHeight * largest,
            approximated = collider.shape != FoliageColliderShape.BOX && !uniform,
        )
        return ColliderPlacementResult.Placed(placement)
    }

    private fun refused(reason: String) = ColliderPlacementResult.Refused(reason)

    private fun axisLength(m: FloatArray, column: Int): Float {
        val i = column * 4
        return sqrt(m[i] * m[i] + m[i + 1] * m[i + 1] + m[i + 2] * m[i + 2])
    }

    private fun orthogonal(m: FloatArray, a: Int, b: Int, lengths: Float): Boolean {
        val i = a * 4
        val j = b * 4
        return abs(m[i] * m[j] + m[i + 1] * m[j + 1] + m[i + 2] * m[j + 2]) <= ORTHOGONAL * lengths
    }
}

private const val EPS = 1e-4f
private const val MIN_SCALE = 1e-6f
private const val ORTHOGONAL = 1e-3f
private const val UNIFORM = 1e-3f
