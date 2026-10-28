/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.editor.scene

import com.badlogic.ashley.core.Component
import com.badlogic.gdx.math.Quaternion
import com.badlogic.gdx.math.Vector3
import com.badlogic.ashley.core.Entity
import net.nevinsky.abyssus.lib.core.dto.SceneDto
import net.nevinsky.abyssus.lib.core.ecs.component.*
import net.nevinsky.abyssus.lib.core.editor.content.*
import net.nevinsky.abyssus.lib.core.editor.water.WaterCodec
import net.nevinsky.abyssus.lib.core.editor.water.WaterSurface

/**
 * A terrain entity's foliage: [foliageName] is the foliage asset folder its `FoliageComponent.assetName` holds as a
 * string, [terrainName] the terrain asset folder the entity shows, and [transform] the entity's own transform.
 */
data class FoliagePlacement(
    val entityId: String,
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
    val entityPositions: Map<String, Vector3> = emptyMap(),
    /** The ids of entities whose `type` is `HANDLE` (the direction handles of look-at lights). */
    val handleIds: Set<String> = emptySet(),
    /** The path arrangements: the lines the view shows handles on when one is selected. */
    val arrangements: List<ArrangementPlacement> = emptyList(),
    /** The saved water surfaces in file order, invalid ones included (they are rows, not drawn). */
    val water: List<WaterSurface> = emptyList(),
) {
    // Built on first use: the lists never change, and every lookup of the selected entity would scan them otherwise.
    private val modelsById by lazy(LazyThreadSafetyMode.NONE) { models.associateBy { it.entityId } }
    private val terrainsById by lazy(LazyThreadSafetyMode.NONE) { terrains.associateBy { it.entityId } }
    private val lightsById by lazy(LazyThreadSafetyMode.NONE) { lights.associateBy { it.entityId } }
    private val camerasById by lazy(LazyThreadSafetyMode.NONE) { cameras.associateBy { it.entityId } }

    /** The model placed as entity [id], or null. */
    fun model(id: String): AssetPlacement? = modelsById[id]

    /** The terrain placed as entity [id], or null. */
    fun terrain(id: String): AssetPlacement? = terrainsById[id]

    /** The light placed as entity [id], or null. */
    fun light(id: String): LightPlacement? = lightsById[id]

    /** The camera placed as entity [id], or null. */
    fun camera(id: String): CameraPlacement? = camerasById[id]

    /** The id of the direction handle (a `HANDLE` entity) [light] looks at, or null when it looks at none. */
    fun aimHandleOf(light: LightPlacement): String? = light.lookAtId?.takeIf { it in handleIds }
}

/** What [scene] places, read from its typed entities. */
fun sceneContentOf(scene: SceneDto): SceneContent {
    val entities = scene.ecs.map { (id, entity) -> decode(id.toString(), entity) }
    val skybox = scene.skyboxName?.takeIf { scene.skyboxEnabled && it.isNotBlank() }
    return PlacementMapper().map(entities, skybox).copy(water = WaterCodec().read(scene.water).surfaces)
}

/**
 * Reads the entity's typed components, so the view and the Properties panel show the same values, defaults included.
 * A disabled entity is present as data (its position still answers a look-at) and absent as behavior; a component
 * disabled on its own is skipped.
 */
private fun decode(id: String, entity: Entity): DecodedEntity {
    val header = entity.getComponent(HeaderComponent::class.java)
    val disabled = header?.disabledComponents.orEmpty()
    fun <C : Component> any(type: Class<C>): C? = entity.getComponent(type)
    fun <C : Component> active(type: Class<C>): C? = any(type)?.takeIf { type !in disabled }
    val name = any(NameComponent::class.java)?.name?.takeIf { it.isNotBlank() } ?: id
    val position = any(PositionComponent::class.java)
    if (header?.entityEnabled == false) return DecodedEntity(id, name, position, position != null, null, null, null, null)
    val type = header?.type
    val lightDisabled = type?.name?.startsWith("LIGHT") == true && LightComponent::class.java in disabled
    val render = active(RenderComponent::class.java)
    val asset = render?.takeIf { it.type != null && it.assetName.isNotEmpty() }?.let { DecodedAsset(it.type!!.name, it.assetName) }
    return DecodedEntity(
        id,
        name,
        position,
        position != null,
        if (lightDisabled) null else type,
        active(CameraComponent::class.java),
        active(LightComponent::class.java),
        asset,
        active(FoliageComponent::class.java)?.assetName,
        active(PathArrangementComponent::class.java),
    )
}

/** The libGDX forward axis (-Z) rotated by [q]. */
fun forwardOf(q: Quaternion): Vector3 {
    val len = kotlin.math.sqrt(q.x * q.x + q.y * q.y + q.z * q.z + q.w * q.w)
    if (len < 1e-6f) return Vector3(0f, 0f, -1f)
    val x = q.x / len
    val y = q.y / len
    val z = q.z / len
    val w = q.w / len
    // q * (0, 0, -1) * q^-1
    return Vector3(0f - 2f * (x * z + w * y), 0f - 2f * (y * z - w * x), 0f - (1f - 2f * (x * x + y * y)))
}

/**
 * The unit direction from [from] toward [to], or null when the two are (almost) the same point. Shared by
 * look-at lights and look-at cameras so the two can't drift apart.
 */
fun aimDirection(from: Vector3, to: Vector3): Vector3? {
    val dx = to.x - from.x
    val dy = to.y - from.y
    val dz = to.z - from.z
    val len2 = dx * dx + dy * dy + dz * dz
    if (len2 < 1e-12f || !len2.isFinite()) return null
    val len = kotlin.math.sqrt(len2)
    return Vector3(dx / len, dy / len, dz / len)
}
