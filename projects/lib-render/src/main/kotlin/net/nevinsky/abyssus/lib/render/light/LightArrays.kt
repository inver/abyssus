/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.render.light

/**
 * The lights laid out for a shader's uniform arrays (`vec3[max]`, `float[max]`): the pure part of binding, so a host can
 * test it without GL. Slots past a light kind's count stay zero.
 */
class LightArrays(
    val directionalCount: Int,
    val directionalDirection: FloatArray,
    val directionalColor: FloatArray,
    val pointCount: Int,
    val pointPosition: FloatArray,
    val pointColor: FloatArray,
    val pointRange: FloatArray,
    val spotCount: Int,
    val spotPosition: FloatArray,
    val spotDirection: FloatArray,
    val spotColor: FloatArray,
    val spotRange: FloatArray,
    val spotOuter: FloatArray,
    val spotInner: FloatArray,
)

/** Packs [lights] into arrays of [slots] entries per kind; lights past [slots] are dropped. */
fun packLights(lights: RenderLights, slots: Int): LightArrays {
    val dirs = lights.directional.take(slots)
    val points = lights.point.take(slots)
    val spots = lights.spot.take(slots)
    val dirDirection = FloatArray(slots * 3)
    val dirColor = FloatArray(slots * 3)
    dirs.forEachIndexed { i, d ->
        put(dirDirection, i, d.direction.x, d.direction.y, d.direction.z)
        put(dirColor, i, d.r, d.g, d.b)
    }
    val pointPosition = FloatArray(slots * 3)
    val pointColor = FloatArray(slots * 3)
    val pointRange = FloatArray(slots)
    points.forEachIndexed { i, p ->
        put(pointPosition, i, p.position.x, p.position.y, p.position.z)
        put(pointColor, i, p.r, p.g, p.b)
        pointRange[i] = p.range
    }
    val spotPosition = FloatArray(slots * 3)
    val spotDirection = FloatArray(slots * 3)
    val spotColor = FloatArray(slots * 3)
    val spotRange = FloatArray(slots)
    val outer = FloatArray(slots)
    val inner = FloatArray(slots)
    spots.forEachIndexed { i, s ->
        put(spotPosition, i, s.position.x, s.position.y, s.position.z)
        put(spotDirection, i, s.direction.x, s.direction.y, s.direction.z)
        put(spotColor, i, s.r, s.g, s.b)
        spotRange[i] = s.range
        outer[i] = s.outerCos
        inner[i] = s.innerCos
    }
    return LightArrays(
        dirs.size, dirDirection, dirColor, points.size, pointPosition, pointColor, pointRange,
        spots.size, spotPosition, spotDirection, spotColor, spotRange, outer, inner,
    )
}

private fun put(into: FloatArray, index: Int, x: Float, y: Float, z: Float) {
    into[index * 3] = x
    into[index * 3 + 1] = y
    into[index * 3 + 2] = z
}
