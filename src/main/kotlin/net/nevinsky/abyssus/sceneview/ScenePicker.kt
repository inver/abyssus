/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.sceneview

import com.badlogic.gdx.graphics.Camera
import com.badlogic.gdx.math.Intersector
import com.badlogic.gdx.math.Matrix4
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.math.collision.BoundingBox
import com.badlogic.gdx.math.collision.Ray
import net.nevinsky.abyssus.core.assets.terrain.TerrainData

/** A model entity as seen by picking: its world-space bounds. */
class BoxTarget(val entityId: String, val bounds: BoundingBox)

/** A terrain entity as seen by picking: its height field and its world transform. */
class TerrainTarget(val entityId: String, val data: TerrainData, val world: Matrix4)

/** Finds the entity under a ray: the nearest hit among model bounding boxes and terrain surfaces. No GL needed. */
object ScenePicker {
    const val REST_EPS = 0.0001f

    fun isResting(lowest: Float, height: Float): Boolean = kotlin.math.abs(height - lowest) < REST_EPS

    /** Highest real surface under the projected box; no invented ground plane or ray-distance limit. */
    fun restHeight(footprint: OrientedBox, boxes: List<OrientedBox>, terrains: List<TerrainTarget>): Float? {
        var best: Float? = null
        for (box in boxes) {
            if (box.bottom < footprint.bottom + REST_EPS && footprint.overlaps(box))
                best = best?.let { maxOf(it, box.top) } ?: box.top
        }
        for (terrain in terrains) {
            val height = TerrainRestHeight().height(footprint, terrain) ?: continue
            best = best?.let { maxOf(it, height) } ?: height
        }
        return best
    }

    /**
     * The ray through the pixel ([x], [y]) (origin top-left) of a [width] x [height] view. Does the unprojection
     * itself: libGDX's `Camera.getPickRay` reads `Gdx.graphics`, which is only installed inside
     * [GdxRuntime.withContext], and clicks arrive outside it.
     */
    fun pickRay(camera: Camera, x: Int, y: Int, width: Int, height: Int): Ray {
        val ndcX = 2f * x / width - 1f
        val ndcY = 1f - 2f * y / height
        val near = Vector3(ndcX, ndcY, -1f).prj(camera.invProjectionView)
        val far = Vector3(ndcX, ndcY, 1f).prj(camera.invProjectionView)
        return Ray(near, far.sub(near).nor())
    }

    /** [maxDistance] limits how far a terrain is searched along the ray (the camera's far plane). */
    fun pick(ray: Ray, boxes: List<BoxTarget>, terrains: List<TerrainTarget>, maxDistance: Float): String? {
        var best: String? = null
        var bestDistance = Float.MAX_VALUE
        val hit = Vector3()
        for (b in boxes) {
            if (!Intersector.intersectRayBounds(ray, b.bounds, hit)) continue
            val d = hit.dst(ray.origin)
            if (d < bestDistance) {
                bestDistance = d
                best = b.entityId
            }
        }
        for (t in terrains) {
            val d = terrainDistance(ray, t, maxDistance) ?: continue
            if (d < bestDistance) {
                bestDistance = d
                best = t.entityId
            }
        }
        return best
    }

    /**
     * Marches the ray through the terrain's local space until it passes from above the surface to below it, then
     * bisects the crossing. A ray that starts below the surface or outside the terrain only hits it by dropping onto it.
     */
    internal fun terrainDistance(ray: Ray, t: TerrainTarget, maxDistance: Float): Float? {
        val inverse = Matrix4(t.world).inv()
        val origin = Vector3(ray.origin).mul(inverse)
        val direction = Vector3(ray.direction).mul(inverse).sub(Vector3().mul(inverse)).nor()
        // world distance of one local unit along the ray (world transforms with uniform scale keep this at 1)
        val toWorld = Vector3(direction).mul(t.world).sub(Vector3().mul(t.world)).len()
        if (!toWorld.isFinite() || toWorld <= 0f) return null
        val step = t.data.size.toFloat() / (t.data.resolution - 1) / 2f
        val limit = maxDistance / toWorld
        fun above(d: Float): Boolean? {
            val h = t.data.heightAt(origin.x + direction.x * d, origin.z + direction.z * d) ?: return null
            return origin.y + direction.y * d >= h
        }
        var previous = 0f
        var previousAbove = above(0f)
        var d = step
        while (d <= limit) {
            val now = above(d)
            if (previousAbove == true && now == false) return refine(::above, previous, d) * toWorld
            previous = d
            previousAbove = now
            d += step
        }
        return null
    }

    private fun refine(above: (Float) -> Boolean?, from: Float, to: Float): Float {
        var lo = from
        var hi = to
        repeat(24) {
            val mid = (lo + hi) / 2f
            if (above(mid) == true) lo = mid else hi = mid
        }
        return (lo + hi) / 2f
    }
}
