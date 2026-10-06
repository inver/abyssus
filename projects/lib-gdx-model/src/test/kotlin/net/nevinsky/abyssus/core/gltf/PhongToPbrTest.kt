/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.gltf

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.g3d.model.data.ModelMaterial
import com.badlogic.gdx.graphics.g3d.model.data.ModelTexture
import com.badlogic.gdx.utils.Array
import net.nevinsky.abyssus.lib.core.model.PbrModelMaterial
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class PhongToPbrTest {
    private fun phong(configure: ModelMaterial.() -> Unit) = ModelMaterial().apply {
        id = "m"
        textures = Array()
        configure()
    }

    private fun texture(usage: Int) = ModelTexture().apply { this.usage = usage; fileName = "t$usage.png" }

    @Test
    fun diffuseBecomesTheBaseColourAndMetallicIsZero() {
        val result = PhongToPbr().convert(phong { diffuse = Color(0.5f, 0.25f, 1f, 1f); textures.add(texture(ModelTexture.USAGE_DIFFUSE)) })
        assertEquals(Color(0.5f, 0.25f, 1f, 1f), result.material.baseColor)
        assertEquals(0f, result.material.metallic!!, 0f)
        assertEquals(listOf(ModelTexture.USAGE_DIFFUSE), result.material.textures.map { it.usage })
        assertTrue(result.approximated.isEmpty())
    }

    @Test
    fun shininessMapsToRoughness() {
        assertEquals(0.5f, PhongToPbr().convert(phong { shininess = 250f }).material.roughness!!, 1e-6f)
        assertEquals(0f, PhongToPbr().convert(phong { shininess = 5000f }).material.roughness!!, 1e-6f)
    }

    @Test
    fun withoutShininessTheRoughnessIsTheDefault() {
        assertEquals(PhongToPbr.DEFAULT_ROUGHNESS, PhongToPbr().convert(phong { }).material.roughness!!, 0f)
    }

    @Test
    fun opacityBelowOneBlends() {
        val material = PhongToPbr().convert(phong { diffuse = Color.RED; opacity = 0.4f }).material
        assertEquals(PbrModelMaterial.AlphaMode.BLEND, material.alphaMode)
        assertEquals(0.4f, material.baseColor!!.a, 1e-6f)
        assertEquals(PbrModelMaterial.AlphaMode.OPAQUE, PhongToPbr().convert(phong { }).material.alphaMode)
    }

    @Test
    fun specularIsReportedAsDropped() {
        val result = PhongToPbr().convert(phong {
            specular = Color(0.9f, 0.9f, 0.9f, 1f)
            textures.add(texture(ModelTexture.USAGE_SPECULAR))
            textures.add(texture(ModelTexture.USAGE_NORMAL))
        })
        assertEquals(listOf("specular colour dropped", "specular map dropped"), result.approximated)
        assertEquals(listOf(ModelTexture.USAGE_NORMAL), result.material.textures.map { it.usage })
    }

    @Test
    fun aBlackSpecularIsNotReported() {
        assertTrue(PhongToPbr().convert(phong { specular = Color(0f, 0f, 0f, 1f) }).approximated.isEmpty())
    }

    @Test
    fun aPbrMaterialIsKept() {
        val pbr = PbrModelMaterial().apply { metallic = 1f; roughness = 0.3f }
        val result = PhongToPbr().convert(pbr)
        assertSame(pbr, result.material)
        assertTrue(result.approximated.isEmpty())
    }
}
