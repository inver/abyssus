/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.gdx.editor.flightgear

import net.nevinsky.abyssus.lib.gdx.editor.flightgear.fixtureSkinPixel
import net.nevinsky.abyssus.lib.gdx.editor.flightgear.sgiRle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.ByteArrayInputStream
import java.nio.ByteBuffer
import javax.imageio.ImageIO

class SgiImageTest {
    private val sgi = SgiImage()

    @Test
    fun rleRgbPixelsSurviveThePngRoundTrip() {
        val png = ImageIO.read(ByteArrayInputStream(sgi.toPng(sgiRle(4, 2, 3, ::fixtureSkinPixel))))
        assertEquals(4, png.width)
        assertEquals(2, png.height)
        for (y in 0 until 2) for (x in 0 until 4) {
            val argb = png.getRGB(x, y)
            assertEquals(255, argb ushr 24)
            assertEquals(fixtureSkinPixel(x, y, 0), (argb shr 16) and 0xFF)
            assertEquals(fixtureSkinPixel(x, y, 1), (argb shr 8) and 0xFF)
            assertEquals(fixtureSkinPixel(x, y, 2), argb and 0xFF)
        }
    }

    @Test
    fun greyAlphaAndRepeatRuns() {
        val image = sgi.decode(sgiRle(3, 2, 2) { x, _, c -> if (c == 0) 200 else x * 100 })
        assertEquals(0xC8C8C8, image.getRGB(0, 0) and 0xFFFFFF)
        assertEquals(0, image.getRGB(0, 0) ushr 24)
        assertEquals(200, image.getRGB(2, 1) ushr 24)
    }

    @Test
    fun verbatimRgba() {
        val width = 2
        val height = 2
        val bytes = ByteBuffer.allocate(512 + width * height * 4)
        bytes.putShort(0, 474)
        bytes.put(3, 1)
        bytes.putShort(6, width.toShort())
        bytes.putShort(8, height.toShort())
        bytes.putShort(10, 4)
        // channel-major, bottom row first: red is 10 on the bottom row and 20 on the top row
        for (c in 0 until 4) for (row in 0 until height) for (x in 0 until width) {
            bytes.put(512 + (c * height + row) * width + x, (if (c == 0) 10 * (row + 1) else 255).toByte())
        }
        val image = sgi.decode(bytes.array())
        assertEquals(20, (image.getRGB(0, 0) shr 16) and 0xFF)
        assertEquals(10, (image.getRGB(0, 1) shr 16) and 0xFF)
    }

    @Test
    fun notAnSgiImage() {
        assertThrows(SgiImageException::class.java) { sgi.decode(ByteArray(600)) }
        assertThrows(SgiImageException::class.java) { sgi.decode(ByteArray(10)) }
    }
}
