/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.assets.model

import java.util.*

internal fun <T> immutableModelList(values: Collection<T>): List<T> = Collections.unmodifiableList(values.toList())

data class RayModelColor(val r: Float, val g: Float, val b: Float, val a: Float)
data class RayModelVector(val x: Float, val y: Float, val z: Float)
data class RayModelRotation(val x: Float, val y: Float, val z: Float, val w: Float)
data class RayModelVertexAttribute(
    val usage: Int, val components: Int, val type: Int, val normalized: Boolean,
    val unit: Int, val alias: String, val offsetBytes: Int,
)

/** Bytes use the raster renderer's linear sampling convention, including diffuse/emissive images. */
enum class RayTextureColorSpace { LINEAR }
enum class RayTextureFilter { LINEAR, MIPMAP_LINEAR_LINEAR }
enum class RayTextureWrap { REPEAT, CLAMP_TO_EDGE }
enum class RayModelAlphaMode { OPAQUE, MASK, BLEND }
data class RayTextureSampler(
    val minFilter: RayTextureFilter = RayTextureFilter.LINEAR,
    val magFilter: RayTextureFilter = RayTextureFilter.LINEAR,
    val wrapU: RayTextureWrap = RayTextureWrap.REPEAT,
    val wrapV: RayTextureWrap = RayTextureWrap.REPEAT,
    val mipmaps: Boolean = false,
)

data class RayModelTexture(
    val fileName: String, val usage: Int,
    val offsetU: Float, val offsetV: Float, val scaleU: Float, val scaleV: Float,
    val colorSpace: RayTextureColorSpace = RayTextureColorSpace.LINEAR,
    val sampler: RayTextureSampler = RayTextureSampler(),
)

/** RGBA8888 rows retain Pixmap's upload order; no vertical flip or gamma conversion is introduced. */
class RayModelImage internal constructor(val width: Int, val height: Int, rgba: ByteArray) {
    private val pixels = rgba.copyOf()
    val byteSize: Long get() = pixels.size.toLong()
    fun rgba(): ByteArray = pixels.copyOf()
}

class RayModelMeshPart internal constructor(val id: String?, val primitiveType: Int, indices: IntArray) {
    private val triangles = indices.copyOf()
    val byteSize: Long get() = triangles.size.toLong() * 4
    fun indices(): IntArray = triangles.copyOf()
}

class RayModelMesh internal constructor(
    val id: String?, vertices: FloatArray, val vertexSizeBytes: Int,
    attributes: List<RayModelVertexAttribute>, parts: List<RayModelMeshPart>,
) {
    private val values = vertices.copyOf()
    val attributes: List<RayModelVertexAttribute> = immutableModelList(attributes)
    val parts: List<RayModelMeshPart> = immutableModelList(parts)
    val byteSize: Long get() = values.size.toLong() * 4 + parts.sumOf { it.byteSize }
    fun vertices(): FloatArray = values.copyOf()
}

class RayModelMaterial internal constructor(
    val id: String?, val pbr: Boolean,
    val ambient: RayModelColor?, val diffuse: RayModelColor?, val specular: RayModelColor?,
    val emissive: RayModelColor?, val reflection: RayModelColor?, val shininess: Float, val opacity: Float,
    val baseColor: RayModelColor?, val metallic: Float?, val roughness: Float?,
    val doubleSided: Boolean, val alphaMode: RayModelAlphaMode, val alphaCutoff: Float,
    textures: List<RayModelTexture>,
) {
    val textures: List<RayModelTexture> = immutableModelList(textures)
}

/** One entry of a model's material table, without its colours or textures (see [RayModelSnapshotReader.materials]). */
data class RayModelMaterialInfo(val id: String?, val pbr: Boolean)

class RayModelMatrix internal constructor(matrix: FloatArray) {
    private val values = matrix.copyOf()
    fun values(): FloatArray = values.copyOf()
}

class RayModelNodePart internal constructor(
    val meshPartId: String?, val materialId: String?, boneBindTransforms: Map<String, RayModelMatrix>,
) {
    val boneBindTransforms: Map<String, RayModelMatrix> = Collections.unmodifiableMap(boneBindTransforms.toMap())
}

class RayModelNode internal constructor(
    val id: String?, val translation: RayModelVector, val rotation: RayModelRotation, val scale: RayModelVector,
    parts: List<RayModelNodePart>, children: List<RayModelNode>,
) {
    val parts: List<RayModelNodePart> = immutableModelList(parts)
    val children: List<RayModelNode> = immutableModelList(children)
}

/** No mutable libGDX objects or GL handles escape asset preparation. Repeated instances share these bytes. */
class RayModelSnapshot internal constructor(
    meshes: List<RayModelMesh>,
    materials: List<RayModelMaterial>,
    nodes: List<RayModelNode>,
    images: Map<String, RayModelImage>,
) {
    val meshes: List<RayModelMesh> = immutableModelList(meshes)
    val materials: List<RayModelMaterial> = immutableModelList(materials)
    val nodes: List<RayModelNode> = immutableModelList(nodes)
    val images: Map<String, RayModelImage> = Collections.unmodifiableMap(images.toMap())

    /** Retained geometry/image payload; backend resource bounds are accounted separately. */
    val byteSize: Long = meshes.sumOf { it.byteSize } + images.values.sumOf { it.byteSize }
}
