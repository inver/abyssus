/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.render.light

import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.math.collision.BoundingBox

/**
 * Which of a frame's point and spot lights light one model, the box [bounds] in world space: those whose range reaches
 * the box, the strongest there first ([localLightWeight]; equal weights by entity id), at most [profile]'s budget of
 * each kind. No GL: the hosts hand the result to their shaders.
 */
fun selectLocalLights(
    point: List<PointInput>,
    spot: List<SpotInput>,
    bounds: BoundingBox,
    profile: LightProfile = LightProfile.GAME_SCENE_LIGHTS,
): RenderLights {
    val nearest = Vector3()
    fun <T> pick(lights: List<T>, budget: Int, position: (T) -> Vector3, id: (T) -> String, weight: (T, Float) -> Float) =
        if (budget <= 0) {
            emptyList()
        } else {
            lights
                .mapNotNull { light ->
                    val d = distanceToBox(position(light), bounds, nearest)
                    weight(light, d).takeIf { it > 0f }?.let { light to it }
                }
                .sortedWith(compareByDescending<Pair<T, Float>> { it.second }.thenBy { id(it.first) })
                .take(budget)
                .map { it.first }
        }
    return RenderLights(
        emptyList(),
        pick(point, profile.maxPoint, { it.position }, { it.entityId }) { l, d ->
            localLightWeight(l.r, l.g, l.b, l.range, d)
        },
        pick(spot, profile.maxSpot, { it.position }, { it.entityId }) { l, d ->
            localLightWeight(l.r, l.g, l.b, l.range, d)
        },
    )
}

/**
 * How strongly a local light of colour [r], [g], [b] (intensity included) and [range] lights a point [distance] away:
 * its luminance, faded out over the last quarter of the range and divided by `1 + distance²`, as the model shaders do.
 * Zero at and beyond the range.
 */
fun localLightWeight(r: Float, g: Float, b: Float, range: Float, distance: Float): Float {
    if (!(distance < range) || range <= 0f) {
        return 0f
    }
    val luminance = 0.2126f * r + 0.7152f * g + 0.0722f * b
    val start = 0.75f * range
    val t = ((distance - start) / (range - start)).coerceIn(0f, 1f)
    val window = 1f - t * t * (3f - 2f * t)
    return luminance * window / (1f + distance * distance)
}

/** The distance from [point] to the nearest point of [box] (0 inside it); [scratch] holds that nearest point. */
private fun distanceToBox(point: Vector3, box: BoundingBox, scratch: Vector3): Float {
    scratch.set(
        point.x.coerceIn(box.min.x, box.max.x),
        point.y.coerceIn(box.min.y, box.max.y),
        point.z.coerceIn(box.min.z, box.max.z),
    )
    return scratch.dst(point)
}
