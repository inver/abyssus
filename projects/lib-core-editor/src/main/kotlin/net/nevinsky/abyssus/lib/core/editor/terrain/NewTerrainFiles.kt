package net.nevinsky.abyssus.lib.core.editor.terrain

import net.nevinsky.abyssus.lib.core.editor.document.SceneJson
import net.nevinsky.abyssus.lib.core.editor.document.newAssetMetaText
import net.nevinsky.abyssus.lib.core.editor.document.TerrainAssetAdditional

import net.nevinsky.abyssus.lib.core.assets.MetaType
import net.nevinsky.abyssus.lib.core.io.JsonProcessor
import net.nevinsky.abyssus.lib.core.editor.meta.NEW_TERRAIN_UV_DEFAULT
import net.nevinsky.abyssus.lib.core.editor.meta.TERRAIN_META_FILE_NAME_DEFAULT
import java.nio.ByteBuffer
import java.util.*


fun sha256Hex(bytes: ByteArray): String =
    net.nevinsky.abyssus.lib.core.util.sha256Hex(bytes)

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
        return newAssetMetaText(
            MetaType.TERRAIN, TerrainAssetAdditional(TERRAIN_META_FILE_NAME_DEFAULT, size, uv), uuid.toString(), lastModified,
        )
    }
}
