/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.lib.core.editor.components

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.math.Vector3
import net.nevinsky.abyssus.lib.core.dto.LightDto
import net.nevinsky.abyssus.lib.core.ecs.component.LightComponent
import net.nevinsky.abyssus.lib.core.ecs.component.NameComponent
import net.nevinsky.abyssus.lib.core.ecs.component.PositionComponent
import net.nevinsky.abyssus.lib.core.dto.EntityType
import net.nevinsky.abyssus.lib.core.editor.EditorMessages
import net.nevinsky.abyssus.lib.core.editor.util.Util
import net.nevinsky.abyssus.lib.core.dto.SceneDto

/** Sun is a directional light with different initial values, never a separate file type. */
enum class LightPreset(
    val labelKey: String,
    val nameKey: String,
    val type: EntityType,
    val color: Color,
    val intensity: Float,
    val rotationDegrees: Float,
    val height: Float,
) {
    DIRECTIONAL(
        "lightDirectional",
        "lightDirectionalName",
        EntityType.LIGHT_DIRECTIONAL,
        Color(1f, 1f, 1f, 1f),
        1f,
        -45f,
        0f
    ),
    SUN(
        "lightSun",
        "lightSunName",
        EntityType.LIGHT_DIRECTIONAL,
        Color(1f, 0.96f, 0.84f, 1f),
        1.2f,
        -30f,
        0f
    ),
    SPOT("lightSpot", "lightSpotName", EntityType.LIGHT_SPOT, Color(1f, 1f, 1f, 1f), 1f, -90f, 5f),
}

data class AddedLight(val result: EditResult, val entityId: Long? = null)

/** Edits only the [SceneDto]; callers write it through editSceneJson as a single undoable command. */
class LightEntities(private val messages: EditorMessages) {
    fun add(scene: SceneDto, preset: LightPreset, position: Vector3): AddedLight {
        if (!Util.isFinite(position) || !(position.y + preset.height).isFinite()) {
            return AddedLight(EditResult.Rejected(messages.message("componentSceneUnreadable")))
        }
        val transform = PositionComponent(position.x, position.y + preset.height, position.z)
        transform.localRotation.set(Vector3.X, preset.rotationDegrees)
        val light = LightComponent(LightDto(intensity = preset.intensity).also { it.color.set(preset.color) })
        val id = scene.add(preset.type) { id -> listOf(NameComponent(messages.message(preset.nameKey, id.toString())), transform, light) }
        return AddedLight(EditResult.Changed, id)
    }
}
