/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.lib.core.editor.ray

import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.VertexAttributes
import com.badlogic.gdx.graphics.g3d.model.data.ModelTexture
import com.badlogic.gdx.math.Matrix4
import com.badlogic.gdx.math.Vector3
import net.nevinsky.abyssus.lib.core.assets.terrain.SPLAT_LAYERS
import net.nevinsky.abyssus.lib.core.editor.ray.snapshot.*
import net.nevinsky.abyssus.lib.raytracing.*

/**
 * The one asset→ray conversion, shared by the editor's [RaySceneSnapshots] and the shipped game's ray view.
 *
 * Converts `RayModelSnapshot`/`RayTerrainSnapshot` assets plus their per-instance world transforms into the
 * `RayMesh`/`RayMaterial`/`RayTexture` values a `RaySceneSnapshot` is built from, reusing mesh and texture caches so
 * unchanged assets keep their identity across conversions. Geometry, materials and textures belong to the asset, not the
 * instance: many instances share one of each. No editor scene types (`ScenePreview`, `AssetPlacement`, `LightSet`) appear
 * in this API: instances are placed by id and world matrix. A model is drawn in bind pose unless [model] is given a
 * [RayModelPose] and [reset] a deformation, which the editor passes for animated and skinned models. It lives in
 * `lib-core-editor`, so a shipped game cannot use it.
 */
class RaySceneConverter(private val defaultLimits: RaySnapshotLimits = RaySnapshotLimits()) {
    /** Meshes built so far, keyed by mesh id; unchanged assets return the same `RayMesh` instance. */
    val meshes = mutableListOf<RayMesh>()

    /** Materials built so far. */
    val materials = mutableListOf<RayMaterial>()

    /** Textures built so far, keyed by texture id. */
    val textures = mutableListOf<RayTexture>()

    /** One entry per placed instance: mesh index, material index, world matrix. */
    val instances = mutableListOf<RayInstance>()

    /** Topology key per instance: "instanceId:meshKey:materialId". */
    val topology = mutableListOf<String>()

    /** World transform per instance id. */
    val transforms = linkedMapOf<String, List<Float>>()
    private val geometry = mutableMapOf<String, Int>()
    private val materialIds = mutableMapOf<String, Int>()
    private var bytes = 0L
    private var triangles = 0L
    private var limits = defaultLimits
    private var deform: ((RayModelMesh, List<FloatArray>) -> FloatArray)? = null
    // Converted meshes per retained asset object, so a frame that reuses an asset reuses its `RayMesh` identity.
    private val caches = java.util.WeakHashMap<Any, MutableMap<String, Any>>()
    private val textureCache = HashMap<String, Pair<RayModelImage, RayTexture>>()
    private val assetIds = java.util.WeakHashMap<Any, Int>()
    private var nextAssetId = 0
    private fun assetKey(asset: Any) = "a${assetIds.getOrPut(asset) { nextAssetId++ }}"

    /**
     * Starts the next conversion: the outputs and bounds are cleared, the per-asset mesh and texture caches are kept.
     * [environmentTextures] take the first texture indices, which is where `RayEnvironment.texture` points, and count
     * toward the byte bound. [limits] bound this conversion only. A [deform] turns a mesh and a skin palette into
     * deformed vertices; with one, a skinned part must come with its pose.
     */
    fun reset(
        environmentTextures: List<RayTexture> = emptyList(),
        limits: RaySnapshotLimits = defaultLimits,
        deform: ((RayModelMesh, List<FloatArray>) -> FloatArray)? = null,
    ) {
        meshes.clear(); materials.clear(); textures.clear(); instances.clear(); topology.clear(); transforms.clear()
        textures.addAll(environmentTextures)
        geometry.clear(); materialIds.clear()
        bytes = environmentTextures.sumOf { it.width.toLong() * it.height * 16 }
        triangles = 0L
        this.limits = limits
        this.deform = deform
    }

    /**
     * Converts one placed model (mesh parts → meshes/instances, materials → materials, images → textures) and returns the
     * number of instances it added. [instanceId] prefixes the instance ids and [world] places the model. With a [pose],
     * the pose's node transforms replace the bind pose and a skinned part is deformed for this instance alone.
     */
    fun model(instanceId: String, world: Matrix4, asset: RayModelSnapshot, pose: RayModelPose? = null): Int {
        val firstInstance = instances.size
        val shared = "model:${assetKey(asset)}"
        val cache = caches.getOrPut(asset) { HashMap() }
        val textureIds =
            asset.images.mapValues { (name, image) -> texture("$shared:$name", image, RayTextureSampler()) }

        fun material(id: String?): Int =
            materialIds.getOrPut("$shared:$id") {
                materials.add(convertMaterial(asset.requireMaterial(id), textureIds))
                materials.lastIndex
            }

        fun visit(node: RayModelNode, parent: Matrix4, path: String) {
            val global = pose?.node(node.id)?.let(::Matrix4) ?: Matrix4(parent).mul(node.localMatrix())
            for ((ordinal, part) in node.parts.withIndex()) {
                val skin = pose?.bones(node.id, part.meshPartId)
                val deform = deform
                require(deform == null || part.boneBindTransforms.isEmpty() || skin != null) {
                    "Skinned model requires the displayed animation pose and deformation"
                }
                val source = asset.requireMeshOfPart(part.meshPartId)
                val meshPart = requireNotNull(source.parts.firstOrNull { it.id == part.meshPartId }) {
                    "Missing mesh part ${part.meshPartId}"
                }
                require(meshPart.primitiveType == GL20.GL_TRIANGLES) { "Only triangle mesh parts are supported" }
                val key = "$shared:${part.meshPartId}" + if (skin != null) ":$instanceId:$path/$ordinal" else ""
                val meshIndex = geometry.getOrPut(key) {
                    fun build(): CachedMesh {
                        val values = if (skin != null) requireNotNull(deform)(source, skin) else source.vertices()
                        return rayMeshOf(source, values, meshPart.indices(), key)
                    }
                    // skinned geometry depends on the instance's pose; static geometry is converted once per asset
                    val built = if (skin != null) build() else cache.getOrPut("mesh:$key") { build() } as CachedMesh
                    bytes += built.bytes
                    triangles += built.mesh.indexCount / 3
                    meshes.add(built.mesh)
                    meshes.lastIndex
                }
                val id = "$instanceId/$path/$ordinal"
                // a skinned instance is placed by the model's world alone: its deformed vertices already hold the node transforms
                val placed = if (skin != null) Matrix4(world) else Matrix4(world).mul(global)
                instance(id, meshIndex, material(part.materialId), placed)
                topology += "$id:$key:${part.materialId}"
            }
            node.children.forEachIndexed { index, child -> visit(child, global, "$path/$index") }
        }
        asset.nodes.forEachIndexed { index, node -> visit(node, Matrix4(), index.toString()) }
        return instances.size - firstInstance
    }

    /** Converts one placed terrain into a mesh, material and instance. */
    fun terrain(instanceId: String, world: Matrix4, asset: RayTerrainSnapshot) {
        val key = "terrain:${assetKey(asset)}"
        val cache = caches.getOrPut(asset) { HashMap() }
        val meshIndex = geometry.getOrPut(key) {
            val built = cache.getOrPut("mesh:$key") { rayTerrainMeshOf(asset, key) } as CachedMesh
            bytes += built.bytes
            triangles += built.mesh.indexCount / 3
            meshes.add(built.mesh)
            meshes.lastIndex
        }
        val materialIndex = materialIds.getOrPut(key) {
            val splat = asset.splatMap?.let { RayTextureBinding(texture("$key:splat", it.image, it.sampler)) }
            val layers = SPLAT_LAYERS.map { name ->
                asset.layers[name]?.let {
                    RayTextureBinding(texture("$key:$name", it.image, it.sampler))
                }
            }
            materials.add(
                RayMaterial(
                    kind = RayMaterialKind.TERRAIN,
                    terrain = RayTerrainMaterial(splat, layers, asset.size.toFloat())
                )
            )
            materials.lastIndex
        }
        instance(instanceId, meshIndex, materialIndex, world)
        topology += "$instanceId:$key"
    }

    /** Says which bound was exceeded and by how much. */
    fun checkBounds() {
        if (instances.size > limits.maxInstances) throw RaySceneLimitException("The scene needs ${instances.size} model parts and terrain; the limit is ${limits.maxInstances}")
        if (triangles > limits.maxTriangles) throw RaySceneLimitException("The scene has $triangles triangles; the limit is ${limits.maxTriangles}")
        if (bytes > limits.maxBytes) throw RaySceneLimitException("The scene needs ${bytes / (1024 * 1024)} MB of geometry and textures; the limit is ${limits.maxBytes / (1024 * 1024)} MB")
    }

    private fun texture(id: String, image: RayModelImage, sampler: RayTextureSampler): Int {
        val existing = textures.indexOfFirst { it.id == id }
        if (existing >= 0) return existing
        bytes += image.byteSize
        checkBounds()
        val cached = textureCache[id]?.takeIf { it.first === image }?.second
        textures.add(
            cached ?: RayTexture(
                id, image.width, image.height, image.rgba(),
                sampler.wrapU.toRayWrap(), sampler.wrapV.toRayWrap()
            ).also { textureCache[id] = image to it }
        )
        return textures.lastIndex
    }

    private fun instance(id: String, mesh: Int, material: Int, matrix: Matrix4) {
        instances.add(RayInstance(id, mesh, material, matrix.`val`))
        transforms[id] = matrix.`val`.toList()
        checkBounds()
    }

}
