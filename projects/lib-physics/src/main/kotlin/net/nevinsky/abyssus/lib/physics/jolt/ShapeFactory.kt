/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.lib.physics.jolt


import com.badlogic.ashley.core.Entity
import com.badlogic.gdx.math.Vector3
import com.github.stephengold.joltjni.*
import net.nevinsky.abyssus.lib.core.assets.MetaType
import net.nevinsky.abyssus.lib.core.ecs.component.assetName
import net.nevinsky.abyssus.lib.physics.ColliderComponent
import net.nevinsky.abyssus.lib.physics.ColliderShape
import net.nevinsky.abyssus.lib.physics.PhysicsAssets
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Builds the Jolt shape of a [ColliderComponent], after checking it: every size finite and greater than `0`, a hull
 * from at least 4 points not in one plane, a height field from terrain heights. Shapes are scaled by the entity's
 * scale (non-uniformly only for boxes, hulls and height fields; a sphere or capsule takes the largest axis). Every
 * native object goes to [own], which releases it with the world.
 */
internal class ShapeFactory(
    private val assets: PhysicsAssets,
    private val own: (JoltPhysicsObject) -> JoltPhysicsObject
) {
    sealed interface Built {
        class Shape(val ref: ShapeRefC, val warning: String?) : Built
        class Refused(val reason: String) : Built
    }

    fun build(entity: Entity, collider: ColliderComponent, scale: Vector3): Built {
        nonFinite(collider)?.let { return Built.Refused(it) }
        val s = Vector3(abs(scale.x), abs(scale.y), abs(scale.z))
        if (s.x == 0f || s.y == 0f || s.z == 0f) return Built.Refused("PositionComponent.localScale has a zero axis")
        val uniform = s.x == s.y && s.y == s.z
        val largest = max(s.x, max(s.y, s.z))
        var warning: String? = null
        val settings: ShapeSettings = when (collider.shape) {
            ColliderShape.BOX -> {
                val h = collider.halfExtents
                if (h.x <= 0f || h.y <= 0f || h.z <= 0f) return Built.Refused("ColliderComponent.halfExtents $h is not greater than 0")
                val half = Vector3(h.x * s.x, h.y * s.y, h.z * s.z)
                BoxShapeSettings(
                    Vec3(half.x, half.y, half.z),
                    min(Jolt.cDefaultConvexRadius, min(half.x, min(half.y, half.z)))
                )
            }

            ColliderShape.SPHERE -> {
                if (collider.radius <= 0f) return Built.Refused("ColliderComponent.radius ${collider.radius} is not greater than 0")
                if (!uniform) warning = "has a non-uniform scale; its sphere takes the largest axis"
                SphereShapeSettings(collider.radius * largest)
            }

            ColliderShape.CAPSULE -> {
                if (collider.radius <= 0f) return Built.Refused("ColliderComponent.radius ${collider.radius} is not greater than 0")
                if (collider.halfHeight <= 0f) return Built.Refused("ColliderComponent.halfHeight ${collider.halfHeight} is not greater than 0")
                if (!uniform) warning = "has a non-uniform scale; its capsule takes the largest axis"
                CapsuleShapeSettings(collider.halfHeight * largest, collider.radius * largest)
            }

            ColliderShape.CONVEX_HULL -> {
                val name = assetName(entity, MetaType.MODEL)
                    ?: return Built.Refused("has a convex hull collider but no model")
                val points = try {
                    assets.modelPoints(name)
                } catch (e: Exception) {
                    return Built.Refused("could not read model $name for its convex hull (${e.message})")
                } ?: return Built.Refused("has no readable model $name for its convex hull")
                val scaled = points.map { Vector3(it.x * s.x, it.y * s.y, it.z * s.z) }
                if (!solid(scaled)) return Built.Refused("its model $name has fewer than 4 points that are not in one plane, so it makes no convex hull")
                ConvexHullShapeSettings(scaled.map { Vec3(it.x, it.y, it.z) })
            }

            ColliderShape.HEIGHT_FIELD -> {
                val name = assetName(entity, MetaType.TERRAIN)
                    ?: return Built.Refused("has a height field collider but no terrain")
                val terrain = try {
                    assets.terrain(name)
                } catch (e: Exception) {
                    return Built.Refused("could not read terrain $name for its height field (${e.message})")
                } ?: return Built.Refused("its terrain $name has no height data")
                if (terrain.heights.any { !it.isFinite() }) return Built.Refused("its terrain $name has heights that are not finite")
                heightField(terrain.resolution, terrain.heights, terrain.size.toFloat(), s)
            }
        }
        own(settings)
        val base = create(settings) ?: return Built.Refused("Jolt refused its ${collider.shape} shape: $lastError")
        val offset = collider.offset
        if (offset.isZero) return Built.Shape(base, warning)
        val moved = own(
            RotatedTranslatedShapeSettings(
                offset.x * s.x,
                offset.y * s.y,
                offset.z * s.z,
                0f,
                0f,
                0f,
                1f,
                base
            )
        ) as ShapeSettings
        val ref = create(moved) ?: return Built.Refused("Jolt refused its offset: $lastError")
        return Built.Shape(ref, warning)
    }

    private var lastError = ""

    private fun create(settings: ShapeSettings): ShapeRefC? {
        val result = settings.create()
        own(result)
        if (result.hasError()) {
            lastError = result.getError()
            return null
        }
        return result.get().also { own(it) }
    }

    /**
     * Jolt needs a sample count that is a multiple of its block size (2): an odd grid gets one more row and column that
     * collide with nothing.
     */
    private fun heightField(resolution: Int, heights: FloatArray, size: Float, scale: Vector3): ShapeSettings {
        val count = if (resolution % 2 == 0) resolution else resolution + 1
        val samples = FloatArray(count * count) { HeightFieldShapeConstants.cNoCollisionValue }
        for (z in 0 until resolution) for (x in 0 until resolution) samples[z * count + x] = heights[z * resolution + x]
        val cell = size / (resolution - 1)
        return HeightFieldShapeSettings(samples, Vec3(0f, 0f, 0f), Vec3(cell * scale.x, scale.y, cell * scale.z), count)
    }

    private fun nonFinite(c: ColliderComponent): String? = listOf(
        "halfExtents" to listOf(c.halfExtents.x, c.halfExtents.y, c.halfExtents.z), "radius" to listOf(c.radius),
        "halfHeight" to listOf(c.halfHeight), "offset" to listOf(c.offset.x, c.offset.y, c.offset.z),
    ).firstOrNull { (_, v) -> v.any { !it.isFinite() } }
        ?.let { (name, v) -> "ColliderComponent.$name is not finite (${v.joinToString(", ")})" }

    /** Whether [points] hold 4 distinct points that are not in one plane. */
    private fun solid(points: List<Vector3>): Boolean {
        if (points.size < 4) return false
        val extent = points.fold(0f) { m, p -> max(m, max(abs(p.x), max(abs(p.y), abs(p.z)))) }
        val tolerance = max(extent, 1f) * 1e-5f
        val a = points[0]
        val b = points.firstOrNull { it.dst(a) > tolerance } ?: return false
        val ab = Vector3(b).sub(a)
        val c = points.firstOrNull { Vector3(it).sub(a).crs(ab).len() > tolerance * ab.len() } ?: return false
        val normal = Vector3(c).sub(a).crs(ab).nor()
        return points.any { abs(Vector3(it).sub(a).dot(normal)) > tolerance }
    }
}
