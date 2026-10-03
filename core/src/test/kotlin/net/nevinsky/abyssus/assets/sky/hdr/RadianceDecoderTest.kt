package net.nevinsky.abyssus.assets.sky.hdr

import net.nevinsky.abyssus.assets.testProject
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.File
import kotlin.math.abs

class RadianceDecoderTest {
    private val decoder = RadianceDecoder()

    private val fixture = File(testProject("Untitled"), "assets/skybox_hdr/sky.hdr")

    private fun decode(bytes: ByteArray, maxWidth: Int = MAX_HDR_WIDTH) =
        decoder.read(ByteArrayInputStream(bytes), maxWidth)

    private fun failure(bytes: ByteArray): String {
        try {
            decode(bytes)
        } catch (e: RadianceFormatException) {
            return e.message!!
        }
        fail("expected a RadianceFormatException")
        error("unreachable")
    }

    /** RGBE keeps 8 bits of mantissa and half floats 11: within 1% of the larger channel. */
    private fun assertClose(expected: FloatArray, actual: FloatArray) {
        val tolerance = 0.01f * expected.max() + 1e-4f
        for (i in 0..2) assertTrue("${expected.toList()} vs ${actual.toList()}", abs(expected[i] - actual[i]) <= tolerance)
    }

    @Test
    fun decodesTheFixture() {
        val image = decoder.read(fixture)
        assertEquals(HdrFixtures.FIXTURE_WIDTH, image.width)
        assertEquals(HdrFixtures.FIXTURE_HEIGHT, image.height)
        for (y in 0 until image.height) for (x in 0 until image.width) assertClose(HdrFixtures.skyPixel(x, y), image.pixel(x, y))
    }

    @Test
    fun runLengthAndFlatDecodeTheSame() {
        val rle = decode(HdrFixtures.bytes(64, 32, pixel = HdrFixtures::skyPixel))
        val flat = decode(HdrFixtures.bytes(64, 32, flat = true, pixel = HdrFixtures::skyPixel))
        assertArrayEquals(flat.rgb, rle.rgb)
    }

    @Test
    fun ignoresExposure() {
        val plain = decode(HdrFixtures.bytes(16, 8, pixel = HdrFixtures.uniform(2f)))
        val exposed = decode(HdrFixtures.bytes(16, 8, header = "#?RGBE\nEXPOSURE=4.0\nGAMMA=1\n\n-Y 8 +X 16\n", pixel = HdrFixtures.uniform(2f)))
        assertArrayEquals(plain.rgb, exposed.rgb)
        assertClose(floatArrayOf(2f, 2f, 2f), exposed.pixel(3, 3))
    }

    @Test
    fun rejectsAPositiveYOrientation() {
        val message = failure(HdrFixtures.bytes(64, 32, header = "#?RADIANCE\n\n+Y 32 +X 64\n", pixel = HdrFixtures.uniform(1f)))
        assertTrue(message, message.contains("+Y 32 +X 64"))
    }

    @Test
    fun rejectsXyze() {
        val message = failure(HdrFixtures.bytes(64, 32, header = "#?RADIANCE\nFORMAT=32-bit_rle_xyze\n\n-Y 32 +X 64\n", pixel = HdrFixtures.uniform(1f)))
        assertTrue(message, message.contains("FORMAT=32-bit_rle_xyze"))
    }

    @Test
    fun rejectsANonTwoToOneImage() {
        val message = failure(HdrFixtures.bytes(40, 40, pixel = HdrFixtures.uniform(1f)))
        assertTrue(message, message.contains("2:1"))
    }

    @Test
    fun rejectsAnOversizedHeaderWithoutAllocating() {
        // the header alone: decoding would need gigabytes, so only a header check can pass this quickly
        val message = failure("#?RADIANCE\n\n-Y 100000 +X 200000\n".toByteArray())
        assertTrue(message, message.contains("too large"))
    }

    @Test
    fun halvesAnImageWiderThan4096() {
        // the source is 8 pixels wide; a 4-pixel limit stands in for 4096 so the test stays small
        val pixel = { x: Int, y: Int -> floatArrayOf(x.toFloat(), y.toFloat(), 1f) }
        val image = decode(HdrFixtures.bytes(8, 4, pixel = pixel), maxWidth = 4)
        assertEquals(4, image.width)
        assertEquals(2, image.height)
        // block (1, 1) covers x 2..3, y 2..3
        assertClose(floatArrayOf(2.5f, 2.5f, 1f), image.pixel(1, 1))
    }

    @Test
    fun aTruncatedFileFails() {
        val bytes = fixture.readBytes()
        val message = failure(bytes.copyOf(bytes.size / 2))
        assertTrue(message, message.contains("truncated"))
    }

    @Test
    fun textIsNotAnImage() {
        val message = failure("hello, this is not an image\n".toByteArray())
        assertEquals("not a Radiance image", message)
    }
}
