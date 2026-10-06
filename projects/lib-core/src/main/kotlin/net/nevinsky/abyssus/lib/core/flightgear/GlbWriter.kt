/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.flightgear

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.ObjectNode
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** A glTF material: base colour (RGBA, alpha below 1 blends), an optional image (index into the images), sides. */
data class GltfMaterial(val name: String, val color: FloatArray, val image: Int?, val doubleSided: Boolean) {
    override fun equals(other: Any?) = other is GltfMaterial && name == other.name && color.contentEquals(other.color) &&
        image == other.image && doubleSided == other.doubleSided

    override fun hashCode() = 31 * (31 * name.hashCode() + color.contentHashCode()) + (image ?: -1) + if (doubleSided) 1 else 0
}

/** Triangles of one material: three floats per position and normal, two per UV, one vertex per [indices] entry. */
class GltfPrimitive(val material: Int, val positions: FloatArray, val normals: FloatArray, val uvs: FloatArray, val indices: IntArray)

/** A named node holding one mesh. */
class GltfNode(val name: String, val primitives: List<GltfPrimitive>)

/**
 * Writes glTF 2.0 binary (GLB): one scene of [GltfNode]s, each with its own mesh, metallic-roughness materials, and
 * images as external URIs (repeating, mipmapped). The output depends only on the input.
 */
class GlbWriter(private val mapper: ObjectMapper = ObjectMapper()) {
    fun write(nodes: List<GltfNode>, materials: List<GltfMaterial>, images: List<String>, generator: String): ByteArray {
        val bin = ByteArrayOutputStream()
        val doc = mapper.createObjectNode()
        doc.putObject("asset").put("version", "2.0").put("generator", generator)
        doc.put("scene", 0)
        doc.putArray("scenes").addObject().putArray("nodes").also { list -> nodes.indices.forEach { list.add(it) } }
        val nodeArray = doc.putArray("nodes")
        val meshArray = doc.putArray("meshes")
        val accessors = doc.putArray("accessors")
        val views = doc.putArray("bufferViews")

        fun view(bytes: ByteArray, target: Int): Int {
            while (bin.size() % 4 != 0) bin.write(0)
            views.addObject().put("buffer", 0).put("byteOffset", bin.size()).put("byteLength", bytes.size).put("target", target)
            bin.write(bytes)
            return views.size() - 1
        }

        fun floats(values: FloatArray, components: Int, type: String, bounds: Boolean): Int {
            val buffer = ByteBuffer.allocate(values.size * 4).order(ByteOrder.LITTLE_ENDIAN)
            values.forEach(buffer::putFloat)
            val accessor = accessors.addObject()
                .put("bufferView", view(buffer.array(), 34962)).put("componentType", 5126)
                .put("count", values.size / components).put("type", type)
            if (bounds) {
                val min = accessor.putArray("min")
                val max = accessor.putArray("max")
                for (c in 0 until components) {
                    var lo = Float.POSITIVE_INFINITY
                    var hi = Float.NEGATIVE_INFINITY
                    var i = c
                    while (i < values.size) {
                        lo = minOf(lo, values[i]); hi = maxOf(hi, values[i]); i += components
                    }
                    min.add(lo); max.add(hi)
                }
            }
            return accessors.size() - 1
        }

        fun indices(values: IntArray): Int {
            val buffer = ByteBuffer.allocate(values.size * 4).order(ByteOrder.LITTLE_ENDIAN)
            values.forEach(buffer::putInt)
            accessors.addObject().put("bufferView", view(buffer.array(), 34963)).put("componentType", 5125)
                .put("count", values.size).put("type", "SCALAR")
            return accessors.size() - 1
        }

        for (node in nodes) {
            val mesh = meshArray.addObject().put("name", node.name)
            val primitives = mesh.putArray("primitives")
            for (p in node.primitives) {
                val attributes = mapper.createObjectNode()
                attributes.put("POSITION", floats(p.positions, 3, "VEC3", true))
                attributes.put("NORMAL", floats(p.normals, 3, "VEC3", false))
                attributes.put("TEXCOORD_0", floats(p.uvs, 2, "VEC2", false))
                primitives.addObject().set<ObjectNode>("attributes", attributes)
                    .put("indices", indices(p.indices)).put("material", p.material).put("mode", 4)
            }
            nodeArray.addObject().put("name", node.name).put("mesh", meshArray.size() - 1)
        }

        val materialArray = doc.putArray("materials")
        for (m in materials) {
            val material = materialArray.addObject().put("name", m.name)
            val pbr = material.putObject("pbrMetallicRoughness")
            pbr.putArray("baseColorFactor").addFloats(m.color.toList())
            m.image?.let { pbr.putObject("baseColorTexture").put("index", it) }
            pbr.put("metallicFactor", 0.0).put("roughnessFactor", 0.8)
            if (m.color[3] < 1f) material.put("alphaMode", "BLEND")
            if (m.doubleSided) material.put("doubleSided", true)
        }
        if (images.isNotEmpty()) {
            doc.putArray("samplers").addObject().put("magFilter", 9729).put("minFilter", 9987).put("wrapS", 10497).put("wrapT", 10497)
            val imageArray = doc.putArray("images")
            val textures = doc.putArray("textures")
            images.forEachIndexed { i, uri ->
                imageArray.addObject().put("uri", uri)
                textures.addObject().put("source", i).put("sampler", 0)
            }
        }
        while (bin.size() % 4 != 0) bin.write(0)
        doc.putArray("buffers").addObject().put("byteLength", bin.size())

        var json = mapper.writeValueAsBytes(doc)
        if (json.size % 4 != 0) json += ByteArray(4 - json.size % 4) { ' '.code.toByte() }
        val data = bin.toByteArray()
        val out = ByteBuffer.allocate(12 + 8 + json.size + 8 + data.size).order(ByteOrder.LITTLE_ENDIAN)
        out.putInt(0x46546C67).putInt(2).putInt(out.capacity())
        out.putInt(json.size).putInt(0x4E4F534A).put(json)
        out.putInt(data.size).putInt(0x004E4942).put(data)
        return out.array()
    }

    private fun ArrayNode.addFloats(values: List<Float>): ArrayNode = apply { values.forEach { add(it) } }
}
