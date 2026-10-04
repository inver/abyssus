/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.ecs.scene

import net.nevinsky.abyssus.runtime.ecs.scene.*
import com.badlogic.gdx.math.Vector3
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.JsonNodeFactory
import com.fasterxml.jackson.databind.node.ObjectNode
import net.nevinsky.abyssus.AbyssusBundle
import net.nevinsky.abyssus.runtime.ecs.component.LightComponent
import net.nevinsky.abyssus.runtime.ecs.component.LightData
import net.nevinsky.abyssus.runtime.ecs.component.NameComponent
import net.nevinsky.abyssus.runtime.ecs.component.PositionComponent
import net.nevinsky.abyssus.runtime.ecs.component.TypeComponent
import net.nevinsky.abyssus.runtime.scene.ColorDto
import net.nevinsky.abyssus.sceneview.Vec3

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
object LightEntities {
    private val nodes = JsonNodeFactory.instance
    private val componentNames = listOf("NameComponent", "TypeComponent", "PositionComponent", "LightComponent")

    fun canAdd(root: JsonNode): Boolean {
        if (root !is ObjectNode || net.nevinsky.abyssus.assets.format.AbyssusDocumentFormat().validate(root, net.nevinsky.abyssus.assets.format.DocumentKind.SCENE) != null) return false
        val ecs = root.get("ecs") ?: return true
        if (ecs !is ObjectNode) return false
        return listOf("entities", "archetypes").all { !ecs.has(it) || ecs.get(it) is ObjectNode }
    }

    fun add(root: JsonNode, preset: LightPreset, position: Vec3): AddedLight {
        fun rejected() = AddedLight(EditResult.Rejected(AbyssusBundle.message("componentSceneUnreadable")))
        if (!canAdd(root) || listOf(position.x, position.y + preset.height, position.z).any { !it.isFinite() }) return rejected()
        val ecs = root.get("ecs") as? ObjectNode ?: nodes.objectNode()
        val entities = ecs.get("entities") as? ObjectNode ?: nodes.objectNode()
        val id = nextId(entities) ?: return rejected()
        val archetypes = ecs.get("archetypes") as? ObjectNode ?: nodes.objectNode()
        val matching = archetypes.properties().firstOrNull { (key, value) ->
            key.toIntOrNull()?.toString() == key && value.isArray && value.size() == componentNames.size &&
                value.all { it.isTextual } && value.map { it.asText() }.toSet() == componentNames.toSet()
        }?.key
        val archetype = matching ?: nextId(archetypes) ?: return rejected()
        val components = nodes.objectNode()
        components.set<JsonNode>("NameComponent", NameCodec().write(NameComponent(AbyssusBundle.message(preset.nameKey, id))))
        components.set<JsonNode>("TypeComponent", TypeCodec().write(TypeComponent(preset.type)))
        val transform = PositionComponent(position.x, position.y + preset.height, position.z)
        transform.localRotation.set(Vector3.X, preset.rotationDegrees)
        components.set<JsonNode>("PositionComponent", PositionCodec().write(transform))
        components.set<JsonNode>("LightComponent", LightCodec().write(LightComponent(LightData(preset.color, preset.intensity))))
        if (matching == null) archetypes.set<JsonNode>(archetype, nodes.arrayNode().also { a -> componentNames.forEach(a::add) })
        if (!ecs.has("archetypes")) ecs.set<JsonNode>("archetypes", archetypes)
        if (!ecs.has("entities")) ecs.set<JsonNode>("entities", entities)
        if (!root.has("ecs")) (root as ObjectNode).set<JsonNode>("ecs", ecs)
        entities.set<JsonNode>(id, nodes.objectNode().put("archetype", archetype.toInt()).set<JsonNode>("components", components))
        return AddedLight(EditResult.Changed, id)
    }

    private fun nextId(values: ObjectNode): String? {
        val highest = values.fieldNames().asSequence().mapNotNull(String::toIntOrNull).maxOrNull() ?: -1
        return if (highest == Int.MAX_VALUE) null else (highest + 1).coerceAtLeast(0).toString()
    }
}
