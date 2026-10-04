/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.assets.terrain.generation

import com.fasterxml.jackson.databind.node.FloatNode
import com.fasterxml.jackson.databind.node.IntNode
import com.fasterxml.jackson.databind.node.JsonNodeFactory
import com.fasterxml.jackson.databind.node.LongNode
import com.fasterxml.jackson.databind.node.NullNode
import com.fasterxml.jackson.databind.node.TextNode
import net.nevinsky.abyssus.assets.SPLAT_FIELDS
import net.nevinsky.abyssus.assets.files.MetaType
import net.nevinsky.abyssus.assets.json.JsonProcessor
import java.nio.ByteBuffer
import java.security.MessageDigest

/** The height data file of a terrain asset, in the native layout. */
const val TERRAIN_DATA_FILE = "terrain.data"

/** The texture repetition a new terrain starts with. */
const val NEW_TERRAIN_UV = 1f

/** The metadata `version`, distinct from document `formatVersion`. */
private const val META_VERSION = 1

/** [bytes] as lowercase hexadecimal SHA-256, the fingerprint recipes keep of applied heights. */
fun sha256Hex(bytes: ByteArray): String =
    MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

/**
 * Writes terrain heights: no header, each height a big-endian 32-bit float, row after row (z-major), as
 * `java.io.DataOutputStream.writeFloat` produces (the editor's `EditorTerrainService.createAndSaveAsset`).
 */
class TerrainHeightEncoder {
    fun encode(heights: FloatArray): ByteArray {
        val buffer = ByteBuffer.allocate(heights.size * Float.SIZE_BYTES) // big-endian by default
        heights.forEach(buffer::putFloat)
        return buffer.array()
    }
}

/** The text of a new terrain's `meta.json` and its height data: what a new terrain folder holds besides the recipe. */
class NewTerrainFiles(val metaText: String, val heightBytes: ByteArray)

/**
 * Builds native terrain asset files: one compact line of `format`, `formatVersion`, `version`, `lastModified`,
 * `uuid`, `type` and `additional` (`terrainFile`, `size`, `uv`, then the six splat references, null while unset), with
 * no trailing newline.
 */
class TerrainAssetWriter(private val json: JsonProcessor, private val encoder: TerrainHeightEncoder) {
    fun create(uuid: String, lastModified: Long, size: Int, heights: FloatArray): NewTerrainFiles =
        NewTerrainFiles(meta(uuid, lastModified, size), encoder.encode(heights))

    fun meta(uuid: String, lastModified: Long, size: Int, uv: Float = NEW_TERRAIN_UV): String {
        require(size > 0) { "size must be positive" }
        val nodes = JsonNodeFactory.instance
        val additional = nodes.objectNode().apply {
            set<TextNode>("terrainFile", TextNode.valueOf(TERRAIN_DATA_FILE))
            set<IntNode>("size", IntNode.valueOf(size))
            set<FloatNode>("uv", FloatNode.valueOf(uv))
            SPLAT_FIELDS.forEach { set<NullNode>(it, NullNode.instance) }
        }
        val root = nodes.objectNode().apply {
            put("format", "abyssus")
            put("formatVersion", 1)
            set<IntNode>("version", IntNode.valueOf(META_VERSION))
            set<LongNode>("lastModified", LongNode.valueOf(lastModified))
            set<TextNode>("uuid", TextNode.valueOf(uuid))
            set<TextNode>("type", TextNode.valueOf(MetaType.TERRAIN.name))
            set<com.fasterxml.jackson.databind.node.ObjectNode>("additional", additional)
        }
        return json.compact(root)
    }
}
