/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.model

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g3d.Material
import com.badlogic.gdx.graphics.g3d.attributes.TextureAttribute
import com.badlogic.gdx.graphics.g3d.attributes.BlendingAttribute
import com.badlogic.gdx.graphics.g3d.attributes.FloatAttribute
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute
import com.badlogic.gdx.graphics.g3d.utils.TextureDescriptor
import net.nevinsky.abyssus.lib.core.model.PBRColorAttribute
import net.nevinsky.abyssus.lib.core.model.PBRFloatAttribute
import net.nevinsky.abyssus.lib.core.model.PBRTextureAttribute
import net.nevinsky.abyssus.lib.core.model.Model
import net.nevinsky.abyssus.lib.core.model.ModelData
import net.nevinsky.abyssus.lib.core.model.PbrModelMaterial
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PbrAttributesTest {
    @Test
    fun importedMaskWithNonUnitAlphaRemainsACutoutCaster() {
        val input = PbrModelMaterial().apply {
            id = "mask"
            alphaMode = PbrModelMaterial.AlphaMode.MASK
            baseColor = Color(1f, 1f, 1f, 0.6f)
            opacity = 0.6f
        }
        val model = Model(ModelData().also { it.materials.add(input) })
        try {
            val material = model.materials.getValue("mask")
            assertTrue(material.has(FloatAttribute.AlphaTest))
            assertFalse(material.has(BlendingAttribute.Type))
            assertEquals(0.6f, (material.get(ColorAttribute.Diffuse) as ColorAttribute).color.a, 0f)
        } finally { model.dispose() }
    }

    @Test
    fun ownTypesAreDistinct() {
        val own = listOf(
            PBRColorAttribute.BaseColorFactor, PBRFloatAttribute.Metallic, PBRFloatAttribute.Roughness,
            PBRTextureAttribute.MetallicRoughnessTexture, PBRTextureAttribute.OcclusionTexture,
        )
        assertEquals(own.size, own.toSet().size)
        own.forEach { assertEquals("one bit per type", 1, java.lang.Long.bitCount(it)) }
    }

    /** As in gdx-gltf, the base color, normal and emissive textures are libGDX's own texture types. */
    @Test
    fun sharedTexturesUseTheLibGdxTypes() {
        assertEquals(TextureAttribute.Diffuse, PBRTextureAttribute.BaseColorTexture)
        assertEquals(TextureAttribute.Normal, PBRTextureAttribute.NormalTexture)
        assertEquals(TextureAttribute.Emissive, PBRTextureAttribute.EmissiveTexture)
    }

    @Test
    fun materialFindsAndCopiesThem() {
        val material = Material(
            PBRColorAttribute.createBaseColorFactor(Color.RED),
            PBRFloatAttribute.createMetallic(0.25f),
            PBRTextureAttribute(PBRTextureAttribute.OcclusionTexture, TextureDescriptor<Texture>()),
        )
        assertTrue(material.has(PBRFloatAttribute.Metallic))
        assertFalse(material.has(PBRFloatAttribute.Roughness))
        assertTrue(material.has(PBRTextureAttribute.OcclusionTexture))

        val copy = material.copy()
        assertTrue(copy.get(PBRFloatAttribute.Metallic) is PBRFloatAttribute)
        assertTrue(copy.get(PBRColorAttribute.BaseColorFactor) is PBRColorAttribute)
        assertTrue(copy.get(PBRTextureAttribute.OcclusionTexture) is PBRTextureAttribute)
        assertEquals(material, copy)
        assertNotEquals(material.getMask() and PBRFloatAttribute.Metallic, 0L)
    }
}
