/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.editor.foliage

import net.nevinsky.abyssus.lib.core.assets.foliage.FoliageBake
import net.nevinsky.abyssus.lib.core.assets.foliage.FoliageCopy
import net.nevinsky.abyssus.lib.core.assets.foliage.FoliageLayerBake

/**
 * [base] with the copies of the chunks in [chunks] replaced by the ones [patch] holds for the same chunks (design
 * decision 7): a brush stroke or a settings edit re-scatters only the chunks it touched, and the view keeps drawing
 * the rest of the bake it already has.
 *
 * The patch's fingerprint becomes the result's, because the merged bake is the one its inputs describe; a patch of
 * another chunk size, or a layer only the patch holds, is taken as it comes. A layer only [base] holds keeps its
 * copies.
 */
fun mergeFoliageChunks(base: FoliageBake, patch: FoliageBake, chunks: Set<FoliageChunk>): FoliageBake {
    if (chunks.isEmpty()) return base
    if (base.chunkSize != patch.chunkSize) return patch // another terrain or another chunking: the patch is the bake
    val layers = LinkedHashMap<Int, FoliageLayerBake>()
    for (layer in base.layers) layers[layer.id] = layer
    for (patched in patch.layers) {
        layers[patched.id] = mergeLayer(layers[patched.id] ?: patched, patched, chunks)
    }
    return FoliageBake(patch.fingerprint, base.chunkSize, layers.values.toList())
}

/** [base] with [chunks] taken from [patch]; the patch's own grid when the two do not match. */
private fun mergeLayer(
    base: FoliageLayerBake,
    patch: FoliageLayerBake,
    chunks: Set<FoliageChunk>,
): FoliageLayerBake {
    if (base.chunksX != patch.chunksX || base.chunksZ != patch.chunksZ) return patch
    val merged = ArrayList<List<FoliageCopy>>(base.chunks)
    for (chunk in chunks) {
        if (chunk.i !in 0 until base.chunksX || chunk.j !in 0 until base.chunksZ) continue
        merged[base.index(chunk.i, chunk.j)] = patch.chunk(chunk.i, chunk.j)
    }
    return FoliageLayerBake(base.id, base.modelCount, base.chunksX, base.chunksZ, merged)
}
