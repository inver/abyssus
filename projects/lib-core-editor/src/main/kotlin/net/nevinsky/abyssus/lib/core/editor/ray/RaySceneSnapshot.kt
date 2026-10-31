/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.lib.core.editor.ray

import net.nevinsky.abyssus.lib.core.editor.scene.LightSet
import net.nevinsky.abyssus.lib.core.editor.scene.SceneRenderParams
import net.nevinsky.abyssus.lib.core.editor.content.storageName
import net.nevinsky.abyssus.lib.core.editor.content.toMatrix
import net.nevinsky.abyssus.lib.core.editor.pick.ScenePreview

import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.graphics.Color


import com.badlogic.gdx.graphics.PerspectiveCamera
import net.nevinsky.abyssus.lib.core.editor.ray.snapshot.RayModelMesh
import net.nevinsky.abyssus.lib.core.assets.runCatchingKeepingCancellation
import net.nevinsky.abyssus.lib.raytracing.*
import net.nevinsky.abyssus.lib.core.editor.pick.DragResult

/** Immutable render-thread camera capture. The actual camera preserves orbit and look-through lens/up conventions. */
data class RayCameraSnapshot(
    val position: Vector3, val direction: Vector3, val up: Vector3,
    val fieldOfView: Float, val near: Float, val far: Float,
    val projection: List<Float>, val view: List<Float>, val activeCameraId: String?,
) {
    fun rayCamera() = RaySliceCamera(position.list(), direction.list(), up.list(), fieldOfView, near, far)
}

/** Render-thread pose copy. Skin matrices are the runtime NodePart.bones in weight-index order. */
class RayModelPose(
    val revision: Long,
    nodeTransforms: Map<String, FloatArray>,
    boneTransforms: Map<String, List<FloatArray>>
) {
    private val nodes = nodeTransforms.mapValues { it.value.copyOf() }
    private val bones = boneTransforms.mapValues { (_, matrices) -> matrices.map { it.copyOf() } }
    fun node(id: String?) = nodes[id]?.copyOf()

    /** Key matches node id + slash + mesh-part id, including the owning node for repeated parts. */
    fun bones(nodeId: String?, meshPartId: String?) = bones["$nodeId/$meshPartId"]?.map { it.copyOf() }
}

data class RaySnapshotLimits(
    val maxInstances: Int = 4096,
    val maxTriangles: Int = 2_000_000,
    val maxBytes: Long = 512L * 1024 * 1024
) {
    init {
        require(maxInstances > 0 && maxTriangles > 0 && maxBytes > 0)
    }
}

class RaySceneFrame internal constructor(
    val scene: RaySceneSnapshot, val camera: RayCameraSnapshot, val projectId: String?,
    internal val assets: Map<String, Any>, internal val topology: List<String>,
    internal val transforms: Map<String, List<Float>>, internal val poses: Map<String, Long>,
    internal val environmentRevision: Long,
    val settings: SceneRaySettings = SceneRaySettings(),
)

sealed interface RaySceneConversion {
    data class Ready(val frame: RaySceneFrame) : RaySceneConversion
    data class Preparing(val assets: List<String>) : RaySceneConversion
    data class Fallback(val reason: RaySceneFallback, val detail: String? = null) : RaySceneConversion
}



enum class RaySceneChange { STRUCTURE, TRANSFORM, POSE, CAMERA, LIGHT, MATERIAL, ENVIRONMENT }
data class RaySceneDiff(val changes: Set<RaySceneChange>) {
    val rebuild: Boolean get() = RaySceneChange.STRUCTURE in changes
    val resetsHistory: Boolean get() = changes.isNotEmpty()
}

fun raySceneDiff(previous: RaySceneFrame?, next: RaySceneFrame): RaySceneDiff {
    if (previous == null) return RaySceneDiff(setOf(RaySceneChange.STRUCTURE))
    val changes = mutableSetOf<RaySceneChange>()
    if (previous.projectId != next.projectId || previous.assets != next.assets || previous.topology != next.topology)
        changes += RaySceneChange.STRUCTURE
    if (previous.assets != next.assets || previous.scene.materials != next.scene.materials || previous.settings != next.settings) changes += RaySceneChange.MATERIAL
    if (previous.transforms != next.transforms) changes += RaySceneChange.TRANSFORM
    if (previous.poses != next.poses) changes += RaySceneChange.POSE
    if (previous.camera != next.camera) changes += RaySceneChange.CAMERA
    if (previous.scene.lights != next.scene.lights) changes += RaySceneChange.LIGHT
    if (previous.scene.environment != next.scene.environment || previous.scene.fog != next.scene.fog ||
        previous.environmentRevision != next.environmentRevision || previous.scene.sunVisibility != next.scene.sunVisibility)
        changes += RaySceneChange.ENVIRONMENT
    return RaySceneDiff(changes)
}


/** CPU-only conversion from the same preview and selected lights used by raster. Never reads GL mesh/texture handles. */
class RaySceneSnapshots(private val limits: RaySnapshotLimits = RaySnapshotLimits()) {
    // Converts the assets; it keeps converted meshes and textures per retained asset object, so unchanged assets keep their
    // identity from one capture to the next and the backend reuses its acceleration structures.
    private val converter = RaySceneConverter(limits)

    // Halved copies of oversized textures, per source object, so a scene over the texel budget reduces them once, not every frame.
    private val halved = java.util.WeakHashMap<RayTexture, RayTexture>()
    private fun halvedTexture(texture: RayTexture): RayTexture? =
        halved[texture] ?: texture.halved()?.also { halved[texture] = it }
    fun capture(
        params: SceneRenderParams, camera: PerspectiveCamera, lightSet: LightSet,
        assets: RaySceneAssetState, preview: Map<Long, DragResult> = emptyMap(), activeCameraId: String? = null,
        environment: RayEnvironment? = null, environmentTextures: List<RayTexture> = emptyList(),
        poses: Map<Long, RayModelPose> = emptyMap(),
        deform: ((RayModelMesh, List<FloatArray>) -> FloatArray)? = null,
        environmentRevision: Long = 0,
        sunVisibility: RaySunVisibility? = null,
    ): RaySceneConversion {
        val settings = params.rayTracing
        if (assets is RaySceneAssetState.Preparing) return RaySceneConversion.Preparing(assets.assets)
        if (assets is RaySceneAssetState.Failed) return RaySceneConversion.Fallback(
            RaySceneFallback.ASSET_FAILURE,
            assets.failures.keys.joinToString()
        )
        assets as RaySceneAssetState.Ready
        val content = ScenePreview().apply(params.content, preview)
        val missing =
            content.models.mapNotNull { if (it.storageName !in assets.models) "model:${it.storageName}" else null } +
                    content.terrains.mapNotNull { if (it.assetName !in assets.terrains) "terrain:${it.assetName}" else null }
        if (missing.isNotEmpty()) return RaySceneConversion.Preparing(missing.distinct())
        var topology: List<String> = emptyList()
        var transforms: Map<String, List<Float>> = emptyMap()
        val result = runCatchingKeepingCancellation {
            val scene = synchronized(converter) {
                converter.reset(
                    environmentTextures,
                    limits.copy(maxInstances = minOf(limits.maxInstances, settings.maxInstances)),
                    deform,
                )
                for (placement in content.models) {
                    val added = converter.model(
                        placement.entityId.toString(), placement.transform.toMatrix(),
                        assets.models.getValue(placement.storageName), poses[placement.entityId],
                    )
                    require(added > 0) { "Displayed model has no supported mesh parts" }
                }
                for (placement in content.terrains) converter.terrain(
                    placement.entityId.toString(), placement.transform.toMatrix(), assets.terrains.getValue(placement.assetName),
                )
                converter.checkBounds()
                topology = converter.topology.toList()
                transforms = converter.transforms.toMap()
                RaySceneSnapshot(
                    converter.meshes.toList(),
                    converter.instances.toList(),
                    converter.materials.toList(),
                    fitTextureTexels(converter.textures.toList(), halve = ::halvedTexture)
                        ?: throw RaySceneLimitException("The scene textures cannot be reduced to $RAY_MAX_TEXTURE_TEXELS texels"),
                    lights(lightSet),
                    environment ?: RayEnvironment(
                        ambient = params.ambient?.ray() ?: RayColor(0f, 0f, 0f),
                        background = params.clear.ray()
                    ),
                    params.fog?.let { RayFog(it.color.ray(), it.density, it.gradient) },
                    sunVisibility)
            }
            val cameraSnapshot = RayCameraSnapshot(
                camera.position.cpy().ray(),
                camera.direction.cpy().ray(),
                camera.up.cpy().ray(),
                camera.fieldOfView,
                camera.near,
                camera.far,
                camera.projection.`val`.toList(),
                camera.view.`val`.toList(),
                activeCameraId
            )
            RaySceneFrame(
                scene,
                cameraSnapshot,
                params.projectDir?.absoluteFile?.toPath()?.normalize()?.toString(),
                assets.models.mapKeys { "model:${it.key}" } + assets.terrains.mapKeys { "terrain:${it.key}" },
                topology,
                transforms,
                poses.entries.associate { it.key.toString() to it.value.revision },
                environmentRevision,
                settings
            )
        }
        return result.fold({ RaySceneConversion.Ready(it) }, {
            RaySceneConversion.Fallback(
                if (it is RaySceneLimitException) RaySceneFallback.RESOURCE_LIMIT else RaySceneFallback.UNSUPPORTED_GEOMETRY,
                it.message
            )
        })
    }

    private fun lights(set: LightSet): List<RayLight> = set.directional.map {
        RayLight(it.entityId.toString(), RayLightKind.DIRECTIONAL, it.color.ray(), it.direction.ray(), it.position.ray())
    } + set.point.map {
        RayLight(
            it.entityId.toString(),
            RayLightKind.POINT,
            it.color.ray(it.range),
            position = it.position.ray(),
            range = it.range
        )
    } + set.spot.map {
        RayLight(
            it.entityId.toString(),
            RayLightKind.SPOT,
            it.color.ray(it.range),
            it.direction.ray(),
            it.position.ray(),
            it.range,
            it.cone.angle / 2f,
            it.cone.softness
        )
    }
}

/** A scene bound the ray view cannot exceed; the message says which and by how much. */
internal class RaySceneLimitException(message: String) : IllegalArgumentException(message)

private fun Vector3.ray() = Vector3(x, y, z)
private fun Color.ray(scale: Float = 1f) = RayColor(r * scale, g * scale, b * scale, a)
private fun Vector3.list() = listOf(x, y, z)
