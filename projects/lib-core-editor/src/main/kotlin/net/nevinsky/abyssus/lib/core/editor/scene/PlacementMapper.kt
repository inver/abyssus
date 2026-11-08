/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.editor.scene

import com.badlogic.gdx.math.Matrix4
import com.badlogic.gdx.math.Quaternion
import com.badlogic.gdx.math.Vector3
import net.nevinsky.abyssus.lib.core.defaults.NO_ENTITY
import net.nevinsky.abyssus.lib.core.ecs.component.CameraComponent
import net.nevinsky.abyssus.lib.core.ecs.component.LightComponent
import net.nevinsky.abyssus.lib.core.ecs.component.PathArrangementComponent
import net.nevinsky.abyssus.lib.core.ecs.component.PositionComponent
import net.nevinsky.abyssus.lib.core.editor.content.ArrangementPlacement
import net.nevinsky.abyssus.lib.core.editor.content.AssetPlacement
import net.nevinsky.abyssus.lib.core.editor.content.CameraPlacement
import net.nevinsky.abyssus.lib.core.editor.content.LightKind
import net.nevinsky.abyssus.lib.core.editor.content.LightPlacement
import net.nevinsky.abyssus.lib.core.editor.content.PlacementTransform
import net.nevinsky.abyssus.lib.core.editor.hierarchy.placementOf
import net.nevinsky.abyssus.lib.core.hierarchy.ResolvedPoses
import net.nevinsky.abyssus.lib.core.format.EntityType
import net.nevinsky.abyssus.lib.core.light.lightKindOf
import net.nevinsky.abyssus.lib.core.light.lightPlacementOf

/**
 * The asset a `RenderComponent` shows: its `type` and `assetName`, and for a model the `meshId` of the one mesh drawn.
 */
class DecodedAsset(val type: String, val name: String, val meshId: String? = null)

/**
 * What the component codecs decoded from one entity; null where it lacks the component. [hasLocalPosition] tells
 * whether the file's `PositionComponent` holds a `localPosition` at all (a camera then takes it over its own position).
 */
class DecodedEntity(
    val id: Long,
    val name: String,
    val position: PositionComponent?,
    val hasLocalPosition: Boolean,
    val type: EntityType?,
    val camera: CameraComponent?,
    val light: LightComponent?,
    val asset: DecodedAsset?,
    /** The string `FoliageComponent.assetName` the entity carries, or null when it carries none. */
    val foliage: String? = null,
    /** The entity's path arrangement, or null. */
    val arrangement: PathArrangementComponent? = null,
    /** Whether the entity is an enabled `DUMMY`. */
    val dummy: Boolean = false,
)

/**
 * Turns decoded entities into the view's placements. Pure: no Swing, GL or platform code, so the mapping that decides
 * what the scene view draws is unit-tested without them.
 */
class PlacementMapper {
    /**
     * Maps [entities] at the world poses of [poses] (each entity's own `PositionComponent` as its world pose when it
     * is null); [nodes] are the model nodes children are bound to.
     */
    fun map(
        entities: List<DecodedEntity>,
        skybox: String?,
        poses: ResolvedPoses? = null,
        nodes: Map<Long, String> = emptyMap(),
    ): SceneContent {
        val models = mutableListOf<AssetPlacement>()
        val terrains = mutableListOf<AssetPlacement>()
        val foliages = mutableListOf<FoliagePlacement>()
        val lights = mutableListOf<LightPlacement>()
        val cameras = mutableListOf<CameraPlacement>()
        val arrangements = mutableListOf<ArrangementPlacement>()
        val dummies = mutableListOf<DummyPlacement>()
        val positions = linkedMapOf<Long, Vector3>()
        val handleIds = mutableSetOf<Long>()
        val locals = linkedMapOf<Long, PlacementTransform>()
        for (entity in entities) {
            entity.position?.let { locals[entity.id] = transformOf(it) }
            val world = poses?.world?.get(entity.id)
            // a root, or a child of an identity frame, keeps its stored values exactly (no decomposition round trip)
            val framed = poses != null && entity.id in poses.parents &&
                !poses.parentWorldOf(entity.id).`val`.contentEquals(IDENTITY_VALUES)
            val framedWorld = world?.takeIf { framed }?.let(::placementOf)
            val transform = framedWorld ?: entity.position?.let(::transformOf) ?: PlacementTransform.IDENTITY
            if (entity.position != null) {
                positions[entity.id] = transform.position
            }
            if (entity.type == EntityType.HANDLE) {
                handleIds += entity.id
            }
            val asset = entity.asset
            entity.arrangement?.let { arrangements += ArrangementPlacement(entity.id, inWorld(it, world)) }
            if (entity.dummy) {
                dummies += DummyPlacement(entity.id, entity.name, transform, poses?.parents?.get(entity.id))
                continue
            }
            when {
                asset != null && asset.type == "MODEL" ->
                    models += AssetPlacement(entity.id, asset.name, transform, asset.meshId)
                asset != null && asset.type == "TERRAIN" -> {
                    terrains += AssetPlacement(entity.id, asset.name, transform)
                    entity.foliage?.let { foliages += FoliagePlacement(entity.id, it, asset.name, transform) }
                }
                entity.camera != null -> cameras += cameraOf(entity, entity.camera, transform)
                else -> lightOf(entity, transform)?.let { lights += it }
            }
        }
        val resolved = lights.map { light ->
            // A point light shines every way, so only directional and spot lights face a look-at target.
            val target = light.lookAtId?.takeIf { light.kind != LightKind.POINT }?.let(positions::get)
            val direction =
                if (target != null) {
                    aimDirection(light.position, target) ?: forwardOf(light.rotation)
                } else {
                    forwardOf(light.rotation)
                }
            light.copy(direction = direction)
        }
        return SceneContent(
            models = models,
            terrains = terrains,
            foliages = foliages,
            lights = resolved,
            skybox = skybox,
            cameras = cameras,
            entityPositions = positions,
            handleIds = handleIds,
            arrangements = arrangements,
            dummies = dummies,
            hierarchy = hierarchyOf(poses, locals, nodes),
        )
    }

    private fun hierarchyOf(
        resolved: ResolvedPoses?,
        locals: Map<Long, PlacementTransform>,
        nodes: Map<Long, String>,
    ): ContentHierarchy {
        resolved ?: return ContentHierarchy(locals = locals)
        return ContentHierarchy(
            parents = resolved.parents,
            parentFrames = resolved.parents.keys.associateWith(resolved::parentWorldOf),
            worlds = resolved.world.mapValues { (_, m) -> Matrix4(m) },
            locals = locals,
            nodes = nodes,
            disabledByAncestor = resolved.disabled.filterTo(HashSet()) { id ->
                resolved.ancestorsOf(id).any { it in resolved.disabled }
            },
            warnings = resolved.warnings,
        )
    }

    /**
     * [arrangement] with its points in world space under the arrangement entity's [world] matrix; the component itself
     * when there is no frame or it is the identity (every arrangement without a `PositionComponent`).
     */
    private fun inWorld(arrangement: PathArrangementComponent, world: Matrix4?): PathArrangementComponent {
        if (world == null || world.`val`.contentEquals(IDENTITY_VALUES)) {
            return arrangement
        }
        return PathArrangementComponent().also {
            it.points = arrangement.points.map { p -> Vector3(p).mul(world) }.toMutableList()
            it.closed = arrangement.closed
            it.asset = arrangement.asset
            it.spacing = arrangement.spacing
            it.offset = arrangement.offset
            it.sides = arrangement.sides
            it.alignToPath = arrangement.alignToPath
            it.yawOffset = arrangement.yawOffset
            it.slots = arrangement.slots
        }
    }

    private fun transformOf(position: PositionComponent) =
        PlacementTransform(
            Vector3(position.localPosition.x, position.localPosition.y, position.localPosition.z),
            Quaternion(
                position.localRotation.x,
                position.localRotation.y,
                position.localRotation.z,
                position.localRotation.w,
            ),
            Vector3(position.localScale.x, position.localScale.y, position.localScale.z),
        )

    /** The position is the entity's own `localPosition`, else the camera's. */
    private fun cameraOf(
        entity: DecodedEntity,
        component: CameraComponent,
        transform: PlacementTransform,
    ): CameraPlacement {
        val camera = component.camera
        val position =
            if (entity.hasLocalPosition) {
                transform.position
            } else {
                Vector3(camera.position.x, camera.position.y, camera.position.z)
            }
        return CameraPlacement(
            entity.id,
            entity.name,
            position,
            Vector3(camera.direction.x, camera.direction.y, camera.direction.z),
            lookAtOf(entity),
            camera.near,
            camera.far,
            camera.fieldOfView,
            transform.rotation,
        )
    }

    /** The id of the entity [entity] looks at, as the file names it; null for none. */
    private fun lookAtOf(entity: DecodedEntity): Long? = entity.position?.lookAtId?.takeIf { it != NO_ENTITY }?.toLong()

    /** A `LIGHT_<KIND>` entity, or one that has a `LightComponent` and no other kind (a directional light). */
    private fun lightOf(entity: DecodedEntity, transform: PlacementTransform): LightPlacement? {
        val kind = lightKindOf(entity.type, entity.light != null) ?: return null
        return lightPlacementOf(
            entity.id,
            kind,
            entity.light?.light,
            transform.position,
            transform.rotation,
            lookAtOf(entity),
        )
    }
}

private val IDENTITY_VALUES = Matrix4().`val`.copyOf()
