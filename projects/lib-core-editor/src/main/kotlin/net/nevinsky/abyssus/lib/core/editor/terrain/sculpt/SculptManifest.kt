/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.editor.terrain.sculpt

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.JsonNodeFactory
import net.nevinsky.abyssus.lib.core.assets.displayMessage
import net.nevinsky.abyssus.lib.core.assets.runCatchingKeepingCancellation
import net.nevinsky.abyssus.lib.core.assets.terrain.MAX_TERRAIN_RESOLUTION
import net.nevinsky.abyssus.lib.core.editor.terrain.MIN_TERRAIN_RESOLUTION
import net.nevinsky.abyssus.lib.core.editor.terrain.TerrainHeightEncoder
import net.nevinsky.abyssus.lib.core.io.JsonProcessor
import java.nio.ByteBuffer

/** The Abyssus-only files beside `terrain.data` that keep a terrain's sculpting. */
const val SCULPT_MANIFEST_FILE = "abyssus-terrain.sculpt.json"
const val SCULPT_DELTA_FILE = "abyssus-terrain.sculpt"
const val SCULPT_BASE_FILE = "abyssus-terrain.base"

/** The manifest layout this code reads and writes. */
const val SCULPT_SCHEMA_VERSION = 1

private const val SHA_HEX_LENGTH = 64
private const val RECIPE_KIND = "recipe"
private const val STORED_KIND = "stored"

/** Where the heights under the delta come from. */
enum class SculptBaseKind { RECIPE, STORED }

/**
 * What the manifest next to a sculpted terrain says: the [resolution] of the delta (and stored base) grid, the
 * fingerprints of the delta file ([deltaSha256]) and of the composed `terrain.data` ([composedSha256]), and for a
 * [SculptBaseKind.STORED] base the fingerprint of the base file ([baseSha256]).
 */
data class SculptManifest(
    val baseKind: SculptBaseKind,
    val resolution: Int,
    val deltaSha256: String,
    val composedSha256: String,
    val baseSha256: String? = null,
    val schemaVersion: Int = SCULPT_SCHEMA_VERSION,
) {
    init {
        require((baseKind == SculptBaseKind.STORED) == (baseSha256 != null)) { "baseSha256 belongs to a stored base only" }
    }
}

/** Reads and writes [SculptManifest] text. */
class SculptManifestCodec(private val json: JsonProcessor) {
    fun encode(manifest: SculptManifest): String {
        val root = JsonNodeFactory.instance.objectNode().apply {
            put("schemaVersion", manifest.schemaVersion)
            put("baseKind", if (manifest.baseKind == SculptBaseKind.RECIPE) RECIPE_KIND else STORED_KIND)
            put("resolution", manifest.resolution)
            put("deltaSha256", manifest.deltaSha256)
            put("composedSha256", manifest.composedSha256)
            manifest.baseSha256?.let { put("baseSha256", it) }
        }
        return json.pretty(root)
    }

    /** The manifest in [text], or why it is not usable. */
    fun decode(text: String): Decoded = runCatchingKeepingCancellation { read(json.readObject(text)) }
        .getOrElse { Decoded.Malformed(it.displayMessage()) }

    sealed interface Decoded {
        data class Manifest(val manifest: SculptManifest) : Decoded

        /** The file is not a manifest: [reason] is the parse problem. */
        data class Malformed(val reason: String) : Decoded

        /** The manifest is from a layout this code does not have; [what] names it. */
        data class Unsupported(val what: String) : Decoded
    }

    private fun read(root: JsonNode): Decoded {
        val version = int(root, "schemaVersion")
        if (version != SCULPT_SCHEMA_VERSION) return Decoded.Unsupported("schemaVersion $version")
        val kind = when (val text = string(root, "baseKind")) {
            RECIPE_KIND -> SculptBaseKind.RECIPE
            STORED_KIND -> SculptBaseKind.STORED
            else -> return Decoded.Unsupported("baseKind $text")
        }
        val resolution = int(root, "resolution")
        check(resolution in MIN_TERRAIN_RESOLUTION..MAX_TERRAIN_RESOLUTION) { "resolution is out of range" }
        val base = if (kind == SculptBaseKind.STORED) sha(root, "baseSha256") else null
        return Decoded.Manifest(
            SculptManifest(kind, resolution, sha(root, "deltaSha256"), sha(root, "composedSha256"), base, version),
        )
    }

    private fun int(node: JsonNode, key: String): Int =
        node.get(key)?.takeIf { it.isIntegralNumber && it.canConvertToInt() }?.intValue() ?: error("$key is missing or not an integer")

    private fun string(node: JsonNode, key: String): String =
        node.get(key)?.takeIf { it.isTextual }?.asText() ?: error("$key is missing or not text")

    private fun sha(node: JsonNode, key: String): String = string(node, key).also {
        check(it.length == SHA_HEX_LENGTH && it.all { c -> c in "0123456789abcdef" }) { "$key is not a SHA-256" }
    }
}

/**
 * Reads a [resolution] by [resolution] grid of big-endian floats, the layout of `terrain.data`, the delta file and
 * the stored base; null when [bytes] hold another number of values.
 */
fun decodeSculptGrid(bytes: ByteArray, resolution: Int): FloatArray? {
    if (bytes.size != resolution * resolution * Float.SIZE_BYTES) return null
    return FloatArray(resolution * resolution).also { ByteBuffer.wrap(bytes).asFloatBuffer().get(it) }
}

/** The bytes of a grid of heights in the `terrain.data` layout (big-endian floats, row after row). */
fun encodeSculptGrid(values: FloatArray): ByteArray = TerrainHeightEncoder().encode(values)
