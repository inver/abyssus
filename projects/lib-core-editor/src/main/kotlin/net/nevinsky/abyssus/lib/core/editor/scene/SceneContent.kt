/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.editor.scene

import com.badlogic.ashley.core.Component
import com.badlogic.gdx.math.Quaternion
import com.badlogic.gdx.math.Vector3
import net.nevinsky.abyssus.lib.core.dto.EntityDto
import net.nevinsky.abyssus.lib.core.dto.SceneDto
import net.nevinsky.abyssus.lib.core.ecs.component.*
import net.nevinsky.abyssus.lib.core.editor.content.*

/**
 * A terrain entity's foliage: [foliageName] is the foliage asset folder its `FoliageComponent.assetName` holds as a
 * string, [terrainName] the terrain asset folder the entity shows, and [transform] the entity's own transform.
 */
data class FoliagePlacement(
    val entityId: Long,
    val foliageName: String,
    val terrainName: String,
    val transform: PlacementTransform,
)

/** What a scene shows besides its environment. */
data class SceneContent(
    val models: List<AssetPlacement> = emptyList(),
    val terrains: List<AssetPlacement> = emptyList(),
    /** The foliage of terrain entities whose `FoliageComponent.assetName` is a string. */
    val foliages: List<FoliagePlacement> = emptyList(),
    val lights: List<LightPlacement> = emptyList(),
    /** The skybox asset folder to draw, or null when the scene has no enabled, named skybox. */
    val skybox: String? = null,
    val cameras: List<CameraPlacement> = emptyList(),
    /** `localPosition` of every entity with a `PositionComponent` (entity id to position), for look-at targets. */
    val entityPositions: Map<Long, Vector3> = emptyMap(),
    /** The ids of entities whose `type` is `HANDLE` (the direction handles of look-at lights). */
    val handleIds: Set<Long> = emptySet(),
    /** The path arrangements: the lines the view shows handles on when one is selected. */
    val arrangements: List<ArrangementPlacement> = emptyList(),
) {
    // Built on first use: the lists never change, and every lookup of the selected entity would scan them otherwise.
    private val modelsById by lazy(LazyThreadSafetyMode.NONE) { models.associateBy { it.entityId } }
    private val terrainsById by lazy(LazyThreadSafetyMode.NONE) { terrains.associateBy { it.entityId } }
    private val lightsById by lazy(LazyThreadSafetyMode.NONE) { lights.associateBy { it.entityId } }
    private val camerasById by lazy(LazyThreadSafetyMode.NONE) { cameras.associateBy { it.entityId } }

    /** The model placed as entity [id], or null. */
    fun model(id: Long): AssetPlacement? = modelsById[id]

    /** The terrain placed as entity [id], or null. */
    fun terrain(id: Long): AssetPlacement? = terrainsById[id]

    /** The light placed as entity [id], or null. */
    fun light(id: Long): LightPlacement? = lightsById[id]

    /** The camera placed as entity [id], or null. */
    fun camera(id: Long): CameraPlacement? = camerasById[id]

    /** The id of the direction handle (a `HANDLE` entity) [light] looks at, or null when it looks at none. */
    fun aimHandleOf(light: LightPlacement): Long? = light.lookAtId?.takeIf { it in handleIds }
}

/** What [scene] places, read from its typed entities. */
fun sceneContentOf(scene: SceneDto): SceneContent {
    val entities = scene.ecs.map { (id, entity) -> decode(id, entity) }
    val skybox = scene.skyboxName?.takeIf { scene.skyboxEnabled && it.isNotBlank() }
    return PlacementMapper().map(entities, skybox)
}

/**
 * Reads the entity's typed components, so the view and the Properties panel show the same values, defaults included. A
 * disabled entity is present as data (its position still answers a look-at) and absent as behavior; a component
 * disabled on its own is skipped.
 */
private fun decode(id: Long, entity: EntityDto): DecodedEntity {
    val name = entity.component(NameComponent::class.java)?.name?.takeIf { it.isNotBlank() } ?: id.toString()
    val position = entity.component(PositionComponent::class.java)
    if (!entity.isActive()) {
        return DecodedEntity(id, name, position, position != null, null, null, null, null)
    }
    val type = entity.component(HeaderComponent::class.java)?.type
    val lightDisabled =
        type?.name?.startsWith("LIGHT") == true && LightComponent::class.java in entity.header().disabledComponents
    val render = entity.activeComponent(RenderComponent::class.java)
    val asset =
        render
            ?.takeIf { it.type != null && it.assetName.isNotEmpty() }
            ?.let { DecodedAsset(it.type!!.name, it.assetName, it.meshId?.takeIf(String::isNotEmpty)) }
    return DecodedEntity(
        id,
        name,
        position,
        position != null,
        if (lightDisabled) {
            null
        } else {
            type
        },
        entity.activeComponent(CameraComponent::class.java),
        entity.activeComponent(LightComponent::class.java),
        asset,
        entity.activeComponent(FoliageComponent::class.java)?.assetName,
        entity.activeComponent(PathArrangementComponent::class.java),
    )
}

/** The libGDX forward axis (-Z) rotated by [q]. */
fun forwardOf(q: Quaternion): Vector3 = net.nevinsky.abyssus.lib.core.light.forwardOf(q)

/**
 * The unit direction from [from] toward [to], or null when the two are (almost) the same point. Shared by look-at
 * lights and look-at cameras so the two can't drift apart.
 */
fun aimDirection(from: Vector3, to: Vector3): Vector3? {
    val direction = Vector3(to).sub(from)
    val len2 = direction.len2()
    if (len2 < 1e-12f || !len2.isFinite()) {
        return null
    }
    return direction.nor()
}
