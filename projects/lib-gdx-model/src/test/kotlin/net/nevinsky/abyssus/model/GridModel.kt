/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.model

import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files

/**
 * A flat [size] x [size] vertex grid on the XZ plane, written as a glTF with a single mesh and 32-bit indices. With
 * `size = 300` it has 90,000 vertices: more than a 16-bit index can address.
 */
class GridModel(val size: Int) {
    val vertexCount = size * size
    val indexCount = (size - 1) * (size - 1) * 6

    /** Writes `model.gltf` and `model.bin` into a new temp folder and returns the glTF file. */
    fun write(): File {
        val dir = Files.createTempDirectory("abyssus_grid").toFile()
        val positionBytes = vertexCount * 3 * 4
        val buffer = ByteBuffer.allocate(positionBytes + indexCount * 4).order(ByteOrder.LITTLE_ENDIAN)
        for (z in 0 until size) for (x in 0 until size) buffer.putFloat(x.toFloat()).putFloat(0f).putFloat(z.toFloat())
        for (z in 0 until size - 1) for (x in 0 until size - 1) {
            val a = z * size + x
            val b = a + 1
            val c = a + size
            val d = c + 1
            buffer.putInt(a).putInt(c).putInt(b)
            buffer.putInt(b).putInt(c).putInt(d)
        }
        File(dir, "model.bin").writeBytes(buffer.array())
        val max = (size - 1).toFloat()
        File(dir, "model.gltf").writeText(
            """
            {
              "asset": { "version": "2.0" },
              "scene": 0,
              "scenes": [ { "nodes": [0] } ],
              "nodes": [ { "name": "grid", "mesh": 0 } ],
              "meshes": [ { "name": "grid", "primitives": [ { "attributes": { "POSITION": 0 }, "indices": 1 } ] } ],
              "buffers": [ { "uri": "model.bin", "byteLength": ${buffer.capacity()} } ],
              "bufferViews": [
                { "buffer": 0, "byteOffset": 0, "byteLength": $positionBytes, "target": 34962 },
                { "buffer": 0, "byteOffset": $positionBytes, "byteLength": ${indexCount * 4}, "target": 34963 }
              ],
              "accessors": [
                { "bufferView": 0, "componentType": 5126, "count": $vertexCount, "type": "VEC3", "min": [0, 0, 0], "max": [$max, 0, $max] },
                { "bufferView": 1, "componentType": 5125, "count": $indexCount, "type": "SCALAR" }
              ]
            }
            """.trimIndent()
        )
        return File(dir, "model.gltf")
    }
}
