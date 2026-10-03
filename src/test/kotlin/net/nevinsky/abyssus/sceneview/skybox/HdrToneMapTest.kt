package net.nevinsky.abyssus.sceneview.skybox

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HdrToneMapTest {
    @Test
    fun midGreyIs140() {
        assertEquals(140f, HdrToneMap.byte(0.18f).toFloat(), 3f)
    }

    @Test
    fun oneTwoFourAreDistinctAndIncreasing() {
        val (a, b, c) = listOf(1f, 2f, 4f).map(HdrToneMap::byte)
        assertTrue("$a $b $c", a < b && b < c && c < 255)
        assertEquals(231f, a.toFloat(), 2f)
        assertEquals(245f, b.toFloat(), 2f)
        assertEquals(252f, c.toFloat(), 2f)
    }

    @Test
    fun clampsAtWhiteAndBlack() {
        assertEquals(255, HdrToneMap.byte(100f))
        assertEquals(0, HdrToneMap.byte(0f))
        assertEquals(0, HdrToneMap.byte(-1f))
    }
}
