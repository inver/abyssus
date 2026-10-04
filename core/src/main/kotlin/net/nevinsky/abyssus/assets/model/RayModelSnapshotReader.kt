/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.assets.model

import com.badlogic.gdx.files.FileHandle
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.g3d.model.data.ModelNode
import net.nevinsky.abyssus.assets.files.AssetFiles
import net.nevinsky.abyssus.core.loader.AssimpModelLoader
import net.nevinsky.abyssus.core.model.ModelData
import net.nevinsky.abyssus.core.model.PbrModelMaterial

/** CPU-only copy before images are uploaded/disposed, or a fresh preparation when ray mode starts later. */
class RayModelSnapshotReader(private val assimp: AssimpModelLoader, private val maxBytes: Long = 128L * 1024 * 1024) {
    init { require(maxBytes > 0) }

    fun read(files: AssetFiles, name: String): RayModelSnapshot? {
        val file = FileHandle(files.model(name) ?: return null)
        val data = assimp.loadData(file)
        val images = assimp.decodeTextures(data, file)
        return try { capture(data, images) } finally { images.values.forEach(Pixmap::dispose) }
    }

    /** Only the material table, in model order: identifiers and whether each is PBR. Decodes no images, for editors. */
    fun materials(files: AssetFiles, name: String): List<RayModelMaterialInfo>? {
        val file = FileHandle(files.model(name) ?: return null)
        return assimp.loadData(file).materials.map { RayModelMaterialInfo(it.id, it is PbrModelMaterial) }
    }

    /** Does not dispose or mutate the caller's parsed model or Pixmaps. */
    fun capture(data: ModelData, images: Map<String, Pixmap>): RayModelSnapshot {
        val bytes = data.meshes.sumOf { mesh ->
            mesh.vertices.size.toLong() * 4 + mesh.parts.sumOf { it.indices.size.toLong() * 4 }
        } + images.values.sumOf { it.width.toLong() * it.height * 4 }
        require(bytes <= maxBytes) { "CPU model snapshot exceeds its $maxBytes byte limit" }
        val meshes = data.meshes.map { mesh ->
            var offset = 0
            val attributes = mesh.attributes.map { attribute ->
                RayModelVertexAttribute(attribute.usage, attribute.numComponents, attribute.type, attribute.normalized,
                    attribute.unit, attribute.alias, offset).also { offset += attribute.sizeInBytes }
            }
            require(offset > 0 && offset % 4 == 0 && mesh.vertices.size % (offset / 4) == 0) { "Invalid model vertex layout" }
            val vertices = mesh.vertices.size / (offset / 4)
            RayModelMesh(mesh.id, mesh.vertices, offset, attributes, mesh.parts.map { part ->
                require(part.indices.all { it in 0 until vertices }) { "Model index outside vertex buffer" }
                RayModelMeshPart(part.id, part.primitiveType, part.indices)
            })
        }
        val materials = data.materials.map { material ->
            val pbr = material as? PbrModelMaterial
            RayModelMaterial(material.id, pbr != null,
                color(material.ambient), color(material.diffuse), color(material.specular),
                color(material.emissive), color(material.reflection), material.shininess, material.opacity,
                color(pbr?.baseColor), pbr?.metallic, pbr?.roughness, pbr?.doubleSided ?: false,
                when {
                    pbr?.alphaMode == PbrModelMaterial.AlphaMode.MASK -> RayModelAlphaMode.MASK
                    pbr?.alphaMode == PbrModelMaterial.AlphaMode.BLEND || material.opacity != 1f -> RayModelAlphaMode.BLEND
                    else -> RayModelAlphaMode.OPAQUE
                }, pbr?.alphaCutoff ?: 0.5f,
                material.textures?.map { texture ->
                    val name = requireNotNull(texture.fileName) { "Model texture has no file name" }
                    require(name in images) { "CPU model image '$name' is missing or unreadable" }
                    RayModelTexture(name, texture.usage, texture.uvTranslation?.x ?: 0f, texture.uvTranslation?.y ?: 0f,
                        texture.uvScaling?.x ?: 1f, texture.uvScaling?.y ?: 1f)
                } ?: emptyList())
        }
        val copiedImages = images.mapValues { (_, image) -> copyRayImage(image) }
        return RayModelSnapshot(meshes, materials, data.nodes.map(::node), copiedImages)
    }

    private fun color(color: Color?) = color?.let { RayModelColor(it.r, it.g, it.b, it.a) }
    private fun node(node: ModelNode): RayModelNode = RayModelNode(
        node.id,
        node.translation?.let { RayModelVector(it.x, it.y, it.z) } ?: RayModelVector(0f, 0f, 0f),
        node.rotation?.let { RayModelRotation(it.x, it.y, it.z, it.w) } ?: RayModelRotation(0f, 0f, 0f, 1f),
        node.scale?.let { RayModelVector(it.x, it.y, it.z) } ?: RayModelVector(1f, 1f, 1f),
        node.parts?.map { part ->
            val bones = LinkedHashMap<String, RayModelMatrix>()
            part.bones?.forEach { entry -> bones[entry.key] = RayModelMatrix(entry.value.`val`) }
            RayModelNodePart(part.meshPartId, part.materialId, bones)
        } ?: emptyList(), node.children?.map(::node) ?: emptyList()
    )
}

/** Shares the raster upload's row order and converts arbitrary Pixmap formats to immutable RGBA8888. */
internal fun copyRayImage(image: Pixmap): RayModelImage {
    val pixels = image.width.toLong() * image.height
    require(pixels <= Int.MAX_VALUE / 4) { "CPU image dimensions exceed array limits" }
    val rgba = ByteArray(pixels.toInt() * 4)
    for (y in 0 until image.height) for (x in 0 until image.width) {
        val pixel = image.getPixel(x, y)
        val start = (y * image.width + x) * 4
        for (channel in 0..3) rgba[start + channel] = (pixel ushr (24 - channel * 8)).toByte()
    }
    return RayModelImage(image.width, image.height, rgba)
}
