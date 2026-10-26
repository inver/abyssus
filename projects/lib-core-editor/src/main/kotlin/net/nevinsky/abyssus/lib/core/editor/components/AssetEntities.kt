/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.lib.core.editor.components

import com.badlogic.gdx.math.Vector3
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.JsonNodeFactory
import net.nevinsky.abyssus.lib.core.assets.BaseMeta
import net.nevinsky.abyssus.lib.core.assets.MetaType
import net.nevinsky.abyssus.lib.core.ecs.component.NameComponent
import net.nevinsky.abyssus.lib.core.ecs.component.PositionComponent
import net.nevinsky.abyssus.lib.core.ecs.component.RenderComponent
import net.nevinsky.abyssus.lib.core.ecs.component.TypeComponent
import net.nevinsky.abyssus.lib.core.editor.EditorMessages
import net.nevinsky.abyssus.lib.core.editor.document.SceneEntityTree
import net.nevinsky.abyssus.lib.core.editor.ecs.EcsWriter
import net.nevinsky.abyssus.lib.core.io.JsonProcessor
import org.slf4j.helpers.NOPLogger

/** The result of adding an entity: [entityId] is the new entity's id when [result] is [EditResult.Changed]. */
data class AddedEntity(val result: EditResult, val entityId: String? = null)

/**
 * Adds a project's model or terrain to a scene as a new entity: a name (`Model <id>` / `Terrain <id>`), a type
 * (`OBJECT` / `TERRAIN`), a position and a render component naming the asset. Edits only the JSON tree; callers write it
 * through `editSceneJson` as one undoable command. [SceneEntityTree] inserts it in either scene layout.
 */
class AssetEntities(private val messages: EditorMessages) {
    private val nodes = JsonNodeFactory.instance
    private val writer = EcsWriter(JsonProcessor(NOPLogger.NOP_LOGGER))

    /** Whether an entity can be added to [root]: the same rules as for a light. */
    fun canAdd(root: JsonNode): Boolean = SceneEntityTree(root).canAdd()

    /**
     * Adds [asset] at [point]. A terrain of [terrainSize] (world units; null when unknown) is centred on [point]; without
     * a size its corner is put there.
     */
    fun add(root: JsonNode, asset: BaseMeta, point: Vector3, terrainSize: Float? = null): AddedEntity {
        val rejected = AddedEntity(EditResult.Rejected(messages.message("componentSceneUnreadable")))
        if (asset.type !in setOf(MetaType.MODEL, MetaType.TERRAIN) || listOf(
                point.x,
                point.y,
                point.z
            ).any { !it.isFinite() }
        ) return rejected
        val half =
            if (asset.isTerrain() && terrainSize != null && terrainSize.isFinite() && terrainSize > 0f) terrainSize / 2f else 0f
        val id = SceneEntityTree(root).insert { id ->
            val components = nodes.objectNode()
            val name = messages.message(if (asset.isTerrain()) "assetEntityTerrainName" else "assetEntityModelName", id)
            components.set<JsonNode>("NameComponent", writer.writeComponent(NameComponent(name)))
            components.set<JsonNode>(
                "TypeComponent",
                writer.writeComponent(TypeComponent(if (asset.isTerrain()) TypeComponent.Type.TERRAIN else TypeComponent.Type.OBJECT))
            )
            components.set<JsonNode>(
                "PositionComponent",
                writer.writeComponent(PositionComponent(point.x - half, point.y, point.z - half))
            )
            components.set<JsonNode>(
                "RenderComponent",
                writer.writeComponent(
                    RenderComponent(
                        if (asset.isTerrain()) "terrain" else "defaultShader",
                        asset.type,
                        asset.name
                    )
                ),
            )
            components
        } ?: return rejected
        return AddedEntity(EditResult.Changed, id)
    }
}
