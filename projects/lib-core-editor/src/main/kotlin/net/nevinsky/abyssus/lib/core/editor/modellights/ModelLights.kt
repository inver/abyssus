/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.editor.modellights

import com.badlogic.gdx.math.Matrix4
import net.nevinsky.abyssus.lib.core.assets.MetaType
import net.nevinsky.abyssus.lib.core.dto.SceneDto
import net.nevinsky.abyssus.lib.core.ecs.component.NameComponent
import net.nevinsky.abyssus.lib.core.ecs.component.PositionComponent
import net.nevinsky.abyssus.lib.core.ecs.component.RenderComponent
import net.nevinsky.abyssus.lib.core.editor.EditorMessages
import net.nevinsky.abyssus.lib.core.editor.components.EditResult
import net.nevinsky.abyssus.lib.core.editor.components.LightEntities

/** The lights one use added: their [ids] in order, and whether lamps were [merged] to stay under the maximum. */
data class AddedLamps(val result: EditResult, val ids: List<Long> = emptyList(), val merged: Boolean = false)

/**
 * Create Lights From Model: adds one point light per lamp of a material of the model that entity draws. Edits only the
 * [SceneDto]; callers write it through `editSceneDocument` as one undoable command.
 */
class ModelLights(private val messages: EditorMessages, private val lights: LightEntities = LightEntities(messages)) {
    /** The model asset and mesh the render component of entity [entityId] names; null if it draws no model. */
    fun modelOf(scene: SceneDto, entityId: Long): Pair<String, String?>? {
        val render = scene.component(entityId, RenderComponent::class.java) ?: return null
        if (render.type != MetaType.MODEL || render.assetName.isEmpty()) {
            return null
        }
        return render.assetName to render.meshId?.takeIf(String::isNotEmpty)
    }

    fun add(
        scene: SceneDto,
        entityId: Long,
        geometry: MaterialGeometry,
        max: Int = LAMP_LIGHT_MAX,
    ): AddedLamps {
        val (assetName, _) =
            modelOf(scene, entityId) ?: return AddedLamps(EditResult.Rejected(messages.message("lampsNotModel")))
        val pieces = lampPieces(geometry)
        if (pieces.isEmpty()) {
            return AddedLamps(EditResult.Rejected(messages.message("lampsNoGeometry")))
        }
        val capped = capPieces(pieces, max)
        val transform =
            scene.component(entityId, PositionComponent::class.java)?.getTransform()?.let(::Matrix4) ?: Matrix4()
        val entityName =
            scene.component(entityId, NameComponent::class.java)?.name?.takeIf(String::isNotEmpty) ?: assetName
        val color = lampColor(geometry.emissive)
        val ids =
            capped.pieces.mapIndexed { index, piece ->
                val placed = piece.placed(transform)
                lights.addPoint(
                    scene,
                    messages.message("lightLampName", entityName, index + 1),
                    placed.center,
                    color,
                    LAMP_INTENSITY,
                    lampRange(placed),
                )
            }
        return AddedLamps(EditResult.Changed, ids, capped.merged)
    }
}
