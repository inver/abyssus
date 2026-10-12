/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.plugin.sceneview.skybox

import net.nevinsky.abyssus.lib.core.editor.scene.LightSet

/**
 * How the clouds shade the sun in one frame. [lights] is what the scene is lit with. With a cloud shadow map active
 * ([mapLightIndex] is the sun's index among the set's directional lights) the sun keeps its full color, the map
 * dims it per position, and [outside] is the uniform share it keeps beyond the map. Without one, [lights] has the sun
 * dimmed uniformly and [outside] is 1.
 */
class SunShading(val lights: LightSet, val mapLightIndex: Int?, val outside: Float)

/**
 * Chooses between the cloud shadow map and the uniform sun dimming, so a surface is never dimmed by both.
 *
 * The map shades the sun only when [cloudsDrawn] (the sky has clouds some technique can still draw), the map is
 * [mapReady], and the sun entity [sunId] is among [lights]' directional lights (the rendered budget): a scene whose sun
 * is not rendered, or that has no usable directional light, shades no other light. Otherwise the sun is scaled by
 * [scale] (the sun-occlusion transmittance) as before cloud shadows; if the sun is not in [lights] there is nothing to
 * scale.
 */
fun planSunShading(lights: LightSet, sunId: String?, cloudsDrawn: Boolean, mapReady: Boolean, scale: Float): SunShading {
    val index = lights.directionalIndexOf(sunId)
    if (cloudsDrawn && mapReady && index != null) return SunShading(lights, index, scale)
    return SunShading(lights.withSunScale(sunId, scale), null, 1f)
}
