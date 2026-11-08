/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.editor.hierarchy

import com.badlogic.gdx.math.Matrix4
import com.badlogic.gdx.math.Quaternion
import com.badlogic.gdx.math.Vector3
import net.nevinsky.abyssus.lib.core.assets.MetaType
import net.nevinsky.abyssus.lib.core.dto.SceneDto
import net.nevinsky.abyssus.lib.core.ecs.component.ParentComponent
import net.nevinsky.abyssus.lib.core.ecs.component.PositionComponent
import net.nevinsky.abyssus.lib.core.ecs.component.RenderComponent
import net.nevinsky.abyssus.lib.core.editor.content.PlacementTransform
import net.nevinsky.abyssus.lib.core.hierarchy.LocalPose
import net.nevinsky.abyssus.lib.core.hierarchy.NodeTransform
import net.nevinsky.abyssus.lib.core.hierarchy.NodeTransforms
import net.nevinsky.abyssus.lib.core.hierarchy.ParentLink
import net.nevinsky.abyssus.lib.core.hierarchy.ResolvedPoses
import net.nevinsky.abyssus.lib.core.hierarchy.WorldPoses
import net.nevinsky.abyssus.lib.core.hierarchy.columnRotation
import net.nevinsky.abyssus.lib.core.hierarchy.columnScale
import net.nevinsky.abyssus.lib.core.ecs.component.localPoseOf
import net.nevinsky.abyssus.lib.core.hierarchy.noNodeTransforms
import net.nevinsky.abyssus.lib.core.ecs.component.parentLinkOf

/**
 * Resolves the world poses of a scene's entities from its DTO, through `ParentComponent` links (OpenSpec
 * `scene-entity-hierarchy`), with the same resolver a game uses. [nodes] answers the rest transforms of model nodes a
 * child is bound to; without it a node-bound child sits on its parent, silently.
 */
class SceneHierarchy(private val nodes: NodeTransforms = noNodeTransforms()) {
    fun resolve(scene: SceneDto): ResolvedPoses {
        val locals = HashMap<Long, LocalPose>()
        val parents = HashMap<Long, ParentLink>()
        val disabled = HashSet<Long>()
        for ((id, entity) in scene.ecs) {
            entity.component(PositionComponent::class.java)?.let { locals[id] = localPoseOf(it) }
            entity.component(ParentComponent::class.java)?.let(::parentLinkOf)?.let { parents[id] = it }
            if (!entity.isActive()) {
                disabled += id
            }
        }
        return WorldPoses().resolve(scene.ecs.keys, locals, parents, nodes, disabled)
    }
}

/**
 * Node rest transforms read from model assets: the parent's `RenderComponent` names a `MODEL` asset, and [models]
 * answers that asset's node table (node name to rest transform within the model), or null while it is not read yet.
 */
fun modelNodeTransforms(scene: SceneDto, models: (String) -> Map<String, Matrix4>?): NodeTransforms =
    NodeTransforms { parentId, node ->
        val render = scene.entity(parentId)?.activeComponent(RenderComponent::class.java)
        val asset = render?.takeIf { it.type == MetaType.MODEL && it.assetName.isNotEmpty() }?.assetName
        when {
            asset == null -> NodeTransform.Missing
            else -> models(asset)
                ?.let { table -> table[node]?.let { NodeTransform.Found(it) } ?: NodeTransform.Missing }
                ?: NodeTransform.NotLoaded
        }
    }

/** [m] as position, rotation and scale (the best fit when it is sheared). */
fun placementOf(m: Matrix4): PlacementTransform {
    val scale = columnScale(m, Vector3())
    return PlacementTransform(m.getTranslation(Vector3()), columnRotation(m, scale, Quaternion()), scale)
}
