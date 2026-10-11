/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.assets.foliage

import org.junit.Assert.*
import org.junit.Test

class FoliageDataFileTest {
    private val file = FoliageDataFile()
    private val fingerprint = ByteArray(FOLIAGE_FINGERPRINT_BYTES) { it.toByte() }

    private fun copy(i: Int) = FoliageCopy(((i % 3) + 3) % 3, i * 1.5f, i * -2.5f, i * 0.25f, 0.5f + i * 0.1f)

    private fun bake(): FoliageBake {
        fun layer(id: Int) = FoliageLayerBake(
            id, modelCount = 2, chunksX = 3, chunksZ = 3,
            chunks = List(9) { c -> List(((id + c) % 4 + 4) % 4) { copy(id * 100 + c * 10 + it) } },
        )
        return FoliageBake(fingerprint, 32f, listOf(layer(7), layer(-3)))
    }

    @Test
    fun twoLayersOverThreeByThreeChunksRoundTripToTheSameBytes() {
        val bytes = file.write(bake())
        val read = file.read(bytes)!!
        assertEquals(bake(), read)
        assertEquals(7, read.layers[0].id)
        assertEquals(2, read.layers[0].modelCount)
        assertEquals(3, read.layers[0].chunksX)
        assertEquals(3, read.layers[0].chunksZ)
        assertArrayEquals(bytes, file.write(read))
        assertTrue(read.layers[0].copyCount > 0)
        assertEquals(read.layers[0].chunk(1, 2), read.layers[0].chunks[2 * 3 + 1])
    }

    @Test
    fun anotherMagicVersionOrALengthThatDoesNotFitReadsAsStale() {
        val bytes = file.write(bake())
        assertEquals(bake(), file.read(bytes)!!)
        val wrongMagic = bytes.copyOf().also { it[0] = 'X'.code.toByte() }
        assertNull(file.read(wrongMagic))
        val version2 = bytes.copyOf().also { it[4] = 0; it[5] = 2 }
        assertNull(file.read(version2))
        assertNull(file.read(bytes.copyOf(bytes.size - 1)))
        assertNull(file.read(ByteArray(0)))
    }

    @Test
    fun theChunkSizeIsAtLeastThirtyTwoWorldUnits() {
        assertEquals(32f, foliageChunkSize(100), 0f)
        assertEquals(32f, foliageChunkSize(1600), 0f)
        assertEquals(64f, foliageChunkSize(4096), 0f)
    }

    @Test
    fun anEmptyBakeRoundTrips() {
        val bytes = file.write(file.empty(fingerprint, foliageChunkSize(1600)))
        val read = file.read(bytes)!!
        assertTrue(read.layers.isEmpty())
        assertEquals(0, read.copyCount)
        assertArrayEquals(fingerprint, read.fingerprint)
        assertEquals(32f, read.chunkSize, 0f)
    }
}
