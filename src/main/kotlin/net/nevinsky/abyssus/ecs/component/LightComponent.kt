package net.nevinsky.abyssus.ecs.component

import com.badlogic.ashley.core.Component
import net.nevinsky.abyssus.scene.ColorDto

/** [nested] remembers whether the file held the values in a `light` object (Mundus' own shape) or directly. */
class LightComponent(val light: LightData = LightData(), var nested: Boolean = true) : Component

/** A light's color and intensity; the light kind lives on [TypeComponent]. */
data class LightData(var color: ColorDto = ColorDto(1f, 1f, 1f, 1f), var intensity: Float = 1f)
