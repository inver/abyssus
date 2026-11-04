/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.lib.physics.jolt

import com.badlogic.ashley.core.Entity
import com.badlogic.gdx.math.Vector3
import com.github.stephengold.joltjni.*
import com.github.stephengold.joltjni.readonly.ConstJoltPhysicsObject
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import net.nevinsky.abyssus.lib.core.assets.MetaType
import net.nevinsky.abyssus.lib.core.ecs.component.assetName
import net.nevinsky.abyssus.lib.physics.ColliderComponent
import net.nevinsky.abyssus.lib.physics.ColliderShape
import net.nevinsky.abyssus.lib.physics.MAX_HULL_INPUT
import net.nevinsky.abyssus.lib.physics.PhysicsAssets
import net.nevinsky.abyssus.lib.physics.hullInput
import net.nevinsky.abyssus.lib.physics.scaledBoxHalfExtents
import net.nevinsky.abyssus.lib.physics.scaledHalfHeight
import net.nevinsky.abyssus.lib.physics.scaledRadius

/**
 * Builds the Jolt shape of a [ColliderComponent], after checking it: every size finite and greater than `0`, a hull
 * from at least 4 points not in one plane, a height field from terrain heights. Shapes are scaled by the entity's scale
 * (non-uniformly only for boxes, hulls and height fields; a sphere or capsule takes the largest axis). Every native
 * object goes to [own], which releases it with the world, except the shapes of assets when there is a [cache]: those
 * are built once for a game run and released by the cache. A hull is built from at most [MAX_HULL_INPUT] points.
 */
internal class ShapeFactory(
    private val assets: PhysicsAssets,
    private val own: (JoltPhysicsObject) -> JoltPhysicsObject,
    private val cache: ShapeCache? = null,
) {
    sealed interface Built {
        class Shape(val ref: ShapeRefC, val warning: String?) : Built

        class Refused(val reason: String) : Built
    }

    fun build(entity: Entity, collider: ColliderComponent, scale: Vector3): Built {
        nonFinite(collider)?.let {
            return Built.Refused(it)
        }
        val s = Vector3(abs(scale.x), abs(scale.y), abs(scale.z))
        if (s.x == 0f || s.y == 0f || s.z == 0f) {
            return Built.Refused("PositionComponent.localScale has a zero axis")
        }
        val uniform = s.x == s.y && s.y == s.z
        var warning: String? = null
        val cached =
            when (collider.shape) {
                ColliderShape.CONVEX_HULL -> assetName(entity, MetaType.MODEL)
                ColliderShape.HEIGHT_FIELD -> assetName(entity, MetaType.TERRAIN)
                else -> null
            }
        if (cache != null && cached != null) {
            var refused: String? = null
            val key = ShapeCache.Key(collider.shape, cached, s.x, s.y, s.z)
            val base =
                cache.shape(key) { keep ->
                    when (val made = assetShape(entity, collider, s)) {
                        is AssetShape.Refused -> {
                            refused = made.reason
                            null
                        }
                        is AssetShape.Settings -> {
                            keep(made.settings)
                            create(made.settings, keep)
                                ?: run {
                                    refused = "Jolt refused its ${collider.shape} shape: $lastError"
                                    null
                                }
                        }
                    }
                } ?: return Built.Refused(refused ?: "Jolt refused its ${collider.shape} shape")
            return offsetOf(base, collider, s, null)
        }
        val settings: ShapeSettings =
            when (collider.shape) {
                ColliderShape.BOX -> {
                    val h = collider.halfExtents
                    if (h.x <= 0f || h.y <= 0f || h.z <= 0f) {
                        return Built.Refused("ColliderComponent.halfExtents $h is not greater than 0")
                    }
                    val half = scaledBoxHalfExtents(h, s)
                    BoxShapeSettings(
                        Vec3(half.x, half.y, half.z),
                        min(Jolt.cDefaultConvexRadius, min(half.x, min(half.y, half.z))),
                    )
                }

                ColliderShape.SPHERE -> {
                    if (collider.radius <= 0f) {
                        return Built.Refused("ColliderComponent.radius ${collider.radius} is not greater than 0")
                    }
                    if (!uniform) {
                        warning = "has a non-uniform scale; its sphere takes the largest axis"
                    }
                    SphereShapeSettings(scaledRadius(collider.radius, s))
                }

                ColliderShape.CAPSULE -> {
                    if (collider.radius <= 0f) {
                        return Built.Refused("ColliderComponent.radius ${collider.radius} is not greater than 0")
                    }
                    if (collider.halfHeight <= 0f) {
                        return Built.Refused(
                            "ColliderComponent.halfHeight ${collider.halfHeight} is not greater than 0"
                        )
                    }
                    if (!uniform) {
                        warning = "has a non-uniform scale; its capsule takes the largest axis"
                    }
                    CapsuleShapeSettings(scaledHalfHeight(collider.halfHeight, s), scaledRadius(collider.radius, s))
                }

                ColliderShape.CONVEX_HULL,
                ColliderShape.HEIGHT_FIELD ->
                    when (val made = assetShape(entity, collider, s)) {
                        is AssetShape.Refused -> return Built.Refused(made.reason)
                        is AssetShape.Settings -> made.settings
                    }
            }
        own(settings)
        val base =
            create(settings) { own(it as JoltPhysicsObject) }
                ?: return Built.Refused("Jolt refused its ${collider.shape} shape: $lastError")
        return offsetOf(base, collider, s, warning)
    }

    private sealed interface AssetShape {
        class Settings(val settings: ShapeSettings) : AssetShape

        class Refused(val reason: String) : AssetShape
    }

    /** The settings of a hull or height field collider, read from the entity's asset and scaled by [s]. */
    private fun assetShape(entity: Entity, collider: ColliderComponent, s: Vector3): AssetShape =
        when (collider.shape) {
            ColliderShape.CONVEX_HULL -> {
                val name = assetName(entity, MetaType.MODEL)
                if (name == null) {
                    AssetShape.Refused("has a convex hull collider but no model")
                } else {
                    val points =
                        try {
                            assets.modelPoints(name)
                        } catch (e: Exception) {
                            return AssetShape.Refused("could not read model $name for its convex hull (${e.message})")
                        } ?: return AssetShape.Refused("has no readable model $name for its convex hull")
                    val scaled = points.map { Vector3(it.x * s.x, it.y * s.y, it.z * s.z) }
                    if (!solid(scaled)) {
                        AssetShape.Refused(
                            "its model $name has fewer than 4 points that are not in one plane, so it makes no " +
                                "convex hull"
                        )
                    }
                    // the hull's own vertices bound its input: its build time does not grow with the model's vertex
                    // count
                    else {
                        AssetShape.Settings(ConvexHullShapeSettings(hullInput(scaled).map { Vec3(it.x, it.y, it.z) }))
                    }
                }
            }

            ColliderShape.HEIGHT_FIELD -> {
                val name = assetName(entity, MetaType.TERRAIN)
                if (name == null) {
                    AssetShape.Refused("has a height field collider but no terrain")
                } else {
                    val terrain =
                        try {
                            assets.terrain(name)
                        } catch (e: Exception) {
                            return AssetShape.Refused(
                                "could not read terrain $name for its height field (${e.message})"
                            )
                        } ?: return AssetShape.Refused("its terrain $name has no height data")
                    if (terrain.heights.any { !it.isFinite() }) {
                        AssetShape.Refused("its terrain $name has heights that are not finite")
                    } else {
                        AssetShape.Settings(heightField(terrain.resolution, terrain.heights, terrain.size.toFloat(), s))
                    }
                }
            }

            else -> error("${collider.shape} is not built from an asset")
        }

    /** [base] moved by the collider's offset (a wrapper this world owns), or [base] itself without one. */
    private fun offsetOf(base: ShapeRefC, collider: ColliderComponent, s: Vector3, warning: String?): Built {
        val offset = collider.offset
        if (offset.isZero) {
            return Built.Shape(base, warning)
        }
        val moved =
            own(
                RotatedTranslatedShapeSettings(
                    offset.x * s.x,
                    offset.y * s.y,
                    offset.z * s.z,
                    0f,
                    0f,
                    0f,
                    1f,
                    base,
                )
            )
                as ShapeSettings
        val ref =
            create(moved) { own(it as JoltPhysicsObject) }
                ?: return Built.Refused("Jolt refused its offset: $lastError")
        return Built.Shape(ref, warning)
    }

    private var lastError = ""

    /** Creates the shape of [settings]; its result and reference go to [keep], which releases them. */
    private fun create(settings: ShapeSettings, keep: (ConstJoltPhysicsObject) -> Unit): ShapeRefC? {
        val result = settings.create()
        keep(result)
        if (result.hasError()) {
            lastError = result.getError()
            return null
        }
        return result.get().also { keep(it) }
    }

    /**
     * Jolt needs a sample count that is a multiple of its block size (2): an odd grid gets one more row and column that
     * collide with nothing.
     */
    private fun heightField(resolution: Int, heights: FloatArray, size: Float, scale: Vector3): ShapeSettings {
        val count =
            if (resolution % 2 == 0) {
                resolution
            } else {
                resolution + 1
            }
        val samples = FloatArray(count * count) { HeightFieldShapeConstants.cNoCollisionValue }
        for (z in 0 until resolution) for (x in 0 until resolution) samples[z * count + x] = heights[z * resolution + x]
        val cell = size / (resolution - 1)
        return HeightFieldShapeSettings(samples, Vec3(0f, 0f, 0f), Vec3(cell * scale.x, scale.y, cell * scale.z), count)
    }

    private fun nonFinite(c: ColliderComponent): String? =
        listOf(
                "halfExtents" to listOf(c.halfExtents.x, c.halfExtents.y, c.halfExtents.z),
                "radius" to listOf(c.radius),
                "halfHeight" to listOf(c.halfHeight),
                "offset" to listOf(c.offset.x, c.offset.y, c.offset.z),
            )
            .firstOrNull { (_, v) -> v.any { !it.isFinite() } }
            ?.let { (name, v) -> "ColliderComponent.$name is not finite (${v.joinToString(", ")})" }

    /** Whether [points] hold 4 distinct points that are not in one plane. */
    private fun solid(points: List<Vector3>): Boolean {
        if (points.size < 4) {
            return false
        }
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
