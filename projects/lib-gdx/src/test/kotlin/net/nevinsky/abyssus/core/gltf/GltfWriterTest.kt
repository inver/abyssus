/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.gdx.gltf

import com.badlogic.gdx.files.FileHandle
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.VertexAttribute
import com.badlogic.gdx.graphics.g3d.model.data.ModelNodePart
import com.badlogic.gdx.graphics.g3d.model.data.ModelTexture
import com.badlogic.gdx.graphics.g3d.model.data.ModelNode
import com.badlogic.gdx.utils.JsonReader
import com.badlogic.gdx.utils.JsonValue
import net.nevinsky.abyssus.lib.gdx.assimp.AssimpModelDataLoader
import net.nevinsky.abyssus.lib.gdx.loader.AssimpModelLoader
import net.nevinsky.abyssus.lib.gdx.model.ModelData
import net.nevinsky.abyssus.lib.gdx.model.ModelMesh
import net.nevinsky.abyssus.lib.gdx.model.ModelMeshPart
import net.nevinsky.abyssus.lib.gdx.model.PbrModelMaterial
import net.nevinsky.abyssus.model.GridModel
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.awt.image.BufferedImage
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import javax.imageio.ImageIO
import com.badlogic.gdx.utils.Array as GdxArray

/** The JSON chunk of a GLB, after checking its header and chunk layout. */
fun glbJson(glb: ByteArray): JsonValue {
    val data = ByteBuffer.wrap(glb).order(ByteOrder.LITTLE_ENDIAN)
    assertEquals(0x46546C67, data.getInt(0))
    assertEquals(2, data.getInt(4))
    assertEquals(glb.size, data.getInt(8))
    val jsonLength = data.getInt(12)
    assertEquals(0x4E4F534A, data.getInt(16))
    assertEquals(0, jsonLength % 4)
    val json = JsonReader().parse(String(glb, 20, jsonLength))
    if (glb.size == 20 + jsonLength) {
        assertTrue(json["buffers"] == null)
        return json
    }
    val binLength = data.getInt(20 + jsonLength)
    assertEquals(0x004E4942, data.getInt(24 + jsonLength))
    assertEquals(0, binLength % 4)
    assertEquals(glb.size, 28 + jsonLength + binLength)
    assertEquals(binLength, json["buffers"][0].getInt("byteLength"))
    return json
}

class GltfWriterTest {
    private fun resource(path: String, name: String = path.substringAfterLast('/')): File {
        val dir = Files.createTempDirectory("abyssus_gltf").toFile()
        val target = File(dir, name)
        javaClass.getResourceAsStream(path)!!.use { input -> target.outputStream().use { input.copyTo(it) } }
        return target
    }

    private fun load(file: File): ModelData = AssimpModelDataLoader().load(file.name, FileHandle(file))

    private fun reread(glb: ByteArray): ModelData {
        val file = File(Files.createTempDirectory("abyssus_glb").toFile(), "model.glb")
        file.writeBytes(glb)
        return load(file)
    }

    private fun tree(node: ModelNode): String =
        node.id + node.children.orEmpty().joinToString(",", "(", ")") { tree(it) }

    private fun vertexCounts(data: ModelData) =
        data.meshes.map { mesh -> mesh.vertices.size / mesh.attributes.sumOf { it.numComponents } }.sorted()

    @Test
    fun theGlbHeaderAndChunksAreConsistent() {
        val json = glbJson(GltfWriter().write(load(resource("/model/crate.dae")), emptyMap(), "test"))
        assertEquals("2.0", json["asset"].getString("version"))
        assertEquals("test", json["asset"].getString("generator"))
        json["bufferViews"].forEach { assertEquals(0, it.getInt("byteOffset") % 4) }
        val position = json["accessors"][json["meshes"][0]["primitives"][0]["attributes"].getInt("POSITION")]
        assertEquals(3, position["min"].size)
        assertEquals(3, position["max"].size)
    }

    @Test
    fun theSameInputWritesTheSameBytes() {
        val data = load(resource("/model/animated.gltf", "model.gltf"))
        assertArrayEquals(GltfWriter().write(data, emptyMap(), "test"), GltfWriter().write(data, emptyMap(), "test"))
    }

    @Test
    fun aLargeMeshIsWrittenWith32BitIndices() {
        val data = load(GridModel(300).write())
        val glb = GltfWriter().write(data, emptyMap(), "test")
        val json = glbJson(glb)
        val indices = json["accessors"][json["meshes"][0]["primitives"][0].getInt("indices")]
        assertEquals(5125, indices.getInt("componentType"))

        val back = reread(glb)
        assertEquals(vertexCounts(data), vertexCounts(back))
        assertEquals(data.meshes.first().parts.first().indices.size, back.meshes.first().parts.first().indices.size)
    }

    @Test
    fun aSmallMeshIsWrittenWith16BitIndices() {
        val json = glbJson(GltfWriter().write(load(resource("/model/crate.dae")), emptyMap(), "test"))
        assertEquals(5123, json["accessors"][json["meshes"][0]["primitives"][0].getInt("indices")].getInt("componentType"))
    }

    @Test
    fun aStaticModelSurvivesAWriteAndReread() {
        val data = load(resource("/model/crate.dae"))
        data.materials.first().diffuse!!.set(0.25f, 0.5f, 0.75f, 1f)
        val back = reread(GltfWriter().write(data, emptyMap(), "test"))

        assertEquals(data.nodes.map(::tree), back.nodes.map(::tree))
        assertEquals(vertexCounts(data), vertexCounts(back))
        val material = back.materials.first() as PbrModelMaterial
        assertEquals(0.25f, material.baseColor!!.r, 1e-6f)
        assertEquals(0.5f, material.baseColor!!.g, 1e-6f)
        assertEquals(0.75f, material.baseColor!!.b, 1e-6f)
        assertEquals(0f, material.metallic!!, 0f)
        assertEquals(PhongToPbr().convert(data.materials.first()).material.roughness!!, material.roughness!!, 1e-6f)
    }

    @Test
    fun anAnimatedSkinnedModelSurvivesAWriteAndReread() {
        val data = load(resource("/model/animated.gltf", "model.gltf"))
        val glb = GltfWriter().write(data, emptyMap(), "test")
        val back = reread(glb)

        assertEquals(data.nodes.map(::tree), back.nodes.map(::tree))
        assertEquals(vertexCounts(data), vertexCounts(back))
        assertEquals(data.animations.map { it.id }, back.animations.map { it.id })
        fun keys(d: ModelData) = d.animations.flatMap { a ->
            a.nodeAnimations.map { "${it.nodeId}:${it.translation?.size}:${it.rotation?.size}:${it.scaling?.size}" }
        }.sorted()
        assertEquals(keys(data), keys(back))
        val bones = { d: ModelData -> d.nodes.flatMap { n -> allNodes(n) }.flatMap { it.parts.orEmpty().toList() }.mapNotNull { it.bones?.keys()?.toArray()?.toList() } }
        assertEquals(bones(data), bones(back))
        val json = glbJson(glb)
        assertEquals(1, json["skins"].size)
        assertEquals(2, json["skins"][0]["joints"].size)
    }

    private fun allNodes(node: ModelNode): List<ModelNode> = listOf(node) + node.children.orEmpty().flatMap { allNodes(it) }

    @Test
    fun texturesAreExternalImages() {
        val data = load(resource("/model/crate.dae"))
        val fileName = data.materials.first().textures.first().fileName
        val json = glbJson(GltfWriter().write(data, mapOf(fileName to "textures/wood.png"), "test"))
        assertEquals("textures/wood.png", json["images"][0].getString("uri"))
        assertEquals(0, json["materials"][0]["pbrMetallicRoughness"]["baseColorTexture"].getInt("index"))

        val without = glbJson(GltfWriter().write(data, emptyMap(), "test"))
        assertTrue(without["images"] == null)
        assertTrue(without["materials"][0]["pbrMetallicRoughness"]["baseColorTexture"] == null)
    }

    /** Two quads with a textured opaque material and a blended, double-sided one (the FlightGear import's shape). */
    private fun quads(): ModelData {
        val data = ModelData()
        fun quad(index: Int, y: Float) {
            val part = ModelMeshPart().apply { id = "part_$index"; indices = intArrayOf(0, 1, 2, 0, 2, 3); primitiveType = GL20.GL_TRIANGLES }
            data.addMesh(ModelMesh().apply {
                id = "mesh_$index"
                attributes = arrayOf(VertexAttribute.Position(), VertexAttribute.Normal(), VertexAttribute.TexCoords(0))
                vertices = floatArrayOf(
                    0f, y, 0f, 0f, 1f, 0f, 0f, 0f,
                    1f, y, 0f, 0f, 1f, 0f, 1f, 0f,
                    1f, y, 1f, 0f, 1f, 0f, 1f, 1f,
                    0f, y, 1f, 0f, 1f, 0f, 0f, 1f,
                )
                parts = arrayOf(part)
            })
        }
        quad(0, 0f)
        quad(1, 1f)
        data.materials.add(PbrModelMaterial().apply {
            id = "skin"; baseColor = Color.WHITE; metallic = 0f; roughness = 0.8f
            textures = GdxArray<ModelTexture>().apply { add(ModelTexture().apply { usage = ModelTexture.USAGE_DIFFUSE; fileName = "textures/skin.png" }) }
        })
        data.materials.add(PbrModelMaterial().apply {
            id = "glass"; baseColor = Color(0.2f, 0.3f, 0.4f, 0.5f); metallic = 0f; roughness = 0.8f
            alphaMode = PbrModelMaterial.AlphaMode.BLEND; doubleSided = true; textures = GdxArray()
        })
        for ((name, i) in listOf("Body" to 0, "Wing" to 1)) {
            data.nodes.add(ModelNode().apply {
                id = name
                parts = arrayOf(ModelNodePart().apply { meshPartId = "part_$i"; materialId = if (i == 0) "skin" else "glass" })
            })
        }
        return data
    }

    @Test
    fun materialsKeepTheirAlphaSidesAndBounds() {
        val json = glbJson(GltfWriter().write(quads(), mapOf("textures/skin.png" to "textures/skin.png"), "test"))
        assertEquals(listOf("Body", "Wing"), json["nodes"].map { it.getString("name") })
        val position = json["accessors"][json["meshes"][1]["primitives"][0]["attributes"].getInt("POSITION")]
        assertEquals(listOf(0f, 1f, 0f), position["min"].asFloatArray().toList())
        assertEquals(listOf(1f, 1f, 1f), position["max"].asFloatArray().toList())
        assertEquals("BLEND", json["materials"][1].getString("alphaMode"))
        assertTrue(json["materials"][1].getBoolean("doubleSided"))
        assertTrue(json["materials"][0]["alphaMode"] == null)
        assertEquals("textures/skin.png", json["images"][0].getString("uri"))
    }

    @Test
    fun theModelLoaderReadsItBackWithItsTexture() {
        val folder = Files.createTempDirectory("abyssus_quads").toFile()
        File(folder, "textures").mkdirs()
        ImageIO.write(BufferedImage(4, 2, BufferedImage.TYPE_INT_RGB), "png", File(folder, "textures/skin.png"))
        val file = File(folder, "model.glb").apply { writeBytes(GltfWriter().write(quads(), mapOf("textures/skin.png" to "textures/skin.png"), "test")) }
        val data = AssimpModelLoader().loadData(FileHandle(file))
        assertEquals(2, data.meshes.size)
        val textures = data.materials.flatMap { it.textures?.toList().orEmpty() }.map { File(it.fileName).name }
        assertEquals(listOf("skin.png"), textures)
        assertTrue(!File(folder, "embedded").exists())
    }

    @Test
    fun weightsThatDoNotSumToOneAreRejected() {
        val data = load(resource("/model/animated.gltf", "model.gltf"))
        val mesh = data.meshes.first()
        val stride = mesh.attributes.sumOf { it.numComponents }
        val weightAt = mesh.attributes.takeWhile { it.usage != com.badlogic.gdx.graphics.VertexAttributes.Usage.BoneWeight }
            .sumOf { it.numComponents } + 1
        mesh.vertices[stride + weightAt] = mesh.vertices[stride + weightAt] + 0.5f
        try {
            GltfWriter().write(data, emptyMap(), "test")
            fail("expected the weights to be rejected")
        } catch (e: GltfWriteException) {
            assertTrue(e.message!!, e.message!!.contains("weights summing"))
        }
    }
}
