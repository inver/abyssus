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
 * The scene-independent asset→ray conversion, extracted from `RaySceneSnapshots`' private builder (design decision 8).
 *
 * Converts `RayModelSnapshot`/`RayTerrainSnapshot` assets plus their per-instance world transforms into the
 * `RayMesh`/`RayMaterial`/`RayTexture` values a `RaySceneSnapshot` is built from, reusing a mesh cache so unchanged
 * assets keep their identity across conversions. No editor scene types (`ScenePreview`, `AssetPlacement`, `LightSet`,
 * optics overrides, pose/deform) appear in this API: instances are placed by id and world matrix, and models are drawn
 * in bind pose. It lives in `lib-core-editor`, so a shipped game cannot use it. `RaySceneSnapshots`' private builder still
 * holds its own copy of this conversion plus the posing and optics it adds; the two share the limit exception.
 */
class RaySceneConverter(private val limits: RaySnapshotLimits = RaySnapshotLimits()) {
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
    // Converted meshes per retained asset object, so a frame that reuses an asset reuses its `RayMesh` identity.
    private val caches = java.util.WeakHashMap<Any, MutableMap<String, Any>>()
    private val textureCache = HashMap<String, Pair<RayModelImage, RayTexture>>()
    private val assetIds = java.util.WeakHashMap<Any, Int>()
    private var nextAssetId = 0
    private fun assetKey(asset: Any) = "a${assetIds.getOrPut(asset) { nextAssetId++ }}"

    /**
     * Starts the next conversion: the outputs and bounds are cleared, the per-asset mesh and texture caches are kept.
     * [environmentTextures] take the first texture indices, which is where `RayEnvironment.texture` points.
     */
    fun reset(environmentTextures: List<RayTexture> = emptyList()) {
        meshes.clear(); materials.clear(); textures.clear(); instances.clear(); topology.clear(); transforms.clear()
        textures.addAll(environmentTextures)
        geometry.clear(); materialIds.clear(); bytes = 0L; triangles = 0L
    }

    /** Converts one placed model (mesh parts → meshes/instances, materials → materials, images → textures) in bind pose. */
    fun model(instanceId: String, world: Matrix4, asset: RayModelSnapshot) {
        val prefix = "model:$instanceId"
        // geometry, materials and textures belong to the asset, not the instance: many instances share one of each
        val shared = "model:${assetKey(asset)}"
        val cache = caches.getOrPut(asset) { HashMap() }
        val textureIds =
            asset.images.mapValues { (name, image) -> texture("$shared:$name", image, RayTextureSampler()) }

        fun material(id: String?): Int =
            materialIds.getOrPut("$shared:$id") {
                val source = asset.requireMaterial(id)
                materials.add(convertMaterial(source, textureIds))
                materials.lastIndex
            }

        fun visit(node: RayModelNode, parent: Matrix4, path: String) {
            val local = node.localMatrix()
            val global = Matrix4(parent).mul(local)
            for ((ordinal, part) in node.parts.withIndex()) {
                val source =
                    asset.requireMeshOfPart(part.meshPartId)
                val meshPart = requireNotNull(source.parts.firstOrNull { it.id == part.meshPartId }) {
                    "Missing mesh part ${part.meshPartId}"
                }
                require(meshPart.primitiveType == GL20.GL_TRIANGLES) { "Only triangle mesh parts are supported" }
                val key = "$shared:${part.meshPartId}"
                val built = cache.getOrPut("mesh:${part.meshPartId}") {
                    buildMesh(source)
                } as CachedMesh
                val meshIndex = geometry.getOrPut(key) {
                    meshes.add(built.mesh)
                    meshes.lastIndex
                }
                bytes += built.bytes
                triangles += built.mesh.indexCount / 3
                val id = "$instanceId/$path/$ordinal"
                val worldMat = Matrix4(world).mul(global)
                instance(id, meshIndex, material(part.materialId), worldMat)
                topology += "$id:$prefix:${part.meshPartId}:${part.materialId}"
            }
            node.children.forEachIndexed { index, child -> visit(child, global, "$path/$index") }
        }
        asset.nodes.forEachIndexed { index, node -> visit(node, Matrix4(), index.toString()) }
    }

    /** Converts one placed terrain into a mesh, material and instance. */
    fun terrain(instanceId: String, world: Matrix4, asset: RayTerrainSnapshot) {
        val key = "terrain:$instanceId"
        val cache = caches.getOrPut(asset) { HashMap() }
        val meshIndex = geometry.getOrPut(key) {
            val built = cache.getOrPut("terrain") { buildTerrainMesh(asset) } as CachedMesh
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

    private fun buildMesh(source: RayModelMesh): CachedMesh =
        rayMeshOf(source, source.vertices(), source.parts.flatMap { it.indices().toList() }.toIntArray(), "mesh")

    private fun buildTerrainMesh(asset: RayTerrainSnapshot): CachedMesh = rayTerrainMeshOf(asset, "terrain")

    private fun texture(id: String, image: RayModelImage, sampler: RayTextureSampler): Int {
        val existing = textures.indexOfFirst { it.id == id }
        if (existing >= 0) return existing
        bytes += image.byteSize
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
