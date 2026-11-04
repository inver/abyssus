/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.lib.core.editor.scene

import com.badlogic.gdx.graphics.Color
import net.nevinsky.abyssus.lib.core.dto.BaseLightDto
import net.nevinsky.abyssus.lib.core.dto.FogDto
import net.nevinsky.abyssus.lib.core.dto.SceneDto

/** The [net.nevinsky.abyssus.lib.core.editor.panel.FieldRef] kind of a scene setting's value (ambient light, fog). */
const val SCENE_SETTING_KIND = "sceneSetting"

/**
 * Sets one value of a scene setting from the [text] typed in the panel. [field] is `<setting>.<value>`, for example
 * `ambientLight.color.r`, `ambientLight.intensity` or `fog.density`. Returns the changed copy, or null when [field] is
 * unknown or [text] is not a finite number (negative values are refused too). A setting the file lacks is created
 * with a white color.
 */
fun SceneDto.withSettingValue(field: String, text: String): SceneDto? {
    val value = text.trim().toFloatOrNull()?.takeIf { it.isFinite() && it >= 0f } ?: return null
    val parts = field.split('.')
    return when (parts.firstOrNull()) {
        "ambientLight" -> {
            val light = ambientLight ?: BaseLightDto()
            when (parts.getOrNull(1)) {
                "color" -> channel(light.color, parts.getOrNull(2), value)
                    ?.let { copy(ambientLight = light.copy(color = it)) }
                "intensity" -> copy(ambientLight = light.copy(intensity = value))
                else -> null
            }
        }
        "fog" -> {
            val fog = fog ?: FogDto()
            when (parts.getOrNull(1)) {
                "color" -> channel(fog.color, parts.getOrNull(2), value)?.let { copy(fog = fog.copy(color = it)) }
                "density" -> copy(fog = fog.copy(density = value))
                "gradient" -> copy(fog = fog.copy(gradient = value))
                else -> null
            }
        }
        else -> null
    }
}

private fun channel(color: Color?, name: String?, value: Float): Color? {
    val c = Color(color ?: Color.WHITE)
    when (name) {
        "r" -> c.r = value
        "g" -> c.g = value
        "b" -> c.b = value
        "a" -> c.a = value
        else -> return null
    }
    return c
}
