package net.nevinsky.abyssus.lib.core.assets.sky.procedural

import com.badlogic.gdx.math.Vector3
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.sqrt

/** [SkyAmbientEstimate], the CPU twin of the fixture sky's scattering (formerly the test-side `AtmosphereModel`). */
class AtmosphereModelTest {
    private val earth = AtmosphereParams()
    private val model = SkyAmbientEstimate(earth)
    private val noon = unit(0f, 0.8f, 0.6f)

    private fun unit(x: Float, y: Float, z: Float): Vector3 {
        val l = sqrt(x * x + y * y + z * z)
        return Vector3(x / l, y / l, z / l)
    }

    private fun luminance(c: DoubleArray) = 0.2126 * c[0] + 0.7152 * c[1] + 0.0722 * c[2]

    @Test
    fun zenithIsBlueAtNoon() {
        val c = model.radiance(Vector3(0f, 1f, 0f), noon)
        assertTrue("blue ${c[2]} must beat red ${c[0]}", c[2] > c[0] * 1.5)
        assertTrue(c[2] > c[1])
    }

    @Test
    fun horizonIsRedderAtSunset() {
        val sunsetSun = unit(0f, 0.02f, 1f)
        val view = unit(0f, 0.05f, 1f)
        val low = model.radiance(view, sunsetSun)
        val high = model.radiance(view, noon)
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
                val c = model.radiance(v, sun)
                c.forEach { assertTrue("$c at $v", it.isFinite() && it >= 0.0) }
            }
        }
    }

    @Test
    fun belowHorizonUsesTheGroundTint() {
        val down = model.radiance(Vector3(0f, -1f, 0f), noon)
        val horizon = model.radiance(unit(0f, 0.05f, 1f), noon)
        assertTrue(luminance(down) > 0.0)
        assertTrue("ground ${luminance(down)} must be dimmer than the horizon sky ${luminance(horizon)}", luminance(down) < luminance(horizon))
        val night = model.radiance(Vector3(0f, -1f, 0f), unit(0f, -0.5f, 1f))
        assertEquals(0.0, luminance(night), 1e-9)
    }

    @Test
    fun ambientZenithIsBluerThanTheHorizonAtNoon() {
        val ambient = model.ambient(noon)
        assertTrue("zenith ${ambient.zenith.toList()} horizon ${ambient.horizon.toList()}",
            ambient.zenith[2] / ambient.zenith[0] > ambient.horizon[2] / ambient.horizon[0])
    }

    @Test
    fun ambientHorizonAndSunlightAreWarmerAtSunset() {
        val sunset = unit(0f, 0.03f, 1f)
        val noonAmbient = model.ambient(noon)
        val noonHorizon = noonAmbient.horizon[0] / noonAmbient.horizon[2]
        val noonSun = noonAmbient.sunlight[0] / noonAmbient.sunlight[2]
        val evening = model.ambient(sunset)
        assertTrue(evening.horizon[0] / evening.horizon[2] > noonHorizon)
        assertTrue("sunset light ${evening.sunlight.toList()} must be redder than noon's", evening.sunlight[0] / evening.sunlight[2] > noonSun)
        assertTrue("red dominates the low sun", evening.sunlight[0] > evening.sunlight[2] * 1.5f)
    }

    @Test
    fun ambientIsCachedPerSunDirection() {
        val first = model.ambient(noon)
        assertTrue(first === model.ambient(Vector3(noon)))
        val other = model.ambient(unit(0f, 0.3f, 1f))
        assertTrue(first !== other)
        assertTrue(other === model.ambient(unit(0f, 0.3f, 1f)))
    }

    @Test
    fun noSunlightBelowTheHorizon() {
        val night = model.ambient(unit(0f, -0.4f, 1f))
        night.sunlight.forEach { assertEquals(0f, it, 0f) }
    }
}
