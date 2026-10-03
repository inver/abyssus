package net.nevinsky.abyssus.sceneview.skybox

import net.nevinsky.abyssus.sceneview.Vec3
import org.junit.Assert.assertEquals
import org.junit.Test

class EquirectTest {
    @Test
    fun minusZIsTheCentre() {
        val (u, v) = Equirect.uv(Vec3(0f, 0f, -1f))
        assertEquals(0.5f, u, 1e-6f)
        assertEquals(0.5f, v, 1e-6f)
    }

    @Test
    fun plusZIsTheEdgeAndPlusXIsThreeQuarters() {
        assertEquals(1f, Equirect.uv(Vec3(0f, 0f, 1f)).first, 1e-6f)
        assertEquals(0.75f, Equirect.uv(Vec3(1f, 0f, 0f)).first, 1e-6f)
        assertEquals(0.25f, Equirect.uv(Vec3(-1f, 0f, 0f)).first, 1e-6f)
    }

    @Test
    fun upIsTheTopRow() {
        assertEquals(0f, Equirect.uv(Vec3(0f, 5f, 0f)).second, 1e-6f)
        assertEquals(1f, Equirect.uv(Vec3(0f, -1f, 0f)).second, 1e-6f)
    }

    @Test
    fun roundTripsDirections() {
        for (u in listOf(0.05f, 0.3f, 0.5f, 0.75f, 0.9f)) for (v in listOf(0.1f, 0.4f, 0.5f, 0.8f)) {
            val (u2, v2) = Equirect.uv(Equirect.direction(u, v))
            assertEquals(u, u2, 1e-5f)
            assertEquals(v, v2, 1e-5f)
        }
    }
}
