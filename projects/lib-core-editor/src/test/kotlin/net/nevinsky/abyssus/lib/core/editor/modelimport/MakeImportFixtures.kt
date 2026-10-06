/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.editor.modelimport

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import net.nevinsky.abyssus.lib.core.assimp.AssimpImporter
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.lwjgl.assimp.Assimp
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Base64

/**
 * Writes the binary model import fixtures into this module's `src/test/resources/modelimport`: `crate.glb` (packed here), and
 * `box.3ds` and `rig.fbx`, built here as text (OBJ, glTF) and written through Assimp's exporters. Runs only with
 * `-Dabyssus.makeFixtures=true`; the committed files are pinned by [ModelSourceTest.fixturesArePinned].
 */
class MakeImportFixtures {
    private val dir = File(System.getProperty("abyssus.importFixtures"))
    private val mapper = ObjectMapper()

    @Test
    fun make() {
        assumeTrue(System.getProperty("abyssus.makeFixtures") == "true")
        val wood = File(dir, "wood.png").readBytes()
        File(dir, "crate.glb").writeBytes(crateGlb(wood))
        export(box3dsObj(), "box.obj", "3ds", File(dir, "box.3ds"), extra = mapOf("box.mtl" to BOX_MTL, "wood.png" to wood))
        export(rigGltf(wood), "rig.gltf", "fbx", File(dir, "rig.fbx"))
        // Assimp's FBX exporter always states Y up; the rig is modelled Z up (3ds Max's axes: front -Y, right +X)
        val rig = File(dir, "rig.fbx")
        patchFbxIntProperty(rig, "UpAxis", 2)
        patchFbxIntProperty(rig, "FrontAxis", 1)
        patchFbxIntProperty(rig, "FrontAxisSign", -1)
    }

    private fun export(text: String, name: String, format: String, target: File, extra: Map<String, Any> = emptyMap()) {
        val work = kotlin.io.path.createTempDirectory("abyssus_fixture").toFile()
        val source = File(work, name).apply { writeText(text) }
        extra.forEach { (file, content) ->
            if (content is ByteArray) File(work, file).writeBytes(content) else File(work, file).writeText(content as String)
        }
        AssimpImporter.importScene(source.path, Assimp.aiProcess_Triangulate).use { imported ->
            val result = Assimp.aiExportScene(imported.scene()!!, format, target.path, 0)
            check(result == Assimp.aiReturn_SUCCESS) { "export of $name failed: ${Assimp.aiGetErrorString()}" }
        }
        work.deleteRecursively()
    }

    /** A 100 x 50 x 200 box standing along Z (the 3DS convention), textured with wood.png. */
    private fun box3dsObj(): String {
        val sb = StringBuilder("mtllib box.mtl\no box\n")
        for (i in 0 until 8) sb.append("v ${100 * (i and 1)} ${50 * (i shr 1 and 1)} ${200 * (i shr 2 and 1)}\n")
        sb.append("vt 0 0\nvt 1 0\nvt 1 1\nvt 0 1\nusemtl Wood\n")
        for (face in listOf(listOf(1, 0, 2, 3), listOf(4, 5, 7, 6), listOf(0, 4, 6, 2), listOf(5, 1, 3, 7), listOf(0, 1, 5, 4), listOf(6, 7, 3, 2))) {
            sb.append("f").apply { face.forEachIndexed { k, v -> append(" ${v + 1}/${k + 1}") } }.append('\n')
        }
        return sb.toString()
    }

    /**
     * A 20 cm square bar, 200 cm tall along Z, skinned to two joints (`hip` at the bottom, `spine` at 100 cm), with
     * the animations `Idle` (2 s) and `Run` (1 s) and one embedded texture.
     */
    private fun rigGltf(wood: ByteArray): String {
        val positions = ArrayList<Float>()
        val joints = ArrayList<Int>()
        val weights = ArrayList<Float>()
        val uvs = ArrayList<Float>()
        for (ring in 0..2) for (corner in 0..3) {
            val x = if (corner == 1 || corner == 2) 10f else -10f
            val y = if (corner >= 2) 10f else -10f
            positions += listOf(x, y, ring * 100f)
            uvs += listOf(corner / 3f, ring / 2f)
            when (ring) {
                0 -> { joints += listOf(0, 0, 0, 0); weights += listOf(1f, 0f, 0f, 0f) }
                1 -> { joints += listOf(0, 1, 0, 0); weights += listOf(0.5f, 0.5f, 0f, 0f) }
                else -> { joints += listOf(1, 0, 0, 0); weights += listOf(1f, 0f, 0f, 0f) }
            }
        }
        val indices = ArrayList<Int>()
        for (ring in 0..1) for (side in 0..3) {
            val a = ring * 4 + side
            val b = ring * 4 + (side + 1) % 4
            indices += listOf(a, b, b + 4, a, b + 4, a + 4)
        }
        indices += listOf(0, 2, 1, 0, 3, 2, 8, 9, 10, 8, 10, 11)

        val bin = Packer()
        val doc = mapper.createObjectNode()
        doc.putObject("asset").put("version", "2.0").put("generator", "Abyssus MakeImportFixtures")
        doc.put("scene", 0)
        doc.putArray("scenes").addObject().putArray("nodes").add(0).add(1)
        val nodes = doc.putArray("nodes")
        nodes.addObject().put("name", "bar").put("mesh", 0).put("skin", 0)
        nodes.addObject().put("name", "hip").putArray("children").add(2)
        nodes.addObject().put("name", "spine").putArray("translation").add(0).add(0).add(100)
        val accessors = doc.putArray("accessors")
        val views = doc.putArray("bufferViews")
        fun accessor(bytes: ByteArray, componentType: Int, count: Int, type: String, configure: ObjectNode.() -> Unit = {}): Int {
            views.addObject().put("buffer", 0).put("byteOffset", bin.add(bytes)).put("byteLength", bytes.size)
            accessors.addObject().put("bufferView", views.size() - 1).put("componentType", componentType).put("count", count)
                .put("type", type).apply(configure)
            return accessors.size() - 1
        }
        val position = accessor(floats(positions), 5126, 12, "VEC3") {
            putArray("min").add(-10).add(-10).add(0); putArray("max").add(10).add(10).add(200)
        }
        val uv = accessor(floats(uvs), 5126, 12, "VEC2")
        val joint = accessor(shorts(joints), 5123, 12, "VEC4")
        val weight = accessor(floats(weights), 5126, 12, "VEC4")
        val index = accessor(shorts(indices), 5123, indices.size, "SCALAR")
        val primitive = doc.putArray("meshes").addObject().put("name", "bar").putArray("primitives").addObject()
        primitive.putObject("attributes").put("POSITION", position).put("TEXCOORD_0", uv).put("JOINTS_0", joint).put("WEIGHTS_0", weight)
        primitive.put("indices", index).put("material", 0)
        val inverse = floats(listOf(identity(0f), identity(-100f)).flatten())
        doc.putArray("skins").addObject().put("inverseBindMatrices", accessor(inverse, 5126, 2, "MAT4"))
            .put("skeleton", 1).putArray("joints").add(1).add(2)

        val animations = doc.putArray("animations")
        fun animation(name: String, duration: Float, angle: Float) {
            val times = accessor(floats(listOf(0f, duration / 2, duration)), 5126, 3, "SCALAR") {
                putArray("min").add(0f); putArray("max").add(duration)
            }
            val s = kotlin.math.sin(Math.toRadians(angle / 2.0)).toFloat()
            val c = kotlin.math.cos(Math.toRadians(angle / 2.0)).toFloat()
            val rotations = accessor(floats(listOf(0f, 0f, 0f, 1f, s, 0f, 0f, c, 0f, 0f, 0f, 1f)), 5126, 3, "VEC4")
            val a = animations.addObject().put("name", name)
            a.putArray("samplers").addObject().put("input", times).put("output", rotations).put("interpolation", "LINEAR")
            a.putArray("channels").addObject().put("sampler", 0).putObject("target").put("node", 2).put("path", "rotation")
        }
        animation("Idle", 2f, 10f)
        animation("Run", 1f, 45f)

        doc.putArray("materials").addObject().put("name", "Wood").putObject("pbrMetallicRoughness")
            .putObject("baseColorTexture").put("index", 0)
        doc.putArray("textures").addObject().put("source", 0)
        doc.putArray("images").addObject().put("uri", "data:image/png;base64," + Base64.getEncoder().encodeToString(wood))
        doc.putArray("buffers").addObject().put("byteLength", bin.size())
            .put("uri", "data:application/octet-stream;base64," + Base64.getEncoder().encodeToString(bin.bytes()))
        return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(doc)
    }

    /** A 1 m cube centred on the origin, metallic 1 and roughness 0.3, its wood texture in the BIN chunk. */
    private fun crateGlb(wood: ByteArray): ByteArray {
        val positions = ArrayList<Float>()
        val normals = ArrayList<Float>()
        val uvs = ArrayList<Float>()
        val indices = ArrayList<Int>()
        for (axis in 0..2) for (sign in listOf(-1f, 1f)) {
            val u = (axis + 1) % 3
            val v = (axis + 2) % 3
            val first = positions.size / 3
            for ((k, corner) in listOf(-1f to -1f, 1f to -1f, 1f to 1f, -1f to 1f).withIndex()) {
                val p = FloatArray(3)
                p[axis] = sign * 0.5f; p[u] = corner.first * 0.5f; p[v] = corner.second * 0.5f
                positions += p.toList()
                normals += FloatArray(3).also { it[axis] = sign }.toList()
                uvs += listOf(if (k == 1 || k == 2) 1f else 0f, if (k >= 2) 0f else 1f)
            }
            // counter-clockwise seen from outside: u x v points along +axis
            if (sign > 0) indices += listOf(first, first + 1, first + 2, first, first + 2, first + 3)
            else indices += listOf(first, first + 2, first + 1, first, first + 3, first + 2)
        }
        val bin = Packer()
        val doc = mapper.createObjectNode()
        doc.putObject("asset").put("version", "2.0").put("generator", "Abyssus MakeImportFixtures")
        doc.put("scene", 0)
        doc.putArray("scenes").addObject().putArray("nodes").add(0)
        doc.putArray("nodes").addObject().put("name", "crate").put("mesh", 0)
        val views = doc.putArray("bufferViews")
        val accessors = doc.putArray("accessors")
        fun view(bytes: ByteArray): Int {
            views.addObject().put("buffer", 0).put("byteOffset", bin.add(bytes)).put("byteLength", bytes.size)
            return views.size() - 1
        }
        fun accessor(bytes: ByteArray, componentType: Int, count: Int, type: String): ObjectNode =
            accessors.addObject().put("bufferView", view(bytes)).put("componentType", componentType).put("count", count).put("type", type)
        accessor(floats(positions), 5126, 24, "VEC3").apply {
            putArray("min").add(-0.5).add(-0.5).add(-0.5); putArray("max").add(0.5).add(0.5).add(0.5)
        }
        accessor(floats(normals), 5126, 24, "VEC3")
        accessor(floats(uvs), 5126, 24, "VEC2")
        accessor(shorts(indices), 5123, indices.size, "SCALAR")
        val primitive = doc.putArray("meshes").addObject().put("name", "crate").putArray("primitives").addObject()
        primitive.putObject("attributes").put("POSITION", 0).put("NORMAL", 1).put("TEXCOORD_0", 2)
        primitive.put("indices", 3).put("material", 0)
        val pbr = doc.putArray("materials").addObject().put("name", "Metal").putObject("pbrMetallicRoughness")
        pbr.putObject("baseColorTexture").put("index", 0)
        pbr.put("metallicFactor", 1.0).put("roughnessFactor", 0.3)
        doc.putArray("textures").addObject().put("source", 0)
        doc.putArray("images").addObject().put("bufferView", view(wood)).put("mimeType", "image/png")
        doc.putArray("buffers").addObject().put("byteLength", bin.size())

        var json = mapper.writeValueAsBytes(doc)
        if (json.size % 4 != 0) json += ByteArray(4 - json.size % 4) { ' '.code.toByte() }
        val data = bin.bytes()
        val out = ByteBuffer.allocate(12 + 8 + json.size + 8 + data.size).order(ByteOrder.LITTLE_ENDIAN)
        out.putInt(0x46546C67).putInt(2).putInt(out.capacity())
        out.putInt(json.size).putInt(0x4E4F534A).put(json)
        out.putInt(data.size).putInt(0x004E4942).put(data)
        return out.array()
    }

    private fun identity(z: Float) = listOf(1f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, z, 1f)

    private fun floats(values: List<Float>): ByteArray =
        ByteBuffer.allocate(values.size * 4).order(ByteOrder.LITTLE_ENDIAN).also { b -> values.forEach { b.putFloat(it) } }.array()

    private fun shorts(values: List<Int>): ByteArray =
        ByteBuffer.allocate(values.size * 2).order(ByteOrder.LITTLE_ENDIAN).also { b -> values.forEach { b.putShort(it.toShort()) } }.array()

    private class Packer {
        private val out = ByteArrayOutputStream()
        fun add(bytes: ByteArray): Int {
            while (out.size() % 4 != 0) out.write(0)
            val offset = out.size()
            out.write(bytes)
            return offset
        }
        fun size(): Int { while (out.size() % 4 != 0) out.write(0); return out.size() }
        fun bytes(): ByteArray { size(); return out.toByteArray() }
    }

    private companion object {
        const val BOX_MTL = "newmtl Wood\nKd 1 1 1\nmap_Kd wood.png\n"
    }
}

/** Sets an int property (`P: "name", "int", "Integer", "", value`) of a binary FBX file in place. */
internal fun patchFbxIntProperty(file: File, name: String, value: Int) {
    val bytes = file.readBytes()
    val key = name.toByteArray()
    // a binary FBX string property: 'S', its int32 length, its bytes
    val at = (5..bytes.size - key.size).first { i ->
        key.indices.all { bytes[i + it] == key[it] } && bytes[i - 5] == 'S'.code.toByte() &&
            ByteBuffer.wrap(bytes, i - 4, 4).order(ByteOrder.LITTLE_ENDIAN).int == key.size
    }
    // the property's value: an 'I' (int32) after the strings name, "int", "Integer", ""
    var i = at + key.size
    repeat(3) {
        check(bytes[i] == 'S'.code.toByte()) { "unexpected $name property layout" }
        val length = ByteBuffer.wrap(bytes, i + 1, 4).order(ByteOrder.LITTLE_ENDIAN).int
        i += 5 + length
    }
    check(bytes[i] == 'I'.code.toByte()) { "unexpected $name value type ${bytes[i].toInt().toChar()}" }
    ByteBuffer.wrap(bytes, i + 1, 4).order(ByteOrder.LITTLE_ENDIAN).putInt(value)
    file.writeBytes(bytes)
}
