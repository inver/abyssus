/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.assets.foliage

import net.nevinsky.abyssus.lib.core.assets.terrain.TerrainData
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class FoliageFingerprintTest {
    private val fingerprint = FoliageFingerprint()
    private val resolution = 16
    private val heights = floatArrayOf(0f, 1f, 2f, 3f)
    private val terrain = TerrainData(2, heights, 10, 1f)
    private val masks = mapOf(1 to ByteArray(resolution * resolution) { 200.toByte() })
    private val layer = FoliageLayerMeta(
        id = 1,
        models = listOf(FoliageModelMeta("tree", 3f)),
        density = 0.05f,
        alignToNormal = 0.5f,
        drawDistance = 80f,
        seed = 7,
    )
    private val meta = FoliageMeta(terrain = "terr", maskResolution = resolution, layers = listOf(layer))
    private val base by lazy { fingerprint.of(meta, terrain, masks) }

    private fun of(meta: FoliageMeta = this.meta, terrain: TerrainData? = this.terrain): ByteArray =
        fingerprint.of(meta, terrain, masks)

    @Test
    fun changingAHeightByteChangesIt() {
        val changed = terrain.heights.copyOf()
        // Flip a byte of one height's bits: the fingerprint reads the height bytes, not the parsed floats.
        changed[1] = java.lang.Float.intBitsToFloat(java.lang.Float.floatToIntBits(changed[1]) xor 0x40)
        assertFalse(base.contentEquals(of(terrain = TerrainData(2, changed, 10, 1f))))
    }

    @Test
    fun changingAMaskByteChangesIt() {
        val changed = masks.getValue(1).copyOf()
        changed[5] = (changed[5] + 1).toByte()
        assertFalse(base.contentEquals(fingerprint.of(meta, terrain, mapOf(1 to changed))))
    }

    @Test
    fun changingASeedChangesIt() {
        assertFalse(base.contentEquals(of(meta.copy(layers = listOf(layer.copy(seed = 8))))))
    }

    @Test
    fun changingAWeightChangesIt() {
        val heavier = listOf(FoliageModelMeta("tree", 3.5f))
        assertFalse(base.contentEquals(of(meta.copy(layers = listOf(layer.copy(models = heavier))))))
    }

    @Test
    fun changingTheTerrainSizeChangesIt() {
        assertFalse(base.contentEquals(of(terrain = TerrainData(2, heights, 20, 1f))))
    }

    @Test
    fun changingAlignToNormalOrDrawDistanceDoesNotChangeIt() {
        // Both act only at draw time: editing them must not make a bake stale.
        val drawOnly = layer.copy(alignToNormal = 0.9f, drawDistance = 120f)
        assertArrayEquals(base, of(meta.copy(layers = listOf(drawOnly))))
    }
}
