/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.assets.foliage

import net.nevinsky.abyssus.lib.core.assets.terrain.TerrainData

import com.badlogic.gdx.math.Matrix4
import com.badlogic.gdx.math.Quaternion
import com.badlogic.gdx.math.Vector3
import kotlin.math.acos

/**
 * Builds the instance matrix of one baked copy: the terrain entity's transform, then the copy's position on the
 * terrain surface, then the lean toward the surface normal by the layer's [align], then the copy's yaw and uniform
 * scale — the order the placement implies.
 *
 * The matrix stands in for `Renderable.worldTransform`, which the drawable keeps at the identity: the `instancedFlag`
 * shaders read the world matrix from the instance attributes, so the copy's transform travels there.
 */
class FoliageMatrices {
    private val normal = Vector3()
    private val axis = Vector3()
    private val tilt = Quaternion()

    /**
     * Writes the matrix of [copy] into [out] and returns it, or returns null and leaves [out] alone when the copy's
     * position falls outside the terrain height grid. [align] is the layer's `alignToNormal`, from 0 (upright)
     * through 1 (with the slope).
     */
    fun copy(
        terrain: TerrainData,
        align: Float,
        entity: Matrix4,
        copy: FoliageCopy,
        out: Matrix4,
    ): Matrix4? {
        val height = terrain.heightAt(copy.x, copy.z) ?: return null
        out.set(entity).translate(copy.x, height, copy.z)

        // Align: blend the surface normal into +Y by the layer's alignment, then rotate the copy's +Y onto it. With
        // align 0, or without a normal outside the terrain, the copy stays upright under the entity's own tilt.
        if (align > 0f) {
            val n = terrain.normalAt(copy.x, copy.z, normal)
            if (n != null) {
                n.scl(align).add(0f, 1f - align, 0f).nor()
                axis.set(0f, 1f, 0f).crs(n)
                if (axis.len2() > 1e-6f) {
                    out.rotate(tilt.setFromAxisRad(axis, acos(n.y.coerceIn(-1f, 1f))))
                }
            }
        }

        out.rotateRad(0f, 1f, 0f, copy.yaw)
        out.scale(copy.scale, copy.scale, copy.scale)
        return out
    }
}
