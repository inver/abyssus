/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.render.light

import net.nevinsky.abyssus.lib.core.util.EDITOR_MAX_DIRECTIONAL
import net.nevinsky.abyssus.lib.core.util.EDITOR_MAX_POINT
import net.nevinsky.abyssus.lib.core.util.EDITOR_MAX_SPOT
import net.nevinsky.abyssus.lib.core.util.GAME_MAX_DIRECTIONAL
import net.nevinsky.abyssus.lib.core.util.GAME_MAX_POINT
import net.nevinsky.abyssus.lib.core.util.GAME_MAX_SPOT

import com.badlogic.gdx.math.Vector3

/** A directional light a frame lights with: [direction] points the way the light travels, [color] already holds the intensity. */
class DirectionalInput(val entityId: String, val direction: Vector3, val r: Float, val g: Float, val b: Float)

class PointInput(
    val entityId: String,
    val position: Vector3,
    val r: Float,
    val g: Float,
    val b: Float,
    val range: Float
)

/** A spot light; [outerCos] / [innerCos] are the cosines of the cone's edge and of where its softening starts. */
class SpotInput(
    val entityId: String,
    val position: Vector3,
    val direction: Vector3,
    val r: Float,
    val g: Float,
    val b: Float,
    val range: Float,
    val outerCos: Float,
    val innerCos: Float,
)

/** The lights of one frame in a host-neutral form; hosts adapt their own light types to it. */
class RenderLights(
    val directional: List<DirectionalInput> = emptyList(),
    val point: List<PointInput> = emptyList(),
    val spot: List<SpotInput> = emptyList(),
)

/** A frame lit by no light. */
val NO_RENDER_LIGHTS = RenderLights()

/**
 * How many lights a host lights with, by name. The editor lights terrains with several lights of every kind; a game that
 * lights with one sun keeps to the first directional light and ignores the rest.
 */
enum class LightProfile(val maxDirectional: Int, val maxPoint: Int, val maxSpot: Int) {
    EDITOR_MULTI_LIGHT(maxDirectional = EDITOR_MAX_DIRECTIONAL, maxPoint = EDITOR_MAX_POINT, maxSpot = EDITOR_MAX_SPOT),
    GAME_SINGLE_SUN(maxDirectional = GAME_MAX_DIRECTIONAL, maxPoint = GAME_MAX_POINT, maxSpot = GAME_MAX_SPOT);

    /** [lights] cut to this profile's budget, in their order. The same instance when nothing is cut. */
    fun select(lights: RenderLights): RenderLights {
        if (lights.directional.size <= maxDirectional && lights.point.size <= maxPoint && lights.spot.size <= maxSpot) return lights
        return RenderLights(
            lights.directional.take(maxDirectional),
            lights.point.take(maxPoint),
            lights.spot.take(maxSpot)
        )
    }
}
