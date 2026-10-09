package net.nevinsky.abyssus.lib.gdx.editor.terrain

import com.fasterxml.jackson.databind.node.JsonNodeFactory
import com.fasterxml.jackson.databind.node.NullNode
import com.fasterxml.jackson.databind.node.ObjectNode
import net.nevinsky.abyssus.lib.gdx.assets.META_VERSION_DEFAULT
import net.nevinsky.abyssus.lib.gdx.assets.MetaType
import net.nevinsky.abyssus.lib.gdx.io.JsonProcessor
import net.nevinsky.abyssus.lib.gdx.editor.meta.NEW_TERRAIN_UV_DEFAULT
import net.nevinsky.abyssus.lib.gdx.editor.meta.TERRAIN_META_FILE_NAME_DEFAULT
import net.nevinsky.abyssus.lib.gdx.assets.terrain.SPLAT_FIELDS
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.util.*


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
    fun create(uuid: UUID, lastModified: Long, size: Int, heights: FloatArray): NewTerrainFiles =
        NewTerrainFiles(meta(uuid, lastModified, size), encoder.encode(heights))

    fun meta(uuid: UUID, lastModified: Long, size: Int, uv: Float = NEW_TERRAIN_UV_DEFAULT): String {
        require(size > 0) { "size must be positive" }
        val nodes = JsonNodeFactory.instance
        val additional = nodes.objectNode().apply {
            put("terrainFile", TERRAIN_META_FILE_NAME_DEFAULT)
            put("size", size)
            put("uv", uv)
            SPLAT_FIELDS.forEach { set<NullNode>(it, NullNode.instance) }
        }
        val root = nodes.objectNode().apply {
            put("format", "abyssus")
            put("formatVersion", 1)
            put("version", META_VERSION_DEFAULT)
            put("lastModified", lastModified)
            put("uuid", uuid.toString())
            put("type", MetaType.TERRAIN.name)
            set<ObjectNode>("additional", additional)
        }
        return json.toString(root)
    }
}
