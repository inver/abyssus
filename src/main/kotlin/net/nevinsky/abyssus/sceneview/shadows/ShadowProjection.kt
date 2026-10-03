/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.sceneview.shadows

import com.badlogic.gdx.graphics.Camera
import com.badlogic.gdx.graphics.OrthographicCamera
import com.badlogic.gdx.graphics.PerspectiveCamera
import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.math.collision.BoundingBox
import kotlin.math.abs
import kotlin.math.max

data class ShadowView(val camera: Camera, val combined: com.badlogic.gdx.math.Matrix4 = camera.combined, val near: Float, val far: Float)

enum class PointShadowFace(val direction: Vector3, val up: Vector3) {
    POS_X(Vector3.X, Vector3(0f, -1f, 0f)), NEG_X(Vector3(-1f, 0f, 0f), Vector3(0f, -1f, 0f)),
    POS_Y(Vector3.Y, Vector3(0f, 0f, 1f)), NEG_Y(Vector3(0f, -1f, 0f), Vector3(0f, 0f, -1f)),
    POS_Z(Vector3.Z, Vector3(0f, -1f, 0f)), NEG_Z(Vector3(0f, 0f, -1f), Vector3(0f, -1f, 0f));

    companion object {
        fun select(ray: Vector3): PointShadowFace {
            val x = abs(ray.x); val y = abs(ray.y); val z = abs(ray.z)
            return when {
                x >= y && x >= z -> if (ray.x >= 0f) POS_X else NEG_X
                y >= z -> if (ray.y >= 0f) POS_Y else NEG_Y
                else -> if (ray.z >= 0f) POS_Z else NEG_Z
            }
        }
        fun radialDepth(distance: Float, range: Float): Float = if (!distance.isFinite() || !range.isFinite() || range <= 0f) 1f else (distance / range).coerceIn(0f, 1f)
    }
}

/** CPU-only view fitting. Returned cameras and matrices are independent snapshots. */
class ShadowProjection(private val resolution: Int = ShadowLayout.TILE_SIZE) {
    fun point(position: Vector3, range: Float): List<ShadowView> {
        if (!finite(position) || !range.isFinite() || range <= 0f) return emptyList()
        return PointShadowFace.entries.map { face ->
            val camera = PerspectiveCamera(90f, resolution.toFloat(), resolution.toFloat())
            camera.position.set(position); camera.direction.set(face.direction); camera.up.set(face.up)
            camera.near = max(1e-6f, range / 10000f).coerceAtMost(range / 2f); camera.far = range
            camera.update(true)
            ShadowView(camera, camera.combined.cpy(), camera.near, camera.far)
        }
    }

    fun spot(position: Vector3, direction: Vector3, angle: Float, range: Float): ShadowView? {
        if (!finite(position) || !finite(direction) || direction.len2() <= 1e-12f || !angle.isFinite() || angle <= 0f || angle >= 180f || !range.isFinite() || range <= 0f) return null
        val camera = PerspectiveCamera(angle, resolution.toFloat(), resolution.toFloat())
        camera.position.set(position); camera.direction.set(direction).nor()
        camera.up.set(if (abs(camera.direction.y) > 0.98f) Vector3.Z else Vector3.Y)
        camera.near = max(1e-6f, range / 10000f).coerceAtMost(range / 2f); camera.far = range
        camera.update(true)
        return ShadowView(camera, camera.combined.cpy(), camera.near, camera.far)
    }

    /** Fits receivers plus upstream casters whose projection overlaps the receiver footprint in light space. */
    fun directional(direction: Vector3, receiverBounds: BoundingBox, casterBounds: List<BoundingBox>): ShadowView? {
        if (!finite(direction) || direction.len2() <= 1e-12f || !valid(receiverBounds)) return null
        val dir = direction.cpy().nor()
        val up = if (abs(dir.y) > 0.98f) Vector3.Z else Vector3.Y
        val right = Vector3(dir).crs(up).nor()
        val correctedUp = Vector3(right).crs(dir).nor()
        val receiverCorners = corners(receiverBounds)
        val rMinX = receiverCorners.minOf { it.dot(right) }; val rMaxX = receiverCorners.maxOf { it.dot(right) }
        val rMinY = receiverCorners.minOf { it.dot(correctedUp) }; val rMaxY = receiverCorners.maxOf { it.dot(correctedUp) }
        val included = receiverCorners.toMutableList()
        casterBounds.filter(::valid).forEach { bounds ->
            val points = corners(bounds)
            val minX = points.minOf { it.dot(right) }; val maxX = points.maxOf { it.dot(right) }
            val minY = points.minOf { it.dot(correctedUp) }; val maxY = points.maxOf { it.dot(correctedUp) }
            val upstream = points.minOf { it.dot(dir) } <= receiverCorners.maxOf { it.dot(dir) }
            if (upstream && maxX >= rMinX && minX <= rMaxX && maxY >= rMinY && minY <= rMaxY) included.addAll(points)
        }
        val minX = rMinX; val maxX = rMaxX
        val minY = rMinY; val maxY = rMaxY
        var width = max(maxX - minX, 0.1f); var height = max(maxY - minY, 0.1f)
        val texel = max(width, height) / resolution
        width = max(width, height); height = width
        val centerX = snap((minX + maxX) / 2f, texel); val centerY = snap((minY + maxY) / 2f, texel)
        val minZ = included.minOf { it.dot(dir) }; val maxZ = included.maxOf { it.dot(dir) }
        val depth = max(maxZ - minZ, 1f)
        val centerZ = (minZ + maxZ) / 2f
        val center = Vector3(right).scl(centerX).mulAdd(correctedUp, centerY).mulAdd(dir, centerZ)
        val camera = OrthographicCamera(width, height)
        camera.position.set(center).mulAdd(dir, -depth / 2f - 1f)
        camera.direction.set(dir); camera.up.set(correctedUp)
        camera.near = 0.01f; camera.far = depth + 2f
        camera.update(true)
        return ShadowView(camera, camera.combined.cpy(), camera.near, camera.far)
    }

    private fun corners(b: BoundingBox): List<Vector3> = buildList(8) {
        for (x in listOf(b.min.x, b.max.x)) for (y in listOf(b.min.y, b.max.y)) for (z in listOf(b.min.z, b.max.z)) add(Vector3(x, y, z))
    }
    private fun valid(b: BoundingBox): Boolean = finite(b.min) && finite(b.max) &&
        b.min.x <= b.max.x && b.min.y <= b.max.y && b.min.z <= b.max.z
    private fun snap(value: Float, increment: Float) = if (increment > 0f && increment.isFinite()) MathUtils.floor(value / increment + 0.5f) * increment else value
    private fun finite(v: Vector3) = v.x.isFinite() && v.y.isFinite() && v.z.isFinite()
}
