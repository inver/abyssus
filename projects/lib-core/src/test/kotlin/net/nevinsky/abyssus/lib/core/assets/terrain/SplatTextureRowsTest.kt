/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.assets.terrain

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.nio.ByteBuffer

class SplatTextureRowsTest {
    private val side = 8

    /** Texel (x, y) has red x, green y, blue 7 and alpha 255, so a copied block shows where each texel came from. */
    private val pixels = ByteArray(side * side * 4).also {
        for (y in 0 until side) for (x in 0 until side) {
            val i = (y * side + x) * 4
            it[i] = x.toByte(); it[i + 1] = y.toByte(); it[i + 2] = 7; it[i + 3] = -1
        }
    }

    private fun pack(x: Int, y: Int, w: Int, h: Int): ByteArray {
        val out = ByteBuffer.allocateDirect(side * side * 4)
        packSplatRows(pixels, side, x, y, w, h, out)
        return ByteArray(out.remaining()).also { out.get(it) }
    }

    @Test
    fun `a rectangle at a corner is packed row after row`() {
        val block = pack(0, 0, 2, 2)
        assertEquals(16, block.size)
        assertArrayEquals(byteArrayOf(0, 0, 7, -1, 1, 0, 7, -1, 0, 1, 7, -1, 1, 1, 7, -1), block)
        val far = pack(6, 6, 2, 2)
        assertArrayEquals(byteArrayOf(6, 6, 7, -1, 7, 6, 7, -1, 6, 7, 7, -1, 7, 7, 7, -1), far)
    }

    @Test
    fun `the full width is a straight copy of the rows and one texel is four bytes`() {
        assertArrayEquals(pixels.copyOfRange(2 * side * 4, 4 * side * 4), pack(0, 2, side, 2))
        assertArrayEquals(byteArrayOf(3, 5, 7, -1), pack(3, 5, 1, 1))
    }

    @Test
    fun `a rectangle outside the map or a small buffer is refused`() {
        assertThrows(IllegalArgumentException::class.java) { pack(7, 0, 2, 1) }
        assertThrows(IllegalArgumentException::class.java) { pack(0, 0, 0, 1) }
        assertThrows(IllegalArgumentException::class.java) { packSplatRows(pixels, side, 0, 0, 2, 2, ByteBuffer.allocate(8)) }
    }
}
