/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.app.game.controlline.render

import net.nevinsky.abyssus.lib.gdx.shader.OutputTransform
import net.nevinsky.abyssus.lib.render.post.ColorGrade
import net.nevinsky.abyssus.lib.render.post.NEUTRAL_GRADE

/** The exposure the field is shown with: the default under which the bundled field reads as a bright midday scene. */
const val DEFAULT_EXPOSURE = 1f

/**
 * How [FieldRenderer] lights and shows the field (OpenSpec `control-line-pbr-rendering`). With an [outputTransform] the
 * surfaces are lit in linear light (colour textures and fog decoded from sRGB) and written through exposure, tone map
 * and sRGB encode; without one (null) they are written as the game did before the pipeline. With [skyLight] a field sky
 * that opts in with `lightsScene` lights models and terrain with its irradiance and reflection instead of the flat
 * ambient colour. The sky itself is display colour and is drawn as it is.
 */
data class FieldRenderSettings(
    val outputTransform: OutputTransform? = OutputTransform(DEFAULT_EXPOSURE),
    val skyLight: Boolean = true,
    /**
     * The final pass (OpenSpec `control-line-post-processing`): with an [outputTransform], the surfaces are drawn into
     * a linear HDR frame and shown through one pass that applies these effects and the transform. Null: the transform
     * ends each shader, as before.
     */
    val post: PostSettings? = PostSettings(),
    /**
     * Whether a scene without a sky lights its models with a reflection probe of itself (needs [outputTransform]); off,
     * such a scene keeps the flat ambient colour.
     */
    val reflectionProbe: Boolean = true,
    /**
     * Whether, in a scene without a sun, its lights cast shadows: its spot lights (up to [SPOT_SHADOW_TILES], the
     * brightest first) and its point lights nearest the scene camera (up to [POINT_SHADOW_LIGHTS]); off, such a scene
     * has none.
     */
    val localShadows: Boolean = true,
) {
    /**
     * These settings on a context that has OpenGL 3.0 when [gl30]: the display transform and the final pass need it, so
     * without it both are left off. The final pass also needs the transform.
     */
    fun forContext(gl30: Boolean): FieldRenderSettings = when {
        !gl30 && (outputTransform != null || post != null) -> copy(outputTransform = null, post = null)
        outputTransform == null && post != null -> copy(post = null)
        else -> this
    }

    companion object {
        /** The game as it was drawn before the pipeline: flat ambient, no display transform. */
        val LEGACY = FieldRenderSettings(outputTransform = null, skyLight = false, post = null)
    }
}

/** How strongly the default [PostSettings.vignette] darkens the corners: a light frame, not a tunnel. */
const val DEFAULT_VIGNETTE = 0.2f

/**
 * How much of the bloom is mixed into the frame's light by default: little, so only light far above the rest (the
 * hangar's lamps, a glint of the sun) glows.
 */
const val DEFAULT_BLOOM_STRENGTH = 0.04f

/** How much of the ambient occlusion darkens the frame's light by default (0..1). */
const val DEFAULT_AO_STRENGTH = 0.8f

/**
 * The effects of the field's final pass. With [bloom], the frame's light is blurred and mixed in at [bloomStrength];
 * with [ambientOcclusion], creases and contact areas are darkened by up to [aoStrength]; [vignette] darkens the frame's
 * corners (0..1, 0 none) and [grade] adjusts the tone mapped colour ([NEUTRAL_GRADE] changes nothing). [OFF] has none:
 * the frame then shows as the per-shader transform shows it. Bloom and ambient occlusion are the costly ones: the
 * display settings' low quality turns them off ([FieldRenderer.highQuality]).
 */
data class PostSettings(
    val bloom: Boolean = true,
    val bloomStrength: Float = DEFAULT_BLOOM_STRENGTH,
    val ambientOcclusion: Boolean = true,
    val aoStrength: Float = DEFAULT_AO_STRENGTH,
    val vignette: Float = DEFAULT_VIGNETTE,
    val grade: ColorGrade = NEUTRAL_GRADE,
) {
    companion object {
        /** No effect at all: only the output transform. */
        val OFF = PostSettings(bloom = false, ambientOcclusion = false, vignette = 0f)
    }
}
