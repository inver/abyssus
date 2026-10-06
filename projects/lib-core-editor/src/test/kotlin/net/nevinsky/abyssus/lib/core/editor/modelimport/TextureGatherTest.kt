/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.editor.modelimport

import com.badlogic.gdx.graphics.g3d.model.data.ModelMaterial
import com.badlogic.gdx.graphics.g3d.model.data.ModelTexture
import com.badlogic.gdx.utils.Array
import net.nevinsky.abyssus.lib.core.model.ModelData
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File
import javax.imageio.ImageIO

class TextureGatherTest {
    private val fixtures = importFixtures()

    private fun material(id: String, vararg files: File) = ModelMaterial().apply {
        this.id = id
        textures = Array()
        files.forEach { file -> textures.add(ModelTexture().apply { usage = ModelTexture.USAGE_DIFFUSE; fileName = file.path }) }
    }

    private fun data(vararg materials: ModelMaterial) = ModelData().apply { materials.forEach { this.materials.add(it) } }

    @Test
    fun twoMaterialsUsingOneImageShareOneFile() {
        val wood = File(fixtures, "wood.png")
        val copy = File(fixtures, "missing/also_wood.png").apply { writeBytes(wood.readBytes()) }
        val gathered = TextureGather().gather(data(material("a", wood), material("b", wood), material("c", copy)))
        assertEquals(listOf("textures/wood.png"), gathered.files.keys.toList())
        assertArrayEquals("a PNG is kept as it is", wood.readBytes(), gathered.files["textures/wood.png"])
        assertEquals(mapOf(wood.path to "textures/wood.png", copy.path to "textures/wood.png"), gathered.uris)
        assertEquals(emptyList<LeftOutItem>(), gathered.leftOut)
    }

    @Test
    fun aTgaIsReportedUnsupported() {
        val tga = File(fixtures, "skin.tga").apply { writeBytes(ByteArray(18 + 4).also { it[2] = 2; it[12] = 1; it[14] = 1; it[16] = 32 }) }
        val gathered = TextureGather().gather(data(material("a", tga), material("b", File(fixtures, "gone.png"))))
        assertEquals(
            listOf(LeftOutItem("skin.tga", LeftOutReason.UNSUPPORTED_TEXTURE), LeftOutItem("gone.png", LeftOutReason.MISSING)),
            gathered.leftOut,
        )
        assertEquals(emptyMap<String, String>(), gathered.uris)
    }

    @Test
    fun aJpegIsReencodedAsPng() {
        val jpeg = File(fixtures, "wood.jpg")
        ImageIO.write(ImageIO.read(File(fixtures, "wood.png")), "jpg", jpeg)
        val gathered = TextureGather().gather(data(material("a", jpeg)))
        val png = gathered.files.getValue("textures/wood.png")
        assertEquals(0x89.toByte(), png[0])
        assertEquals(16, ImageIO.read(png.inputStream()).width)
    }

    @Test
    fun anFbxEmbeddedTextureBecomesAPng() {
        ModelSourceOpener().open(File(fixtures, "rig.fbx")).use { source ->
            val gathered = TextureGather().gather(source.data)
            assertEquals(1, gathered.files.size)
            val (path, bytes) = gathered.files.entries.single()
            assertEquals("textures/embedded_0.png", path)
            assertEquals(16, ImageIO.read(bytes.inputStream()).width)
        }
    }
}
