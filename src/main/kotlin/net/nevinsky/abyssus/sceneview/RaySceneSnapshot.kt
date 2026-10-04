/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.sceneview

import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.PerspectiveCamera
import com.badlogic.gdx.graphics.VertexAttributes
import com.badlogic.gdx.graphics.g3d.model.data.ModelTexture
import com.badlogic.gdx.math.Matrix4
import net.nevinsky.abyssus.assets.SPLAT_LAYERS
import net.nevinsky.abyssus.assets.model.*
import net.nevinsky.abyssus.assets.terrain.RayTerrainSnapshot
import net.nevinsky.abyssus.assets.runCatchingKeepingCancellation
import net.nevinsky.abyssus.raytracing.*
import net.nevinsky.abyssus.sceneview.gizmo.DragResult

/** Immutable render-thread camera capture. The actual camera preserves orbit and look-through lens/up conventions. */
data class RayCameraSnapshot(
    val position: RayVec3, val direction: RayVec3, val up: RayVec3,
    val fieldOfView: Float, val near: Float, val far: Float,
    val projection: List<Float>, val view: List<Float>, val activeCameraId: String?,
) {
    fun rayCamera() = RaySliceCamera(position.list(), direction.list(), up.list(), fieldOfView, near, far)
}

/** Render-thread pose copy. Skin matrices are the runtime NodePart.bones in weight-index order. */
class RayModelPose(val revision: Long, nodeTransforms: Map<String, FloatArray>, boneTransforms: Map<String, List<FloatArray>>) {
    private val nodes = nodeTransforms.mapValues { it.value.copyOf() }
    private val bones = boneTransforms.mapValues { (_, matrices) -> matrices.map { it.copyOf() } }
    fun node(id: String?) = nodes[id]?.copyOf()
    /** Key matches node id + slash + mesh-part id, including the owning node for repeated parts. */
    fun bones(nodeId: String?, meshPartId: String?) = bones["$nodeId/$meshPartId"]?.map { it.copyOf() }
}

data class RaySnapshotLimits(val maxInstances: Int = 1024, val maxTriangles: Int = 2_000_000, val maxBytes: Long = 512L * 1024 * 1024) {
    init { require(maxInstances > 0 && maxTriangles > 0 && maxBytes > 0) }
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
enum class RaySceneFallback { ASSET_FAILURE, RESOURCE_LIMIT, UNSUPPORTED_GEOMETRY }

enum class RaySceneChange { STRUCTURE, TRANSFORM, POSE, CAMERA, LIGHT, MATERIAL, ENVIRONMENT }
data class RaySceneDiff(val changes: Set<RaySceneChange>) {
    val rebuild: Boolean get() = RaySceneChange.STRUCTURE in changes
    val resetsHistory: Boolean get() = changes.isNotEmpty()
    companion object {
        fun between(previous: RaySceneFrame?, next: RaySceneFrame): RaySceneDiff {
            if (previous == null) return RaySceneDiff(setOf(RaySceneChange.STRUCTURE))
            val changes = mutableSetOf<RaySceneChange>()
            if (previous.projectId != next.projectId || previous.assets != next.assets || previous.topology != next.topology)
                changes += RaySceneChange.STRUCTURE
            if (previous.assets != next.assets || previous.scene.materials != next.scene.materials || previous.settings != next.settings) changes += RaySceneChange.MATERIAL
            if (previous.transforms != next.transforms) changes += RaySceneChange.TRANSFORM
            if (previous.poses != next.poses) changes += RaySceneChange.POSE
            if (previous.camera != next.camera) changes += RaySceneChange.CAMERA
            if (previous.scene.lights != next.scene.lights) changes += RaySceneChange.LIGHT
            if (previous.scene.environment != next.scene.environment || previous.scene.fog != next.scene.fog || previous.environmentRevision != next.environmentRevision)
                changes += RaySceneChange.ENVIRONMENT
            return RaySceneDiff(changes)
        }
    }
}

/** CPU-only conversion from the same preview and selected lights used by raster. Never reads GL mesh/texture handles. */
class RaySceneSnapshots(private val limits: RaySnapshotLimits = RaySnapshotLimits()) {
    // Converted static meshes and textures per retained asset object, so unchanged assets keep their identity from one
    // capture to the next and the backend reuses its acceleration structures. Weak: a replaced asset drops its entry.
    private val shared = java.util.WeakHashMap<Any, MutableMap<String, Any>>()
    fun capture(
        params: SceneRenderParams, camera: PerspectiveCamera, lightSet: LightSet,
        assets: RaySceneAssetState, preview: Map<String, DragResult> = emptyMap(), activeCameraId: String? = null,
        environment: RayEnvironment? = null, environmentTextures: List<RayTexture> = emptyList(),
        poses: Map<String, RayModelPose> = emptyMap(),
        deform: ((RayModelMesh, List<FloatArray>) -> FloatArray)? = null,
        environmentRevision: Long = 0,
    ): RaySceneConversion {
        if (params.raySettings.settings == null) return RaySceneConversion.Fallback(RaySceneFallback.UNSUPPORTED_GEOMETRY,
            "Malformed rayTracing settings: ${params.raySettings.errors.keys.joinToString()}")
        if (assets is RaySceneAssetState.Preparing) return RaySceneConversion.Preparing(assets.assets)
        if (assets is RaySceneAssetState.Failed) return RaySceneConversion.Fallback(RaySceneFallback.ASSET_FAILURE, assets.failures.keys.joinToString())
        assets as RaySceneAssetState.Ready
        val content = ScenePreview.apply(params.content, preview)
        val missing = content.models.mapNotNull { if (it.assetName !in assets.models) "model:${it.assetName}" else null } +
            content.terrains.mapNotNull { if (it.assetName !in assets.terrains) "terrain:${it.assetName}" else null }
        if (missing.isNotEmpty()) return RaySceneConversion.Preparing(missing.distinct())
        val result = runCatchingKeepingCancellation {
            val builder = Builder(limits, environmentTextures, deform) { shared.getOrPut(it) { HashMap() } }
            for (placement in content.models) {
                val render=params.ecs?.path("entities")?.path(placement.entityId)?.path("components")?.get("RenderComponent")
                builder.model(placement, assets.models.getValue(placement.assetName), poses[placement.entityId],render)
            }
            for (placement in content.terrains) builder.terrain(placement, assets.terrains.getValue(placement.assetName))
            builder.checkBounds()
            val scene = RaySceneSnapshot(builder.meshes, builder.instances, builder.materials, builder.textures,
                lights(lightSet), environment ?: RayEnvironment(ambient = params.ambient?.ray() ?: RayColor(0f, 0f, 0f), background = params.clear.ray()),
                params.fog?.let { RayFog(it.color.ray(), it.density, it.gradient) })
            val cameraSnapshot = RayCameraSnapshot(camera.position.toVec3().ray(), camera.direction.toVec3().ray(), camera.up.toVec3().ray(),
                camera.fieldOfView, camera.near, camera.far, camera.projection.`val`.toList(), camera.view.`val`.toList(), activeCameraId)
            RaySceneFrame(scene, cameraSnapshot, params.projectDir?.absoluteFile?.toPath()?.normalize()?.toString(),
                assets.models.mapKeys { "model:${it.key}" } + assets.terrains.mapKeys { "terrain:${it.key}" }, builder.topology.toList(),
                builder.transforms.toMap(), poses.mapValues { it.value.revision }, environmentRevision, params.raySettings.settings)
        }
        return result.fold({ RaySceneConversion.Ready(it) }, {
            RaySceneConversion.Fallback(if (it is RaySceneLimitException) RaySceneFallback.RESOURCE_LIMIT else RaySceneFallback.UNSUPPORTED_GEOMETRY, it.message)
        })
    }

    private class Builder(
        private val limits: RaySnapshotLimits, initialTextures: List<RayTexture>, private val deform: ((RayModelMesh, List<FloatArray>) -> FloatArray)?,
        private val cacheFor: (Any) -> MutableMap<String, Any>,
    ) {
        private class CachedMesh(val mesh: RayMesh, val bytes: Long)
        private var cache: MutableMap<String, Any> = HashMap()
        val meshes = mutableListOf<RayMesh>(); val instances = mutableListOf<RayInstance>()
        val materials = mutableListOf<RayMaterial>(); val textures = initialTextures.toMutableList()
        val topology = mutableListOf<String>(); val transforms = linkedMapOf<String, List<Float>>()
        private val geometry = mutableMapOf<String, Int>(); private val materialIds = mutableMapOf<String, Int>()
        private var bytes = initialTextures.sumOf { it.width.toLong() * it.height * 16 }; private var triangles = 0L

        fun model(placement: AssetPlacement, asset: RayModelSnapshot, pose: RayModelPose?,render: com.fasterxml.jackson.databind.JsonNode?) {
            val codec=RayMaterialOverrides()
            val identities=asset.materials.map { RayMaterialIdentity(it.id,it.pbr) }
            val optics=render?.let(codec::read)
            require(optics?.errors.isNullOrEmpty()) { "Malformed optical overrides: ${optics?.errors?.keys?.joinToString()}" }
            val unresolved=render?.let { codec.unresolved(it,identities) }.orEmpty()
            require(unresolved.isEmpty()) { "Unresolved or ineligible optical material IDs: ${unresolved.joinToString()}" }
            val firstInstance = instances.size
            cache = cacheFor(asset)
            val prefix = "model:${placement.assetName}"
            val textureIds = asset.images.mapValues { (name, image) -> texture("$prefix:$name", image, RayTextureSampler()) }
            fun material(id: String?): Int = materialIds.getOrPut("$prefix:$id" + if(optics?.values?.containsKey(id)==true) ":${placement.entityId}" else "") {
                val source = requireNotNull(asset.materials.firstOrNull { it.id == id }) { "Missing material $id" }
                val material=convertMaterial(source,textureIds)
                val override=optics?.values?.get(id)
                materials.add(if(override==null) material else material.copy(transmission=override.transmission.toFloat(),ior=override.ior.toFloat()))
                materials.lastIndex
            }
            fun visit(node: RayModelNode, parent: Matrix4, path: String) {
                val local = PlacementTransform(Vec3(node.translation.x, node.translation.y, node.translation.z),
                    Quat(node.rotation.x, node.rotation.y, node.rotation.z, node.rotation.w), Vec3(node.scale.x, node.scale.y, node.scale.z)).toMatrix()
                val global = pose?.node(node.id)?.let(::Matrix4) ?: Matrix4(parent).mul(local)
                for ((ordinal, part) in node.parts.withIndex()) {
                    val skin = pose?.bones(node.id, part.meshPartId)
                    require(part.boneBindTransforms.isEmpty() || (skin != null && deform != null)) { "Skinned model requires the displayed animation pose and deformation" }
                    val source = requireNotNull(asset.meshes.firstOrNull { mesh -> mesh.parts.any { it.id == part.meshPartId } }) { "Missing mesh part ${part.meshPartId}" }
                    val indices = source.parts.first { it.id == part.meshPartId }
                    require(indices.primitiveType == GL20.GL_TRIANGLES) { "Only triangle mesh parts are supported" }
                    val key = "$prefix:${part.meshPartId}" + if (skin != null) ":${placement.entityId}:$path/$ordinal" else ""
                    val mesh = geometry.getOrPut(key) {
                        fun build(): CachedMesh {
                            val values = if (skin != null) requireNotNull(deform)(source, skin) else source.vertices()
                            val stride = source.vertexSizeBytes / 4
                            val position = channel(source, values, stride, VertexAttributes.Usage.Position, 3)
                            val normal = channelOrNull(source, values, stride, VertexAttributes.Usage.Normal, 3)
                            val uv = channelOrNull(source, values, stride, VertexAttributes.Usage.TextureCoordinates, 2)
                            val tangent = tangent(source, values, stride, normal)
                            val copiedIndices = indices.indices()
                            val size = (position.size.toLong() + (normal?.size ?: 0) + (uv?.size ?: 0) + (tangent?.size ?: 0) + copiedIndices.size) * 4
                            return CachedMesh(RayMesh(key, position, copiedIndices, normal, uv, tangent), size)
                        }
                        // skinned geometry depends on the instance's pose; static geometry is converted once per asset
                        val built = if (skin != null) build() else cache.getOrPut("mesh:$key") { build() } as CachedMesh
                        bytes += built.bytes
                        triangles += built.mesh.indexCount / 3
                        meshes.add(built.mesh); meshes.lastIndex
                    }
                    val id = "${placement.entityId}/$path/$ordinal"
                    val world = placement.transform.toMatrix()
                    if (skin == null) world.mul(global)
                    instance(id, mesh, material(part.materialId), world)
                    topology += "$id:$key:${part.materialId}"
                }
                node.children.forEachIndexed { index, child -> visit(child, global, "$path/$index") }
            }
            asset.nodes.forEachIndexed { index, node -> visit(node, Matrix4(), index.toString()) }
            require(instances.size > firstInstance) { "Displayed model has no supported mesh parts" }
        }

        fun terrain(placement: AssetPlacement, asset: RayTerrainSnapshot) {
            val key = "terrain:${placement.assetName}"
            cache = cacheFor(asset)
            val mesh = geometry.getOrPut(key) {
                val built = cache.getOrPut("mesh:$key") {
                    val values = asset.vertices(); val count = values.size / 8
                    val positions = FloatArray(count * 3) { values[it / 3 * 8 + it % 3] }
                    val normals = FloatArray(count * 3) { values[it / 3 * 8 + 3 + it % 3] }
                    val uvs = FloatArray(count * 2) { values[it / 2 * 8 + 6 + it % 2] }
                    val indices = asset.indices()
                    CachedMesh(RayMesh(key, positions, indices, normals, uvs), (positions.size.toLong() + normals.size + uvs.size + indices.size) * 4)
                } as CachedMesh
                triangles += built.mesh.indexCount / 3; bytes += built.bytes
                meshes.add(built.mesh); meshes.lastIndex
            }
            val material = materialIds.getOrPut(key) {
                val splat = asset.splatMap?.let { RayTextureBinding(texture("$key:splat", it.image, it.sampler)) }
                val layers = SPLAT_LAYERS.map { name -> asset.layers[name]?.let { RayTextureBinding(texture("$key:$name", it.image, it.sampler)) } }
                materials.add(RayMaterial(kind = RayMaterialKind.TERRAIN, terrain = RayTerrainMaterial(splat, layers, asset.size.toFloat()))); materials.lastIndex
            }
            instance(placement.entityId, mesh, material, placement.transform.toMatrix()); topology += "${placement.entityId}:$key"
        }

        private fun instance(id: String, mesh: Int, material: Int, matrix: Matrix4) {
            instances.add(RayInstance(id, mesh, material, matrix.`val`)); transforms[id] = matrix.`val`.toList(); checkBounds()
        }
        /** Says which bound was exceeded and by how much, so the toolbar can tell the user what to reduce. */
        fun checkBounds() {
            if (instances.size > limits.maxInstances) throw RaySceneLimitException("The scene needs ${instances.size} model parts and terrain; the limit is ${limits.maxInstances}")
            if (triangles > limits.maxTriangles) throw RaySceneLimitException("The scene has $triangles triangles; the limit is ${limits.maxTriangles}")
            if (bytes > limits.maxBytes) throw RaySceneLimitException("The scene needs ${bytes / (1024 * 1024)} MB of geometry and textures; the limit is ${limits.maxBytes / (1024 * 1024)} MB")
        }
        private fun texture(id: String, image: RayModelImage, sampler: RayTextureSampler): Int {
            val existing = textures.indexOfFirst { it.id == id }
            if (existing >= 0) return existing
            bytes += image.byteSize; checkBounds()
            // 8-bit texels stay 8-bit all the way to the backend: a 2048x2048 image is 16 MB, not 67 MB of floats
            textures.add(cache.getOrPut("texture:$id:${sampler.wrapU}:${sampler.wrapV}") {
                RayTexture(id, image.width, image.height, image.rgba(),
                    if (sampler.wrapU == RayTextureWrap.REPEAT) RayWrap.REPEAT else RayWrap.CLAMP_TO_EDGE,
                    if (sampler.wrapV == RayTextureWrap.REPEAT) RayWrap.REPEAT else RayWrap.CLAMP_TO_EDGE)
            } as RayTexture)
            return textures.lastIndex
        }
        private fun convertMaterial(source: RayModelMaterial, images: Map<String, Int>): RayMaterial {
            fun binding(usage: Int) = source.textures.firstOrNull { it.usage == usage }?.let {
                RayTextureBinding(images.getValue(it.fileName), it.offsetU, it.offsetV, it.scaleU, it.scaleV)
            }
            fun RayModelColor?.color(default: RayColor) = this?.let { RayColor(it.r, it.g, it.b, it.a) } ?: default
            return RayMaterial(kind = if (source.pbr) RayMaterialKind.PBR else RayMaterialKind.DEFAULT,
                baseColor = (if (source.pbr) source.baseColor ?: source.diffuse else source.diffuse).color(RayColor(1f, 1f, 1f)),
                emissive = source.emissive.color(if (binding(ModelTexture.USAGE_EMISSIVE) != null) RayColor(1f, 1f, 1f) else RayColor(0f, 0f, 0f)),
                specular = source.specular.color(if (binding(ModelTexture.USAGE_SPECULAR) != null) RayColor(1f, 1f, 1f) else RayColor(0f, 0f, 0f)),
                shininess = source.shininess.takeIf { it > 0f } ?: 20f, metallic = source.metallic ?: 0f, roughness = source.roughness ?: 1f, opacity = source.opacity,
                alphaMode = RayAlphaMode.valueOf(source.alphaMode.name), alphaCutoff = source.alphaCutoff, doubleSided = source.doubleSided,
                baseTexture = binding(ModelTexture.USAGE_DIFFUSE), emissiveTexture = binding(ModelTexture.USAGE_EMISSIVE),
                specularTexture = binding(ModelTexture.USAGE_SPECULAR), normalTexture = binding(ModelTexture.USAGE_NORMAL),
                metallicRoughnessTexture = binding(100), occlusionTexture = binding(101))
        }
        private fun channelOrNull(mesh: RayModelMesh, values: FloatArray, stride: Int, usage: Int, components: Int): FloatArray? =
            if (mesh.attributes.none { it.usage == usage && it.unit == 0 }) null else channel(mesh, values, stride, usage, components)
        private fun channel(mesh: RayModelMesh, values: FloatArray, stride: Int, usage: Int, components: Int): FloatArray {
            val attribute = requireNotNull(mesh.attributes.firstOrNull { it.usage == usage && it.unit == 0 }) { "Missing position channel" }
            require(attribute.type == GL20.GL_FLOAT && attribute.components >= components && attribute.offsetBytes % 4 == 0 && values.size % stride == 0) { "Unsupported vertex channel" }
            val offset = attribute.offsetBytes / 4
            return FloatArray(values.size / stride * components) { values[it / components * stride + offset + it % components] }
        }
        private fun tangent(mesh: RayModelMesh, values: FloatArray, stride: Int, normals: FloatArray?): FloatArray? {
            val attribute = mesh.attributes.firstOrNull { it.usage == VertexAttributes.Usage.Tangent } ?: return null
            if (attribute.components == 4) return channel(mesh, values, stride, VertexAttributes.Usage.Tangent, 4)
            val xyz = channel(mesh, values, stride, VertexAttributes.Usage.Tangent, 3)
            val binormal = channelOrNull(mesh, values, stride, VertexAttributes.Usage.BiNormal, 3)
            return FloatArray(xyz.size / 3 * 4) { index ->
                val vertex = index / 4
                if (index % 4 != 3) xyz[vertex * 3 + index % 4]
                else if (normals == null || binormal == null) 1f
                else {
                    val i = vertex * 3
                    val handed = (normals[i + 1] * xyz[i + 2] - normals[i + 2] * xyz[i + 1]) * binormal[i] +
                        (normals[i + 2] * xyz[i] - normals[i] * xyz[i + 2]) * binormal[i + 1] +
                        (normals[i] * xyz[i + 1] - normals[i + 1] * xyz[i]) * binormal[i + 2]
                    if (handed < 0f) -1f else 1f
                }
            }
        }
    }

    private fun lights(set: LightSet): List<RayLight> = set.directional.map {
        RayLight(it.entityId, RayLightKind.DIRECTIONAL, it.color.ray(), it.direction.ray(), it.position.ray())
    } + set.point.map {
        RayLight(it.entityId, RayLightKind.POINT, it.color.ray(it.range), position = it.position.ray(), range = it.range)
    } + set.spot.map {
        RayLight(it.entityId, RayLightKind.SPOT, it.color.ray(it.range), it.direction.ray(), it.position.ray(), it.range, it.cone.angle / 2f, it.cone.softness)
    }
}

private class RaySceneLimitException(message: String) : IllegalArgumentException(message)
private fun Vec3.ray() = RayVec3(x, y, z)
private fun Rgba.ray(scale: Float = 1f) = RayColor(r * scale, g * scale, b * scale, a)
private fun RayVec3.list() = listOf(x, y, z)
