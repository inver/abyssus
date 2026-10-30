/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.gdx.g3d

import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.GLTexture
import com.badlogic.gdx.graphics.VertexAttributes.Usage
import com.badlogic.gdx.graphics.g3d.Attributes
import com.badlogic.gdx.graphics.g3d.Environment
import com.badlogic.gdx.graphics.g3d.Material
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute
import com.badlogic.gdx.graphics.g3d.shaders.DefaultShader
import com.badlogic.gdx.math.Matrix4
import net.nevinsky.abyssus.lib.gdx.model.PBRFloatAttribute
import net.nevinsky.abyssus.lib.gdx.model.PBRTextureAttribute
import net.nevinsky.abyssus.lib.gdx.shader.ClipPlaneAttribute
import net.nevinsky.abyssus.lib.gdx.shader.DirectionalVisibilityAttribute
import net.nevinsky.abyssus.lib.gdx.shader.EnvironmentLightAttribute
import net.nevinsky.abyssus.lib.gdx.shader.ShadowAtlasAttribute
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The `#define` builders need no GL context. */
class ShaderDefinesTest {
    private val position = Usage.Position.toLong()
    private val lit = position or Usage.Normal.toLong()

    @Test
    fun nothingExtraForAPlainMaterial() {
        assertEquals("", extraDefines(Attributes(), lit, hasEnvironment = true, instanced = false))
    }

    @Test
    fun theInstancedVariantDefinesInstancedFlag() {
        assertEquals("#define instancedFlag\n", extraDefines(Attributes(), position, hasEnvironment = false, instanced = true))
    }

    @Test
    fun aClipPlaneDefinesTheFlagEvenWithoutLighting() {
        val attributes = Attributes().apply { set(ClipPlaneAttribute(0f, 1f, 0f, 0f)) }
        assertEquals("#define clipPlaneFlag\n", extraDefines(attributes, position, hasEnvironment = false, instanced = false))
    }

    @Test
    fun shadowAndVisibilityFlagsNeedAnEnvironmentAndNormals() {
        val attributes = Attributes().apply {
            set(ShadowAtlasAttribute(FakeTexture(), emptyList()))
            set(directionalVisibility())
        }
        val both = "#define shadowAtlasFlag\n#define directionalVisibilityFlag\n"
        assertEquals(both, extraDefines(attributes, lit, hasEnvironment = true, instanced = false))
        assertEquals("", extraDefines(attributes, lit, hasEnvironment = false, instanced = false))
        assertEquals("", extraDefines(attributes, position, hasEnvironment = true, instanced = false))
        val tangents = position or Usage.Tangent.toLong() or Usage.BiNormal.toLong()
        assertEquals(both, extraDefines(attributes, tangents, hasEnvironment = true, instanced = false))
    }

    @Test
    fun pbrDefinesFollowTheMaterialMask() {
        val mask = Material(
            PBRFloatAttribute.createMetallic(0.5f), PBRFloatAttribute.createRoughness(0.5f)
        ).mask
        assertEquals("#define metallicFactorFlag\n#define roughnessFactorFlag\n", PbrModelShader.pbrDefines(mask, null))
        assertEquals("", PbrModelShader.pbrDefines(0L, null))
        assertEquals(
            "#define metallicRoughnessTextureFlag\n#define occlusionTextureFlag\n",
            PbrModelShader.pbrDefines(PBRTextureAttribute.MetallicRoughnessTexture or PBRTextureAttribute.OcclusionTexture, null)
        )
    }

    @Test
    fun anEnvironmentLightAddsTheFlagAndAFadeAddsTheSecond() {
        val sky = EnvironmentLightAttribute(FakeTexture(), FakeTexture(), 1, FloatArray(18))
        val fading = EnvironmentLightAttribute(FakeTexture(), FakeTexture(), 1, FloatArray(18), sky, 0.5f)
        val plain = Environment().apply { set(ColorAttribute.createAmbientLight(0.1f, 0.1f, 0.1f, 1f)) }
        val lit = Environment().apply { set(sky) }
        val fade = Environment().apply { set(fading) }
        assertFalse(PbrModelShader.pbrDefines(PBRFloatAttribute.Metallic, plain).contains("environmentLightFlag"))
        assertTrue(PbrModelShader.pbrDefines(PBRFloatAttribute.Metallic, lit).contains("#define environmentLightFlag\n"))
        assertFalse(PbrModelShader.pbrDefines(PBRFloatAttribute.Metallic, lit).contains("environmentFadeFlag"))
        assertTrue(PbrModelShader.pbrDefines(PBRFloatAttribute.Metallic, fade).contains("#define environmentFadeFlag\n"))
    }

    @Test
    fun theProviderRaisesTheBoneCountInStepsAndKeepsTheConfiguredOnesOtherwise() {
        val config = DefaultShader.Config().apply { numBones = 12; numPointLights = 2 }
        assertEquals(12, ModelShaderProvider.fitBones(config, 0).numBones)
        assertEquals(12, ModelShaderProvider.fitBones(config, 12).numBones)
        assertEquals(16, ModelShaderProvider.fitBones(config, 13).numBones)
        assertEquals(24, ModelShaderProvider.fitBones(config, 17).numBones)
        val fitted = ModelShaderProvider.fitBones(config, 30)
        assertEquals(32, fitted.numBones)
        assertEquals(2, fitted.numPointLights)
        assertEquals("every weight slot the vertex shader has", 8, fitted.numBoneWeights)
        assertEquals("the caller's config is untouched", 12, config.numBones)
    }

    // the attributes only keep this reference; no GL call is made on it
    private class FakeTexture : GLTexture(GL20.GL_TEXTURE_2D, 3) {
        override fun getWidth() = 1
        override fun getHeight() = 1
        override fun getDepth() = 0
        override fun isManaged() = false
        override fun reload() = Unit
    }

    private fun directionalVisibility() = DirectionalVisibilityAttribute(FakeTexture(), 0, Matrix4())
}
