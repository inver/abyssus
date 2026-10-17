/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.render.shadow

import com.badlogic.gdx.graphics.OrthographicCamera
import com.badlogic.gdx.math.Vector3
import kotlin.math.abs

/** How far a sun shadow camera sits back along the light, and how deep it sees: tall scenery upwind still casts. */
const val SUN_SHADOW_DEPTH = 400f

/**
 * A single sun's orthographic shadow camera: [extent] metres square around [center], looking along the light ([towardSun]
 * reversed). Its centre is snapped to whole texels of a [resolution] map, so a moving centre does not make edges
 * shimmer. No GL.
 */
fun sunShadowCamera(towardSun: Vector3, center: Vector3, extent: Float, resolution: Int, depth: Float = SUN_SHADOW_DEPTH): OrthographicCamera {
    val direction = Vector3(towardSun).nor().scl(-1f)
    val up = if (abs(direction.y) > 0.98f) Vector3.Z else Vector3.Y
    val right = Vector3(direction).crs(up).nor()
    val lightUp = Vector3(right).crs(direction).nor()
    val texel = extent / resolution
    val x = Math.round(center.dot(right) / texel) * texel
    val y = Math.round(center.dot(lightUp) / texel) * texel
    val snapped = Vector3(right).scl(x).mulAdd(lightUp, y).mulAdd(direction, center.dot(direction))
    return OrthographicCamera().apply {
        viewportWidth = extent
        viewportHeight = extent
        position.set(snapped).mulAdd(direction, -depth / 2f)
        this.direction.set(direction)
        this.up.set(lightUp)
        near = 1f
        far = depth
        update(false) // the map is drawn whole: no frustum culling, so no frustum
    }
}

/** The depth bias for [camera]'s map: a quarter texel's worth of depth, as directional shadows use. */
fun sunShadowBias(camera: OrthographicCamera, resolution: Int): Float =
    0.25f * camera.viewportWidth / (camera.far - camera.near) / resolution
