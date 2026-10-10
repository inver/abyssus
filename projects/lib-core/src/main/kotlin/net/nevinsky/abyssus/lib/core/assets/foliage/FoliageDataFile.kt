/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.assets.foliage

import net.nevinsky.abyssus.lib.core.assets.runCatchingKeepingCancellation
import java.nio.ByteBuffer
import kotlin.math.max

const val FOLIAGE_DATA_MAGIC = "ABFO"
const val FOLIAGE_DATA_VERSION = 1
const val FOLIAGE_FINGERPRINT_BYTES = 32
const val FOLIAGE_COPY_BYTES = 18

/** One baked copy of a layer, at terrain-local [x], [z], turned by [yaw] radians, scaled by [scale]. */
data class FoliageCopy(val model: Int, val x: Float, val z: Float, val yaw: Float, val scale: Float)

/**
 * The baked copies of one layer, cut into a `chunksX` by `chunksZ` grid of [chunks] in z-major order. [modelCount] is
 * the number of models the bake was made from; a copy's [FoliageCopy.model] is its index among them.
 */
class FoliageLayerBake(
    val id: Int,
    val modelCount: Int,
    val chunksX: Int,
    val chunksZ: Int,
    val chunks: List<List<FoliageCopy>>,
) {
    init {
        require(chunks.size == chunksX * chunksZ) { "a ${chunksX}x$chunksZ grid needs ${chunksX * chunksZ} chunks" }
    }

    fun chunk(chunksXIndex: Int, chunksZIndex: Int): List<FoliageCopy> = chunks[index(chunksXIndex, chunksZIndex)]

    /** The position of chunk ([chunksXIndex], [chunksZIndex]) in the z-major [chunks] list. */
    fun index(chunksXIndex: Int, chunksZIndex: Int): Int = chunksZIndex * chunksX + chunksXIndex

    val copyCount: Int get() = chunks.sumOf { it.size }

    override fun equals(other: Any?): Boolean = other is FoliageLayerBake &&
            other.id == id && other.modelCount == modelCount && other.chunksX == chunksX && other.chunksZ == chunksZ &&
            other.chunks.size == chunks.size && other.chunks.indices.all { other.chunks[it] == chunks[it] }

    override fun hashCode(): Int = (id * 31 + chunksX) * 31 + chunksZ
}

/** The content of a `foliage.data`: the fingerprint of its inputs and the copies of each layer by chunk. */
class FoliageBake(
    val fingerprint: ByteArray,
    val chunkSize: Float,
    val layers: List<FoliageLayerBake>,
) {
    val copyCount: Int get() = layers.sumOf { it.copyCount }

    override fun equals(other: Any?): Boolean = other is FoliageBake &&
            other.fingerprint.contentEquals(fingerprint) && other.chunkSize == chunkSize && other.layers == layers

    override fun hashCode(): Int = (fingerprint.contentHashCode() * 31 + chunkSize.hashCode()) * 31 + layers.hashCode()
}

/**
 * Reads and writes `foliage.data`: a big-endian bake with its own magic and version, laid out as
 * `"ABFO" u16 version u8[32] fingerprint f32 chunkSize u16 layerCount`, then per layer `i32 id u16 modelCount
 * u16 chunksX u16 chunksZ`, then per chunk (z-major) `i32 count` and `count` copies of
 * `u16 model f32 x f32 z f32 yaw f32 scale`. A file of another magic or version, or a truncated one, reads as stale
 * (null) rather than as an error: the editor regenerates it.
 */
class FoliageDataFile {

    /** The bake in [bytes], or null when it is not a readable `foliage.data` (treated as stale). */
    fun read(bytes: ByteArray): FoliageBake? {
        val buffer = ByteBuffer.wrap(bytes)
        if (buffer.remaining() < FOLIAGE_DATA_MAGIC.length + 2 + FOLIAGE_FINGERPRINT_BYTES + 4 + 2) return null
        val magic = ByteArray(FOLIAGE_DATA_MAGIC.length).also { buffer.get(it) }
        if (String(magic, Charsets.US_ASCII) != FOLIAGE_DATA_MAGIC) return null
        if (buffer.short.toInt() and 0xFFFF != FOLIAGE_DATA_VERSION) return null
        val fingerprint = ByteArray(FOLIAGE_FINGERPRINT_BYTES).also { buffer.get(it) }
        val chunkSize = buffer.float
        val layerCount = buffer.short.toInt() and 0xFFFF
        val layers = ArrayList<FoliageLayerBake>(layerCount)
        repeat(layerCount) {
            val layer = readLayer(buffer) ?: return null
            layers += layer
        }
        if (buffer.hasRemaining()) return null
        return FoliageBake(fingerprint, chunkSize, layers)
    }

    private fun readLayer(buffer: ByteBuffer): FoliageLayerBake? = runCatchingKeepingCancellation {
        val id = buffer.int
        val modelCount = buffer.short.toInt() and 0xFFFF
        val chunksX = buffer.short.toInt() and 0xFFFF
        val chunksZ = buffer.short.toInt() and 0xFFFF
        val chunks = ArrayList<List<FoliageCopy>>(chunksX * chunksZ)
        repeat(chunksX * chunksZ) {
            val count = buffer.int
            if (count < 0) return null
            val copies = ArrayList<FoliageCopy>(count)
            repeat(count) {
                copies += FoliageCopy(
                    buffer.short.toInt() and 0xFFFF, buffer.float, buffer.float, buffer.float, buffer.float
                )
            }
            chunks += copies
        }
        FoliageLayerBake(id, modelCount, chunksX, chunksZ, chunks)
    }.getOrNull()

    /** The bytes of [bake]. */
    fun write(bake: FoliageBake): ByteArray {
        require(bake.fingerprint.size == FOLIAGE_FINGERPRINT_BYTES) { "a ${FOLIAGE_FINGERPRINT_BYTES}-byte fingerprint" }
        require(bake.layers.size <= 0xFFFF) { "at most ${0xFFFF} layers" }
        val buffer = ByteBuffer.allocate(sizeOf(bake))
        buffer.put(FOLIAGE_DATA_MAGIC.toByteArray(Charsets.US_ASCII))
        buffer.putShort(FOLIAGE_DATA_VERSION.toShort())
        buffer.put(bake.fingerprint)
        buffer.putFloat(bake.chunkSize)
        buffer.putShort(bake.layers.size.toShort())
        for (layer in bake.layers) {
            require(layer.modelCount <= 0xFFFF && layer.chunksX <= 0xFFFF && layer.chunksZ <= 0xFFFF) { "a bake header" }
            buffer.putInt(layer.id)
            buffer.putShort(layer.modelCount.toShort())
            buffer.putShort(layer.chunksX.toShort())
            buffer.putShort(layer.chunksZ.toShort())
            for (chunk in layer.chunks) {
                require(chunk.size <= Int.MAX_VALUE) { "a chunk" }
                buffer.putInt(chunk.size)
                for (copy in chunk) {
                    require(copy.model in 0..0xFFFF) { "a model index" }
                    buffer.putShort(copy.model.toShort())
                    buffer.putFloat(copy.x)
                    buffer.putFloat(copy.z)
                    buffer.putFloat(copy.yaw)
                    buffer.putFloat(copy.scale)
                }
            }
        }
        return buffer.array()
    }

    /** A bake with the given [fingerprint] and no copies: what a new foliage asset holds. */
    fun empty(fingerprint: ByteArray, chunkSize: Float): FoliageBake =
        FoliageBake(fingerprint, chunkSize, emptyList())

    private fun sizeOf(bake: FoliageBake): Int {
        var layers = FOLIAGE_DATA_MAGIC.length + 2 + FOLIAGE_FINGERPRINT_BYTES + 4 + 2
        for (layer in bake.layers) {
            layers += 4 + 2 + 2 + 2 + layer.chunks.sumOf { 4 + it.size * FOLIAGE_COPY_BYTES }
        }
        return layers
    }
}

/**
 * The chunk side in world units for a terrain of [size]: `max(32, size / 64)`, which bounds the chunk grid of a
 * bake at 64 by 64.
 */
fun foliageChunkSize(size: Int): Float = max(32f, size / 64f)
