/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.lib.gdx.dto

import com.badlogic.gdx.graphics.g3d.environment.BaseLight
import net.nevinsky.abyssus.lib.core.util.EcsUtils.Companion.LIGHT_CONE_ANGLE
import net.nevinsky.abyssus.lib.core.util.EcsUtils.Companion.LIGHT_EDGE_SOFTNESS
import net.nevinsky.abyssus.lib.core.util.EcsUtils.Companion.LIGHT_INTENSITY
import net.nevinsky.abyssus.lib.core.util.EcsUtils.Companion.LIGHT_RANGE

data class LightDto(
    var intensity: Float = LIGHT_INTENSITY,
    var range: Float = LIGHT_RANGE,
    var coneAngle: Float = LIGHT_CONE_ANGLE,
    var edgeSoftness: Float = LIGHT_EDGE_SOFTNESS,
) : BaseLight<LightDto>() {
    init {
        // a light with no color in the file is white, not libGDX's black
        color.set(1f, 1f, 1f, 1f)
    }
}

data class LightWrapper(val light: LightDto)