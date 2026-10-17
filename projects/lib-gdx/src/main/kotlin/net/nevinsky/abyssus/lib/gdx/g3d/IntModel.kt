/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.gdx.g3d

import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.VertexAttributes
import com.badlogic.gdx.graphics.g3d.Material
import com.badlogic.gdx.graphics.g3d.Model
import com.badlogic.gdx.graphics.g3d.attributes.BlendingAttribute
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute
import com.badlogic.gdx.graphics.g3d.attributes.FloatAttribute
import com.badlogic.gdx.graphics.g3d.attributes.IntAttribute
import com.badlogic.gdx.graphics.g3d.attributes.TextureAttribute
import com.badlogic.gdx.graphics.g3d.model.MeshPart
import com.badlogic.gdx.graphics.g3d.model.Node
import com.badlogic.gdx.graphics.g3d.model.data.ModelMaterial
import com.badlogic.gdx.graphics.g3d.model.data.ModelTexture
import com.badlogic.gdx.graphics.g3d.utils.TextureDescriptor
import com.badlogic.gdx.graphics.g3d.utils.TextureProvider
import com.badlogic.gdx.utils.BufferUtils
import net.nevinsky.abyssus.lib.gdx.ModelLogging
import net.nevinsky.abyssus.lib.gdx.model.ModelData
import net.nevinsky.abyssus.lib.gdx.model.ModelMesh
import net.nevinsky.abyssus.lib.gdx.model.PBRColorAttribute
import net.nevinsky.abyssus.lib.gdx.model.PBRFloatAttribute
import net.nevinsky.abyssus.lib.gdx.model.PBRTextureAttribute
import net.nevinsky.abyssus.lib.gdx.model.PbrModelMaterial
import net.nevinsky.abyssus.lib.gdx.model.PbrModelMaterial.AlphaMode
import kotlin.math.max

/**
 * A stock libGDX [Model] built from this module's [ModelData], whose meshes are [IntMesh]es with 32-bit indices. The
 * nodes, mesh parts, materials and animations are the stock types, so a stock `ModelInstance` and `ModelBatch` draw it.
 *
 * Compared to the stock `Model(modelData, textureProvider)` it keeps one texture per file for the whole model, converts
 * the PBR material properties of a [PbrModelMaterial], and reports [maxBones] and [hasPbrMaterials].
 */
class IntModel private constructor() : Model() {
    private val loadedTextures = HashMap<String, Texture>()

    /** Builds the model; a GL context must be current. [textureProvider] loads each image file once. */
    constructor(modelData: ModelData, textureProvider: TextureProvider) : this() {
        load(modelData, textureProvider)
    }

    /** The largest number of bones one node part uses, 0 if the model is not skinned. Size `Config.numBones` with it. */
    val maxBones: Int
        get() = nodes.fold(0) { most, node -> max(most, maxBones(node)) }

    /** `true` if any material has metallic or roughness values, so the PBR shader should draw this model. */
    fun hasPbrMaterials(): Boolean =
        materials.any { it.has(PBRFloatAttribute.Metallic) || it.has(PBRFloatAttribute.Roughness) }

    /** The stock `load` for this module's [ModelData], which holds `IntArray` indices. */
    fun load(modelData: ModelData, textureProvider: TextureProvider) {
        loadIntMeshes(modelData.meshes)
        loadMaterials(modelData.materials, textureProvider)
        loadNodes(modelData.nodes)
        loadAnimations(modelData.animations)
        calculateTransforms()
    }

    private fun loadIntMeshes(modelMeshes: Iterable<ModelMesh>) {
        for (modelMesh in modelMeshes) {
            convertIntMesh(modelMesh)
        }
    }

    private fun convertIntMesh(modelMesh: ModelMesh) {
        val numIndices = modelMesh.parts.sumOf { it.indices.size }
        val hasIndices = numIndices > 0
        val attributes = VertexAttributes(*modelMesh.attributes)
        val numVertices = modelMesh.vertices.size / (attributes.vertexSize / Float.SIZE_BYTES)

        val mesh = IntMesh(true, numVertices, numIndices, attributes)
        meshes.add(mesh)
        disposables.add(mesh)
        BufferUtils.copy(modelMesh.vertices, mesh.getVerticesBuffer(true), modelMesh.vertices.size, 0)

        val indices = mesh.intIndices(true)
        indices.clear()
        var offset = 0
        var hasBoundingBox = true
        val parts = ArrayList<MeshPart>()
        for (part in modelMesh.parts) {
            val meshPart = MeshPart()
            meshPart.id = part.id
            meshPart.primitiveType = part.primitiveType
            meshPart.offset = offset
            meshPart.size = if (hasIndices) part.indices.size else numVertices
            meshPart.mesh = mesh
            if (hasIndices) {
                indices.put(part.indices)
            }
            offset += meshPart.size
            val box = part.boundingBox
            if (box == null) {
                hasBoundingBox = false
            } else {
                box.getCenter(meshPart.center)
                box.getDimensions(meshPart.halfExtents).scl(0.5f)
                meshPart.radius = meshPart.halfExtents.len()
            }
            meshParts.add(meshPart)
            parts.add(meshPart)
        }
        indices.position(0)
        if (!hasBoundingBox) {
            // a loaded model without bounding boxes: calculate them for the parts of this mesh
            parts.forEach { it.update() }
        }
    }

    override fun loadMaterials(modelMaterials: Iterable<ModelMaterial>, textureProvider: TextureProvider) {
        // one texture per file for the whole model: materials commonly share their images, and loading an image
        // once per material (as stock does) decodes and uploads it again and again
        loadedTextures.clear()
        super.loadMaterials(modelMaterials, textureProvider)
        loadedTextures.clear()
    }

    override fun convertMaterial(mtl: ModelMaterial, textureProvider: TextureProvider): Material {
        val result = Material()
        result.id = mtl.id
        mtl.ambient?.let { result.set(ColorAttribute(ColorAttribute.Ambient, it)) }
        mtl.diffuse?.let { result.set(ColorAttribute(ColorAttribute.Diffuse, it)) }
        mtl.specular?.let { result.set(ColorAttribute(ColorAttribute.Specular, it)) }
        mtl.emissive?.let { result.set(ColorAttribute(ColorAttribute.Emissive, it)) }
        mtl.reflection?.let { result.set(ColorAttribute(ColorAttribute.Reflection, it)) }
        if (mtl.shininess > 0f) {
            result.set(FloatAttribute(FloatAttribute.Shininess, mtl.shininess))
        }
        if (mtl.opacity != 1f && (mtl !is PbrModelMaterial || mtl.alphaMode != AlphaMode.MASK)) {
            result.set(BlendingAttribute(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA, mtl.opacity))
        }
        if (mtl is PbrModelMaterial) {
            applyPbrProperties(mtl, result)
        }
        for (tex in mtl.textures ?: return result) {
            val texture = loadedTextures.getOrPut(tex.fileName) {
                textureProvider.load(tex.fileName).also { disposables.add(it) }
            }
            val descriptor = TextureDescriptor<Texture>(texture)
            descriptor.minFilter = texture.minFilter
            descriptor.magFilter = texture.magFilter
            descriptor.uWrap = texture.uWrap
            descriptor.vWrap = texture.vWrap
            val offsetU = tex.uvTranslation?.x ?: 0f
            val offsetV = tex.uvTranslation?.y ?: 0f
            val scaleU = tex.uvScaling?.x ?: 1f
            val scaleV = tex.uvScaling?.y ?: 1f

            val type = legacyTextureType(tex.usage)
            when {
                type != null -> result.set(TextureAttribute(type, descriptor, offsetU, offsetV, scaleU, scaleV))
                tex.usage == PbrModelMaterial.USAGE_METALLIC_ROUGHNESS -> result.set(
                    pbrTexture(PBRTextureAttribute.MetallicRoughnessTexture, descriptor, offsetU, offsetV, scaleU, scaleV)
                )
                tex.usage == PbrModelMaterial.USAGE_OCCLUSION -> result.set(
                    pbrTexture(PBRTextureAttribute.OcclusionTexture, descriptor, offsetU, offsetV, scaleU, scaleV)
                )
                else -> ModelLogging.logger.atDebug().log { "Texture usage ${tex.usage} of material '${mtl.id}' is not supported" }
            }
            if (mtl is PbrModelMaterial) {
                // PBR shaders read their own attributes, keep the legacy ones for the non PBR shaders
                pbrAlias(tex.usage)?.let { result.set(pbrTexture(it, descriptor, offsetU, offsetV, scaleU, scaleV)) }
            }
        }
        return result
    }

    private fun legacyTextureType(usage: Int): Long? = when (usage) {
        ModelTexture.USAGE_DIFFUSE -> TextureAttribute.Diffuse
        ModelTexture.USAGE_SPECULAR -> TextureAttribute.Specular
        ModelTexture.USAGE_BUMP -> TextureAttribute.Bump
        ModelTexture.USAGE_NORMAL -> TextureAttribute.Normal
        ModelTexture.USAGE_AMBIENT -> TextureAttribute.Ambient
        ModelTexture.USAGE_EMISSIVE -> TextureAttribute.Emissive
        ModelTexture.USAGE_REFLECTION -> TextureAttribute.Reflection
        else -> null
    }

    private fun pbrAlias(usage: Int): Long? = when (usage) {
        ModelTexture.USAGE_DIFFUSE -> PBRTextureAttribute.BaseColorTexture
        ModelTexture.USAGE_NORMAL -> PBRTextureAttribute.NormalTexture
        ModelTexture.USAGE_EMISSIVE -> PBRTextureAttribute.EmissiveTexture
        else -> null
    }

    private fun pbrTexture(
        type: Long, descriptor: TextureDescriptor<Texture>, offsetU: Float, offsetV: Float, scaleU: Float, scaleV: Float
    ): PBRTextureAttribute {
        val attribute = PBRTextureAttribute(type, descriptor)
        attribute.offsetU = offsetU
        attribute.offsetV = offsetV
        attribute.scaleU = scaleU
        attribute.scaleV = scaleV
        return attribute
    }

    private fun applyPbrProperties(mtl: PbrModelMaterial, result: Material) {
        mtl.baseColor?.let { baseColor ->
            result.set(PBRColorAttribute.createBaseColorFactor(baseColor))
            if (!result.has(ColorAttribute.Diffuse)) {
                // the shaders read the base color factor from the diffuse color
                result.set(ColorAttribute(ColorAttribute.Diffuse, baseColor))
            }
        }
        mtl.metallic?.let { result.set(PBRFloatAttribute.createMetallic(it)) }
        mtl.roughness?.let { result.set(PBRFloatAttribute.createRoughness(it)) }
        if (mtl.doubleSided) {
            result.set(IntAttribute.createCullFace(GL20.GL_NONE))
        }
        if (mtl.alphaMode == AlphaMode.MASK) {
            result.set(FloatAttribute.createAlphaTest(mtl.alphaCutoff))
        } else if (mtl.alphaMode == AlphaMode.BLEND && !result.has(BlendingAttribute.Type)) {
            result.set(BlendingAttribute(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA, mtl.opacity))
        }
    }

    private fun maxBones(node: Node): Int {
        var most = 0
        for (part in node.parts) {
            part.invBoneBindTransforms?.let { most = max(most, it.size) }
        }
        for (child in node.children) {
            most = max(most, maxBones(child))
        }
        return most
    }
}
