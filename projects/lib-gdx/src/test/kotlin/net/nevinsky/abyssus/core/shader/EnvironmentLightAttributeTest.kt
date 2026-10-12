package net.nevinsky.abyssus.lib.gdx.shader

import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.GLTexture
import com.badlogic.gdx.graphics.g3d.Environment
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute
import com.badlogic.gdx.graphics.g3d.environment.AmbientCubemap
import net.nevinsky.abyssus.lib.gdx.model.PBRFloatAttribute
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EnvironmentLightAttributeTest {
    /** A texture handle without GL: the attribute only carries it. */
    private class FakeCube(handle: Int) : GLTexture(GL20.GL_TEXTURE_CUBE_MAP, handle) {
        override fun getWidth() = 1
        override fun getHeight() = 1
        override fun getDepth() = 0
        override fun isManaged() = false
        override fun reload() = Unit
    }

    private val axisColors = FloatArray(18) { (it + 1) / 20f }
    private fun sky() = EnvironmentLightAttribute(FakeCube(7), FakeCube(8), 6, axisColors)

    @Test
    fun withoutTheAttributeThePbrDefinesAreUnchanged() {
        val mask = PBRFloatAttribute.Metallic or PBRFloatAttribute.Roughness
        val plain = Environment().apply { set(ColorAttribute(ColorAttribute.AmbientLight, 0.3f, 0.3f, 0.3f, 1f)) }
        // exactly the defines the shader added before the attribute existed
        assertEquals("#define metallicFactorFlag\n#define roughnessFactorFlag\n", PbrShader.pbrDefines(mask, plain))
        assertEquals("#define metallicFactorFlag\n#define roughnessFactorFlag\n", PbrShader.pbrDefines(mask, null))
    }

    @Test
    fun theAttributeAddsTheFlag() {
        val lit = Environment().apply { set(sky()) }
        assertTrue(PbrShader.pbrDefines(PBRFloatAttribute.Metallic, lit).contains("#define environmentLightFlag\n"))
        assertTrue(EnvironmentLightAttribute.has(lit))
        assertFalse(EnvironmentLightAttribute.has(Environment()))
    }

    @Test
    fun theAttributeChangesTheEnvironmentMask() {
        // shaders are cached per attribute mask, so a lit environment gets its own program
        val plain = Environment()
        val lit = Environment().apply { set(sky()) }
        assertTrue(plain.getMask() != lit.getMask())
        assertEquals(1, java.lang.Long.bitCount(EnvironmentLightAttribute.Type))
    }

    @Test
    fun defaultShaderAmbientUsesTheSixColors() {
        val cubemap = AmbientCubemap()
        val both = Environment().apply {
            set(ColorAttribute(ColorAttribute.AmbientLight, 0.3f, 0.3f, 0.3f, 1f))
            set(sky())
        }
        DefaultShader.ACubemapSetter.setAmbientBase(cubemap, both)
        assertArrayEquals(axisColors, cubemap.data, 0f)
    }

    @Test
    fun defaultShaderAmbientWithoutTheSkyIsTheAmbientColor() {
        val cubemap = AmbientCubemap()
        DefaultShader.ACubemapSetter.setAmbientBase(cubemap, Environment().apply { set(ColorAttribute(ColorAttribute.AmbientLight, 0.3f, 0.3f, 0.3f, 1f)) })
        assertArrayEquals(FloatArray(18) { 0.3f }, cubemap.data, 1e-6f)
    }

    @Test
    fun copiesAndComparesByContent() {
        val a = sky()
        assertEquals(a, a.copy())
        assertEquals(0, a.compareTo(a.copy()))
    }

    private fun fading(weight: Float) =
        EnvironmentLightAttribute(FakeCube(7), FakeCube(8), 6, axisColors, EnvironmentLightAttribute(FakeCube(9), FakeCube(10), 5, FloatArray(18) { 1f }), weight)

    @Test
    fun aFadingAttributeCopiesAndComparesWithItsSecondEnvironment() {
        val a = fading(0.25f)
        assertEquals(a, a.copy())
        assertEquals(a.hashCode(), a.copy().hashCode())
        assertEquals(0, a.compareTo(a.copy()))
        assertFalse(a == fading(0.5f))
        assertFalse(a == sky())
        assertTrue(a.compareTo(fading(0.5f)) != 0)
    }

    @Test
    fun theWeightIsClampedAndMeaninglessWithoutASecondEnvironment() {
        assertEquals(1f, fading(3f).weight, 0f)
        assertEquals(0f, fading(-1f).weight, 0f)
        assertEquals(0f, EnvironmentLightAttribute(FakeCube(7), FakeCube(8), 6, axisColors, null, 0.7f).weight, 0f)
        assertEquals(0f, sky().weight, 0f)
    }

    @Test
    fun onlyOneEnvironmentCanBeFadedTo() {
        val chained = runCatching { EnvironmentLightAttribute(FakeCube(1), FakeCube(2), 1, axisColors, fading(0.5f), 0.5f) }
        assertTrue(chained.exceptionOrNull() is IllegalArgumentException)
    }

    @Test
    fun theDefaultShaderBlendsTheAxisColorsOnTheCpu() {
        for ((weight, expected) in listOf(0f to 0.1f, 0.5f to 0.55f, 1f to 1f)) {
            val cubemap = AmbientCubemap()
            val env = Environment().apply {
                set(EnvironmentLightAttribute(FakeCube(7), FakeCube(8), 6, FloatArray(18) { 0.1f },
                    EnvironmentLightAttribute(FakeCube(9), FakeCube(10), 6, FloatArray(18) { 1f }), weight))
            }
            DefaultShader.ACubemapSetter.setAmbientBase(cubemap, env)
            assertArrayEquals("weight $weight", FloatArray(18) { expected }, cubemap.data, 1e-6f)
        }
    }

    @Test
    fun onlyAFadingEnvironmentAddsTheFadeFlag() {
        val plain = Environment().apply { set(sky()) }
        val fade = Environment().apply { set(fading(0.5f)) }
        assertFalse(PbrShader.pbrDefines(PBRFloatAttribute.Metallic, plain).contains("environmentFadeFlag"))
        assertTrue(PbrShader.pbrDefines(PBRFloatAttribute.Metallic, fade).contains("#define environmentFadeFlag\n"))
        assertTrue(EnvironmentLightAttribute.isFading(fade))
        assertFalse(EnvironmentLightAttribute.isFading(plain))
        // same attribute type, so the shader cache needs PbrShader.canRender to tell the two programs apart
        assertEquals(plain.getMask(), fade.getMask())
    }
}
