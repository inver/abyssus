/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.assets.foliage

import net.nevinsky.abyssus.lib.core.assets.terrain.TerrainData
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.security.MessageDigest

/** The id of the generator whose copies the bakes hold; changing the algorithm changes it, so old bakes go stale. */
const val FOLIAGE_GENERATOR_ID = "foliage-grid-v1"

/**
 * The fingerprint a `foliage.data` records: SHA-256 over the generator id, the terrain's size and heights,
 * `maskResolution`, each layer's generation fields (kind, models and weights, density, scale, seed, height and slope
 * limits) and each mask's bytes. `alignToNormal` and `drawDistance` act only at draw time, so editing them does not
 * make the bake stale. The encoding is canonical: field by field, in this order, big-endian.
 */
class FoliageFingerprint {

    fun of(meta: FoliageMeta, terrain: TerrainData?, masks: Map<Int, ByteArray>): ByteArray {
        val bytes = ByteArrayOutputStream()
        DataOutputStream(bytes).use { out ->
            out.text(FOLIAGE_GENERATOR_ID)
            out.writeInt(terrain?.size ?: -1)
            if (terrain != null) for (height in terrain.heights) out.writeFloat(height)
            out.writeInt(meta.maskResolution)
            for (layer in meta.layers) {
                out.writeInt(layer.id)
                out.text(layer.kind)
                out.writeInt(layer.models.size)
                for (model in layer.models) {
                    out.text(model.asset)
                    out.writeFloat(model.weight)
                }
                out.writeFloat(layer.density)
                out.writeFloat(layer.scale.min)
                out.writeFloat(layer.scale.max)
                out.writeInt(layer.seed)
                out.writeOptional(layer.minHeight)
                out.writeOptional(layer.maxHeight)
                out.writeOptional(layer.maxSlope)
            }
            for (layer in meta.layers.sortedBy { it.id }) {
                out.writeInt(layer.id)
                out.write(masks[layer.id] ?: fullMask(meta.maskResolution))
            }
        }
        return MessageDigest.getInstance("SHA-256").digest(bytes.toByteArray())
    }

    private fun DataOutputStream.text(value: String) {
        val text = value.toByteArray(Charsets.US_ASCII)
        writeInt(text.size)
        write(text)
    }

    private fun DataOutputStream.writeOptional(value: Float?) {
        if (value == null) writeByte(0) else {
            writeByte(1)
            writeFloat(value)
        }
    }
}
