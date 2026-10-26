/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.render.frame

import com.badlogic.gdx.math.Matrix4
import com.badlogic.gdx.math.Vector3
import net.nevinsky.abyssus.lib.gdx.shader.OutputTransform
import net.nevinsky.abyssus.lib.render.light.LightProfile
import net.nevinsky.abyssus.lib.render.light.RenderLights
import kotlin.math.exp

/**
 * Native fog: the share at distance `d` is `1 - exp(-(d * density)^gradient)`. [color] is linear RGB.
 */
class RenderFog(val r: Float, val g: Float, val b: Float, val density: Float, val gradient: Float) {
    /**
     * Multiplier for a `dot(d, d) * k` fog term that reaches the exponential share at `1 / density` for any gradient (the
     * editor's model shaders fog with such a quadratic ramp).
     */
    val quadraticCoefficient: Float get() = ((1.0 - exp(-1.0)) * density.toDouble() * density).toFloat()
}

/**
 * What every pass of one frame shares, with no GL object and no host type: the camera ([viewProjection],
 * [cameraPosition]), the [ambient] light (linear RGB), the [fog] when there is any, and the [lights] already cut to
 * [profile]. With an [output] transform the host asks its passes to light in linear space and end in the display
 * transform; without one (the default) they write what they always did.
 */
class FrameInputs(
    val viewProjection: Matrix4,
    val cameraPosition: Vector3,
    val ambient: FloatArray,
    val fog: RenderFog?,
    lights: RenderLights,
    val profile: LightProfile,
    val output: OutputTransform? = null,
) {
    val lights: RenderLights = profile.select(lights)

    init {
        require(ambient.size == 3) { "ambient is RGB" }
    }
}
