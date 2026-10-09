/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.lib.gdx.editor.components

import com.badlogic.gdx.graphics.Color
import net.nevinsky.abyssus.lib.gdx.io.JsonProcessor
import net.nevinsky.abyssus.lib.gdx.editor.ecs.EcsWriter
import com.badlogic.gdx.math.Vector3
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.JsonNodeFactory
import net.nevinsky.abyssus.lib.gdx.editor.EditorMessages
import net.nevinsky.abyssus.lib.gdx.editor.document.SceneEntityTree
import net.nevinsky.abyssus.lib.gdx.ecs.component.LightComponent
import org.slf4j.helpers.NOPLogger
import net.nevinsky.abyssus.lib.gdx.ecs.component.NameComponent
import net.nevinsky.abyssus.lib.gdx.ecs.component.PositionComponent
import net.nevinsky.abyssus.lib.gdx.ecs.component.TypeComponent
import net.nevinsky.abyssus.lib.gdx.dto.LightDto
import net.nevinsky.abyssus.lib.gdx.editor.content.Vec3

/** Sun is a directional light with different initial values, never a separate file type. */
enum class LightPreset(
    val labelKey: String,
    val nameKey: String,
    val type: TypeComponent.Type,
    val color: Color,
    val intensity: Float,
    val rotationDegrees: Float,
    val height: Float,
) {
    DIRECTIONAL(
        "lightDirectional",
        "lightDirectionalName",
        TypeComponent.Type.LIGHT_DIRECTIONAL,
        Color(1f, 1f, 1f, 1f),
        1f,
        -45f,
        0f
    ),
    SUN(
        "lightSun",
        "lightSunName",
        TypeComponent.Type.LIGHT_DIRECTIONAL,
        Color(1f, 0.96f, 0.84f, 1f),
        1.2f,
        -30f,
        0f
    ),
    SPOT("lightSpot", "lightSpotName", TypeComponent.Type.LIGHT_SPOT, Color(1f, 1f, 1f, 1f), 1f, -90f, 5f),
}

data class AddedLight(val result: EditResult, val entityId: String? = null)

/** Edits only the JSON tree; callers write it through editSceneJson as a single undoable command. */
class LightEntities(private val messages: EditorMessages) {
    private val nodes = JsonNodeFactory.instance
    private val writer = EcsWriter(JsonProcessor(NOPLogger.NOP_LOGGER).mapper)

    fun canAdd(root: JsonNode): Boolean = SceneEntityTree(root).canAdd()

    fun add(root: JsonNode, preset: LightPreset, position: Vec3): AddedLight {
        fun rejected() = AddedLight(EditResult.Rejected(messages.message("componentSceneUnreadable")))
        if (!canAdd(root) || listOf(
                position.x,
                position.y + preset.height,
                position.z
            ).any { !it.isFinite() }
        ) return rejected()
        val id = SceneEntityTree(root).insert { id ->
            val transform = PositionComponent(position.x, position.y + preset.height, position.z)
            transform.localRotation.set(Vector3.X, preset.rotationDegrees)
            nodes.objectNode().apply {
                set<JsonNode>(
                    "NameComponent",
                    writer.writeComponent(NameComponent(messages.message(preset.nameKey, id)))
                )
                set<JsonNode>("TypeComponent", writer.writeComponent(TypeComponent(preset.type)))
                set<JsonNode>("PositionComponent", writer.writeComponent(transform))
                set<JsonNode>(
                    "LightComponent",
                    writer.writeComponent(
                        LightComponent(LightDto(intensity = preset.intensity).also { it.color.set(preset.color) }),
                    )
                )
            }
        } ?: return rejected()
        return AddedLight(EditResult.Changed, id)
    }
}
