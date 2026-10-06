/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.ecs.scene

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.JsonNodeFactory
import net.nevinsky.abyssus.AbyssusBundle
import net.nevinsky.abyssus.core.JsonProcessor
import net.nevinsky.abyssus.projectView.RenderAsset
import net.nevinsky.abyssus.runtime.ecs.EcsWriter
import net.nevinsky.abyssus.runtime.ecs.component.NameComponent
import net.nevinsky.abyssus.runtime.ecs.component.PositionComponent
import net.nevinsky.abyssus.runtime.ecs.component.TypeComponent
import net.nevinsky.abyssus.editor.content.Vec3

/** The result of adding an entity: [entityId] is the new entity's id when [result] is [EditResult.Changed]. */
data class AddedEntity(val result: EditResult, val entityId: String? = null)

/**
 * Adds a project's model or terrain to a scene as a new entity: a name (`Model <id>` / `Terrain <id>`), a type
 * (`OBJECT` / `TERRAIN`), a position and a render component naming the asset. Edits only the JSON tree; callers write it
 * through `editSceneJson` as one undoable command. [SceneEntities] inserts it in either scene layout.
 */
object AssetEntities {
    private val nodes = JsonNodeFactory.instance
    private val writer = EcsWriter(JsonProcessor().mapper)

    /** Whether an entity can be added to [root]: the same rules as for a light. */
    fun canAdd(root: JsonNode): Boolean = SceneEntities.canAdd(root)

    /**
     * Adds [asset] at [point]. A terrain of [terrainSize] (world units; null when unknown) is centred on [point]; without
     * a size its corner is put there.
     */
    fun add(root: JsonNode, asset: RenderAsset, point: Vec3, terrainSize: Float? = null): AddedEntity {
        val rejected = AddedEntity(EditResult.Rejected(AbyssusBundle.message("componentSceneUnreadable")))
        val terrain = asset.type == "TERRAIN"
        if (asset.type !in setOf("MODEL", "TERRAIN") || listOf(point.x, point.y, point.z).any { !it.isFinite() }) return rejected
        val half = if (terrain && terrainSize != null && terrainSize.isFinite() && terrainSize > 0f) terrainSize / 2f else 0f
        val id = SceneEntities.insert(root) { id ->
            val components = nodes.objectNode()
            val name = AbyssusBundle.message(if (terrain) "assetEntityTerrainName" else "assetEntityModelName", id)
            components.set<JsonNode>("NameComponent", writer.writeComponent(NameComponent(name)))
            components.set<JsonNode>("TypeComponent", writer.writeComponent(TypeComponent(if (terrain) TypeComponent.Type.TERRAIN else TypeComponent.Type.OBJECT)))
            components.set<JsonNode>("PositionComponent", writer.writeComponent(PositionComponent(point.x - half, point.y, point.z - half)))
            val renderable = nodes.objectNode().put("kind", "asset").put("shaderKey", if (terrain) "terrain" else "defaultShader")
            renderable.putObject("asset").put("type", asset.type).put("assetName", asset.name)
            components.set<JsonNode>("RenderComponent", nodes.objectNode().set<JsonNode>("renderable", renderable))
            components
        } ?: return rejected
        return AddedEntity(EditResult.Changed, id)
    }
}
