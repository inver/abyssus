/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.gdx.gltf

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.g3d.model.data.ModelMaterial
import com.badlogic.gdx.graphics.g3d.model.data.ModelTexture
import com.badlogic.gdx.utils.Array
import net.nevinsky.abyssus.lib.gdx.model.PbrModelMaterial
import kotlin.math.sqrt

/**
 * Maps a Phong-style [ModelMaterial] (Assimp's OBJ, FBX, 3DS and DAE path) to metallic-roughness:
 * - base colour = diffuse colour, with the opacity as its alpha; the diffuse texture becomes the base colour texture;
 * - metallic = 0;
 * - roughness = `1 - sqrt(clamp(shininess / 1000, 0, 1))`, or [DEFAULT_ROUGHNESS] without a shininess;
 * - an opacity below 1 blends;
 * - normal and emissive maps and the emissive colour are kept.
 *
 * Specular colour and the specular, shininess, ambient, bump and reflection maps cannot be kept: they are dropped, and
 * each is named in [Result.approximated]. A [PbrModelMaterial] is returned as it is.
 */
class PhongToPbr {
    /** The PBR [material] and what it could not keep. */
    class Result(val material: PbrModelMaterial, val approximated: List<String>)

    fun convert(material: ModelMaterial): Result {
        if (material is PbrModelMaterial) {
            return Result(material, emptyList())
        }
        val approximated = ArrayList<String>()
        val pbr = PbrModelMaterial()
        pbr.id = material.id
        pbr.diffuse = material.diffuse?.let(::Color)
        pbr.emissive = material.emissive?.let(::Color)
        pbr.opacity = material.opacity
        pbr.shininess = material.shininess
        pbr.baseColor = Color(material.diffuse ?: Color.WHITE).also { it.a = material.opacity.coerceIn(0f, 1f) }
        pbr.metallic = 0f
        pbr.roughness = if (material.shininess > 0f) {
            1f - sqrt((material.shininess / MAX_SHININESS).coerceIn(0f, 1f))
        } else {
            DEFAULT_ROUGHNESS
        }
        if (material.opacity < 1f) {
            pbr.alphaMode = PbrModelMaterial.AlphaMode.BLEND
        }
        val specular = material.specular
        if (specular != null && (specular.r > 0f || specular.g > 0f || specular.b > 0f)) {
            approximated.add("specular colour dropped")
        }
        pbr.textures = Array()
        for (texture in material.textures ?: Array()) {
            when (texture.usage) {
                ModelTexture.USAGE_DIFFUSE, ModelTexture.USAGE_NORMAL, ModelTexture.USAGE_EMISSIVE ->
                    pbr.textures.add(texture)
                else -> approximated.add("${usageName(texture.usage)} map dropped")
            }
        }
        return Result(pbr, approximated.distinct())
    }

    private fun usageName(usage: Int): String = when (usage) {
        ModelTexture.USAGE_SPECULAR -> "specular"
        ModelTexture.USAGE_SHININESS -> "shininess"
        ModelTexture.USAGE_AMBIENT -> "ambient"
        ModelTexture.USAGE_BUMP -> "bump"
        ModelTexture.USAGE_REFLECTION -> "reflection"
        ModelTexture.USAGE_TRANSPARENCY -> "transparency"
        else -> "texture ($usage)"
    }

    companion object {
        const val DEFAULT_ROUGHNESS = 0.8f

        /** The shininess that maps to a roughness of 0. */
        const val MAX_SHININESS = 1000f
    }
}
