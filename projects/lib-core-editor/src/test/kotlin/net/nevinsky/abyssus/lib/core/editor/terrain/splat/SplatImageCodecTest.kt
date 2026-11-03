/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.editor.terrain.splat

import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class SplatImageCodecTest {
    private val codec = SplatImageCodec()

    private fun png(image: BufferedImage): ByteArray = ByteArrayOutputStream().also { ImageIO.write(image, "png", it) }.toByteArray()

    private fun reason(name: String, bytes: ByteArray): SplatImageProblem =
        (assertThrows(SplatImageException::class.java) { codec.decode(name, bytes) }).problem

    @Test
    fun `every channel value survives a round trip at alpha 0 and 255`() {
        for (alpha in listOf(0, 255)) {
            val rgba = ByteArray(64 * 64 * 4)
            for (i in 0 until 64 * 64) {
                val v = i % 256
                rgba[i * 4] = v.toByte()
                rgba[i * 4 + 1] = (255 - v).toByte()
                rgba[i * 4 + 2] = ((v * 7) % 256).toByte()
                rgba[i * 4 + 3] = alpha.toByte()
            }
            val back = codec.decode("splat.png", codec.encode(SplatPixels(64, rgba)))
            assertArrayEquals("alpha $alpha", rgba, back.rgba)
        }
    }

    @Test
    fun `colour is not premultiplied at alpha 0`() {
        val rgba = ByteArray(64 * 64 * 4)
        rgba[0] = 200.toByte(); rgba[1] = 100; rgba[2] = 50; rgba[3] = 0
        val back = codec.decode("splat.png", codec.encode(SplatPixels(64, rgba)))
        assertEquals(200, back.rgba[0].toInt() and 0xFF)
        assertEquals(100, back.rgba[1].toInt())
        assertEquals(50, back.rgba[2].toInt())
        assertEquals(0, back.rgba[3].toInt())
    }

    @Test
    fun `an image of another kind is refused with its reason`() {
        assertEquals(SplatImageProblem.NOT_RGBA8, reason("a.png", png(BufferedImage(64, 64, BufferedImage.TYPE_INT_RGB))))
        assertEquals(SplatImageProblem.NOT_RGBA8, reason("a.png", png(BufferedImage(64, 64, BufferedImage.TYPE_BYTE_GRAY))))
        assertEquals(SplatImageProblem.NOT_RGBA8, reason("a.png", png(BufferedImage(64, 64, BufferedImage.TYPE_BYTE_INDEXED))))
        assertEquals(SplatImageProblem.NOT_RGBA8, reason("a.png", png(BufferedImage(64, 64, BufferedImage.TYPE_USHORT_GRAY))))
        assertEquals(SplatImageProblem.NOT_SQUARE, reason("a.png", png(BufferedImage(64, 128, BufferedImage.TYPE_4BYTE_ABGR))))
        assertEquals(SplatImageProblem.TOO_SMALL, reason("a.png", png(BufferedImage(63, 63, BufferedImage.TYPE_4BYTE_ABGR))))
        assertEquals(SplatImageProblem.TOO_LARGE, reason("a.png", png(BufferedImage(2049, 2049, BufferedImage.TYPE_4BYTE_ABGR))))
        assertEquals(SplatImageProblem.NOT_PNG, reason("a.jpg", png(BufferedImage(64, 64, BufferedImage.TYPE_4BYTE_ABGR))))
        assertEquals(SplatImageProblem.UNREADABLE, reason("a.png", byteArrayOf(1, 2, 3)))
    }

    @Test
    fun `the limits are accepted`() {
        assertEquals(64, codec.decode("a.png", png(BufferedImage(64, 64, BufferedImage.TYPE_4BYTE_ABGR))).side)
        assertEquals(2048, codec.decode("a.png", png(BufferedImage(2048, 2048, BufferedImage.TYPE_4BYTE_ABGR))).side)
    }
}
