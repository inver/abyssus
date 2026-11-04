/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.lib.core.editor.components

import com.badlogic.gdx.math.Vector3
import net.nevinsky.abyssus.lib.core.assets.BaseMeta
import net.nevinsky.abyssus.lib.core.assets.MetaType
import net.nevinsky.abyssus.lib.core.dto.SceneDto
import net.nevinsky.abyssus.lib.core.ecs.component.NameComponent
import net.nevinsky.abyssus.lib.core.ecs.component.PositionComponent
import net.nevinsky.abyssus.lib.core.ecs.component.RenderComponent
import net.nevinsky.abyssus.lib.core.editor.EditorMessages
import net.nevinsky.abyssus.lib.core.editor.util.Util
import net.nevinsky.abyssus.lib.core.format.EntityType

/** The result of adding an entity: [entityId] is the new entity's id when [result] is [EditResult.Changed]. */
data class AddedEntity(val result: EditResult, val entityId: Long? = null)

/**
 * Adds a project's model or terrain to a scene as a new entity: a name (`Model <id>` / `Terrain <id>`), a type
 * (`OBJECT` / `TERRAIN`), a position and a render component naming the asset. Edits only the [SceneDto]; callers write
 * it through `editSceneJson` as one undoable command.
 */
class AssetEntities(private val messages: EditorMessages) {
    /**
     * Adds [asset] at [point]. A terrain of [terrainSize] (world units; null when unknown) is centred on [point];
     * without a size its corner is put there. A model with [meshId] draws only that mesh; the id is ignored for a
     * terrain.
     */
    fun add(
        scene: SceneDto,
        asset: BaseMeta,
        point: Vector3,
        terrainSize: Float? = null,
        meshId: String? = null,
    ): AddedEntity {
        if (asset.type !in setOf(MetaType.MODEL, MetaType.TERRAIN) || !Util.isFinite(point)) {
            return AddedEntity(EditResult.Rejected(messages.message("componentSceneUnreadable")))
        }
        val half =
            if (asset.isTerrain() && terrainSize != null && terrainSize.isFinite() && terrainSize > 0f) {
                terrainSize / 2f
            } else {
                0f
            }
        val render =
            RenderComponent(
                    if (asset.isTerrain()) {
                        "terrain"
                    } else {
                        "defaultShader"
                    },
                    asset.type,
                    asset.name,
                )
                .also {
                    if (!asset.isTerrain()) {
                        it.meshId = meshId?.takeIf(String::isNotEmpty)
                    }
                }
        val id =
            scene.add(
                if (asset.isTerrain()) {
                    EntityType.TERRAIN
                } else {
                    EntityType.OBJECT
                }
            ) { id ->
                val name =
                    messages.message(
                        if (asset.isTerrain()) {
                            "assetEntityTerrainName"
                        } else {
                            "assetEntityModelName"
                        },
                        id.toString(),
                    )
                listOf(NameComponent(name), PositionComponent(point.x - half, point.y, point.z - half), render)
            }
        return AddedEntity(EditResult.Changed, id)
    }
}
