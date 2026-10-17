/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.lib.core.editor.ray

import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.VertexAttributes
import com.badlogic.gdx.graphics.g3d.model.data.ModelTexture
import com.badlogic.gdx.math.Matrix4
import net.nevinsky.abyssus.lib.core.editor.content.PlacementTransform
import net.nevinsky.abyssus.lib.core.editor.content.Quat
import net.nevinsky.abyssus.lib.core.editor.content.Vec3
import net.nevinsky.abyssus.lib.core.editor.content.toMatrix
import net.nevinsky.abyssus.lib.core.editor.ray.snapshot.*
import net.nevinsky.abyssus.lib.raytracing.*

/**
 * The scene-independent asset→ray conversion, extracted from `RaySceneSnapshots`' private builder (design decision 8).
 *
 * Converts `RayModelSnapshot`/`RayTerrainSnapshot` assets plus their per-instance world transforms into the
 * `RayMesh`/`RayMaterial`/`RayTexture` values a `RaySceneSnapshot` is built from, reusing a mesh cache so unchanged
 * assets keep their identity across conversions. No editor scene types (`ScenePreview`, `AssetPlacement`, `LightSet`,
 * optics overrides, pose/deform) appear in this API: instances are placed by id and world matrix, and models are drawn
 * in bind pose. Usable by both the editor and the game.
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
                val source = requireNotNull(asset.materials.firstOrNull { it.id == id }) { "Missing material $id" }
                materials.add(convertMaterial(source, textureIds))
                materials.lastIndex
            }

        fun visit(node: RayModelNode, parent: Matrix4, path: String) {
            val local = PlacementTransform(
                Vec3(node.translation.x, node.translation.y, node.translation.z),
                Quat(node.rotation.x, node.rotation.y, node.rotation.z, node.rotation.w),
                Vec3(node.scale.x, node.scale.y, node.scale.z)
            ).toMatrix()
            val global = Matrix4(parent).mul(local)
            for ((ordinal, part) in node.parts.withIndex()) {
                val source =
                    requireNotNull(asset.meshes.firstOrNull { mesh -> mesh.parts.any { it.id == part.meshPartId } }) {
                        "Missing mesh part ${part.meshPartId}"
                    }
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
            val layers = listOf("layer0", "layer1", "layer2", "layer3", "layer4").map { name ->
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
        if (instances.size > limits.maxInstances) throw IllegalArgumentException("The scene needs ${instances.size} model parts and terrain; the limit is ${limits.maxInstances}")
        if (triangles > limits.maxTriangles) throw IllegalArgumentException("The scene has $triangles triangles; the limit is ${limits.maxTriangles}")
        if (bytes > limits.maxBytes) throw IllegalArgumentException("The scene needs ${bytes / (1024 * 1024)} MB of geometry and textures; the limit is ${limits.maxBytes / (1024 * 1024)} MB")
    }

    private fun buildMesh(source: RayModelMesh): CachedMesh {
        val values = source.vertices()
        val stride = source.vertexSizeBytes / 4
        val position = channel(source, values, stride, VertexAttributes.Usage.Position, 3)
        val normal = channelOrNull(source, values, stride, VertexAttributes.Usage.Normal, 3)
        val uv = channelOrNull(source, values, stride, VertexAttributes.Usage.TextureCoordinates, 2)
        val tangent = tangent(source, values, stride, normal)
        val copiedIndices = source.parts.flatMap { it.indices().toList() }
        val size = (position.size.toLong() + (normal?.size ?: 0) + (uv?.size ?: 0) + (tangent?.size
            ?: 0) + copiedIndices.size) * 4
        return CachedMesh(RayMesh("mesh", position, copiedIndices.toIntArray(), normal, uv, tangent), size)
    }

    private fun buildTerrainMesh(asset: RayTerrainSnapshot): CachedMesh {
        val values = asset.vertices()
        val count = values.size / 8
        val positions = FloatArray(count * 3) { values[it / 3 * 8 + it % 3] }
        val normals = FloatArray(count * 3) { values[it / 3 * 8 + 3 + it % 3] }
        val uvs = FloatArray(count * 2) { values[it / 2 * 8 + 6 + it % 2] }
        return CachedMesh(
            RayMesh("terrain", positions, asset.indices(), normals, uvs),
            (positions.size.toLong() + normals.size + uvs.size + asset.indices().size) * 4
        )
    }

    private fun texture(id: String, image: RayModelImage, sampler: RayTextureSampler): Int {
        val existing = textures.indexOfFirst { it.id == id }
        if (existing >= 0) return existing
        bytes += image.byteSize
        val cached = textureCache[id]?.takeIf { it.first === image }?.second
        textures.add(
            cached ?: RayTexture(
                id, image.width, image.height, image.rgba(),
                if (sampler.wrapU == RayTextureWrap.REPEAT) RayWrap.REPEAT else RayWrap.CLAMP_TO_EDGE,
                if (sampler.wrapV == RayTextureWrap.REPEAT) RayWrap.REPEAT else RayWrap.CLAMP_TO_EDGE
            ).also { textureCache[id] = image to it }
        )
        return textures.lastIndex
    }

    private fun convertMaterial(source: RayModelMaterial, images: Map<String, Int>): RayMaterial {
        fun binding(usage: Int) = source.textures.firstOrNull { it.usage == usage }?.let {
            RayTextureBinding(images.getValue(it.fileName), it.offsetU, it.offsetV, it.scaleU, it.scaleV)
        }

        fun RayModelColor?.toRay(default: RayColor) = this?.let { RayColor(it.r, it.g, it.b, it.a) } ?: default
        return RayMaterial(
            kind = if (source.pbr) RayMaterialKind.PBR else RayMaterialKind.DEFAULT,
            baseColor = (if (source.pbr) source.baseColor ?: source.diffuse else source.diffuse).toRay(
                RayColor(
                    1f,
                    1f,
                    1f
                )
            ),
            emissive = source.emissive.toRay(
                if (binding(ModelTexture.USAGE_EMISSIVE) != null) RayColor(
                    1f,
                    1f,
                    1f
                ) else RayColor(0f, 0f, 0f)
            ),
            specular = source.specular.toRay(
                if (binding(ModelTexture.USAGE_SPECULAR) != null) RayColor(
                    1f,
                    1f,
                    1f
                ) else RayColor(0f, 0f, 0f)
            ),
            shininess = source.shininess.takeIf { it > 0f } ?: 20f,
            metallic = source.metallic ?: 0f,
            roughness = source.roughness ?: 1f,
            opacity = source.opacity,
            alphaMode = RayAlphaMode.valueOf(source.alphaMode.name),
            alphaCutoff = source.alphaCutoff,
            doubleSided = source.doubleSided,
            baseTexture = binding(ModelTexture.USAGE_DIFFUSE),
            emissiveTexture = binding(ModelTexture.USAGE_EMISSIVE),
            specularTexture = binding(ModelTexture.USAGE_SPECULAR),
            normalTexture = binding(ModelTexture.USAGE_NORMAL),
            metallicRoughnessTexture = binding(100),
            occlusionTexture = binding(101))
    }

    private fun channelOrNull(
        mesh: RayModelMesh,
        values: FloatArray,
        stride: Int,
        usage: Int,
        components: Int
    ): FloatArray? =
        if (mesh.attributes.none { it.usage == usage && it.unit == 0 }) null else channel(
            mesh,
            values,
            stride,
            usage,
            components
        )

    private fun channel(mesh: RayModelMesh, values: FloatArray, stride: Int, usage: Int, components: Int): FloatArray {
        val attribute =
            requireNotNull(mesh.attributes.firstOrNull { it.usage == usage && it.unit == 0 }) { "Missing position channel" }
        require(attribute.type == GL20.GL_FLOAT && attribute.components >= components && attribute.offsetBytes % 4 == 0 && values.size % stride == 0) {
            "Unsupported vertex channel"
        }
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

    private fun instance(id: String, mesh: Int, material: Int, matrix: Matrix4) {
        instances.add(RayInstance(id, mesh, material, matrix.`val`))
        transforms[id] = matrix.`val`.toList()
        checkBounds()
    }

    private class CachedMesh(val mesh: RayMesh, val bytes: Long)
}
