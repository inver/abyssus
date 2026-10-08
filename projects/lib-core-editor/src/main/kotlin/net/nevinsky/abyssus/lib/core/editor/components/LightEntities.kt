/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.lib.core.editor.components

import net.nevinsky.abyssus.lib.core.io.JsonProcessor
import net.nevinsky.abyssus.lib.runtime.ecs.EcsWriter
import com.badlogic.gdx.math.Vector3
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.JsonNodeFactory
import net.nevinsky.abyssus.lib.core.editor.EditorMessages
import net.nevinsky.abyssus.lib.core.editor.document.SceneEntityTree
import net.nevinsky.abyssus.lib.core.ecs.component.LightComponent
import net.nevinsky.abyssus.lib.core.ecs.component.LightData
import net.nevinsky.abyssus.lib.core.ecs.component.NameComponent
import net.nevinsky.abyssus.lib.core.ecs.component.PositionComponent
import net.nevinsky.abyssus.lib.core.ecs.component.TypeComponent
import net.nevinsky.abyssus.lib.core.dto.ColorDto
import net.nevinsky.abyssus.lib.core.editor.content.Vec3

/** Sun is a directional light with different initial values, never a separate file type. */
enum class LightPreset(
    val labelKey: String,
    val nameKey: String,
    val type: TypeComponent.Type,
    val color: ColorDto,
    val intensity: Float,
    val rotationDegrees: Float,
    val height: Float,
) {
    DIRECTIONAL("lightDirectional", "lightDirectionalName", TypeComponent.Type.LIGHT_DIRECTIONAL, ColorDto(1f, 1f, 1f, 1f), 1f, -45f, 0f),
    SUN("lightSun", "lightSunName", TypeComponent.Type.LIGHT_DIRECTIONAL, ColorDto(1f, 0.96f, 0.84f, 1f), 1.2f, -30f, 0f),
    SPOT("lightSpot", "lightSpotName", TypeComponent.Type.LIGHT_SPOT, ColorDto(1f, 1f, 1f, 1f), 1f, -90f, 5f),
}

data class AddedLight(val result: EditResult, val entityId: String? = null)

/** Edits only the JSON tree; callers write it through editSceneJson as a single undoable command. */
class LightEntities(private val messages: EditorMessages) {
    private val nodes = JsonNodeFactory.instance
    private val writer = EcsWriter(JsonProcessor().mapper)

    fun canAdd(root: JsonNode): Boolean = SceneEntityTree(root).canAdd()

    fun add(root: JsonNode, preset: LightPreset, position: Vec3): AddedLight {
        fun rejected() = AddedLight(EditResult.Rejected(messages.message("componentSceneUnreadable")))
        if (!canAdd(root) || listOf(position.x, position.y + preset.height, position.z).any { !it.isFinite() }) return rejected()
        val id = SceneEntityTree(root).insert { id ->
            val transform = PositionComponent(position.x, position.y + preset.height, position.z)
            transform.localRotation.set(Vector3.X, preset.rotationDegrees)
            nodes.objectNode().apply {
                set<JsonNode>("NameComponent", writer.writeComponent(NameComponent(messages.message(preset.nameKey, id))))
                set<JsonNode>("TypeComponent", writer.writeComponent(TypeComponent(preset.type)))
                set<JsonNode>("PositionComponent", writer.writeComponent(transform))
                set<JsonNode>("LightComponent", writer.writeComponent(LightComponent(LightData(preset.color, preset.intensity))))
            }
        } ?: return rejected()
        return AddedLight(EditResult.Changed, id)
    }
}
