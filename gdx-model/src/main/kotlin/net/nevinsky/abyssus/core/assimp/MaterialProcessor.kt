/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.core.assimp

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.g3d.model.data.ModelMaterial
import com.badlogic.gdx.graphics.g3d.model.data.ModelTexture
import com.badlogic.gdx.utils.Array
import net.nevinsky.abyssus.core.model.PbrModelMaterial
import net.nevinsky.abyssus.core.model.PbrModelMaterial.AlphaMode
import org.lwjgl.assimp.AIColor4D
import org.lwjgl.assimp.AIMaterial
import org.lwjgl.assimp.AIString
import org.lwjgl.assimp.Assimp
import org.lwjgl.system.MemoryStack
import java.nio.IntBuffer

/**
 * Converts an [AIMaterial] into a [ModelMaterial]. All native temporaries live on the [MemoryStack].
 */
internal class MaterialProcessor(private val textures: TextureProcessor) {
    private val usedIds: MutableSet<String> = HashSet<String>()

    fun process(aiMaterial: AIMaterial, index: Int): ModelMaterial {
        MemoryStack.stackPush().use { stack ->
            val metallic: Float? = readFloat(aiMaterial, Assimp.AI_MATKEY_METALLIC_FACTOR, stack)
            val roughness: Float? = readFloat(aiMaterial, Assimp.AI_MATKEY_ROUGHNESS_FACTOR, stack)
            val baseColor: Color? = readColor(aiMaterial, Assimp.AI_MATKEY_BASE_COLOR, stack)
            val pbr: Boolean = isPbr(baseColor, metallic, roughness)

            val material = if (pbr) readPbr(aiMaterial, baseColor, metallic, roughness, stack) else ModelMaterial()
            material.textures = Array<ModelTexture>()
            material.id = uniqueId(readName(aiMaterial, stack), index)
            material.diffuse = readColor(aiMaterial, Assimp.AI_MATKEY_COLOR_DIFFUSE, stack)
            material.ambient = readColor(aiMaterial, Assimp.AI_MATKEY_COLOR_AMBIENT, stack)
            material.specular = readColor(aiMaterial, Assimp.AI_MATKEY_COLOR_SPECULAR, stack)
            material.emissive = readColor(aiMaterial, Assimp.AI_MATKEY_COLOR_EMISSIVE, stack)
            material.reflection = readColor(aiMaterial, Assimp.AI_MATKEY_COLOR_REFLECTIVE, stack)

            val opacity: Float? = readFloat(aiMaterial, Assimp.AI_MATKEY_OPACITY, stack)
            if (opacity != null) {
                material.opacity = opacity
            }
            val shininess: Float? = readFloat(aiMaterial, Assimp.AI_MATKEY_SHININESS, stack)
            if (shininess != null) {
                material.shininess = shininess
            }
            readTextures(aiMaterial, material, stack, TEXTURE_USAGES)
            if (pbr) {
                readTextures(aiMaterial, material, stack, PBR_TEXTURE_USAGES)
            }
            return material
        }
    }

    private fun uniqueId(name: String?, index: Int): String {
        val base = if (name == null || name.isEmpty()) "material_" + index else name
        var id = base
        var n = 1
        while (!usedIds.add(id)) {
            id = base + "_" + n
            n++
        }
        return id
    }

    private fun readTextures(
        aiMaterial: AIMaterial,
        material: ModelMaterial,
        stack: MemoryStack,
        usages: kotlin.Array<IntArray>
    ) {
        for (entry in usages) {
            val path = AIString.calloc(stack)
            val res = Assimp.aiGetMaterialTexture(
                aiMaterial, entry[0], 0, path, null as IntBuffer?, null, null, null, null,
                null
            )
            if (res != Assimp.aiReturn_SUCCESS) {
                continue
            }
            if (hasUsage(material, entry[1])) {
                // e.g. glTF reports a base color texture as both DIFFUSE and BASE_COLOR
                continue
            }
            val fileName = textures.resolve(path.dataString())
            if (fileName == null) {
                continue
            }
            val texture = ModelTexture()
            texture.id = material.id + "_" + material.textures.size
            texture.usage = entry[1]
            texture.fileName = fileName
            material.textures.add(texture)
        }
    }

    companion object {
        private val TEXTURE_USAGES = arrayOf<IntArray>(
            intArrayOf(Assimp.aiTextureType_DIFFUSE, ModelTexture.USAGE_DIFFUSE),
            intArrayOf(Assimp.aiTextureType_BASE_COLOR, ModelTexture.USAGE_DIFFUSE),
            intArrayOf(Assimp.aiTextureType_SPECULAR, ModelTexture.USAGE_SPECULAR),
            intArrayOf(Assimp.aiTextureType_AMBIENT, ModelTexture.USAGE_AMBIENT),
            intArrayOf(Assimp.aiTextureType_EMISSIVE, ModelTexture.USAGE_EMISSIVE),
            intArrayOf(Assimp.aiTextureType_NORMALS, ModelTexture.USAGE_NORMAL),
            intArrayOf(Assimp.aiTextureType_HEIGHT, ModelTexture.USAGE_BUMP),
            intArrayOf(Assimp.aiTextureType_SHININESS, ModelTexture.USAGE_SHININESS),
            intArrayOf(Assimp.aiTextureType_REFLECTION, ModelTexture.USAGE_REFLECTION),
        )

        /**
         * Only meaningful for PBR materials: glTF maps the metallic-roughness texture to UNKNOWN and the occlusion texture
         * to LIGHTMAP.
         */
        private val PBR_TEXTURE_USAGES = arrayOf<IntArray>(
            intArrayOf(Assimp.aiTextureType_UNKNOWN, PbrModelMaterial.Companion.USAGE_METALLIC_ROUGHNESS),
            intArrayOf(Assimp.aiTextureType_LIGHTMAP, PbrModelMaterial.Companion.USAGE_OCCLUSION),
        )

        /**
         * Some importers (e.g. OBJ) report the default metallic 0 and roughness 1 for every material, so the factors
         * alone don't make a material PBR. A base color is only set by PBR aware importers like glTF.
         */
        private fun isPbr(baseColor: Color?, metallic: Float?, roughness: Float?): Boolean {
            return baseColor != null || metallic != null && metallic != 0f || roughness != null && roughness != 1f
        }

        private fun readPbr(
            aiMaterial: AIMaterial, baseColor: Color?, metallic: Float?, roughness: Float?,
            stack: MemoryStack
        ): PbrModelMaterial {
            val material = PbrModelMaterial()
            material.metallic = metallic
            material.roughness = roughness
            material.baseColor = baseColor
            val twoSided: Int? = readInt(aiMaterial, Assimp.AI_MATKEY_TWOSIDED, stack)
            material.doubleSided = twoSided != null && twoSided != 0

            val alphaMode = AIString.calloc(stack)
            if (Assimp.aiGetMaterialString(
                    aiMaterial,
                    Assimp.AI_MATKEY_GLTF_ALPHAMODE,
                    Assimp.aiTextureType_NONE,
                    0,
                    alphaMode
                )
                == Assimp.aiReturn_SUCCESS
            ) {
                when (alphaMode.dataString()) {
                    "MASK" -> material.alphaMode = AlphaMode.MASK
                    "BLEND" -> material.alphaMode = AlphaMode.BLEND
                    else -> material.alphaMode = AlphaMode.OPAQUE
                }
            }
            val cutoff: Float? = readFloat(aiMaterial, Assimp.AI_MATKEY_GLTF_ALPHACUTOFF, stack)
            if (cutoff != null) {
                material.alphaCutoff = cutoff
            }
            return material
        }

        private fun readName(aiMaterial: AIMaterial, stack: MemoryStack): String? {
            val name = AIString.calloc(stack)
            if (Assimp.aiGetMaterialString(
                    aiMaterial,
                    Assimp.AI_MATKEY_NAME,
                    Assimp.aiTextureType_NONE,
                    0,
                    name
                ) != Assimp.aiReturn_SUCCESS
            ) {
                return null
            }
            return name.dataString()
        }

        private fun readColor(aiMaterial: AIMaterial, key: String, stack: MemoryStack): Color? {
            val color = AIColor4D.calloc(stack)
            if (Assimp.aiGetMaterialColor(
                    aiMaterial,
                    key,
                    Assimp.aiTextureType_NONE,
                    0,
                    color
                ) != Assimp.aiReturn_SUCCESS
            ) {
                return null
            }
            return Color(color.r(), color.g(), color.b(), color.a())
        }

        private fun readInt(aiMaterial: AIMaterial, key: String, stack: MemoryStack): Int? {
            val value = stack.mallocInt(1)
            val max = stack.ints(1)
            if (Assimp.aiGetMaterialIntegerArray(
                    aiMaterial,
                    key,
                    Assimp.aiTextureType_NONE,
                    0,
                    value,
                    max
                ) != Assimp.aiReturn_SUCCESS
            ) {
                return null
            }
            return value.get(0)
        }

        private fun readFloat(aiMaterial: AIMaterial, key: String, stack: MemoryStack): Float? {
            val value = stack.mallocFloat(1)
            val max = stack.ints(1)
            if (Assimp.aiGetMaterialFloatArray(
                    aiMaterial,
                    key,
                    Assimp.aiTextureType_NONE,
                    0,
                    value,
                    max
                ) != Assimp.aiReturn_SUCCESS
            ) {
                return null
            }
            return value.get(0)
        }

        private fun hasUsage(material: ModelMaterial, usage: Int): Boolean {
            for (texture in material.textures) {
                if (texture.usage == usage) {
                    return true
                }
            }
            return false
        }
    }
}
