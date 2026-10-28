/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.render.light

import com.badlogic.gdx.graphics.g3d.Environment
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute
import net.nevinsky.abyssus.lib.core.assets.sky.hdr.HdrEnvironment
import net.nevinsky.abyssus.lib.gdx.shader.EnvironmentLightAttribute

/** The sky light of [current], fading toward [next] by [weight] (0 without a [next]). */
fun skyLightAttribute(current: HdrEnvironment, next: HdrEnvironment?, weight: Float): EnvironmentLightAttribute =
    EnvironmentLightAttribute(
        current.specular, current.irradiance, current.levels, current.ambient,
        next?.let { EnvironmentLightAttribute(it.specular, it.irradiance, it.levels, it.ambient) },
        if (next != null) weight else 0f,
    )

/** Lights this environment with the sky of [current] (fading toward [next] by [weight]) in place of the flat ambient colour. */
fun Environment.setSkyLight(current: HdrEnvironment, next: HdrEnvironment?, weight: Float) {
    remove(ColorAttribute.AmbientLight)
    set(skyLightAttribute(current, next, weight))
}
