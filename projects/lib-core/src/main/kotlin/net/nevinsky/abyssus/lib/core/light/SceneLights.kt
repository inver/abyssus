/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.light

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.g3d.environment.PointLight
import com.badlogic.gdx.graphics.g3d.environment.SpotLight
import com.badlogic.gdx.math.Quaternion
import com.badlogic.gdx.math.Vector3
import net.nevinsky.abyssus.lib.core.ecs.component.LIGHT_CONE_ANGLE
import net.nevinsky.abyssus.lib.core.ecs.component.LIGHT_EDGE_SOFTNESS
import net.nevinsky.abyssus.lib.core.ecs.component.LIGHT_RANGE
import net.nevinsky.abyssus.lib.core.ecs.component.LightDto
import net.nevinsky.abyssus.lib.core.format.EntityType

/*
 * The light entities of a scene as every renderer reads them (OpenSpec `scene-entity-lights`, "Light values match the
 * Properties panel"): the editor's scene view and a game share these rules, so a light shows the same reach and cone in
 * both. No GL.
 */

enum class LightKind {
    DIRECTIONAL,
    POINT,
    SPOT,
}

/** A copy of [r], [g], [b], [a] that keeps values above 1 (libGDX's [Color] setters clamp to 0..1). */
fun unclampedColor(r: Float, g: Float, b: Float, a: Float): Color =
    Color().also {
        it.r = r
        it.g = g
        it.b = b
        it.a = a
    }

/**
 * One light entity. [direction] is the unit vector the light shines along (directional and spot); [position] matters
 * for point and spot.
 */
data class LightPlacement(
    val entityId: Long,
    val kind: LightKind,
    val color: Color,
    val intensity: Float,
    val position: Vector3,
    val direction: Vector3,
    val range: Float = LIGHT_RANGE,
    val rotation: Quaternion = Quaternion(),
    val coneAngle: Float = LIGHT_CONE_ANGLE,
    val edgeSoftness: Float = LIGHT_EDGE_SOFTNESS,
    /** The `PositionComponent.lookAtId` of the light, or null when it does not name a target. */
    val lookAtId: Long? = null,
)

/** The unit vector a light or camera with rotation [q] faces: its rotated -Z; -Z for a zero quaternion. */
fun forwardOf(q: Quaternion): Vector3 {
    if (q.len2() < 1e-12f) {
        return Vector3(0f, 0f, -1f)
    }
    return Quaternion(q).nor().transform(Vector3(0f, 0f, -1f))
}

/**
 * The kind of light an entity of [type] is: `LIGHT_POINT` / `LIGHT_SPOT` as named, any other `LIGHT_...` type
 * directional, and an entity of no light type that still has a light component ([hasLight]) directional too. Null for
 * any other entity.
 */
fun lightKindOf(type: EntityType?, hasLight: Boolean): LightKind? =
    when {
        type == null || !type.name.startsWith("LIGHT") ->
            if (hasLight) {
                LightKind.DIRECTIONAL
            } else {
                null
            }
        type == EntityType.LIGHT_POINT -> LightKind.POINT
        type == EntityType.LIGHT_SPOT -> LightKind.SPOT
        else -> LightKind.DIRECTIONAL
    }

/**
 * The light entity [entityId] of [kind] with the values of [light] (defaults without one) at [position], turned by
 * [rotation]. A negative intensity reads as 0.
 */
fun lightPlacementOf(
    entityId: Long,
    kind: LightKind,
    light: LightDto?,
    position: Vector3,
    rotation: Quaternion,
    lookAtId: Long? = null,
): LightPlacement {
    val values = light ?: LightDto()
    return LightPlacement(
        entityId,
        kind,
        Color(values.color.r, values.color.g, values.color.b, 1f),
        values.intensity.coerceAtLeast(0f),
        position,
        forwardOf(rotation),
        values.range,
        rotation,
        values.coneAngle,
        values.edgeSoftness,
        lookAtId,
    )
}

/**
 * Whether [l] can light anything: a positive, finite intensity and range, finite colour, position and direction, and
 * for a spot a cone between 0 and 180 degrees, an edge softness in 0..1 and a direction.
 */
fun usable(l: LightPlacement): Boolean =
    l.intensity > 0f &&
        l.intensity.isFinite() &&
        l.range > 0f &&
        l.range.isFinite() &&
        (l.kind != LightKind.SPOT ||
            (l.coneAngle.isFinite() &&
                l.coneAngle > 0f &&
                l.coneAngle < 180f &&
                l.edgeSoftness.isFinite() &&
                l.edgeSoftness in 0f..1f &&
                (l.direction.x != 0f || l.direction.y != 0f || l.direction.z != 0f))) &&
        listOf(
                l.color.r,
                l.color.g,
                l.color.b,
                l.position.x,
                l.position.y,
                l.position.z,
                l.direction.x,
                l.direction.y,
                l.direction.z,
            )
            .all { it.isFinite() }

/** [l]'s colour times its intensity, unclamped. */
fun scaledColor(l: LightPlacement): Color =
    unclampedColor(l.color.r * l.intensity, l.color.g * l.intensity, l.color.b * l.intensity, 1f)

/** The usable lights of a scene, nearest first: at most the directional and the local (point and spot) budget. */
class SceneLightSelection(val directional: List<LightPlacement>, val local: List<LightPlacement>)

/**
 * The usable [lights] nearest [target] (ties by entity id), at most [maxDirectional] directional lights and [maxLocal]
 * point and spot lights together.
 */
fun selectLights(
    lights: List<LightPlacement>,
    target: Vector3,
    maxDirectional: Int,
    maxLocal: Int,
): SceneLightSelection {
    val order = compareBy<LightPlacement> { it.position.dst(target) }.thenBy { it.entityId }
    val directional =
        lights.filter { it.kind == LightKind.DIRECTIONAL && usable(it) }.sortedWith(order).take(maxDirectional)
    val local = lights.filter { it.kind != LightKind.DIRECTIONAL && usable(it) }.sortedWith(order).take(maxLocal)
    return SceneLightSelection(directional, local)
}

/**
 * The libGDX point light the model shaders read for [l] (a point light): the scaled colour, unclamped so an intensity
 * above 1 still brightens (libGDX's `Color.set(Color)` copies without clamping), and the range where libGDX keeps the
 * intensity (`LocalLightBindings` packs it so).
 */
fun pointLightOf(l: LightPlacement): PointLight =
    PointLight().set(scaledColor(l), l.position.x, l.position.y, l.position.z, l.range)

/**
 * The libGDX spot light the model shaders read for [l] (a spot light): the range where libGDX keeps the intensity, the
 * cone's half angle as the cut-off and the edge softness as the exponent.
 */
fun spotLightOf(l: LightPlacement): SpotLight =
    SpotLight()
        .set(scaledColor(l), Vector3(l.position), Vector3(l.direction), l.range, l.coneAngle / 2f, l.edgeSoftness)
