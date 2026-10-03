package net.nevinsky.abyssus.assets.sky.hdr

import com.badlogic.gdx.math.Vector3
import org.junit.Assert.assertEquals
import org.junit.Test

class EquirectProjectionTest {
    private val equirect = EquirectProjection()

    @Test
    fun minusZIsTheCentre() {
        val (u, v) = equirect.uv(Vector3(0f, 0f, -1f))
        assertEquals(0.5f, u, 1e-6f)
        assertEquals(0.5f, v, 1e-6f)
    }

    @Test
    fun plusZIsTheEdgeAndPlusXIsThreeQuarters() {
        assertEquals(1f, equirect.uv(Vector3(0f, 0f, 1f)).first, 1e-6f)
        assertEquals(0.75f, equirect.uv(Vector3(1f, 0f, 0f)).first, 1e-6f)
        assertEquals(0.25f, equirect.uv(Vector3(-1f, 0f, 0f)).first, 1e-6f)
    }

    @Test
    fun upIsTheTopRow() {
        assertEquals(0f, equirect.uv(Vector3(0f, 5f, 0f)).second, 1e-6f)
        assertEquals(1f, equirect.uv(Vector3(0f, -1f, 0f)).second, 1e-6f)
    }

    @Test
    fun roundTripsDirections() {
        for (u in listOf(0.05f, 0.3f, 0.5f, 0.75f, 0.9f)) for (v in listOf(0.1f, 0.4f, 0.5f, 0.8f)) {
            val (u2, v2) = equirect.uv(equirect.direction(u, v))
            assertEquals(u, u2, 1e-5f)
            assertEquals(v, v2, 1e-5f)
        }
    }
}
