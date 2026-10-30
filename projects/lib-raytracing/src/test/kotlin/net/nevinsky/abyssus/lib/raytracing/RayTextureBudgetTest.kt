/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.lib.raytracing

import org.junit.Assert.*
import org.junit.Test

class RayTextureBudgetTest {
    private fun bytes(id: String, w: Int, h: Int, value: Int = 100) =
        RayTexture(id, w, h, ByteArray(w * h * 4) { value.toByte() }, RayWrap.CLAMP_TO_EDGE, RayWrap.REPEAT, RayFilter.NEAREST)

    @Test fun halvingAveragesBlocksAndKeepsIdAndSampler() {
        val t = RayTexture("t", 2, 2, byteArrayOf(0, 0, 0, -1, 100, 100, 100, -1, 100, 100, 100, -1, 0, 0, 0, -1))
        val half = t.halved()!!
        assertEquals(1, half.width); assertEquals(1, half.height)
        assertEquals(50, half.rgba8()!![0].toInt() and 255)
        assertEquals(255, half.rgba8()!![3].toInt() and 255)
        assertEquals("t", half.id); assertEquals(t.wrapU, half.wrapU); assertEquals(t.filter, half.filter)
    }

    @Test fun oddSizesAndLimitsAreHandled() {
        val odd = bytes("odd", 3, 5).halved()!!
        assertEquals(1, odd.width); assertEquals(2, odd.height)
        assertNull(bytes("one", 1, 1).halved())
        assertNull(RayTexture("f", 2, 2, FloatArray(16)).halved())
        assertEquals(0L, RayTexture("f", 2, 2, FloatArray(16)).byteTexels)
    }

    @Test fun texturesWithinTheBudgetAreReturnedAsIs() {
        val list = listOf(bytes("a", 8, 8), bytes("b", 4, 4))
        assertSame(list, fitTextureTexels(list, 80))
    }

    @Test fun theLargestTexturesAreHalvedUntilTheyFitAndIndicesAreKept() {
        val list = listOf(bytes("small", 4, 4), bytes("big", 16, 16), bytes("mid", 8, 8))
        val fitted = fitTextureTexels(list, 16 + 64 + 64)!!
        assertEquals(listOf("small", "big", "mid"), fitted.map { it.id })
        assertTrue(fitted.sumOf { it.byteTexels } <= 144)
        assertEquals(4, fitted[0].width)
        assertEquals(8, fitted[1].width)
        assertEquals(8, fitted[2].width)
    }

    @Test fun anImpossibleBudgetIsNull() {
        assertNull(fitTextureTexels(listOf(bytes("a", 4, 4), bytes("b", 4, 4)), 1))
    }

    @Test fun anOversizedSceneIsAnExplicitFallbackInsteadOfAnEncodingFailure() {
        val tooMany = List(5) { bytes("t$it", 2048, 2048) }
        val scene = RaySceneSnapshot(emptyList(), emptyList(), emptyList(), tooMany, emptyList())
        assertTrue(scene.unsupportedReason()!!.contains("texels"))
        val fitted = fitTextureTexels(tooMany)!!
        assertNull(RaySceneSnapshot(emptyList(), emptyList(), emptyList(), fitted, emptyList()).unsupportedReason())
        RaySceneEncoding(RaySceneSnapshot(emptyList(), emptyList(), emptyList(), fitted, emptyList())).encode()
    }
}
