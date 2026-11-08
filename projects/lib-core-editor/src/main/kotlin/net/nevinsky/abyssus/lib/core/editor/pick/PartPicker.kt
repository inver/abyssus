/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.editor.pick

import com.badlogic.gdx.math.Intersector
import com.badlogic.gdx.math.Matrix4
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.math.collision.Ray
import net.nevinsky.abyssus.lib.core.editor.modelmesh.ModelPartsInfo

/** A model entity as seen by part picking: its world matrix, the model's parts and whether it can be hit. */
class PartTarget(val entityId: Long, val world: Matrix4, val parts: ModelPartsInfo, val enabled: Boolean = true)

/**
 * A triangle of a part that a ray hit: the [point] and the corners [a], [b], [c], all in world space, and the
 * [distance] along the ray.
 */
class PartHit(
    val entityId: Long,
    val node: String,
    val triangle: Int,
    val point: Vector3,
    val a: Vector3,
    val b: Vector3,
    val c: Vector3,
    val distance: Float,
) {
    /** The triangle's normal from its winding (a, b, c), in world space. */
    fun normal(): Vector3 = Vector3(b).sub(a).crs(Vector3(c).sub(a)).nor()
}

/** Finds the part of a model entity whose triangles a ray hits first. CPU only, no GL. */
class PartPicker {
    /** The nearest hit among the enabled [targets]' part triangles, or null. */
    fun pick(ray: Ray, targets: List<PartTarget>, maxDistance: Float = Float.MAX_VALUE): PartHit? {
        var best: PartHit? = null
        for (target in targets) {
            if (!target.enabled) continue
            val info = target.parts
            for ((node, geometry) in info.triangles) {
                val rest = info.rest[node] ?: continue
                val world = Matrix4(target.world).mul(rest)
                val inverse = Matrix4(world).inv()
                val origin = Vector3(ray.origin).mul(inverse)
                // A direction carries no translation.
                val direction = Vector3(ray.direction).mul(inverse.setTranslation(0f, 0f, 0f))
                val local = Ray(origin, direction)
                val box = info.find(node)?.bounds
                if (box != null && !Intersector.intersectRayBounds(local, box, null)) continue
                val v0 = Vector3()
                val v1 = Vector3()
                val v2 = Vector3()
                val hit = Vector3()
                for (t in 0 until geometry.triangleCount) {
                    geometry.vertex(geometry.indices[t * 3], v0)
                    geometry.vertex(geometry.indices[t * 3 + 1], v1)
                    geometry.vertex(geometry.indices[t * 3 + 2], v2)
                    if (!intersect(local, v0, v1, v2, hit)) continue
                    // The local ray's direction is the world one carried through the inverse, so t is shared.
                    val worldPoint = Vector3(hit).mul(world)
                    val distance = worldPoint.dst(ray.origin)
                    if (distance > maxDistance || distance >= (best?.distance ?: Float.MAX_VALUE)) continue
                    best = PartHit(
                        target.entityId,
                        node,
                        t,
                        worldPoint,
                        Vector3(v0).mul(world),
                        Vector3(v1).mul(world),
                        Vector3(v2).mul(world),
                        distance,
                    )
                }
            }
        }
        return best
    }

    /** Moller-Trumbore, both faces, hit point in [out]. */
    private fun intersect(ray: Ray, a: Vector3, b: Vector3, c: Vector3, out: Vector3): Boolean {
        val e1 = Vector3(b).sub(a)
        val e2 = Vector3(c).sub(a)
        val p = Vector3(ray.direction).crs(e2)
        val det = e1.dot(p)
        if (kotlin.math.abs(det) < 1e-12f) return false
        val inv = 1f / det
        val s = Vector3(ray.origin).sub(a)
        val u = s.dot(p) * inv
        if (u < 0f || u > 1f) return false
        val q = Vector3(s).crs(e1)
        val v = ray.direction.dot(q) * inv
        if (v < 0f || u + v > 1f) return false
        val t = e2.dot(q) * inv
        if (t < 0f) return false
        out.set(ray.origin).mulAdd(ray.direction, t)
        return true
    }
}
