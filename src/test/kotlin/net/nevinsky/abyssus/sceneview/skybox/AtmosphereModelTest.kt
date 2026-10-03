package net.nevinsky.abyssus.sceneview.skybox

import net.nevinsky.abyssus.sceneview.Vec3
import net.nevinsky.abyssus.sceneview.skybox.procedural.AtmosphereParams
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.sqrt

class AtmosphereModelTest {
    private val earth = AtmosphereParams.EARTH
    private val noon = unit(0f, 0.8f, 0.6f)

    private fun unit(x: Float, y: Float, z: Float): Vec3 {
        val l = sqrt(x * x + y * y + z * z)
        return Vec3(x / l, y / l, z / l)
    }

    private fun luminance(c: DoubleArray) = 0.2126 * c[0] + 0.7152 * c[1] + 0.0722 * c[2]

    @Test
    fun zenithIsBlueAtNoon() {
        val c = AtmosphereModel.radiance(Vec3(0f, 1f, 0f), noon, earth)
        assertTrue("blue ${c[2]} must beat red ${c[0]}", c[2] > c[0] * 1.5)
        assertTrue(c[2] > c[1])
    }

    @Test
    fun horizonIsRedderAtSunset() {
        val sunsetSun = unit(0f, 0.02f, 1f)
        val view = unit(0f, 0.05f, 1f)
        val low = AtmosphereModel.radiance(view, sunsetSun, earth)
        val high = AtmosphereModel.radiance(view, noon, earth)
        assertTrue("red/blue at sunset ${low[0] / low[2]} vs noon ${high[0] / high[2]}", low[0] / low[2] > high[0] / high[2])
        assertTrue(low[0] > low[2] * 0.9)
    }

    @Test
    fun outputIsFiniteAndNonNegative() {
        for (sun in listOf(noon, unit(0f, 0.02f, 1f), unit(0f, -0.3f, 1f))) {
            for (yaw in 0 until 12) for (pitch in -5..5) {
                val a = yaw * Math.PI / 6
                val e = pitch * 0.3
                val v = unit((Math.cos(e) * Math.sin(a)).toFloat(), Math.sin(e).toFloat(), (Math.cos(e) * Math.cos(a)).toFloat())
                val c = AtmosphereModel.radiance(v, sun, earth)
                c.forEach { assertTrue("$c at $v", it.isFinite() && it >= 0.0) }
            }
        }
    }

    @Test
    fun belowHorizonUsesTheGroundTint() {
        val down = AtmosphereModel.radiance(Vec3(0f, -1f, 0f), noon, earth)
        val horizon = AtmosphereModel.radiance(unit(0f, 0.05f, 1f), noon, earth)
        assertTrue(luminance(down) > 0.0)
        assertTrue("ground ${luminance(down)} must be dimmer than the horizon sky ${luminance(horizon)}", luminance(down) < luminance(horizon))
        val night = AtmosphereModel.radiance(Vec3(0f, -1f, 0f), unit(0f, -0.5f, 1f), earth)
        assertEquals(0.0, luminance(night), 1e-9)
    }
}
