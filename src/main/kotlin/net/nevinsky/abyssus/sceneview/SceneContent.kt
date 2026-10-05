/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.sceneview

import com.fasterxml.jackson.databind.JsonNode
import net.nevinsky.abyssus.assets.runCatchingKeepingCancellation
import net.nevinsky.abyssus.runtime.scene.SceneDto
import net.nevinsky.abyssus.runtime.ecs.component.LIGHT_RANGE
import net.nevinsky.abyssus.runtime.ecs.component.CAMERA_NEAR
import net.nevinsky.abyssus.runtime.ecs.component.CAMERA_FAR
import net.nevinsky.abyssus.runtime.ecs.component.CAMERA_FOV
import net.nevinsky.abyssus.runtime.ecs.component.LIGHT_CONE_ANGLE
import net.nevinsky.abyssus.runtime.ecs.component.LIGHT_EDGE_SOFTNESS
import net.nevinsky.abyssus.runtime.ecs.scene.SceneEcsPaths
import net.nevinsky.abyssus.runtime.ecs.scene.ComponentCodecs
import net.nevinsky.abyssus.runtime.ecs.component.PositionComponent
import net.nevinsky.abyssus.runtime.ecs.component.TypeComponent
import net.nevinsky.abyssus.runtime.ecs.component.CameraComponent
import net.nevinsky.abyssus.runtime.ecs.component.LightComponent

/** [w] is 1 for the identity rotation, which native scenes leave out of the file together with the other default fields. */
data class Quat(val x: Float, val y: Float, val z: Float, val w: Float) {
    companion object {
        val IDENTITY = Quat(0f, 0f, 0f, 1f)
    }
}

/** Native `PositionComponent`: position 0, identity rotation and unit scale unless the file says otherwise. */
data class PlacementTransform(val position: Vec3, val rotation: Quat, val scale: Vec3) {
    companion object {
        val IDENTITY = PlacementTransform(Vec3(0f, 0f, 0f), Quat.IDENTITY, Vec3(1f, 1f, 1f))
    }
}

/** An entity showing the asset folder [assetName]. [entityId] is its key under `ecs/entities`; picking reports it back. */
data class AssetPlacement(val entityId: String, val assetName: String, val transform: PlacementTransform)

enum class LightKind { DIRECTIONAL, POINT, SPOT }

/** [direction] is the unit vector the light shines along (directional and spot); [position] matters for point and spot. */
data class LightPlacement(
    val entityId: String,
    val kind: LightKind,
    val color: Rgba,
    val intensity: Float,
    val position: Vec3,
    val direction: Vec3,
    val range: Float = LIGHT_RANGE,
    val rotation: Quat = Quat.IDENTITY,
    val coneAngle: Float = LIGHT_CONE_ANGLE,
    val edgeSoftness: Float = LIGHT_EDGE_SOFTNESS,
    /** The `PositionComponent.lookAtId` of the light, or null when it does not name a target. */
    val lookAtId: String? = null,
)

/**
 * A camera entity. [direction] is the view direction from `viewPointPosition` (as in the file, not yet normalized);
 * it is overridden by the position of [lookAtId] while that resolves. [name] is the entity's `NameComponent` name,
 * or its id when unnamed.
 */
data class CameraPlacement(
    val entityId: String,
    val name: String,
    val position: Vec3,
    val direction: Vec3,
    val lookAtId: String?,
    val near: Float = CAMERA_NEAR,
    val far: Float = CAMERA_FAR,
    val fieldOfView: Float = CAMERA_FOV,
    val rotation: Quat = Quat.IDENTITY,
)

/** What a scene shows besides its environment. */
data class SceneContent(
    val models: List<AssetPlacement> = emptyList(),
    val terrains: List<AssetPlacement> = emptyList(),
    val lights: List<LightPlacement> = emptyList(),
    /** The skybox asset folder to draw, or null when the scene has no enabled, named skybox. */
    val skybox: String? = null,
    val cameras: List<CameraPlacement> = emptyList(),
    /** `localPosition` of every entity with a `PositionComponent` (entity id to position), for look-at targets. */
    val entityPositions: Map<String, Vec3> = emptyMap(),
    /** The ids of entities whose `TypeComponent.type` is `HANDLE` (the direction handles of look-at lights). */
    val handleIds: Set<String> = emptySet(),
) {
    /** The id of the direction handle (a `HANDLE` entity) [light] looks at, or null when it looks at none. */
    fun aimHandleOf(light: LightPlacement): String? = light.lookAtId?.takeIf { it in handleIds }

    companion object {
        val EMPTY = SceneContent()

        fun of(scene: SceneDto): SceneContent {
            val codecs = ComponentCodecs()
            val entities = SceneEcsPaths().entitiesIn(scene.ecs)?.properties().orEmpty().mapNotNull { (id, entity) ->
                val components = SceneEcsPaths().componentsOf(entity) ?: return@mapNotNull null
                runCatchingKeepingCancellation { decode(codecs, id, components) }.getOrNull()
            }
            val skybox = scene.skyboxName?.takeIf { scene.skyboxEnabled == true && it.isNotBlank() }
            return PlacementMapper().map(entities, skybox)
        }

        /**
         * Reads the entity's components through the same codecs the Properties panel uses, so both show the same values,
         * defaults included. The render asset is read as the file names it, whichever delegate class holds it.
         */
        private fun decode(codecs: ComponentCodecs, id: String, components: JsonNode): DecodedEntity {
            val position = components.opt("PositionComponent")
            val asset = components.opt("RenderComponent")?.opt("renderable")?.opt("asset")
            val assetType = asset?.text("type")
            val assetName = asset?.text("assetName")
            return DecodedEntity(
                id,
                SceneEcsPaths().entityName(components, id),
                position?.let { codecs.read<PositionComponent>("PositionComponent", it) },
                position?.opt("localPosition") != null,
                components.opt("TypeComponent")?.let { codecs.read<TypeComponent>("TypeComponent", it).type },
                components.opt("CameraComponent")?.let { codecs.read<CameraComponent>("CameraComponent", it) },
                components.opt("LightComponent")?.let { codecs.read<LightComponent>("LightComponent", it) },
                if (assetType != null && assetName != null) DecodedAsset(assetType, assetName) else null,
            )
        }

        /** The libGDX forward axis (-Z) rotated by [q]. */
        internal fun forward(q: Quat): Vec3 {
            val len = kotlin.math.sqrt(q.x * q.x + q.y * q.y + q.z * q.z + q.w * q.w)
            if (len < 1e-6f) return Vec3(0f, 0f, -1f)
            val x = q.x / len
            val y = q.y / len
            val z = q.z / len
            val w = q.w / len
            // q * (0, 0, -1) * q^-1
            return Vec3(0f - 2f * (x * z + w * y), 0f - 2f * (y * z - w * x), 0f - (1f - 2f * (x * x + y * y)))
        }

        /**
         * The unit direction from [from] toward [to], or null when the two are (almost) the same point. Shared by
         * look-at lights and look-at cameras so the two can't drift apart.
         */
        internal fun aim(from: Vec3, to: Vec3): Vec3? {
            val dx = to.x - from.x
            val dy = to.y - from.y
            val dz = to.z - from.z
            val len2 = dx * dx + dy * dy + dz * dz
            if (len2 < 1e-12f || !len2.isFinite()) return null
            val len = kotlin.math.sqrt(len2)
            return Vec3(dx / len, dy / len, dz / len)
        }
    }
}
