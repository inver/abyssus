/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.lib.core.dto

import com.badlogic.gdx.graphics.g3d.environment.BaseLight

const val LIGHT_INTENSITY = 1f
const val LIGHT_RANGE = 100f
const val LIGHT_CONE_ANGLE = 45f
const val LIGHT_EDGE_SOFTNESS = 0.2f

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