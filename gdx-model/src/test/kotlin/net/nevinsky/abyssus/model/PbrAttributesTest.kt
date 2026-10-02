/*
 * Copyright 2023-2026 Alexey Nevinsky
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.nevinsky.abyssus.model

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g3d.Material
import com.badlogic.gdx.graphics.g3d.attributes.TextureAttribute
import com.badlogic.gdx.graphics.g3d.utils.TextureDescriptor
import net.nevinsky.abyssus.core.model.PBRColorAttribute
import net.nevinsky.abyssus.core.model.PBRFloatAttribute
import net.nevinsky.abyssus.core.model.PBRTextureAttribute
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PbrAttributesTest {
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
