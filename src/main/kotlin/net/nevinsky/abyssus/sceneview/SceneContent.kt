/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.sceneview

import net.nevinsky.abyssus.editor.content.Vec3
import net.nevinsky.abyssus.editor.content.Quat
import net.nevinsky.abyssus.editor.content.AssetPlacement
import net.nevinsky.abyssus.editor.content.LightPlacement
import net.nevinsky.abyssus.editor.content.CameraPlacement

import com.fasterxml.jackson.databind.JsonNode
import net.nevinsky.abyssus.core.assets.runCatchingKeepingCancellation
import net.nevinsky.abyssus.core.scene.Scene
import net.nevinsky.abyssus.runtime.ecs.EcsUtils.Companion.LIGHT_RANGE
import net.nevinsky.abyssus.runtime.ecs.EcsUtils.Companion.CAMERA_NEAR
import net.nevinsky.abyssus.runtime.ecs.EcsUtils.Companion.CAMERA_FAR
import net.nevinsky.abyssus.runtime.ecs.EcsUtils.Companion.CAMERA_FOV
import net.nevinsky.abyssus.runtime.ecs.EcsUtils.Companion.LIGHT_CONE_ANGLE
import net.nevinsky.abyssus.runtime.ecs.EcsUtils.Companion.LIGHT_EDGE_SOFTNESS
import net.nevinsky.abyssus.SceneEcsPaths
import com.badlogic.ashley.core.Component
import net.nevinsky.abyssus.core.JsonProcessor
import net.nevinsky.abyssus.ecs.scene.ComponentReader
import org.slf4j.helpers.NOPLogger
import net.nevinsky.abyssus.runtime.ecs.component.PositionComponent
import net.nevinsky.abyssus.runtime.ecs.component.TypeComponent
import net.nevinsky.abyssus.runtime.ecs.component.CameraComponent
import net.nevinsky.abyssus.runtime.ecs.component.LightComponent
import net.nevinsky.abyssus.runtime.opt
import net.nevinsky.abyssus.runtime.text

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

        fun of(scene: Scene): SceneContent {
            val entities = SceneEcsPaths().entitiesIn(scene.ecs)?.properties().orEmpty().mapNotNull { (id, entity) ->
                val components = SceneEcsPaths().componentsOf(entity) ?: return@mapNotNull null
                runCatchingKeepingCancellation { decode(id, components) }.getOrNull()
            }
            val skybox = scene.skyboxName?.takeIf { scene.skyboxEnabled == true && it.isNotBlank() }
            return PlacementMapper().map(entities, skybox)
        }

        /**
         * Reads the entity's components through the runtime loader the Properties panel uses, so both show the same values,
         * defaults included. The render asset is read as the file names it, whichever delegate class holds it.
         */
        private fun decode(id: String, components: JsonNode): DecodedEntity {
            val position = components.opt("PositionComponent")
            val asset = components.opt("RenderComponent")?.opt("renderable")?.opt("asset")
            val assetType = asset?.text("type")
            val assetName = asset?.text("assetName")
            return DecodedEntity(
                id,
                SceneEcsPaths().entityName(components, id),
                position?.let { read<PositionComponent>(it) },
                position?.opt("localPosition") != null,
                components.opt("TypeComponent")?.let { read<TypeComponent>(it)?.type },
                components.opt("CameraComponent")?.let { read<CameraComponent>(it) },
                components.opt("LightComponent")?.let { read<LightComponent>(it) },
                if (assetType != null && assetName != null) DecodedAsset(assetType, assetName) else null,
            )
        }

        /** Binds components the way a scene load does, so the view and the Properties panel show the same values. */
        private val components = ComponentReader(JsonProcessor().mapper, { _, _ -> null }, NOPLogger.NOP_LOGGER)

        /** A component that cannot be bound is left out, so one bad value does not hide the entity. */
        private inline fun <reified C : Component> read(node: JsonNode): C? =
            runCatchingKeepingCancellation { components.read(C::class.java, node) }.getOrNull()

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
