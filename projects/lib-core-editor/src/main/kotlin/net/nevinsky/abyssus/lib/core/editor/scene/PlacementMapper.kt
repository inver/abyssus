/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.gdx.editor.scene

import net.nevinsky.abyssus.lib.gdx.util.EcsUtils.Companion.NO_ENTITY
import net.nevinsky.abyssus.lib.gdx.editor.content.Vec3
import net.nevinsky.abyssus.lib.gdx.editor.content.Rgba
import net.nevinsky.abyssus.lib.gdx.editor.content.Quat
import net.nevinsky.abyssus.lib.gdx.editor.content.PlacementTransform
import net.nevinsky.abyssus.lib.gdx.editor.content.AssetPlacement
import net.nevinsky.abyssus.lib.gdx.editor.content.LightKind
import net.nevinsky.abyssus.lib.gdx.editor.content.LightPlacement
import net.nevinsky.abyssus.lib.gdx.editor.content.CameraPlacement

import net.nevinsky.abyssus.lib.gdx.ecs.component.CameraComponent
import net.nevinsky.abyssus.lib.gdx.ecs.component.LightComponent
import net.nevinsky.abyssus.lib.gdx.dto.LightDto
import net.nevinsky.abyssus.lib.gdx.ecs.component.PositionComponent
import net.nevinsky.abyssus.lib.gdx.ecs.component.TypeComponent

/** The asset a `RenderComponent` shows: `renderable.asset.type` and `assetName`. */
class DecodedAsset(val type: String, val name: String)

/**
 * What the component codecs decoded from one entity; null where it lacks the component. [hasLocalPosition] tells
 * whether the file's `PositionComponent` holds a `localPosition` at all (a camera then takes it over its own position).
 */
class DecodedEntity(
    val id: String,
    val name: String,
    val position: PositionComponent?,
    val hasLocalPosition: Boolean,
    val type: TypeComponent.Type?,
    val camera: CameraComponent?,
    val light: LightComponent?,
    val asset: DecodedAsset?,
)

/**
 * Turns decoded entities into the view's placements. Pure: no Swing, GL or platform code, so the mapping that decides
 * what the scene view draws is unit-tested without them.
 */
class PlacementMapper {
    fun map(entities: List<DecodedEntity>, skybox: String?): SceneContent {
        val models = mutableListOf<AssetPlacement>()
        val terrains = mutableListOf<AssetPlacement>()
        val lights = mutableListOf<LightPlacement>()
        val cameras = mutableListOf<CameraPlacement>()
        val positions = linkedMapOf<String, Vec3>()
        val handleIds = mutableSetOf<String>()
        for (entity in entities) {
            val transform = entity.position?.let(::transformOf) ?: PlacementTransform.IDENTITY
            if (entity.position != null) positions[entity.id] = transform.position
            if (entity.type == TypeComponent.Type.HANDLE) handleIds += entity.id
            val asset = entity.asset
            when {
                asset != null && asset.type == "MODEL" -> models += AssetPlacement(entity.id, asset.name, transform)
                asset != null && asset.type == "TERRAIN" -> terrains += AssetPlacement(entity.id, asset.name, transform)
                entity.camera != null -> cameras += cameraOf(entity, entity.camera, transform)
                else -> lightOf(entity, transform)?.let { lights += it }
            }
        }
        val resolved = lights.map { light ->
            // A point light shines every way, so only directional and spot lights face a look-at target.
            val target = light.lookAtId?.takeIf { light.kind != LightKind.POINT }?.let(positions::get)
            val direction = if (target != null) aimDirection(light.position, target) ?: forwardOf(light.rotation) else forwardOf(light.rotation)
            light.copy(direction = direction)
        }
        return SceneContent(models, terrains, resolved, skybox, cameras, positions, handleIds)
    }

    private fun transformOf(position: PositionComponent) = PlacementTransform(
        Vec3(position.localPosition.x, position.localPosition.y, position.localPosition.z),
        Quat(position.localRotation.x, position.localRotation.y, position.localRotation.z, position.localRotation.w),
        Vec3(position.localScale.x, position.localScale.y, position.localScale.z),
    )

    /** The position is the entity's own `localPosition`, else the camera's. */
    private fun cameraOf(entity: DecodedEntity, component: CameraComponent, transform: PlacementTransform): CameraPlacement {
        val camera = component.camera
        val position = if (entity.hasLocalPosition) transform.position else Vec3(camera.position.x, camera.position.y, camera.position.z)
        return CameraPlacement(
            entity.id, entity.name, position, Vec3(camera.direction.x, camera.direction.y, camera.direction.z), lookAtOf(entity),
            camera.near, camera.far, camera.fieldOfView, transform.rotation,
        )
    }

    /** The id of the entity [entity] looks at, as the file names it; null for none. */
    private fun lookAtOf(entity: DecodedEntity): String? =
        entity.position?.lookAtId?.takeIf { it != NO_ENTITY }?.toString()

    /** A `LIGHT_<KIND>` entity, or one that has a `LightComponent` and no other kind (a directional light). */
    private fun lightOf(entity: DecodedEntity, transform: PlacementTransform): LightPlacement? {
        val type = entity.type
        val kind = when {
            type == null || !type.name.startsWith("LIGHT") -> if (entity.light != null) LightKind.DIRECTIONAL else return null
            type == TypeComponent.Type.LIGHT_POINT -> LightKind.POINT
            type == TypeComponent.Type.LIGHT_SPOT -> LightKind.SPOT
            else -> LightKind.DIRECTIONAL
        }
        val light: LightDto = entity.light?.light ?: LightDto()
        return LightPlacement(
            entity.id, kind, Rgba(light.color.r, light.color.g, light.color.b, 1f), light.intensity.coerceAtLeast(0f),
            transform.position, forwardOf(transform.rotation), light.range, transform.rotation,
            light.coneAngle, light.edgeSoftness, lookAtOf(entity),
        )
    }
}
