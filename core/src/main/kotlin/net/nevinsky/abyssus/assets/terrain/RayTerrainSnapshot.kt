/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.assets.terrain

import net.nevinsky.abyssus.assets.loading.RaySnapshot
import net.nevinsky.abyssus.assets.model.RayModelImage
import net.nevinsky.abyssus.assets.model.RayTextureColorSpace
import net.nevinsky.abyssus.assets.model.RayTextureSampler
import java.util.*

data class RayTerrainTexture(
    val image: RayModelImage, val sampler: RayTextureSampler,
    val colorSpace: RayTextureColorSpace = RayTextureColorSpace.LINEAR,
)

/** Shared terrain-local geometry; instance world/normal matrices belong to the live scene snapshot. */
class RayTerrainSnapshot internal constructor(
    val resolution: Int, val size: Int, val uv: Float,
    heights: FloatArray, vertices: FloatArray, indices: IntArray,
    val splatMap: RayTerrainTexture?, layers: Map<String, RayTerrainTexture>,
) : RaySnapshot {
    private val heightValues = heights.copyOf()
    private val vertexValues = vertices.copyOf()
    private val indexValues = indices.copyOf()

    /** Base then R/G/B/A: sequential shader mixes, with absent layers skipped and absent base neutral gray. */
    val layers: Map<String, RayTerrainTexture> = Collections.unmodifiableMap(LinkedHashMap(layers))
    val byteSize: Long = (heightValues.size.toLong() + vertexValues.size + indexValues.size) * 4 +
            (splatMap?.image?.byteSize ?: 0) + layers.values.sumOf { it.image.byteSize }

    fun heights(): FloatArray = heightValues.copyOf()

    /** Eight floats per vertex: position, normal, repeated layer UV. Splat UV is local xz/size. */
    fun vertices(): FloatArray = vertexValues.copyOf()
    fun indices(): IntArray = indexValues.copyOf()
}
