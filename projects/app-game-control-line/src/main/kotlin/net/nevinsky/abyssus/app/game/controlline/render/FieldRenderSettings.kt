/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.app.game.controlline.render

import net.nevinsky.abyssus.lib.gdx.shader.OutputTransform

/** The exposure the field is shown with: the default under which the bundled field reads as a bright midday scene. */
const val DEFAULT_EXPOSURE = 1f

/**
 * How [FieldRenderer] lights and shows the field (OpenSpec `control-line-pbr-rendering`). With an [outputTransform] the
 * surfaces are lit in linear light (colour textures and fog decoded from sRGB) and written through exposure, tone map and
 * sRGB encode; without one (null) they are written as the game did before the pipeline. With [skyLight] a field sky that
 * opts in with `lightsScene` lights models and terrain with its irradiance and reflection instead of the flat ambient colour.
 * The sky itself is display colour and is drawn as it is.
 */
data class FieldRenderSettings(
    val outputTransform: OutputTransform? = OutputTransform(DEFAULT_EXPOSURE),
    val skyLight: Boolean = true,
) {
    /** These settings on a context that has OpenGL 3.0 when [gl30]: the display transform needs it, so without it the transform is left off. */
    fun forContext(gl30: Boolean): FieldRenderSettings = if (outputTransform == null || gl30) this else copy(outputTransform = null)

    companion object {
        /** The game as it was drawn before the pipeline: flat ambient, no display transform. */
        val LEGACY = FieldRenderSettings(outputTransform = null, skyLight = false)
    }
}
